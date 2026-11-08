package net.kasax.questsicons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
 *
 * <p>Fabric entry point: every loader event is wired here and in {@link QuestsIconsClient}; the rest of the code is
 * the same as on Forge (QuestServer, ClientQuests and QuestNet expose plain hook methods).
 */
public class QuestsIcons implements ModInitializer {
    public static final String MOD_ID = "questsicons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** The age {@code ItemEntity.makeFakeItem()} gives the item that only shows /give's pickup animation. */
    private static final int FAKE_ITEM_AGE = 5999;

    @Override
    public void onInitialize() {
        ModBlocks.registerBlocks();
        ModItems.registerModItems();
        ModItemGroup.registerItemGroups();
        QuestNet.register();
        ServerLifecycleEvents.SERVER_STARTED.register(QuestServer::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> QuestServer.serverStopped());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s, rm, ok) -> QuestServer.dataPacksReloaded());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> QuestServer.playerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> QuestServer.playerLeft(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(s -> QuestServer.serverTick());
        ServerLivingEntityEvents.AFTER_DEATH.register(QuestServer::entityKilled);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> QuestCommands.init(dispatcher));
        // A dropped Dream Star never despawns and nothing but the void destroys it: fire and lava (it floats like
        // netherite, see ModItems), explosions, cactus and lightning (the item entity is made invulnerable). Not the fake
        // item /give spawns for its pickup animation (ItemEntity.makeFakeItem: age 5999, nobody can pick it up): made
        // permanent, it floated at the player's feet forever.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.getItem().is(ModItems.DREAM_STAR) && item.getAge() != FAKE_ITEM_AGE) {
                item.setUnlimitedLifetime();
                item.setInvulnerable(true);
            }
        });
    }
}
