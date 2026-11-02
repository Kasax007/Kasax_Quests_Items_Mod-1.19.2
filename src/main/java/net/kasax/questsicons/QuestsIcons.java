package net.kasax.questsicons;

import net.fabricmc.api.ModInitializer;
import net.kasax.questsicons.item.ModItemGroup;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.server.QuestCommands;
import net.kasax.questsicons.server.QuestServer;
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
        ModItemGroup.registerItemGroups();
        QuestNet.register();
        QuestServer.init();
        QuestCommands.init();
    }
}
