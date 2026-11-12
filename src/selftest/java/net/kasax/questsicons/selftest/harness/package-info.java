/**
 * A small client game-test harness for NeoForge, which has no client game-test API. It offers the part of Fabric's API
 * the self-test uses, with the same names, so {@code Selftest} stays the same as on Fabric: real mouse clicks and keys
 * through Minecraft's own input handlers and a singleplayer world created through the Create World screen. NeoForge
 * cannot start a dedicated server in the client's JVM; the dedicated-server parts run against a real server instead
 * ({@code -PselftestServer=host:port}, see {@code Selftest.externalServer}).
 *
 * <p>Unlike Fabric's harness the game is not stepped tick by tick: the test thread waits for client ticks.
 */
package net.kasax.questsicons.selftest.harness;
