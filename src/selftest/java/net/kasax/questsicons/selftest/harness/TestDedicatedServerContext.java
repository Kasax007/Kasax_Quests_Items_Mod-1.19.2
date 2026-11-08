package net.kasax.questsicons.selftest.harness;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * A dedicated server started in this JVM; the client joins it over a real (localhost) network connection.
 *
 * <p>Minecraft 1.20.1 with Fabric API 0.92: Fabric's registry sync remaps the item registry the client shares with an
 * in-JVM server, and a second server in the same JVM then cannot encode the mod's items. So the first server and its
 * connection stay up for every test that asks for one ({@code close} is a no-op) until {@link #shutDown} at the end.
 */
public class TestDedicatedServerContext extends TestServerContext implements AutoCloseable {
    static TestDedicatedServerContext shared;
    private final ClientGameTestContext context;
    private final DedicatedServer dedicated;
    private TestDedicatedServerConnection connection;

    TestDedicatedServerContext(ClientGameTestContext context, DedicatedServer server) {
        super(server);
        this.context = context;
        this.dedicated = server;
    }

    public TestDedicatedServerConnection connect() {
        if (connection != null && context.computeOnClient(mc -> mc.level != null)) return connection;
        String address = "localhost:" + dedicated.getPort();
        context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                new ServerData("localhost", address, false), false));
        context.waitFor(mc -> mc.level != null && mc.player != null && mc.screen == null, 2400);
        context.waitTicks(20);
        connection = new TestDedicatedServerConnection(context);
        return connection;
    }

    @Override
    public void close() {
    }

    boolean isRunning() {
        return dedicated.isRunning();
    }

    /** Disconnects and stops the shared server (TestRunner, after the tests). */
    static void shutDown() {
        TestDedicatedServerContext c = shared;
        if (c == null) return;
        shared = null;
        if (c.connection != null) c.connection.disconnect();
        c.dedicated.halt(false);
        c.context.waitFor(mc -> !c.dedicated.getRunningThread().isAlive(), 2400);
        TestRunner.dedicated = null;
    }
}
