package net.kasax.questsicons.compat;

import net.minecraft.nbt.NumericTag;

/** The number accessors of 1.21.5+ {@code NumericTag} (see package-info). */
public interface QiNumericTag {
    private NumericTag self() {
        return (NumericTag) (Object) this;
    }

    default int intValue() {
        return self().getAsInt();
    }

    default long longValue() {
        return self().getAsLong();
    }

    default short shortValue() {
        return self().getAsShort();
    }

    default byte byteValue() {
        return self().getAsByte();
    }

    default float floatValue() {
        return self().getAsFloat();
    }

    default double doubleValue() {
        return self().getAsDouble();
    }

    default Number box() {
        return self().getAsNumber();
    }
}
