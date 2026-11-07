package net.kasax.questsicons.importer;

import net.kasax.questsicons.importer.FtbItems.Use;
import net.kasax.questsicons.importer.ImportReport.Kind;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ChapterGroup;
import net.kasax.questsicons.quest.ChapterImage;
import net.kasax.questsicons.quest.ItemSpec;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.QuestShape;
import net.kasax.questsicons.quest.RewardTable;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.quest.task.Task;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts an FTB Quests book ({@link FtbFolder}) into a Quest Icons {@link QuestBook}. Everything Quest Icons can
 * express is mapped; whatever it cannot is turned into a checkmark task, approximated or skipped, and each case goes
 * into the {@link ImportReport}. FTB's ids (16 hex digits) are kept for chapters, groups, quests, tasks and rewards, so
 * importing the same book again updates it instead of duplicating it, and progress made after the first import stays.
 */
public final class FtbImporter {
    /** The FTB book's own id, used in its lang keys ("file.0000000000000001.title"). */
    private static final String FILE_ID = "0000000000000001";

    /** Upgrades an item stack from an older Minecraft data version (the vanilla data fixer in game). */
    public interface ItemFixer {
        CompoundTag fix(CompoundTag stack, int fromDataVersion);
    }

    /** What exists in the running game, to find ids of mods that are missing (null in tests without a game). */
    public interface GameData {
        boolean item(String id);

        boolean itemTag(String id);

        boolean entity(String id);

        boolean entityTag(String id);

        boolean advancement(String id);

        boolean dimension(String id);

        boolean biome(String id);

        boolean biomeTag(String id);

        boolean structure(String id);

        boolean structureTag(String id);

        boolean block(String id);

        boolean blockTag(String id);

        boolean stat(String id);

        boolean lootTable(String id);

        List<String> itemIds();

        /** Is a mod (or Minecraft itself) with this namespace installed? */
        boolean namespace(String namespace);
    }

    public static final class Options {
        public String locale = "en_us";
        public @Nullable ItemFixer fixer;
        public @Nullable GameData game;
    }

    public record Result(QuestBook book, ImportReport report, FtbFolder source) {
    }

    // ------------------------------------------------------------------------------------------- state

    private final FtbFolder src;
    private final Options opt;
    private final ImportReport report = new ImportReport();
    private final QuestBook book = new QuestBook();
    private final FtbFolder.Texts texts;
    private final FtbItems items;

    private boolean defaultTeam;
    private final Map<String, Boolean> teamByType = new HashMap<>();
    private String defaultAutoclaim = "disabled";
    private boolean defaultConsume;
    private String bookPreset = "";
    private final Map<String, double[]> presetSize = new HashMap<>();
    private final Map<String, String> presetShape = new HashMap<>();

    private final Map<Long, Table> tables = new LinkedHashMap<>();
    private final Map<Long, Set<Long>> chapterQuests = new HashMap<>();
    private final Set<Long> questIds = new HashSet<>();
    private final Set<Long> groupIds = new HashSet<>();
    private final Map<String, String> refWhere = new HashMap<>();
    private final Map<String, Integer> refCount = new HashMap<>();

    /** A converted reward table plus what is needed to expand FTB's table rewards. */
    private static final class Table {
        long id;
        String key;
        String title = "";
        RewardTable qi;
        int lootSize = 1;
        float emptyWeight;
        int weightScale = 1;
        /** Every entry: the FTB reward compound and its weight. */
        final List<CompoundTag> rewards = new ArrayList<>();
        final List<Float> weights = new ArrayList<>();
        @Nullable String crate;
    }

    private FtbImporter(FtbFolder src, Options opt) {
        this.src = src;
        this.opt = opt;
        String fallback = src.data.getStringOr("fallback_locale", "en_us");
        String locale = opt.locale;
        if (!src.lang.containsKey(locale) && !src.lang.isEmpty() && !src.lang.containsKey(fallback)) locale = src.locales().get(0);
        this.texts = new FtbFolder.Texts(src, locale, fallback);
        this.items = new FtbItems(opt, report, src.isJson5());
        report.header.add("Texts: " + src.textLayout + (src.lang.isEmpty() ? "" : ", language " + locale + " (fallback " + fallback
                + "); available: " + String.join(", ", src.locales())));
    }

    public static Result convert(FtbFolder src, Options opt) {
        return new FtbImporter(src, opt).run();
    }

    private Result run() {
        for (String e : src.errors) report.add(Kind.ERROR, "unreadable file", e, "the file was left out (FTB Quests leaves it out too)");
        readBookSettings();
        readGroups();
        readTables();
        indexQuests();
        List<FtbFolder.SourceFile> ordered = orderChapters();
        int order = 0;
        for (FtbFolder.SourceFile f : ordered) convertChapter(f, order++);
        countSource();
        book.rebuild();
        checkIds();
        countResult();
        report.add(Kind.NOTE, "progress is not imported", "", "FTB Quests keeps progress in the world save in its own format; players start the imported book fresh (operators can use /quests complete)");
        return new Result(book, report, src);
    }

    // ------------------------------------------------------------------------------------------- texts

    private static final Pattern UNICODE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    /** FTB unescapes "\\u2022" in texts and reads a literal "\n" as a line break. */
    static String clean(@Nullable String s) {
        if (s == null) return "";
        if (s.contains("\\u")) {
            Matcher m = UNICODE.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
            m.appendTail(sb);
            s = sb.toString();
        }
        return s;
    }

    private static boolean isJson(String s) {
        String t = s.trim();
        return t.startsWith("[") && t.endsWith("]") || t.startsWith("{\"") && t.endsWith("}");
    }

    /** A one-line text (title, subtitle). */
    private String title(String type, long id, @Nullable CompoundTag inline, String key, String inlineKey) {
        String v = texts.string(type + "." + QuestObject.idToString(id) + "." + key);
        if (v == null && inline != null && inline.get(inlineKey) instanceof StringTag st) v = st.value();
        v = clean(v);
        if (!isJson(v)) v = v.replace("\\n", " ").replace("\n", " ");
        return v;
    }

    /** A multi-line text (quest description, chapter subtitle), one entry per line. */
    private List<String> lines(String type, long id, @Nullable CompoundTag inline, String key, String inlineKey) {
        List<String> raw = texts.list(type + "." + QuestObject.idToString(id) + "." + key);
        if (raw == null && inline != null) {
            Tag t = inline.get(inlineKey);
            if (t instanceof ListTag l) {
                raw = new ArrayList<>();
                for (Tag e : l) raw.add(e instanceof StringTag s ? s.value() : e.toString());
            } else if (t instanceof StringTag s) {
                raw = new ArrayList<>(List.of(s.value()));
            }
        }
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw) {
            String l = clean(line);
            if (l.trim().equals("{@pagebreak}")) {
                out.add("");
                continue;
            }
            if (isJson(l)) {
                out.add(l);
                continue;
            }
            for (String part : l.replace("\r", "").replace("\\n", "\n").split("\n", -1)) out.add(part);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- ids

    /** FTB ids: 16 hex digits as a string (sometimes "#" first), a number in very old books. */
    private static long id(@Nullable Tag t) {
        if (t instanceof NumericTag n) return n.longValue();
        if (t instanceof StringTag s) {
            String v = s.value().trim();
            if (v.startsWith("#")) v = v.substring(1);
            if (v.isEmpty() || v.equals("-")) return 0L;
            try {
                return Long.parseUnsignedLong(v, 16);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    private static long stableId(long ftbId, String fallbackKey) {
        return ftbId != 0 ? ftbId : QuestObject.hashId("ftbquests/" + fallbackKey);
    }

    private static String hex(long id) {
        return QuestObject.idToString(id);
    }

    // ------------------------------------------------------------------------------------------- book

    private void readBookSettings() {
        CompoundTag d = src.data;
        String t = title("file", 1L, d, "title", "title");
        book.title = t.isEmpty() ? "Quest Book" : t;
        FtbItems.Out icon = items.convert(d.get("icon"), "book icon", Use.ICON, true);
        book.icon = icon.empty() ? ItemSpec.of("questsicons:quest_book") : new ItemSpec(icon.spec);
        book.defaultShape = shape(d.getStringOr("default_quest_shape", "circle"), "book", "circle");
        defaultTeam = FtbItems.bool(d.get("default_reward_team"), false);
        book.defaultTeamReward = defaultTeam;
        CompoundTag byType = d.getCompoundOrEmpty("default_reward_team_by_type");
        for (String k : byType.keySet()) teamByType.put(stripNs(k), FtbItems.bool(byType.get(k), defaultTeam));
        defaultAutoclaim = d.getStringOr("default_autoclaim_rewards", "disabled");
        defaultConsume = FtbItems.bool(d.get("default_consume_items"), false);
        bookPreset = d.getStringOr("preset", "");
        // FTB's built-in visual presets, unless the book defines its own list.
        CompoundTag presets = d.getCompoundOrEmpty("presets");
        if (!d.contains("presets")) {
            presets = new CompoundTag();
            presets.put("normal", preset("square", 1.0));
            presets.put("info", preset("gear", 1.0));
            presets.put("goal", preset("hexagon", 2.0));
        }
        for (String name : presets.keySet()) {
            CompoundTag p = presets.getCompoundOrEmpty(name);
            presetShape.put(name, p.getStringOr("shape", ""));
            presetSize.put(name, new double[]{num(p.get("size"), 0)});
        }
        String progression = d.getStringOr("progression_mode", "linear");
        if (progression.equals("flexible")) {
            report.add(Kind.APPROXIMATED, "flexible progression", "book", "FTB lets players work on locked quests in \"flexible\" mode; in Quest Icons a quest starts when its dependencies are done");
        }
        for (String k : new String[]{"emergency_items", "lock_message", "loot_crate_no_drop"}) {
            if (k.equals("loot_crate_no_drop") && !FtbItems.bool(d.get("drop_loot_crates"), false)) continue;
            if (k.equals("lock_message") && d.getStringOr(k, "").isEmpty()) continue;
            if (k.equals("emergency_items") && d.getListOrEmpty(k).isEmpty()) continue;
            report.add(Kind.SKIPPED, "book setting " + k, "book", "no counterpart in Quest Icons");
        }
        if (FtbItems.bool(d.get("drop_loot_crates"), false)) {
            report.add(Kind.SKIPPED, "loot crates dropped by mobs", "book", "the reward tables are imported, mobs do not drop crates");
        }
    }

    private static CompoundTag preset(String shape, double size) {
        CompoundTag c = new CompoundTag();
        c.putString("shape", shape);
        c.putDouble("size", size);
        return c;
    }

    private static double num(@Nullable Tag t, double def) {
        return t instanceof NumericTag n ? n.doubleValue() : def;
    }

    private static String stripNs(String type) {
        return type.startsWith("ftbquests:") ? type.substring(10) : type;
    }

    private String shape(String ftb, String where, String fallback) {
        if (ftb == null || ftb.isEmpty() || ftb.equals("default")) return fallback;
        QuestShape s = QuestShape.byName(ftb, null);
        if (s == null) {
            report.add(Kind.APPROXIMATED, "unknown quest shape \"" + ftb + "\"", where, "from a resource pack; the default shape is used");
            return fallback;
        }
        return s.id();
    }

    private void readGroups() {
        int i = 0;
        for (CompoundTag g : src.groups) {
            long gid = stableId(id(g.get("id")), "group/" + i++);
            ChapterGroup cg = new ChapterGroup();
            cg.id = gid;
            cg.title = title("chapter_group", gid, g, "title", "title");
            FtbItems.Out icon = items.convert(g.get("icon"), "chapter group " + hex(gid), Use.ICON, true);
            if (!icon.empty()) cg.icon = new ItemSpec(icon.spec);
            book.groups.add(cg);
            groupIds.add(gid);
        }
    }

    // ------------------------------------------------------------------------------------------- reward tables

    private void readTables() {
        Set<String> keys = new HashSet<>();
        int index = 0;
        List<FtbFolder.SourceFile> files = new ArrayList<>(src.rewardTables);
        files.sort(Comparator.comparingLong(f -> (long) num(f.tag().get("order_index"), 0)));
        for (FtbFolder.SourceFile f : files) {
            CompoundTag c = f.tag();
            Table t = new Table();
            t.id = stableId(id(c.get("id")), "table/" + f.name());
            String key = f.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            if (key.isEmpty()) key = "table_" + index;
            String base = key;
            for (int n = 2; !keys.add(key); n++) key = base + "_" + n;
            index++;
            t.key = key;
            t.title = title("reward_table", t.id, c, "title", "title");
            t.lootSize = (int) Math.max(1, num(c.get("loot_size"), 1));
            t.emptyWeight = (float) num(c.get("empty_weight"), 0);
            CompoundTag crate = c.getCompoundOrEmpty("loot_crate");
            if (!crate.isEmpty()) t.crate = crate.getStringOr("string_id", "");
            RewardTable qi = new RewardTable();
            qi.key = key;
            qi.title = t.title.isEmpty() && t.crate != null ? crate.getStringOr("item_name", "") : t.title;
            FtbItems.Out icon = items.convert(c.get("icon"), "reward table " + f.name(), Use.ICON, true);
            if (!icon.empty()) qi.icon = new ItemSpec(icon.spec);
            float min = Float.MAX_VALUE;
            for (Tag e : c.getListOrEmpty("rewards")) {
                if (!(e instanceof CompoundTag rc)) continue;
                float w = (float) num(rc.get("weight"), 1);
                t.rewards.add(rc);
                t.weights.add(w);
                if (w > 0) min = Math.min(min, w);
            }
            // FTB weights are decimals (default 1), Quest Icons weights whole numbers: scale so ratios stay.
            t.weightScale = min == Float.MAX_VALUE ? 1 : (int) Math.min(1_000_000, Math.max(1, Math.ceil(10 / Math.min(1f, min))));
            int e = 0;
            for (int i = 0; i < t.rewards.size(); i++) {
                CompoundTag rc = t.rewards.get(i);
                String where = "reward table " + f.name() + " entry " + (++e);
                String type = stripNs(rc.getStringOr("type", "item"));
                if (!type.equals("item")) {
                    report.add(Kind.SKIPPED, "reward table entry of type " + type, where,
                            "Quest Icons tables hold items; \"all_table\" rewards still give it");
                    continue;
                }
                if (t.weights.get(i) <= 0) continue;   // always given by FTB: handled where the table is used
                FtbItems.Out it = items.convert(rc.get("item"), where, Use.REWARD, true);
                note(it, where);
                if (it.empty()) continue;
                RewardTable.Entry en = new RewardTable.Entry();
                en.item = new ItemSpec(itemNoCount(it.spec));
                long count = num(rc.get("count"), 0) > 0 ? (long) num(rc.get("count"), 1) : it.count;
                en.count = (int) Math.max(1, Math.min(Integer.MAX_VALUE, count));
                en.weight = weight(t.weights.get(i), t.weightScale);
                en.randomBonus = (int) Math.max(0, num(rc.get("random_bonus"), 0));
                qi.entries.add(en);
            }
            if (t.emptyWeight > 0) {
                report.add(Kind.NOTE, "\"nothing\" chance of table " + key, "reward table " + f.name(),
                        "used by \"loot\" rewards only; kept there as an empty entry");
            }
            if (c.contains("loot_table_id")) {
                report.add(Kind.SKIPPED, "loot table link of a reward table", "reward table " + f.name(), c.getStringOr("loot_table_id", ""));
            }
            t.qi = qi;
            tables.put(t.id, t);
            book.rewardTables.put(key, qi);
        }
    }

    /** The spec without a count (counts live in the task, reward or table entry). */
    private static Tag itemNoCount(Tag spec) {
        if (spec instanceof CompoundTag c && c.contains("count")) {
            CompoundTag copy = c.copy();
            copy.remove("count");
            return copy;
        }
        return spec;
    }

    // ------------------------------------------------------------------------------------------- chapters

    private void indexQuests() {
        for (FtbFolder.SourceFile f : src.chapters) {
            long cid = stableId(id(f.tag().get("id")), "chapter/" + f.name());
            Set<Long> ids = new HashSet<>();
            int i = 0;
            for (Tag t : f.tag().getListOrEmpty("quests")) {
                if (t instanceof CompoundTag q) {
                    long qid = stableId(id(q.get("id")), "quest/" + f.name() + "/" + i);
                    ids.add(qid);
                    questIds.add(qid);
                }
                i++;
            }
            chapterQuests.put(cid, ids);
        }
    }

    /** Ungrouped chapters first, then each group in the group file's order; by order_index inside each. */
    private List<FtbFolder.SourceFile> orderChapters() {
        Map<Long, List<FtbFolder.SourceFile>> byGroup = new LinkedHashMap<>();
        byGroup.put(0L, new ArrayList<>());
        for (ChapterGroup g : book.groups) byGroup.put(g.id, new ArrayList<>());
        for (FtbFolder.SourceFile f : src.chapters) {
            long g = id(f.tag().get("group"));
            if (g != 0 && !byGroup.containsKey(g)) {
                report.add(Kind.APPROXIMATED, "chapter in a group that does not exist", "chapter " + f.name(), "shown without a group");
                g = 0;
            }
            byGroup.get(g).add(f);
        }
        List<FtbFolder.SourceFile> out = new ArrayList<>();
        for (List<FtbFolder.SourceFile> l : byGroup.values()) {
            l.sort(Comparator.comparingLong((FtbFolder.SourceFile f) -> (long) num(f.tag().get("order_index"), 0)).thenComparing(FtbFolder.SourceFile::name));
            out.addAll(l);
        }
        return out;
    }

    private void convertChapter(FtbFolder.SourceFile f, int order) {
        CompoundTag c = f.tag();
        Chapter ch = new Chapter();
        ch.book = book;
        ch.id = stableId(id(c.get("id")), "chapter/" + f.name());
        String fname = c.getStringOr("filename", f.name()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        ch.filename = fname.isEmpty() ? "chapter_" + hex(ch.id).toLowerCase(Locale.ROOT) : fname;
        for (Chapter other : book.chapters) if (other.filename.equals(ch.filename)) ch.filename = ch.filename + "_" + hex(ch.id).toLowerCase(Locale.ROOT);
        ch.title = title("chapter", ch.id, c, "title", "title");
        ch.subtitle.addAll(lines("chapter", ch.id, c, "chapter_subtitle", "subtitle"));
        String where = "chapter " + ch.filename;
        FtbItems.Out icon = items.convert(c.get("icon"), where + " icon", Use.ICON, true);
        note(icon, where + " icon");
        if (!icon.empty()) ch.icon = new ItemSpec(icon.spec);
        long g = id(c.get("group"));
        ch.group = g != 0 && groupIds.contains(g) ? hex(g) : "";
        ch.orderIndex = order;
        String chShape = c.getStringOr("default_quest_shape", "");
        ch.defaultQuestShape = chShape.isEmpty() || chShape.equals("default") ? "" : shape(chShape, where, "");
        ch.alwaysInvisible = FtbItems.bool(c.get("always_invisible"), false);
        double defaultSize = num(c.get("default_quest_size"), 1.0);
        boolean defHideLines = FtbItems.bool(c.get("default_hide_dependency_lines"), false);
        boolean hideDepsVisible = FtbItems.bool(c.get("hide_quest_until_deps_visible"), false);
        boolean hideDepsComplete = FtbItems.bool(c.get("hide_quest_until_deps_complete"), false);
        String chPreset = c.getStringOr("preset", "");
        Tag chConsume = c.get("consume_items");
        for (String k : new String[]{"hide_quest_details_until_startable", "hide_text_until_complete", "default_repeatable_quest", "require_sequential_tasks"}) {
            if (FtbItems.bool(c.get(k), false)) report.add(Kind.SKIPPED, "chapter setting " + k, where, "no counterpart in Quest Icons");
        }
        if (c.getStringOr("progression_mode", "default").equals("flexible")) {
            report.add(Kind.APPROXIMATED, "flexible progression", where, "quests start when their dependencies are done");
        }
        book.chapters.add(ch);
        int qi = 0;
        for (Tag t : c.getListOrEmpty("quests")) {
            if (t instanceof CompoundTag q) {
                Quest quest = convertQuest(ch, q, qi, f.name(), defaultSize, defHideLines, hideDepsVisible, hideDepsComplete, chPreset, chConsume);
                ch.quests.add(quest);
            }
            qi++;
        }
        ListTag links = c.getListOrEmpty("quest_links");
        for (Tag t : links) {
            if (t instanceof CompoundTag l) {
                report.add(Kind.SKIPPED, "quest link", where, "a copy of quest " + l.getStringOr("linked_quest", "?")
                        + " shown in this chapter too; the quest itself stays in its own chapter");
            }
        }
        List<CompoundTag> images = new ArrayList<>();
        for (Tag t : c.getListOrEmpty("images")) if (t instanceof CompoundTag im) images.add(im);
        images.sort(Comparator.comparingLong(im -> (long) num(im.get("order"), 0)));   // FTB draws them in this order, below the quests
        for (CompoundTag im : images) image(ch, im, where);
    }

    private void image(Chapter ch, CompoundTag im, String where) {
        String img = im.getStringOr("image", "");
        long iid = id(im.get("id"));
        if (FtbItems.bool(im.get("dev"), false)) {
            report.add(Kind.SKIPPED, "editor-only image", where, img);
            return;
        }
        String tex = texture(img);
        if (tex == null) {
            report.add(Kind.SKIPPED, "chapter image that is not a texture", where, img);
            return;
        }
        ChapterImage ci = new ChapterImage();
        ci.image = tex;
        ci.x = num(im.get("x"), 0);
        ci.y = num(im.get("y"), 0);
        ci.width = num(im.get("width"), 1);
        ci.height = num(im.get("height"), 1);
        ci.rotation = num(im.get("rotation"), 0);
        if (im.get("color") instanceof NumericTag col) ci.color = col.intValue() & 0xFFFFFF;
        if (im.get("alpha") instanceof NumericTag a) ci.alpha = Math.max(0, Math.min(255, a.intValue()));
        String hover = iid != 0 ? title("image", iid, null, "title", "title") : "";
        if (hover.isEmpty() && im.get("hover") instanceof ListTag hl && !hl.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Tag h : hl) parts.add(h instanceof StringTag s ? clean(s.value()) : h.toString());
            hover = String.join(" ", parts);
        }
        if (hover.isEmpty() && im.get("title") instanceof StringTag st) hover = clean(st.value());
        ci.hover = hover;
        if (im.contains("dependency")) {
            long dep = id(im.get("dependency"));
            if (questIds.contains(dep)) {
                ci.dependency = dep;
            } else {
                report.add(Kind.APPROXIMATED, "image shown only after something that is not a quest", where, img + " is always shown");
            }
        }
        if (im.contains("click") || im.contains("click_action")) {
            report.add(Kind.SKIPPED, "image click action", where, im.getStringOr("click", im.getStringOr("click_action", "")));
        }
        if (FtbItems.bool(im.get("corner"), false)) {
            // FTB aligns such an image by its corner instead of its centre.
            ci.x += ci.width / 2;
            ci.y += ci.height / 2;
        }
        ch.images.add(ci);
    }

    /** An FTB icon string as a texture path ("ns:textures/x.png"); atlas sprites ("minecraft:item/diamond") get one. */
    static @Nullable String texture(String img) {
        if (img == null || img.isEmpty() || img.startsWith("http") || img.startsWith("#") || img.startsWith("item:")) return null;
        String s = img;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        if (!s.contains(":")) s = "minecraft:" + s;
        String ns = s.substring(0, s.indexOf(':'));
        String path = s.substring(s.indexOf(':') + 1);
        if (!path.endsWith(".png")) path = "textures/" + path + ".png";
        if (!(ns + ":" + path).matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return null;
        return ns + ":" + path;
    }

    // ------------------------------------------------------------------------------------------- quests

    private Quest convertQuest(Chapter ch, CompoundTag q, int index, String file, double defaultSize, boolean defHideLines,
                               boolean hideDepsVisible, boolean hideDepsComplete, String chPreset, @Nullable Tag chConsume) {
        Quest quest = new Quest();
        quest.chapter = ch;
        quest.id = stableId(id(q.get("id")), "quest/" + file + "/" + index);
        quest.title = title("quest", quest.id, q, "title", "title");
        quest.subtitle = title("quest", quest.id, q, "quest_subtitle", "subtitle");
        quest.description.addAll(lines("quest", quest.id, q, "quest_desc", "description"));
        String where = "chapter " + ch.filename + " / quest " + hex(quest.id) + (quest.title.isEmpty() ? "" : " \"" + shorten(quest.title) + "\"");
        FtbItems.Out icon = items.convert(q.get("icon"), where + " icon", Use.ICON, true);
        note(icon, where + " icon");
        if (!icon.empty()) quest.icon = new ItemSpec(icon.spec);
        quest.x = num(q.get("x"), 0);
        quest.y = num(q.get("y"), 0);
        // Visual preset (FTB 2101.1.x and later) > the quest's own shape and size > chapter defaults.
        String presetName = q.getStringOr("preset", "");
        if (presetName.isEmpty()) presetName = chPreset.isEmpty() ? bookPreset : chPreset;
        String shape = q.getStringOr("shape", "");
        double size = num(q.get("size"), 0);
        if (!presetName.isEmpty() && presetShape.containsKey(presetName)) {
            shape = presetShape.get(presetName);
            size = presetSize.get(presetName)[0];
        }
        quest.shape = shape.isEmpty() || shape.equals("default") ? "" : shape(shape, where, "");
        quest.size = size == 0 ? defaultSize : size;
        if (quest.size <= 0) quest.size = 1.0;
        // Dependencies: quests by id; a chapter id stands for all of its quests.
        Tag deps = q.get("dependencies");
        List<Long> depIds = new ArrayList<>();
        if (deps instanceof ListTag l) {
            for (Tag t : l) depIds.add(id(t));
        } else if (deps instanceof IntArrayTag ia) {
            for (int v : ia.getAsIntArray()) depIds.add((long) v);
        }
        for (long dep : depIds) {
            if (dep == 0 || dep == quest.id) continue;
            if (questIds.contains(dep)) {
                if (!quest.dependencies.contains(dep)) quest.dependencies.add(dep);
            } else if (chapterQuests.containsKey(dep)) {
                for (long cq : chapterQuests.get(dep)) if (cq != quest.id && !quest.dependencies.contains(cq)) quest.dependencies.add(cq);
                report.add(Kind.APPROXIMATED, "dependency on a whole chapter", where, "replaced by all quests of chapter " + hex(dep));
            } else {
                report.add(Kind.SKIPPED, "dependency on something that is not a quest", where, hex(dep) + " (missing, or a task/reward/group)");
            }
        }
        String req = q.getStringOr("dependency_requirement", "all_completed");
        int minDeps = (int) num(q.get("min_required_dependencies"), 0);
        quest.dependsOnAny = req.startsWith("one");
        if (req.endsWith("started")) {
            report.add(Kind.APPROXIMATED, "dependency requirement " + req, where, "Quest Icons waits for the dependency to be completed, not started");
        }
        if (minDeps > 0 && quest.dependencies.size() > 1) {
            if (minDeps == 1) {
                quest.dependsOnAny = true;
            } else if (minDeps < quest.dependencies.size()) {
                report.add(Kind.APPROXIMATED, "at least " + minDeps + " dependencies", where, "all " + quest.dependencies.size() + " dependencies are required");
            }
        }
        quest.optional = FtbItems.bool(q.get("optional"), false);
        quest.invisible = FtbItems.bool(q.get("invisible"), false);
        if (num(q.get("invisible_until_tasks"), 0) > 0) {
            quest.invisible = true;
            report.add(Kind.APPROXIMATED, "invisible until " + (long) num(q.get("invisible_until_tasks"), 0) + " tasks are done", where, "hidden until completed");
        }
        boolean hideVisible = tristate(q.get("hide_until_deps_visible"), q.get("hide"), hideDepsVisible);
        boolean hideComplete = tristate(q.get("hide_until_deps_complete"), null, hideDepsComplete);
        quest.hideUntilDepsComplete = hideComplete || hideVisible;
        if (hideVisible && !hideComplete && !quest.dependencies.isEmpty()) {
            report.add(Kind.APPROXIMATED, "hidden until dependencies are visible", where, "hidden until the dependencies are completed");
        }
        quest.hideDependencyLines = tristate(q.get("hide_dependency_lines"), null, defHideLines);
        quest.disableToast = FtbItems.bool(q.get("disable_toast"), false);
        if (FtbItems.bool(q.get("can_repeat"), false)) report.add(Kind.APPROXIMATED, "repeatable quest", where, "can be completed once");
        quest.hideDependentLines = FtbItems.bool(q.get("hide_dependent_lines"), false);
        if (num(q.get("max_completable_dependents"), 0) > 0) report.add(Kind.SKIPPED, "max completable dependents", where, "all dependents can be done");
        if (q.getStringOr("progression_mode", "default").equals("flexible")) {
            report.add(Kind.APPROXIMATED, "flexible progression", where, "the quest starts when its dependencies are done");
        }
        if (tristate(q.get("require_sequential_tasks"), null, false)) report.add(Kind.SKIPPED, "tasks in order", where, "tasks can be done in any order");
        if (tristate(q.get("hide_text_until_complete"), null, false) || tristate(q.get("hide_details_until_startable"), null, false)) {
            report.add(Kind.SKIPPED, "hidden quest text", where, "the text is visible");
        }
        if (!q.getStringOr("guide_page", "").isEmpty()) report.add(Kind.SKIPPED, "guide page link", where, q.getStringOr("guide_page", ""));
        int ti = 0;
        for (Tag t : q.getListOrEmpty("tasks")) {
            if (t instanceof CompoundTag tc) {
                Task task = convertTask(quest, tc, ti, where, chConsume);
                if (task != null) quest.tasks.add(task);
            }
            ti++;
        }
        int ri = 0;
        for (Tag t : q.getListOrEmpty("rewards")) {
            if (t instanceof CompoundTag rc) convertReward(quest, rc, ri, where);
            ri++;
        }
        return quest;
    }

    private static boolean tristate(@Nullable Tag value, @Nullable Tag legacy, boolean def) {
        if (value != null) return FtbItems.bool(value, def);
        if (legacy != null) return FtbItems.bool(legacy, def);
        return def;
    }

    private static String shorten(String s) {
        String plain = s.replaceAll("&[0-9a-fk-orA-FK-OR]", "").replaceAll("§.", "");
        return plain.length() > 40 ? plain.substring(0, 37) + "..." : plain;
    }

    private void note(FtbItems.Out o, String where) {
        for (String a : o.approx) report.add(Kind.APPROXIMATED, a.contains("custom texture") ? "custom texture icon" : a.startsWith("entity face") ? "entity face icon" : approxKind(a), where, a);
    }

    private static String approxKind(String a) {
        if (a.startsWith("mod data")) return "mod-specific item data left out";
        if (a.startsWith("1.20 item data")) return "1.20 item data left out";
        if (a.startsWith("filter") || a.startsWith("item filter")) return "item filter simplified";
        return a.length() > 70 ? a.substring(0, 67) + "..." : a;
    }

    // ------------------------------------------------------------------------------------------- tasks

    private void ref(String kind, String id, String where) {
        if (id == null || id.isEmpty()) return;
        refWhere.putIfAbsent(kind + "|" + id, where);
        refCount.merge(kind + "|" + id, 1, Integer::sum);
    }

    private @Nullable Task convertTask(Quest quest, CompoundTag t, int index, String questWhere, @Nullable Tag chConsume) {
        String type = stripNs(t.getStringOr("type", ""));
        long tid = stableId(id(t.get("id")), hex(quest.id) + "/task/" + index);
        String where = questWhere + " / task " + hex(tid);
        report.count(report.sourceTaskTypes, type.isEmpty() ? "?" : type, 1);
        String title = title("task", tid, t, "title", "title");
        FtbItems.Out icon = items.convert(t.get("icon"), where + " icon", Use.ICON, true);
        note(icon, where + " icon");
        boolean optional = FtbItems.bool(t.get("optional_task"), false);
        Task out;
        switch (type) {
            case "item" -> {
                Tasks.ItemTask it = new Tasks.ItemTask();
                String match = t.getStringOr("match_components", "");
                boolean matchData = FtbItems.bool(t.get("match_nbt"), false) || match.equals("strict") || match.equals("fuzzy");
                FtbItems.Out item = items.convert(t.get("item"), where, Use.TASK, matchData);
                if (!item.empty()) note(item, where);
                if (item.empty()) {
                    return checkmark(quest, tid, title, icon, where, "item task whose item cannot be checked",
                            item.approx.isEmpty() ? String.valueOf(t.get("item")) : String.join("; ", item.approx), describeItem(t, title));
                }
                if (item.crate != null) {
                    report.add(Kind.APPROXIMATED, "loot crate as task item", where, "asks for the Quest Icons crate " + item.specText());
                }
                it.item = new ItemSpec(itemNoCount(item.spec));
                long count = (long) num(t.get("count"), 0);
                it.count = Math.max(1, count > 0 ? count : 1);
                Tag consume = t.get("consume_items");
                it.consume = consume != null ? FtbItems.bool(consume, false) : chConsume != null ? FtbItems.bool(chConsume, defaultConsume) : defaultConsume;
                it.matchComponents = matchData && item.spec instanceof CompoundTag;
                it.fuzzyMatch = it.matchComponents && (match.equals("fuzzy") || FtbItems.bool(t.get("weak_nbt_match"), false));
                if (FtbItems.bool(t.get("only_from_crafting"), false)) report.add(Kind.APPROXIMATED, "only crafted items count", where, "any item counts");
                if (FtbItems.bool(t.get("task_screen_only"), false)) report.add(Kind.APPROXIMATED, "task screen only", where, "items are submitted from the inventory");
                out = it;
            }
            case "checkmark" -> out = new Tasks.CheckmarkTask();
            case "kill" -> {
                Tasks.KillTask k = new Tasks.KillTask();
                String tag = t.getStringOr("entityTypeTag", t.getStringOr("entity_type_tag", ""));
                k.entity = tag.isEmpty() ? t.getStringOr("entity", "minecraft:zombie") : "#" + tag;
                k.value = Math.max(1, (long) num(t.get("value"), 1));
                ref(tag.isEmpty() ? "entity" : "entity_tag", tag.isEmpty() ? k.entity : tag, where);
                if (!t.getStringOr("custom_name", "").isEmpty()) report.add(Kind.APPROXIMATED, "kill task for a named mob", where, "any " + k.entity + " counts");
                if (!t.getStringOr("nbt_filter", "").isEmpty()) report.add(Kind.APPROXIMATED, "kill task with an NBT filter", where, "any " + k.entity + " counts");
                out = k;
            }
            case "advancement" -> {
                Tasks.AdvancementTask a = new Tasks.AdvancementTask();
                a.advancement = t.getStringOr("advancement", "minecraft:story/root");
                a.criterion = t.getStringOr("criterion", "");
                ref("advancement", a.advancement, where);
                out = a;
            }
            case "dimension" -> {
                Tasks.DimensionTask d = new Tasks.DimensionTask();
                d.dimension = t.getStringOr("dimension", "minecraft:the_nether");
                ref("dimension", d.dimension, where);
                out = d;
            }
            case "biome" -> {
                Tasks.BiomeTask b = new Tasks.BiomeTask();
                b.biome = t.getStringOr("biome", "minecraft:plains");
                ref(b.biome.startsWith("#") ? "biome_tag" : "biome", b.biome.startsWith("#") ? b.biome.substring(1) : b.biome, where);
                out = b;
            }
            case "structure" -> {
                Tasks.StructureTask s = new Tasks.StructureTask();
                s.structure = t.getStringOr("structure", "minecraft:village_plains");
                ref(s.structure.startsWith("#") ? "structure_tag" : "structure", s.structure.startsWith("#") ? s.structure.substring(1) : s.structure, where);
                out = s;
            }
            case "xp" -> {
                // FTB takes the levels or points away when the player submits (XPTask.consumesResources).
                Tasks.XpTask x = new Tasks.XpTask();
                x.value = Math.max(1, (long) num(t.get("value"), 1));
                x.points = FtbItems.bool(t.get("points"), false);
                x.consume = true;
                out = x;
            }
            case "stat" -> {
                Tasks.StatTask s = new Tasks.StatTask();
                s.stat = t.getStringOr("stat", "minecraft:jump");
                s.value = Math.max(1, (long) num(t.get("value"), 1));
                ref("stat", s.stat, where);
                out = s;
            }
            case "observation" -> {
                Tasks.ObservationTask o = new Tasks.ObservationTask();
                String kind = observeType(t);
                String what = t.getStringOr("to_observe", "");
                o.timer = Math.max(1, (long) num(t.get("timer"), 20));
                switch (kind) {
                    case "block" -> o.observe = what;
                    case "block_tag" -> o.observe = what.startsWith("#") ? what : "#" + what;
                    case "block_state" -> {
                        o.observe = what.contains("[") ? what.substring(0, what.indexOf('[')) : what;
                        report.add(Kind.APPROXIMATED, "observe a block state", where, "any state of " + o.observe + " counts");
                    }
                    case "block_entity", "block_entity_type" -> {
                        o.observe = what;
                        report.add(Kind.APPROXIMATED, "observe a block entity", where, "observes the block " + what);
                    }
                    case "entity_type" -> {
                        o.observe = what;
                        o.entity = true;
                    }
                    case "entity_type_tag" -> {
                        o.observe = what.startsWith("#") ? what : "#" + what;
                        o.entity = true;
                    }
                    default -> o.observe = what;
                }
                if (o.observe.isEmpty()) {
                    return checkmark(quest, tid, title, icon, where, "observation task without a target", "", "Look around");
                }
                ref(o.entity ? (o.observe.startsWith("#") ? "entity_tag" : "entity") : (o.observe.startsWith("#") ? "block_tag" : "block"),
                        o.observe.startsWith("#") ? o.observe.substring(1) : o.observe, where);
                out = o;
            }
            case "location" -> {
                Tasks.LocationTask l = new Tasks.LocationTask();
                l.dimension = t.getStringOr("dimension", "minecraft:overworld");
                int[] pos = ints(t.get("position"));
                int[] size = ints(t.get("size"));
                if (pos.length == 3) {
                    int w = size.length == 3 ? size[0] : 1;
                    int h = size.length == 3 ? size[1] : 1;
                    int d = size.length == 3 ? size[2] : 1;
                    // FTB checks a box from position to position + size; Quest Icons a sphere around a point.
                    l.x = pos[0] + w / 2;
                    l.y = pos[1] + h / 2;
                    l.z = pos[2] + d / 2;
                    l.radius = Math.max(1, (int) Math.ceil(Math.max(w, Math.max(h, d)) / 2.0));
                    report.add(Kind.APPROXIMATED, "location box", where, "a sphere of radius " + l.radius + " around " + l.x + " " + l.y + " " + l.z);
                }
                if (FtbItems.bool(t.get("ignore_dimension"), false)) report.add(Kind.APPROXIMATED, "location in any dimension", where, "only in " + l.dimension);
                ref("dimension", l.dimension, where);
                out = l;
            }
            default -> {
                String what = switch (type) {
                    case "fluid" -> "fluid task";
                    case "forge_energy", "techreborn_energy" -> "energy task";
                    case "gamestage" -> "game stage task";
                    case "custom" -> "custom (KubeJS) task";
                    case "interaction" -> "interaction task";
                    default -> type.isEmpty() ? "task without a type" : "task type " + type;
                };
                return checkmark(quest, tid, title, icon, where, what, describeTask(type, t), describeTask(type, t));
            }
        }
        out.id = tid;
        out.quest = quest;
        out.title = title;
        out.optional = optional;
        if (!icon.empty()) out.icon = new ItemSpec(icon.spec);
        return out;
    }

    private static String observeType(CompoundTag t) {
        String named = t.getStringOr("observation_type", "");
        if (!named.isEmpty()) return named.toLowerCase(Locale.ROOT);
        int ordinal = (int) num(t.get("observe_type"), 0);
        String[] values = {"block", "block_tag", "block_state", "block_entity", "block_entity_type", "entity_type", "entity_type_tag"};
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : "block";
    }

    private static int[] ints(@Nullable Tag t) {
        if (t instanceof IntArrayTag a) return a.getAsIntArray();
        if (t instanceof ListTag l) {
            int[] out = new int[l.size()];
            for (int i = 0; i < out.length; i++) out[i] = l.get(i) instanceof NumericTag n ? n.intValue() : 0;
            return out;
        }
        return new int[0];
    }

    private Task checkmark(Quest quest, long tid, String title, FtbItems.Out icon, String where, String what, String detail, String fallbackTitle) {
        Tasks.CheckmarkTask c = new Tasks.CheckmarkTask();
        c.id = tid;
        c.quest = quest;
        c.title = !title.isEmpty() ? title : fallbackTitle;
        if (!icon.empty()) c.icon = new ItemSpec(icon.spec);
        report.add(Kind.CHECKMARK, what, where, (detail.isEmpty() ? "" : detail + "; ") + "now a checkmark \"" + shorten(c.title) + "\"");
        return c;
    }

    private static String describeItem(CompoundTag t, String title) {
        if (!title.isEmpty()) return title;
        long count = (long) num(t.get("count"), 1);
        return (count > 1 ? count + "x " : "") + "the item shown";
    }

    private static String describeTask(String type, CompoundTag t) {
        return switch (type) {
            case "fluid" -> {
                long amount = (long) num(t.get("amount"), 1000);
                String fluid = t.get("fluid") instanceof CompoundTag f ? f.getStringOr("id", f.getStringOr("FluidName", "?")) : t.getStringOr("fluid", "?");
                yield String.format(Locale.ROOT, "Deliver %,d mB of %s", amount, pretty(fluid));
            }
            case "forge_energy", "techreborn_energy" -> String.format(Locale.ROOT, "Deliver %,d energy", (long) num(t.get("value"), 0));
            case "gamestage" -> "Reach the stage \"" + t.getStringOr("stage", "?") + "\"";
            case "interaction" -> "Use " + pretty(t.getStringOr("target", t.getStringOr("block", "a block")));
            default -> type.contains(":") ? pretty(type) + " (" + type.substring(0, type.indexOf(':')) + ")" : type.isEmpty() ? "Task" : pretty(type);
        };
    }

    private static String pretty(String id) {
        String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        p = p.substring(p.lastIndexOf('/') + 1).replace('_', ' ');
        return p.isEmpty() ? id : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    // ------------------------------------------------------------------------------------------- rewards

    private boolean team(CompoundTag r, String type) {
        if (r.contains("team_reward")) return FtbItems.bool(r.get("team_reward"), defaultTeam);
        return teamByType.getOrDefault(type, defaultTeam);
    }

    private boolean auto(CompoundTag r) {
        String a = r.getStringOr("auto", "default");
        if (a.equals("default")) a = defaultAutoclaim;
        return a.equals("enabled") || a.equals("no_toast") || a.equals("invisible");
    }

    private <R extends Reward> R base(R r, Quest quest, long id, CompoundTag src, String type, String title, @Nullable FtbItems.Out icon) {
        r.id = id;
        r.quest = quest;
        r.title = title;
        if (icon != null && !icon.empty()) r.icon = new ItemSpec(icon.spec);
        boolean team = team(src, type);
        if (team != defaultTeam || src.contains("team_reward") || teamByType.containsKey(type)) r.teamReward = team;
        r.autoClaim = auto(src);
        r.excludeFromClaimAll = FtbItems.bool(src.get("exclude_from_claim_all"), false);
        quest.rewards.add(r);
        return r;
    }

    private void convertReward(Quest quest, CompoundTag r, int index, String questWhere) {
        String type = stripNs(r.getStringOr("type", "item"));
        long rid = stableId(id(r.get("id")), hex(quest.id) + "/reward/" + index);
        String where = questWhere + " / reward " + hex(rid);
        report.count(report.sourceRewardTypes, type, 1);
        String title = title("reward", rid, r, "title", "title");
        FtbItems.Out icon = items.convert(r.get("icon"), where + " icon", Use.ICON, true);
        note(icon, where + " icon");
        switch (type) {
            case "item" -> {
                FtbItems.Out item = items.convert(r.get("item"), where, Use.REWARD, true);
                note(item, where);
                long count = (long) num(r.get("count"), 0);
                if (count <= 0) count = item.count;
                if (item.crate != null) {
                    Table t = tableForCrate(item.crate);
                    if (t != null) {
                        for (int i = 0; i < count; i++) {
                            Rewards.RandomReward rr = base(new Rewards.RandomReward(), quest, i == 0 ? rid : derived(rid, i), r, type, title, icon);
                            rr.table = t.key;
                        }
                        report.add(Kind.APPROXIMATED, "loot crate reward", where, "gives a random item of table " + t.key + " right away instead of a crate");
                        return;
                    }
                }
                if (item.empty()) {
                    report.add(Kind.SKIPPED, "item reward without a usable item", where, String.valueOf(r.get("item")));
                    return;
                }
                if (item.filter || item.spec instanceof ListTag) {
                    item.spec = item.spec instanceof ListTag l && !l.isEmpty() ? l.get(0) : item.spec;
                    report.add(Kind.APPROXIMATED, "filter item as a reward", where, "gives " + item.specText());
                }
                Rewards.ItemReward ir = base(new Rewards.ItemReward(), quest, rid, r, type, title, icon);
                ir.item = new ItemSpec(itemNoCount(item.spec));
                ir.count = (int) Math.max(1, Math.min(Integer.MAX_VALUE, count));
                ir.randomBonus = (int) Math.max(0, num(r.get("random_bonus"), 0));
                if (FtbItems.bool(r.get("only_one"), false)) report.add(Kind.APPROXIMATED, "only one item reward", where, "given even if the player has one");
            }
            case "xp" -> base(new Rewards.XpReward(), quest, rid, r, type, title, icon).xp = (int) num(r.get("xp"), 100);
            case "xp_levels" -> base(new Rewards.XpLevelsReward(), quest, rid, r, type, title, icon).levels = (int) num(r.get("xp_levels"), 5);
            case "command" -> {
                Rewards.CommandReward c = base(new Rewards.CommandReward(), quest, rid, r, type, title, icon);
                c.command = r.getStringOr("command", "");
                int level = r.contains("permission_level") ? (int) num(r.get("permission_level"), 0) : FtbItems.bool(r.get("elevate_perms"), false) ? 2 : 0;
                c.elevatePerms = level >= 2;
                c.permissionLevel = Math.max(2, Math.min(4, level));
                c.silent = FtbItems.bool(r.get("silent"), false);
                if (level == 1) report.add(Kind.APPROXIMATED, "command with permission level 1", where, "runs with the player's own permissions: " + c.command);
                if (!r.getStringOr("feedback_message", "").isEmpty()) report.add(Kind.SKIPPED, "command feedback message", where, r.getStringOr("feedback_message", ""));
            }
            case "random", "loot" -> tableReward(quest, rid, r, type, title, icon, where);
            case "all_table" -> {
                Table t = table(r, where);
                if (t == null) return;
                int n = 0;
                for (int i = 0; i < t.rewards.size(); i++) {
                    CompoundTag entry = t.rewards.get(i).copy();
                    if (!entry.contains("type")) entry.putString("type", "item");
                    entry.putString("id", hex(derived(rid, n)));
                    if (r.contains("team_reward")) entry.put("team_reward", r.get("team_reward"));
                    if (r.contains("auto")) entry.put("auto", r.get("auto"));
                    convertReward(quest, entry, 1000 + n, questWhere);
                    report.count(report.sourceRewardTypes, stripNs(entry.getStringOr("type", "item")), -1);   // counted as all_table
                    n++;
                }
                report.add(Kind.NOTE, "\"all of table\" reward split into " + n + " rewards", where, "table " + t.key);
            }
            case "choice" -> {
                Table t = table(r, where);
                if (t == null) return;
                Rewards.ChoiceReward c = base(new Rewards.ChoiceReward(), quest, rid, r, type, title, icon);
                for (int i = 0; i < t.rewards.size(); i++) {
                    CompoundTag e = t.rewards.get(i);
                    if (!stripNs(e.getStringOr("type", "item")).equals("item")) continue;
                    FtbItems.Out it = items.convert(e.get("item"), where, Use.REWARD, true);
                    if (it.empty()) continue;
                    long cnt = num(e.get("count"), 0) > 0 ? (long) num(e.get("count"), 1) : it.count;
                    Tag spec = itemNoCount(it.spec);
                    if (cnt > 1) {
                        CompoundTag withCount = spec instanceof CompoundTag sc ? sc.copy() : new CompoundTag();
                        if (!(spec instanceof CompoundTag)) withCount.putString("id", ((StringTag) spec).value());
                        withCount.putInt("count", (int) Math.min(Integer.MAX_VALUE, cnt));
                        spec = withCount;
                    }
                    c.items.add(new ItemSpec(spec));
                }
                if (c.items.isEmpty()) {
                    quest.rewards.remove(c);
                    report.add(Kind.SKIPPED, "choice reward without items", where, "table " + t.key);
                }
            }
            case "advancement" -> {
                Rewards.AdvancementReward a = base(new Rewards.AdvancementReward(), quest, rid, r, type, title, icon);
                a.advancement = r.getStringOr("advancement", "");
                ref("advancement", a.advancement, where);
                if (!r.getStringOr("criterion", "").isEmpty()) {
                    report.add(Kind.APPROXIMATED, "advancement reward for one criterion", where, "grants the whole advancement " + a.advancement);
                }
            }
            default -> {
                String what = switch (type) {
                    case "toast" -> "toast reward (a pop-up message)";
                    case "gamestage" -> "game stage reward";
                    case "custom" -> "custom (KubeJS) reward";
                    case "currency" -> "currency reward (FTB money)";
                    default -> "reward type " + type;
                };
                String detail = switch (type) {
                    case "toast" -> r.getStringOr("description", "");
                    case "gamestage" -> (FtbItems.bool(r.get("remove"), false) ? "remove stage " : "stage ") + r.getStringOr("stage", "");
                    case "currency" -> "amount " + (long) num(r.get("amount"), 0);
                    default -> "";
                };
                report.add(Kind.SKIPPED, what, where, detail);
            }
        }
    }

    private static int weight(float w, int scale) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE / 64, Math.round((double) w * scale)));
    }

    private static long derived(long id, int n) {
        return n == 0 ? id : QuestObject.hashId(QuestObject.idToString(id) + "/" + n);
    }

    private @Nullable Table table(CompoundTag r, String where) {
        long tid = id(r.get("table_id"));
        if (r.get("table_id") instanceof NumericTag n) tid = n.longValue();
        Table t = tables.get(tid);
        if (t == null && r.get("table_data") instanceof CompoundTag data) {
            // An "internal" table stored inside the reward.
            t = new Table();
            t.id = QuestObject.hashId("inline/" + where);
            t.key = "inline_" + QuestObject.idToString(t.id).toLowerCase(Locale.ROOT);
            t.qi = new RewardTable();
            t.qi.key = t.key;
            for (Tag e : data.getListOrEmpty("rewards")) {
                if (e instanceof CompoundTag rc) {
                    t.rewards.add(rc);
                    t.weights.add((float) num(rc.get("weight"), 1));
                }
            }
            t.lootSize = (int) Math.max(1, num(data.get("loot_size"), 1));
            t.emptyWeight = (float) num(data.get("empty_weight"), 0);
        }
        if (t == null) {
            report.add(Kind.SKIPPED, tid == 0 ? "table reward without a reward table" : "reward from a reward table that does not exist", where,
                    tid == 0 ? "the FTB file names no table, so FTB gives nothing either" : "table id " + hex(tid));
        }
        return t;
    }

    private @Nullable Table tableForCrate(String crate) {
        for (Table t : tables.values()) if (crate.equals(t.crate)) return t;
        return null;
    }

    /**
     * FTB "random" (one roll per loot size, never empty) and "loot" (may roll "nothing"): Quest Icons rolls one
     * weighted entry per random reward, so loot size n becomes n rewards; entries of weight 0 are always given.
     */
    private void tableReward(Quest quest, long rid, CompoundTag r, String type, String title, FtbItems.Out icon, String where) {
        Table t = table(r, where);
        if (t == null) return;
        boolean withEmpty = type.equals("loot") && t.emptyWeight > 0;
        int n = 0;
        for (int roll = 0; roll < t.lootSize; roll++) {
            Rewards.RandomReward rr = base(new Rewards.RandomReward(), quest, derived(rid, n++), r, type, title.isEmpty() ? t.title : title, icon);
            if (!withEmpty && book.rewardTables.containsKey(t.key)) {
                rr.table = t.key;
            } else {
                // Inline entries: the table's items plus "nothing" with FTB's empty weight.
                int scale = t.weightScale <= 0 ? 10 : t.weightScale;
                for (int i = 0; i < t.rewards.size(); i++) {
                    CompoundTag e = t.rewards.get(i);
                    if (!stripNs(e.getStringOr("type", "item")).equals("item") || t.weights.get(i) <= 0) continue;
                    FtbItems.Out it = items.convert(e.get("item"), where, Use.REWARD, true);
                    if (it.empty()) continue;
                    RewardTable.Entry en = new RewardTable.Entry();
                    en.item = new ItemSpec(itemNoCount(it.spec));
                    en.count = (int) Math.max(1, num(e.get("count"), 0) > 0 ? num(e.get("count"), 1) : it.count);
                    en.weight = weight(t.weights.get(i), scale);
                    en.randomBonus = (int) Math.max(0, num(e.get("random_bonus"), 0));
                    rr.inline.add(en);
                }
                if (withEmpty) {
                    RewardTable.Entry nothing = new RewardTable.Entry();
                    nothing.item = ItemSpec.EMPTY;
                    nothing.weight = weight(t.emptyWeight, scale);
                    rr.inline.add(nothing);
                }
                if (rr.inline.isEmpty()) {
                    quest.rewards.remove(rr);
                    report.add(Kind.SKIPPED, "table reward without items", where, "table " + t.key);
                    return;
                }
            }
        }
        if (t.lootSize > 1) report.add(Kind.NOTE, type + " reward with " + t.lootSize + " rolls", where, "became " + t.lootSize + " random rewards");
        // Entries with weight 0 are always given by FTB.
        for (int i = 0; i < t.rewards.size(); i++) {
            if (t.weights.get(i) > 0) continue;
            CompoundTag entry = t.rewards.get(i).copy();
            if (!entry.contains("type")) entry.putString("type", "item");
            entry.putString("id", hex(derived(rid, n++)));
            convertReward(quest, entry, 2000 + n, where);
            report.count(report.sourceRewardTypes, stripNs(entry.getStringOr("type", "item")), -1);
        }
    }

    // ------------------------------------------------------------------------------------------- report

    private void countSource() {
        int quests = 0;
        int tasks = 0;
        int rewards = 0;
        int links = 0;
        int images = 0;
        for (FtbFolder.SourceFile f : src.chapters) {
            for (Tag t : f.tag().getListOrEmpty("quests")) {
                if (!(t instanceof CompoundTag q)) continue;
                quests++;
                tasks += q.getListOrEmpty("tasks").size();
                rewards += q.getListOrEmpty("rewards").size();
            }
            links += f.tag().getListOrEmpty("quest_links").size();
            images += f.tag().getListOrEmpty("images").size();
        }
        report.source.put("Chapter groups", src.groups.size());
        report.source.put("Chapters", src.chapters.size());
        report.source.put("Quests", quests);
        report.source.put("Tasks", tasks);
        report.source.put("Rewards", rewards);
        report.source.put("Reward tables", src.rewardTables.size());
        report.source.put("Quest links", links);
        report.source.put("Chapter images", images);
        report.sourceRewardTypes.values().removeIf(v -> v <= 0);
    }

    private void countResult() {
        int tasks = 0;
        int checkmarks = 0;
        int rewards = 0;
        int images = 0;
        for (Chapter c : book.chapters) {
            images += c.images.size();
            for (Quest q : c.quests) {
                tasks += q.tasks.size();
                rewards += q.rewards.size();
            }
        }
        report.result.put("Chapter groups", book.groups.size());
        report.result.put("Chapters", book.chapters.size());
        report.result.put("Quests", book.questCount());
        report.result.put("Tasks", tasks);
        report.result.put("  of them checkmarks for unsupported tasks", report.count(Kind.CHECKMARK));
        report.result.put("Rewards", rewards);
        report.result.put("Reward tables", book.rewardTables.size());
        report.result.put("Chapter images", images);
    }

    /**
     * In game: everything the book names must exist. An id of a mod that is not installed is listed under that mod
     * (so the report reads "mod mekanism is not installed: 120 ids"), anything else as renamed or mistyped.
     */
    private void checkIds() {
        GameData g = opt.game;
        if (g == null) return;
        for (var e : items.itemRefs.entrySet()) {
            String id = e.getKey();
            if (!g.item(id)) unknown(g, "item", id, e.getValue(), items.refCounts.getOrDefault("item:" + id, 1));
        }
        for (var e : items.tagRefs.entrySet()) {
            if (!g.itemTag(e.getKey())) unknown(g, "item tag", e.getKey(), e.getValue(), items.refCounts.getOrDefault("tag:" + e.getKey(), 1));
        }
        for (var e : refWhere.entrySet()) {
            String kind = e.getKey().substring(0, e.getKey().indexOf('|'));
            String id = e.getKey().substring(kind.length() + 1);
            boolean ok = switch (kind) {
                case "entity" -> g.entity(id);
                case "entity_tag" -> g.entityTag(id);
                case "advancement" -> g.advancement(id);
                case "dimension" -> g.dimension(id);
                case "biome" -> g.biome(id);
                case "biome_tag" -> g.biomeTag(id);
                case "structure" -> g.structure(id);
                case "structure_tag" -> g.structureTag(id);
                case "block" -> g.block(id);
                case "block_tag" -> g.blockTag(id);
                case "stat" -> g.stat(id);
                default -> true;
            };
            if (!ok) unknown(g, kind.replace('_', ' '), id, e.getValue(), refCount.getOrDefault(e.getKey(), 1));
        }
    }

    private void unknown(GameData g, String kind, String id, String where, int uses) {
        String shown = kind.endsWith("tag") ? "#" + id : id;
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String what = !g.namespace(ns) ? "mod \"" + ns + "\" is not installed" : kind + " " + shown + " does not exist (renamed or a typo?)";
        report.add(Kind.UNKNOWN, what, where, kind + " " + shown + ", used " + uses + "x");
    }

    /** The mods whose ids the book uses but which are not installed, most used first ("mekanism (120)"). */
    public static List<String> missingMods(ImportReport r) {
        List<String> out = new ArrayList<>();
        for (var e : r.summary(Kind.UNKNOWN).entrySet()) {
            String w = e.getKey();
            if (w.startsWith("mod \"")) out.add(w.substring(5, w.indexOf('"', 5)) + " (" + e.getValue() + ")");
        }
        return out;
    }
}
