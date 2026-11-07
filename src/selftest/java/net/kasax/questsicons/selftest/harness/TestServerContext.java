package net.kasax.questsicons.selftest.harness;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;
import java.util.function.Function;

/** Runs code and commands on a (singleplayer or dedicated) server, waiting for the result. */
public class TestServerContext {
    protected final MinecraftServer server;

    TestServerContext(MinecraftServer server) {
        this.server = server;
    }

    public void runCommand(String command) {
        runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command));
    }

    public void runOnServer(Consumer<MinecraftServer> action) {
        computeOnServer(s -> {
            action.accept(s);
            return null;
        });
    }

    public <T> T computeOnServer(Function<MinecraftServer, T> fn) {
        return server.submit(() -> fn.apply(server)).join();
    }
}
