package net.kasax.questsicons;

import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.gui.QuestHud;
import net.kasax.questsicons.client.render.DreamStarPedestalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client half of the NeoForge entry point (see {@link QuestsIcons}). */
@Mod(value = QuestsIcons.MOD_ID, dist = Dist.CLIENT)
public class QuestsIconsClient {
    public QuestsIconsClient(IEventBus modBus) {
        ClientQuests.init();
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientQuests.OPEN_BOOK));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(QuestsIcons.MOD_ID, "pinned"), new QuestHud()));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(ModBlocks.DREAM_STAR_PEDESTAL_BE, DreamStarPedestalRenderer::new));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientQuests.clientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientQuests.disconnected());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            Button b = ClientQuests.inventoryButton(Minecraft.getInstance(), e.getScreen());
            if (b != null) e.addListener(b);
        });
    }
}
