package net.kasax.questsicons.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;

import java.util.Set;

/** The 1.21.5+ accessors of {@link CompoundTag} (see package-info): a missing or wrongly typed entry gives the default. */
public interface QiCompoundTag {
    private CompoundTag self() {
        return (CompoundTag) (Object) this;
    }

    default Set<String> keySet() {
        return self().getAllKeys();
    }

    default String getStringOr(String key, String def) {
        return self().get(key) instanceof StringTag s ? s.getAsString() : def;
    }

    default int getIntOr(String key, int def) {
        return self().get(key) instanceof NumericTag n ? n.getAsInt() : def;
    }

    default long getLongOr(String key, long def) {
        return self().get(key) instanceof NumericTag n ? n.getAsLong() : def;
    }

    default short getShortOr(String key, short def) {
        return self().get(key) instanceof NumericTag n ? n.getAsShort() : def;
    }

    default byte getByteOr(String key, byte def) {
        return self().get(key) instanceof NumericTag n ? n.getAsByte() : def;
    }

    default float getFloatOr(String key, float def) {
        return self().get(key) instanceof NumericTag n ? n.getAsFloat() : def;
    }

    default double getDoubleOr(String key, double def) {
        return self().get(key) instanceof NumericTag n ? n.getAsDouble() : def;
    }

    default boolean getBooleanOr(String key, boolean def) {
        return self().get(key) instanceof NumericTag n ? n.getAsByte() != 0 : def;
    }

    default CompoundTag getCompoundOrEmpty(String key) {
        return self().get(key) instanceof CompoundTag c ? c : new CompoundTag();
    }

    default ListTag getListOrEmpty(String key) {
        return self().get(key) instanceof ListTag l ? l : new ListTag();
    }
}
