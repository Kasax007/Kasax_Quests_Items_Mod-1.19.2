package net.kasax.questsicons.compat.mixin;

import net.kasax.questsicons.compat.QiCompoundTag;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;

/** Makes {@link CompoundTag} implement {@link QiCompoundTag} (see the compat package). */
@Mixin(CompoundTag.class)
public abstract class CompoundTagMixin implements QiCompoundTag {
}
