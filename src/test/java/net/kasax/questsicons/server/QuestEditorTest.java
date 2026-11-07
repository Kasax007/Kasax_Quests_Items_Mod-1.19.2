package net.kasax.questsicons.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kasax.questsicons.icons.IconLibrary;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The in-game editor's server operations (what its forms and map gestures send), undo, the files, the icon library. */
class QuestEditorTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag op(String kind) {
        CompoundTag t = new CompoundTag();
        t.putString("op", kind);
        return t;
    }

    private static QuestBook book() {
        QuestBook b = new QuestBook();
        Chapter c = new Chapter();
        c.book = b;
        c.id = 0x100;
        c.title = "One";
        c.filename = "one";
        b.chapters.add(c);
        b.rebuild();
        return b;
    }

    private static CompoundTag quest(String id, String title, double x, String... deps) {
        CompoundTag q = new CompoundTag();
        q.putString("id", id);
        q.putString("title", title);
        q.putDouble("x", x);
        q.putDouble("y", 0);
        ListTag tasks = new ListTag();
        CompoundTag task = new CompoundTag();
        task.putString("id", id + "1");
        task.putString("type", "kill");
        task.putString("entity", "minecraft:zombie");
        task.putLong("value", 3);
        tasks.add(task);
        q.put("tasks", tasks);
        if (deps.length > 0) {
            ListTag l = new ListTag();
            for (String d : deps) l.add(StringTag.valueOf(d));
            q.put("dependencies", l);
        }
        return q;
    }

    private static void apply(QuestBook b, CompoundTag op) {
        QuestEditor.applyOne(b, null, op);
        b.rebuild();
    }

    private static CompoundTag set(String chapter, CompoundTag quest) {
        CompoundTag o = op("quest_set");
        o.putString("chapter", chapter);
        o.put("quest", quest);
        return o;
    }

    @Test
    void questsLinksMovesAndBatches() {
        QuestBook b = book();
        String ch = QuestObject.idToString(0x100);
        apply(b, set(ch, quest("A1", "First", 0)));
        apply(b, set(ch, quest("A2", "Second", 2, "A1")));
        assertEquals(2, b.chapter(0x100).quests.size());
        Quest second = b.quest(QuestObject.parseId("A2"));
        assertTrue(second.dependencies.contains(QuestObject.parseId("A1")));
        assertTrue(second.tasks.getFirst() instanceof Tasks.KillTask k && k.value == 3);
        // Editing keeps the place in the chapter.
        apply(b, set(ch, quest("A1", "First (edited)", 0)));
        assertEquals("First (edited)", b.chapter(0x100).quests.getFirst().title);
        // A batch moves several quests at once.
        CompoundTag batch = op("batch");
        ListTag ops = new ListTag();
        for (String id : List.of("A1", "A2")) {
            CompoundTag m = op("quest_move");
            m.putString("id", id);
            m.putDouble("x", 5);
            m.putDouble("y", 7);
            ops.add(m);
        }
        batch.put("ops", ops);
        apply(b, batch);
        for (Quest q : b.allQuests()) assertEquals(7, q.y);
        // Deleting a quest removes the links to it.
        CompoundTag del = op("quest_delete");
        del.putString("id", "A1");
        apply(b, del);
        assertTrue(b.quest(QuestObject.parseId("A2")).dependencies.isEmpty());
    }

    @Test
    void undoRestoresChaptersGroupsAndTables() {
        QuestBook b = book();
        String ch = QuestObject.idToString(0x100);
        apply(b, set(ch, quest("B1", "Keep me", 0)));
        String before = b.chapter(0x100).write().toString();
        CompoundTag undo = QuestEditor.restoreOp(b, List.of(0x100L, 0x200L), true, true, true);
        // A delete, a new chapter, a group, a table and new settings ...
        CompoundTag del = op("quest_delete");
        del.putString("id", "B1");
        apply(b, del);
        CompoundTag nc = op("chapter_new");
        nc.putString("id", QuestObject.idToString(0x200));
        nc.putString("title", "Two");
        apply(b, nc);
        CompoundTag ng = op("group_new");
        ng.putString("title", "Group");
        apply(b, ng);
        CompoundTag table = op("table_set");
        table.putString("key", "common_crate");
        CompoundTag t = new CompoundTag();
        ListTag entries = new ListTag();
        CompoundTag e = new CompoundTag();
        e.putString("item", "minecraft:emerald");
        e.putInt("count", 3);
        e.putInt("weight", 10);
        entries.add(e);
        t.put("entries", entries);
        table.put("table", t);
        apply(b, table);
        CompoundTag data = op("book_set");
        CompoundTag d = b.writeData();
        d.putString("title", "Changed");
        data.put("data", d);
        apply(b, data);
        assertNull(b.quest(QuestObject.parseId("B1")));
        assertNotNull(b.chapter(0x200));
        assertEquals(1, b.groups.size());
        assertEquals(3, b.rewardTables.get("common_crate").entries.getFirst().count);
        assertEquals("Changed", b.title);
        // ... and one undo puts everything back.
        apply(b, undo);
        assertEquals(before, b.chapter(0x100).write().toString());
        assertNull(b.chapter(0x200));
        assertTrue(b.groups.isEmpty());
        assertTrue(b.rewardTables.isEmpty());
        assertEquals("Quest Book", b.title);
    }

    @Test
    void chapterOrderGroupsAndPictures() {
        QuestBook b = book();
        for (int i = 2; i <= 3; i++) {
            CompoundTag nc = op("chapter_new");
            nc.putString("id", QuestObject.idToString(0x100L * i));
            nc.putString("title", "C" + i);
            apply(b, nc);
        }
        CompoundTag ng = op("group_new");
        ng.putString("id", "G1");
        ng.putString("title", "Group");
        apply(b, ng);
        CompoundTag mv = op("chapter_move");
        mv.putString("id", QuestObject.idToString(0x300));
        mv.putInt("index", 0);
        mv.putString("group", QuestObject.idToString(QuestObject.parseId("G1")));
        apply(b, mv);
        assertEquals(0x300, b.chapters.getFirst().id);
        assertEquals(QuestObject.idToString(QuestObject.parseId("G1")), b.chapters.getFirst().group);
        CompoundTag gd = op("group_delete");
        gd.putString("id", "G1");
        apply(b, gd);
        assertEquals("", b.chapters.getFirst().group);
        // Pictures: add, change, delete.
        CompoundTag ia = op("image_add");
        ia.putString("chapter", QuestObject.idToString(0x100));
        CompoundTag img = new CompoundTag();
        img.putString("image", "questsicons:textures/gui/map/banner_start.png");
        img.putDouble("width", 4);
        ia.put("image", img);
        apply(b, ia);
        CompoundTag is = op("image_set");
        is.putString("chapter", QuestObject.idToString(0x100));
        is.putInt("index", 0);
        CompoundTag img2 = img.copy();
        img2.putDouble("x", 3);
        is.put("image", img2);
        apply(b, is);
        assertEquals(3, b.chapter(0x100).images.getFirst().x);
        CompoundTag idl = op("image_delete");
        idl.putString("chapter", QuestObject.idToString(0x100));
        idl.putInt("index", 0);
        apply(b, idl);
        assertTrue(b.chapter(0x100).images.isEmpty());
    }

    @Test
    void savedFilesLoadTheSameAndDeletedTablesStayDeleted(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        QuestBook b = book();
        String ch = QuestObject.idToString(0x100);
        apply(b, set(ch, quest("C1", "Saved", 1)));
        CompoundTag table = op("table_set");
        table.putString("key", "rare_crate");
        table.put("table", new CompoundTag());
        apply(b, table);
        b.save(dir);
        assertTrue(Files.exists(dir.resolve("reward_tables/rare_crate.snbt")));
        QuestBook loaded = QuestBook.load(dir);
        assertEquals(b.chapter(0x100).write().toString(), loaded.chapter(0x100).write().toString());
        CompoundTag del = op("table_delete");
        del.putString("key", "rare_crate");
        apply(loaded, del);
        loaded.save(dir);
        assertFalse(Files.exists(dir.resolve("reward_tables/rare_crate.snbt")));
    }

    /** Every icon of the library has its model, item definition, texture and name (what the generator writes). */
    @Test
    void iconLibraryIsComplete() throws Exception {
        JsonObject lang;
        try (var in = getClass().getResourceAsStream("/assets/questsicons/lang/en_us.json")) {
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        int icons = 0;
        for (IconLibrary.Category c : IconLibrary.CATEGORIES) {
            assertTrue(lang.has("questsicons.icons.category." + c.id()), c.id());
            for (IconLibrary.Icon i : c.icons()) {
                icons++;
                assertNotNull(getClass().getResource("/assets/questsicons/models/item/" + i.id() + ".json"), i.id());
                assertNotNull(getClass().getResource("/assets/questsicons/items/" + i.id() + ".json"), i.id());
                assertTrue(lang.has("item.questsicons." + i.id()), i.id());
                String tex = c.id().equals("classic") ? "/assets/questsicons/textures/item/" + i.id() + ".png"
                        : "/assets/questsicons/textures/item/icons/" + c.id() + "/" + i.id() + ".png";
                assertNotNull(getClass().getResource(tex), tex);
            }
        }
        assertTrue(icons >= 400, "icons: " + icons);
        for (String old : List.of("chapter_lock", "end_lock", "quest_backpack", "quest_crown", "quest_dungeon", "quest_key", "quest_portal_one",
                "quest_portal_two", "quest_portal_three", "quest_portal_four", "quest_portal_five", "logo_fabric", "icon_compass", "icon_sword",
                "icon_shield", "icon_pickaxe", "icon_magic", "icon_gear", "icon_house", "icon_food", "icon_map", "icon_star", "icon_trophy",
                "icon_skull", "icon_heart", "icon_question", "icon_exclamation", "icon_raft", "icon_potion", "icon_chest", "icon_tree", "icon_moon")) {
            assertTrue(IconLibrary.ICONS.containsKey(old), "the 1.2 icon " + old + " must keep its id");
        }
        for (IconLibrary.MapImage m : IconLibrary.MAP_IMAGES) {
            assertNotNull(getClass().getResource("/assets/questsicons/textures/gui/map/" + m.id() + ".png"), m.id());
            assertTrue(lang.has("questsicons.map_image." + m.id()), m.id());
        }
        assertNotNull(getClass().getResource("/data/questsicons/tags/item/icons/letters.json"));
    }
}
