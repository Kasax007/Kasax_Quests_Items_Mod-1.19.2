package net.kasax.questsicons.selftest.mixin;

import net.minecraft.Util;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The dedicated server of the self-test shares the JVM with the client: stopping it must not stop Minecraft's executors. */
@Mixin(DedicatedServer.class)
public class DedicatedServerMixin {
    @Redirect(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;shutdownExecutors()V"))
    private void qi$keepExecutors() {
    }
}
