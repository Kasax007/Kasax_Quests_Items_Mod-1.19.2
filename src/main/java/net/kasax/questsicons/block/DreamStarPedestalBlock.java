package net.kasax.questsicons.block;

import com.mojang.serialization.MapCodec;
import net.kasax.questsicons.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A pedestal for the Dream Star. Right-click it with the star to put it on display; right-click with an empty hand
 * to take it back. While the star is on it, the pedestal glows (light 15) and sparkles. No hopper can take the star.
 */
public class DreamStarPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<DreamStarPedestalBlock> CODEC = simpleCodec(DreamStarPedestalBlock::new);
    public static final BooleanProperty HAS_STAR = BooleanProperty.create("has_star");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(4, 3, 4, 12, 13, 12),
            Block.box(3, 13, 3, 13, 15, 13));

    public DreamStarPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(HAS_STAR, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_STAR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DreamStarPedestalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(ModItems.DREAM_STAR) || !(level.getBlockEntity(pos) instanceof DreamStarPedestalBlockEntity be) || !be.getStar().isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            be.setStar(player.hasInfiniteMaterials() ? stack.copyWithCount(1) : stack.split(1));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof DreamStarPedestalBlockEntity be)
                || be.getStar().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, be.takeStar());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HAS_STAR)) return;
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.38;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.7, y + (random.nextDouble() - 0.5) * 0.5,
                    z + (random.nextDouble() - 0.5) * 0.7, 0, 0.01 + random.nextDouble() * 0.02, 0);
        }
        // Enchanting glyphs that drift into the star.
        double a = random.nextDouble() * Math.PI * 2;
        level.addParticle(ParticleTypes.ENCHANT, x, y, z, Math.cos(a) * 1.2, -0.4 - random.nextDouble() * 0.4, Math.sin(a) * 1.2);
    }
}
