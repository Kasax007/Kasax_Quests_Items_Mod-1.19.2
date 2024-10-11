package net.kasax.questsicons.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.kasax.questsicons.QuestsIcons;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroup {
    public static ItemGroup QUEST_ITEMS = Registry.register(Registries.ITEM_GROUP, Identifier.of(QuestsIcons.MOD_ID, "questitems"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.questitems"))
                    .icon(() -> new ItemStack(ModItems.QUEST_CROWN)).entries((displayContext, entries) -> {
                        entries.add(ModItems.CHAPTER_LOCK);
                        entries.add(ModItems.END_LOCK);
                        entries.add(ModItems.QUEST_BACKPACK);
                        entries.add(ModItems.QUEST_CROWN);
                        entries.add(ModItems.QUEST_DUNGEON);
                        entries.add(ModItems.QUEST_KEY);
                        entries.add(ModItems.QUEST_PORTAL_ONE);
                        entries.add(ModItems.QUEST_PORTAL_TWO);
                        entries.add(ModItems.QUEST_PORTAL_THREE);
                        entries.add(ModItems.QUEST_PORTAL_FOUR);
                        entries.add(ModItems.QUEST_PORTAL_FIVE);
                        entries.add(ModItems.QUEST_REWARD);
                        entries.add(ModItems.LOGO_FABRIC);
                    }).build());
    public static void registerItemGroups() {

    }
}
