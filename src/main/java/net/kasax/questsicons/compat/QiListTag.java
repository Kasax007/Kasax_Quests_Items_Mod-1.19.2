package net.kasax.questsicons.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;

/**
 * The 1.21.5+ index accessors of {@link ListTag} (see package-info), and lists that may mix element types.
 *
 * <p>Since 1.21.5 a list may hold, say, an item id (a string) next to an item with data (a compound); quest files use
 * that for "any of" items and choice rewards. A 1.21.1 list refuses a second element type, so the quest code creates its
 * lists with {@link #mixed()}. Such a list is written to the network and to disk the 1.21.5+ way (every element of a
 * mixed list wrapped as {@code {"": value}}, see {@code ListTagMixin}) and {@link MixedLists#unwrap} reads it back.
 */
public interface QiListTag {
    /** A new list that accepts elements of different types (see above). */
    static ListTag mixed() {
        ListTag l = new ListTag();
        ((QiListTag) l).questsicons$allowMixed();
        return l;
    }

    /** Implemented by ListTagMixin. */
    default void questsicons$allowMixed() {
        throw new IllegalStateException("ListTagMixin not applied");
    }

    private ListTag self() {
        return (ListTag) (Object) this;
    }

    default String getStringOr(int i, String def) {
        return i >= 0 && i < self().size() && self().get(i) instanceof StringTag s ? s.getAsString() : def;
    }

    default CompoundTag getCompoundOrEmpty(int i) {
        return i >= 0 && i < self().size() && self().get(i) instanceof CompoundTag c ? c : new CompoundTag();
    }

    default ListTag getListOrEmpty(int i) {
        return i >= 0 && i < self().size() && self().get(i) instanceof ListTag l ? l : new ListTag();
    }

    default int getIntOr(int i, int def) {
        return i >= 0 && i < self().size() && self().get(i) instanceof NumericTag n ? n.getAsInt() : def;
    }

    default double getDoubleOr(int i, double def) {
        return i >= 0 && i < self().size() && self().get(i) instanceof NumericTag n ? n.getAsDouble() : def;
    }

    default float getFloatOr(int i, float def) {
        return i >= 0 && i < self().size() && self().get(i) instanceof NumericTag n ? n.getAsFloat() : def;
    }
}
