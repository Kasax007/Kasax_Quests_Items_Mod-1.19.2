package net.kasax.questsicons.quest;

import net.kasax.questsicons.QuestsIcons;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
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
 * {@code "minecraft:stone"}, {@code "#c:ingots/iron"} (an item tag) and
 * {@code { id: "minecraft:stone", count: 4, components: {...} }}.
 */
public final class ItemSpec {
    public static final ItemSpec EMPTY = new ItemSpec(StringTag.valueOf("minecraft:air"));

    private final Tag raw;
    private @Nullable ItemStack cached;
    private @Nullable List<ItemStack> tagItems;

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
        if (stack.getComponentsPatch().isEmpty()) {
            if (stack.getCount() == 1) return of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            CompoundTag c = new CompoundTag();
            c.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            c.putInt("count", stack.getCount());
            return new ItemSpec(c);
        }
        Tag t = ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
        return new ItemSpec(t);
    }

    public Tag write() {
        return raw.copy();
    }

    public boolean isTag() {
        return raw instanceof StringTag s && s.value().startsWith("#");
    }

    public @Nullable TagKey<Item> tagKey() {
        if (!isTag()) return null;
        Identifier id = Identifier.tryParse(((StringTag) raw).value().substring(1));   // null for a typo instead of a crash
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    public String idString() {
        if (raw instanceof StringTag s) return s.value();
        if (raw instanceof CompoundTag c) return c.getStringOr("id", "minecraft:air");
        return "minecraft:air";
    }

    public boolean isEmpty() {
        String id = idString();
        return id.equals("minecraft:air") || id.isEmpty();
    }

    /** The concrete stack (for a tag: the first item of the tag). Never null. */
    public ItemStack stack(@Nullable HolderLookup.Provider registries) {
        if (cached != null) return cached;
        ItemStack result = ItemStack.EMPTY;
        try {
            if (isTag()) {
                List<ItemStack> items = tagItems();
                result = items.isEmpty() ? new ItemStack(Items.BARRIER) : items.get(0);
            } else if (raw instanceof StringTag s) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(s.value()));
                result = new ItemStack(item);
            } else if (raw instanceof CompoundTag c) {
                if (c.contains("components") && registries != null) {
                    CompoundTag fixed = c.copy();
                    if (!fixed.contains("count")) fixed.putInt("count", 1);
                    result = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), fixed)
                            .result().orElse(ItemStack.EMPTY);
                }
                if (result.isEmpty()) {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(c.getStringOr("id", "minecraft:air")));
                    result = new ItemStack(item, Math.max(1, c.getIntOr("count", 1)));
                }
            }
        } catch (Exception e) {
            result = new ItemStack(Items.BARRIER);
        }
        if (result.isEmpty() && !isEmpty()) result = new ItemStack(Items.BARRIER);
        if (registries != null || !(raw instanceof CompoundTag c2 && c2.contains("components"))) cached = result;
        return result;
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

    /** The items an item tag stands for (for cycling icons). */
    public List<ItemStack> tagItems() {
        if (tagItems != null) return tagItems;
        List<ItemStack> list = new ArrayList<>();
        TagKey<Item> key = tagKey();
        if (key != null) {
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) list.add(new ItemStack(holder.value()));
        }
        tagItems = list;
        return list;
    }

    public int count() {
        if (raw instanceof CompoundTag c) return Math.max(1, c.getIntOr("count", 1));
        return 1;
    }

    /** Does {@code candidate} satisfy this spec (ignoring count)? */
    public boolean matches(ItemStack candidate, boolean matchComponents, @Nullable HolderLookup.Provider registries) {
        if (candidate.isEmpty()) return false;
        if (isTag()) {
            TagKey<Item> key = tagKey();
            return key != null && candidate.is(key);
        }
        ItemStack target = stack(registries);
        if (!candidate.is(target.getItem())) return false;
        if (!matchComponents) return true;
        return Objects.equals(candidate.getComponentsPatch(), target.getComponentsPatch());
    }

    @Override
    public String toString() {
        return raw.toString();
    }
}
