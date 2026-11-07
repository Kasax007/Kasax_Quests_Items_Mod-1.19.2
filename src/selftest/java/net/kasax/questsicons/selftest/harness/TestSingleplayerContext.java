package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** A singleplayer world; closing it saves and leaves the world. */
public class TestSingleplayerContext implements AutoCloseable {
    private final ClientGameTestContext context;
    private final TestServerContext server;

    TestSingleplayerContext(ClientGameTestContext context, MinecraftServer server) {
        this.context = context;
        this.server = new TestServerContext(server);
    }

    public TestServerContext getServer() {
        return server;
    }

    @Override
    public void close() {
        context.runOnClient(mc -> {
            if (mc.level != null) mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        });
        context.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 1200);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }
}
