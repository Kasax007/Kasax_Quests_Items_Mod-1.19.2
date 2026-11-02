package net.kasax.questsicons.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.questsicons.client.ClientQuests;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    private static final int SIDEBAR = 136;
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
    private final List<Zone> lastZones = new ArrayList<>();
    private long openedAt;

    record Zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip) {
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
        if (book() == null) return;
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
    }

    private @Nullable Chapter firstVisibleChapter() {
        for (Chapter c : book().chapters) if (isChapterVisible(c)) return c;
        return book().chapters.isEmpty() ? null : book().chapters.get(0);
    }

    private boolean isChapterVisible(Chapter c) {
        if (editMode) return true;
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
        zones.clear();
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
                if (!editMode && !p.isVisible(d)) continue;
                int color = p.isQuestCompleted(q.id) ? GREEN : p.isQuestCompleted(d.id) ? ACCENT : 0xFF4A4458;
                if (q == selected || d == selected) color = GOLD;
                float x1 = d == dragging ? sx(dragQx) : sx(d.x);
                float y1 = d == dragging ? sy(dragQy) : sy(d.y);
                float x2 = q == dragging ? sx(dragQx) : sx(q.x);
                float y2 = q == dragging ? sy(dragQy) : sy(q.y);
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
            float cx = q == dragging ? sx(dragQx) : sx(q.x);
            float cy = q == dragging ? sy(dragQy) : sy(q.y);
            int x = Math.round(cx - s / 2f);
            int y = Math.round(cy - s / 2f);
            int tint = done ? GREEN : canStart ? AVAILABLE : LOCKED;
            if (!visible) tint = 0x80FFFFFF & tint;
            QuestShape shape = q.shape();
            if (q == hovered || q == selected || q == linkSource) {
                int gs = s + Math.max(4, s / 4);
                g.blit(RenderPipelines.GUI_TEXTURED, shapeTex(shape), Math.round(cx - gs / 2f), Math.round(cy - gs / 2f), 0, 0, gs, gs, 64, 64, 64, 64,
                        q == linkSource ? 0xFFFF5555 : 0xFFFFE27A);
            }
            g.blit(RenderPipelines.GUI_TEXTURED, shapeTex(shape), x, y, 0, 0, s, s, 64, 64, 64, 64, tint);
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
        int w = Math.round((float) img.width * GRID * zoom);
        int h = Math.round((float) img.height * GRID * zoom);
        float cx = sx(img.x);
        float cy = sy(img.y);
        try {
            Identifier tex = Identifier.parse(img.image);
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
                g.text(font, name, 6, y + 2, MUTED, false);
                g.fill(6, y + 12, SIDEBAR - 8, y + 13, 0x40FFFFFF);
                y += 16;
            }
            boolean sel = c == chapter;
            boolean hov = mouseX >= 0 && mouseX < SIDEBAR && mouseY >= y && mouseY < y + 20 && mouseY >= TOP;
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
            String title = font.plainSubstrByWidth(c.displayTitle().getString(), SIDEBAR - 34 - pctW - 6);
            g.text(font, Component.literal(title).setStyle(c.displayTitle().getStyle()), 27, y + 6, sel ? TEXT : 0xFFD6C8EA, false);
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
                }, null));
            }
            y += 22;
        }
        if (editMode) {
            int by = y + 4;
            button(g, 6, by, SIDEBAR - 12, 16, Component.translatable("questsicons.edit.new_chapter"), mouseX, mouseY, () -> {
                CompoundTag op = new CompoundTag();
                op.putString("op", "chapter_new");
                op.putString("title", "New Chapter");
                edit(op);
            });
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
                editMode = !editMode;
                linkSource = null;
                if (chapter == null) chapter = firstVisibleChapter();
            });
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

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, int mouseX, int mouseY, Runnable action) {
        boolean hov = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        frame(g, x, y, w, h, hov ? 0xFF3A2358 : 0xFF261839, hov ? ACCENT : 0xFF5B3A86);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, hov ? 0xFFFFFFFF : TEXT);
        zones.add(new Zone(x, y, w, h, action, null));
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
                zones.add(new Zone(px + 16, y, textW, 10, () -> jumpTo(target), null));
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
                if (t instanceof Tasks.ItemTask it && it.item.isTag() && !it.item.tagItems().isEmpty()) {
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
                String title = font.plainSubstrByWidth(t.title(reg()).getString(), right - ptw - (px + 32) - 6);
                g.text(font, title, px + 32, y + 5, tdone ? 0xFFB9F5C9 : TEXT, false);
                if (mouseY >= y && mouseY < y + 18 && mouseX >= px + 8 && mouseX < px + 30) {
                    zones.add(new Zone(px + 8, y, 22, 18, () -> {
                    }, t.title(reg())));
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
                g.text(font, font.plainSubstrByWidth(title.getString(), right - (px + 32) - 4), px + 32, y + 5, claimed ? MUTED : TEXT, false);
                if (mouseY >= y && mouseY < y + 18 && mouseX >= px + 8 && mouseX < px + 30) {
                    List<ItemStack> choices = r.choices(reg());
                    zones.add(new Zone(px + 8, y, 22, 18, () -> {
                    }, choices.isEmpty() ? title : Component.translatable("questsicons.gui.choice_count", choices.size())));
                }
                y += 22;
            }
        }
        panelContentHeight = y + panelScroll - bodyTop + 4;
        g.disableScissor();
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
            Component e = Component.translatable("questsicons.edit.edit_snbt");
            int ew = font.width(e) + 12;
            button(g, fx, fy, ew, 16, e, mouseX, mouseY, () -> editQuestSnbt(quest));
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
            g.blit(RenderPipelines.GUI_TEXTURED, Identifier.parse(tex), ix, y, 0, 0, w, h, w, h);
        } catch (Exception ignored) {
        }
        return y + h + 4;
    }

    private void drawChoice(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<ItemStack> items = choosing.choices(reg());
        int cols = Math.min(6, Math.max(1, items.size()));
        int rows = (items.size() + cols - 1) / cols;
        int w = cols * 26 + 16;
        int h = rows * 26 + 40;
        int x = SIDEBAR + (width - SIDEBAR - w) / 2;
        int y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x80000000);
        frame(g, x, y, w, h, PANEL, GOLD);
        g.centeredText(font, Component.translatable("questsicons.gui.pick_one"), x + w / 2, y + 6, GOLD);
        for (int i = 0; i < items.size(); i++) {
            int ix = x + 8 + (i % cols) * 26;
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
            }, null));
            ey += 14;
        }
    }

    // ------------------------------------------------------------------------------- input

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private boolean runZone(double mx, double my) {
        for (int i = lastZones.size() - 1; i >= 0; i--) {
            Zone z = lastZones.get(i);
            if (z.contains(mx, my)) {
                z.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (book() == null) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (System.currentTimeMillis() - openedAt < 150) return true;
        if (menu != null) {
            if (button == 0 && runZone(mx, my)) return true;
            menu = null;
            return true;
        }
        if (choosing != null) {
            if (button == 0) runZone(mx, my);
            return true;
        }
        if (selected != null) {
            if (button == 0 && runZone(mx, my)) return true;
            int px = panelX();
            if (mx < px || mx > px + panelW() || my < TOP + 10) selected = null;
            return true;
        }
        if (button == 0 && runZone(mx, my)) return true;
        if (!inMap(mx, my)) {
            if (button == 1 && editMode && mx < SIDEBAR) openChapterMenu(mx, my);
            return true;
        }
        Quest q = questAt(mx, my);
        if (button == 0) {
            if (q != null) {
                if (editMode && linkSource != null) {
                    if (q != linkSource) {
                        CompoundTag op = new CompoundTag();
                        op.putString("op", "dependency");
                        op.putString("id", linkSource.idString());
                        op.putString("dep", q.idString());
                        op.putBoolean("remove", linkSource.dependencies.contains(q.id));
                        edit(op);
                    }
                    linkSource = null;
                    return true;
                }
                if (editMode) {
                    dragging = q;
                    dragMoved = false;
                    dragQx = (float) q.x;
                    dragQy = (float) q.y;
                    return true;
                }
                selected = q;
                panelScroll = 0;
                click();
                return true;
            }
            linkSource = null;
            panning = true;
            return true;
        }
        if (button == 1 && editMode) {
            if (q != null) openQuestMenu(q, mx, my);
            else openMapMenu(mx, my);
            return true;
        }
        if (button == 1 || button == 2) {
            panning = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragQx = (float) (Math.round(qx(event.x()) * 2) / 2.0);
            dragQy = (float) (Math.round(qy(event.y()) * 2) / 2.0);
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
        if (dragging != null) {
            Quest q = dragging;
            dragging = null;
            if (dragMoved) {
                CompoundTag op = new CompoundTag();
                op.putString("op", "quest_move");
                op.putString("id", q.idString());
                op.putDouble("x", dragQx);
                op.putDouble("y", dragQy);
                q.x = dragQx;
                q.y = dragQy;
                edit(op);
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
        if (event.key() == 256) {
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
        }
        if (ClientQuests.OPEN_BOOK.matches(event) && selected == null && menu == null) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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

    // ------------------------------------------------------------------------------- editing

    private void edit(CompoundTag op) {
        ClientPlayNetworking.send(new QuestNet.Edit(op));
    }

    private void sendQuest(Quest q, CompoundTag questTag) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "quest_set");
        op.putString("chapter", q.chapter.idString());
        op.put("quest", questTag);
        edit(op);
    }

    private static String randomId() {
        return QuestObject.idToString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0x7FFFFFFFFFFFFFFFL);
    }

    private ItemStack held() {
        return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
    }

    private void openQuestMenu(Quest q, double mx, double my) {
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.open"), () -> {
            selected = q;
            panelScroll = 0;
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.edit_snbt"), () -> editQuestSnbt(q)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.link"), () -> linkSource = q));
        if (!q.dependencies.isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.clear_deps"), () -> {
                CompoundTag t = q.write();
                t.remove("dependencies");
                sendQuest(q, t);
            }));
        }
        ItemStack held = held();
        if (!held.isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.icon_from_hand"), () -> {
                CompoundTag t = q.write();
                t.put("icon", ItemSpec.of(held.copyWithCount(1), reg()).write());
                sendQuest(q, t);
            }));
            e.add(new MenuEntry(Component.translatable("questsicons.edit.task_from_hand"), () -> {
                CompoundTag t = q.write();
                CompoundTag task = new CompoundTag();
                task.putString("id", randomId());
                task.putString("type", "item");
                task.put("item", ItemSpec.of(held.copyWithCount(1), reg()).write());
                if (held.getCount() > 1) task.putLong("count", held.getCount());
                t.getListOrEmpty("tasks").add(task);
                if (!t.contains("tasks")) {
                    net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
                    l.add(task);
                    t.put("tasks", l);
                }
                sendQuest(q, t);
            }));
            e.add(new MenuEntry(Component.translatable("questsicons.edit.reward_from_hand"), () -> {
                CompoundTag t = q.write();
                CompoundTag r = new CompoundTag();
                r.putString("id", randomId());
                r.putString("type", "item");
                r.put("item", ItemSpec.of(held.copy(), reg()).write());
                net.minecraft.nbt.ListTag l = t.getListOrEmpty("rewards");
                l.add(r);
                t.put("rewards", l);
                sendQuest(q, t);
            }));
        }
        e.add(new MenuEntry(Component.translatable("questsicons.edit.add_checkmark"), () -> {
            CompoundTag t = q.write();
            CompoundTag task = new CompoundTag();
            task.putString("id", randomId());
            task.putString("type", "checkmark");
            net.minecraft.nbt.ListTag l = t.getListOrEmpty("tasks");
            l.add(task);
            t.put("tasks", l);
            sendQuest(q, t);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.add_xp"), () -> {
            CompoundTag t = q.write();
            CompoundTag r = new CompoundTag();
            r.putString("id", randomId());
            r.putString("type", "xp");
            r.putInt("xp", 100);
            net.minecraft.nbt.ListTag l = t.getListOrEmpty("rewards");
            l.add(r);
            t.put("rewards", l);
            sendQuest(q, t);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.cycle_shape"), () -> {
            CompoundTag t = q.write();
            t.putString("shape", q.shape().next().id());
            sendQuest(q, t);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.duplicate"), () -> {
            CompoundTag t = q.write();
            t.putString("id", randomId());
            t.putDouble("x", q.x + 1);
            for (var task : t.getListOrEmpty("tasks")) if (task instanceof CompoundTag c) c.putString("id", randomId());
            for (var rew : t.getListOrEmpty("rewards")) if (rew instanceof CompoundTag c) c.putString("id", randomId());
            sendQuest(q, t);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.delete").withStyle(net.minecraft.ChatFormatting.RED), () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "quest_delete");
            op.putString("id", q.idString());
            edit(op);
            if (selected == q) selected = null;
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openMapMenu(double mx, double my) {
        if (chapter == null) return;
        double x = Math.round(qx(mx) * 2) / 2.0;
        double y = Math.round(qy(my) * 2) / 2.0;
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.new_quest"), () -> {
            CompoundTag t = new CompoundTag();
            t.putString("id", randomId());
            t.putString("title", "New Quest");
            t.putDouble("x", x);
            t.putDouble("y", y);
            net.minecraft.nbt.ListTag tasks = new net.minecraft.nbt.ListTag();
            CompoundTag task = new CompoundTag();
            task.putString("id", randomId());
            ItemStack held = held();
            if (held.isEmpty()) {
                task.putString("type", "checkmark");
            } else {
                task.putString("type", "item");
                task.put("item", ItemSpec.of(held.copyWithCount(1), reg()).write());
                if (held.getCount() > 1) task.putLong("count", held.getCount());
            }
            tasks.add(task);
            t.put("tasks", tasks);
            CompoundTag op = new CompoundTag();
            op.putString("op", "quest_set");
            op.putString("chapter", chapter.idString());
            op.put("quest", t);
            edit(op);
        }));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.chapter_settings"), () -> editChapterSnbt(chapter)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.reload"), () -> {
            CompoundTag op = new CompoundTag();
            op.putString("op", "reload");
            edit(op);
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void openChapterMenu(double mx, double my) {
        Chapter target = null;
        for (Zone z : lastZones) {
            if (z.contains(mx, my) && z.x() == 0) {
                z.action().run();
                target = chapter;
            }
        }
        if (target == null) return;
        Chapter c = target;
        List<MenuEntry> e = new ArrayList<>();
        e.add(new MenuEntry(Component.translatable("questsicons.edit.chapter_settings"), () -> editChapterSnbt(c)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_up"), () -> moveChapter(c, -1)));
        e.add(new MenuEntry(Component.translatable("questsicons.edit.move_down"), () -> moveChapter(c, 1)));
        if (!held().isEmpty()) {
            e.add(new MenuEntry(Component.translatable("questsicons.edit.icon_from_hand"), () -> {
                CompoundTag t = chapterTag(c);
                t.put("icon", ItemSpec.of(held().copyWithCount(1), reg()).write());
                sendChapter(t);
            }));
        }
        e.add(new MenuEntry(Component.translatable("questsicons.edit.delete_chapter").withStyle(net.minecraft.ChatFormatting.RED), () -> {
            if (!c.quests.isEmpty() && !minecraft.hasShiftDown()) {
                if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatable("questsicons.edit.delete_chapter_shift"));
                return;
            }
            CompoundTag op = new CompoundTag();
            op.putString("op", "chapter_delete");
            op.putString("id", c.idString());
            edit(op);
        }));
        menu = new ContextMenu((int) mx, (int) my, e);
    }

    private void moveChapter(Chapter c, int delta) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "chapter_move");
        op.putString("id", c.idString());
        op.putInt("delta", delta);
        edit(op);
    }

    private static CompoundTag chapterTag(Chapter c) {
        CompoundTag t = c.write();
        t.remove("quests");
        return t;
    }

    private void sendChapter(CompoundTag t) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "chapter_set");
        op.put("chapter", t);
        edit(op);
    }

    private void editQuestSnbt(Quest q) {
        minecraft.gui.setScreen(new SnbtEditScreen(this, Component.translatable("questsicons.edit.edit_quest", q.displayTitle()), q.write(), t -> {
            if (!t.contains("id")) t.putString("id", q.idString());
            sendQuest(q, t);
        }));
    }

    private void editChapterSnbt(Chapter c) {
        minecraft.gui.setScreen(new SnbtEditScreen(this, Component.translatable("questsicons.edit.edit_chapter", c.displayTitle()), chapterTag(c), t -> {
            t.putString("id", c.idString());
            sendChapter(t);
        }));
    }
}
