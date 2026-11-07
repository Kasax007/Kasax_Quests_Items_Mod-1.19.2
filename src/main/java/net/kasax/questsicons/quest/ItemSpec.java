package net.kasax.questsicons.quest;

import net.kasax.questsicons.QuestsIcons;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An item reference inside the quest file. Accepts the FTB-style shapes
 * {@code "minecraft:stone"}, {@code "#c:ingots/iron"} (an item tag),
 * {@code { id: "minecraft:stone", count: 4, components: {...} }},
 * {@code "@create"} (any item of a mod) and a list of these, {@code ["minecraft:a", "#c:b"]}, meaning "any of them"
 * (what FTB Quests' item filters become on import).
 */
public final class ItemSpec {
    public static final ItemSpec EMPTY = new ItemSpec(StringTag.valueOf("minecraft:air"));

    private final Tag raw;
    private @Nullable ItemStack cached;
    private @Nullable List<ItemStack> tagItems;
    private @Nullable List<ItemSpec> alternatives;

    public ItemSpec(Tag raw) {
        this.raw = raw;
    }

    public static ItemSpec read(@Nullable Tag tag) {
        return tag == null ? EMPTY : new ItemSpec(tag.copy());
    }

    public static ItemSpec of(String id) {
        return new ItemSpec(StringTag.valueOf(id));
    }

    public static ItemSpec of(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return EMPTY;
        // 1.20.1: item data is the stack's NBT tag, stored as {id, count, tag} (1.20.5+ stores "components").
        if (!stack.hasTag()) {
            if (stack.getCount() == 1) return of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            CompoundTag c = new CompoundTag();
            c.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            c.putInt("count", stack.getCount());
            return new ItemSpec(c);
        }
        CompoundTag c = new CompoundTag();
        c.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        c.putInt("count", stack.getCount());
        c.put("tag", stack.getTag().copy());
        return new ItemSpec(c);
    }

    public Tag write() {
        return raw.copy();
    }

    public boolean isTag() {
        return raw instanceof StringTag s && s.value().startsWith("#");
    }

    /** "@modid": any item of that mod. */
    public boolean isMod() {
        return raw instanceof StringTag s && s.value().startsWith("@");
    }

    /** A list of alternatives: any of them will do. */
    public boolean isList() {
        return raw instanceof ListTag;
    }

    /** The alternatives of a list (an empty list has none); a single spec is its own only alternative. */
    public List<ItemSpec> alternatives() {
        if (alternatives == null) {
            List<ItemSpec> l = new ArrayList<>();
            if (raw instanceof ListTag list) {
                for (Tag t : list) l.add(new ItemSpec(t));
            } else {
                l.add(this);
            }
            alternatives = l;
        }
        return alternatives;
    }

    public @Nullable TagKey<Item> tagKey() {
        if (!isTag()) return null;
        ResourceLocation id = ResourceLocation.tryParse(((StringTag) raw).value().substring(1));   // null for a typo instead of a crash
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    public String idString() {
        if (raw instanceof StringTag s) return s.value();
        if (raw instanceof CompoundTag c) return c.getStringOr("id", "minecraft:air");
        if (raw instanceof ListTag && !alternatives().isEmpty()) return alternatives().get(0).idString();
        return "minecraft:air";
    }

    public boolean isEmpty() {
        if (raw instanceof ListTag) return alternatives().isEmpty();
        String id = idString();
        return id.equals("minecraft:air") || id.isEmpty();
    }

    /** The concrete stack (for a tag or a mod: its first item; for a list: the first alternative). Never null. */
    public ItemStack stack(@Nullable HolderLookup.Provider registries) {
        if (cached != null) return cached;
        ItemStack result = ItemStack.EMPTY;
        try {
            if (isList()) {
                for (ItemSpec alt : alternatives()) {
                    result = alt.stack(registries);
                    if (!result.isEmpty() && !result.is(Items.BARRIER)) break;
                }
            } else if (isTag() || isMod()) {
                List<ItemStack> items = tagItems();
                result = items.isEmpty() ? new ItemStack(Items.BARRIER) : items.get(0);
            } else if (raw instanceof StringTag s) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(s.value()));
                result = new ItemStack(item);
            } else if (raw instanceof CompoundTag c) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(c.getStringOr("id", "minecraft:air")));
                result = new ItemStack(item, Math.max(1, c.getIntOr("count", 1)));
                // Item data of 1.20.1 ("tag"); 1.20.5+ "components" (from a newer quest file) cannot be read here.
                if (!result.isEmpty() && c.get("tag") instanceof CompoundTag nbt) result.setTag(nbt.copy());
            }
        } catch (Exception e) {
            result = new ItemStack(Items.BARRIER);
        }
        if (result.isEmpty() && !isEmpty()) result = new ItemStack(Items.BARRIER);
        if (registries != null || !hasComponents()) cached = result;
        return result;
    }

    private boolean hasComponents() {
        if (raw instanceof CompoundTag c) return c.contains("tag");
        if (raw instanceof ListTag) for (ItemSpec a : alternatives()) if (a.hasComponents()) return true;
        return false;
    }

    /**
     * The stack to hand out as a reward. {@link #stack} shows a barrier for an unknown id or an empty tag (fine for an
     * icon); a reward must not give out barriers, so this is empty instead and the log names the broken entry.
     */
    public ItemStack rewardStack(@Nullable HolderLookup.Provider registries) {
        ItemStack s = stack(registries);
        if (s.is(Items.BARRIER) && !idString().equals("minecraft:barrier")) {
            QuestsIcons.LOGGER.warn("A quest reward names an item that does not exist (or an empty tag): {}", idString());
            return ItemStack.EMPTY;
        }
        return s;
    }

    /**
     * The items this spec stands for when it is more than one item (for cycling icons): the items of a tag or a mod,
     * or every alternative of a list (tags and mods in it expanded). Empty for a single item.
     */
    public List<ItemStack> tagItems() {
        if (tagItems != null) return tagItems;
        List<ItemStack> list = new ArrayList<>();
        if (isTag()) {
            TagKey<Item> key = tagKey();
            if (key != null) {
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) list.add(new ItemStack(holder.value()));
            }
        } else if (isMod()) {
            String mod = ((StringTag) raw).value().substring(1);
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(mod)) list.add(new ItemStack(item));
                if (list.size() >= 256) break;
            }
        } else if (isList()) {
            for (ItemSpec alt : alternatives()) {
                if (alt.isTag() || alt.isMod()) {
                    list.addAll(alt.tagItems());
                } else {
                    ItemStack s = alt.stack(null);
                    if (!s.isEmpty() && !s.is(Items.BARRIER)) list.add(s);
                }
                if (list.size() >= 256) break;
            }
        }
        tagItems = list;
        return list;
    }

    /** Shows more than one item (a tag, a mod or a list), so icons cycle through {@link #tagItems()}. */
    public boolean cycles() {
        return (isTag() || isMod() || isList()) && !tagItems().isEmpty();
    }

    public int count() {
        if (raw instanceof CompoundTag c) return Math.max(1, c.getIntOr("count", 1));
        return 1;
    }

    /** Does {@code candidate} satisfy this spec (ignoring count)? */
    public boolean matches(ItemStack candidate, boolean matchComponents, @Nullable HolderLookup.Provider registries) {
        return matches(candidate, matchComponents, false, registries);
    }

    /**
     * With {@code fuzzy} the candidate needs the spec's components (it may carry more, like FTB's fuzzy match);
     * otherwise its components must be exactly the spec's.
     */
    public boolean matches(ItemStack candidate, boolean matchComponents, boolean fuzzy, @Nullable HolderLookup.Provider registries) {
        if (candidate.isEmpty()) return false;
        if (isList()) {
            for (ItemSpec alt : alternatives()) if (alt.matches(candidate, matchComponents, fuzzy, registries)) return true;
            return false;
        }
        if (isTag()) {
            TagKey<Item> key = tagKey();
            return key != null && candidate.is(key);
        }
        if (isMod()) {
            return BuiltInRegistries.ITEM.getKey(candidate.getItem()).getNamespace().equals(((StringTag) raw).value().substring(1));
        }
        ItemStack target = stack(registries);
        if (!candidate.is(target.getItem())) return false;
        if (!matchComponents) return true;
        if (!fuzzy) return Objects.equals(candidate.getTag(), target.getTag());
        // Fuzzy: the candidate has at least the target's data (1.20.1: NBT, lists may hold more entries).
        return NbtUtils.compareNbt(target.getTag(), candidate.getTag(), true);
    }

    @Override
    public String toString() {
        return raw.toString();
    }
}
