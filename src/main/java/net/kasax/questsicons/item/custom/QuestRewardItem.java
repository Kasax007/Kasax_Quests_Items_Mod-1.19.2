package net.kasax.questsicons.item.custom;

import net.kasax.questsicons.QuestsIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;

/** The old "mystery reward": up to five uses, each gives 1-3 of a random survival item. */
public class QuestRewardItem extends Item {
    public QuestRewardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        user.getCooldowns().addCooldown(this, 20);
        FeatureFlagSet enabled = level.enabledFeatures();
        List<Holder.Reference<Item>> items = BuiltInRegistries.ITEM.holders().filter(h -> isPrize(h, enabled)).toList();
        Item item = items.get(level.getRandom().nextInt(items.size())).value();
        ItemStack gift = new ItemStack(item, 1 + level.getRandom().nextInt(Math.min(3, Math.max(1, new ItemStack(item).getMaxStackSize()))));
        if (!user.getInventory().add(gift)) user.drop(gift, false);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f,
                1f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        user.sendSystemMessage(Component.translatable("item.questsicons.quest_reward.received").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!user.getAbilities().instabuild) stack.hurtAndBreak(1, user, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.success(stack);
    }

    /** Operator-only and creative-only blocks, items of disabled experiments and Quest Icons' own items are no prizes. */
    private static final Set<Item> NOT_A_PRIZE = Set.of(Items.AIR, Items.BARRIER, Items.LIGHT, Items.STRUCTURE_VOID, Items.DEBUG_STICK,
            Items.KNOWLEDGE_BOOK, Items.BEDROCK, Items.END_PORTAL_FRAME, Items.REINFORCED_DEEPSLATE, Items.SPAWNER,
            Items.BUDDING_AMETHYST, Items.PETRIFIED_OAK_SLAB);

    static boolean isPrize(Holder.Reference<Item> h, FeatureFlagSet enabled) {
        Item i = h.value();
        if (NOT_A_PRIZE.contains(i) || i instanceof GameMasterBlockItem || !i.isEnabled(enabled)) return false;
        ResourceLocation id = h.key().location();
        if (id.getNamespace().equals(QuestsIcons.MOD_ID)) return false;
        String path = id.getPath();
        return !path.contains("spawn_egg") && !path.contains("command") && !path.contains("creative") && !path.contains("debug");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.questsicons.quest_reward.tooltip").withStyle(ChatFormatting.YELLOW));
    }
}
