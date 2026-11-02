package net.kasax.questsicons.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The Dreamcraft endgame trophy (like the "ATM Star"): crafted from key items of the whole pack. It shines, cannot
 * burn or be blown up, never despawns when dropped and can be shown off on a Dream Star Pedestal.
 */
public class DreamStarItem extends Item {
    public DreamStarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.questsicons.dream_star.tooltip").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        builder.accept(Component.translatable("item.questsicons.dream_star.tooltip_pedestal").withStyle(ChatFormatting.GRAY));
    }
}
