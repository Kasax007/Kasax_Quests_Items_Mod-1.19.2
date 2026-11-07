package net.kasax.questsicons.quest;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.task.Task;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The whole quest book. On disk it uses the FTB Quests folder layout:
 * {@code data.snbt}, {@code chapter_groups.snbt}, {@code chapters/*.snbt}, {@code reward_tables/*.snbt}.
 */
public final class QuestBook {
    public String title = "Quest Book";
    public ItemSpec icon = ItemSpec.EMPTY;
    public String defaultShape = "circle";
    public boolean defaultTeamReward;
    public boolean giveBookOnFirstJoin = true;
    /** Optional texture drawn behind the quest map (e.g. "questsicons:textures/gui/background.png"). */
    public String background = "";
    public final List<ChapterGroup> groups = new ArrayList<>();
    public final List<Chapter> chapters = new ArrayList<>();
    public final Map<String, RewardTable> rewardTables = new LinkedHashMap<>();

    /** Registry access used to build item stacks (set by the server or the client after loading). */
    public transient HolderLookup.@Nullable Provider registries;

    private final Long2ObjectOpenHashMap<Quest> quests = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Task> tasks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Reward> rewards = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Chapter> chapterById = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongArrayList> dependents = new Long2ObjectOpenHashMap<>();

    public @Nullable Quest quest(long id) {
        return quests.get(id);
    }

    public @Nullable Task task(long id) {
        return tasks.get(id);
    }

    public @Nullable Reward reward(long id) {
        return rewards.get(id);
    }

    public @Nullable Chapter chapter(long id) {
        return chapterById.get(id);
    }

    public Iterable<Quest> allQuests() {
        return quests.values();
    }

    public Iterable<Task> allTasks() {
        return tasks.values();
    }

    /** Quests that list {@code questId} as a dependency. */
    public LongArrayList dependents(long questId) {
        LongArrayList l = dependents.get(questId);
        return l == null ? new LongArrayList() : l;
    }

    public void rebuild() {
        chapters.sort(Comparator.comparingInt((Chapter c) -> c.orderIndex));
        quests.clear();
        tasks.clear();
        rewards.clear();
        chapterById.clear();
        dependents.clear();
        Set<Long> seen = new HashSet<>();
        for (Chapter c : chapters) {
            c.book = this;
            chapterById.put(c.id, c);
            for (Quest q : c.quests) {
                q.chapter = c;
                if (!seen.add(q.id)) QuestsIcons.LOGGER.warn("Duplicate quest id {} in chapter {}", q.idString(), c.filename);
                quests.put(q.id, q);
                for (Task t : q.tasks) {
                    t.quest = q;
                    tasks.put(t.id, t);
                }
                for (Reward r : q.rewards) {
                    r.quest = q;
                    rewards.put(r.id, r);
                }
            }
        }
        for (Quest q : quests.values()) {
            for (long dep : q.dependencies) dependents.computeIfAbsent(dep, k -> new LongArrayList()).add(q.id);
        }
    }

    public long newId() {
        long id;
        do {
            id = java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0x7FFFFFFFFFFFFFFFL;
        } while (id == 0 || quests.containsKey(id) || tasks.containsKey(id) || rewards.containsKey(id) || chapterById.containsKey(id));
        return id;
    }

    // ---------------------------------------------------------------- whole-book tag (network, copy)

    public CompoundTag writeAll() {
        CompoundTag tag = writeData();
        ListTag gl = new ListTag();
        for (ChapterGroup g : groups) gl.add(g.write());
        tag.put("chapter_groups", gl);
        ListTag cl = new ListTag();
        for (Chapter c : chapters) cl.add(c.write());
        tag.put("chapters", cl);
        CompoundTag tables = new CompoundTag();
        rewardTables.forEach((k, t) -> tables.put(k, t.write()));
        tag.put("reward_tables", tables);
        return tag;
    }

    public static QuestBook readAll(CompoundTag tag) {
        QuestBook b = new QuestBook();
        b.readData(tag);
        for (Tag t : tag.getListOrEmpty("chapter_groups")) {
            if (t instanceof CompoundTag c) b.groups.add(ChapterGroup.read(c));
        }
        for (Tag t : tag.getListOrEmpty("chapters")) {
            if (!(t instanceof CompoundTag c)) continue;
            Chapter ch = new Chapter();
            ch.book = b;
            ch.read(c);
            b.chapters.add(ch);
        }
        CompoundTag tables = tag.getCompoundOrEmpty("reward_tables");
        for (String k : tables.keySet()) b.rewardTables.put(k, RewardTable.read(k, tables.getCompoundOrEmpty(k)));
        b.rebuild();
        return b;
    }

    public CompoundTag writeData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", title);
        if (!icon.isEmpty()) tag.put("icon", icon.write());
        tag.putString("default_quest_shape", defaultShape);
        tag.putBoolean("default_reward_team", defaultTeamReward);
        tag.putBoolean("give_book_on_first_join", giveBookOnFirstJoin);
        if (!background.isEmpty()) tag.putString("background", background);
        return tag;
    }

    public void readData(CompoundTag tag) {
        title = tag.getStringOr("title", title);
        icon = ItemSpec.read(tag.get("icon"));
        defaultShape = tag.getStringOr("default_quest_shape", "circle");
        defaultTeamReward = tag.getBooleanOr("default_reward_team", false);
        giveBookOnFirstJoin = tag.getBooleanOr("give_book_on_first_join", true);
        background = tag.getStringOr("background", "");
    }

    // ---------------------------------------------------------------- folder IO

    public static QuestBook load(Path dir) throws IOException {
        QuestBook b = new QuestBook();
        Path data = dir.resolve("data.snbt");
        if (Files.exists(data)) b.readData(Snbt.read(data));
        Path groups = dir.resolve("chapter_groups.snbt");
        if (Files.exists(groups)) {
            for (Tag t : Snbt.read(groups).getListOrEmpty("chapter_groups")) {
                if (t instanceof CompoundTag c) b.groups.add(ChapterGroup.read(c));
            }
        }
        Path chapters = dir.resolve("chapters");
        if (Files.isDirectory(chapters)) {
            try (Stream<Path> files = Files.list(chapters)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".snbt")).sorted().toList()) {
                    try {
                        Chapter c = new Chapter();
                        c.book = b;
                        String name = f.getFileName().toString();
                        c.filename = name.substring(0, name.length() - 5);
                        c.read(Snbt.read(f));
                        if (c.id == 0) c.id = QuestObject.hashId("chapter/" + c.filename);
                        b.chapters.add(c);
                    } catch (Exception e) {
                        QuestsIcons.LOGGER.error("Could not read quest chapter {}: {}", f, e.getMessage());
                    }
                }
            }
        }
        Path tables = dir.resolve("reward_tables");
        if (Files.isDirectory(tables)) {
            try (Stream<Path> files = Files.list(tables)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".snbt")).sorted().toList()) {
                    try {
                        String name = f.getFileName().toString();
                        String key = name.substring(0, name.length() - 5);
                        b.rewardTables.put(key, RewardTable.read(key, Snbt.read(f)));
                    } catch (Exception e) {
                        QuestsIcons.LOGGER.error("Could not read reward table {}: {}", f, e.getMessage());
                    }
                }
            }
        }
        b.rebuild();
        return b;
    }

    public void save(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("chapters"));
        Snbt.write(dir.resolve("data.snbt"), writeData());
        CompoundTag g = new CompoundTag();
        ListTag gl = new ListTag();
        for (ChapterGroup cg : groups) gl.add(cg.write());
        g.put("chapter_groups", gl);
        Snbt.write(dir.resolve("chapter_groups.snbt"), g);
        Set<String> written = new HashSet<>();
        for (Chapter c : chapters) {
            if (c.filename.isEmpty()) c.filename = "chapter_" + c.idString().toLowerCase();
            Snbt.write(dir.resolve("chapters").resolve(c.filename + ".snbt"), c.write());
            written.add(c.filename + ".snbt");
        }
        try (Stream<Path> files = Files.list(dir.resolve("chapters"))) {
            for (Path f : files.toList()) {
                if (f.toString().endsWith(".snbt") && !written.contains(f.getFileName().toString())) Files.delete(f);
            }
        }
        Set<String> tables = new HashSet<>();
        if (!rewardTables.isEmpty()) {
            Files.createDirectories(dir.resolve("reward_tables"));
            for (RewardTable t : rewardTables.values()) {
                Snbt.write(dir.resolve("reward_tables").resolve(t.key + ".snbt"), t.write());
                tables.add(t.key + ".snbt");
            }
        }
        // A table deleted in the editor must not come back from its old file on the next start.
        if (Files.isDirectory(dir.resolve("reward_tables"))) {
            try (Stream<Path> files = Files.list(dir.resolve("reward_tables"))) {
                for (Path f : files.toList()) {
                    if (f.toString().endsWith(".snbt") && !tables.contains(f.getFileName().toString())) Files.delete(f);
                }
            }
        }
    }

    public int questCount() {
        return quests.size();
    }
}
