package net.kasax.questsicons.item.custom;

import net.kasax.questsicons.QuestsIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
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
        FeatureFlagSet enabled = level.enabledFeatures();
        List<Holder.Reference<Item>> items = BuiltInRegistries.ITEM.listElements().filter(h -> isPrize(h, enabled)).toList();
        Item item = items.get(level.getRandom().nextInt(items.size())).value();
        ItemStack gift = new ItemStack(item, 1 + level.getRandom().nextInt(Math.min(3, Math.max(1, new ItemStack(item).getMaxStackSize()))));
        if (!user.getInventory().add(gift)) user.drop(gift, false);
        level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f,
                1f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        user.displayClientMessage(Component.translatable("item.questsicons.quest_reward.received").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (!user.getAbilities().instabuild) stack.hurtAndBreak(1, user, hand);
        return InteractionResult.SUCCESS;
    }

    /** Operator-only and creative-only blocks, items of disabled experiments and Quest Icons' own items are no prizes. */
    private static final Set<Item> NOT_A_PRIZE = Set.of(Items.AIR, Items.BARRIER, Items.LIGHT, Items.STRUCTURE_VOID, Items.DEBUG_STICK,
            Items.KNOWLEDGE_BOOK, Items.BEDROCK, Items.END_PORTAL_FRAME, Items.REINFORCED_DEEPSLATE, Items.SPAWNER, Items.TRIAL_SPAWNER,
            Items.VAULT, Items.BUDDING_AMETHYST, Items.PETRIFIED_OAK_SLAB);

    static boolean isPrize(Holder.Reference<Item> h, FeatureFlagSet enabled) {
        Item i = h.value();
        if (NOT_A_PRIZE.contains(i) || i instanceof GameMasterBlockItem || !i.isEnabled(enabled)) return false;
        Identifier id = h.key().identifier();
        if (id.getNamespace().equals(QuestsIcons.MOD_ID)) return false;
        String path = id.getPath();
        return !path.contains("spawn_egg") && !path.contains("command") && !path.contains("creative") && !path.contains("debug");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.questsicons.quest_reward.tooltip").withStyle(ChatFormatting.YELLOW));
    }
}
