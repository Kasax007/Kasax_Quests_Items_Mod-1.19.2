package net.kasax.questsicons.client;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.client.gui.QuestBookScreen;
import net.kasax.questsicons.client.gui.QuestHud;
import net.kasax.questsicons.client.gui.QuestToast;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ProgressView;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Client state: the synced book, the team's progress, pinned quests, key binding and notifications. */
@Environment(EnvType.CLIENT)
public final class ClientQuests {
    public static @Nullable QuestBook book;
    public static boolean canEdit;
    public static final Progress progress = new Progress();
    public static final LongLinkedOpenHashSet pinned = new LongLinkedOpenHashSet();
    private static final Path PIN_FILE = FabricLoader.getInstance().getConfigDir().resolve("questsicons-pinned.txt");

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(QuestsIcons.MOD_ID, "main"));
    public static final KeyMapping OPEN_BOOK = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.questsicons.open_book", InputConstants.Type.KEYSYM, InputConstants.KEY_J, CATEGORY));

    private ClientQuests() {
    }

    public static void init() {
        loadPins();
        EditorData.init();
        ClientPlayNetworking.registerGlobalReceiver(QuestNet.BookSync.TYPE, (payload, ctx) -> {
            try {
                QuestBook b = QuestBook.readAll(QuestNet.decompress(payload.data()));
                if (ctx.client().level != null) b.registries = ctx.client().level.registryAccess();
                book = b;
                canEdit = payload.canEdit();
                if (ctx.client().screen instanceof QuestBookScreen s) s.bookUpdated();
            } catch (Exception e) {
                QuestsIcons.LOGGER.error("Could not read the quest book from the server", e);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(QuestNet.TeamSync.TYPE, (payload, ctx) -> {
            progress.read(payload.data());
            if (ctx.client().screen instanceof QuestBookScreen s) s.progressUpdated();
        });
        ClientPlayNetworking.registerGlobalReceiver(QuestNet.Notify.TYPE, (payload, ctx) -> notify(ctx.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            book = null;
            canEdit = false;
            progress.clear();
            EditorData.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_BOOK.consumeClick()) {
                if (mc.player != null && mc.screen == null) open(mc);
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(QuestsIcons.MOD_ID, "pinned"), new QuestHud());
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
                Button b = Button.builder(Component.literal("✉"), btn -> open(mc))
                        .bounds(4, 4, 20, 20)
                        .tooltip(Tooltip.create(Component.translatable("questsicons.gui.open_book")))
                        .build();
                Screens.getButtons(screen).add(b);
            }
        });
    }

    public static void open(Minecraft mc) {
        if (book == null) {
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("questsicons.gui.no_book"), false);
            ClientPlayNetworking.send(new QuestNet.Action(QuestNet.Action.REQUEST_SYNC, 0, 0));
            return;
        }
        mc.setScreen(new QuestBookScreen());
    }

    private static void notify(Minecraft mc, QuestNet.Notify n) {
        switch (n.kind()) {
            case QuestNet.Notify.OPEN -> open(mc);
            case QuestNet.Notify.QUEST -> {
                Quest q = book == null ? null : book.quest(n.id());
                if (q != null) {
                    mc.getToastManager().addToast(new QuestToast(q.displayIcon(book.registries), q.displayTitle(),
                            Component.translatable("questsicons.toast.quest_complete"), false));
                }
            }
            case QuestNet.Notify.CHAPTER -> {
                Chapter c = book == null ? null : book.chapter(n.id());
                if (c != null) {
                    mc.getToastManager().addToast(new QuestToast(c.displayIcon(book.registries), c.displayTitle(),
                            Component.translatable("questsicons.toast.chapter_complete"), true));
                }
            }
            case QuestNet.Notify.CLAIMED -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.2f));
            default -> {
            }
        }
    }

    public static void togglePin(long questId) {
        if (!pinned.remove(questId)) pinned.add(questId);
        savePins();
    }

    private static void loadPins() {
        try {
            if (Files.exists(PIN_FILE)) {
                for (String line : Files.readAllLines(PIN_FILE)) if (!line.isBlank()) pinned.add(QuestObject.parseId(line.trim()));
            }
        } catch (Exception ignored) {
        }
    }

    private static void savePins() {
        try {
            List<String> lines = new ArrayList<>();
            for (long id : pinned) lines.add(QuestObject.idToString(id));
            Files.write(PIN_FILE, lines);
        } catch (Exception ignored) {
        }
    }

    /** The client's copy of the team progress. */
    public static final class Progress implements ProgressView {
        public String teamName = "";
        public final List<String> members = new ArrayList<>();
        private final Long2LongOpenHashMap tasks = new Long2LongOpenHashMap();
        private final Long2LongOpenHashMap completed = new Long2LongOpenHashMap();
        private final LongOpenHashSet claimed = new LongOpenHashSet();
        private final LongOpenHashSet teamClaimed = new LongOpenHashSet();

        void clear() {
            tasks.clear();
            completed.clear();
            claimed.clear();
            teamClaimed.clear();
            members.clear();
        }

        void read(CompoundTag t) {
            clear();
            teamName = t.getStringOr("name", "");
            for (Tag x : t.getListOrEmpty("members")) if (x instanceof StringTag s) members.add(s.value());
            CompoundTag p = t.getCompoundOrEmpty("progress");
            for (String k : p.keySet()) tasks.put(QuestObject.parseId(k), p.getLongOr(k, 0));
            CompoundTag c = t.getCompoundOrEmpty("completed");
            for (String k : c.keySet()) completed.put(QuestObject.parseId(k), c.getLongOr(k, 0));
            for (Tag x : t.getListOrEmpty("claimed")) if (x instanceof StringTag s) claimed.add(QuestObject.parseId(s.value()));
            for (Tag x : t.getListOrEmpty("team_claimed")) if (x instanceof StringTag s) teamClaimed.add(QuestObject.parseId(s.value()));
        }

        @Override
        public long taskProgress(long taskId) {
            return tasks.get(taskId);
        }

        @Override
        public boolean isQuestCompleted(long questId) {
            return completed.containsKey(questId);
        }

        @Override
        public boolean isRewardClaimed(long rewardId, boolean teamReward) {
            return teamReward ? teamClaimed.contains(rewardId) : claimed.contains(rewardId);
        }

        public int unclaimedTotal() {
            if (book == null) return 0;
            int n = 0;
            for (Quest q : book.allQuests()) if (hasUnclaimedRewards(q)) n++;
            return n;
        }
    }
}
