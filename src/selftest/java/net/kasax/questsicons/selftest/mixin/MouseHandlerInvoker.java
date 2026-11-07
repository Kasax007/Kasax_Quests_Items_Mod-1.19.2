package net.kasax.questsicons.selftest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onPress")
    void qi$onPress(long window, int button, int action, int mods);

    @Invoker("onMove")
    void qi$onMove(long window, double x, double y);

    @Invoker("onScroll")
    void qi$onScroll(long window, double dx, double dy);
}
