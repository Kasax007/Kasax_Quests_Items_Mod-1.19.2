package net.kasax.questsicons;

import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.item.ModItemGroup;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.server.QuestCommands;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quest Icons: icon items for quest books, plus a complete quest book system in the spirit of
 * FTB Quests (chapters, quest maps, tasks, rewards, teams, loot crates and an in-game editor).
 *
 * <p>Forge entry point: every loader event is wired here and in {@link QuestsIconsClient}; the rest of the code
 * is the same as on Fabric and NeoForge (QuestServer, ClientQuests and QuestNet expose plain hook methods).
 */
@Mod(QuestsIcons.MOD_ID)
public class QuestsIcons {
    public static final String MOD_ID = "questsicons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** The age {@code ItemEntity.makeFakeItem()} gives the item that only shows /give's pickup animation. */
    private static final int FAKE_ITEM_AGE = 5999;

    public QuestsIcons() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Blocks, items, the block entity and the creative tabs are plain Registry.register calls in static initialisers
        // (as on Fabric). Forge only accepts them while it fires RegisterEvent, so the classes are loaded in the first one.
        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey().equals(Registries.BLOCK)) {
                ModBlocks.registerBlocks();
                ModItems.registerModItems();
                ModItemGroup.registerItemGroups();
            }
        });
        modBus.addListener((FMLCommonSetupEvent e) -> QuestNet.register());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestsIconsClient.init(modBus));

        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> QuestServer.serverStarted(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> QuestServer.serverStopped());
        // After /reload (player == null: the data packs were sent to everyone).
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) QuestServer.dataPacksReloaded();
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) QuestServer.playerJoined(p);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) QuestServer.playerLeft(p);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) QuestServer.serverTick();
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (!e.isCanceled() && !e.getEntity().level().isClientSide()) QuestServer.entityKilled(e.getEntity(), e.getSource());
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> QuestCommands.init(e.getDispatcher()));
        // A dropped Dream Star never despawns and nothing but the void destroys it: fire and lava (it floats like
        // netherite, see ModItems), explosions, cactus and lightning (the item entity is made invulnerable). Not the fake
        // item /give spawns for its pickup animation (ItemEntity.makeFakeItem: age 5999, nobody can pick it up): made
        // permanent, it floated at the player's feet forever.
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof ItemEntity item && item.getItem().is(ModItems.DREAM_STAR)
                    && item.getAge() != FAKE_ITEM_AGE) {
                item.setUnlimitedLifetime();
                item.setInvulnerable(true);
            }
        });
    }
}
