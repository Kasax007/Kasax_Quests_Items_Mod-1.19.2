package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Real input: every call goes through Minecraft's own mouse and keyboard handlers (the path the window's events take),
 * so a test clicks exactly like a player. Minecraft 26.3 uses SDL numbering (left mouse 1, Escape 41); always use the
 * InputConstants constants.
 */
public class TestInput {
    /** Keys a test holds down; InputConstantsMixin reports them as pressed (Screen.hasShiftDown() and friends). */
    public static final Set<Integer> HELD = ConcurrentHashMap.newKeySet();
    private final ClientGameTestContext context;

    TestInput(ClientGameTestContext context) {
        this.context = context;
    }

    private static long window(Minecraft mc) {
        return mc.getWindow().handle();
    }

    private static int mods() {
        return HELD.contains(InputConstants.KEY_LSHIFT) ? InputConstants.MOD_SHIFT : 0;
    }

    /**
     * The SDL keycode of a key (26.3's KeyEvent carries both: the scancode for key bindings, the keycode for text
     * editing, e.g. EditBox's Backspace and End). Printable keys are their character, the rest the scancode with
     * SDLK_SCANCODE_MASK, like SDL does for a US layout.
     */
    private static int keycode(int scancode) {
        if (scancode >= 4 && scancode <= 29) return 'a' + (scancode - 4);
        if (scancode >= 30 && scancode <= 38) return '1' + (scancode - 30);
        return switch (scancode) {
            case 39 -> '0';
            case 40 -> 13;    // Return
            case 41 -> 27;    // Escape
            case 42 -> 8;     // Backspace
            case 43 -> 9;     // Tab
            case 44 -> 32;    // Space
            case 76 -> 127;   // Delete
            default -> scancode | (1 << 30);
        };
    }

    public void resizeWindow(int width, int height) {
        context.runOnClient(mc -> mc.getWindow().setWindowed(width, height));
        context.waitTicks(5);
    }

    public void setCursorPos(double x, double y) {
        context.runOnClient(mc -> {
            mc.mouseHandler.onMove(window(mc), x, y, 0, 0);
            mc.mouseHandler.onMove(window(mc), x, y, 0, 0);   // the first move after a while only sets the position
        });
        context.waitTick();
    }

    public void holdMouse(int button) {
        context.runOnClient(mc -> mc.mouseHandler.onButton(window(mc), new MouseButtonInfo(button, mods()), InputConstants.PRESS));
        context.waitTick();
    }

    public void releaseMouse(int button) {
        context.runOnClient(mc -> mc.mouseHandler.onButton(window(mc), new MouseButtonInfo(button, mods()), InputConstants.RELEASE));
        context.waitTick();
    }

    public void pressMouse(int button) {
        holdMouse(button);
        releaseMouse(button);
    }

    public void scroll(double amount) {
        context.runOnClient(mc -> mc.mouseHandler.onScroll(window(mc), 0, amount));
        context.waitTick();
    }

    public void holdKey(int key) {
        HELD.add(key);
        context.runOnClient(mc -> mc.keyboardHandler.keyPress(window(mc), InputConstants.PRESS, new KeyEvent(key, keycode(key), mods())));
        context.waitTick();
    }

    public void releaseKey(int key) {
        HELD.remove(key);
        context.runOnClient(mc -> mc.keyboardHandler.keyPress(window(mc), InputConstants.RELEASE, new KeyEvent(key, keycode(key), mods())));
        context.waitTick();
    }

    public void pressKey(int key) {
        holdKey(key);
        releaseKey(key);
    }

    /** Presses whatever key or mouse button the key mapping is bound to (e.g. {@code o -> o.keyUse}). */
    public void pressKey(Function<Options, KeyMapping> mapping) {
        // The game is not stepped like in Fabric's harness: give it two ticks to pick the block under the cursor first.
        context.waitTicks(2);
        InputConstants.Key key = context.computeOnClient(mc -> mapping.apply(mc.options).getKey());
        if (key.getType() == InputConstants.Type.MOUSE) pressMouse(key.getValue());
        else pressKey(key.getValue());
    }

    public void holdShift() {
        holdKey(InputConstants.KEY_LSHIFT);
    }

    public void releaseShift() {
        releaseKey(InputConstants.KEY_LSHIFT);
    }

    public void typeChars(String text) {
        context.runOnClient(mc -> text.codePoints().forEach(cp -> mc.keyboardHandler.charTyped(window(mc), new CharacterEvent(cp))));
        context.waitTick();
    }

    public void lookAt(BlockPos pos) {
        context.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos)));
        context.waitTick();
    }
}
