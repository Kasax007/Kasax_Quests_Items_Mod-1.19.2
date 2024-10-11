package net.kasax.questsicons.item.custom;

import net.kasax.questsicons.function.ItemListGenerator;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;

public class QuestRewardItem extends Item {
    public QuestRewardItem(Settings settings) {
        super(settings.maxDamage(4));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if(!world.isClient() && hand == Hand.MAIN_HAND) {
            user.getItemCooldownManager().set(this, 20);
            giveRandomItem(user.getInventory());
            world.playSound((PlayerEntity) null, user.getX(), user.getY(), user.getZ(),
                    BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 10F,
                    1F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            outputRandomNumber(user);
        }
        if (!user.getAbilities().creativeMode) {
            ItemStack stack = user.getStackInHand(hand);
            stack.damage(1, user, EquipmentSlot.MAINHAND);
        }
        return super.use(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Text.translatable("Right click this item in your hand up to 5 times to receive a random item.").formatted(Formatting.RED));
        }
        else {
            tooltip.add(Text.translatable("Press Shift for more Info!").formatted(Formatting.YELLOW));
        }


        super.appendTooltip(stack, context, tooltip, type);
    }

    private void giveRandomItem(PlayerInventory player) {
        List<Identifier> list = ItemListGenerator.generateItemList();
        int index = (int)(Math.random() * list.size());
        Identifier itemId = list.get(index);
        ItemStack stack = new ItemStack(Registries.ITEM.get(itemId));
        int stackSize = ThreadLocalRandom.current().nextInt(3) + 1;  // Generate a random number between 1 and 10 (inclusive)
        stack.setCount(stackSize);
        player.offerOrDrop(stack);
    }
    private void outputRandomNumber(PlayerEntity player) {
        player.sendMessage(Text.literal("You have received a random item!"));
    }
}
