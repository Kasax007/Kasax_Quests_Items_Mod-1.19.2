package net.kasax.questsicons.client.gui;

import net.kasax.questsicons.icons.IconLibrary;
import net.kasax.questsicons.item.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The editor's pickers: a search box, category tabs on the left and a grid (items, icons, map pictures) or a list
 * (ids such as mobs, biomes, advancements, quests). One click picks and goes back.
 *
 * <p>Item mode: "All icons", one tab per icon category, then the player's inventory and every item of the game.
 */
public class PickerScreen extends QiScreen {
    public enum Mode { ITEMS, IMAGES, LIST }

    /** A tab: an id for tests ("tab:&lt;id&gt;"), its name and what it holds. */
    private record Tab(String id, Component name, List<Entry> entries) {
    }

    /** One pickable thing: the item or picture or text it shows, the value it returns and what search looks at. */
    private record Entry(ItemStack stack, @Nullable IconLibrary.MapImage image, String value, Component label, String search) {
    }

    private final Mode mode;
    private final List<Tab> tabs = new ArrayList<>();
    private final @Nullable Consumer<ItemStack> onItem;
    private final @Nullable Consumer<String> onValue;
    private int tab;
    private int scroll;
    private int tabScroll;
    private String query = "";
    private EditBox search;
    private static final int CELL = 20;
    private static final int TAB_W = 112;

    private PickerScreen(Component title, Screen parent, Mode mode, @Nullable Consumer<ItemStack> onItem, @Nullable Consumer<String> onValue) {
        super(title, parent);
        this.mode = mode;
        this.onItem = onItem;
        this.onValue = onValue;
    }

    /** Icons first (the library by category), then the inventory and all items. */
    public static PickerScreen items(Screen parent, Component title, boolean iconsFirst, Consumer<ItemStack> onPick) {
        PickerScreen p = new PickerScreen(title, parent, Mode.ITEMS, onPick, null);
        List<Entry> allIcons = new ArrayList<>();
        List<Tab> cats = new ArrayList<>();
        for (IconLibrary.Category c : IconLibrary.CATEGORIES) {
            List<Entry> list = new ArrayList<>();
            Component cname = Component.translatable("questsicons.icons.category." + c.id());
            for (IconLibrary.Icon i : c.icons()) {
                Item item = ModItems.ICON_BY_ID.get(i.id());
                if (item == null) continue;
                ItemStack s = new ItemStack(item);
                Entry e = new Entry(s, null, "questsicons:" + i.id(), s.getHoverName(),
                        (s.getHoverName().getString() + " " + i.id() + " " + i.keywords() + " " + cname.getString()).toLowerCase(Locale.ROOT));
                list.add(e);
                allIcons.add(e);
            }
            cats.add(new Tab(c.id(), cname, list));
        }
        List<Entry> inv = new ArrayList<>();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.isEmpty()) continue;
                inv.add(itemEntry(s.copyWithCount(1)));
            }
        }
        List<Entry> all = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            all.add(itemEntry(new ItemStack(item)));
        }
        Tab icons = new Tab("icons", Component.translatable("questsicons.picker.all_icons"), allIcons);
        Tab invTab = new Tab("inventory", Component.translatable("questsicons.picker.inventory"), inv);
        Tab allTab = new Tab("items", Component.translatable("questsicons.picker.all_items"), all);
        if (iconsFirst) {
            p.tabs.add(icons);
            p.tabs.addAll(cats);
            p.tabs.add(invTab);
            p.tabs.add(allTab);
        } else {
            p.tabs.add(allTab);
            p.tabs.add(invTab);
            p.tabs.add(icons);
            p.tabs.addAll(cats);
        }
        return p;
    }

    private static Entry itemEntry(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        return new Entry(s, null, id, s.getHoverName(), (s.getHoverName().getString() + " " + id).toLowerCase(Locale.ROOT));
    }

    /** The quest-map pictures of the library, by category. Returns the texture id. */
    public static PickerScreen images(Screen parent, Consumer<String> onPick) {
        PickerScreen p = new PickerScreen(Component.translatable("questsicons.picker.images"), parent, Mode.IMAGES, null, onPick);
        List<Entry> all = new ArrayList<>();
        List<Tab> cats = new ArrayList<>();
        for (String cat : IconLibrary.MAP_IMAGE_CATEGORIES) {
            List<Entry> list = new ArrayList<>();
            for (IconLibrary.MapImage m : IconLibrary.MAP_IMAGES) {
                if (!m.category().equals(cat)) continue;
                Component name = Component.translatable("questsicons.map_image." + m.id());
                Entry e = new Entry(ItemStack.EMPTY, m, m.texture(), name, (name.getString() + " " + m.id()).toLowerCase(Locale.ROOT));
                list.add(e);
                all.add(e);
            }
            cats.add(new Tab(cat, Component.translatable("questsicons.map_image.category." + cat), list));
        }
        p.tabs.add(new Tab("all", Component.translatable("questsicons.picker.all_images"), all));
        p.tabs.addAll(cats);
        return p;
    }

    /** A plain list of ids (mobs, biomes, advancements, ...) or of named values (quests: value = id, label = title). */
    public static PickerScreen list(Screen parent, Component title, List<String> values, @Nullable List<Component> labels,
                                    @Nullable List<ItemStack> icons, Consumer<String> onPick) {
        PickerScreen p = new PickerScreen(title, parent, Mode.LIST, null, onPick);
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Component label = labels != null ? labels.get(i) : Component.literal(values.get(i));
            ItemStack icon = icons != null ? icons.get(i) : ItemStack.EMPTY;
            list.add(new Entry(icon, null, values.get(i), label, (label.getString() + " " + values.get(i)).toLowerCase(Locale.ROOT)));
        }
        p.tabs.add(new Tab("all", title, list));
        return p;
    }

    @Override
    protected void init() {
        super.init();
        String old = search != null ? search.getValue() : query;
        int left = tabs.size() > 1 ? TAB_W + 12 : 10;
        search = new EditBox(font, left, 22, Math.min(220, width - left - 10), 16, Component.translatable("questsicons.picker.search"));
        search.setHint(Component.translatable("questsicons.picker.search"));
        search.setValue(old);
        search.setResponder(s -> {
            query = s.toLowerCase(Locale.ROOT).trim();
            scroll = 0;
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    private List<Entry> visible() {
        List<Entry> out = new ArrayList<>();
        // Searching in a single icon category looks through the whole library instead.
        List<Entry> src = tabs.get(tab).entries();
        for (Entry e : src) {
            if (query.isEmpty()) {
                out.add(e);
                continue;
            }
            boolean all = true;
            for (String w : query.split(" ")) {
                if (!w.isEmpty() && !e.search().contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) out.add(e);
        }
        return out;
    }

    private void pick(Entry e) {
        // The value first (the form behind rebuilds its fields from it), then back - unless the callback opened a screen.
        if (onItem != null) onItem.accept(e.stack().copy());
        if (onValue != null) onValue.accept(e.value());
        if (minecraft != null && minecraft.screen == this) back();
    }

    @Override
    protected void draw(GuiGraphics g, int mouseX, int mouseY, float a) {
        g.drawCenteredString(font, title, width / 2, 8, QuestBookScreen.TEXT);
        int top = 44;
        int bottom = height - 28;
        int left = 10;
        // Tabs.
        if (tabs.size() > 1) {
            left = TAB_W + 12;
            int ty = top - tabScroll;
            g.enableScissor(0, top, TAB_W + 6, bottom);
            clipTop = top;
            clipBottom = bottom;
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                boolean sel = i == tab;
                boolean hov = hovered(6, ty, TAB_W, 12, mouseX, mouseY);
                if (sel) g.fill(6, ty, 6 + TAB_W, ty + 12, 0x807E0CC9);
                else if (hov) g.fill(6, ty, 6 + TAB_W, ty + 12, 0x30FFFFFF);
                g.drawString(font, QuestBookScreen.clip(font, t.name(), TAB_W - 6), 9, ty + 2, sel ? 0xFFFFFFFF : QuestBookScreen.AVAILABLE, false);
                final int idx = i;
                zone(6, ty, TAB_W, 12, () -> {
                    tab = idx;
                    scroll = 0;
                }, null, "tab:" + t.id());
                ty += 12;
            }
            clipTop = Integer.MIN_VALUE;
            clipBottom = Integer.MAX_VALUE;
            g.disableScissor();
        }
        List<Entry> list = visible();
        int areaW = width - left - 10;
        g.fill(left - 2, top - 2, width - 8, bottom + 2, 0x60000000);
        g.enableScissor(left - 2, top - 2, width - 8, bottom + 2);
        clipTop = top;
        clipBottom = bottom;
        int contentH;
        if (mode == Mode.LIST) {
            int rowH = 14;
            contentH = list.size() * rowH;
            scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (bottom - top)));
            int y = top - scroll;
            for (Entry e : list) {
                if (y + rowH >= top && y < bottom) {
                    boolean hov = hovered(left, y, areaW, rowH, mouseX, mouseY);
                    if (hov) g.fill(left, y, left + areaW, y + rowH, 0x407E0CC9);
                    int tx = left + 4;
                    if (!e.stack().isEmpty()) {
                        g.pose().pushMatrix();
                        g.pose().translate(left + 2, y + 1);
                        g.pose().scale(0.75f, 0.75f);
                        g.renderFakeItem(e.stack(), 0, 0);
                        g.pose().popMatrix();
                        tx += 14;
                    }
                    g.drawString(font, QuestBookScreen.clip(font, e.label(), areaW - (tx - left) - 4), tx, y + 3, hov ? 0xFFFFFFFF : QuestBookScreen.TEXT, false);
                    if (!e.label().getString().equals(e.value())) {
                        int w = font.width(e.value());
                        if (font.width(e.label()) + w + 30 < areaW) g.drawString(font, e.value(), left + areaW - w - 4, y + 3, QuestBookScreen.MUTED, false);
                    }
                    zone(left, y, areaW, rowH, () -> pick(e), null, e.value());
                }
                y += rowH;
            }
        } else {
            int cell = mode == Mode.IMAGES ? 44 : CELL;
            int cols = Math.max(1, areaW / cell);
            int rows = (list.size() + cols - 1) / cols;
            contentH = rows * cell;
            scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - (bottom - top)));
            for (int i = 0; i < list.size(); i++) {
                int x = left + (i % cols) * cell;
                int y = top + (i / cols) * cell - scroll;
                if (y + cell < top || y > bottom) continue;
                Entry e = list.get(i);
                boolean hov = hovered(x, y, cell - 2, cell - 2, mouseX, mouseY);
                QuestBookScreen.frame(g, x, y, cell - 2, cell - 2, hov ? 0xFF3A2358 : 0xFF1C1230, hov ? QuestBookScreen.GOLD : 0xFF3A2858);
                if (e.image() != null) {
                    IconLibrary.MapImage m = e.image();
                    int max = cell - 8;
                    float s = Math.min((float) max / m.width(), (float) max / m.height());
                    int w = Math.max(1, Math.round(m.width() * s));
                    int h = Math.max(1, Math.round(m.height() * s));
                    g.blit(RenderPipelines.GUI_TEXTURED, Identifier.parse(m.texture()), x + (cell - 2 - w) / 2, y + (cell - 2 - h) / 2,
                            0, 0, w, h, w, h, w, h);
                } else {
                    g.renderFakeItem(e.stack(), x + (cell - 18) / 2, y + (cell - 18) / 2);
                }
                zone(x, y, cell - 2, cell - 2, () -> pick(e), e.label(), e.value());
            }
        }
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        g.disableScissor();
        if (list.isEmpty()) g.drawCenteredString(font, Component.translatable("questsicons.picker.nothing"), left + areaW / 2, top + 20, QuestBookScreen.MUTED);
        // Footer.
        int fy = height - 22;
        int x = width / 2 - 50;
        if (mode == Mode.ITEMS && minecraft != null && minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            ItemStack hand = minecraft.player.getMainHandItem().copyWithCount(1);
            Component hl = Component.translatable("questsicons.picker.hand");
            int w = font.width(hl) + 12;
            button(g, x - w - 6, fy, w, 16, hl, mouseX, mouseY, () -> pick(new Entry(hand, null,
                    BuiltInRegistries.ITEM.getKey(hand.getItem()).toString(), hand.getHoverName(), "")));
        }
        button(g, x, fy, 100, 16, Component.translatable("gui.cancel"), mouseX, mouseY, this::back);
        g.drawString(font, Component.translatable("questsicons.picker.count", list.size()), width - 10 - font.width(Component.translatable("questsicons.picker.count", list.size())), fy + 4, QuestBookScreen.MUTED, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tabs.size() > 1 && mx < TAB_W + 8) {
            tabScroll = Mth.clamp(tabScroll - (int) (sy * 12), 0, Math.max(0, tabs.size() * 12 - (height - 72)));
        } else {
            scroll = Math.max(0, scroll - (int) (sy * (mode == Mode.IMAGES ? 44 : CELL)));
        }
        return true;
    }

    /** For tests: switch to the tab with this id. */
    public void selectTab(String id) {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id().equals(id)) tab = i;
    }
}
