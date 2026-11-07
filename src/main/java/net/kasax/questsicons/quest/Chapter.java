package net.kasax.questsicons.quest;

import net.kasax.questsicons.compat.QiListTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** One page of the quest book: a map of quests (file {@code chapters/<filename>.snbt}). */
public final class Chapter extends QuestObject {
    public QuestBook book;
    public String filename = "";
    public final List<String> subtitle = new ArrayList<>();
    public String group = "";
    public int orderIndex;
    public String defaultQuestShape = "";
    /** The size new quests of this chapter get in the editor (1 = normal). */
    public double defaultQuestSize = 1.0;
    /** Hidden in the sidebar until one of its quests can be started. */
    public boolean hideUntilStartable;
    /** Never shown to players, only in edit mode (FTB Quests' "always invisible" chapters). */
    public boolean alwaysInvisible;
    public final List<Quest> quests = new ArrayList<>();
    public final List<ChapterImage> images = new ArrayList<>();

    public void read(CompoundTag tag) {
        readBase(tag);
        filename = tag.getStringOr("filename", filename);
        subtitle.clear();
        Tag s = tag.get("subtitle");
        if (s instanceof ListTag l) {
            for (Tag t : l) subtitle.add(t instanceof StringTag st ? st.value() : t.toString());
        } else if (s instanceof StringTag st) {
            subtitle.add(st.value());
        }
        group = tag.getStringOr("group", "");
        orderIndex = tag.getIntOr("order_index", 0);
        defaultQuestShape = tag.getStringOr("default_quest_shape", "");
        defaultQuestSize = tag.getDoubleOr("default_quest_size", 1.0);
        hideUntilStartable = tag.getBooleanOr("hide_until_startable", false);
        alwaysInvisible = tag.getBooleanOr("always_invisible", false);
        quests.clear();
        for (Tag t : tag.getListOrEmpty("quests")) {
            if (!(t instanceof CompoundTag c)) continue;
            Quest q = new Quest();
            q.chapter = this;
            q.read(c);
            quests.add(q);
        }
        images.clear();
        for (Tag t : tag.getListOrEmpty("images")) {
            if (t instanceof CompoundTag c) images.add(ChapterImage.read(c));
        }
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        writeBase(tag);
        tag.putString("filename", filename);
        if (!subtitle.isEmpty()) {
            ListTag l = QiListTag.mixed();
            for (String s : subtitle) l.add(StringTag.valueOf(s));
            tag.put("subtitle", l);
        }
        if (!group.isEmpty()) tag.putString("group", group);
        tag.putInt("order_index", orderIndex);
        if (!defaultQuestShape.isEmpty()) tag.putString("default_quest_shape", defaultQuestShape);
        if (defaultQuestSize != 1.0) tag.putDouble("default_quest_size", defaultQuestSize);
        if (hideUntilStartable) tag.putBoolean("hide_until_startable", true);
        if (alwaysInvisible) tag.putBoolean("always_invisible", true);
        ListTag ql = QiListTag.mixed();
        for (Quest q : quests) ql.add(q.write());
        tag.put("quests", ql);
        if (!images.isEmpty()) {
            ListTag il = QiListTag.mixed();
            for (ChapterImage i : images) il.add(i.write());
            tag.put("images", il);
        }
        return tag;
    }

    @Override
    public ItemStack displayIcon(HolderLookup.Provider registries) {
        if (!icon.isEmpty()) return icon.stack(registries);
        if (!quests.isEmpty()) return quests.get(0).displayIcon(registries);
        return new ItemStack(Items.BOOK);
    }
}
