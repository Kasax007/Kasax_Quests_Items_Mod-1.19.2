package net.kasax.questsicons.item.custom;

import net.kasax.questsicons.quest.RewardTable;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        QuestServer qs = QuestServer.get();
        RewardTable t = qs == null ? null : qs.book.rewardTables.get(table);
        RewardTable.Entry e = t == null ? null : RewardTable.pick(t.entries, sp.getRandom());
        if (e == null) {
            sp.sendSystemMessage(Component.translatable("item.questsicons.crate.empty", table).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ItemStack loot = e.item.stack(level.registryAccess()).copyWithCount(Math.max(1, e.count));
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        Component name = loot.getHoverName();
        int count = loot.getCount();
        if (!sp.getInventory().add(loot)) sp.drop(loot, false, net.minecraft.util.Prediction.SERVER_ONLY);
        level.playSound(null, sp.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 1.2f);
        level.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, sp.getX(), sp.getY() + 1, sp.getZ(), 12, 0.4, 0.5, 0.4, 0.1);
        sp.sendSystemMessage(Component.translatable("item.questsicons.crate.opened", count, name).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.questsicons.crate.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
