package net.kasax.questsicons.compat.mixin;

import net.kasax.questsicons.compat.QiNumericTag;
import net.minecraft.nbt.NumericTag;
import org.spongepowered.asm.mixin.Mixin;

/** Makes {@link NumericTag} implement {@link QiNumericTag} (see the compat package). */
@Mixin(NumericTag.class)
public abstract class NumericTagMixin implements QiNumericTag {
}
