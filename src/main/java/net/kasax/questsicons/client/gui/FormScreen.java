package net.kasax.questsicons.client.gui;

import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.EditorData;
import net.kasax.questsicons.quest.ItemSpec;
import net.kasax.questsicons.quest.QuestBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A form that edits one object of the quest book (quest, chapter, task, reward, table, picture, ...) as a working
 * copy of its SNBT tag. Every field writes into the copy as you type or pick; Save checks every field (red field and a
 * message instead of saving broken data) and hands the copy over, Cancel drops it. Lists of objects (tasks, rewards,
 * table entries) open their own form for one element.
 */
public class FormScreen extends QiScreen {
    public final CompoundTag tag;
    private final List<Field> fields = new ArrayList<>();
    private final Consumer<CompoundTag> onSave;
    private @Nullable Function<CompoundTag, String> check;
    private int scroll;
    private int contentH;
    private boolean showErrors;
    private String error = "";
    private static final int LABEL_W = 112;

    public FormScreen(Screen parent, Component title, CompoundTag tag, Consumer<CompoundTag> onSave) {
        super(title, parent);
        this.tag = tag;
        this.onSave = onSave;
    }

    public FormScreen add(Field f) {
        fields.add(f);
        return this;
    }

    /** An extra whole-form check: returns a translation key of the problem or null. */
    public FormScreen check(Function<CompoundTag, String> check) {
        this.check = check;
        return this;
    }

    static HolderLookup.Provider reg() {
        QuestBook b = ClientQuests.book;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (b != null && b.registries != null) return b.registries;
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    @Override
    protected void init() {
        super.init();
        for (Field f : fields) f.init(this);
    }

    int formW() {
        return Math.min(width - 24, 400);
    }

    int formX() {
        return (width - formW()) / 2;
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int fx = formX();
        int fw = formW();
        int top = 26;
        int bottom = height - 30;
        QuestBookScreen.frame(g, fx - 6, top - 4, fw + 12, bottom - top + 8, QuestBookScreen.PANEL, QuestBookScreen.BORDER);
        g.centeredText(font, title, width / 2, 10, QuestBookScreen.TEXT);
        int total = 0;
        for (Field f : fields) total += f.height(this) + 4;
        contentH = total;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (bottom - top)));
        g.enableScissor(fx - 5, top, fx + fw + 5, bottom);
        clipTop = top;
        clipBottom = bottom;
        int y = top + 2 - scroll;
        for (Field f : fields) {
            int h = f.height(this);
            boolean vis = y >= top - 1 && y + h <= bottom + 1;
            f.layout(this, fx, y, fw, vis);
            String err = showErrors ? f.validate(this) : null;
            if (err != null) g.fill(fx - 3, y - 1, fx - 1, y + h + 1, QuestBookScreen.RED);
            if (y + h >= top && y <= bottom) {
                if (f.label != null) {
                    g.text(font, QuestBookScreen.clip(font, f.label, LABEL_W - 6), fx, y + 5, err != null ? QuestBookScreen.RED : QuestBookScreen.AVAILABLE, false);
                    if (f.help != null && hovered(fx, y, LABEL_W, 16, mouseX, mouseY)) zone(fx, y, LABEL_W, 16, () -> {
                    }, f.help, null);
                }
                f.draw(this, g, fx + LABEL_W, y, fw - LABEL_W, mouseX, mouseY);
                if (err != null) g.text(font, Component.translatable(err), fx + LABEL_W, y + h - 9, QuestBookScreen.RED, false);
            }
            y += h + 4;
        }
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        g.disableScissor();
        if (contentH > bottom - top) {
            int track = bottom - top;
            int bar = Math.max(12, track * track / contentH);
            int by = top + (track - bar) * scroll / Math.max(1, contentH - track);
            g.fill(fx + fw + 2, by, fx + fw + 4, by + bar, 0xA0C084FC);
        }
        int by = height - 24;
        button(g, width / 2 - 104, by, 100, 18, Component.translatable("questsicons.edit.save"), mouseX, mouseY, this::save);
        button(g, width / 2 + 4, by, 100, 18, Component.translatable("gui.cancel"), mouseX, mouseY, this::back);
        if (!error.isEmpty()) g.centeredText(font, Component.translatable(error), width / 2, by - 11 > bottom ? by - 11 : bottom + 2, QuestBookScreen.RED);
    }

    public boolean valid() {
        for (Field f : fields) if (f.validate(this) != null) return false;
        return check == null || check.apply(tag) == null;
    }

    /** Save: every field must be valid, then the copy goes to whoever opened the form. */
    public void save() {
        showErrors = true;
        int y = 0;
        for (Field f : fields) {
            if (f.validate(this) != null) {
                error = "questsicons.form.error.fix";
                scroll = y;
                return;
            }
            y += f.height(this) + 4;
        }
        if (check != null) {
            String e = check.apply(tag);
            if (e != null) {
                error = e;
                return;
            }
        }
        for (Field f : fields) f.finish(this);
        back();
        onSave.accept(tag);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (super.mouseScrolled(mx, my, sx, sy)) return true;
        scroll = Math.max(0, scroll - (int) (sy * 20));
        return true;
    }

    /** For tests: the text box of the field with this label (to click into it), or null. */
    public float[] fieldPos(String label) {
        for (Field f : fields) {
            if (f.label != null && f.label.getString().equals(label) && f.box() != null) {
                var b = f.box();
                return new float[]{b.getX() + 6, b.getY() + b.getHeight() / 2f};
            }
        }
        return null;
    }

    // =================================================================================================================
    // Fields
    // =================================================================================================================

    public abstract static class Field {
        final @Nullable Component label;
        @Nullable Component help;

        Field(@Nullable Component label) {
            this.label = label;
        }

        public Field help(Component help) {
            this.help = help;
            return this;
        }

        int height(FormScreen f) {
            return 20;
        }

        void init(FormScreen f) {
        }

        void layout(FormScreen f, int x, int y, int w, boolean visible) {
        }

        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        }

        /** Null if fine, else the translation key of the problem. */
        @Nullable String validate(FormScreen f) {
            return null;
        }

        void finish(FormScreen f) {
        }

        @Nullable net.minecraft.client.gui.components.AbstractWidget box() {
            return null;
        }
    }

    /** One line of text. Optional: empty removes the key. A kind ("entity", "biome", ...) adds a picker and checks the id. */
    public static class Text extends Field {
        final String key;
        final boolean optional;
        @Nullable String kind;
        @Nullable Function<String, String> validator;
        @Nullable java.util.function.BiFunction<FormScreen, Consumer<String>, Screen> picker;
        EditBox edit;
        int maxLength = 512;

        /** A picker button that opens this screen; the picked value goes into the field. */
        public Text picker(java.util.function.BiFunction<FormScreen, Consumer<String>, Screen> picker) {
            this.picker = picker;
            return this;
        }

        public Text(String key, Component label, boolean optional) {
            super(label);
            this.key = key;
            this.optional = optional;
        }

        public Text kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Text validator(Function<String, String> v) {
            this.validator = v;
            return this;
        }

        @Override
        int height(FormScreen f) {
            return f.showErrors && validate(f) != null ? 30 : 20;
        }

        String value(FormScreen f) {
            return f.tag.getStringOr(key, "");
        }

        @Override
        void init(FormScreen f) {
            edit = new EditBox(f.font, 0, 0, 100, 16, label == null ? Component.empty() : label);
            edit.setMaxLength(maxLength);
            edit.setValue(value(f));
            edit.setResponder(s -> {
                if (s.isEmpty() && optional) f.tag.remove(key);
                else f.tag.putString(key, s);
            });
            f.addRenderableWidget(edit);
        }

        @Override
        void layout(FormScreen f, int x, int y, int w, boolean visible) {
            int pick = kind != null || picker != null ? 22 : 0;
            edit.setX(x + LABEL_W);
            edit.setY(y + 2);
            edit.setWidth(w - LABEL_W - pick);
            edit.visible = visible;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            if (picker != null) {
                f.button(g, x + w - 20, y + 2, 20, 16, Component.literal("…"), mx, my, () ->
                        f.minecraft.gui.setScreen(picker.apply(f, v -> f.tag.putString(key, v))));
                return;
            }
            if (kind == null) return;
            f.button(g, x + w - 20, y + 2, 20, 16, Component.literal("…"), mx, my, () -> {
                List<String> ids = EditorData.ids(kind);
                f.minecraft.gui.setScreen(PickerScreen.list(f, label, ids, null, null, v -> f.tag.putString(key, v)));
            });
        }

        @Override
        @Nullable String validate(FormScreen f) {
            String v = value(f);
            if (v.isEmpty()) return optional ? null : "questsicons.form.error.empty";
            if (validator != null) {
                String e = validator.apply(v);
                if (e != null) return e;
            }
            if (kind != null) return EditorData.check(kind, v);
            return null;
        }

        @Override
        net.minecraft.client.gui.components.AbstractWidget box() {
            return edit;
        }
    }

    /** A whole number or a decimal number in a range. */
    public static class Number extends Field {
        final String key;
        final boolean decimal;
        final double min;
        final double max;
        final double def;
        final boolean isLong;
        EditBox edit;

        public Number(String key, Component label, double def, double min, double max, boolean decimal) {
            this(key, label, def, min, max, decimal, false);
        }

        public Number(String key, Component label, double def, double min, double max, boolean decimal, boolean isLong) {
            super(label);
            this.key = key;
            this.def = def;
            this.min = min;
            this.max = max;
            this.decimal = decimal;
            this.isLong = isLong;
        }

        @Override
        int height(FormScreen f) {
            return f.showErrors && validate(f) != null ? 30 : 20;
        }

        String text(FormScreen f) {
            Tag t = f.tag.get(key);
            if (t == null) return fmt(def);
            if (t instanceof net.minecraft.nbt.NumericTag n) return decimal ? fmt(n.doubleValue()) : Long.toString(n.longValue());
            return t instanceof StringTag s ? s.value() : t.toString();
        }

        static String fmt(double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }

        @Override
        void init(FormScreen f) {
            edit = new EditBox(f.font, 0, 0, 80, 16, label);
            edit.setValue(text(f));
            edit.setResponder(s -> store(f, s));
            f.addRenderableWidget(edit);
        }

        void store(FormScreen f, String s) {
            try {
                if (decimal) {
                    f.tag.putDouble(key, Double.parseDouble(s.trim()));
                } else if (isLong) {
                    f.tag.putLong(key, Long.parseLong(s.trim()));
                } else {
                    f.tag.putInt(key, Integer.parseInt(s.trim()));
                }
            } catch (NumberFormatException e) {
                f.tag.putString(key, s);   // shown as an error until it is a number
            }
        }

        @Override
        void layout(FormScreen f, int x, int y, int w, boolean visible) {
            edit.setX(x + LABEL_W);
            edit.setY(y + 2);
            edit.setWidth(Math.min(110, w - LABEL_W));
            edit.visible = visible;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            String range = decimal ? fmt(min) + " – " + fmt(max) : (long) min + " – " + (max >= Integer.MAX_VALUE ? "∞" : Long.toString((long) max));
            g.text(f.font, range, x + Math.min(110, w) + 6, y + 6, QuestBookScreen.MUTED, false);
        }

        @Override
        @Nullable String validate(FormScreen f) {
            Tag t = f.tag.get(key);
            if (t == null) return null;
            if (!(t instanceof net.minecraft.nbt.NumericTag n)) return "questsicons.form.error.number";
            double v = n.doubleValue();
            if (v < min || v > max) return "questsicons.form.error.range";
            return null;
        }

        @Override
        net.minecraft.client.gui.components.AbstractWidget box() {
            return edit;
        }
    }

    /** Yes / no. Written only when it differs from the default. */
    public static class Bool extends Field {
        final String key;
        final boolean def;

        public Bool(String key, Component label, boolean def) {
            super(label);
            this.key = key;
            this.def = def;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            boolean on = f.tag.getBooleanOr(key, def);
            Component t = Component.translatable(on ? "questsicons.form.yes" : "questsicons.form.no");
            f.button(g, x, y + 2, 60, 16, t.copy().withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY),
                    mx, my, () -> {
                        boolean n = !f.tag.getBooleanOr(key, def);
                        if (n == def) f.tag.remove(key);
                        else f.tag.putBoolean(key, n);
                    });
            if (help != null) g.text(f.font, QuestBookScreen.clip(f.font, help, w - 68), x + 66, y + 6, QuestBookScreen.MUTED, false);
        }
    }

    /** One of a few values; a click goes to the next. The first value "" means "not set". */
    public static class Choice extends Field {
        final String key;
        final List<String> values;
        final List<Component> names;

        public Choice(String key, Component label, List<String> values, List<Component> names) {
            super(label);
            this.key = key;
            this.values = values;
            this.names = names;
        }

        int index(FormScreen f) {
            int i = values.indexOf(f.tag.getStringOr(key, values.get(0)));
            return Math.max(0, i);
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            int i = index(f);
            f.button(g, x, y + 2, Math.min(170, w), 16, Component.literal("◀ ").append(names.get(i)).append(" ▶"), mx, my, () -> {
                int n = (index(f) + 1) % values.size();
                if (values.get(n).isEmpty()) f.tag.remove(key);
                else f.tag.putString(key, values.get(n));
            });
        }
    }

    /** Several lines of text (quest description, chapter subtitle), stored as a list of strings. */
    public static class Lines extends Field {
        final String key;
        final int lines;
        MultiLineEditBox edit;

        public Lines(String key, Component label, int lines) {
            super(label);
            this.key = key;
            this.lines = lines;
        }

        @Override
        int height(FormScreen f) {
            return lines * 9 + 10 + (help != null ? 11 : 0);
        }

        @Override
        void init(FormScreen f) {
            StringBuilder sb = new StringBuilder();
            Tag t = f.tag.get(key);
            if (t instanceof ListTag l) {
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(l.get(i) instanceof StringTag s ? s.value() : l.get(i).toString());
                }
            } else if (t instanceof StringTag s) {
                sb.append(s.value());
            }
            edit = MultiLineEditBox.builder().setShowBackground(true).build(f.font, 200, lines * 9 + 8, label);
            edit.setCharacterLimit(20000);
            edit.setValue(sb.toString());
            edit.setValueListener(s -> {
                if (s.isEmpty()) {
                    f.tag.remove(key);
                    return;
                }
                ListTag l = new ListTag();
                for (String line : s.split("\n", -1)) l.add(StringTag.valueOf(line));
                f.tag.put(key, l);
            });
            f.addRenderableWidget(edit);
        }

        @Override
        void layout(FormScreen f, int x, int y, int w, boolean visible) {
            edit.setX(x + LABEL_W);
            edit.setY(y + 1);
            edit.setWidth(w - LABEL_W);
            edit.visible = visible;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            if (help != null) g.text(f.font, QuestBookScreen.clip(f.font, help, w), x, y + lines * 9 + 11, QuestBookScreen.MUTED, false);
        }

        @Override
        net.minecraft.client.gui.components.AbstractWidget box() {
            return edit;
        }
    }

    /** A line of help text. */
    public static class Info extends Field {
        final Component text;

        public Info(Component text) {
            super(null);
            this.text = text;
        }

        @Override
        int height(FormScreen f) {
            return f.font.split(text, f.formW()).size() * 10 + 2;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            int yy = y + 1;
            for (var line : f.font.split(text, f.formW())) {
                g.text(f.font, line, x - LABEL_W, yy, QuestBookScreen.MUTED, false);
                yy += 10;
            }
        }
    }

    /** A button row (e.g. "Use my position"). */
    public static class Action extends Field {
        final Component text;
        final Consumer<FormScreen> action;

        public Action(Component label, Component text, Consumer<FormScreen> action) {
            super(label);
            this.text = text;
            this.action = action;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            f.button(g, x, y + 2, Math.min(w, f.font.width(text) + 16), 16, text, mx, my, () -> {
                action.accept(f);
                f.rebuildWidgets();
            });
        }
    }

    /**
     * An item: the picker (icons first or all items first), the item in the hand, or typed (an id, {@code #tag} or
     * {@code @mod}). Stored the way the quest files store items.
     */
    public static class ItemField extends Field {
        final String key;
        final boolean optional;
        final boolean iconsFirst;
        final boolean allowTags;
        EditBox edit;
        private boolean syncing;

        public ItemField(String key, Component label, boolean optional, boolean iconsFirst, boolean allowTags) {
            super(label);
            this.key = key;
            this.optional = optional;
            this.iconsFirst = iconsFirst;
            this.allowTags = allowTags;
        }

        @Override
        int height(FormScreen f) {
            return f.showErrors && validate(f) != null ? 32 : 22;
        }

        ItemSpec spec(FormScreen f) {
            return ItemSpec.read(f.tag.get(key));
        }

        String text(FormScreen f) {
            Tag t = f.tag.get(key);
            if (t == null) return "";
            if (t instanceof StringTag s) return s.value();
            if (t instanceof CompoundTag c) return c.getStringOr("id", "");
            return t.toString();
        }

        @Override
        void init(FormScreen f) {
            edit = new EditBox(f.font, 0, 0, 100, 16, label);
            edit.setMaxLength(256);
            edit.setValue(text(f));
            edit.setResponder(s -> {
                if (syncing) return;
                if (s.isEmpty()) f.tag.remove(key);
                else f.tag.putString(key, s.trim());
            });
            f.addRenderableWidget(edit);
        }

        @Override
        void layout(FormScreen f, int x, int y, int w, boolean visible) {
            edit.setX(x + LABEL_W + 22);
            edit.setY(y + 3);
            edit.setWidth(Math.max(40, w - LABEL_W - 22 - 104));
            edit.visible = visible;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            QuestBookScreen.frame(g, x, y + 1, 20, 20, 0xFF1C1230, 0xFF5B3A86);
            HolderLookup.Provider reg = reg();
            ItemSpec s = spec(f);
            if (!s.isEmpty() && reg != null) {
                ItemStack st = s.stack(reg);
                g.fakeItem(st, x + 2, y + 3);
                if (f.hovered(x, y + 1, 20, 20, mx, my)) f.zone(x, y + 1, 20, 20, () -> {
                }, st.getHoverName(), null);
            }
            int bx = x + w - 100;
            f.button(g, bx, y + 3, 50, 16, Component.translatable("questsicons.form.pick"), mx, my, () ->
                    f.minecraft.gui.setScreen(PickerScreen.items(f, label, iconsFirst, st -> set(f, st))));
            f.button(g, bx + 52, y + 3, 48, 16, Component.translatable("questsicons.form.hand"), mx, my, () -> {
                if (f.minecraft.player != null && !f.minecraft.player.getMainHandItem().isEmpty()) {
                    set(f, f.minecraft.player.getMainHandItem().copyWithCount(1));
                    syncing = true;
                    edit.setValue(text(f));
                    syncing = false;
                }
            });
        }

        void set(FormScreen f, ItemStack st) {
            HolderLookup.Provider reg = reg();
            if (reg == null) return;
            f.tag.put(key, ItemSpec.of(st.copyWithCount(1), reg).write());
        }

        @Override
        @Nullable String validate(FormScreen f) {
            Tag t = f.tag.get(key);
            if (t == null) return optional ? null : "questsicons.form.error.no_item";
            return checkItem(t, allowTags);
        }

        @Override
        net.minecraft.client.gui.components.AbstractWidget box() {
            return edit;
        }
    }

    /** Null if the stored item reference is known: an item id, a known item tag, a mod with items, or a list of these. */
    static @Nullable String checkItem(Tag t, boolean allowTags) {
        if (t instanceof ListTag l) {
            if (l.isEmpty()) return "questsicons.form.error.no_item";
            for (Tag e : l) {
                String r = checkItem(e, allowTags);
                if (r != null) return r;
            }
            return null;
        }
        String id = t instanceof StringTag s ? s.value().trim() : t instanceof CompoundTag c ? c.getStringOr("id", "") : "";
        if (id.isEmpty()) return "questsicons.form.error.no_item";
        if (id.startsWith("#")) {
            if (!allowTags) return "questsicons.form.error.no_tags";
            Identifier tid = Identifier.tryParse(id.substring(1));
            if (tid == null) return "questsicons.form.error.bad_id";
            boolean known = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, tid)).isPresent();
            return known ? null : "questsicons.form.error.unknown_tag";
        }
        if (id.startsWith("@")) {
            if (!allowTags) return "questsicons.form.error.no_tags";
            String mod = id.substring(1);
            for (Identifier k : BuiltInRegistries.ITEM.keySet()) if (k.getNamespace().equals(mod)) return null;
            return "questsicons.form.error.unknown_mod";
        }
        Identifier iid = Identifier.tryParse(id);
        if (iid == null) return "questsicons.form.error.bad_id";
        return BuiltInRegistries.ITEM.containsKey(iid) ? null : "questsicons.form.error.unknown_item";
    }

    /**
     * A list of objects stored under a key (tasks, rewards, table entries, items of a choice, dependencies): one row each
     * with Edit, up, down and remove, and an "add" button.
     */
    public static class ObjectList extends Field {
        final String key;
        final Function<Tag, Component> rowText;
        final Function<Tag, ItemStack> rowIcon;
        final @Nullable BiConsumer<FormScreen, Integer> onEdit;
        final Component addText;
        final Consumer<FormScreen> onAdd;
        boolean singleAsValue;   // one element is stored as the element itself (FTB's "item" may be a list)
        int min;

        public ObjectList(String key, Component label, Function<Tag, Component> rowText, Function<Tag, ItemStack> rowIcon,
                          @Nullable BiConsumer<FormScreen, Integer> onEdit, Component addText, Consumer<FormScreen> onAdd) {
            super(label);
            this.key = key;
            this.rowText = rowText;
            this.rowIcon = rowIcon;
            this.onEdit = onEdit;
            this.addText = addText;
            this.onAdd = onAdd;
        }

        public ObjectList singleAsValue() {
            singleAsValue = true;
            return this;
        }

        public ObjectList min(int min) {
            this.min = min;
            return this;
        }

        ListTag list(FormScreen f) {
            Tag t = f.tag.get(key);
            if (t instanceof ListTag l) return l;
            ListTag l = new ListTag();
            if (t != null) l.add(t);
            return l;
        }

        void store(FormScreen f, ListTag l) {
            if (l.isEmpty()) f.tag.remove(key);
            else if (singleAsValue && l.size() == 1) f.tag.put(key, l.get(0));
            else f.tag.put(key, l);
        }

        @Override
        int height(FormScreen f) {
            return Math.max(1, list(f).size()) * 18 + 20 + (f.showErrors && validate(f) != null ? 10 : 0);
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            ListTag l = list(f);
            int yy = y;
            if (l.isEmpty()) {
                g.text(f.font, Component.translatable("questsicons.form.none"), x + 4, yy + 5, QuestBookScreen.MUTED, false);
                yy += 18;
            }
            for (int i = 0; i < l.size(); i++) {
                final int idx = i;
                Tag e = l.get(i);
                g.fill(x, yy, x + w, yy + 17, 0x30FFFFFF);
                ItemStack icon = rowIcon.apply(e);
                int tx = x + 4;
                if (icon != null && !icon.isEmpty()) {
                    g.fakeItem(icon, x + 1, yy);
                    tx = x + 20;
                }
                int bw = 16;
                int right = x + w - 2;
                right -= bw;
                f.button(g, right, yy + 1, bw, 15, Component.literal("✖"), mx, my, () -> {
                    ListTag n = list(f).copy();
                    n.remove(idx);
                    store(f, n);
                });
                right -= bw + 1;
                if (i < l.size() - 1) f.button(g, right, yy + 1, bw, 15, Component.literal("▼"), mx, my, () -> swap(f, idx, idx + 1));
                right -= bw + 1;
                if (i > 0) f.button(g, right, yy + 1, bw, 15, Component.literal("▲"), mx, my, () -> swap(f, idx, idx - 1));
                if (onEdit != null) {
                    Component et = Component.translatable("questsicons.form.edit");
                    int ew = f.font.width(et) + 10;
                    right -= ew + 1;
                    f.button(g, right, yy + 1, ew, 15, et, mx, my, () -> onEdit.accept(f, idx));
                }
                g.text(f.font, QuestBookScreen.clip(f.font, rowText.apply(e), right - tx - 4), tx, yy + 5, QuestBookScreen.TEXT, false);
                yy += 18;
            }
            int aw = f.font.width(addText) + 14;
            f.button(g, x, yy + 1, aw, 16, addText, mx, my, () -> onAdd.accept(f));
        }

        void swap(FormScreen f, int a, int b) {
            ListTag n = list(f).copy();
            Tag ta = n.get(a);
            n.set(a, n.get(b));
            n.set(b, ta);
            store(f, n);
        }

        void addElement(FormScreen f, Tag e) {
            ListTag n = list(f).copy();
            n.add(e);
            store(f, n);
        }

        void setElement(FormScreen f, int i, Tag e) {
            ListTag n = list(f).copy();
            if (i >= 0 && i < n.size()) n.set(i, e);
            else n.add(e);
            store(f, n);
        }

        @Override
        @Nullable String validate(FormScreen f) {
            return list(f).size() < min ? "questsicons.form.error.need_one" : null;
        }
    }

    /** Default / yes / no (e.g. "team reward": not set follows the book's default). */
    public static class TriBool extends Field {
        final String key;

        public TriBool(String key, Component label) {
            super(label);
            this.key = key;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            int state = !f.tag.contains(key) ? 0 : f.tag.getBooleanOr(key, false) ? 1 : 2;
            Component t = Component.translatable(state == 0 ? "questsicons.form.default" : state == 1 ? "questsicons.form.yes" : "questsicons.form.no");
            f.button(g, x, y + 2, 80, 16, t, mx, my, () -> {
                int n = (state + 1) % 3;
                if (n == 0) f.tag.remove(key);
                else f.tag.putBoolean(key, n == 1);
            });
        }
    }

    /** A colour as RRGGBB, stored as a number. */
    public static class Color extends Field {
        final String key;
        final int def;
        EditBox edit;

        public Color(String key, Component label, int def) {
            super(label);
            this.key = key;
            this.def = def;
        }

        @Override
        void init(FormScreen f) {
            edit = new EditBox(f.font, 0, 0, 70, 16, label);
            edit.setMaxLength(7);
            edit.setValue(String.format(Locale.ROOT, "%06X", f.tag.getIntOr(key, def) & 0xFFFFFF));
            edit.setResponder(s -> {
                try {
                    f.tag.putInt(key, Integer.parseInt(s.replace("#", "").trim(), 16));
                } catch (NumberFormatException e) {
                    f.tag.putString(key, s);
                }
            });
            f.addRenderableWidget(edit);
        }

        @Override
        void layout(FormScreen f, int x, int y, int w, boolean visible) {
            edit.setX(x + LABEL_W);
            edit.setY(y + 2);
            edit.visible = visible;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            int c = f.tag.getIntOr(key, def);
            g.fill(x + 76, y + 3, x + 90, y + 17, 0xFF000000 | c);
        }

        @Override
        @Nullable String validate(FormScreen f) {
            return f.tag.get(key) instanceof StringTag ? "questsicons.form.error.color" : null;
        }
    }

    /** Typing an id, tag or mod and "Add" appends it to an item list (e.g. "#minecraft:logs" as an alternative). */
    public static class AddId extends Field {
        final ObjectList target;
        final boolean allowTags;
        EditBox edit;
        String err;

        public AddId(Component label, ObjectList target, boolean allowTags) {
            super(label);
            this.target = target;
            this.allowTags = allowTags;
        }

        @Override
        int height(FormScreen f) {
            return err != null ? 30 : 20;
        }

        @Override
        void init(FormScreen f) {
            edit = new EditBox(f.font, 0, 0, 100, 16, label);
            edit.setMaxLength(256);
            edit.setHint(Component.literal(allowTags ? "minecraft:oak_log, #minecraft:logs, @create" : "minecraft:diamond"));
            f.addRenderableWidget(edit);
        }

        @Override
        void layout(FormScreen f, int x, int y, int w, boolean visible) {
            edit.setX(x + LABEL_W);
            edit.setY(y + 2);
            edit.setWidth(w - LABEL_W - 50);
            edit.visible = visible;
        }

        @Override
        void draw(FormScreen f, GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
            f.button(g, x + w - 46, y + 2, 46, 16, Component.translatable("questsicons.form.add"), mx, my, () -> {
                String v = edit.getValue().trim();
                err = checkItem(StringTag.valueOf(v), allowTags);
                if (err == null) {
                    target.addElement(f, StringTag.valueOf(v));
                    edit.setValue("");
                }
            });
            if (err != null) g.text(f.font, Component.translatable(err), x, y + 21, QuestBookScreen.RED, false);
        }

        @Override
        net.minecraft.client.gui.components.AbstractWidget box() {
            return edit;
        }
    }

    /** Lower-case id helper for the forms. */
    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    static Supplier<Void> nothing() {
        return () -> null;
    }
}
