package net.kasax.questsicons.selftest.harness;

/** A client self-test; {@link TestRunner} calls it once the title screen is up. */
public interface FabricClientGameTest {
    void runTest(ClientGameTestContext context);
}
