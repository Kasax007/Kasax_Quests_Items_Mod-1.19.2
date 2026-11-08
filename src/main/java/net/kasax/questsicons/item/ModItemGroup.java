package net.kasax.questsicons.item;

import net.kasax.questsicons.ModRegistry;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.icons.IconLibrary;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Creative tabs: "Quest Icons" (book, crates, Dream Star and the classic icons) and one tab per group of icon
 * categories from the icon library (letters and numbers, symbols, ranks, themes).
 */
public final class ModItemGroup {
    public static final CreativeModeTab QUEST_ITEMS = ModRegistry.register(Registries.CREATIVE_MODE_TAB,
            new ResourceLocation(QuestsIcons.MOD_ID, "questitems"),
            FabricItemGroup.builder()
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
                        for (IconLibrary.Category c : IconLibrary.categoriesOfTab("questitems")) {
                            for (IconLibrary.Icon i : c.icons()) out.accept(ModItems.ICON_BY_ID.get(i.id()));
                        }
                    }).build());

    static {
        for (IconLibrary.Tab tab : IconLibrary.TABS) {
            if (tab.id().equals("questitems")) continue;
            ModRegistry.register(Registries.CREATIVE_MODE_TAB, new ResourceLocation(QuestsIcons.MOD_ID, "icons_" + tab.id()),
                    FabricItemGroup.builder()
                            .title(Component.translatable("itemgroup.questsicons." + tab.id()))
                            .icon(() -> new ItemStack(ModItems.ICON_BY_ID.get(tab.icon())))
                            .displayItems((params, out) -> {
                                for (IconLibrary.Category c : IconLibrary.categoriesOfTab(tab.id())) {
                                    for (IconLibrary.Icon i : c.icons()) {
                                        Item item = ModItems.ICON_BY_ID.get(i.id());
                                        if (item != null) out.accept(item);
                                    }
                                }
                            }).build());
        }
    }

    private ModItemGroup() {
    }

    public static void registerItemGroups() {
    }
}
