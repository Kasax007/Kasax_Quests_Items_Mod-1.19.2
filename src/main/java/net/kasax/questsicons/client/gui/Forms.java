package net.kasax.questsicons.client.gui;

import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.EditorData;
import net.kasax.questsicons.client.gui.FormScreen.Action;
import net.kasax.questsicons.client.gui.FormScreen.AddId;
import net.kasax.questsicons.client.gui.FormScreen.Bool;
import net.kasax.questsicons.client.gui.FormScreen.Choice;
import net.kasax.questsicons.client.gui.FormScreen.Color;
import net.kasax.questsicons.client.gui.FormScreen.Info;
import net.kasax.questsicons.client.gui.FormScreen.ItemField;
import net.kasax.questsicons.client.gui.FormScreen.Lines;
import net.kasax.questsicons.client.gui.FormScreen.Number;
import net.kasax.questsicons.client.gui.FormScreen.ObjectList;
import net.kasax.questsicons.client.gui.FormScreen.Text;
import net.kasax.questsicons.client.gui.FormScreen.TriBool;
import net.kasax.questsicons.quest.ChapterGroup;
import net.kasax.questsicons.quest.ItemSpec;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.QuestShape;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.quest.task.Task;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** The forms of the in-game editor: one per kind of object in the quest files. */
public final class Forms {
    private Forms() {
    }

    static Component l(String key) {
        return Component.translatable("questsicons.form." + key);
    }

    static String randomId() {
        return QuestObject.idToString(ThreadLocalRandom.current().nextLong() & 0x7FFFFFFFFFFFFFFFL);
    }

    private static HolderLookup.Provider reg() {
        return FormScreen.reg();
    }

    private static List<String> shapes(boolean withDefault) {
        List<String> v = new ArrayList<>();
        if (withDefault) v.add("");
        for (QuestShape s : QuestShape.values()) v.add(s.id());
        return v;
    }

    private static List<Component> shapeNames(boolean withDefault) {
        List<Component> v = new ArrayList<>();
        if (withDefault) v.add(l("default"));
        for (QuestShape s : QuestShape.values()) v.add(Component.literal(s.id()));
        return v;
    }

    // ------------------------------------------------------------------------------------------------ quest
    public static FormScreen quest(Screen parent, Quest q, CompoundTag tag, Consumer<CompoundTag> onSave) {
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.edit.edit_quest", q == null ? l("new_quest") : q.displayTitle()),
                tag, onSave);
        f.add(new Text("title", l("title"), true).help(l("help.title")));
        f.add(new Text("subtitle", l("subtitle"), true));
        f.add(new ItemField("icon", l("icon"), true, true, false).help(l("help.icon")));
        f.add(new ObjectList("tasks", l("tasks"), Forms::taskName, Forms::taskIcon,
                (form, i) -> editTask(form, q, i), l("add_task"), form -> addTask(form, q)));
        f.add(new ObjectList("rewards", l("rewards"), Forms::rewardName, Forms::rewardIcon,
                (form, i) -> editReward(form, q, i), l("add_reward"), form -> addReward(form, q)));
        f.add(new Lines("description", l("description"), 5).help(l("help.text")));
        f.add(new ObjectList("dependencies", l("dependencies"), t -> questName(t), t -> questIcon(t), null, l("add_dependency"),
                form -> Minecraft.getInstance().setScreen(questPicker(form, q == null ? 0 : q.id, id -> addDep(form, id)))));
        f.add(new Choice("dependency_requirement", l("requirement"), List.of("", "one_completed"), List.of(l("req_all"), l("req_one"))));
        f.add(new Choice("shape", l("shape"), shapes(true), shapeNames(true)));
        f.add(new Number("size", l("size"), 1, 0.25, 4, true));
        f.add(new Number("x", l("x"), 0, -10000, 10000, true));
        f.add(new Number("y", l("y"), 0, -10000, 10000, true));
        f.add(new Bool("optional", l("optional"), false).help(l("help.optional")));
        f.add(new Bool("hide_until_deps_complete", l("hide_until_deps"), false));
        f.add(new Bool("invisible", l("invisible"), false).help(l("help.invisible")));
        f.add(new Bool("hide_dependency_lines", l("hide_lines"), false));
        f.add(new Bool("hide_dependent_lines", l("hide_dependent_lines"), false));
        f.add(new Bool("disable_toast", l("disable_toast"), false));
        return f;
    }

    private static void addDep(FormScreen form, String id) {
        var list = form.tag.getListOrEmpty("dependencies").copy();
        for (Tag t : list) if (t instanceof StringTag s && QuestObject.parseId(s.value()) == QuestObject.parseId(id)) return;
        list.add(StringTag.valueOf(id));
        form.tag.put("dependencies", list);
    }

    static Component questName(Tag t) {
        QuestBook b = ClientQuests.book;
        Quest q = b == null || !(t instanceof StringTag s) ? null : b.quest(QuestObject.parseId(s.value()));
        if (q == null) return Component.literal(t instanceof StringTag s ? s.value() : t.toString()).withStyle(net.minecraft.ChatFormatting.RED);
        return q.displayTitle().copy().append(Component.literal("  (" + q.chapter.displayTitle().getString() + ")").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    static ItemStack questIcon(Tag t) {
        QuestBook b = ClientQuests.book;
        Quest q = b == null || !(t instanceof StringTag s) ? null : b.quest(QuestObject.parseId(s.value()));
        return q == null || reg() == null ? ItemStack.EMPTY : q.displayIcon(reg());
    }

    /** All quests of the book (title and chapter), except one. */
    public static Screen questPicker(Screen parent, long except, Consumer<String> onPick) {
        QuestBook b = ClientQuests.book;
        List<String> ids = new ArrayList<>();
        List<Component> names = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        if (b != null) {
            for (var c : b.chapters) {
                for (Quest q : c.quests) {
                    if (q.id == except) continue;
                    ids.add(q.idString());
                    names.add(q.displayTitle().copy().append(Component.literal("  · " + c.displayTitle().getString()).withStyle(net.minecraft.ChatFormatting.GRAY)));
                    icons.add(reg() == null ? ItemStack.EMPTY : q.displayIcon(reg()));
                }
            }
        }
        return PickerScreen.list(parent, l("pick_quest"), ids, names, icons, onPick);
    }

    // ------------------------------------------------------------------------------------------------ tasks
    static Component taskName(Tag t) {
        if (!(t instanceof CompoundTag c)) return Component.literal("?");
        String type = c.getStringOr("type", "item");
        try {
            Task task = Tasks.create(type);
            task.read(c.copy());
            return Component.literal("[" + Component.translatable("questsicons.task_type." + type).getString() + "] ").withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(task.title(reg()));
        } catch (Exception e) {
            return Component.literal(type);
        }
    }

    static ItemStack taskIcon(Tag t) {
        if (!(t instanceof CompoundTag c) || reg() == null) return ItemStack.EMPTY;
        try {
            Task task = Tasks.create(c.getStringOr("type", "item"));
            task.read(c.copy());
            return task.displayIcon(reg());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void addTask(FormScreen form, Quest q) {
        List<String> types = new ArrayList<>(Tasks.TYPES.keySet());
        List<Component> names = new ArrayList<>();
        for (String type : types) names.add(Component.translatable("questsicons.task_type." + type));
        Minecraft.getInstance().setScreen(PickerScreen.list(form, l("pick_task_type"), types, names, null, type -> {
            CompoundTag t = newTask(type);
            Minecraft.getInstance().setScreen(task(form, t, saved -> {
                var list = form.tag.getListOrEmpty("tasks").copy();
                list.add(saved);
                form.tag.put("tasks", list);
            }));
        }));
    }

    private static void editTask(FormScreen form, Quest q, int i) {
        var list = form.tag.getListOrEmpty("tasks");
        if (!(list.get(i) instanceof CompoundTag t)) return;
        Minecraft.getInstance().setScreen(task(form, t.copy(), saved -> {
            var l = form.tag.getListOrEmpty("tasks").copy();
            l.set(i, saved);
            form.tag.put("tasks", l);
        }));
    }

    /** A new task of this type with sensible defaults (the held item, the current position). */
    public static CompoundTag newTask(String type) {
        CompoundTag t = new CompoundTag();
        t.putString("id", randomId());
        t.putString("type", type);
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case "kill" -> {
                t.putString("entity", "minecraft:zombie");
                t.putLong("value", 1);
            }
            case "advancement" -> t.putString("advancement", "minecraft:story/mine_stone");
            case "dimension" -> t.putString("dimension", "minecraft:the_nether");
            case "biome" -> t.putString("biome", "minecraft:plains");
            case "structure" -> t.putString("structure", "minecraft:village_plains");
            case "xp" -> t.putLong("value", 5);
            case "stat" -> {
                t.putString("stat", "minecraft:jump");
                t.putLong("value", 10);
            }
            case "observation" -> {
                t.putString("observe", "minecraft:diamond_ore");
                t.putLong("timer", 20);
            }
            case "location" -> usePosition(t);
            default -> {
            }
        }
        return t;
    }

    static void usePosition(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        t.putString("dimension", mc.player.level().dimension().identifier().toString());
        t.putInt("x", mc.player.getBlockX());
        t.putInt("y", mc.player.getBlockY());
        t.putInt("z", mc.player.getBlockZ());
        if (!t.contains("radius")) t.putInt("radius", 8);
    }

    public static FormScreen task(Screen parent, CompoundTag t, Consumer<CompoundTag> onSave) {
        String type = t.getStringOr("type", "item");
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.task_title",
                Component.translatable("questsicons.task_type." + type)), t, onSave);
        f.add(new Info(Component.translatable("questsicons.task_help." + type)));
        switch (type) {
            case "item" -> {
                ObjectList real = new ObjectList("item", l("items_any"), Forms::itemName, Forms::itemIcon, null, l("add_item"),
                        form -> Minecraft.getInstance().setScreen(PickerScreen.items(form, l("items_any"), false, st -> {
                            if (reg() != null) addItem(form, "item", ItemSpec.of(st.copyWithCount(1), reg()).write(), true);
                        }))).singleAsValue().min(1);
                f.add(real.help(l("help.items_any")));
                f.add(new AddId(l("add_id"), real, true));
                f.add(new Number("count", l("count"), 1, 1, 1_000_000_000, false, true));
                f.add(new Bool("consume_items", l("consume"), false).help(l("help.consume")));
                f.add(new Choice("match_components", l("match"), List.of("", "strict", "fuzzy"), List.of(l("match_off"), l("match_strict"), l("match_fuzzy"))));
            }
            case "kill" -> {
                f.add(new Text("entity", l("entity"), false).kind("entity"));
                f.add(new Number("value", l("amount"), 1, 1, 1_000_000_000, false, true));
            }
            case "advancement" -> {
                f.add(new Text("advancement", l("advancement"), false).kind("advancement"));
                f.add(new Text("criterion", l("criterion"), true).validator(v -> {
                    List<String> c = EditorData.criteria(f.tag.getStringOr("advancement", ""));
                    return c.isEmpty() || c.contains(v) ? null : "questsicons.form.error.unknown_criterion";
                }).picker((form, set) -> PickerScreen.list(form, l("criterion"), EditorData.criteria(form.tag.getStringOr("advancement", "")),
                        null, null, set)));
            }
            case "dimension" -> f.add(new Text("dimension", l("dimension"), false).kind("dimension"));
            case "biome" -> f.add(new Text("biome", l("biome"), false).kind("biome"));
            case "structure" -> f.add(new Text("structure", l("structure"), false).kind("structure"));
            case "xp" -> {
                f.add(new Number("value", l("amount"), 5, 1, 1_000_000_000, false, true));
                f.add(new Bool("points", l("xp_points"), false).help(l("help.xp_points")));
                f.add(new Bool("consume", l("consume"), false).help(l("help.consume_xp")));
            }
            case "stat" -> {
                f.add(new Text("stat", l("stat"), false).kind("stat"));
                f.add(new Number("value", l("amount"), 1, 1, Long.MAX_VALUE, false, true));
            }
            case "observation" -> {
                f.add(new Bool("entity", l("observe_entity"), false));
                f.add(new Text("observe", l("observe"), false).validator(v -> EditorData.check(f.tag.getBooleanOr("entity", false) ? "entity" : "block", v))
                        .picker((form, set) -> PickerScreen.list(form, l("observe"),
                                EditorData.ids(form.tag.getBooleanOr("entity", false) ? "entity" : "block"), null, null, set)));
                f.add(new Number("timer", l("timer"), 20, 1, 1_000_000, false, true));
            }
            case "location" -> {
                f.add(new Action(l("position"), l("use_position"), form -> usePosition(form.tag)));
                f.add(new Text("dimension", l("dimension"), false).kind("dimension"));
                f.add(new Number("x", l("x"), 0, -30_000_000, 30_000_000, false));
                f.add(new Number("y", l("y"), 0, -2048, 2048, false));
                f.add(new Number("z", l("z"), 0, -30_000_000, 30_000_000, false));
                f.add(new Number("radius", l("radius"), 8, 0, 100_000, false));
            }
            default -> {
            }
        }
        f.add(new Text("title", l("custom_title"), true).help(l("help.custom_title")));
        f.add(new ItemField("icon", l("icon"), true, true, false));
        f.add(new Bool("optional_task", l("optional_task"), false).help(l("help.optional_task")));
        return f;
    }

    static void addItem(FormScreen form, String key, Tag item, boolean singleAsValue) {
        Tag cur = form.tag.get(key);
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        if (cur instanceof net.minecraft.nbt.ListTag cl) l.addAll(cl.copy());
        else if (cur != null) l.add(cur);
        l.add(item);
        form.tag.put(key, singleAsValue && l.size() == 1 ? l.get(0) : l);
    }

    static Component itemName(Tag t) {
        if (t instanceof StringTag s && (s.value().startsWith("#") || s.value().startsWith("@"))) return Component.literal(s.value());
        if (reg() == null) return Component.literal(t.toString());
        ItemSpec spec = ItemSpec.read(t);
        ItemStack st = spec.stack(reg());
        String id = t instanceof StringTag s ? s.value() : t instanceof CompoundTag c ? c.getStringOr("id", "") : "";
        return st.getHoverName().copy().append(Component.literal("  " + id).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    static ItemStack itemIcon(Tag t) {
        if (reg() == null) return ItemStack.EMPTY;
        ItemSpec spec = ItemSpec.read(t);
        if (spec.cycles()) {
            List<ItemStack> items = spec.tagItems();
            return items.isEmpty() ? new ItemStack(Items.BARRIER) : items.get((int) ((System.currentTimeMillis() / 1000) % items.size()));
        }
        return spec.stack(reg());
    }

    // ------------------------------------------------------------------------------------------------ rewards
    static Component rewardName(Tag t) {
        if (!(t instanceof CompoundTag c)) return Component.literal("?");
        String type = c.getStringOr("type", "item");
        try {
            Reward r = Rewards.create(type);
            r.read(c.copy());
            return Component.literal("[" + Component.translatable("questsicons.reward_type." + type).getString() + "] ").withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(r.title(reg()));
        } catch (Exception e) {
            return Component.literal(type);
        }
    }

    static ItemStack rewardIcon(Tag t) {
        if (!(t instanceof CompoundTag c) || reg() == null) return ItemStack.EMPTY;
        try {
            Reward r = Rewards.create(c.getStringOr("type", "item"));
            r.read(c.copy());
            return r.displayIcon(reg());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void addReward(FormScreen form, Quest q) {
        List<String> types = new ArrayList<>(Rewards.TYPES.keySet());
        List<Component> names = new ArrayList<>();
        for (String type : types) names.add(Component.translatable("questsicons.reward_type." + type));
        Minecraft.getInstance().setScreen(PickerScreen.list(form, l("pick_reward_type"), types, names, null, type -> {
            CompoundTag t = newReward(type);
            Minecraft.getInstance().setScreen(reward(form, t, saved -> {
                var list = form.tag.getListOrEmpty("rewards").copy();
                list.add(saved);
                form.tag.put("rewards", list);
            }));
        }));
    }

    private static void editReward(FormScreen form, Quest q, int i) {
        var list = form.tag.getListOrEmpty("rewards");
        if (!(list.get(i) instanceof CompoundTag t)) return;
        Minecraft.getInstance().setScreen(reward(form, t.copy(), saved -> {
            var l = form.tag.getListOrEmpty("rewards").copy();
            l.set(i, saved);
            form.tag.put("rewards", l);
        }));
    }

    public static CompoundTag newReward(String type) {
        CompoundTag t = new CompoundTag();
        t.putString("id", randomId());
        t.putString("type", type);
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case "xp" -> t.putInt("xp", 100);
            case "xp_levels" -> t.putInt("xp_levels", 5);
            case "command" -> t.putString("command", "/say {p} finished {quest}!");
            case "random" -> t.putString("table", "common_crate");
            case "loot" -> t.putString("loot_table", "minecraft:chests/simple_dungeon");
            case "advancement" -> t.putString("advancement", "minecraft:story/root");
            default -> {
            }
        }
        return t;
    }

    public static FormScreen reward(Screen parent, CompoundTag t, Consumer<CompoundTag> onSave) {
        String type = t.getStringOr("type", "item");
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.reward_title",
                Component.translatable("questsicons.reward_type." + type)), t, onSave);
        f.add(new Info(Component.translatable("questsicons.reward_help." + type)));
        switch (type) {
            case "item" -> {
                f.add(new ItemField("item", l("item"), false, false, false));
                f.add(new Number("count", l("count"), 1, 1, 100_000, false));
                f.add(new Number("random_bonus", l("random_bonus"), 0, 0, 100_000, false).help(l("help.random_bonus")));
            }
            case "xp" -> f.add(new Number("xp", l("xp"), 100, 1, 1_000_000_000, false));
            case "xp_levels" -> f.add(new Number("xp_levels", l("levels"), 5, 1, 100_000, false));
            case "command" -> {
                f.add(new Text("command", l("command"), false));
                f.add(new Info(l("help.command")));
                f.add(new Number("permission_level", l("permission_level"), 2, 2, 4, false));
                f.add(new Bool("elevate_perms", l("elevate"), true));
                f.add(new Bool("silent", l("silent"), true));
            }
            case "random" -> {
                f.add(new Text("table", l("table"), false).kind("table").help(l("help.table")));
                if (t.contains("items")) f.add(new Info(l("help.inline_items")));
            }
            case "choice" -> {
                ObjectList items = new ObjectList("items", l("choices"), Forms::itemName, Forms::itemIcon, null, l("add_item"),
                        form -> Minecraft.getInstance().setScreen(PickerScreen.items(form, l("choices"), false, st -> {
                            if (reg() != null) addItem(form, "items", ItemSpec.of(st.copyWithCount(1), reg()).write(), false);
                        }))).min(1);
                f.add(items);
                f.add(new AddId(l("add_id"), items, false));
            }
            case "loot" -> {
                f.add(new Text("loot_table", l("loot_table"), false).kind("loot_table"));
                f.add(new Number("rolls", l("rolls"), 1, 1, 64, false));
            }
            case "advancement" -> f.add(new Text("advancement", l("advancement"), false).kind("advancement"));
            default -> {
            }
        }
        f.add(new Text("title", l("custom_title"), true));
        f.add(new ItemField("icon", l("icon"), true, true, false));
        f.add(new TriBool("team_reward", l("team_reward")).help(l("help.team_reward")));
        f.add(new Bool("auto", l("auto_claim"), false));
        f.add(new Bool("exclude_from_claim_all", l("exclude_claim_all"), false));
        return f;
    }

    // ------------------------------------------------------------------------------------------------ chapters, groups, book
    public static FormScreen chapter(Screen parent, CompoundTag t, Consumer<CompoundTag> onSave) {
        QuestBook b = ClientQuests.book;
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.chapter_title"), t, onSave);
        f.add(new Text("title", l("title"), false));
        f.add(new Lines("subtitle", l("subtitle"), 2));
        f.add(new ItemField("icon", l("icon"), true, true, false));
        List<String> gv = new ArrayList<>();
        List<Component> gn = new ArrayList<>();
        gv.add("");
        gn.add(l("no_group"));
        if (b != null) {
            for (ChapterGroup g : b.groups) {
                gv.add(g.idString());
                gn.add(g.displayTitle());
            }
        }
        // A chapter that names its group by title keeps it.
        String cur = t.getStringOr("group", "");
        if (!cur.isEmpty() && !gv.contains(cur)) {
            gv.add(cur);
            gn.add(Component.literal(cur));
        }
        f.add(new Choice("group", l("group"), gv, gn));
        f.add(new Choice("default_quest_shape", l("default_shape"), shapes(true), shapeNames(true)));
        f.add(new Number("default_quest_size", l("default_size"), 1, 0.25, 4, true).help(l("help.default_size")));
        f.add(new Bool("hide_until_startable", l("hide_until_startable"), false));
        f.add(new Bool("always_invisible", l("always_invisible"), false).help(l("help.always_invisible")));
        f.add(new Info(Component.translatable("questsicons.form.filename", t.getStringOr("filename", "?"))));
        return f;
    }

    public static FormScreen group(Screen parent, CompoundTag t, Consumer<CompoundTag> onSave) {
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.group_title"), t, onSave);
        f.add(new Text("title", l("title"), false));
        f.add(new ItemField("icon", l("icon"), true, true, false));
        return f;
    }

    public static FormScreen book(Screen parent, CompoundTag t, Consumer<CompoundTag> onSave) {
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.book_title"), t, onSave);
        f.add(new Text("title", l("title"), false));
        f.add(new ItemField("icon", l("icon"), true, true, false));
        f.add(new Choice("default_quest_shape", l("default_shape"), shapes(false), shapeNames(false)));
        f.add(new Bool("default_reward_team", l("default_team_reward"), false).help(l("help.team_reward")));
        f.add(new Bool("give_book_on_first_join", l("give_book"), true));
        f.add(new Text("background", l("background"), true).validator(Forms::checkTexture)
                .picker((form, set) -> PickerScreen.images(form, set)).help(l("help.background")));
        return f;
    }

    static String checkTexture(String v) {
        var id = net.minecraft.resources.Identifier.tryParse(v);
        if (id == null) return "questsicons.form.error.bad_id";
        return Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? null : "questsicons.form.error.no_texture";
    }

    // ------------------------------------------------------------------------------------------------ map pictures
    public static FormScreen image(Screen parent, long questId, CompoundTag t, Consumer<CompoundTag> onSave) {
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.image_title"), t, onSave);
        f.add(new Text("image", l("image"), false).validator(Forms::checkTexture).picker((form, set) -> PickerScreen.images(form, set)));
        f.add(new Number("x", l("x"), 0, -10000, 10000, true));
        f.add(new Number("y", l("y"), 0, -10000, 10000, true));
        f.add(new Number("width", l("width"), 1, 0.05, 200, true));
        f.add(new Number("height", l("height"), 1, 0.05, 200, true));
        f.add(new Number("rotation", l("rotation"), 0, -360, 360, true));
        f.add(new Color("color", l("color"), 0xFFFFFF));
        f.add(new Number("alpha", l("alpha"), 255, 0, 255, false));
        f.add(new Text("hover", l("hover"), true));
        f.add(new Bool("front", l("front"), false).help(l("help.front")));
        f.add(new Text("dependency", l("image_dependency"), true).validator(v ->
                ClientQuests.book != null && ClientQuests.book.quest(QuestObject.parseId(v)) != null ? null : "questsicons.form.error.unknown_quest")
                .picker((form, set) -> questPicker(form, 0, set)).help(l("help.image_dependency")));
        return f;
    }

    // ------------------------------------------------------------------------------------------------ reward tables
    public static FormScreen table(Screen parent, String key, CompoundTag t, boolean isNew, Consumer<CompoundTag> onSave) {
        QuestBook b = ClientQuests.book;
        if (isNew) t.putString("__key", key);
        FormScreen f = new FormScreen(parent, Component.translatable("questsicons.form.table_title", key.isEmpty() ? l("new_table") : Component.literal(key)), t, onSave);
        if (isNew) {
            f.add(new Text("__key", l("table_key"), false).validator(v -> !v.matches("[a-z0-9_.-]+") ? "questsicons.form.error.key"
                    : b != null && b.rewardTables.containsKey(v) ? "questsicons.form.error.key_taken" : null).help(l("help.table_key")));
        } else {
            f.add(new Info(Component.translatable("questsicons.form.table_key_info", key)));
        }
        f.add(new Text("title", l("title"), true));
        f.add(new ItemField("icon", l("icon"), true, true, false));
        f.add(new ObjectList("entries", l("entries"), Forms::entryName, t2 -> t2 instanceof CompoundTag c && reg() != null
                ? ItemSpec.read(c.get("item")).stack(reg()) : ItemStack.EMPTY,
                (form, i) -> {
                    var list = form.tag.getListOrEmpty("entries");
                    if (!(list.get(i) instanceof CompoundTag e)) return;
                    Minecraft.getInstance().setScreen(entry(form, e.copy(), saved -> {
                        var l = form.tag.getListOrEmpty("entries").copy();
                        l.set(i, saved);
                        form.tag.put("entries", l);
                    }));
                }, l("add_entry"), form -> {
                    CompoundTag e = new CompoundTag();
                    e.putInt("weight", 10);
                    Minecraft.getInstance().setScreen(entry(form, e, saved -> {
                        var l = form.tag.getListOrEmpty("entries").copy();
                        l.add(saved);
                        form.tag.put("entries", l);
                    }));
                }));
        if (key.endsWith("_crate")) {
            f.add(new Info(l("help.crate_table")));
        }
        return f;
    }

    static Component entryName(Tag t) {
        if (!(t instanceof CompoundTag c)) return Component.literal("?");
        Component item = itemName(c.get("item") == null ? StringTag.valueOf("minecraft:air") : c.get("item"));
        int count = c.getIntOr("count", 1);
        int bonus = c.getIntOr("random_bonus", 0);
        return Component.literal(count + (bonus > 0 ? "-" + (count + bonus) : "") + "× ").append(item)
                .append(Component.literal("  w" + c.getIntOr("weight", 10)).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    public static FormScreen entry(Screen parent, CompoundTag t, Consumer<CompoundTag> onSave) {
        FormScreen f = new FormScreen(parent, l("entry_title"), t, onSave);
        f.add(new ItemField("item", l("item"), false, false, false));
        f.add(new Number("count", l("count"), 1, 1, 100_000, false));
        f.add(new Number("weight", l("weight"), 10, 1, 1_000_000, false).help(l("help.weight")));
        f.add(new Number("random_bonus", l("random_bonus"), 0, 0, 100_000, false).help(l("help.random_bonus")));
        return f;
    }
}
