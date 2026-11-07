package net.kasax.questsicons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** "Quest completed" / "Chapter completed" popup in the style of advancement toasts. */
public class QuestToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private final ItemStack icon;
    private final Component title;
    private final Component header;
    private final boolean big;
    private boolean playedSound;

    public QuestToast(ItemStack icon, Component title, Component header, boolean big) {
        this.icon = icon;
        this.title = title;
        this.header = header;
        this.big = big;
    }

    private @Nullable SoundEvent sound() {
        return big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long fullyVisibleForMs) {
        if (!playedSound && fullyVisibleForMs > 0L) {
            playedSound = true;
            SoundEvent sound = sound();
            if (sound != null) toasts.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 1.0f));
        }
        Font font = toasts.getMinecraft().font;
        g.blitSprite(BACKGROUND, 0, 0, width(), height());
        g.drawString(font, header, 30, 7, big ? 0xFFFF88FF : 0xFFFFFF55, false);
        g.drawString(font, QuestBookScreen.clip(font, title, 125), 30, 18, 0xFFFFFFFF, false);
        g.renderFakeItem(icon, 8, 8);
        return fullyVisibleForMs >= 5000.0 * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
