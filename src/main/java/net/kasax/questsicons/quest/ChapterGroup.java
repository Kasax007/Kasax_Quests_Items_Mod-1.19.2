package net.kasax.questsicons.quest;

import net.minecraft.nbt.CompoundTag;

/** A collapsible heading in the chapter sidebar. Chapters refer to it by id. */
public final class ChapterGroup extends QuestObject {
    public static ChapterGroup read(CompoundTag tag) {
        ChapterGroup g = new ChapterGroup();
        g.readBase(tag);
        return g;
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        writeBase(t);
        return t;
    }
}
