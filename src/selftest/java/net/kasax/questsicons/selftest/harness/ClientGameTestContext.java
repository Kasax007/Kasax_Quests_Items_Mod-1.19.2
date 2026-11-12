package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** What a test does on the client: wait for ticks, run code on the render thread, screenshots, input, worlds. */
public class ClientGameTestContext {
    /** Like Fabric's: 10 seconds. */
    public static final int DEFAULT_TIMEOUT = 20 * 10;
    private static final Object TICK = new Object();
    private static long ticks;
    private final TestInput input = new TestInput(this);

    static void onTick() {
        synchronized (TICK) {
            ticks++;
            TICK.notifyAll();
        }
    }

    public void waitTick() {
        synchronized (TICK) {
            long now = ticks;
            while (ticks == now) {
                try {
                    TICK.wait(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void waitTicks(int n) {
        for (int i = 0; i < n; i++) waitTick();
    }

    public int waitFor(Predicate<Minecraft> condition) {
        return waitFor(condition, DEFAULT_TIMEOUT);
    }

    /** Waits until the condition holds (checked on the render thread once per tick); fails after {@code timeout} ticks. */
    public int waitFor(Predicate<Minecraft> condition, int timeout) {
        for (int i = 0; i <= timeout; i++) {
            if (computeOnClient(condition::test)) return i;
            waitTick();
        }
        throw new AssertionError("Timed out waiting for a condition (" + timeout + " ticks)");
    }

    public <T> T computeOnClient(Function<Minecraft, T> fn) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) return fn.apply(mc);
        return mc.submit(() -> fn.apply(mc)).join();
    }

    public void runOnClient(Consumer<Minecraft> action) {
        computeOnClient(mc -> {
            action.accept(mc);
            return null;
        });
    }

    public void setScreen(Supplier<Screen> screen) {
        runOnClient(mc -> mc.setScreen(screen.get()));
        waitTick();
    }

    /** Saves screenshots/{@code name}.png in the run directory. */
    public void takeScreenshot(String name) {
        waitTicks(2);
        runOnClient(mc -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, msg -> {
        }));
        System.out.println("[selftest] screenshot " + name);
    }

    public TestInput getInput() {
        return input;
    }

    public TestWorldBuilder worldBuilder() {
        return new TestWorldBuilder(this);
    }
}
