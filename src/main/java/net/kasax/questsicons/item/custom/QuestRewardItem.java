package net.kasax.questsicons.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/** The old "mystery reward": up to five uses, each gives 1-3 of a random survival item. */
public class QuestRewardItem extends Item {
    public QuestRewardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = user.getItemInHand(hand);
        user.getCooldowns().addCooldown(stack, 20);
        List<Holder.Reference<Item>> items = BuiltInRegistries.ITEM.listElements()
                .filter(h -> {
                    Item i = h.value();
                    return i != Items.AIR && i != Items.COMMAND_BLOCK && i != Items.BARRIER && i != Items.STRUCTURE_BLOCK
                            && i != Items.JIGSAW && i != Items.DEBUG_STICK && i != Items.LIGHT && i != Items.STRUCTURE_VOID
                            && !h.key().identifier().getPath().contains("spawn_egg") && !h.key().identifier().getPath().contains("command");
                }).toList();
        Item item = items.get(level.getRandom().nextInt(items.size())).value();
        ItemStack gift = new ItemStack(item, 1 + level.getRandom().nextInt(Math.min(3, Math.max(1, new ItemStack(item).getMaxStackSize()))));
        if (!user.getInventory().add(gift)) user.drop(gift, false, net.minecraft.util.Prediction.SERVER_ONLY);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f,
                1f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        user.sendSystemMessage(Component.translatable("item.questsicons.quest_reward.received").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!user.getAbilities().instabuild) stack.hurtAndBreak(1, user, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.questsicons.quest_reward.tooltip").withStyle(ChatFormatting.YELLOW));
    }
}
