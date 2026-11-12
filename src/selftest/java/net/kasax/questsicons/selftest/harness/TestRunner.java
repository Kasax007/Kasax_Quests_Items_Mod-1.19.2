package net.kasax.questsicons.selftest.harness;

import net.kasax.questsicons.selftest.Selftest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;

/**
 * Starts the self-tests on their own thread once the title screen shows, when the
 * game runs with -Dqi.selftest=true. Exits the game afterwards: status 0 if every test passed, 1 otherwise.
 */
@Mod(value = "questsicons_selftest", dist = Dist.CLIENT)
public class TestRunner {
    static volatile DedicatedServer dedicated;
    private static boolean started;

    public TestRunner() {
        if (!Boolean.getBoolean("qi.selftest")) return;
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            if (e.getServer() instanceof DedicatedServer d) dedicated = d;
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            ClientGameTestContext.onTick();
            if (!started && mc.gui.screen() instanceof TitleScreen) {
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
        for (FabricClientGameTest test : List.<FabricClientGameTest>of(new Selftest())) {
            try {
                test.runTest(ctx);
            } catch (Throwable e) {
                ok = false;
                System.out.println("[selftest] " + test.getClass().getSimpleName() + " failed: " + e);
                e.printStackTrace(System.out);
            }
        }
        System.out.println("[selftest] finished: " + (ok ? "PASSED" : "FAILED"));
        if (ok) {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        } else {
            Runtime.getRuntime().halt(1);
        }
    }
}
