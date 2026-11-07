package net.kasax.questsicons.quest.reward;

import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Something a player receives after finishing a quest. */
public abstract class Reward extends QuestObject {
    public Quest quest;
    /** null = book default; true = one claim for the whole team. */
    public Boolean teamReward;
    public boolean excludeFromClaimAll;
    public boolean autoClaim;

    public abstract String type();

    public void read(CompoundTag tag) {
        readBase(tag);
        teamReward = tag.contains("team_reward") ? tag.getBooleanOr("team_reward", false) : null;
        excludeFromClaimAll = tag.getBooleanOr("exclude_from_claim_all", false);
        autoClaim = tag.getBooleanOr("auto", false);
    }

    public void write(CompoundTag tag) {
        writeBase(tag);
        tag.putString("type", type());
        if (teamReward != null) tag.putBoolean("team_reward", teamReward);
        if (excludeFromClaimAll) tag.putBoolean("exclude_from_claim_all", true);
        if (autoClaim) tag.putBoolean("auto", true);
    }

    public final CompoundTag write() {
        CompoundTag t = new CompoundTag();
        write(t);
        return t;
    }

    public boolean isTeamReward() {
        return teamReward != null ? teamReward : quest != null && quest.chapter.book.defaultTeamReward;
    }

    public boolean isExcludedFromClaimAll() {
        return excludeFromClaimAll || needsChoice();
    }

    /** True for rewards where the player picks one option (opens a picker instead of claiming). */
    public boolean needsChoice() {
        return false;
    }

    /** Is {@code choice} (-1 = no pick) a valid way to claim this reward? */
    public boolean acceptsChoice(int choice) {
        return !needsChoice() || choice >= 0;
    }

    /** Options for a choice reward. */
    public List<ItemStack> choices(HolderLookup.Provider registries) {
        return List.of();
    }

    protected abstract Component defaultTitle(HolderLookup.Provider registries);

    protected abstract ItemStack defaultIcon(HolderLookup.Provider registries);

    public Component title(HolderLookup.Provider registries) {
        return title.isEmpty() ? defaultTitle(registries) : text(title);
    }

    @Override
    public ItemStack displayIcon(HolderLookup.Provider registries) {
        return icon.isEmpty() ? defaultIcon(registries) : icon.stack(registries);
    }

    /** Give the reward. {@code choice} is the picked option for choice rewards, otherwise -1. */
    public abstract void claim(ServerPlayer player, int choice);

    /** Gives a stack of any size, split into normal stacks; what does not fit is dropped at the player's feet. */
    public static void give(ServerPlayer player, ItemStack stack) {
        int left = stack.getCount();
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(left, stack.getMaxStackSize()));
            left -= part.getCount();
            if (!player.getInventory().add(part) || !part.isEmpty()) {
                player.drop(part, false);
            }
        }
    }
}
