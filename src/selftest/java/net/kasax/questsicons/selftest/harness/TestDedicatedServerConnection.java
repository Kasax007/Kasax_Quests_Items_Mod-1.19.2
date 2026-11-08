package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.gui.screens.TitleScreen;

/** The client's connection to a {@link TestDedicatedServerContext}; closing it disconnects. */
public class TestDedicatedServerConnection implements AutoCloseable {
    private final ClientGameTestContext context;

    TestDedicatedServerConnection(ClientGameTestContext context) {
        this.context = context;
    }

    /** The connection stays up for the next test (see TestDedicatedServerContext). */
    @Override
    public void close() {
    }

    void disconnect() {
        context.runOnClient(mc -> {
            if (mc.level != null) mc.level.disconnect();
            mc.clearLevel(new TitleScreen());
        });
        context.waitFor(mc -> mc.level == null, 1200);
        context.waitTicks(2);
        context.setScreen(TitleScreen::new);
    }
}
