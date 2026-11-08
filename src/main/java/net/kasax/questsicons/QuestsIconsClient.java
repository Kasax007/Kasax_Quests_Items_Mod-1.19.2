package net.kasax.questsicons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientNet;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.gui.QuestHud;
import net.kasax.questsicons.client.render.DreamStarPedestalRenderer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** The client half of the Fabric entry point (see {@link QuestsIcons}). */
public class QuestsIconsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientQuests.init();
        ClientNet.init();
        KeyBindingHelper.registerKeyBinding(ClientQuests.OPEN_BOOK);
        HudRenderCallback.EVENT.register(new QuestHud());
        BlockEntityRenderers.register(ModBlocks.DREAM_STAR_PEDESTAL_BE, DreamStarPedestalRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(ClientQuests::clientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> ClientQuests.disconnected());
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            Button b = ClientQuests.inventoryButton(mc, screen);
            if (b != null) Screens.getButtons(screen).add(b);
        });
    }
}
