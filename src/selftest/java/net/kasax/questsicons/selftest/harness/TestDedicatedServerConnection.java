package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.gui.screens.TitleScreen;

/** The client's connection to a {@link TestDedicatedServerContext}; closing it disconnects. */
public class TestDedicatedServerConnection implements AutoCloseable {
    private final ClientGameTestContext context;

    TestDedicatedServerConnection(ClientGameTestContext context) {
        this.context = context;
    }

    @Override
    public void close() {
        context.runOnClient(mc -> {
            mc.disconnect(new TitleScreen(), false);
        });
        context.waitFor(mc -> mc.level == null, 1200);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }
}
