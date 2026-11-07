/**
 * Port layer for Minecraft 1.21.1: the quest code is written against the NBT accessors of 1.21.5+ / 26.x
 * ({@code tag.getStringOr("title", "")}, {@code stringTag.value()}, ...). Minecraft 1.21.1 has older accessors, so the
 * interfaces here add the newer names as default methods. ModDevGradle's interface injection
 * (META-INF/interfaces.json) makes them visible to the compiler, the mixins in
 * {@code compat.mixin} make the Minecraft classes implement them at run time. This keeps the quest code identical to
 * the 26.x branches; a later port only has to carry this package along.
 */
package net.kasax.questsicons.compat;
