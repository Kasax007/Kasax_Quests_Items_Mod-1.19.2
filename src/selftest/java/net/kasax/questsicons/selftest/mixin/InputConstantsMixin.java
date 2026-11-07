package net.kasax.questsicons.selftest.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.kasax.questsicons.selftest.harness.TestInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keys the test holds count as pressed (GLFW itself knows nothing of simulated key presses). */
@Mixin(InputConstants.class)
public class InputConstantsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void qi$heldByTest(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (TestInput.HELD.contains(key)) cir.setReturnValue(true);
    }
}
