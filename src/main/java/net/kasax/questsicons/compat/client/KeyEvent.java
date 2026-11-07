package net.kasax.questsicons.compat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;

/** The key press of Minecraft 1.21.9+ ({@code net.minecraft.client.input.KeyEvent}) for 1.21.1 (see {@link MouseButtonEvent}). */
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean isEscape() {
        return key == InputConstants.KEY_ESCAPE;
    }

    public boolean isCopy() {
        return Screen.isCopy(key);
    }

    public boolean isPaste() {
        return Screen.isPaste(key);
    }

    public boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    public boolean hasControlDown() {
        return Screen.hasControlDown();
    }
}
