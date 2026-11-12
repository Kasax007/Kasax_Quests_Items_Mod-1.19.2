package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.gui.screens.TitleScreen;

/** The client's connection to a {@link TestDedicatedServerContext}; closing it disconnects. */
public class TestServerConnection implements AutoCloseable {
    private final ClientGameTestContext context;

    TestServerConnection(ClientGameTestContext context) {
        this.context = context;
    }

    @Override
    public void close() {
        context.runOnClient(mc -> {
            mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        });
        context.waitFor(mc -> mc.level == null, 1200);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }
}
