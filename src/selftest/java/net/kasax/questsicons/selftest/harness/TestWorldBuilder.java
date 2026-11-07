package net.kasax.questsicons.selftest.harness;

import net.kasax.questsicons.selftest.mixin.CreateWorldScreenInvoker;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Creates a singleplayer world through the Create World screen, or starts a dedicated server in this JVM. */
public class TestWorldBuilder {
    private final ClientGameTestContext context;
    private Consumer<WorldCreationUiState> settings = s -> {
    };

    TestWorldBuilder(ClientGameTestContext context) {
        this.context = context;
    }

    public TestWorldBuilder adjustSettings(Consumer<WorldCreationUiState> settings) {
        this.settings = this.settings.andThen(settings);
        return this;
    }

    public TestSingleplayerContext create() {
        context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, new TitleScreen()));
        context.waitFor(mc -> mc.screen instanceof CreateWorldScreen, 1200);
        context.runOnClient(mc -> {
            CreateWorldScreen screen = (CreateWorldScreen) mc.screen;
            screen.getUiState().setName("Selftest");
            settings.accept(screen.getUiState());
            ((CreateWorldScreenInvoker) screen).qi$onCreate();
        });
        context.waitFor(mc -> mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && mc.screen == null, 2400);
        context.waitTicks(5);
        return new TestSingleplayerContext(context, context.computeOnClient(mc -> mc.getSingleplayerServer()));
    }

    /** Starts {@code net.minecraft.server.Main} with "nogui" in the run directory (server.properties: offline, flat). */
    public TestDedicatedServerContext createServer() {
        try {
            Files.writeString(Path.of("server.properties"), "online-mode=false\nspawn-protection=0\nmax-players=2\nlevel-type=minecraft\\:flat\n"
                    + "server-port=" + (25600 + (int) (Math.random() * 300)) + "\nview-distance=4\nsimulation-distance=4\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        TestRunner.dedicated = null;
        Thread t = new Thread(() -> net.minecraft.server.Main.main(new String[]{"nogui"}), "Dedicated server main");
        t.setDaemon(true);
        t.start();
        context.waitFor(mc -> TestRunner.dedicated != null && TestRunner.dedicated.isReady(), 6000);
        return new TestDedicatedServerContext(context, TestRunner.dedicated);
    }
}
