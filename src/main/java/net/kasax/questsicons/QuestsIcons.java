package net.kasax.questsicons;

import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.item.ModItemGroup;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.server.QuestCommands;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quest Icons: icon items for quest books, plus a complete quest book system in the spirit of
 * FTB Quests (chapters, quest maps, tasks, rewards, teams, loot crates and an in-game editor).
 */
@Mod(QuestsIcons.MOD_ID)
public class QuestsIcons {
    public static final String MOD_ID = "questsicons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** The age {@code ItemEntity.makeFakeItem()} gives the item that only shows /give's pickup animation. */
    private static final int FAKE_ITEM_AGE = 5999;

    /**
     * NeoForge entry point: every loader event is wired here and in {@link QuestsIconsClient}; the rest of the code is
     * the same as on Fabric (QuestServer, ClientQuests and QuestNet expose plain hook methods).
     */
    public QuestsIcons(IEventBus modBus) {
        // Items, blocks, the block entity and the creative tabs are plain Registry.register calls in static initialisers
        // (as on Fabric). NeoForge only accepts them while it fires RegisterEvent, so the classes are loaded in the first one.
        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey().equals(Registries.BLOCK)) {
                ModItems.registerModItems();
                ModBlocks.registerBlocks();
                ModItemGroup.registerItemGroups();
            }
        });
        modBus.addListener(QuestNet::register);

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> QuestServer.serverStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> QuestServer.serverStopped());
        // After /reload (player == null: the data packs were sent to everyone).
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) QuestServer.dataPacksReloaded();
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) QuestServer.playerJoined(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> QuestServer.serverTick());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (!e.isCanceled() && !e.getEntity().level().isClientSide()) QuestServer.entityKilled(e.getEntity(), e.getSource());
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> QuestCommands.init(e.getDispatcher()));
        // A dropped Dream Star never despawns (fire and explosions cannot hurt it either, see ModItems). Not the fake
        // item /give spawns for its pickup animation (ItemEntity.makeFakeItem: age 5999, nobody can pick it up): made
        // permanent, it floated at the player's feet forever.
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof ItemEntity item && item.getItem().is(ModItems.DREAM_STAR)
                    && item.getAge() != FAKE_ITEM_AGE) {
                item.setUnlimitedLifetime();
            }
        });
    }
}
