package net.kasax.questsicons.compat;

import net.minecraft.nbt.StringTag;

/** {@code StringTag.value()} of 1.21.5+ (see package-info). */
public interface QiStringTag {
    default String value() {
        return ((StringTag) (Object) this).getAsString();
    }
}
