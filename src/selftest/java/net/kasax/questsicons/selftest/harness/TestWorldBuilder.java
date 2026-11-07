package net.kasax.questsicons.selftest.harness;

import net.kasax.questsicons.selftest.mixin.CreateWorldScreenInvoker;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.util.function.Consumer;

/** Creates a singleplayer world through the Create World screen. */
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

    /**
     * False on NeoForge: its server Main loads every mod a second time in the same JVM, which mod loading refuses. The
     * dedicated server is covered by the production test instead (a real NeoForge server and client with the release jar).
     */
    public static final boolean DEDICATED_SERVER_IN_JVM = false;

    public TestDedicatedServerContext createServer() {
        throw new UnsupportedOperationException("NeoForge cannot start a second, dedicated server in the client's JVM");
    }
}
