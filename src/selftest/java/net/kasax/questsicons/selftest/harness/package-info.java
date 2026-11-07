/**
 * A small client game-test harness for Minecraft 1.21.1, which has no Fabric client game-test API yet (it came with
 * 1.21.4). It offers the part of that API the self-test uses, with the same names, so {@code Selftest} stays the same
 * as on 26.x: real mouse clicks and keys through Minecraft's own input handlers, a singleplayer world created through
 * the Create World screen, and a dedicated server started in the same JVM ({@code net.minecraft.server.Main}) that
 * the client joins over a real network connection.
 *
 * <p>Unlike Fabric's harness the game is not stepped tick by tick: the test thread waits for client ticks.
 */
package net.kasax.questsicons.selftest.harness;
