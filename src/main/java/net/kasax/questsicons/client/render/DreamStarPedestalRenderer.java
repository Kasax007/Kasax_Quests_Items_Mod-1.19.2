package net.kasax.questsicons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.questsicons.block.DreamStarPedestalBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The star on the pedestal: floats, bobs gently, turns once every twelve seconds and is drawn at full brightness. */
@Environment(EnvType.CLIENT)
public class DreamStarPedestalRenderer implements BlockEntityRenderer<DreamStarPedestalBlockEntity> {
    /** Seamless loop: 30 turns (240 ticks each) and 90 bobs (80 ticks each). */
    private static final long LOOP = 7200;
    private final ItemRenderer itemRenderer;

    public DreamStarPedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(DreamStarPedestalBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        ItemStack star = be.getStar();
        if (star.isEmpty() || be.getLevel() == null) return;
        float time = (be.getLevel().getGameTime() % LOOP) + partialTick;
        poseStack.pushPose();
        float bob = Mth.sin(time * Mth.TWO_PI / 80f) * 0.05f;
        poseStack.translate(0.5f, 15f / 16f + 0.45f + bob, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.5f));
        poseStack.scale(0.7f, 0.7f, 0.7f);
        this.itemRenderer.renderStatic(star, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                be.getLevel(), (int) be.getBlockPos().asLong());
        poseStack.popPose();
    }
}
