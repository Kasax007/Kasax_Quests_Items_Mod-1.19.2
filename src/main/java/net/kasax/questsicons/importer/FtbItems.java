package net.kasax.questsicons.importer;

import net.kasax.questsicons.compat.QiListTag;
import net.kasax.questsicons.importer.ImportReport.Kind;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns FTB Quests item stacks into Quest Icons item specs: the 1.20 shape ({@code Count}/{@code tag}), the 1.21 and
 * 26.1 shape ({@code count}/{@code components}), FTB's own wrapper items (missing items, custom icons, the quest book,
 * loot crates) and the item filters of Item Filters (1.20) and FTB Filter System (1.21 and later), which become
 * "any of" lists.
 */
final class FtbItems {
    /** Minecraft 1.20.1 and 1.21.1, the data versions FTB Quests' SNBT item stacks come from. */
    static final int DATA_1_20_1 = 3465;
    static final int DATA_1_21_1 = 3955;

    /** Forge tag paths that changed their name in the common ("c") tags of 1.21. */
    private static final Map<String, String> FORGE_TAGS = new HashMap<>();

    static {
        String[][] pairs = {
                {"stone", "c:stones"}, {"cobblestone", "c:cobblestones"}, {"sand", "c:sands"}, {"gravel", "c:gravels"},
                {"obsidian", "c:obsidians"}, {"netherrack", "c:netherracks"}, {"sandstone", "c:sandstone_blocks"},
                {"glass", "c:glass_blocks"}, {"slimeballs", "c:slime_balls"}, {"gunpowder", "c:gunpowders"},
                {"leather", "c:leathers"}, {"string", "c:strings"}, {"tools/swords", "minecraft:swords"},
                {"tools/axes", "minecraft:axes"}, {"tools/pickaxes", "minecraft:pickaxes"}, {"tools/shovels", "minecraft:shovels"},
                {"tools/hoes", "minecraft:hoes"}, {"heads", "c:skulls"}, {"rods/wooden", "c:rods/wooden"}
        };
        for (String[] p : pairs) FORGE_TAGS.put(p[0], p[1]);
    }

    enum Use { ICON, TASK, REWARD }

    /** The converted item: a spec (null if nothing usable is left), a count and notes for the report. */
    static final class Out {
        @Nullable Tag spec;
        long count = 1;
        /** The FTB loot crate this item is (its "string id"), if it is one. */
        @Nullable String crate;
        boolean filter;
        final List<String> approx = new ArrayList<>();

        boolean empty() {
            return spec == null;
        }

        String specText() {
            return spec == null ? "-" : spec instanceof StringTag s ? s.value() : spec.toString();
        }
    }

    private final FtbImporter.Options options;
    private final ImportReport report;
    private final boolean json5;
    private final Map<String, CompoundTag> fixCache = new HashMap<>();
    private final Set<String> renamedTags = new LinkedHashSet<>();
    /** id -> where it was first seen, for the in-game check of unknown ids. */
    final Map<String, String> itemRefs = new HashMap<>();
    final Map<String, String> tagRefs = new HashMap<>();
    final Map<String, Integer> refCounts = new HashMap<>();

    FtbItems(FtbImporter.Options options, ImportReport report, boolean json5) {
        this.options = options;
        this.report = report;
        this.json5 = json5;
    }

    // ------------------------------------------------------------------------------------------- entry

    Out convert(@Nullable Tag raw, String where, Use use, boolean keepComponents) {
        Out o = new Out();
        if (raw == null) return o;
        if (raw instanceof StringTag s) {
            String id = s.value().trim();
            if (id.isEmpty() || id.equals("minecraft:air")) return o;
            if (id.startsWith("#")) {
                o.spec = StringTag.valueOf("#" + tag(id.substring(1), where));
                return o;
            }
            plain(o, id, DATA_1_20_1, where);
            return o;
        }
        if (!(raw instanceof CompoundTag c)) return o;
        String id = c.getStringOr("id", "");
        o.count = Math.max(1, num(c.get("Count"), num(c.get("count"), 1)));
        CompoundTag tag = c.getCompoundOrEmpty("tag");
        CompoundTag comps = c.getCompoundOrEmpty("components");
        switch (id) {
            case "", "minecraft:air" -> {
                return o;
            }
            case "ftbquests:missing_item" -> {
                // FTB keeps an item of a mod that was missing when the book was saved: 1.21+ as "id" or "10x id",
                // 1.20 as the whole stack under tag.Item.
                if (tag.contains("Item")) {
                    Out inner = convert(tag.get("Item"), where, use, keepComponents);
                    return inner;
                }
                String text = comps.getStringOr("ftbquests:missing_item", "");
                var m = Pattern.compile("^(\\d+)x (.+)$").matcher(text);
                if (m.matches()) {
                    o.count = Long.parseLong(m.group(1));
                    text = m.group(2);
                }
                if (text.isEmpty()) return o;
                plain(o, text.trim(), json5 ? -1 : DATA_1_21_1, where);
                return o;
            }
            case "ftbquests:custom_icon" -> {
                String face = comps.getStringOr("ftbquests:entity_face", "");
                if (!face.isEmpty()) {
                    // FTB draws the mob's face; its spawn egg is the closest item.
                    String egg = face + "_spawn_egg";
                    o.spec = StringTag.valueOf(egg);
                    ref(egg, where);
                    o.approx.add("entity face icon of " + face + " shown as its spawn egg");
                    return o;
                }
                String texture = comps.getStringOr("ftbquests:icon", tag.getStringOr("Icon", ""));
                o.approx.add("custom texture icon " + (texture.isEmpty() ? "" : texture + " ") + "has no item form; the quest shows its first task's item");
                return o;
            }
            case "ftbquests:book" -> {
                o.spec = StringTag.valueOf("questsicons:quest_book");
                return o;
            }
            case "ftbquests:lootcrate" -> {
                String crate = tag.getStringOr("type", comps.getStringOr("ftbquests:loot_crate", ""));
                o.crate = crate;
                o.spec = StringTag.valueOf(crateItem(crate));
                return o;
            }
            case "ftbfiltersystem:smart_filter" -> {
                String expr = comps.getStringOr("ftbfiltersystem:filter", tag.getStringOr("ftbfiltersystem:filter", ""));
                o.filter = true;
                smartFilter(o, expr, where);
                return o;
            }
            default -> {
                if (id.startsWith("itemfilters:")) {
                    o.filter = true;
                    List<String> alts = new ArrayList<>();
                    itemFilter(o, c, alts, where, 0);
                    setAlternatives(o, alts);
                    return o;
                }
                if (id.startsWith("ftbquests:") || id.startsWith("ftbfiltersystem:")) {
                    o.approx.add("FTB Quests item " + id + " has no counterpart");
                    return o;
                }
            }
        }
        // A normal item.
        boolean legacy = c.contains("Count") || c.contains("tag");
        int from = json5 ? -1 : legacy ? DATA_1_20_1 : DATA_1_21_1;
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        if (legacy) {
            stack.putByte("Count", (byte) Math.min(64, o.count));
            if (!tag.isEmpty()) stack.put("tag", tag.copy());
        } else {
            stack.putInt("count", (int) Math.min(Integer.MAX_VALUE, o.count));
            if (!comps.isEmpty()) stack.put("components", comps.copy());
        }
        // Minecraft 1.20.1 keeps item data as the stack's NBT tag. A 1.20 book is upgraded to 1.20.1 with the data fixer;
        // the "components" of a 1.21+ book cannot be turned back into NBT and are left out (the report says so).
        CompoundTag fixed = legacy ? fix(stack, from, where) : stack;
        String newId = fixed.getStringOr("id", id);
        CompoundTag newTag = fixed.getCompoundOrEmpty("tag").copy();
        if (!legacy && !comps.isEmpty()) {
            comps.keySet().removeIf(k -> k.startsWith("ftbquests:") || k.startsWith("!ftbquests:") || k.startsWith("ftbfiltersystem:"));
            if (!comps.isEmpty()) o.approx.add("1.21 item data (components) of " + id + " left out on 1.20.1 (" + String.join(", ", comps.keySet()) + ")");
        }
        if (newTag.get("Damage") instanceof NumericTag n && n.intValue() == 0) newTag.remove("Damage");
        ref(newId, where);
        if (!newTag.isEmpty() && (use != Use.TASK || keepComponents)) {
            CompoundTag spec = new CompoundTag();
            spec.putString("id", newId);
            spec.put("tag", newTag);
            o.spec = spec;
        } else {
            // FTB ignores item data unless the task matches it, so dropping it there changes nothing.
            o.spec = StringTag.valueOf(newId);
        }
        return o;
    }

    private void plain(Out o, String id, int from, String where) {
        String newId = id;
        if (from > 0 && id.startsWith("minecraft:") && options.fixer != null) {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", id);
            if (from == DATA_1_20_1) {
                stack.putByte("Count", (byte) 1);
            } else {
                stack.putInt("count", 1);
            }
            newId = fix(stack, from, where).getStringOr("id", id);
            if (!newId.equals(id)) report.add(Kind.NOTE, "renamed item " + id + " -> " + newId, where, "");
        }
        if (newId.equals("ftbquests:book")) newId = "questsicons:quest_book";
        o.spec = StringTag.valueOf(newId);
        ref(newId, where);
    }

    private CompoundTag fix(CompoundTag stack, int from, String where) {
        // 1.20.1 can only upgrade data up to its own version (a 1.21 book's data stays as it is).
        if (from <= 0 || from > DATA_1_20_1 || options.fixer == null) return stack;
        String key = from + "|" + stack;
        CompoundTag cached = fixCache.get(key);
        if (cached != null) return cached;
        CompoundTag out;
        try {
            out = options.fixer.fix(stack.copy(), from);
        } catch (Exception e) {
            report.add(Kind.APPROXIMATED, "item data could not be upgraded", where, stack + ": " + e);
            out = stack;
        }
        if (out == null) out = stack;
        fixCache.put(key, out);
        return out;
    }

    private void ref(String id, String where) {
        itemRefs.putIfAbsent(id, where);
        refCounts.merge("item:" + id, 1, Integer::sum);
    }

    /** An item tag, with Forge's 1.20 tags moved to the common tags of 1.21 and later. */
    String tag(String id, String where) {
        String out = id;
        if (id.startsWith("forge:")) {
            String path = id.substring(6);
            out = FORGE_TAGS.getOrDefault(path, "c:" + path);
            if (options.game != null && !options.game.itemTag(out) && options.game.itemTag(id)) out = id;
            // Fabric 1.20.1 names its common tags the other way round: forge:ingots/iron is c:iron_ingots there.
            if (options.game != null && !options.game.itemTag(out) && path.indexOf('/') > 0 && path.indexOf('/') == path.lastIndexOf('/')) {
                String flipped = "c:" + path.substring(path.indexOf('/') + 1) + "_" + path.substring(0, path.indexOf('/'));
                if (options.game.itemTag(flipped)) out = flipped;
            }
            if (!out.equals(id) && renamedTags.add(id)) {
                report.add(Kind.NOTE, "Forge tag #" + id + " -> #" + out, where, "Forge's tags are called \"c\" tags since 1.21");
            }
        }
        tagRefs.putIfAbsent(out, where);
        refCounts.merge("tag:" + out, 1, Integer::sum);
        return out;
    }

    private static String crateItem(String crate) {
        String c = crate.toLowerCase(Locale.ROOT);
        if (c.contains("legend")) return "questsicons:legendary_crate";
        if (c.contains("epic")) return "questsicons:epic_crate";
        if (c.contains("rare") || c.contains("uncommon")) return "questsicons:rare_crate";
        return "questsicons:common_crate";
    }

    static long num(@Nullable Tag t, long def) {
        if (t instanceof NumericTag n) return n.longValue();
        if (t instanceof StringTag s) {
            try {
                return Long.parseLong(s.value().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    // ------------------------------------------------------------------------------------------- filters

    private void setAlternatives(Out o, List<String> alts) {
        List<String> uniq = new ArrayList<>(new LinkedHashSet<>(alts));
        if (uniq.isEmpty()) {
            o.spec = null;
        } else if (uniq.size() == 1) {
            o.spec = StringTag.valueOf(uniq.get(0));
        } else {
            ListTag l = QiListTag.mixed();
            for (String a : uniq) l.add(StringTag.valueOf(a));
            o.spec = l;
        }
    }

    /** Item Filters (Minecraft 1.16 to 1.20): itemfilters:tag/or/and/mod/id_regex/strong_nbt/... */
    private void itemFilter(Out o, CompoundTag c, List<String> alts, String where, int depth) {
        String id = c.getStringOr("id", "");
        CompoundTag tag = c.getCompoundOrEmpty("tag");
        if (depth > 8) return;
        switch (id) {
            case "itemfilters:tag" -> {
                String v = tag.getStringOr("value", "");
                if (!v.isEmpty()) alts.add("#" + tag(v, where));
            }
            case "itemfilters:mod" -> {
                String v = tag.getStringOr("value", "");
                if (!v.isEmpty()) alts.add("@" + v);
            }
            case "itemfilters:or" -> {
                for (Tag t : tag.getListOrEmpty("items")) {
                    if (t instanceof CompoundTag ic) {
                        String iid = ic.getStringOr("id", "");
                        if (iid.startsWith("itemfilters:")) {
                            itemFilter(o, ic, alts, where, depth + 1);
                        } else {
                            Out inner = convert(ic, where, Use.TASK, false);
                            alts.addAll(flatten(inner.spec));
                        }
                    } else if (t instanceof StringTag st) {
                        Out inner = convert(st, where, Use.TASK, false);
                        alts.addAll(flatten(inner.spec));
                    }
                }
            }
            case "itemfilters:and" -> {
                List<String> first = new ArrayList<>();
                ListTag items = tag.getListOrEmpty("items");
                if (!items.isEmpty() && items.get(0) instanceof CompoundTag ic) {
                    if (ic.getStringOr("id", "").startsWith("itemfilters:")) {
                        itemFilter(o, ic, first, where, depth + 1);
                    } else {
                        first.addAll(flatten(convert(ic, where, Use.TASK, false).spec));
                    }
                }
                alts.addAll(first);
                if (items.size() > 1) o.approx.add("item filter \"and\": only its first part is checked");
            }
            case "itemfilters:id_regex" -> {
                String v = tag.getStringOr("value", "");
                if (options.game != null && !v.isEmpty()) {
                    try {
                        Pattern p = Pattern.compile(v);
                        int before = alts.size();
                        for (String item : options.game.itemIds()) if (p.matcher(item).find()) alts.add(item);
                        o.approx.add("item filter id_regex(" + v + ") expanded to the " + (alts.size() - before) + " items of this game");
                    } catch (Exception e) {
                        o.approx.add("item filter id_regex(" + v + ") is not a valid pattern");
                    }
                } else {
                    o.approx.add("item filter id_regex(" + v + ") can only be expanded in game");
                }
            }
            default -> o.approx.add("item filter " + id + (tag.isEmpty() ? "" : " " + tag) + " has no counterpart");
        }
    }

    private static List<String> flatten(@Nullable Tag spec) {
        List<String> l = new ArrayList<>();
        if (spec instanceof StringTag s) {
            l.add(s.value());
        } else if (spec instanceof ListTag list) {
            for (Tag t : list) if (t instanceof StringTag s) l.add(s.value());
        } else if (spec instanceof CompoundTag c) {
            l.add(c.getStringOr("id", ""));
        }
        return l;
    }

    /** FTB Filter System (1.20.1 and later), e.g. {@code or(item(a:b)item_tag(c:ingots/iron))}. */
    private void smartFilter(Out o, String expr, String where) {
        try {
            List<Node> root = parseFilters(expr.trim());
            List<String> alts = new ArrayList<>();
            // The top level is an implicit "and", like FTB's RootFilter.
            selectors(and(root), alts, o, where);
            setAlternatives(o, alts);
        } catch (IllegalArgumentException e) {
            o.approx.add("filter " + expr + " could not be read: " + e.getMessage());
            o.spec = null;
        }
    }

    record Node(String type, String arg, List<Node> children) {
    }

    private static Node and(List<Node> nodes) {
        return nodes.size() == 1 ? nodes.get(0) : new Node("and", "", nodes);
    }

    static List<Node> parseFilters(String str) {
        List<Node> res = new ArrayList<>();
        str = str.trim();
        while (!str.isEmpty()) {
            int start = str.indexOf('(');
            if (start <= 0) throw new IllegalArgumentException("missing '(' in " + str);
            int end = matching(str, start);
            String type = str.substring(0, start).trim();
            if (type.startsWith("ftbfiltersystem:")) type = type.substring(16);
            String arg = str.substring(start + 1, end).trim();
            List<Node> children = List.of();
            if (type.equals("or") || type.equals("and") || type.equals("not") || type.equals("only_one")) {
                children = arg.isEmpty() ? List.of() : parseFilters(arg);
            } else {
                arg = unescape(arg);
            }
            res.add(new Node(type, arg, children));
            str = str.substring(end + 1).trim();
        }
        return res;
    }

    private static int matching(String str, int pos) {
        int depth = 0;
        for (int i = pos; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("missing ')'");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                if (++i >= s.length()) break;
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    /** True if the node selects items (item, tag, mod, or of those); restrictions (component, block, ...) do not. */
    private static boolean selects(Node n) {
        return switch (n.type) {
            case "item", "item_tag", "mod" -> true;
            case "or" -> n.children.stream().anyMatch(FtbItems::selects);
            case "and" -> n.children.stream().anyMatch(FtbItems::selects);
            default -> false;
        };
    }

    private void selectors(Node n, List<String> alts, Out o, String where) {
        switch (n.type) {
            case "item" -> {
                String id = n.arg.trim();
                if (!id.isEmpty()) {
                    Out inner = new Out();
                    plain(inner, id, json5 ? -1 : DATA_1_21_1, where);
                    alts.addAll(flatten(inner.spec));
                }
            }
            case "item_tag" -> {
                if (!n.arg.isBlank()) alts.add("#" + tag(n.arg.trim(), where));
            }
            case "mod" -> {
                if (!n.arg.isBlank()) alts.add("@" + n.arg.trim());
            }
            case "or" -> {
                for (Node c : n.children) {
                    if (selects(c)) {
                        selectors(c, alts, o, where);
                    } else {
                        o.approx.add("filter part " + c.type + "(" + c.arg + ") inside \"or\" has no counterpart");
                    }
                }
            }
            case "and" -> {
                List<Node> sel = n.children.stream().filter(FtbItems::selects).toList();
                for (Node c : n.children) {
                    if (!selects(c)) o.approx.add("filter condition " + c.type + "(" + shorten(c.arg) + ") is not checked");
                }
                if (sel.isEmpty()) return;
                if (sel.size() > 1) o.approx.add("filter \"and\" of several item lists: only the first is checked");
                selectors(sel.get(0), alts, o, where);
            }
            default -> o.approx.add("filter " + n.type + "(" + shorten(n.arg) + ") has no counterpart");
        }
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    /** Booleans in FTB files may be written as true/false (bytes) or, rarely, as other numbers. */
    static boolean bool(@Nullable Tag t, boolean def) {
        if (t instanceof ByteTag b) return b.byteValue() != 0;
        if (t instanceof NumericTag n) return n.intValue() != 0;
        if (t instanceof StringTag s) return s.value().equalsIgnoreCase("true");
        return def;
    }
}
