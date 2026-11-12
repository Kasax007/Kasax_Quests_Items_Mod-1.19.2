package net.kasax.questsicons.server;

import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ChapterGroup;
import net.kasax.questsicons.quest.ChapterImage;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.RewardTable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies editor operations sent by an operator's client. Every op names the object it touches; the server
 * re-indexes, writes the SNBT files and sends the new book to everyone (other editors see changes live).
 *
 * <p>Ops: {@code quest_set} (chapter, quest), {@code quest_delete} (id), {@code quest_move} (id, x, y[, chapter]),
 * {@code dependency} (id, dep, remove), {@code chapter_set} (chapter: properties without quests), {@code chapter_new}
 * (title[, id, group]), {@code chapter_delete} (id), {@code chapter_move} (id, delta | index[, group]),
 * {@code group_new} (title[, id]), {@code group_set} (group), {@code group_delete} (id), {@code group_move} (id, delta),
 * {@code table_set} (key, table), {@code table_delete} (key), {@code image_add} (chapter, image), {@code image_set}
 * (chapter, index, image), {@code image_delete} (chapter, index), {@code book_set} (data | title, ...),
 * {@code restore} (undo: whole chapters, groups, tables or book settings as they were), {@code batch} (ops),
 * {@code reload}, and {@code lists} (sends the ids the editor's pickers offer; changes nothing).
 */
public final class QuestEditor {
    private QuestEditor() {
    }

    public static void apply(QuestServer qs, ServerPlayer player, CompoundTag op) {
        if (!QuestServer.canEdit(player)) {
            player.sendSystemMessage(Component.translatable("questsicons.edit.denied").withStyle(ChatFormatting.RED));
            return;
        }
        String kind = op.getStringOr("op", "");
        if (kind.equals("lists")) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new QuestNet.EditorLists(lists(qs.server)));
            return;
        }
        if (kind.equals("reload")) {
            qs.load();
            player.sendSystemMessage(Component.translatable("questsicons.command.reloaded", qs.book.chapters.size()));
            return;
        }
        try {
            if (!applyOne(qs.book, player, op)) return;
        } catch (Exception e) {
            QuestsIcons.LOGGER.error("Editor op {} failed", kind, e);
            player.sendSystemMessage(Component.literal("Edit failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return;
        }
        qs.bookChanged(true);
    }

    /** Applies one op to the book; false if it changed nothing (unknown object). */
    public static boolean applyOne(QuestBook book, @org.jetbrains.annotations.Nullable ServerPlayer player, CompoundTag op) {
        String kind = op.getStringOr("op", "");
        switch (kind) {
            case "batch" -> {
                boolean any = false;
                for (Tag t : op.getListOrEmpty("ops")) {
                    if (t instanceof CompoundTag c) {
                        any |= applyOne(book, player, c);
                        book.rebuild();   // later ops look objects up by id
                    }
                }
                return any;
            }
            case "quest_set" -> {
                Chapter chapter = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                if (chapter == null) return false;
                CompoundTag qt = op.getCompoundOrEmpty("quest");
                // Typed without an id: a fresh one (the id a quest file derives from the position could be taken).
                if (QuestObject.parseId(qt.getStringOr("id", "")) == 0) qt.putString("id", QuestObject.idToString(book.newId()));
                Quest q = new Quest();
                q.chapter = chapter;
                q.read(qt);
                Quest old = book.quest(q.id);
                if (old != null) {
                    int idx = old.chapter.quests.indexOf(old);
                    old.chapter.quests.remove(old);
                    if (old.chapter == chapter) {
                        chapter.quests.add(Math.max(0, Math.min(idx, chapter.quests.size())), q);
                    } else {
                        chapter.quests.add(q);
                    }
                } else {
                    chapter.quests.add(q);
                }
            }
            case "quest_delete" -> {
                long id = QuestObject.parseId(op.getStringOr("id", ""));
                Quest q = book.quest(id);
                if (q == null) return false;
                q.chapter.quests.remove(q);
                for (Quest other : book.allQuests()) other.dependencies.rem(id);
                for (Chapter c : book.chapters) for (ChapterImage i : c.images) if (i.dependency == id) i.dependency = 0;
            }
            case "quest_move" -> {
                Quest q = book.quest(QuestObject.parseId(op.getStringOr("id", "")));
                if (q == null) return false;
                q.x = op.getDoubleOr("x", q.x);
                q.y = op.getDoubleOr("y", q.y);
                if (op.contains("chapter")) {
                    Chapter target = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                    if (target != null && target != q.chapter) {
                        q.chapter.quests.remove(q);
                        q.chapter = target;
                        target.quests.add(q);
                    }
                }
            }
            case "dependency" -> {
                Quest q = book.quest(QuestObject.parseId(op.getStringOr("id", "")));
                long dep = QuestObject.parseId(op.getStringOr("dep", ""));
                if (q == null || book.quest(dep) == null || dep == q.id) return false;
                if (op.getBooleanOr("remove", false)) q.dependencies.rem(dep);
                else if (!q.dependencies.contains(dep)) q.dependencies.add(dep);
            }
            case "chapter_set" -> {
                CompoundTag ct = op.getCompoundOrEmpty("chapter");
                Chapter c = book.chapter(QuestObject.parseId(ct.getStringOr("id", "")));
                if (c == null) return false;
                var quests = new ArrayList<>(c.quests);
                var images = new ArrayList<>(c.images);
                String filename = c.filename;
                int order = c.orderIndex;
                c.read(ct);
                c.filename = filename;
                if (!ct.contains("order_index")) c.orderIndex = order;
                c.quests.clear();
                c.quests.addAll(quests);
                if (!ct.contains("images")) {
                    c.images.clear();
                    c.images.addAll(images);
                }
            }
            case "chapter_new" -> {
                Chapter c = new Chapter();
                c.book = book;
                long wanted = QuestObject.parseId(op.getStringOr("id", ""));
                c.id = wanted != 0 && book.chapter(wanted) == null ? wanted : book.newId();
                c.title = op.getStringOr("title", "New Chapter");
                c.group = op.getStringOr("group", "");
                c.filename = c.title.toLowerCase().replaceAll("&.", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                if (c.filename.isEmpty() || book.chapters.stream().anyMatch(x -> x.filename.equals(c.filename))) {
                    c.filename = "chapter_" + c.idString().toLowerCase();
                }
                c.orderIndex = book.chapters.stream().mapToInt(x -> x.orderIndex).max().orElse(-1) + 1;
                book.chapters.add(c);
            }
            case "chapter_delete" -> {
                Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("id", "")));
                if (c == null) return false;
                book.chapters.remove(c);
            }
            case "chapter_move" -> {
                Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("id", "")));
                if (c == null) return false;
                int i = book.chapters.indexOf(c);
                int j = op.contains("index") ? op.getIntOr("index", i) : i + op.getIntOr("delta", 0);
                book.chapters.remove(i);
                book.chapters.add(Math.max(0, Math.min(book.chapters.size(), j)), c);
                if (op.contains("group")) c.group = op.getStringOr("group", "");
                for (int k = 0; k < book.chapters.size(); k++) book.chapters.get(k).orderIndex = k;
            }
            case "group_new" -> {
                ChapterGroup g = new ChapterGroup();
                long wanted = QuestObject.parseId(op.getStringOr("id", ""));
                g.id = wanted != 0 && group(book, wanted) == null ? wanted : book.newId();
                g.title = op.getStringOr("title", "New Group");
                book.groups.add(g);
            }
            case "group_set" -> {
                CompoundTag gt = op.getCompoundOrEmpty("group");
                ChapterGroup g = group(book, QuestObject.parseId(gt.getStringOr("id", "")));
                if (g == null) return false;
                ChapterGroup n = ChapterGroup.read(gt);
                g.title = n.title;
                g.icon = n.icon;
            }
            case "group_delete" -> {
                ChapterGroup g = group(book, QuestObject.parseId(op.getStringOr("id", "")));
                if (g == null) return false;
                book.groups.remove(g);
                for (Chapter c : book.chapters) if (c.group.equals(g.idString()) || c.group.equals(g.title)) c.group = "";
            }
            case "group_move" -> {
                ChapterGroup g = group(book, QuestObject.parseId(op.getStringOr("id", "")));
                if (g == null) return false;
                int i = book.groups.indexOf(g);
                int j = Math.max(0, Math.min(book.groups.size() - 1, i + op.getIntOr("delta", 0)));
                book.groups.remove(i);
                book.groups.add(j, g);
            }
            case "table_set" -> {
                String key = op.getStringOr("key", "");
                if (!key.matches("[a-z0-9_.-]+")) return false;
                book.rewardTables.put(key, RewardTable.read(key, op.getCompoundOrEmpty("table")));
            }
            case "table_delete" -> {
                if (book.rewardTables.remove(op.getStringOr("key", "")) == null) return false;
            }
            case "image_add" -> {
                Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                if (c == null) return false;
                c.images.add(ChapterImage.read(op.getCompoundOrEmpty("image")));
            }
            case "image_set" -> {
                Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                int idx = op.getIntOr("index", -1);
                if (c == null || idx < 0 || idx >= c.images.size()) return false;
                c.images.set(idx, ChapterImage.read(op.getCompoundOrEmpty("image")));
            }
            case "image_delete" -> {
                Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                int idx = op.getIntOr("index", -1);
                if (c == null || idx < 0 || idx >= c.images.size()) return false;
                c.images.remove(idx);
            }
            case "book_set" -> {
                if (op.contains("data")) {
                    book.readData(op.getCompoundOrEmpty("data"));
                } else {
                    book.title = op.getStringOr("title", book.title);
                    book.defaultShape = op.getStringOr("default_quest_shape", book.defaultShape);
                }
            }
            case "restore" -> restore(book, op);
            default -> {
                if (player != null) player.sendSystemMessage(Component.literal("Unknown editor op " + kind).withStyle(ChatFormatting.RED));
                return false;
            }
        }
        return true;
    }

    private static ChapterGroup group(QuestBook book, long id) {
        for (ChapterGroup g : book.groups) if (g.id == id) return g;
        return null;
    }

    /**
     * Undo: puts back chapters (with their quests and images) exactly as the client saw them before an edit, or removes
     * chapters that did not exist then; optionally the groups, the reward tables and the book settings.
     */
    private static void restore(QuestBook book, CompoundTag op) {
        for (Tag t : op.getListOrEmpty("chapters")) {
            if (!(t instanceof CompoundTag e)) continue;
            long id = QuestObject.parseId(e.getStringOr("id", ""));
            Chapter old = book.chapter(id);
            int index = old != null ? book.chapters.indexOf(old) : e.getIntOr("index", book.chapters.size());
            if (old != null) book.chapters.remove(old);
            if (!e.getBooleanOr("present", true)) continue;
            Chapter c = new Chapter();
            c.book = book;
            CompoundTag ct = e.getCompoundOrEmpty("tag");
            c.filename = ct.getStringOr("filename", "chapter_" + QuestObject.idToString(id).toLowerCase());
            c.read(ct);
            c.id = id;
            // A quest that moved to another chapter since then comes back here only once.
            Set<Long> ids = new HashSet<>();
            for (Quest q : c.quests) ids.add(q.id);
            for (Chapter other : book.chapters) other.quests.removeIf(q -> ids.contains(q.id));
            book.chapters.add(Math.max(0, Math.min(book.chapters.size(), index)), c);
        }
        if (op.contains("groups")) {
            book.groups.clear();
            for (Tag t : op.getListOrEmpty("groups")) if (t instanceof CompoundTag c) book.groups.add(ChapterGroup.read(c));
        }
        if (op.contains("tables")) {
            book.rewardTables.clear();
            CompoundTag tables = op.getCompoundOrEmpty("tables");
            for (String k : tables.keySet()) book.rewardTables.put(k, RewardTable.read(k, tables.getCompoundOrEmpty(k)));
        }
        if (op.contains("data")) book.readData(op.getCompoundOrEmpty("data"));
    }

    /**
     * The undo of an edit, taken before it: a "restore" op with these chapters as they are now (chapters that do not
     * exist yet are marked absent, so the undo removes them), optionally with the groups, the tables and the settings.
     */
    public static CompoundTag restoreOp(QuestBook book, java.util.Collection<Long> chapterIds, boolean groups, boolean tables, boolean data) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "restore");
        ListTag list = new ListTag();
        for (long id : new java.util.LinkedHashSet<>(chapterIds)) {
            CompoundTag e = new CompoundTag();
            e.putString("id", QuestObject.idToString(id));
            Chapter c = book.chapter(id);
            e.putBoolean("present", c != null);
            if (c != null) {
                e.putInt("index", book.chapters.indexOf(c));
                e.put("tag", c.write());
            }
            list.add(e);
        }
        op.put("chapters", list);
        if (groups) {
            ListTag gl = new ListTag();
            for (ChapterGroup g : book.groups) gl.add(g.write());
            op.put("groups", gl);
        }
        if (tables) {
            CompoundTag t = new CompoundTag();
            book.rewardTables.forEach((k, v) -> t.put(k, v.write()));
            op.put("tables", t);
        }
        if (data) op.put("data", book.writeData());
        return op;
    }

    /** What the editor's pickers offer that only the server knows: advancements, structures, loot tables, dimensions. */
    static CompoundTag lists(MinecraftServer server) {
        CompoundTag t = new CompoundTag();
        ListTag adv = new ListTag();
        CompoundTag criteria = new CompoundTag();
        for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
            String id = h.id().toString();
            adv.add(StringTag.valueOf(id));
            ListTag cl = new ListTag();
            for (String c : h.value().criteria().keySet()) cl.add(StringTag.valueOf(c));
            criteria.put(id, cl);
        }
        t.put("advancements", adv);
        t.put("criteria", criteria);
        t.put("structures", ids(server.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElementIds().map(k -> k.identifier().toString()).toList()));
        t.put("structure_tags", ids(server.registryAccess().lookupOrThrow(Registries.STRUCTURE).listTagIds().map(k -> "#" + k.location()).toList()));
        t.put("biome_tags", ids(server.registryAccess().lookupOrThrow(Registries.BIOME).listTagIds().map(k -> "#" + k.location()).toList()));
        List<String> dims = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) dims.add(l.dimension().identifier().toString());
        t.put("dimensions", ids(dims));
        t.put("loot_tables", ids(server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
                .map(ResourceKey::identifier).map(Object::toString).toList()));
        return t;
    }

    private static ListTag ids(List<String> list) {
        ListTag l = new ListTag();
        for (String s : list.stream().sorted().toList()) l.add(StringTag.valueOf(s));
        return l;
    }
}
