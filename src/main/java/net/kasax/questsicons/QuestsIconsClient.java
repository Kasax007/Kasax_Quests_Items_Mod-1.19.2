package net.kasax.questsicons;

import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.gui.QuestHud;
import net.kasax.questsicons.client.render.DreamStarPedestalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** The client half of the Forge entry point (see {@link QuestsIcons}). */
public final class QuestsIconsClient {
    private QuestsIconsClient() {
    }

    static void init(IEventBus modBus) {
        ClientQuests.init();
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientQuests.OPEN_BOOK));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("pinned", new QuestHud()));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(ModBlocks.DREAM_STAR_PEDESTAL_BE, DreamStarPedestalRenderer::new));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ClientQuests.clientTick(Minecraft.getInstance());
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientQuests.disconnected());
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            Button b = ClientQuests.inventoryButton(Minecraft.getInstance(), e.getScreen());
            if (b != null) e.addListener(b);
        });
    }
}
