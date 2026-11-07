package net.kasax.questsicons.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.EditorData;
import net.kasax.questsicons.icons.IconLibrary;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ChapterGroup;
import net.kasax.questsicons.quest.ChapterImage;
import net.kasax.questsicons.quest.ItemSpec;
import net.kasax.questsicons.quest.ProgressView;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.QuestShape;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.quest.task.Task;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quest book: chapters on the left, the chapter's quest map on the right (drag to pan, wheel to
 * zoom), a details panel for the clicked quest. Operators can switch to edit mode: drag quests,
 * right-click for a context menu (new quest, dependencies, icon/task/reward from the held item,
 * edit as SNBT, delete).
 */
public class QuestBookScreen extends Screen {
    // Dreamcraft-flavoured theme.
    static final int BG_TOP = 0xF0120A1E;
    static final int BG_BOTTOM = 0xF00A0612;
    static final int PANEL = 0xF0160E24;
    static final int PANEL_LIGHT = 0xF0221636;
    static final int BORDER = 0xFF7E0CC9;
    static final int ACCENT = 0xFFC084FC;
    static final int TEXT = 0xFFF3E8FF;
    static final int MUTED = 0xFF9C8DB0;
    static final int GOLD = 0xFFFFC94D;
    static final int GREEN = 0xFF5BE38A;
    static final int RED = 0xFFFF6B6B;
    static final int LOCKED = 0xFF5A5468;
    static final int AVAILABLE = 0xFFE7D4FF;

    /** Width of the chapter list; grows with the screen so chapter titles fit. */
    private static int SIDEBAR = 150;
    private static final int TOP = 24;
    private static final int GRID = 24;
    private static long lastChapter;
    private static final Map<Long, float[]> VIEWS = new HashMap<>();

    private @Nullable Chapter chapter;
    private float panX;
    private float panY;
    private float zoom = 1f;
    private @Nullable Quest selected;
    private int panelScroll;
    private int panelContentHeight;
    private int sidebarScroll;
    private boolean editMode;
    private @Nullable Quest dragging;
    private boolean dragMoved;
    private float dragQx;
    private float dragQy;
    private boolean panning;
    private @Nullable Quest linkSource;
    private @Nullable Reward choosing;
    private @Nullable ContextMenu menu;
    private final List<Zone> zones = new ArrayList<>();

    /**
     * Mouse buttons as Minecraft 26.3 reports them (SDL numbering: left = 1, middle = 2, right = 3). Older versions
     * used GLFW numbering (left = 0, right = 1, middle = 2); comparing against those numbers made every left click
     * count as a right click.
     */
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final int MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    private final List<Zone> lastZones = new ArrayList<>();
    /** Visible band of the scrolling quest panel: buttons scrolled out of it must not react to clicks. */
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;
    /** Where the zones of the reward picker or context menu start: while one is open, clicks must not reach what is behind it. */
    private int modalFrom;
    private int lastModalFrom;
    private long openedAt;

    // Edit mode: multi-select, shift-drag links, ctrl-drag box selection, dragging pictures and chapters.
    private final LinkedHashSet<Long> selection = new LinkedHashSet<>();
    private @Nullable Quest linkDrag;
    private double linkX;
    private double linkY;
    private boolean boxSelecting;
    private double boxX0, boxY0, boxX1, boxY1;
    private int dragImage = -1;
    private double dragImgX, dragImgY, dragImgOffX, dragImgOffY;
    private boolean dragImgMoved;
    private double dragStartX, dragStartY;
    private @Nullable Chapter dragChapter;
    private double dragChapterStartY, dragChapterY;
    private boolean dragChapterMoved;
    private double lastMouseX, lastMouseY;
    /** A chapter created from here: selected as soon as the server sends it. */
    private long pendingChapter;

    /** A row of the chapter list: a chapter or a group heading. */
    private record Row(@Nullable Chapter chapter, @Nullable ChapterGroup group, int y, int h) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Row> lastRows = new ArrayList<>();

    record Zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip, @Nullable String label) {
        Zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip) {
            this(x, y, w, h, action, tooltip, null);
        }

        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    record MenuEntry(Component label, Runnable action) {
    }

    static final class ContextMenu {
        final int x;
        final int y;
        final List<MenuEntry> entries;

        ContextMenu(int x, int y, List<MenuEntry> entries) {
            this.x = x;
            this.y = y;
            this.entries = entries;
        }
    }

    public QuestBookScreen() {
        super(Component.translatable("questsicons.gui.title"));
    }

    private QuestBook book() {
        return ClientQuests.book;
    }

    private HolderLookup.Provider reg() {
        QuestBook b = book();
        if (b != null && b.registries == null && minecraft != null && minecraft.level != null) b.registries = minecraft.level.registryAccess();
        return b == null ? null : b.registries;
    }

    private ProgressView progress() {
        return ClientQuests.progress;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        TEXTURES.clear();   // resource packs may have changed
        SIDEBAR = Mth.clamp(width / 5, 150, 200);
        if (book() == null) return;
        // Back from a form or picker: the book may have been replaced by a sync in the meantime.
        if (chapter != null && book().chapter(chapter.id) != chapter) bookUpdated();
        if (chapter == null) {
            Chapter c = book().chapter(lastChapter);
            if (c == null || !isChapterVisible(c)) c = firstVisibleChapter();
            selectChapter(c);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void bookUpdated() {
        if (book() == null) return;
        Chapter c = chapter == null ? null : book().chapter(chapter.id);
        chapter = c != null ? c : firstVisibleChapter();
        if (selected != null) selected = book().quest(selected.id);
        if (linkSource != null) linkSource = book().quest(linkSource.id);
        choosing = null;
        selection.removeIf(id -> book().quest(id) == null);
        if (pendingChapter != 0 && book().chapter(pendingChapter) != null) {
            selectChapter(book().chapter(pendingChapter));
            pendingChapter = 0;
        }
    }

    public void progressUpdated() {
    }

    /** Opens the details of a quest (also used by tests and links). */
    public void openQuest(long id) {
        Quest q = book() == null ? null : book().quest(id);
        if (q != null) jumpTo(q);
    }

    public void setEditMode(boolean on) {
        editMode = on && ClientQuests.canEdit;
        if (editMode) EditorData.request();
        selection.clear();
    }

    private @Nullable Chapter firstVisibleChapter() {
        for (Chapter c : book().chapters) if (isChapterVisible(c)) return c;
        return book().chapters.isEmpty() ? null : book().chapters.get(0);
    }

    private boolean isChapterVisible(Chapter c) {
        if (editMode) return true;
        if (c.alwaysInvisible) return false;
        if (!c.hideUntilStartable) return !c.quests.isEmpty();
        for (Quest q : c.quests) if (progress().canStart(q) || progress().isQuestCompleted(q.id)) return true;
        return false;
    }

    private void selectChapter(@Nullable Chapter c) {
        if (chapter != null) VIEWS.put(chapter.id, new float[]{panX, panY, zoom});
        chapter = c;
        selected = null;
        panelScroll = 0;
        if (c == null) return;
        lastChapter = c.id;
        float[] v = VIEWS.get(c.id);
        if (v != null) {
            panX = v[0];
            panY = v[1];
            zoom = v[2];
        } else {
            zoom = 1f;
            double minX = 0, maxX = 0, minY = 0, maxY = 0;
            boolean first = true;
            for (Quest q : c.quests) {
                if (first) {
                    minX = maxX = q.x;
                    minY = maxY = q.y;
                    first = false;
                }
                minX = Math.min(minX, q.x);
                maxX = Math.max(maxX, q.x);
                minY = Math.min(minY, q.y);
                maxY = Math.max(maxY, q.y);
            }
            int mapW = width - SIDEBAR;
            int mapH = height - TOP;
            double spanX = (maxX - minX + 2) * GRID;
            double spanY = (maxY - minY + 2) * GRID;
            zoom = (float) Mth.clamp(Math.min(mapW / spanX, mapH / spanY), 0.5, 1.4);
            panX = (float) (-(minX + maxX) / 2 * GRID * zoom);
            panY = (float) (-(minY + maxY) / 2 * GRID * zoom);
        }
    }

    // ------------------------------------------------------------------------------- coordinates

    private float originX() {
        return SIDEBAR + (width - SIDEBAR) / 2f + panX;
    }

    private float originY() {
        return TOP + (height - TOP) / 2f + panY;
    }

    private float sx(double qx) {
        return originX() + (float) (qx * GRID * zoom);
    }

    private float sy(double qy) {
        return originY() + (float) (qy * GRID * zoom);
    }

    private double qx(double screenX) {
        return (screenX - originX()) / (GRID * zoom);
    }

    private double qy(double screenY) {
        return (screenY - originY()) / (GRID * zoom);
    }

    private int nodeSize(Quest q) {
        return Math.max(8, Math.round(22 * (float) q.size * zoom));
    }

    /** While a selected quest is dragged, the other selected quests move along. */
    private double groupDx(Quest q) {
        return dragging != null && dragMoved && dragging != q && selection.contains(q.id) && selection.contains(dragging.id) ? dragQx - dragging.x : 0;
    }

    private double groupDy(Quest q) {
        return dragging != null && dragMoved && dragging != q && selection.contains(q.id) && selection.contains(dragging.id) ? dragQy - dragging.y : 0;
    }

    private boolean inMap(double mx, double my) {
        return mx >= SIDEBAR && my >= TOP;
    }

    private @Nullable Quest questAt(double mx, double my) {
        if (chapter == null || !inMap(mx, my)) return null;
        for (int i = chapter.quests.size() - 1; i >= 0; i--) {
            Quest q = chapter.quests.get(i);
            if (!editMode && !progress().isVisible(q)) continue;
            float x = q == dragging ? sx(dragQx) : sx(q.x);
            float y = q == dragging ? sy(dragQy) : sy(q.y);
            float r = nodeSize(q) / 2f;
            if (Math.abs(mx - x) <= r && Math.abs(my - y) <= r) return q;
        }
        return null;
    }

    // ------------------------------------------------------------------------------- rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        lastZones.clear();
        lastZones.addAll(zones);
        lastRows.clear();
        lastRows.addAll(rows);
        rows.clear();
        lastModalFrom = modalFrom;
        zones.clear();
        modalFrom = 0;
        if (book() == null) {
            g.centeredText(font, Component.translatable("questsicons.gui.no_book"), width / 2, height / 2, TEXT);
            return;
        }
        boolean overlay = selected != null || menu != null || choosing != null;
        drawMap(g, overlay ? -1 : mouseX, overlay ? -1 : mouseY);
        drawSidebar(g, overlay ? -1 : mouseX, overlay ? -1 : mouseY);
        drawTopBar(g, overlay ? -1 : mouseX, overlay ? -1 : mouseY);
        if (selected != null) {
            g.nextStratum();
            g.fill(SIDEBAR, TOP, width, height, 0x90000000);
            drawPanel(g, choosing == null && menu == null ? mouseX : -1, choosing == null && menu == null ? mouseY : -1);
        }
        if (choosing != null) {
            g.nextStratum();
            drawChoice(g, menu == null ? mouseX : -1, mouseY);
        }
        if (menu != null) {
            g.nextStratum();
            drawMenu(g, mouseX, mouseY);
        }
        for (Zone z : zones) {
            if (z.tooltip() != null && z.contains(mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, z.tooltip(), mouseX, mouseY);
                break;
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.enableScissor(SIDEBAR, TOP, width, height);
        // Background picture or a dotted grid.
        QuestBook b = book();
        if (!b.background.isEmpty()) {
            try {
                g.blit(RenderPipelines.GUI_TEXTURED, Identifier.parse(b.background), SIDEBAR, TOP, 0, 0, width - SIDEBAR, height - TOP,
                        width - SIDEBAR, height - TOP, 0x60FFFFFF);
            } catch (Exception ignored) {
            }
        }
        float step = GRID * zoom;
        if (step >= 8) {
            float startX = originX() - (float) Math.floor((originX() - SIDEBAR) / step) * step;
            float startY = originY() - (float) Math.floor((originY() - TOP) / step) * step;
            for (float x = startX; x < width; x += step) {
                for (float y = startY; y < height; y += step) {
                    g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, 0x30FFFFFF);
                }
            }
        }
        if (chapter == null) {
            g.disableScissor();
            return;
        }
        ProgressView p = progress();
        for (ChapterImage img : chapter.images) if (!img.front) drawImage(g, img, mouseX, mouseY);

        // Dependency lines.
        for (Quest q : chapter.quests) {
            if (!editMode && (!p.isVisible(q) || q.hideDependencyLines)) continue;
            for (long depId : q.dependencies) {
                Quest d = book().quest(depId);
                if (d == null || d.chapter != chapter) continue;
                if (!editMode && (!p.isVisible(d) || d.hideDependentLines)) continue;
                int color = p.isQuestCompleted(q.id) ? GREEN : p.isQuestCompleted(d.id) ? ACCENT : 0xFF4A4458;
                if (q == selected || d == selected) color = GOLD;
                float x1 = d == dragging ? sx(dragQx) : sx(d.x + groupDx(d));
                float y1 = d == dragging ? sy(dragQy) : sy(d.y + groupDy(d));
                float x2 = q == dragging ? sx(dragQx) : sx(q.x + groupDx(q));
                float y2 = q == dragging ? sy(dragQy) : sy(q.y + groupDy(q));
                line(g, x1, y1, x2, y2, Math.max(1f, 2.2f * zoom), color);
            }
        }

        // Quest nodes.
        Quest hovered = questAt(mouseX, mouseY);
        long now = System.currentTimeMillis();
        for (Quest q : chapter.quests) {
            boolean visible = p.isVisible(q);
            if (!visible && !editMode) continue;
            boolean done = p.isQuestCompleted(q.id);
            boolean canStart = p.canStart(q);
            int s = nodeSize(q);
            float cx = q == dragging ? sx(dragQx) : sx(q.x + groupDx(q));
            float cy = q == dragging ? sy(dragQy) : sy(q.y + groupDy(q));
            int x = Math.round(cx - s / 2f);
            int y = Math.round(cy - s / 2f);
            int tint = done ? GREEN : canStart ? AVAILABLE : LOCKED;
            if (!visible) tint = 0x80FFFFFF & tint;
            QuestShape shape = q.shape();
            boolean picked = selection.contains(q.id);
            if (q == hovered || q == selected || q == linkSource || q == linkDrag || picked) {
                int gs = s + Math.max(4, s / 4);
                g.blit(RenderPipelines.GUI_TEXTURED, shapeTex(shape == QuestShape.NONE ? QuestShape.CIRCLE : shape), Math.round(cx - gs / 2f),
                        Math.round(cy - gs / 2f), 0, 0, gs, gs, 64, 64, 64, 64,
                        q == linkSource || q == linkDrag ? 0xFFFF5555 : picked ? 0xFF55E0FF : 0xFFFFE27A);
            }
            if (shape != QuestShape.NONE) g.blit(RenderPipelines.GUI_TEXTURED, shapeTex(shape), x, y, 0, 0, s, s, 64, 64, 64, 64, tint);
            float scale = s * 0.62f / 16f;
            g.pose().pushMatrix();
            g.pose().translate(cx - 8 * scale, cy - 8 * scale);
            g.pose().scale(scale, scale);
            g.fakeItem(q.displayIcon(reg()), 0, 0);
            g.pose().popMatrix();
            if (done && p.hasUnclaimedRewards(q)) {
                float pulse = 0.75f + 0.25f * Mth.sin((now % 1000) / 1000f * Mth.TWO_PI);
                badge(g, x + s - 4, y - 2, "!", GOLD, pulse);
            } else if (done) {
                badge(g, x + s - 4, y - 2, "✔", GREEN, 1f);
            } else if (!canStart) {
                badge(g, x + s - 4, y - 2, "✖", 0xFF8A8494, 0.9f);
            } else if (p.questProgress(q) > 0) {
                int barW = s;
                g.fill(x, y + s + 1, x + barW, y + s + 3, 0xA0000000);
                g.fill(x, y + s + 1, x + Math.round(barW * p.questProgress(q)), y + s + 3, ACCENT);
            }
            if (q.optional && !done && zoom >= 0.8f) g.text(font, "?", x - 2, y - 2, MUTED, true);
        }
        for (ChapterImage img : chapter.images) if (img.front) drawImage(g, img, mouseX, mouseY);
        if (linkDrag != null) {
            line(g, sx(linkDrag.x), sy(linkDrag.y), (float) linkX, (float) linkY, 2f, 0xFFFF7777);
        }
        if (boxSelecting) {
            int bx0 = (int) Math.min(boxX0, boxX1), bx1 = (int) Math.max(boxX0, boxX1);
            int by0 = (int) Math.min(boxY0, boxY1), by1 = (int) Math.max(boxY0, boxY1);
            g.fill(bx0, by0, bx1, by1, 0x3055E0FF);
            g.outline(bx0, by0, Math.max(1, bx1 - bx0), Math.max(1, by1 - by0), 0xFF55E0FF);
        }

        // Chapter heading.
        int hx = SIDEBAR + 8;
        int hy = TOP + 6;
        g.text(font, chapter.displayTitle(), hx, hy, TEXT, true);
        int ly = hy + 11;
        for (String line : chapter.subtitle) {
            g.text(font, QuestObject.text(line), hx, ly, MUTED, true);
            ly += 10;
        }
        if (editMode) {
            g.text(font, Component.translatable(linkSource != null ? "questsicons.edit.linking" : "questsicons.edit.mode_hint"),
                    hx, height - 12, linkSource != null ? 0xFFFF7777 : GOLD, true);
        }
        g.disableScissor();

        if (hovered != null && dragging == null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.displayTitle());
            if (!hovered.subtitle.isEmpty()) tip.add(QuestObject.text(hovered.subtitle).withStyle(net.minecraft.ChatFormatting.GRAY));
            if (p.isQuestCompleted(hovered.id)) {
                tip.add(Component.translatable(p.hasUnclaimedRewards(hovered) ? "questsicons.gui.rewards_waiting" : "questsicons.gui.completed")
                        .withStyle(p.hasUnclaimedRewards(hovered) ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.GREEN));
            } else if (!p.canStart(hovered)) {
                tip.add(Component.translatable("questsicons.gui.locked").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            } else {
                tip.add(Component.translatable("questsicons.gui.progress", Math.round(p.questProgress(hovered) * 100))
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    private void drawImage(GuiGraphicsExtractor g, ChapterImage img, int mouseX, int mouseY) {
        if (!editMode && img.dependency != 0 && !progress().isQuestCompleted(img.dependency)) return;
        int w = Math.round((float) img.width * GRID * zoom);
        int h = Math.round((float) img.height * GRID * zoom);
        boolean moving = dragImage >= 0 && chapter != null && dragImage < chapter.images.size() && chapter.images.get(dragImage) == img;
        float cx = moving ? sx(dragImgX) : sx(img.x);
        float cy = moving ? sy(dragImgY) : sy(img.y);
        if (editMode && (moving || Math.abs(mouseX - cx) < w / 2f && Math.abs(mouseY - cy) < h / 2f && questAt(mouseX, mouseY) == null)) {
            g.outline(Math.round(cx - w / 2f) - 1, Math.round(cy - h / 2f) - 1, w + 2, h + 2, 0x9055E0FF);
        }
        try {
            Identifier tex = Identifier.parse(img.image);
            if (!textureExists(tex)) return;   // e.g. a picture of a mod the pack no longer has (imported books)
            int color = (img.alpha << 24) | (img.color & 0xFFFFFF);
            if (img.rotation != 0) {
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().rotate((float) Math.toRadians(img.rotation));
                g.blit(RenderPipelines.GUI_TEXTURED, tex, -w / 2, -h / 2, 0, 0, w, h, w, h, w, h, color);
                g.pose().popMatrix();
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(cx - w / 2f), Math.round(cy - h / 2f), 0, 0, w, h, w, h, w, h, color);
            }
        } catch (Exception ignored) {
        }
        if (!img.hover.isEmpty() && Math.abs(mouseX - cx) < w / 2f && Math.abs(mouseY - cy) < h / 2f) {
            zones.add(new Zone(Math.round(cx - w / 2f), Math.round(cy - h / 2f), w, h, () -> {
            }, QuestObject.text(img.hover)));
        }
    }

    private static final java.util.Map<Identifier, Boolean> TEXTURES = new java.util.HashMap<>();

    /** Missing pictures are left out instead of drawn as the purple and black "missing texture". */
    private static boolean textureExists(Identifier tex) {
        return TEXTURES.computeIfAbsent(tex, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    private static Identifier shapeTex(QuestShape shape) {
        return Identifier.fromNamespaceAndPath("questsicons", "textures/gui/shapes/" + shape.id() + ".png");
    }

    private void badge(GuiGraphicsExtractor g, int x, int y, String s, int color, float alpha) {
        int a = Math.round(alpha * 255) << 24;
        g.fill(x - 1, y - 1, x + 8, y + 9, (a & 0xC0000000) | 0x000000);
        g.text(font, s, x, y, (color & 0xFFFFFF) | a, false);
    }

    static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate((float) Math.atan2(dy, dx));
        int t = Math.max(1, Math.round(thickness));
        g.fill(0, -t / 2, Math.round(len), t - t / 2, color);
        g.pose().popMatrix();
    }

    /** The text cut to a width, keeping its colours and formatting. */
    static FormattedCharSequence clip(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        return Language.getInstance().getVisualOrder(font.substrByWidth(text, Math.max(0, width)));
    }

    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.outline(x, y, w, h, border);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, SIDEBAR, height, PANEL);
        g.fill(SIDEBAR - 1, 0, SIDEBAR, height, BORDER);
        int y = TOP + 4 - sidebarScroll;
        g.enableScissor(0, TOP, SIDEBAR - 1, height);
        String currentGroup = null;
        List<Chapter> list = new ArrayList<>(book().chapters);
        // Ungrouped chapters first, then by group order.
        List<Chapter> ordered = new ArrayList<>();
        for (Chapter c : list) if (c.group.isEmpty()) ordered.add(c);
        for (ChapterGroup grp : book().groups) for (Chapter c : list) if (c.group.equals(grp.idString()) || c.group.equals(grp.title)) ordered.add(c);
        for (Chapter c : list) if (!ordered.contains(c)) ordered.add(c);
        for (Chapter c : ordered) {
            if (!isChapterVisible(c)) continue;
            if (!c.group.isEmpty() && !c.group.equals(currentGroup)) {
                currentGroup = c.group;
                ChapterGroup grp = null;
                for (ChapterGroup gg : book().groups) if (gg.idString().equals(c.group) || gg.title.equals(c.group)) grp = gg;
                Component name = grp != null ? grp.displayTitle() : Component.literal(c.group);
                y += 3;
                if (grp != null && !grp.icon.isEmpty()) {
                    g.pose().pushMatrix();
                    g.pose().translate(5, y);
                    g.pose().scale(0.625f, 0.625f);
                    g.fakeItem(grp.icon.stack(reg()), 0, 0);
                    g.pose().popMatrix();
                    g.text(font, name, 17, y + 2, MUTED, false);
                } else {
                    g.text(font, name, 6, y + 2, MUTED, false);
                }
                g.fill(6, y + 12, SIDEBAR - 8, y + 13, 0x40FFFFFF);
                if (grp != null) rows.add(new Row(null, grp, y, 14));
                y += 16;
            }
            boolean sel = c == chapter;
            boolean hov = mouseX >= 0 && mouseX < SIDEBAR && mouseY >= y && mouseY < y + 20 && mouseY >= TOP;
            rows.add(new Row(c, null, y, 22));
            if (dragChapter != null && dragChapterMoved && dragChapterY >= y && dragChapterY < y + 22 && c != dragChapter) {
                int ly = dragChapterY > y + 11 ? y + 21 : y - 1;
                g.fill(2, ly, SIDEBAR - 3, ly + 2, GOLD);
            }
            if (sel) {
                g.fill(2, y, SIDEBAR - 3, y + 20, 0x607E0CC9);
                g.fill(2, y, 4, y + 20, ACCENT);
            } else if (hov) {
                g.fill(2, y, SIDEBAR - 3, y + 20, 0x30FFFFFF);
            }
            g.fakeItem(c.displayIcon(reg()), 7, y + 2);
            int pct = progress().chapterPercent(c);
            String pctText = pct >= 100 ? "✔" : pct + "%";
            int pctW = font.width(pctText);
            g.text(font, clip(font, c.displayTitle(), SIDEBAR - 34 - pctW - 6), 27, y + 6, sel ? TEXT : 0xFFD6C8EA, false);
            g.text(font, pctText, SIDEBAR - 8 - pctW, y + 6, pct >= 100 ? GREEN : MUTED, false);
            boolean unclaimed = false;
            for (Quest q : c.quests) if (progress().hasUnclaimedRewards(q)) unclaimed = true;
            if (unclaimed) g.text(font, "!", 3, y + 6, GOLD, true);
            final Chapter target = c;
            final int ry = y;
            if (ry + 20 > TOP) {
                zones.add(new Zone(0, Math.max(TOP, ry), SIDEBAR - 1, 20, () -> {
                    selectChapter(target);
                    click();
                }, null, "chapter:" + target.idString()));   // the label lets tests click a chapter
            }
            y += 22;
        }
        if (editMode) {
            int by = y + 4;
            int half = (SIDEBAR - 14) / 2;
            button(g, 6, by, half, 16, Component.translatable("questsicons.edit.new_chapter"), mouseX, mouseY, () -> newChapter(""));
            button(g, 8 + half, by, half, 16, Component.translatable("questsicons.edit.new_group"), mouseX, mouseY, this::newGroup);
            y += 24;
        }
        g.disableScissor();
        int contentH = y + sidebarScroll - TOP;
        maxSidebarScroll = Math.max(0, contentH - (height - TOP) + 8);
    }

    private int maxSidebarScroll;

    private void drawTopBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, width, TOP, PANEL_LIGHT);
        g.fill(0, TOP - 1, width, TOP, BORDER);
        g.fakeItem(book().icon.isEmpty() ? new ItemStack(net.kasax.questsicons.item.ModItems.QUEST_BOOK) : book().icon.stack(reg()), 4, 4);
        g.text(font, QuestObject.text(book().title), 24, 8, TEXT, true);
        int x = width - 4;
        // Close
        x -= 20;
        button(g, x, 4, 18, 16, Component.literal("✕"), mouseX, mouseY, this::onClose);
        if (ClientQuests.canEdit) {
            Component label = Component.translatable(editMode ? "questsicons.edit.on" : "questsicons.edit.off");
            int w = font.width(label) + 12;
            x -= w + 4;
            button(g, x, 4, w, 16, label, mouseX, mouseY, () -> {
                setEditMode(!editMode);
                linkSource = null;
                if (chapter == null) chapter = firstVisibleChapter();
            });
            if (editMode) {
                x = toolButton(g, x, Component.translatable("questsicons.edit.book_settings"), mouseX, mouseY, this::editBook);
                x = toolButton(g, x, Component.translatable("questsicons.edit.tables"), mouseX, mouseY, () -> minecraft.gui.setScreen(new TablesScreen(this)));
                x = toolButton(g, x, Component.translatable(EditorData.snap ? "questsicons.edit.snap_on" : "questsicons.edit.snap_off"), mouseX, mouseY,
                        () -> EditorData.snap = !EditorData.snap);
                if (EditorData.undoSize() > 0) {
                    x = toolButton(g, x, Component.translatable("questsicons.edit.undo"), mouseX, mouseY, this::undo);
                }
            }
        }
        int unclaimed = ClientQuests.progress.unclaimedTotal();
        Component claim = Component.translatable("questsicons.gui.claim_all", unclaimed);
        int cw = font.width(claim) + 12;
        x -= cw + 4;
        if (unclaimed > 0) {
            button(g, x, 4, cw, 16, claim, mouseX, mouseY, () -> {
                ClientPlayNetworking.send(new QuestNet.Action(QuestNet.Action.CLAIM_ALL, 0, 0));
                click();
            });
        }
        // Team and overall progress.
        int total = 0;
        int done = 0;
        for (Quest q : book().allQuests()) {
            if (q.optional) continue;
            total++;
            if (progress().isQuestCompleted(q.id)) done++;
        }
        String info = (ClientQuests.progress.teamName.isEmpty() ? "" : ClientQuests.progress.teamName + "  •  ") + done + "/" + total;
        int iw = font.width(info);
        int ix = x - iw - 10;
        if (ix > 24 + font.width(QuestObject.text(book().title)) + 10) g.text(font, info, ix, 8, MUTED, false);
    }

    private int toolButton(GuiGraphicsExtractor g, int right, Component label, int mouseX, int mouseY, Runnable action) {
        int w = font.width(label) + 10;
        int x = right - w - 4;
        button(g, x, 4, w, 16, label, mouseX, mouseY, action);
        return x;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, int mouseX, int mouseY, Runnable action) {
        boolean hov = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
        frame(g, x, y, w, h, hov ? 0xFF3A2358 : 0xFF261839, hov ? ACCENT : 0xFF5B3A86);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, hov ? 0xFFFFFFFF : TEXT);
        zone(x, y, w, h, action, null, label.getString());
    }

    /** Adds a click/tooltip area, cut to the visible part of the scrolling panel. */
    private void zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip) {
        zone(x, y, w, h, action, tooltip, null);
    }

    private void zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip, @Nullable String label) {
        int top = Math.max(y, clipTop);
        int bottom = Math.min(y + h, clipBottom);
        if (bottom > top) zones.add(new Zone(x, top, w, bottom - top, action, tooltip, label));
    }

    // ------------------------------------------------------------------------------- details panel

    private int panelX() {
        int pw = panelW();
        return SIDEBAR + (width - SIDEBAR - pw) / 2;
    }

    private int panelW() {
        return Math.min(width - SIDEBAR - 24, 330);
    }

    private void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Quest q = selected;
        ProgressView p = progress();
        int pw = panelW();
        int px = panelX();
        int py = TOP + 10;
        int ph = height - TOP - 20;
        frame(g, px, py, pw, ph, PANEL, BORDER);
        g.fill(px + 1, py + 1, px + pw - 1, py + 30, PANEL_LIGHT);
        // Header (fixed).
        g.pose().pushMatrix();
        g.pose().translate(px + 8, py + 6);
        g.pose().scale(1.25f, 1.25f);
        g.fakeItem(q.displayIcon(reg()), 0, 0);
        g.pose().popMatrix();
        g.text(font, q.displayTitle(), px + 34, py + 6, TEXT, true);
        if (!q.subtitle.isEmpty()) g.text(font, QuestObject.text(q.subtitle), px + 34, py + 17, MUTED, false);
        boolean done = p.isQuestCompleted(q.id);
        boolean canStart = p.canStart(q);
        Component state = done ? Component.translatable("questsicons.gui.completed") : canStart
                ? Component.translatable("questsicons.gui.progress", Math.round(p.questProgress(q) * 100))
                : Component.translatable("questsicons.gui.locked");
        int sw = font.width(state);
        g.text(font, state, px + pw - sw - 8, py + 6, done ? GREEN : canStart ? ACCENT : LOCKED, false);

        // Scrollable body.
        int bodyTop = py + 32;
        int bodyBottom = py + ph - 26;
        g.enableScissor(px + 1, bodyTop, px + pw - 1, bodyBottom);
        clipTop = bodyTop;
        clipBottom = bodyBottom;
        int y = bodyTop + 4 - panelScroll;
        int textW = pw - 20;
        for (String line : q.description) {
            if (line.startsWith("{image:")) {
                y = drawInlineImage(g, line, px + 10, y, textW);
                continue;
            }
            if (line.isEmpty()) {
                y += 6;
                continue;
            }
            for (FormattedCharSequence seq : font.split(QuestObject.text(line), textW)) {
                g.text(font, seq, px + 10, y, 0xFFE6DCF5, false);
                y += 10;
            }
        }
        if (!q.description.isEmpty()) y += 6;

        if (!canStart && !done && !q.dependencies.isEmpty()) {
            g.text(font, Component.translatable("questsicons.gui.requires"), px + 10, y, RED, false);
            y += 11;
            for (long dep : q.dependencies) {
                Quest d = book().quest(dep);
                if (d == null) continue;
                boolean dd = p.isQuestCompleted(dep);
                g.text(font, Component.literal((dd ? "✔ " : "✖ ")).append(d.displayTitle()), px + 16, y, dd ? GREEN : MUTED, false);
                final Quest target = d;
                zone(px + 16, y, textW, 10, () -> jumpTo(target), null);
                y += 10;
            }
            if (q.dependsOnAny) {
                g.text(font, Component.translatable("questsicons.gui.requires_any"), px + 16, y, MUTED, false);
                y += 10;
            }
            y += 6;
        }

        // Tasks.
        if (!q.tasks.isEmpty()) {
            g.text(font, Component.translatable("questsicons.gui.tasks"), px + 10, y, ACCENT, false);
            g.fill(px + 10, y + 10, px + pw - 10, y + 11, 0x407E0CC9);
            y += 14;
            for (Task t : q.tasks) {
                long prog = p.taskProgress(t.id);
                boolean tdone = done || prog >= t.maxProgress();
                g.fill(px + 8, y - 1, px + pw - 8, y + 19, 0x30FFFFFF);
                g.fakeItem(t.displayIcon(reg()), px + 11, y + 1);
                if (t instanceof Tasks.ItemTask it && it.icon.isEmpty() && it.item.cycles()) {
                    List<ItemStack> items = it.item.tagItems();
                    ItemStack cycle = items.get((int) ((System.currentTimeMillis() / 1000) % items.size()));
                    g.fakeItem(cycle, px + 11, y + 1);
                }
                String pt = tdone ? "✔" : t.progressText(prog);
                int ptw = font.width(pt);
                int right = px + pw - 12;
                boolean showBtn = !tdone && canStart && t.hasSubmitButton();
                int btnW = showBtn ? font.width(t.submitLabel()) + 10 : 0;
                if (showBtn) {
                    final Task task = t;
                    button(g, right - btnW, y + 1, btnW, 16, t.submitLabel(), mouseX, mouseY, () -> {
                        ClientPlayNetworking.send(new QuestNet.Action(QuestNet.Action.SUBMIT, task.id, 0));
                        click();
                    });
                    right -= btnW + 4;
                }
                g.text(font, pt, right - ptw, y + 5, tdone ? GREEN : MUTED, false);
                Component taskTitle = t.optional ? t.title(reg()).copy().append(Component.translatable("questsicons.gui.optional_task")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)) : t.title(reg());
                g.text(font, clip(font, taskTitle, right - ptw - (px + 32) - 6), px + 32, y + 5, tdone ? 0xFFB9F5C9 : TEXT, false);
                if (editMode) {
                    final int ti = q.tasks.indexOf(t);
                    final Component tt = t.title(reg());
                    zone(px + 8, y, 22, 18, () -> pickPartIcon(q, "tasks", ti, tt), Component.translatable("questsicons.edit.task_icon_hint"), "task_icon:" + ti);
                } else if (mouseY >= y && mouseY < y + 18 && mouseX >= px + 8 && mouseX < px + 30) {
                    zone(px + 8, y, 22, 18, () -> {
                    }, t.title(reg()));
                }
                y += 22;
            }
            y += 4;
        }

        // Rewards.
        if (!q.rewards.isEmpty()) {
            g.text(font, Component.translatable("questsicons.gui.rewards"), px + 10, y, GOLD, false);
            g.fill(px + 10, y + 10, px + pw - 10, y + 11, 0x40FFC94D);
            y += 14;
            for (Reward r : q.rewards) {
                boolean claimed = p.isRewardClaimed(r.id, r.isTeamReward());
                g.fill(px + 8, y - 1, px + pw - 8, y + 19, 0x30FFFFFF);
                g.fakeItem(r.displayIcon(reg()), px + 11, y + 1);
                ItemStack icon = r.displayIcon(reg());
                if (icon.getCount() > 1) g.itemDecorations(font, icon, px + 11, y + 1);
                int right = px + pw - 12;
                if (done && !claimed) {
                    Component lbl = Component.translatable(r.needsChoice() ? "questsicons.gui.choose" : "questsicons.gui.claim");
                    int bw = font.width(lbl) + 10;
                    final Reward reward = r;
                    button(g, right - bw, y + 1, bw, 16, lbl, mouseX, mouseY, () -> {
                        if (reward.needsChoice()) {
                            choosing = reward;
                        } else {
                            ClientPlayNetworking.send(new QuestNet.Action(QuestNet.Action.CLAIM, reward.id, -1));
                        }
                        click();
                    });
                    right -= bw + 4;
                } else {
                    String st = claimed ? "✔" : "🔒";
                    if (!claimed) st = "•";
                    int stw = font.width(st);
                    g.text(font, st, right - stw, y + 5, claimed ? GREEN : LOCKED, false);
                    right -= stw + 4;
                }
                Component title = r.title(reg());
                if (r.isTeamReward()) title = title.copy().append(Component.translatable("questsicons.gui.team_reward").withStyle(net.minecraft.ChatFormatting.AQUA));
                g.text(font, clip(font, title, right - (px + 32) - 4), px + 32, y + 5, claimed ? MUTED : TEXT, false);
                if (editMode) {
                    final int ri = q.rewards.indexOf(r);
                    final Component rt = title;
                    zone(px + 8, y, 22, 18, () -> pickPartIcon(q, "rewards", ri, rt), Component.translatable("questsicons.edit.reward_icon_hint"), "reward_icon:" + ri);
                } else if (mouseY >= y && mouseY < y + 18 && mouseX >= px + 8 && mouseX < px + 30) {
                    List<ItemStack> choices = r.choices(reg());
                    zone(px + 8, y, 22, 18, () -> {
                    }, choices.isEmpty() ? title : Component.translatable("questsicons.gui.choice_count", choices.size()));
                }
                y += 22;
            }
        }
        panelContentHeight = y + panelScroll - bodyTop + 4;
        g.disableScissor();
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        int maxScroll = Math.max(0, panelContentHeight - (bodyBottom - bodyTop));
        if (maxScroll > 0) {
            int trackH = bodyBottom - bodyTop;
            int barH = Math.max(12, trackH * trackH / panelContentHeight);
            int barY = bodyTop + (trackH - barH) * panelScroll / maxScroll;
            g.fill(px + pw - 4, barY, px + pw - 2, barY + barH, 0xA0C084FC);
        }

        // Footer.
        int fy = py + ph - 22;
        g.fill(px + 1, fy - 3, px + pw - 1, fy - 2, 0x407E0CC9);
        int fx = px + 8;
        Component pin = Component.translatable(ClientQuests.pinned.contains(q.id) ? "questsicons.gui.unpin" : "questsicons.gui.pin");
        int pinW = font.width(pin) + 12;
        final Quest quest = q;
        button(g, fx, fy, pinW, 16, pin, mouseX, mouseY, () -> ClientQuests.togglePin(quest.id));
        fx += pinW + 4;
        if (editMode) {
            Component e = Component.translatable("questsicons.edit.edit_quest_form");
            int ew = font.width(e) + 12;
            button(g, fx, fy, ew, 16, e, mouseX, mouseY, () -> editQuestForm(quest));
            fx += ew + 4;
        }
        Component close = Component.translatable("questsicons.gui.close");
        int cw = font.width(close) + 12;
        button(g, px + pw - cw - 8, fy, cw, 16, close, mouseX, mouseY, () -> selected = null);
    }

    private int drawInlineImage(GuiGraphicsExtractor g, String line, int x, int y, int maxW) {
        // {image:ns:path width:120 height:60 align:center}
        String body = line.substring(7, line.endsWith("}") ? line.length() - 1 : line.length()).trim();
        String[] parts = body.split(" ");
        String tex = parts[0];
        int w = 64;
        int h = 64;
        String align = "center";
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split(":", 2);
            if (kv.length != 2) continue;
            try {
                switch (kv[0]) {
                    case "width" -> w = Integer.parseInt(kv[1]);
                    case "height" -> h = Integer.parseInt(kv[1]);
                    case "align" -> align = kv[1];
                    default -> {
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (w > maxW) {
            h = h * maxW / w;
            w = maxW;
        }
        int ix = align.equals("left") ? x : align.equals("right") ? x + maxW - w : x + (maxW - w) / 2;
        try {
            Identifier id = Identifier.parse(tex);
            if (!textureExists(id)) return y;
            g.blit(RenderPipelines.GUI_TEXTURED, id, ix, y, 0, 0, w, h, w, h);
        } catch (Exception ignored) {
        }
        return y + h + 4;
    }

    private void drawChoice(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        modalFrom = zones.size();
        List<ItemStack> items = choosing.choices(reg());
        int cols = Math.min(6, Math.max(1, items.size()));
        int rows = (items.size() + cols - 1) / cols;
        Component title = Component.translatable("questsicons.gui.pick_one");
        int gridW = cols * 26 - 2;
        int w = Math.max(gridW, font.width(title)) + 16;   // wide enough for the title, too
        int h = rows * 26 + 40;
        int x = SIDEBAR + (width - SIDEBAR - w) / 2;
        int y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x80000000);
        frame(g, x, y, w, h, PANEL, GOLD);
        g.centeredText(font, title, x + w / 2, y + 6, GOLD);
        for (int i = 0; i < items.size(); i++) {
            int ix = x + (w - gridW) / 2 + (i % cols) * 26;
            int iy = y + 20 + (i / cols) * 26;
            boolean hov = mouseX >= ix && mouseY >= iy && mouseX < ix + 24 && mouseY < iy + 24;
            frame(g, ix, iy, 24, 24, hov ? 0xFF3A2358 : 0xFF261839, hov ? GOLD : 0xFF5B3A86);
            g.fakeItem(items.get(i), ix + 4, iy + 4);
            g.itemDecorations(font, items.get(i), ix + 4, iy + 4);
            final int idx = i;
            final Reward r = choosing;
            zones.add(new Zone(ix, iy, 24, 24, () -> {
                ClientPlayNetworking.send(new QuestNet.Action(QuestNet.Action.CLAIM, r.id, idx));
                choosing = null;
                click();
            }, items.get(i).getHoverName()));
        }
        Component cancel = Component.translatable("gui.cancel");
        int cw = font.width(cancel) + 12;
        button(g, x + (w - cw) / 2, y + h - 18, cw, 14, cancel, mouseX, mouseY, () -> choosing = null);
    }

    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        modalFrom = zones.size();
        ContextMenu m = menu;
        int w = 0;
        for (MenuEntry e : m.entries) w = Math.max(w, font.width(e.label()));
        w += 16;
        int h = m.entries.size() * 14 + 4;
        int x = Math.min(m.x, width - w - 2);
        int y = Math.min(m.y, height - h - 2);
        frame(g, x, y, w, h, 0xF8140C22, ACCENT);
        int ey = y + 2;
        for (MenuEntry e : m.entries) {
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= ey && mouseY < ey + 14;
            if (hov) g.fill(x + 1, ey, x + w - 1, ey + 14, 0x607E0CC9);
            g.text(font, e.label(), x + 8, ey + 3, TEXT, false);
            final Runnable action = e.action();
            zones.add(new Zone(x, ey, w, 14, () -> {
                menu = null;
                action.run();
            }, null, e.label().getString()));
            ey += 14;
        }
    }

    // ------------------------------------------------------------------------------- input

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private boolean runZone(double mx, double my) {
        return runZone(mx, my, 0);
    }

    /** Runs the topmost zone under the cursor, looking only at zones from index {@code from} on. */
    private boolean runZone(double mx, double my, int from) {
        for (int i = lastZones.size() - 1; i >= from; i--) {
            Zone z = lastZones.get(i);
            if (z.contains(mx, my)) {
                z.action().run();
                return true;
            }
        }
        return false;
    }

    /** For tests: the GUI position of a quest's centre on the current map, or null if it is not in this chapter. */
    public float[] questScreenPos(long id) {
        if (book() == null || chapter == null) return null;
        for (Quest q : chapter.quests) {
            if (q.id == id) return new float[]{sx(q.x), sy(q.y)};
        }
        return null;
    }

    /** For tests: the GUI position of the centre of the clickable button (or picker item) showing this text, or null. */
    public float[] zonePos(String text) {
        for (int i = lastZones.size() - 1; i >= 0; i--) {
            Zone z = lastZones.get(i);
            if (text.equals(z.label()) || z.tooltip() != null && text.equals(z.tooltip().getString())) {
                return new float[]{z.x() + z.w() / 2f, z.y() + z.h() / 2f};
            }
        }
        return null;
    }

    /** For tests: the quest whose detail panel is open. */
    public @Nullable Quest selectedQuest() {
        return selected;
    }

    /** For tests: the chapter shown on the map. */
    public @Nullable Chapter selectedChapter() {
        return chapter;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (book() == null) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (System.currentTimeMillis() - openedAt < 150) return true;
        if (menu != null) {
            if (button == LEFT && runZone(mx, my, lastModalFrom)) return true;
            menu = null;
            return true;
        }
        if (choosing != null) {
            // The picker is modal: the quest panel's buttons behind it must not react.
            if (button == LEFT) runZone(mx, my, lastModalFrom);
            return true;
        }
        if (selected != null) {
            if (button == LEFT && runZone(mx, my)) return true;
            int px = panelX();
            if (mx < px || mx > px + panelW() || my < TOP + 10) selected = null;
            return true;
        }
        // Edit mode, chapter list: press on a chapter row starts a drag (reorder); a click without moving selects it.
        if (editMode && mx < SIDEBAR && my >= TOP && button == LEFT) {
            Row r = rowAt(my);
            if (r != null && r.chapter() != null) {
                dragChapter = r.chapter();
                dragChapterStartY = my;
                dragChapterY = my;
                dragChapterMoved = false;
                selectChapter(r.chapter());
                click();
                return true;
            }
        }
        if (button == LEFT && runZone(mx, my)) return true;
        if (!inMap(mx, my)) {
            if (button == RIGHT && editMode && mx < SIDEBAR) {
                Row r = rowAt(my);
                if (r != null && r.chapter() != null) openChapterMenu(r.chapter(), mx, my);
                else if (r != null && r.group() != null) openGroupMenu(r.group(), mx, my);
            }
            return true;
        }
        Quest q = questAt(mx, my);
        if (button == LEFT) {
            if (q != null) {
                if (editMode && linkSource != null) {
                    if (q != linkSource) toggleDependency(linkSource, q);
                    linkSource = null;
                    return true;
                }
                if (editMode && event.hasShiftDown()) {
                    linkDrag = q;
                    linkX = mx;
                    linkY = my;
                    return true;
                }
                if (editMode && event.hasControlDown()) {
                    if (!selection.remove(q.id)) selection.add(q.id);
                    return true;
                }
                if (editMode) {
                    if (!selection.contains(q.id)) selection.clear();
                    dragging = q;
                    dragMoved = false;
                    dragQx = (float) q.x;
                    dragQy = (float) q.y;
                    dragStartX = mx;
                    dragStartY = my;
                    return true;
                }
                selected = q;
                panelScroll = 0;
                click();
                return true;
            }
            linkSource = null;
            if (editMode) {
                int img = imageAt(mx, my);
                if (event.hasControlDown()) {
                    boxSelecting = true;
                    boxX0 = boxX1 = mx;
                    boxY0 = boxY1 = my;
                    return true;
                }
                if (img >= 0) {
                    ChapterImage ci = chapter.images.get(img);
                    dragImage = img;
                    dragImgMoved = false;
                    dragImgX = ci.x;
                    dragImgY = ci.y;
                    dragImgOffX = qx(mx) - ci.x;
                    dragImgOffY = qy(my) - ci.y;
                    return true;
                }
                long[] line = lineAt(mx, my);
                if (line != null) {
                    openLineMenu(line, mx, my);
                    return true;
                }
                selection.clear();
            }
            panning = true;
            return true;
        }
        if (button == RIGHT && editMode) {
            if (q != null) {
                if (selection.size() > 1 && selection.contains(q.id)) openSelectionMenu(mx, my);
                else openQuestMenu(q, mx, my);
            } else {
                int img = imageAt(mx, my);
                if (img >= 0) openImageMenu(img, mx, my);
                else openMapMenu(mx, my);
            }
            return true;
        }
        if (button == RIGHT || button == MIDDLE) {
            panning = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private double snapTo(double v) {
        return EditorData.snap ? Math.round(v * 2) / 2.0 : Math.round(v * 10) / 10.0;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragChapter != null) {
            dragChapterY = event.y();
            if (Math.abs(dragChapterY - dragChapterStartY) > 4) dragChapterMoved = true;
            return true;
        }
        if (linkDrag != null) {
            linkX = event.x();
            linkY = event.y();
            return true;
        }
        if (boxSelecting) {
            boxX1 = event.x();
            boxY1 = event.y();
            return true;
        }
        if (dragImage >= 0 && chapter != null && dragImage < chapter.images.size()) {
            double nx = snapTo(qx(event.x()) - dragImgOffX);
            double ny = snapTo(qy(event.y()) - dragImgOffY);
            if (nx != dragImgX || ny != dragImgY) dragImgMoved = true;
            dragImgX = nx;
            dragImgY = ny;
            return true;
        }
        if (dragging != null) {
            if (Math.abs(event.x() - dragStartX) < 2 && Math.abs(event.y() - dragStartY) < 2 && !dragMoved) return true;
            dragQx = (float) snapTo(qx(event.x()));
            dragQy = (float) snapTo(qy(event.y()));
            if (dragQx != dragging.x || dragQy != dragging.y) dragMoved = true;
            return true;
        }
        if (panning) {
            panX += (float) dx;
            panY += (float) dy;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragChapter != null) {
            Chapter c = dragChapter;
            dragChapter = null;
            if (dragChapterMoved) dropChapter(c, event.y());
            return true;
        }
        if (linkDrag != null) {
            Quest from = linkDrag;
            linkDrag = null;
            Quest to = questAt(event.x(), event.y());
            // Drawn from the quest that comes first to the quest that needs it.
            if (to != null && to != from) toggleDependency(to, from);
            return true;
        }
        if (boxSelecting) {
            boxSelecting = false;
            double x0 = Math.min(boxX0, boxX1), x1 = Math.max(boxX0, boxX1);
            double y0 = Math.min(boxY0, boxY1), y1 = Math.max(boxY0, boxY1);
            if (chapter != null) {
                for (Quest q : chapter.quests) {
                    if (sx(q.x) >= x0 && sx(q.x) <= x1 && sy(q.y) >= y0 && sy(q.y) <= y1) selection.add(q.id);
                }
            }
            return true;
        }
        if (dragImage >= 0) {
            int idx = dragImage;
            dragImage = -1;
            if (dragImgMoved && chapter != null && idx < chapter.images.size()) {
                ChapterImage ci = chapter.images.get(idx);
                CompoundTag it = ci.write();
                it.putDouble("x", dragImgX);
                it.putDouble("y", dragImgY);
                CompoundTag op = new CompoundTag();
                op.putString("op", "image_set");
                op.putString("chapter", chapter.idString());
                op.putInt("index", idx);
                op.put("image", it);
                edit("move picture", List.of(chapter.id), op);
                ci.x = dragImgX;
                ci.y = dragImgY;
            }
            return true;
        }
        if (dragging != null) {
            Quest q = dragging;
            dragging = null;
            if (dragMoved) {
                double ddx = dragQx - q.x;
                double ddy = dragQy - q.y;
                List<Quest> moving = new ArrayList<>();
                if (selection.contains(q.id)) {
                    for (long id : selection) {
                        Quest s = book().quest(id);
                        if (s != null) moving.add(s);
                    }
                } else {
                    moving.add(q);
                }
                ListTag ops = new ListTag();
                for (Quest m : moving) {
                    CompoundTag op = new CompoundTag();
                    op.putString("op", "quest_move");
                    op.putString("id", m.idString());
                    op.putDouble("x", m == q ? dragQx : m.x + ddx);
                    op.putDouble("y", m == q ? dragQy : m.y + ddy);
                    ops.add(op);
                }
                edit(moving.size() > 1 ? "move quests" : "move quest", chaptersOf(moving), batch(ops));
                for (Quest m : moving) {
                    if (m == q) continue;
                    m.x += ddx;
                    m.y += ddy;
                }
                q.x = dragQx;
                q.y = dragQy;
            } else {
                selected = q;
                panelScroll = 0;
            }
            return true;
        }
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (selected != null) {
            int max = Math.max(0, panelContentHeight - (height - TOP - 20 - 58));
            panelScroll = Mth.clamp(panelScroll - (int) (scrollY * 18), 0, max);
            return true;
        }
        if (mx < SIDEBAR) {
            sidebarScroll = Mth.clamp(sidebarScroll - (int) (scrollY * 16), 0, maxSidebarScroll);
            return true;
        }
        float old = zoom;
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f), 0.35f, 3f);
        // Zoom around the cursor.
        float cx = SIDEBAR + (width - SIDEBAR) / 2f;
        float cy = TOP + (height - TOP) / 2f;
        panX = (float) (mx - cx - (mx - cx - panX) * zoom / old);
        panY = (float) (my - cy - (my - cy - panY) * zoom / old);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 26.3 reports SDL key codes (Escape = 41, not GLFW's 256): with the old number Escape closed the whole book.
        if (event.isEscape()) {
            if (menu != null) {
                menu = null;
                return true;
            }
            if (choosing != null) {
                choosing = null;
                return true;
            }
            if (selected != null) {
                selected = null;
                return true;
            }
            if (linkSource != null) {
                linkSource = null;
                return true;
            }
            if (!selection.isEmpty()) {
                selection.clear();
                return true;
            }
        }
        if (editMode && selected == null && menu == null && choosing == null) {
            if (event.hasControlDown() && event.key() == InputConstants.KEY_Z) {
                undo();
                return true;
            }
            if (event.isCopy()) {
                copySelection(hoveredQuest());
                return true;
            }
            if (event.isPaste()) {
                paste(qx(lastMouseX), qy(lastMouseY));
                return true;
            }
            if ((event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE) && !selection.isEmpty()) {
                openSelectionMenu(lastMouseX, lastMouseY);
                return true;
            }
        }
        if (ClientQuests.OPEN_BOOK.matches(event) && selected == null && menu == null) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        lastMouseX = mx;
        lastMouseY = my;
        super.mouseMoved(mx, my);
    }

    private @Nullable Quest hoveredQuest() {
        return questAt(lastMouseX, lastMouseY);
    }

    @Override
    public void onClose() {
        if (chapter != null) VIEWS.put(chapter.id, new float[]{panX, panY, zoom});
        super.onClose();
    }

    private void jumpTo(Quest q) {
        if (q.chapter != chapter) selectChapter(q.chapter);
        selected = q;
        panelScroll = 0;
        panX = (float) (-q.x * GRID * zoom);
        panY = (float) (-q.y * GRID * zoom);
    }

    // ------------------------------------------------------------------------------- hit tests (edit mode)

    private @Nullable Row rowAt(double my) {
        for (Row r : lastRows) if (my >= r.y() && my < r.y() + r.h()) return r;
        return null;
    }

    /** The picture under the cursor (topmost first), or -1. */
    private int imageAt(double mx, double my) {
        if (chapter == null) return -1;
        for (int i = chapter.images.size() - 1; i >= 0; i--) {
            ChapterImage img = chapter.images.get(i);
            float w = (float) (img.width * GRID * zoom);
            float h = (float) (img.height * GRID * zoom);
            double cx = i == dragImage ? sx(dragImgX) : sx(img.x);
            double cy = i == dragImage ? sy(dragImgY) : sy(img.y);
            if (Math.abs(mx - cx) <= w / 2 && Math.abs(my - cy) <= h / 2) return i;
        }
        return -1;
    }

    /** The dependency line under the cursor: {quest, its dependency}, or null. */
    private long @Nullable [] lineAt(double mx, double my) {
        if (chapter == null) return null;
        for (Quest q : chapter.quests) {
            for (long depId : q.dependencies) {
                Quest d = book().quest(depId);
                if (d == null || d.chapter != chapter) continue;
                double x1 = sx(d.x), y1 = sy(d.y), x2 = sx(q.x), y2 = sy(q.y);
                double len2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
                if (len2 < 1) continue;
                double t = Mth.clamp(((mx - x1) * (x2 - x1) + (my - y1) * (y2 - y1)) / len2, 0, 1);
                double px = x1 + t * (x2 - x1), py = y1 + t * (y2 - y1);
                double dist = Math.sqrt((mx - px) * (mx - px) + (my - py) * (my - py));
                double r = nodeSize(q) / 2.0 + 2;
                double fromEnd = Math.min(Math.sqrt((mx - x1) * (mx - x1) + (my - y1) * (my - y1)), Math.sqrt((mx - x2) * (mx - x2) + (my - y2) * (my - y2)));
                if (dist <= 3 && fromEnd > r) return new long[]{q.id, d.id};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------- editing

    private void edit(CompoundTag op) {
        ClientPlayNetworking.send(new QuestNet.Edit(op));
    }

    /** Sends an edit and remembers how to undo it: the touched chapters as they are now. */
    void edit(String name, Collection<Long> chapters, CompoundTag op) {
        edit(name, chapters, false, false, false, op);
    }

    void edit(String name, Collection<Long> chapters, boolean groups, boolean tables, boolean data, CompoundTag op) {
        if (book() != null) EditorData.remember(name, EditorData.snapshot(book(), chapters, groups, tables, data));
        edit(op);
    }

    /** For the tables screen: an edit of the reward tables (undoable). */
    void editTables(String name, CompoundTag op) {
        edit(name, List.of(), false, true, false, op);
    }

    public void undo() {
        EditorData.Step s = EditorData.popUndo();
        if (s == null) return;
        selection.clear();
        edit(s.restore());
        click();
    }

    private static CompoundTag batch(ListTag ops) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "batch");
        op.put("ops", ops);
        return op;
    }

    private static List<Long> chaptersOf(Collection<Quest> quests) {
        List<Long> out = new ArrayList<>();
        for (Quest q : quests) if (!out.contains(q.chapter.id)) out.add(q.chapter.id);
        return out;
    }

    private void sendQuest(Quest q, CompoundTag questTag) {
        sendQuest("edit quest", q.chapter, questTag);
    }

    private void sendQuest(String name, Chapter c, CompoundTag questTag) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "quest_set");
        op.putString("chapter", c.idString());
        op.put("quest", questTag);
        edit(name, List.of(c.id), op);
    }

    private void toggleDependency(Quest q, Quest dep) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "dependency");
        op.putString("id", q.idString());
        op.putString("dep", dep.idString());
        op.putBoolean("remove", q.dependencies.contains(dep.id));
        edit(q.dependencies.contains(dep.id) ? "remove link" : "add link", List.of(q.chapter.id), op);
    }

    private static String randomId() {
        return QuestObject.idToString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0x7FFFFFFFFFFFFFFFL);
    }

    private ItemStack held() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
    }

    /** The quest form (everything a quest file can say). */
    public void editQuestForm(Quest q) {
        minecraft.gui.setScreen(Forms.quest(this, q, q.write(), t -> {
            if (!t.contains("id")) t.putString("id", q.idString());
            sendQuest(q, t);
        }));
    }

    /** A new quest at a map position: the form opens first, Cancel creates nothing. */
    public void newQuestForm(Chapter c, double x, double y) {
        CompoundTag t = new CompoundTag();
        t.putString("id", randomId());
        t.putString("title", Component.translatable("questsicons.edit.default_quest_title").getString());
        t.putDouble("x", x);
        t.putDouble("y", y);
        if (c.defaultQuestSize != 1.0) t.putDouble("size", c.defaultQuestSize);
        t.put("tasks", new ListTag());
        minecraft.gui.setScreen(Forms.quest(this, null, t, saved -> sendQuest("new quest", c, saved)));
    }

    public void editChapterForm(Chapter c) {
        minecraft.gui.setScreen(Forms.chapter(this, chapterTag(c), t -> {
            t.putString("id", c.idString());
            sendChapter(c, t);
        }));
    }

    private void editGroupForm(ChapterGroup grp) {
        minecraft.gui.setScreen(Forms.group(this, grp.write(), t -> {
            t.putString("id", grp.idString());
            CompoundTag op = new CompoundTag();
            op.putString("op", "group_set");
            op.put("group", t);
            edit("edit group", List.of(), true, false, false, op);
        }));
    }

    private void editBook() {
        minecraft.gui.setScreen(Forms.book(this, book().writeData(), t -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "book_set");
            op.put("data", t);
            edit("book settings", List.of(), false, false, true, op);
        }));
    }

    private void editImageForm(int idx) {
        if (chapter == null || idx >= chapter.images.size()) return;
        Chapter c = chapter;
        minecraft.gui.setScreen(Forms.image(this, 0, c.images.get(idx).write(), t -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "image_set");
            op.putString("chapter", c.idString());
            op.putInt("index", idx);
            op.put("image", t);
            edit("edit picture", List.of(c.id), op);
        }));
    }

    /** Pick a library picture, then place it here (its form opens with the default size). */
    private void addImage(Chapter c, double x, double y) {
        minecraft.gui.setScreen(PickerScreen.images(this, tex -> {
            CompoundTag t = new CompoundTag();
            t.putString("image", tex);
            t.putDouble("x", x);
            t.putDouble("y", y);
            double w = 1, h = 1;
            for (IconLibrary.MapImage m : IconLibrary.MAP_IMAGES) {
                if (m.texture().equals(tex)) {
                    w = m.gridWidth();
                    h = m.gridHeight();
                }
            }
            t.putDouble("width", w);
            t.putDouble("height", h);
            minecraft.gui.setScreen(Forms.image(this, 0, t, saved -> {
                CompoundTag op = new CompoundTag();
                op.putString("op", "image_add");
                op.putString("chapter", c.idString());
                op.put("image", saved);
                edit("add picture", List.of(c.id), op);
            }));
        }));
    }

    /** The icon picker for a quest, a chapter, a task or a reward: the library first, every other item in a tab. */
    private void pickIcon(Component what, java.util.function.Consumer<CompoundTag> applyIcon) {
        minecraft.gui.setScreen(PickerScreen.items(this, Component.translatable("questsicons.picker.icon_for", what), true, st -> {
            CompoundTag holder = new CompoundTag();
            holder.put("icon", ItemSpec.of(st.copyWithCount(1), reg()).write());
            applyIcon.accept(holder);
        }));
    }

    private void pickQuestIcon(Quest q) {
        pickIcon(q.displayTitle(), h -> {
            CompoundTag t = q.write();
            t.put("icon", h.get("icon"));
            sendQuest("quest icon", q.chapter, t);
        });
    }

    private void pickChapterIcon(Chapter c) {
        pickIcon(c.displayTitle(), h -> {
            CompoundTag t = chapterTag(c);
            t.put("icon", h.get("icon"));
            sendChapter(c, t);
        });
    }

    /** Task or reward icon: index into the quest's tasks (kind "tasks") or rewards (kind "rewards"). */
    private void pickPartIcon(Quest q, String kind, int index, Component what) {
        pickIcon(what, h -> {
            CompoundTag t = q.write();
            ListTag l = t.getListOrEmpty(kind);
            if (index < l.size() && l.get(index) instanceof CompoundTag part) {
                part.put("icon", h.get("icon"));
                sendQuest(kind.equals("tasks") ? "task icon" : "reward icon", q.chapter, t);
            }
        });
    }

    // ------------------------------------------------------------------------------- copy, paste, delete

    private void copySelection(@Nullable Quest fallback) {
        List<Quest> qs = new ArrayList<>();
        for (long id : selection) {
            Quest q = book().quest(id);
            if (q != null) qs.add(q);
        }
        if (qs.isEmpty() && fallback != null) qs.add(fallback);
        if (qs.isEmpty()) return;
        EditorData.CLIPBOARD.clear();
        for (Quest q : qs) EditorData.CLIPBOARD.add(q.write());
        if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.translatable("questsicons.edit.copied", qs.size()));
    }

    /** Pastes the copied quests with new ids around a map position; links among them follow, others stay. */
    private void paste(double x, double y) {
        if (chapter == null || EditorData.CLIPBOARD.isEmpty()) return;
        CompoundTag first = EditorData.CLIPBOARD.getFirst();
        double ox = snapTo(x) - first.getDoubleOr("x", 0);
        double oy = snapTo(y) - first.getDoubleOr("y", 0);
        java.util.Map<Long, String> newIds = new java.util.HashMap<>();
        for (CompoundTag t : EditorData.CLIPBOARD) newIds.put(QuestObject.parseId(t.getStringOr("id", "")), randomId());
        ListTag ops = new ListTag();
        selection.clear();
        for (CompoundTag src : EditorData.CLIPBOARD) {
            CompoundTag t = src.copy();
            String id = newIds.get(QuestObject.parseId(src.getStringOr("id", "")));
            t.putString("id", id);
            t.putDouble("x", src.getDoubleOr("x", 0) + ox);
            t.putDouble("y", src.getDoubleOr("y", 0) + oy);
            for (var task : t.getListOrEmpty("tasks")) if (task instanceof CompoundTag c) c.putString("id", randomId());
            for (var rew : t.getListOrEmpty("rewards")) if (rew instanceof CompoundTag c) c.putString("id", randomId());
            ListTag deps = new ListTag();
            for (var d : t.getListOrEmpty("dependencies")) {
                if (!(d instanceof net.minecraft.nbt.StringTag s)) continue;
                long dep = QuestObject.parseId(s.value());
                String mapped = newIds.get(dep);
                if (mapped != null) deps.add(net.minecraft.nbt.StringTag.valueOf(mapped));
                else if (book().quest(dep) != null) deps.add(s);
            }
            if (deps.isEmpty()) t.remove("dependencies");
            else t.put("dependencies", deps);
            CompoundTag op = new CompoundTag();
            op.putString("op", "quest_set");
            op.putString("chapter", chapter.idString());
            op.put("quest", t);
            ops.add(op);
            selection.add(QuestObject.parseId(id));
        }
        edit(ops.size() > 1 ? "paste quests" : "paste quest", List.of(chapter.id), batch(ops));
    }

    private void deleteQuests(Collection<Long> ids) {
        List<Quest> qs = new ArrayList<>();
        for (long id : ids) {
            Quest q = book().quest(id);
            if (q != null) qs.add(q);
        }
        if (qs.isEmpty()) return;
        ListTag ops = new ListTag();
        for (Quest q : qs) {
            CompoundTag op = new CompoundTag();
            op.putString("op", "quest_delete");
            op.putString("id", q.idString());
            ops.add(op);
        }
        // Undo needs every chapter whose quests lose a dependency, too.
        List<Long> touched = new ArrayList<>(chaptersOf(qs));
        for (Quest other : book().allQuests()) {
            for (Quest q : qs) if (other.dependencies.contains(q.id) && !touched.contains(other.chapter.id)) touched.add(other.chapter.id);
        }
        edit(qs.size() > 1 ? "delete quests" : "delete quest", touched, batch(ops));
        if (selected != null && ids.contains(selected.id)) selected = null;
        selection.clear();
    }

    private void moveToChapter(Collection<Long> ids) {
        List<String> values = new ArrayList<>();
        List<Component> names = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        for (Chapter c : book().chapters) {
            if (c == chapter) continue;
            values.add(c.idString());
            names.add(c.displayTitle());
            icons.add(c.displayIcon(reg()));
        }
        minecraft.gui.setScreen(PickerScreen.list(this, Component.translatable("questsicons.edit.move_to_chapter"), values, names, icons, cid -> {
            Chapter target = book().chapter(QuestObject.parseId(cid));
            if (target == null) return;
            List<Quest> qs = new ArrayList<>();
            for (long id : ids) {
                Quest q = book().quest(id);
                if (q != null) qs.add(q);
            }
            ListTag ops = new ListTag();
            for (Quest q : qs) {
                CompoundTag op = new CompoundTag();
                op.putString("op", "quest_move");
                op.putString("id", q.idString());
                op.putString("chapter", cid);
                ops.add(op);
            }
            List<Long> touched = new ArrayList<>(chaptersOf(qs));
            touched.add(target.id);
            edit("move to chapter", touched, batch(ops));
            selection.clear();
        }));
    }

    private void dropChapter(Chapter c, double my) {
        Row r = rowAt(my);
        if (r == null) return;
        List<Chapter> all = book().chapters;
        CompoundTag op = new CompoundTag();
        op.putString("op", "chapter_move");
        op.putString("id", c.idString());
        int from = all.indexOf(c);
        if (r.chapter() != null) {
            if (r.chapter() == c) return;
            int to = all.indexOf(r.chapter());
            op.putInt("index", from < to ? to - 1 + (my > r.y() + r.h() / 2.0 ? 1 : 0) : to + (my > r.y() + r.h() / 2.0 ? 1 : 0));
            op.putString("group", r.chapter().group);
        } else if (r.group() != null) {
            op.putInt("index", from);
            op.putString("group", r.group().idString());
        } else {
            return;
        }
        // Undo of a reorder puts every chapter back in its place (and the moved one in its old group).
        List<Long> allIds = new ArrayList<>();
        for (Chapter ch : all) allIds.add(ch.id);
        edit("move chapter", allIds, op);
    }

    // ------------------------------------------------------------------------------- menus

    /** A menu entry that asks "click again" before it runs (deletes). */
    private MenuEntry confirmEntry(Component label, double mx, double my, Runnable action) {
        return new MenuEntry(label.copy().withStyle(net.minecraft.ChatFormatting.RED), () -> menu = new ContextMenu((int) mx, (int) my, List.of(
                new MenuEntry(Component.translatable("questsicons.edit.confirm_delete").withStyle(net.minecraft.ChatFormatting.RED), action),
                new MenuEntry(Component.translatable("gui.cancel"), () -> {
                }))));
    }

    private void openQuestMenu(Quest q, double mx, double my) {
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.edit_quest_form"), () -> editQuestForm(q)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.open"), () -> {
            selected = q;
            panelScroll = 0;
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.choose_icon"), () -> pickQuestIcon(q)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.link"), () -> linkSource = q));
        if (!q.dependencies.isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.clear_deps"), () -> {
                CompoundTag t = q.write();
                t.remove("dependencies");
                sendQuest("clear links", q.chapter, t);
            }));
        }
        e.add(new MenuEntry(Component.translatable("questsicons.edit.copy"), () -> {
            selection.clear();
            copySelection(q);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.duplicate"), () -> {
            CompoundTag t = q.write();
            t.putString("id", randomId());
            t.putDouble("x", q.x + 1);
            for (var task : t.getListOrEmpty("tasks")) if (task instanceof CompoundTag c) c.putString("id", randomId());
            for (var rew : t.getListOrEmpty("rewards")) if (rew instanceof CompoundTag c) c.putString("id", randomId());
            sendQuest("duplicate quest", q.chapter, t);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_to_chapter"), () -> moveToChapter(List.of(q.id))));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.cycle_shape"), () -> {
            CompoundTag t = q.write();
            t.putString("shape", q.shape().next().id());
            sendQuest("quest shape", q.chapter, t);
        }));
        ItemStack held = held();
        if (!held.isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.icon_from_hand"), () -> {
                CompoundTag t = q.write();
                t.put("icon", ItemSpec.of(held.copyWithCount(1), reg()).write());
                sendQuest("quest icon", q.chapter, t);
            }));
            e.add(new MenuEntry(Component.translatable("questsicons.edit.task_from_hand"), () -> {
                CompoundTag t = q.write();
                CompoundTag task = new CompoundTag();
                task.putString("id", randomId());
                task.putString("type", "item");
                task.put("item", ItemSpec.of(held.copyWithCount(1), reg()).write());
                if (held.getCount() > 1) task.putLong("count", held.getCount());
                ListTag l = t.getListOrEmpty("tasks");
                l.add(task);
                t.put("tasks", l);
                sendQuest("add task", q.chapter, t);
            }));
            e.add(new MenuEntry(Component.translatable("questsicons.edit.reward_from_hand"), () -> {
                CompoundTag t = q.write();
                CompoundTag r = new CompoundTag();
                r.putString("id", randomId());
                r.putString("type", "item");
                r.put("item", ItemSpec.of(held.copy(), reg()).write());
                ListTag l = t.getListOrEmpty("rewards");
                l.add(r);
                t.put("rewards", l);
                sendQuest("add reward", q.chapter, t);
            }));
        }
        e.add(new MenuEntry(Component.translatable("questsicons.edit.edit_snbt"), () -> editQuestSnbt(q)));
        e.add(confirmEntry(Component.translatable("questsicons.edit.delete"), mx, my, () -> deleteQuests(List.of(q.id))));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openSelectionMenu(double mx, double my) {
        List<Long> ids = new ArrayList<>(selection);
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.copy_n", ids.size()), () -> copySelection(null)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_n_to_chapter", ids.size()), () -> moveToChapter(ids)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.clear_selection"), selection::clear));
        e.add(confirmEntry(Component.translatable("questsicons.edit.delete_n", ids.size()), mx, my, () -> deleteQuests(ids)));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openLineMenu(long[] line, double mx, double my) {
        Quest q = book().quest(line[0]);
        Quest d = book().quest(line[1]);
        if (q == null || d == null) return;
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.remove_link", d.displayTitle(), q.displayTitle()), () -> toggleDependency(q, d)));
        e.add(new MenuEntry(Component.translatable("gui.cancel"), () -> {
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openImageMenu(int idx, double mx, double my) {
        Chapter c = chapter;
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.edit_image"), () -> editImageForm(idx)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.image_front"), () -> {
            CompoundTag it = c.images.get(idx).write();
            it.putBoolean("front", !c.images.get(idx).front);
            CompoundTag op = new CompoundTag();
            op.putString("op", "image_set");
            op.putString("chapter", c.idString());
            op.putInt("index", idx);
            op.put("image", it);
            edit("picture layer", List.of(c.id), op);
        }));
        e.add(confirmEntry(Component.translatable("questsicons.edit.delete_image"), mx, my, () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "image_delete");
            op.putString("chapter", c.idString());
            op.putInt("index", idx);
            edit("delete picture", List.of(c.id), op);
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openMapMenu(double mx, double my) {
        if (chapter == null) return;
        Chapter c = chapter;
        double x = snapTo(qx(mx));
        double y = snapTo(qy(my));
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.new_quest"), () -> newQuestForm(c, x, y)));
        if (!EditorData.CLIPBOARD.isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.paste", EditorData.CLIPBOARD.size()), () -> paste(x, y)));
        }
        e.add(new MenuEntry(Component.translatable("questsicons.edit.add_image"), () -> addImage(c, x, y)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.chapter_settings"), () -> editChapterForm(c)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.chapter_snbt"), () -> editChapterSnbt(c)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.reload"), () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "reload");
            edit(op);
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openChapterMenu(Chapter c, double mx, double my) {
        selectChapter(c);
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.chapter_settings"), () -> editChapterForm(c)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.choose_icon"), () -> pickChapterIcon(c)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_up"), () -> moveChapter(c, -1)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_down"), () -> moveChapter(c, 1)));
        if (!held().isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.icon_from_hand"), () -> {
                CompoundTag t = chapterTag(c);
                t.put("icon", ItemSpec.of(held().copyWithCount(1), reg()).write());
                sendChapter(c, t);
            }));
        }
        e.add(confirmEntry(Component.translatable("questsicons.edit.delete_chapter"), mx, my, () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "chapter_delete");
            op.putString("id", c.idString());
            edit("delete chapter", List.of(c.id), op);
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openGroupMenu(ChapterGroup grp, double mx, double my) {
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.edit_group"), () -> editGroupForm(grp)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.new_chapter_in_group"), () -> newChapter(grp.idString())));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_up"), () -> moveGroup(grp, -1)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_down"), () -> moveGroup(grp, 1)));
        e.add(confirmEntry(Component.translatable("questsicons.edit.delete_group"), mx, my, () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "group_delete");
            op.putString("id", grp.idString());
            List<Long> touched = new ArrayList<>();
            for (Chapter ch : book().chapters) if (ch.group.equals(grp.idString()) || ch.group.equals(grp.title)) touched.add(ch.id);
            edit("delete group", touched, true, false, false, op);
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void moveGroup(ChapterGroup grp, int delta) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "group_move");
        op.putString("id", grp.idString());
        op.putInt("delta", delta);
        edit("move group", List.of(), true, false, false, op);
    }

    private void newChapter(String group) {
        String id = randomId();
        CompoundTag op = new CompoundTag();
        op.putString("op", "chapter_new");
        op.putString("id", id);
        op.putString("title", Component.translatable("questsicons.edit.default_chapter_title").getString());
        op.putString("group", group);
        edit("new chapter", List.of(QuestObject.parseId(id)), op);
        pendingChapter = QuestObject.parseId(id);
    }

    private void newGroup() {
        CompoundTag op = new CompoundTag();
        op.putString("op", "group_new");
        op.putString("id", randomId());
        op.putString("title", Component.translatable("questsicons.edit.default_group_title").getString());
        edit("new group", List.of(), true, false, false, op);
    }

    private void moveChapter(Chapter c, int delta) {
        List<Long> allIds = new ArrayList<>();
        for (Chapter ch : book().chapters) allIds.add(ch.id);
        CompoundTag op = new CompoundTag();
        op.putString("op", "chapter_move");
        op.putString("id", c.idString());
        op.putInt("delta", delta);
        edit("move chapter", allIds, op);
    }

    private static CompoundTag chapterTag(Chapter c) {
        CompoundTag t = c.write();
        t.remove("quests");
        t.remove("images");
        return t;
    }

    private void sendChapter(Chapter c, CompoundTag t) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "chapter_set");
        op.put("chapter", t);
        edit("chapter settings", List.of(c.id), op);
    }

    private void editQuestSnbt(Quest q) {
        minecraft.gui.setScreen(new SnbtEditScreen(this, Component.translatable("questsicons.edit.edit_quest", q.displayTitle()), q.write(), t -> {
            if (!t.contains("id")) t.putString("id", q.idString());
            sendQuest(q, t);
        }));
    }

    private void editChapterSnbt(Chapter c) {
        CompoundTag tag = c.write();
        tag.remove("quests");
        minecraft.gui.setScreen(new SnbtEditScreen(this, Component.translatable("questsicons.edit.edit_chapter", c.displayTitle()), tag, t -> {
            t.putString("id", c.idString());
            sendChapter(c, t);
        }));
    }

    // ------------------------------------------------------------------------------- test hooks

    /** For tests: the quests selected for a group move, copy or delete. */
    public java.util.Set<Long> selection() {
        return selection;
    }

    /** For tests: the GUI position of a chapter row or group header in the chapter list. */
    public float[] rowPos(long chapterOrGroupId) {
        for (Row r : lastRows) {
            if (r.chapter() != null && r.chapter().id == chapterOrGroupId || r.group() != null && r.group().id == chapterOrGroupId) {
                return new float[]{SIDEBAR / 2f, r.y() + r.h() / 2f};
            }
        }
        return null;
    }

    /** For tests: where a map position (in quest units) is on the screen. */
    public float[] mapPos(double x, double y) {
        return new float[]{sx(x), sy(y)};
    }

    public boolean editMode() {
        return editMode;
    }
}
