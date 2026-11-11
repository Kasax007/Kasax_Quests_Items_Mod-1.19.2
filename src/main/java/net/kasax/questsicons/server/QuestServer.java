package net.kasax.questsicons.server;

import net.kasax.questsicons.QuestsIcons;
import net.neoforged.neoforge.network.PacketDistributor;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.progress.QuestSavedData;
import net.kasax.questsicons.progress.TeamData;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ProgressView;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.StarterBook;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.task.Task;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server side of the quest system: owns the book, detects progress, hands out rewards. */
public final class QuestServer {
    public static final Path DIR = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("questsicons").resolve("quests");

    private static @Nullable QuestServer instance;

    public final MinecraftServer server;
    public QuestBook book;
    private byte[] bookBytes;
    private final List<Task> detectTasks = new ArrayList<>();
    /** Tasks whose detection threw (e.g. a typo in an id): switched off until the book is reloaded. */
    private final List<Task> brokenTasks = new ArrayList<>();
    private final Set<UUID> dirtyTeams = new HashSet<>();
    private long tick;

    private QuestServer(MinecraftServer server) {
        this.server = server;
        load();
    }

    public static @Nullable QuestServer get() {
        return instance;
    }

    // ------------------------------------------------------------------------------- loader hooks
    // NeoForge events call these (wired in QuestsIcons and QuestNet); on Fabric the same calls sit in Fabric API callbacks.

    public static void serverStarted(MinecraftServer s) {
        instance = new QuestServer(s);
    }

    public static void serverStopped() {
        instance = null;
    }

    public static void dataPacksReloaded() {
        if (instance != null) instance.bookChanged(false);
    }

    public static void playerJoined(ServerPlayer p) {
        if (instance != null) instance.onJoin(p);
    }

    public static void serverTick() {
        if (instance != null) instance.tick();
    }

    public static void entityKilled(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (instance != null && source.getEntity() instanceof ServerPlayer p) instance.onKill(p, entity);
    }

    public static void received(ServerPlayer p, QuestNet.Action payload) {
        if (instance != null) instance.onAction(p, payload);
    }

    public static void received(ServerPlayer p, QuestNet.Edit payload) {
        if (instance != null) QuestEditor.apply(instance, p, payload.op());
    }

    // ------------------------------------------------------------------------------- book

    public void load() {
        try {
            if (!Files.exists(DIR.resolve("data.snbt"))) {
                StarterBook.create().save(DIR);
                QuestsIcons.LOGGER.info("Created a starter quest book in {}", DIR);
            }
            book = QuestBook.load(DIR);
            QuestsIcons.LOGGER.info("Loaded quest book '{}': {} chapters, {} quests", book.title, book.chapters.size(), book.questCount());
        } catch (Exception e) {
            QuestsIcons.LOGGER.error("Could not load the quest book from {}", DIR, e);
            book = new QuestBook();
            book.rebuild();
        }
        bookChanged(false);
    }

    public void save() {
        try {
            book.save(DIR);
        } catch (Exception e) {
            QuestsIcons.LOGGER.error("Could not save the quest book", e);
        }
    }

    /** Re-index, re-encode and send the book to everyone. */
    public void bookChanged(boolean save) {
        book.registries = server.registryAccess();
        book.rebuild();
        if (save) save();
        bookBytes = QuestNet.compress(book.writeAll());
        detectTasks.clear();
        brokenTasks.clear();
        for (Task t : book.allTasks()) if (t.autoDetect()) detectTasks.add(t);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendBook(p);
            TeamData team = team(p);
            completeTaskless(team, p);
            dirtyTeams.add(team.id);
        }
    }

    public HolderLookup.Provider registries() {
        return server.registryAccess();
    }

    public static boolean canEdit(ServerPlayer p) {
        return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public void sendBook(ServerPlayer p) {
        boolean can = canEdit(p);
        editors.put(p.getUUID(), can);
        PacketDistributor.sendToPlayer(p, new QuestNet.BookSync(bookBytes, can));
    }

    // ------------------------------------------------------------------------------- teams

    public QuestSavedData data() {
        return QuestSavedData.get(server);
    }

    public TeamData team(ServerPlayer p) {
        return data().teamOf(p.getUUID(), p.getGameProfile().name());
    }

    public void syncTeam(TeamData team) {
        for (UUID m : team.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) PacketDistributor.sendToPlayer(p, new QuestNet.TeamSync(team.writeForClient(m)));
        }
    }

    public void markDirty(TeamData team) {
        data().setDirty();
        dirtyTeams.add(team.id);
    }

    private void onJoin(ServerPlayer p) {
        TeamData team = team(p);
        sendBook(p);
        completeTaskless(team, p);
        PacketDistributor.sendToPlayer(p, new QuestNet.TeamSync(team.writeForClient(p.getUUID())));
        if (book.giveBookOnFirstJoin && !team.gotBook.contains(p.getUUID())) {
            team.gotBook.add(p.getUUID());
            data().setDirty();
            ItemStack bookItem = new ItemStack(ModItems.QUEST_BOOK);
            if (!p.getInventory().add(bookItem)) p.drop(bookItem, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    // ------------------------------------------------------------------------------- progress

    /** Who could edit at the last check: an operator made or removed while online gets the edit button at once. */
    private final java.util.Map<java.util.UUID, Boolean> editors = new java.util.HashMap<>();

    private void tick() {
        tick++;
        if (tick % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Boolean was = editors.get(p.getUUID());
                if (was != null && was != canEdit(p)) sendBook(p);
            }
        }
        if (!detectTasks.isEmpty()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator()) continue;
                TeamData team = team(p);
                ProgressView view = team.viewFor(p.getUUID());
                long phase = tick + (p.getId() * 7L);
                for (Task t : detectTasks) {
                    if (phase % t.detectInterval() != 0) continue;
                    if (view.isTaskComplete(t) || view.isQuestCompleted(t.quest.id) || !view.canStart(t.quest)) continue;
                    try {
                        t.detect(p, ctx(team, t, p));
                    } catch (RuntimeException e) {
                        // A broken task must not take the server down with it: report it once and stop checking it.
                        if (!brokenTasks.contains(t)) {
                            brokenTasks.add(t);
                            QuestsIcons.LOGGER.error("Quest {}: task {} ({}) failed and is switched off until /quests reload",
                                    t.quest.idString(), t.idString(), t.type(), e);
                        }
                    }
                }
            }
            if (!brokenTasks.isEmpty()) detectTasks.removeAll(brokenTasks);
        }
        if (!dirtyTeams.isEmpty()) {
            for (UUID id : dirtyTeams) {
                TeamData t = data().teams.get(id);
                if (t != null) syncTeam(t);
            }
            dirtyTeams.clear();
        }
    }

    private Task.TaskContext ctx(TeamData team, Task task, ServerPlayer actor) {
        return new Task.TaskContext() {
            @Override
            public long get() {
                return team.progress.get(task.id);
            }

            @Override
            public void set(long value) {
                setProgress(team, task, value, actor);
            }

            @Override
            public HolderLookup.Provider registries() {
                return server.registryAccess();
            }
        };
    }

    public void setProgress(TeamData team, Task task, long value, @Nullable ServerPlayer actor) {
        long v = Math.max(0, Math.min(value, task.maxProgress()));
        long old = team.progress.get(task.id);
        if (v == old) return;
        team.progress.put(task.id, v);
        markDirty(team);
        if (v >= task.maxProgress() && old < task.maxProgress()) {
            Quest q = task.quest;
            ProgressView view = team.viewFor(actor != null ? actor.getUUID() : team.owner);
            if (!view.isQuestCompleted(q.id) && q.tasksComplete(view::isTaskComplete)) {
                completeQuest(team, q, actor);
            }
        }
    }

    public void completeQuest(TeamData team, Quest q, @Nullable ServerPlayer actor) {
        if (team.completed.containsKey(q.id)) return;
        team.completed.put(q.id, System.currentTimeMillis());
        for (Task t : q.tasks) team.progress.put(t.id, t.maxProgress());
        markDirty(team);
        Chapter chapter = q.chapter;
        // Only the quest that finishes the chapter announces it (an optional quest in a done chapter does not).
        boolean chapterDone = !q.optional && team.viewFor(team.owner).isChapterComplete(chapter);
        for (UUID m : team.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p == null) continue;
            if (!q.disableToast) PacketDistributor.sendToPlayer(p, new QuestNet.Notify(QuestNet.Notify.QUEST, q.id));
            if (chapterDone) PacketDistributor.sendToPlayer(p, new QuestNet.Notify(QuestNet.Notify.CHAPTER, chapter.id));
            for (Reward r : q.rewards) {
                if (r.autoClaim && !r.needsChoice()) claim(p, team, r, -1, false);
            }
        }
        // Dependents without tasks (info / milestone quests) finish on their own.
        for (long dep : book.dependents(q.id)) {
            Quest d = book.quest(dep);
            if (d != null && d.tasks.isEmpty() && team.viewFor(team.owner).canStart(d)) completeQuest(team, d, actor);
        }
    }

    /** Quests without tasks whose dependencies are met complete immediately. */
    public void completeTaskless(TeamData team, @Nullable ServerPlayer actor) {
        ProgressView v = team.viewFor(team.owner);
        for (Quest q : book.allQuests()) {
            if (q.tasks.isEmpty() && !v.isQuestCompleted(q.id) && v.canStart(q)) completeQuest(team, q, actor);
        }
        // A book edit can also make existing progress complete a quest.
        for (Quest q : book.allQuests()) {
            if (!q.tasks.isEmpty() && !v.isQuestCompleted(q.id) && q.tasksComplete(v::isTaskComplete)) completeQuest(team, q, actor);
        }
    }

    private void onKill(ServerPlayer killer, net.minecraft.world.entity.LivingEntity victim) {
        TeamData team = team(killer);
        ProgressView view = team.viewFor(killer.getUUID());
        for (Task t : book.allTasks()) {
            if (t instanceof Tasks.KillTask k && !brokenTasks.contains(t) && k.matches(victim) && !view.isTaskComplete(t)
                    && !view.isQuestCompleted(t.quest.id) && view.canStart(t.quest)) {
                setProgress(team, t, team.progress.get(t.id) + 1, killer);
            }
        }
    }

    // ------------------------------------------------------------------------------- actions

    private void onAction(ServerPlayer p, QuestNet.Action a) {
        TeamData team = team(p);
        switch (a.action()) {
            case QuestNet.Action.SUBMIT -> {
                Task t = book.task(a.id());
                if (t == null) return;
                ProgressView view = team.viewFor(p.getUUID());
                if (view.isQuestCompleted(t.quest.id) || !view.canStart(t.quest) || view.isTaskComplete(t)) return;
                t.submit(p, ctx(team, t, p));
            }
            case QuestNet.Action.CLAIM -> {
                Reward r = book.reward(a.id());
                if (r != null) claim(p, team, r, a.extra(), true);
            }
            case QuestNet.Action.CLAIM_ALL -> {
                int n = 0;
                for (Quest q : book.allQuests()) {
                    if (!team.completed.containsKey(q.id)) continue;
                    for (Reward r : q.rewards) {
                        if (!r.isExcludedFromClaimAll() && claim(p, team, r, -1, false)) n++;
                    }
                }
                if (n > 0) p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            case QuestNet.Action.REQUEST_SYNC -> {
                sendBook(p);
                PacketDistributor.sendToPlayer(p, new QuestNet.TeamSync(team.writeForClient(p.getUUID())));
            }
            default -> {
            }
        }
    }

    public boolean claim(ServerPlayer p, TeamData team, Reward r, int choice, boolean sound) {
        if (!team.completed.containsKey(r.quest.id)) return false;
        boolean teamReward = r.isTeamReward();
        if (team.isRewardClaimed(p.getUUID(), r.id, teamReward)) return false;
        // A bad pick (e.g. the book changed while the picker was open) must not use up the reward.
        if (!r.acceptsChoice(choice)) return false;
        team.markClaimed(p.getUUID(), r.id, teamReward);
        markDirty(team);
        try {
            r.claim(p, choice);
        } catch (Exception e) {
            QuestsIcons.LOGGER.error("Reward {} failed", r.idString(), e);
            p.sendSystemMessage(Component.literal("Reward failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }
        if (sound) p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
        PacketDistributor.sendToPlayer(p, new QuestNet.Notify(QuestNet.Notify.CLAIMED, r.id));
        return true;
    }

    // ------------------------------------------------------------------------------- admin

    public void forceComplete(TeamData team, Quest q, @Nullable ServerPlayer actor) {
        for (Task t : q.tasks) team.progress.put(t.id, t.maxProgress());
        completeQuest(team, q, actor);
        markDirty(team);
    }

    public void reset(TeamData team, @Nullable Quest q) {
        if (q == null) {
            team.progress.clear();
            team.completed.clear();
            team.claimed.clear();
            team.teamClaimed.clear();
        } else {
            for (Task t : q.tasks) team.progress.remove(t.id);
            team.completed.remove(q.id);
            for (Reward r : q.rewards) {
                team.teamClaimed.remove(r.id);
                team.claimed.values().forEach(s -> s.remove(r.id));
            }
        }
        markDirty(team);
        completeTaskless(team, null);
    }

    public void openBook(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new QuestNet.Notify(QuestNet.Notify.OPEN, 0));
    }
}
