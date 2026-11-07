package net.kasax.questsicons.item.custom;

import net.kasax.questsicons.server.QuestServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;

import java.util.List;

/** Right-click opens the quest book. */
public class QuestBookItem extends Item {
    public QuestBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && QuestServer.get() != null) {
            QuestServer.get().openBook(sp);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.questsicons.quest_book.tooltip", Component.keybind("key.questsicons.open_book"))
                .withStyle(ChatFormatting.GRAY));
    }
}
