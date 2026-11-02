package net.kasax.questsicons.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds the displayed Dream Star (synced to clients so the renderer can draw it). */
public class DreamStarPedestalBlockEntity extends BlockEntity {
    private ItemStack star = ItemStack.EMPTY;

    public DreamStarPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DREAM_STAR_PEDESTAL_BE, pos, state);
    }

    public ItemStack getStar() {
        return this.star;
    }

    public void setStar(ItemStack stack) {
        this.star = stack;
        changed();
    }

    public ItemStack takeStar() {
        ItemStack out = this.star;
        this.star = ItemStack.EMPTY;
        changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (this.level == null) return;
        BlockState st = getBlockState();
        boolean has = !this.star.isEmpty();
        if (st.getValue(DreamStarPedestalBlock.HAS_STAR) != has) {
            this.level.setBlock(this.worldPosition, st.setValue(DreamStarPedestalBlock.HAS_STAR, has), Block.UPDATE_ALL);
        } else {
            this.level.sendBlockUpdated(this.worldPosition, st, st, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.star = input.read("star", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.star.isEmpty()) output.store("star", ItemStack.CODEC, this.star);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveAdditional(out);
        return out.buildResult();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.star.isEmpty()) {
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.star);
            this.star = ItemStack.EMPTY;
        }
    }
}
