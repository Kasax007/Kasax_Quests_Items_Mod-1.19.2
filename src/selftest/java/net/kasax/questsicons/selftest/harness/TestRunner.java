package net.kasax.questsicons.selftest.harness;

import net.kasax.questsicons.selftest.Selftest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Starts the self-tests on their own thread once the title screen shows, when the game runs with -Dqi.selftest=true.
 * Forge 1.20.1 has no unit-test support in the build, so the converter tests (src/test) run here first, inside the game
 * where the compat mixins are applied. Exits the game afterwards: status 0 if every test passed, 1 otherwise.
 */
@Mod("questsicons_selftest")
public class TestRunner {
    static volatile DedicatedServer dedicated;
    private static boolean started;

    public TestRunner() {
        if (!Boolean.getBoolean("qi.selftest") || FMLEnvironment.dist != Dist.CLIENT) return;
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            if (e.getServer() instanceof DedicatedServer d) dedicated = d;
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            ClientGameTestContext.onTick();
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                Thread t = new Thread(TestRunner::runAll, "Test thread");
                t.setDaemon(true);
                t.start();
            }
        });
    }

    /** The JUnit tests of src/test (FtbImporterTest, QuestEditorTest). */
    private static boolean unitTests() {
        LauncherDiscoveryRequest request;
        try {
            request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(Class.forName("net.kasax.questsicons.importer.FtbImporterTest")),
                    selectClass(Class.forName("net.kasax.questsicons.server.QuestEditorTest"))).build();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("the converter tests are not in the game (questsicons mod, test source set)", e);
        }
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        summary.printFailuresTo(new PrintWriter(System.out), 30);
        System.out.println("[selftest] unit tests: " + summary.getTestsSucceededCount() + " passed, " + summary.getTestsFailedCount()
                + " failed, " + summary.getTestsSkippedCount() + " skipped");
        return summary.getTestsFailedCount() == 0 && summary.getTestsSucceededCount() > 0;
    }

    private static void runAll() {
        boolean ok;
        try {
            ok = unitTests();
        } catch (Throwable e) {
            ok = false;
            System.out.println("[selftest] unit tests could not run: " + e);
            e.printStackTrace(System.out);
        }
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
