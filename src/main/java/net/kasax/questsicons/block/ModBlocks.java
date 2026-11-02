package net.kasax.questsicons.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.kasax.questsicons.QuestsIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Consumer;

public final class ModBlocks {
    private ModBlocks() {
    }

    /** Shows off a Dream Star: it spins above the pedestal, sparkles and lights up the room. */
    public static final Block DREAM_STAR_PEDESTAL;
    public static final Item DREAM_STAR_PEDESTAL_ITEM;
    public static final BlockEntityType<DreamStarPedestalBlockEntity> DREAM_STAR_PEDESTAL_BE;

    static {
        Identifier id = Identifier.fromNamespaceAndPath(QuestsIcons.MOD_ID, "dream_star_pedestal");
        DREAM_STAR_PEDESTAL = Registry.register(BuiltInRegistries.BLOCK, id, new DreamStarPedestalBlock(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)
                .lightLevel(s -> s.getValue(DreamStarPedestalBlock.HAS_STAR) ? 15 : 0).pushReaction(PushReaction.BLOCK).noOcclusion()));
        DREAM_STAR_PEDESTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(DREAM_STAR_PEDESTAL,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix().rarity(Rarity.RARE)) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
                builder.accept(Component.translatable("block.questsicons.dream_star_pedestal.tooltip").withStyle(ChatFormatting.GRAY));
            }
        });
        DREAM_STAR_PEDESTAL_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
                FabricBlockEntityTypeBuilder.create(DreamStarPedestalBlockEntity::new, DREAM_STAR_PEDESTAL).build());
    }

    public static void registerBlocks() {
        QuestsIcons.LOGGER.debug("Registered {}", BuiltInRegistries.BLOCK.getKey(DREAM_STAR_PEDESTAL));
    }
}
