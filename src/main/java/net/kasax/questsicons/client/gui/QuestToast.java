package net.kasax.questsicons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** "Quest completed" / "Chapter completed" popup in the style of advancement toasts. */
public class QuestToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private final ItemStack icon;
    private final Component title;
    private final Component header;
    private final boolean big;
    private Visibility visibility = Visibility.SHOW;

    public QuestToast(ItemStack icon, Component title, Component header, boolean big) {
        this.icon = icon;
        this.title = title;
        this.header = header;
        this.big = big;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        visibility = fullyVisibleForMs >= 5000.0 * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public @Nullable SoundEvent getSoundEvent() {
        return big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
        g.text(font, header, 30, 7, big ? 0xFFFF88FF : 0xFFFFFF55, false);
        String t = font.plainSubstrByWidth(title.getString(), 125);
        g.text(font, Component.literal(t).setStyle(title.getStyle()), 30, 18, 0xFFFFFFFF, false);
        g.fakeItem(icon, 8, 8);
    }
}
