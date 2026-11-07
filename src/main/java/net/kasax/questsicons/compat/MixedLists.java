package net.kasax.questsicons.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Reading back mixed lists (see {@link QiListTag}): a list of {@code {"": value}} compounds becomes the values again. */
public final class MixedLists {
    private MixedLists() {
    }

    /** Unwraps every wrapped list in this tree, in place; returns the tag for chaining. */
    public static <T extends Tag> T unwrap(T tag) {
        if (tag instanceof CompoundTag c) {
            for (String k : c.getAllKeys()) {
                Tag v = c.get(k);
                Tag u = unwrapValue(v);
                if (u != v) c.put(k, u);
            }
        } else if (tag instanceof ListTag l) {
            for (int i = 0; i < l.size(); i++) unwrap(l.get(i));
        }
        return tag;
    }

    private static Tag unwrapValue(Tag v) {
        if (!(v instanceof ListTag l)) {
            unwrap(v);
            return v;
        }
        boolean wrapped = false;
        for (Tag e : l) {
            if (e instanceof CompoundTag c && isWrapper(c)) {
                wrapped = true;
                break;
            }
        }
        if (!wrapped) {
            unwrap(l);
            return l;
        }
        ListTag out = QiListTag.mixed();
        for (Tag e : l) {
            Tag x = e instanceof CompoundTag c && isWrapper(c) ? c.get("") : e;
            out.add(unwrap(x));
        }
        return out;
    }

    static boolean isWrapper(CompoundTag c) {
        return c.size() == 1 && c.contains("");
    }
}
