package net.kasax.questsicons.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.item.custom.QuestRewardItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;

public class ModItems {

    public static final Item CHAPTER_LOCK = registerItem("chapter_lock" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item END_LOCK = registerItem("end_lock" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_BACKPACK = registerItem("quest_backpack" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_CROWN = registerItem("quest_crown" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_DUNGEON = registerItem("quest_dungeon" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_KEY = registerItem("quest_key" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_PORTAL_ONE = registerItem("quest_portal_one" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_PORTAL_TWO = registerItem("quest_portal_two" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_PORTAL_THREE = registerItem("quest_portal_three" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_PORTAL_FOUR = registerItem("quest_portal_four" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_PORTAL_FIVE = registerItem("quest_portal_five" ,
            new Item(new FabricItemSettings().maxCount(1)));

    public static final Item QUEST_REWARD = registerItem("quest_reward" ,
            new QuestRewardItem(new FabricItemSettings().maxCount(1)));

    public static final Item LOGO_FABRIC = registerItem("logo_fabric" ,
            new Item(new FabricItemSettings().maxCount(1)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(QuestsIcons.MOD_ID, name), item);
    }

    public static  void registerModItems() {
        QuestsIcons.LOGGER.debug("Register Mod Items for " + QuestsIcons.MOD_ID);
    }
}
