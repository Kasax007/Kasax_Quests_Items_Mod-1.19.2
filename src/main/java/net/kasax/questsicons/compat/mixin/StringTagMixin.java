package net.kasax.questsicons.compat.mixin;

import net.kasax.questsicons.compat.QiStringTag;
import net.minecraft.nbt.StringTag;
import org.spongepowered.asm.mixin.Mixin;

/** Makes {@link StringTag} implement {@link QiStringTag} (see the compat package). */
@Mixin(StringTag.class)
public abstract class StringTagMixin implements QiStringTag {
}
