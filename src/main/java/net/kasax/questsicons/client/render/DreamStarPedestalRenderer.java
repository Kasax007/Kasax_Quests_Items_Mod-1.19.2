package net.kasax.questsicons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.questsicons.block.DreamStarPedestalBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The star on the pedestal: floats, bobs gently, turns once every twelve seconds and is drawn at full brightness. */
@Environment(EnvType.CLIENT)
public class DreamStarPedestalRenderer implements BlockEntityRenderer<DreamStarPedestalBlockEntity, DreamStarPedestalRenderer.State> {
    /** Seamless loop: 30 turns (240 ticks each) and 90 bobs (80 ticks each). */
    private static final long LOOP = 7200;
    private final ItemModelResolver itemModelResolver;

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean hasStar;
        float time;
    }

    public DreamStarPedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DreamStarPedestalBlockEntity be, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, crumbling);
        ItemStack star = be.getStar();
        state.hasStar = !star.isEmpty() && be.getLevel() != null;
        if (state.hasStar) {
            this.itemModelResolver.updateForTopItem(state.item, star, ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong());
            state.time = (be.getLevel().getGameTime() % LOOP) + partialTick;
        } else {
            state.item.clear();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.hasStar || state.item.isEmpty()) return;
        poseStack.pushPose();
        float bob = Mth.sin(state.time * Mth.TWO_PI / 80f) * 0.05f;
        poseStack.translate(0.5f, 15f / 16f + 0.45f + bob, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.time * 1.5f));
        poseStack.scale(0.7f, 0.7f, 0.7f);
        state.item.submit(poseStack, collector, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
