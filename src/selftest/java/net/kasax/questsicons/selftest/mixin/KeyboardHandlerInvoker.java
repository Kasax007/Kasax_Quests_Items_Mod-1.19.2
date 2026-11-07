package net.kasax.questsicons.selftest.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
    @Invoker("charTyped")
    void qi$charTyped(long window, int codePoint, int mods);
}
