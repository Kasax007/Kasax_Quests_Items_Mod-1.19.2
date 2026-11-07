package net.kasax.questsicons.compat.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * The mouse click of Minecraft 1.21.9+ ({@code net.minecraft.client.input.MouseButtonEvent}) for 1.21.1, where screens
 * get x, y and button as separate arguments: the quest screens build one and keep the code of the newer versions.
 */
public record MouseButtonEvent(double x, double y, int button) {
    public boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    public boolean hasControlDown() {
        return Screen.hasControlDown();
    }
}
