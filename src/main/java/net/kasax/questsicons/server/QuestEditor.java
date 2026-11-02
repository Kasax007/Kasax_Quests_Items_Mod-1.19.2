package net.kasax.questsicons.server;

import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ChapterImage;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies editor operations sent by an operator's client. Every op names the object it touches,
 * the server re-indexes, writes the SNBT files and sends the new book to everyone.
 *
 * <p>Ops: {@code quest_set} (chapter, quest), {@code quest_delete} (id), {@code quest_move} (id, x, y),
 * {@code chapter_set} (chapter: properties without quests), {@code chapter_new} (title),
 * {@code chapter_delete} (id), {@code chapter_move} (id, delta), {@code image_add} (chapter, image),
 * {@code image_delete} (chapter, index), {@code book_set} (title, ...), {@code reload}.
 */
public final class QuestEditor {
    private QuestEditor() {
    }

    public static void apply(QuestServer qs, ServerPlayer player, CompoundTag op) {
        if (!QuestServer.canEdit(player)) {
            player.sendSystemMessage(Component.translatable("questsicons.edit.denied").withStyle(ChatFormatting.RED));
            return;
        }
        QuestBook book = qs.book;
        String kind = op.getStringOr("op", "");
        try {
            switch (kind) {
                case "reload" -> {
                    qs.load();
                    player.sendSystemMessage(Component.translatable("questsicons.command.reloaded", book.chapters.size()));
                    return;
                }
                case "quest_set" -> {
                    Chapter chapter = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                    if (chapter == null) return;
                    CompoundTag qt = op.getCompoundOrEmpty("quest");
                    Quest q = new Quest();
                    q.chapter = chapter;
                    q.read(qt);
                    if (q.id == 0) q.id = book.newId();
                    Quest old = book.quest(q.id);
                    if (old != null) {
                        int idx = old.chapter.quests.indexOf(old);
                        old.chapter.quests.remove(old);
                        if (old.chapter == chapter) {
                            chapter.quests.add(Math.max(0, idx), q);
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
                    if (q == null) return;
                    q.chapter.quests.remove(q);
                    for (Quest other : book.allQuests()) other.dependencies.rem(id);
                }
                case "quest_move" -> {
                    Quest q = book.quest(QuestObject.parseId(op.getStringOr("id", "")));
                    if (q == null) return;
                    q.x = op.getDoubleOr("x", q.x);
                    q.y = op.getDoubleOr("y", q.y);
                    if (op.contains("chapter")) {
                        Chapter target = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                        if (target != null && target != q.chapter) {
                            q.chapter.quests.remove(q);
                            target.quests.add(q);
                        }
                    }
                }
                case "dependency" -> {
                    Quest q = book.quest(QuestObject.parseId(op.getStringOr("id", "")));
                    long dep = QuestObject.parseId(op.getStringOr("dep", ""));
                    if (q == null || book.quest(dep) == null || dep == q.id) return;
                    if (op.getBooleanOr("remove", false)) q.dependencies.rem(dep);
                    else if (!q.dependencies.contains(dep)) q.dependencies.add(dep);
                }
                case "chapter_set" -> {
                    CompoundTag ct = op.getCompoundOrEmpty("chapter");
                    Chapter c = book.chapter(QuestObject.parseId(ct.getStringOr("id", "")));
                    if (c == null) return;
                    var quests = new java.util.ArrayList<>(c.quests);
                    var images = new java.util.ArrayList<>(c.images);
                    String filename = c.filename;
                    c.read(ct);
                    c.filename = filename;
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
                    c.id = book.newId();
                    c.title = op.getStringOr("title", "New Chapter");
                    c.filename = c.title.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
                    if (c.filename.isEmpty() || book.chapters.stream().anyMatch(x -> x.filename.equals(c.filename))) {
                        c.filename = "chapter_" + c.idString().toLowerCase();
                    }
                    c.orderIndex = book.chapters.stream().mapToInt(x -> x.orderIndex).max().orElse(-1) + 1;
                    book.chapters.add(c);
                }
                case "chapter_delete" -> {
                    Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("id", "")));
                    if (c == null) return;
                    book.chapters.remove(c);
                }
                case "chapter_move" -> {
                    Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("id", "")));
                    if (c == null) return;
                    int i = book.chapters.indexOf(c);
                    int j = Math.max(0, Math.min(book.chapters.size() - 1, i + op.getIntOr("delta", 0)));
                    book.chapters.remove(i);
                    book.chapters.add(j, c);
                    for (int k = 0; k < book.chapters.size(); k++) book.chapters.get(k).orderIndex = k;
                }
                case "image_add" -> {
                    Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                    if (c == null) return;
                    c.images.add(ChapterImage.read(op.getCompoundOrEmpty("image")));
                }
                case "image_delete" -> {
                    Chapter c = book.chapter(QuestObject.parseId(op.getStringOr("chapter", "")));
                    int idx = op.getIntOr("index", -1);
                    if (c == null || idx < 0 || idx >= c.images.size()) return;
                    c.images.remove(idx);
                }
                case "book_set" -> {
                    book.title = op.getStringOr("title", book.title);
                    book.defaultShape = op.getStringOr("default_quest_shape", book.defaultShape);
                }
                default -> {
                    player.sendSystemMessage(Component.literal("Unknown editor op " + kind).withStyle(ChatFormatting.RED));
                    return;
                }
            }
        } catch (Exception e) {
            QuestsIcons.LOGGER.error("Editor op {} failed", kind, e);
            player.sendSystemMessage(Component.literal("Edit failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return;
        }
        qs.bookChanged(true);
    }
}
