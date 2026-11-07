package net.kasax.questsicons.item.custom;

import net.kasax.questsicons.quest.RewardTable;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A loot crate: opening it rolls the quest book's reward table with the same name
 * ({@code reward_tables/<table>.snbt}), so the pack decides what a crate may contain.
 */
public class LootCrateItem extends Item {
    private final String table;

    public LootCrateItem(Properties properties, String table) {
        super(properties);
        this.table = table;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        QuestServer qs = QuestServer.get();
        RewardTable t = qs == null ? null : qs.book.rewardTables.get(table);
        RewardTable.Entry e = t == null ? null : RewardTable.pick(t.entries, sp.getRandom());
        if (e == null) {
            sp.sendSystemMessage(Component.translatable("item.questsicons.crate.empty", table).withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }
        ItemStack loot = e.item.rewardStack(level.registryAccess()).copyWithCount(e.rollCount(sp.getRandom()));
        if (loot.isEmpty()) {
            // An item that does not exist (a typo or a mod that was removed): keep the crate instead of handing out a barrier.
            sp.sendSystemMessage(Component.translatable("item.questsicons.crate.broken", table, e.item.idString()).withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        Component name = loot.getHoverName();
        int count = loot.getCount();
        Reward.give(sp, loot);   // split into normal stacks, the rest drops
        level.playSound(null, sp.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 1.2f);
        level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1, sp.getZ(), 12, 0.4, 0.5, 0.4, 0.1);
        sp.sendSystemMessage(Component.translatable("item.questsicons.crate.opened", count, name).withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.questsicons.crate.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
