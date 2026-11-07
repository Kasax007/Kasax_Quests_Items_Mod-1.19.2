package net.kasax.questsicons.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the editor's screens (forms and pickers): the quest book's look, buttons drawn as click zones (the zones of
 * the last frame answer clicks, so what is drawn is what is clickable) and test hooks to find a button by its text.
 */
public abstract class QiScreen extends Screen {
    protected static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    protected static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    protected record Zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip, @Nullable String label) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    protected final List<Zone> zones = new ArrayList<>();
    private final List<Zone> lastZones = new ArrayList<>();
    protected final @Nullable Screen parent;
    /** Zones outside this band (a scrolled list) are not added. */
    protected int clipTop = Integer.MIN_VALUE;
    protected int clipBottom = Integer.MAX_VALUE;
    private long openedAt;

    protected QiScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, QuestBookScreen.BG_TOP, QuestBookScreen.BG_BOTTOM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        lastZones.clear();
        lastZones.addAll(zones);
        zones.clear();
        draw(g, mouseX, mouseY, a);
        super.extractRenderState(g, mouseX, mouseY, a);
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (z.tooltip() != null && z.contains(mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, z.tooltip(), mouseX, mouseY);
                break;
            }
        }
    }

    protected abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

    protected void zone(int x, int y, int w, int h, Runnable action, @Nullable Component tooltip, @Nullable String label) {
        int top = Math.max(y, clipTop);
        int bottom = Math.min(y + h, clipBottom);
        if (bottom > top) zones.add(new Zone(x, top, w, bottom - top, action, tooltip, label));
    }

    protected boolean hovered(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && my >= y && mx < x + w && my < y + h && my >= clipTop && my < clipBottom;
    }

    /** A button in the quest book's style; returns its width. */
    protected int button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, int mx, int my, Runnable action) {
        return button(g, x, y, w, h, label, mx, my, action, true);
    }

    protected int button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, int mx, int my, Runnable action, boolean enabled) {
        boolean hov = enabled && hovered(x, y, w, h, mx, my);
        QuestBookScreen.frame(g, x, y, w, h, hov ? 0xFF3A2358 : 0xFF261839, !enabled ? 0xFF3A2C4C : hov ? QuestBookScreen.ACCENT : 0xFF5B3A86);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, !enabled ? QuestBookScreen.MUTED : hov ? 0xFFFFFFFF : QuestBookScreen.TEXT);
        if (enabled) zone(x, y, w, h, () -> {
            click();
            action.run();
        }, null, label.getString());
        return w;
    }

    protected int textButton(GuiGraphicsExtractor g, int x, int y, Component label, int mx, int my, Runnable action) {
        int w = font.width(label) + 12;
        return button(g, x, y, w, 16, label, mx, my, action);
    }

    protected void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (System.currentTimeMillis() - openedAt < 120) return true;
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == LEFT) {
            for (int i = lastZones.size() - 1; i >= 0; i--) {
                Zone z = lastZones.get(i);
                if (z.contains(event.x(), event.y())) {
                    z.action().run();
                    return true;
                }
            }
        }
        return clicked(event.x(), event.y(), event.button());
    }

    /** A click that hit no zone and no widget. */
    protected boolean clicked(double mx, double my, int button) {
        return false;
    }

    /** For tests: the GUI position of the centre of the first button (or picker cell) with this text or tooltip, or null. */
    public float[] zonePos(String text) {
        for (int i = 0; i < lastZones.size(); i++) {
            Zone z = lastZones.get(i);
            if (text.equals(z.label()) || z.tooltip() != null && text.equals(z.tooltip().getString())) {
                return new float[]{z.x() + z.w() / 2f, z.y() + z.h() / 2f};
            }
        }
        return null;
    }

    public void back() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        back();
    }
}
