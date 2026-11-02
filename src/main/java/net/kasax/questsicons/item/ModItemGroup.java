package net.kasax.questsicons.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroup {
    public static final CreativeModeTab QUEST_ITEMS = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(QuestsIcons.MOD_ID, "questitems"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemgroup.questitems"))
                    .icon(() -> new ItemStack(ModItems.QUEST_CROWN))
                    .displayItems((params, out) -> {
                        out.accept(ModItems.QUEST_BOOK);
                        out.accept(ModItems.DREAM_STAR);
                        out.accept(ModBlocks.DREAM_STAR_PEDESTAL_ITEM);
                        out.accept(ModItems.COMMON_CRATE);
                        out.accept(ModItems.RARE_CRATE);
                        out.accept(ModItems.EPIC_CRATE);
                        out.accept(ModItems.LEGENDARY_CRATE);
                        out.accept(ModItems.QUEST_REWARD);
                        for (Item i : ModItems.ICONS) out.accept(i);
                    }).build());

    private ModItemGroup() {
    }

    public static void registerItemGroups() {
    }
}
