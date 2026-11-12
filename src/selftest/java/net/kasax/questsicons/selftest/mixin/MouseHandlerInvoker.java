package net.kasax.questsicons.selftest.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The mouse handler's GLFW callbacks, for real clicks in the self-test. */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onMove")
    void qi$onMove(long handle, double x, double y);

    @Invoker("onButton")
    void qi$onButton(long handle, MouseButtonInfo button, int action);

    @Invoker("onScroll")
    void qi$onScroll(long handle, double x, double y);
}
