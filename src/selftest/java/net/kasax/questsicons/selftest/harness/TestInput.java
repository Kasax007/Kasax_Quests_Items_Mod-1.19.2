package net.kasax.questsicons.selftest.harness;

import net.kasax.questsicons.selftest.mixin.KeyboardHandlerInvoker;
import net.kasax.questsicons.selftest.mixin.MouseHandlerInvoker;

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
 * Real input: every call goes through Minecraft's own mouse and keyboard handlers (the path GLFW events take), so a
 * test clicks exactly like a player. Minecraft 26.2 uses GLFW numbering (left mouse 0, Escape 256); always use the
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

    public void resizeWindow(int width, int height) {
        context.runOnClient(mc -> mc.getWindow().setWindowed(width, height));
        context.waitTicks(5);
    }

    public void setCursorPos(double x, double y) {
        context.runOnClient(mc -> {
            ((MouseHandlerInvoker) mc.mouseHandler).qi$onMove(window(mc), x, y);
            ((MouseHandlerInvoker) mc.mouseHandler).qi$onMove(window(mc), x, y);   // the first move after a while only sets the position
        });
        context.waitTick();
    }

    public void holdMouse(int button) {
        context.runOnClient(mc -> ((MouseHandlerInvoker) mc.mouseHandler).qi$onButton(window(mc), new MouseButtonInfo(button, mods()), InputConstants.PRESS));
        context.waitTick();
    }

    public void releaseMouse(int button) {
        context.runOnClient(mc -> ((MouseHandlerInvoker) mc.mouseHandler).qi$onButton(window(mc), new MouseButtonInfo(button, mods()), InputConstants.RELEASE));
        context.waitTick();
    }

    public void pressMouse(int button) {
        holdMouse(button);
        releaseMouse(button);
    }

    public void scroll(double amount) {
        context.runOnClient(mc -> ((MouseHandlerInvoker) mc.mouseHandler).qi$onScroll(window(mc), 0, amount));
        context.waitTick();
    }

    public void holdKey(int key) {
        HELD.add(key);
        context.runOnClient(mc -> ((KeyboardHandlerInvoker) mc.keyboardHandler).qi$keyPress(window(mc), InputConstants.PRESS, new KeyEvent(key, 0, mods())));
        context.waitTick();
    }

    public void releaseKey(int key) {
        HELD.remove(key);
        context.runOnClient(mc -> ((KeyboardHandlerInvoker) mc.keyboardHandler).qi$keyPress(window(mc), InputConstants.RELEASE, new KeyEvent(key, 0, mods())));
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
        context.runOnClient(mc -> text.codePoints().forEach(cp -> ((KeyboardHandlerInvoker) mc.keyboardHandler).qi$charTyped(window(mc), new CharacterEvent(cp))));
        context.waitTick();
    }

    public void lookAt(BlockPos pos) {
        context.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos)));
        context.waitTick();
    }
}
