package net.kasax.questsicons.selftest.harness;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Starts the self-tests (entrypoint "questsicons-selftest") on their own thread once the title screen shows, when the
 * game runs with -Dqi.selftest=true. Exits the game afterwards: status 0 if every test passed, 1 otherwise.
 */
public class TestRunner implements ClientModInitializer {
    static volatile DedicatedServer dedicated;
    private static boolean started;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("qi.selftest")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            if (s instanceof DedicatedServer d) dedicated = d;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientGameTestContext.onTick();
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                Thread t = new Thread(TestRunner::runAll, "Test thread");
                t.setDaemon(true);
                t.start();
            }
        });
    }

    private static void runAll() {
        boolean ok = true;
        ClientGameTestContext ctx = new ClientGameTestContext();
        for (FabricClientGameTest test : FabricLoader.getInstance().getEntrypoints("questsicons-selftest", FabricClientGameTest.class)) {
            try {
                test.runTest(ctx);
            } catch (Throwable e) {
                ok = false;
                System.out.println("[selftest] " + test.getClass().getSimpleName() + " failed: " + e);
                e.printStackTrace(System.out);
            }
        }
        try {
            TestDedicatedServerContext.shutDown();
        } catch (Throwable e) {
            System.out.println("[selftest] stopping the test server failed: " + e);
        }
        System.out.println("[selftest] finished: " + (ok ? "PASSED" : "FAILED"));
        if (ok) {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        } else {
            Runtime.getRuntime().halt(1);
        }
    }
}
