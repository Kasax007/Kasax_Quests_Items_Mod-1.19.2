package net.kasax.questsicons.quest;

import net.kasax.questsicons.compat.QiListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A weighted list of items (file {@code reward_tables/<key>.snbt}). Used by "random" rewards and by
 * the loot crates, so the pack author decides exactly what a crate can contain.
 */
public final class RewardTable {
    public String key = "";
    public String title = "";
    public ItemSpec icon = ItemSpec.EMPTY;
    public final List<Entry> entries = new ArrayList<>();

    public static RewardTable read(String key, CompoundTag tag) {
        RewardTable t = new RewardTable();
        t.key = key;
        t.title = tag.getStringOr("title", "");
        t.icon = ItemSpec.read(tag.get("icon"));
        for (Tag e : tag.getListOrEmpty("entries")) {
            if (e instanceof CompoundTag c) t.entries.add(Entry.read(c));
        }
        return t;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        if (!title.isEmpty()) tag.putString("title", title);
        if (!icon.isEmpty()) tag.put("icon", icon.write());
        ListTag l = QiListTag.mixed();
        for (Entry e : entries) l.add(e.write());
        tag.put("entries", l);
        return tag;
    }

    public static @Nullable Entry pick(List<Entry> entries, RandomSource random) {
        int total = 0;
        for (Entry e : entries) total += Math.max(0, e.weight);
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Entry e : entries) {
            roll -= Math.max(0, e.weight);
            if (roll < 0) return e;
        }
        return entries.get(entries.size() - 1);
    }

    public static final class Entry {
        public ItemSpec item = ItemSpec.EMPTY;
        public int count = 1;
        public int weight = 10;
        /** Up to this many extra items, rolled each time (FTB's "random bonus"). */
        public int randomBonus;

        public static Entry read(CompoundTag c) {
            Entry e = new Entry();
            e.item = ItemSpec.read(c.get("item"));
            e.count = c.getIntOr("count", e.item.count());
            e.weight = c.getIntOr("weight", 10);
            e.randomBonus = Math.max(0, c.getIntOr("random_bonus", 0));
            return e;
        }

        public CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.put("item", item.write());
            if (count != 1) c.putInt("count", count);
            c.putInt("weight", weight);
            if (randomBonus > 0) c.putInt("random_bonus", randomBonus);
            return c;
        }

        /** The count for one roll: the base count plus the random bonus. */
        public int rollCount(RandomSource random) {
            return Math.max(1, count) + (randomBonus > 0 ? random.nextInt(randomBonus + 1) : 0);
        }
    }
}
