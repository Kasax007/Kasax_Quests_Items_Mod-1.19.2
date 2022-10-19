package net.kasax.questsicons.item;

import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.kasax.questsicons.QuestsIcons;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class ModItemGroup {
    public static final ItemGroup QUEST_ITEMS = FabricItemGroupBuilder.build(new Identifier(QuestsIcons.MOD_ID, "questitems"), () -> new ItemStack(ModItems.QUEST_CROWN));

}
