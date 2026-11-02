package net.kasax.questsicons.item;

import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.item.custom.LootCrateItem;
import net.kasax.questsicons.item.custom.QuestBookItem;
import net.kasax.questsicons.item.custom.QuestRewardItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
    /** Every pure icon item, in creative-tab order. */
    public static final List<Item> ICONS = new ArrayList<>();

    private ModItems() {
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        Identifier id = Identifier.fromNamespaceAndPath(QuestsIcons.MOD_ID, name);
        Item item = factory.apply(props.setId(ResourceKey.create(Registries.ITEM, id)));
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    private static Item icon(String name) {
        Item item = register(name, Item::new, new Item.Properties().stacksTo(1));
        ICONS.add(item);
        return item;
    }

    // The quest book and the loot crates.
    public static final Item QUEST_BOOK = register("quest_book", QuestBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final Item COMMON_CRATE = register("common_crate", p -> new LootCrateItem(p, "common_crate"), new Item.Properties().rarity(Rarity.COMMON));
    public static final Item RARE_CRATE = register("rare_crate", p -> new LootCrateItem(p, "rare_crate"), new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final Item EPIC_CRATE = register("epic_crate", p -> new LootCrateItem(p, "epic_crate"), new Item.Properties().rarity(Rarity.RARE));
    public static final Item LEGENDARY_CRATE = register("legendary_crate", p -> new LootCrateItem(p, "legendary_crate"), new Item.Properties().rarity(Rarity.EPIC));

    // The original icons (kept with their ids so old quest files still find them).
    public static final Item CHAPTER_LOCK = icon("chapter_lock");
    public static final Item END_LOCK = icon("end_lock");
    public static final Item QUEST_BACKPACK = icon("quest_backpack");
    public static final Item QUEST_CROWN = icon("quest_crown");
    public static final Item QUEST_DUNGEON = icon("quest_dungeon");
    public static final Item QUEST_KEY = icon("quest_key");
    public static final Item QUEST_PORTAL_ONE = icon("quest_portal_one");
    public static final Item QUEST_PORTAL_TWO = icon("quest_portal_two");
    public static final Item QUEST_PORTAL_THREE = icon("quest_portal_three");
    public static final Item QUEST_PORTAL_FOUR = icon("quest_portal_four");
    public static final Item QUEST_PORTAL_FIVE = icon("quest_portal_five");
    public static final Item LOGO_FABRIC = icon("logo_fabric");

    // New chapter and quest icons.
    public static final Item ICON_COMPASS = icon("icon_compass");
    public static final Item ICON_SWORD = icon("icon_sword");
    public static final Item ICON_SHIELD = icon("icon_shield");
    public static final Item ICON_PICKAXE = icon("icon_pickaxe");
    public static final Item ICON_MAGIC = icon("icon_magic");
    public static final Item ICON_GEAR = icon("icon_gear");
    public static final Item ICON_HOUSE = icon("icon_house");
    public static final Item ICON_FOOD = icon("icon_food");
    public static final Item ICON_MAP = icon("icon_map");
    public static final Item ICON_STAR = icon("icon_star");
    public static final Item ICON_TROPHY = icon("icon_trophy");
    public static final Item ICON_SKULL = icon("icon_skull");
    public static final Item ICON_HEART = icon("icon_heart");
    public static final Item ICON_QUESTION = icon("icon_question");
    public static final Item ICON_EXCLAMATION = icon("icon_exclamation");
    public static final Item ICON_RAFT = icon("icon_raft");
    public static final Item ICON_POTION = icon("icon_potion");
    public static final Item ICON_CHEST = icon("icon_chest");
    public static final Item ICON_TREE = icon("icon_tree");
    public static final Item ICON_MOON = icon("icon_moon");

    public static final Item QUEST_REWARD = register("quest_reward", QuestRewardItem::new, new Item.Properties().stacksTo(1).durability(5));

    public static void registerModItems() {
        QuestsIcons.LOGGER.debug("Registered {} quest icons", ICONS.size());
    }
}
