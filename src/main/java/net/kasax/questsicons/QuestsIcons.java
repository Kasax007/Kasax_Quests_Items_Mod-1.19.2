package net.kasax.questsicons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.item.ModItemGroup;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.server.QuestCommands;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quest Icons: icon items for quest books, plus a complete quest book system in the spirit of
 * FTB Quests (chapters, quest maps, tasks, rewards, teams, loot crates and an in-game editor).
 */
public class QuestsIcons implements ModInitializer {
    public static final String MOD_ID = "questsicons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModBlocks.registerBlocks();
        ModItemGroup.registerItemGroups();
        // A dropped Dream Star never despawns (fire and explosions cannot hurt it either, see ModItems).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.getItem().is(ModItems.DREAM_STAR)) item.setUnlimitedLifetime();
        });
        QuestNet.register();
        QuestServer.init();
        QuestCommands.init();
    }
}
