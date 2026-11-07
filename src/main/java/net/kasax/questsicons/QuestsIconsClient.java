package net.kasax.questsicons;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.render.DreamStarPedestalRenderer;

public class QuestsIconsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientQuests.init();
        BlockEntityRenderers.register(ModBlocks.DREAM_STAR_PEDESTAL_BE, DreamStarPedestalRenderer::new);
    }
}
