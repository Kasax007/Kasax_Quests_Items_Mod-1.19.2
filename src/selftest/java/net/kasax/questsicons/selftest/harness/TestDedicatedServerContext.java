package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.dedicated.DedicatedServer;

/** A dedicated server started in this JVM; the client joins it over a real (localhost) network connection. */
public class TestDedicatedServerContext extends TestServerContext implements AutoCloseable {
    private final ClientGameTestContext context;
    private final DedicatedServer dedicated;

    TestDedicatedServerContext(ClientGameTestContext context, DedicatedServer server) {
        super(server);
        this.context = context;
        this.dedicated = server;
    }

    public TestDedicatedServerConnection connect() {
        String address = "localhost:" + dedicated.getPort();
        context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                new ServerData("localhost", address, ServerData.Type.OTHER), false, null));
        context.waitFor(mc -> mc.level != null && mc.player != null && mc.screen == null, 2400);
        context.waitTicks(20);
        return new TestDedicatedServerConnection(context);
    }

    @Override
    public void close() {
        dedicated.halt(false);
        context.waitFor(mc -> !dedicated.getRunningThread().isAlive(), 2400);
        TestRunner.dedicated = null;
    }
}
