package net.kasax.questsicons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.render.DreamStarPedestalRenderer;

public class QuestsIconsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientQuests.init();
        BlockEntityRendererRegistry.register(ModBlocks.DREAM_STAR_PEDESTAL_BE, DreamStarPedestalRenderer::new);
    }
}
