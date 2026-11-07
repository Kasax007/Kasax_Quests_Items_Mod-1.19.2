package net.kasax.questsicons.importer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import net.kasax.questsicons.importer.ImportReport.Kind;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ChapterImage;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.RewardTable;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.quest.task.Task;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FTB Quests importer on a small book written by hand in all three FTB layouts (1.20 SNBT with inline texts,
 * 1.21 SNBT with a lang file, 26.1 JSON5 with split lang files), and on real modpack quest folders if they are given.
 */
class FtbImporterTest {
    private static FtbImporter.Options options;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        options = new FtbImporter.Options();
        options.fixer = (stack, from) -> (CompoundTag) DataFixers.getDataFixer()
                .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, stack), from, current).getValue();
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(FtbImporterTest.class.getResource("/ftbquests/" + name + "/chapters").toURI()).getParent();
    }

    private static long id(String hex) {
        return QuestObject.parseId(hex);
    }

    private static Quest quest(QuestBook b, String hex) {
        Quest q = b.quest(id(hex));
        assertNotNull(q, "quest " + hex);
        return q;
    }

    private static <T> T task(QuestBook b, String hex, Class<T> type) {
        Task t = b.task(id(hex));
        assertNotNull(t, "task " + hex);
        return assertInstanceOf(type, t);
    }

    private static <T> T reward(QuestBook b, long id, Class<T> type) {
        Reward r = b.reward(id);
        assertNotNull(r, "reward " + QuestObject.idToString(id));
        return assertInstanceOf(type, r);
    }

    private static long derived(String hex, int n) {
        return QuestObject.hashId(hex + "/" + n);
    }

    /** Converts, saves in Quest Icons' format, loads that again: the book a server would really use. */
    private static FtbImporter.Result roundTrip(Path folder, Path out) throws IOException {
        FtbFolder f = FtbFolder.load(FtbFolder.locate(folder));
        FtbImporter.Result r = FtbImporter.convert(f, options);
        r.book().save(out);
        QuestBook back = QuestBook.load(out);
        return new FtbImporter.Result(back, r.report(), r.source());
    }

    private static Path out(String name) throws IOException {
        Path p = Path.of(System.getProperty("qi.testOut", "build/test-import")).resolve(name);
        if (Files.exists(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path x : s.sorted((a, c) -> c.getNameCount() - a.getNameCount()).toList()) Files.delete(x);
            }
        }
        return p;
    }

    // ------------------------------------------------------------------------------------------- readers

    @Test
    void snbtReaderFollowsFtbRules() {
        CompoundTag t = FtbSnbtReader.parse("""
                # comment line
                {
                	a: 1
                	b: 2L, c: 1.5d
                	d: 3.0
                	e: true
                	"f g": "x\\ny\\q"
                	h: [I; 1, 2, 3]
                	i: [{ k: "v" } { k: "w" }]
                	big: 12345678901
                	n: null
                }
                """);
        assertEquals(1, t.getIntOr("a", 0));
        assertEquals(2L, t.getLongOr("b", 0));
        assertEquals(1.5, t.getDoubleOr("c", 0));
        assertEquals(3.0, t.getDoubleOr("d", 0));
        assertTrue(t.getBooleanOr("e", false));
        assertEquals("x\ny", t.getStringOr("f g", ""), "an unknown escape drops the character, like FTB");
        assertEquals(3, t.getIntArray("h").orElseThrow().length);
        assertEquals(2, t.getListOrEmpty("i").size());
        assertEquals(12345678901.0, t.getDoubleOr("big", 0), "a plain number that is no int is a double in FTB's SNBT");
        assertFalse(t.contains("n"));
    }

    @Test
    void json5ReaderReadsEverything() {
        CompoundTag t = Json5Reader.parse("""
                // comment
                {
                  unquoted: 'single',
                  "quoted": "a\\u0041\\x42",
                  hex: 0x10, long: 4611686018427387905, neg: -.5, plus: +3,
                  list: [1, 2, ], /* block */
                  obj: { nested: true, },
                  nothing: null,
                }
                """);
        assertEquals("single", t.getStringOr("unquoted", ""));
        assertEquals("aAB", t.getStringOr("quoted", ""));
        assertEquals(16, t.getIntOr("hex", 0));
        assertEquals(4611686018427387905L, t.getLongOr("long", 0), "64-bit ids stay exact");
        assertEquals(-0.5, t.getDoubleOr("neg", 0));
        assertEquals(3, t.getIntOr("plus", 0));
        assertEquals(2, t.getListOrEmpty("list").size());
        assertTrue(t.getCompoundOrEmpty("obj").getBooleanOr("nested", false));
        assertFalse(t.contains("nothing"));
    }

    @Test
    void filterExpressions() {
        List<FtbItems.Node> n = FtbItems.parseFilters("item(a:b)or(item_tag(c:d) mod(e))");
        assertEquals(2, n.size());
        assertEquals("or", n.get(1).type());
        assertEquals(2, n.get(1).children().size());
        assertEquals("c:d", n.get(1).children().get(0).arg());
    }

    @Test
    void textures() {
        assertEquals("minecraft:textures/item/diamond.png", FtbImporter.texture("minecraft:item/diamond"));
        assertEquals("atm:textures/questpics/a.png", FtbImporter.texture("atm:textures/questpics/a.png"));
        assertEquals(null, FtbImporter.texture("https://example.com/a.png"));
    }

    // ------------------------------------------------------------------------------------------- the fixture book

    @ParameterizedTest
    @ValueSource(strings = {"inline", "lang", "json5"})
    void fixtureBook(String layout) throws Exception {
        FtbImporter.Result r = roundTrip(fixture(layout), out("fixture-" + layout));
        QuestBook b = r.book();
        ImportReport rep = r.report();
        assertEquals(layout.equals("json5"), r.source().isJson5());

        // Book, groups, chapters in FTB's order (ungrouped first, then the groups).
        assertEquals("Fixture Book", b.title);
        assertEquals("questsicons:quest_book", b.icon.idString());
        assertEquals("rsquare", b.defaultShape);
        assertEquals(1, b.groups.size());
        assertEquals("Basics Group", b.groups.getFirst().title);
        assertEquals(id("1000000000000001"), b.groups.getFirst().id);
        assertEquals(List.of("secret", "basics"), b.chapters.stream().map(c -> c.filename).toList());
        Chapter secret = b.chapters.get(0);
        Chapter basics = b.chapters.get(1);
        assertEquals(id("2000000000000001"), basics.id);
        assertEquals("&6Basics", basics.title);
        assertEquals(List.of("First steps"), basics.subtitle);
        assertEquals("1000000000000001", basics.group);
        assertEquals("minecraft:oak_log", basics.icon.idString());
        assertTrue(secret.alwaysInvisible);
        assertEquals("none", secret.defaultQuestShape);
        assertTrue(secret.icon.isEmpty(), "a custom texture icon has no item form");
        assertEquals(4, basics.quests.size());
        assertEquals(5, b.questCount());

        // The picture shows once the first quest is done.
        assertEquals(1, basics.images.size());
        ChapterImage img = basics.images.getFirst();
        assertEquals("minecraft:textures/item/diamond.png", img.image);
        assertEquals(2.0, img.x);
        assertEquals(-2.0, img.y);
        assertEquals(id("3000000000000001"), img.dependency);
        assertEquals("Shiny", img.hover);

        // Quest 1: texts, shape, an item filter task, xp and an item with a random bonus.
        Quest q1 = quest(b, "3000000000000001");
        assertEquals("Getting Wood", q1.title);
        assertEquals("Punch trees", q1.subtitle);
        assertEquals(List.of("Collect logs.", "", "&aGood luck!", "Second line"), q1.description);
        assertEquals("diamond", q1.shape);
        assertEquals(1.5, q1.size);
        Tasks.ItemTask logs = task(b, "6000000000000001", Tasks.ItemTask.class);
        assertEquals("#minecraft:logs", logs.item.idString());
        assertEquals(8, logs.count);
        assertFalse(logs.consume);
        assertEquals(50, reward(b, id("5000000000000001"), Rewards.XpReward.class).xp);
        Rewards.ItemReward apples = reward(b, id("5000000000000002"), Rewards.ItemReward.class);
        assertEquals("minecraft:apple", apples.item.idString());
        assertEquals(4, apples.count);
        assertEquals(2, apples.randomBonus);

        // Quest 2: "one of" dependency, enchanted item with exact data, optional checkmark, table rewards.
        Quest q2 = quest(b, "3000000000000002");
        assertEquals(List.of(id("3000000000000001")), List.copyOf(q2.dependencies));
        assertTrue(q2.dependsOnAny);
        Tasks.ItemTask sword = task(b, "6000000000000002", Tasks.ItemTask.class);
        assertEquals("minecraft:diamond_sword", sword.item.idString());
        CompoundTag comps = ((CompoundTag) sword.item.write()).getCompoundOrEmpty("components");
        assertEquals(5, comps.getCompoundOrEmpty("minecraft:enchantments").getIntOr("minecraft:sharpness", 0),
                "1.20 NBT and the 1.21 component are both upgraded: " + sword.item);
        assertTrue(sword.matchComponents);
        assertFalse(sword.fuzzyMatch);
        assertTrue(sword.consume);
        Tasks.CheckmarkTask hi = task(b, "6000000000000003", Tasks.CheckmarkTask.class);
        assertTrue(hi.optional);
        assertEquals("Say hi", hi.title);
        Rewards.CommandReward cmd = reward(b, id("5000000000000003"), Rewards.CommandReward.class);
        assertEquals("/say {p} did it", cmd.command);
        assertTrue(cmd.elevatePerms);
        assertEquals(layout.equals("inline") ? 2 : 3, cmd.permissionLevel);
        assertFalse(cmd.silent);
        Rewards.ChoiceReward choice = reward(b, id("5000000000000004"), Rewards.ChoiceReward.class);
        assertEquals(List.of("minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot"), choice.items.stream().map(i -> i.idString()).toList());
        assertEquals(3, choice.items.get(1).count());
        // Loot size 2 becomes two random rewards; the weight-0 entry is always given.
        assertEquals("loot", reward(b, id("5000000000000005"), Rewards.RandomReward.class).table);
        assertEquals("loot", reward(b, derived("5000000000000005", 1), Rewards.RandomReward.class).table);
        assertEquals("minecraft:gold_ingot", reward(b, derived("5000000000000005", 2), Rewards.ItemReward.class).item.idString());
        assertEquals(5, q2.rewards.size());

        // Quest 3: visibility flags and every task type Quest Icons has.
        Quest q3 = quest(b, "3000000000000003");
        assertTrue(q3.hideUntilDepsComplete);
        assertTrue(q3.invisible);
        assertTrue(q3.optional);
        assertEquals(9, q3.tasks.size());
        Tasks.KillTask kill = task(b, "6000000000000004", Tasks.KillTask.class);
        assertEquals("minecraft:zombie", kill.entity);
        assertEquals(5, kill.value);
        assertEquals("minecraft:story/mine_stone", task(b, "6000000000000005", Tasks.AdvancementTask.class).advancement);
        assertEquals("minecraft:the_nether", task(b, "6000000000000006", Tasks.DimensionTask.class).dimension);
        assertEquals("#minecraft:is_forest", task(b, "6000000000000007", Tasks.BiomeTask.class).biome);
        assertEquals("minecraft:village_plains", task(b, "6000000000000008", Tasks.StructureTask.class).structure);
        Tasks.XpTask xp = task(b, "6000000000000009", Tasks.XpTask.class);
        assertEquals(5, xp.value);
        assertFalse(xp.points);
        assertTrue(xp.consume, "FTB takes the levels when the task is submitted");
        Tasks.StatTask stat = task(b, "600000000000000A", Tasks.StatTask.class);
        assertEquals("minecraft:jump", stat.stat);
        assertEquals(10, stat.value);
        Tasks.ObservationTask obs = task(b, "600000000000000B", Tasks.ObservationTask.class);
        assertEquals("#minecraft:logs", obs.observe);
        assertFalse(obs.entity);
        assertEquals(40, obs.timer);
        Tasks.LocationTask loc = task(b, "600000000000000C", Tasks.LocationTask.class);
        assertEquals("minecraft:overworld", loc.dimension);
        assertEquals(List.of(12, 65, -3, 2), List.of(loc.x, loc.y, loc.z, loc.radius));
        assertEquals(3, reward(b, id("5000000000000006"), Rewards.XpLevelsReward.class).levels);
        // "loot" with an empty weight: inline entries plus "nothing".
        Rewards.RandomReward junk = reward(b, id("5000000000000007"), Rewards.RandomReward.class);
        assertEquals(2, junk.inline.size());
        assertEquals("minecraft:stick", junk.inline.get(0).item.idString());
        assertTrue(junk.inline.get(1).item.isEmpty());
        assertEquals(junk.inline.get(0).weight, junk.inline.get(1).weight);
        // "all of table" is split into one reward per entry (the xp entry too).
        assertEquals("minecraft:diamond", reward(b, id("5000000000000008"), Rewards.ItemReward.class).item.idString());
        assertEquals(3, reward(b, derived("5000000000000008", 1), Rewards.ItemReward.class).count);
        assertEquals(10, reward(b, derived("5000000000000008", 2), Rewards.XpReward.class).xp);
        assertEquals("minecraft:gold_ingot", reward(b, derived("5000000000000008", 3), Rewards.ItemReward.class).item.idString());
        assertEquals("minecraft:story/root", reward(b, id("5000000000000009"), Rewards.AdvancementReward.class).advancement);
        assertEquals(7, q3.rewards.size());

        // Quest 4: tasks Quest Icons does not have become checkmarks; the money reward is skipped.
        Quest q4 = quest(b, "3000000000000004");
        assertEquals(3, q4.tasks.size());
        for (Task t : q4.tasks) assertInstanceOf(Tasks.CheckmarkTask.class, t);
        assertEquals("Deliver 1,000 mB of Water", task(b, "600000000000000D", Tasks.CheckmarkTask.class).title);
        assertEquals("Deliver 10,000 energy", task(b, "600000000000000E", Tasks.CheckmarkTask.class).title);
        assertTrue(q4.rewards.isEmpty());

        // Quest 5: a dependency on a whole chapter means all of its quests; 1.20's "grass" is "short_grass" now.
        Quest q5 = quest(b, "3000000000000005");
        assertEquals(4, q5.dependencies.size());
        assertEquals("minecraft:short_grass", task(b, "6000000000000010", Tasks.ItemTask.class).item.idString());

        // Reward tables keep their file name; FTB's decimal weights are scaled, ratios stay.
        RewardTable loot = b.rewardTables.get("loot");
        assertNotNull(loot);
        assertEquals("Loot Table", loot.title);
        assertEquals(2, loot.entries.size());
        assertEquals(2.5, loot.entries.get(1).weight / (double) loot.entries.get(0).weight, 1e-9);
        assertEquals(3, loot.entries.get(1).count);
        assertNotNull(b.rewardTables.get("junk"));

        // The report lists everything that is not exact.
        assertEquals(3, rep.count(Kind.CHECKMARK));
        assertEquals(1, rep.count(Kind.SKIPPED, "quest link"));
        assertEquals(1, rep.count(Kind.SKIPPED, "toast reward (a pop-up message)"));
        assertEquals(1, rep.count(Kind.SKIPPED, "game stage reward"));
        assertEquals(1, rep.count(Kind.SKIPPED, "custom (KubeJS) reward"));
        assertEquals(1, rep.count(Kind.SKIPPED, "reward type ftbmoney:money"));
        assertEquals(1, rep.count(Kind.SKIPPED, "reward table entry of type xp"));
        assertEquals(1, rep.count(Kind.APPROXIMATED, "dependency on a whole chapter"));
        assertEquals(1, rep.count(Kind.APPROXIMATED, "location box"));
        assertEquals(1, rep.count(Kind.APPROXIMATED, "custom texture icon"));
        assertEquals(5, rep.source("Quests"));
        assertEquals(16, rep.source("Tasks"));
        assertEquals(13, rep.source("Rewards"));
        assertEquals(16, rep.result("Tasks"));
        assertEquals(14, rep.result("Rewards"));
        assertTrue(rep.toText().contains("Tasks that became a checkmark task"));
    }

    @Test
    void reimportKeepsIds() throws Exception {
        Path out = out("reimport");
        FtbImporter.Result first = roundTrip(fixture("lang"), out);
        FtbImporter.Result second = roundTrip(fixture("lang"), out);
        List<Long> a = new ArrayList<>();
        List<Long> c = new ArrayList<>();
        for (Quest q : first.book().allQuests()) a.add(q.id);
        for (Quest q : second.book().allQuests()) c.add(q.id);
        a.sort(null);
        c.sort(null);
        assertEquals(a, c);
        assertEquals(first.book().writeAll(), second.book().writeAll(), "importing twice gives the same book");
    }

    @Test
    void otherLanguage() throws Exception {
        FtbImporter.Options o = new FtbImporter.Options();
        o.fixer = options.fixer;
        o.locale = "de_de";
        FtbImporter.Result r = FtbImporter.convert(FtbFolder.load(fixture("lang")), o);
        assertEquals("Holz sammeln", r.book().quest(id("3000000000000001")).title);
        assertEquals("Tools", r.book().quest(id("3000000000000002")).title, "missing texts come from the fallback language");
    }

    // ------------------------------------------------------------------------------------------- real modpacks

    /**
     * Real FTB Quests folders (-PftbSamples=dir, one folder per pack). Expected counts come from an independent reader
     * ({@code <pack>.stats.json} next to the folder); the book is written, loaded again and must keep every quest.
     */
    @TestFactory
    Stream<DynamicTest> realModpacks() throws IOException {
        String dir = System.getProperty("qi.ftbSamples", "");
        if (dir.isEmpty() || !Files.isDirectory(Path.of(dir))) return Stream.empty();
        List<DynamicTest> tests = new ArrayList<>();
        try (Stream<Path> s = Files.list(Path.of(dir))) {
            for (Path pack : s.filter(Files::isDirectory).sorted().toList()) {
                tests.add(DynamicTest.dynamicTest(pack.getFileName().toString(), () -> realPack(pack)));
            }
        }
        return tests.stream();
    }

    private static void realPack(Path pack) throws Exception {
        String name = pack.getFileName().toString();
        long t0 = System.currentTimeMillis();
        FtbImporter.Result r = roundTrip(pack, out("pack-" + name));
        long ms = System.currentTimeMillis() - t0;
        QuestBook b = r.book();
        ImportReport rep = r.report();
        Files.writeString(out("pack-" + name).resolveSibling(name + "-report.txt"), rep.toText());
        int tasks = 0;
        int rewards = 0;
        int images = 0;
        for (Chapter c : b.chapters) {
            images += c.images.size();
            for (Quest q : c.quests) {
                tasks += q.tasks.size();
                rewards += q.rewards.size();
                for (long dep : q.dependencies) assertNotNull(b.quest(dep), "dependency " + QuestObject.idToString(dep) + " of " + q.idString());
            }
        }
        Path stats = pack.resolveSibling(name + ".stats.json");
        if (Files.exists(stats)) {
            JsonObject e = JsonParser.parseString(Files.readString(stats)).getAsJsonObject();
            assertEquals(e.get("groups").getAsInt(), rep.source("Chapter groups"), "groups");
            assertEquals(e.get("chapters").getAsInt(), rep.source("Chapters"), "chapters");
            assertEquals(e.get("quests").getAsInt(), rep.source("Quests"), "quests");
            assertEquals(e.get("tasks").getAsInt(), rep.source("Tasks"), "tasks");
            assertEquals(e.get("rewards").getAsInt(), rep.source("Rewards"), "rewards");
            assertEquals(e.get("reward_tables").getAsInt(), rep.source("Reward tables"), "reward tables");
            assertEquals(e.get("quest_links").getAsInt(), rep.source("Quest links"), "quest links");
            assertEquals(e.get("images").getAsInt(), rep.source("Chapter images"), "images");
            // Every quest of the FTB book is in the written book, with its FTB id.
            assertEquals(e.get("quests").getAsInt(), b.questCount(), "quests after writing and loading");
            for (var qid : e.getAsJsonArray("first_quest_ids")) assertNotNull(b.quest(id(qid.getAsString())), "quest " + qid);
            // Every task is still there (unsupported ones as checkmarks).
            assertEquals(e.get("tasks").getAsInt(), tasks, "tasks after writing and loading");
            for (Map.Entry<String, com.google.gson.JsonElement> t : e.getAsJsonObject("task_types").entrySet()) {
                assertEquals(t.getValue().getAsInt(), rep.sourceTaskTypes.getOrDefault(t.getKey(), 0), "task type " + t.getKey());
            }
        }
        assertEquals(rep.source("Chapters"), b.chapters.size());
        assertEquals(rep.result("Tasks"), tasks);
        assertEquals(rep.result("Rewards"), rewards);
        assertEquals(rep.result("Chapter images"), images);
        assertEquals(0, rep.count(Kind.ERROR), "all files readable");
        int size = QuestNet.compress(b.writeAll()).length;
        assertTrue(size < 16 * 1024 * 1024, "the book fits the large book payload");
        System.out.printf("[import] %-8s %s: %d groups, %d chapters, %d quests, %d tasks (%d checkmarks), %d rewards, %d tables, %d images;"
                        + " skipped %d, approximated %d; book %d KiB; %d ms%n", name, r.source().isJson5() ? "json5" : "snbt",
                b.groups.size(), b.chapters.size(), b.questCount(), tasks, rep.count(Kind.CHECKMARK), rewards, b.rewardTables.size(), images,
                rep.count(Kind.SKIPPED), rep.count(Kind.APPROXIMATED), size / 1024, ms);
    }
}
