package net.kasax.questsicons.selftest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.kasax.questsicons.block.DreamStarPedestalBlock;
import net.kasax.questsicons.block.DreamStarPedestalBlockEntity;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.gui.QuestBookScreen;
import net.kasax.questsicons.item.ModItems;
import net.kasax.questsicons.quest.ItemSpec;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestObject;
import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the quest book in a fresh world and clicks through it with real mouse clicks and keys (26.3: SDL numbering),
 * then checks the Dream Star and its pedestal. Saves screenshots and fails if a check fails.
 */
public class Selftest implements FabricClientGameTest {
    private static final BlockPos PEDESTAL = new BlockPos(0, 151, 0);
    private final List<String> failed = new ArrayList<>();
    private ClientGameTestContext ctx;

    private void check(String what, boolean ok) {
        System.out.println("[selftest] " + what + ": " + (ok ? "OK" : "FAILED"));
        if (!ok) failed.add(what);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            s.setAllowCommands(true);
        }).create()) {
            ctx.waitTicks(60);
            ctx.waitFor(mc -> ClientQuests.book != null, 600);
            ctx.takeScreenshot("qi_world");
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);
            ctx.takeScreenshot("qi_inventory_button");
            questBook(sp.getServer());
            dreamStar(sp.getServer());
        }
        if (!failed.isEmpty()) throw new AssertionError("self-test checks failed: " + failed);
        System.out.println("[selftest] all checks passed");
    }

    // ------------------------------------------------------------------------------- quest book

    private void questBook(TestServerContext server) {
        long welcome = QuestObject.hashId("quest/welcome");
        long wood = QuestObject.hashId("quest/wood");
        ctx.setScreen(QuestBookScreen::new);
        ctx.waitTicks(10);
        ctx.takeScreenshot("qi_book_map");
        // A real left click on the first quest must open its panel (26.3 reports left = 1, not 0).
        clickQuest(welcome);
        check("left click opens the quest", selected() == welcome);
        ctx.takeScreenshot("qi_click_quest");

        // The checkmark task's button, the reward's Claim button, Pin and Unpin: all real clicks.
        check("tick the checkmark task", clickLabel(tr("questsicons.gui.check")));
        ctx.waitTicks(10);
        check("ticking completes the quest", ClientQuests.progress.isQuestCompleted(welcome));
        int xpBefore = server.computeOnServer(s -> player(s).totalExperience);
        check("click Claim", clickLabel(tr("questsicons.gui.claim")));
        ctx.waitTicks(10);
        int xpAfter = server.computeOnServer(s -> player(s).totalExperience);
        check("claiming gives the 50 XP (" + xpBefore + " -> " + xpAfter + ")", xpAfter - xpBefore == 50);
        ctx.takeScreenshot("qi_book_completed");
        check("click Pin", clickLabel(tr("questsicons.gui.pin")));
        check("the quest is pinned", ClientQuests.pinned.contains(welcome));
        check("click Unpin", clickLabel(tr("questsicons.gui.unpin")));
        check("the quest is unpinned", !ClientQuests.pinned.contains(welcome));

        // Escape closes only the panel (26.3: Escape is key 41, not 256), a second Escape the book.
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        check("Escape closes the quest panel", bookOpen() && selected() == 0);
        clickQuest(welcome);
        check("click Close", clickLabel(tr("questsicons.gui.close")));
        check("Close closes the quest panel", bookOpen() && selected() == 0);

        // A "choose one" reward: the picker opens, the panel behind it does not react, the pick arrives.
        server.runOnServer(s -> {
            QuestServer qs = QuestServer.get();
            Quest q = qs.book.quest(wood);
            Rewards.ChoiceReward r = new Rewards.ChoiceReward();
            r.id = QuestObject.hashId("wood/choice");
            r.quest = q;
            r.items.add(ItemSpec.of("minecraft:diamond"));
            r.items.add(ItemSpec.of("minecraft:emerald"));
            q.rewards.add(r);
            qs.bookChanged(false);
            player(s).getInventory().clearContent();
        });
        server.runCommand("quests complete " + QuestObject.idToString(wood) + " @a");
        ctx.waitTicks(20);
        clickQuest(wood);
        float[] close = ctx.computeOnClient(mc -> screen(mc).zonePos(tr("questsicons.gui.close")));
        check("click Choose", clickLabel(tr("questsicons.gui.choose")));
        ctx.takeScreenshot("qi_choice_picker");
        if (close != null) click(close[0], close[1]);
        check("the panel behind the picker ignores clicks", selected() == wood && zone(name(Items.EMERALD)) != null);
        check("pick the emerald", clickLabel(name(Items.EMERALD)));
        ctx.waitTicks(10);
        check("the emerald arrives", server.computeOnServer(s -> player(s).getInventory().countItem(Items.EMERALD)) == 1
                && server.computeOnServer(s -> player(s).getInventory().countItem(Items.DIAMOND)) == 0);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> screen(mc).setEditMode(true));
        ctx.waitTicks(5);
        ctx.takeScreenshot("qi_book_edit");

        // Edit mode: a right click on the empty map opens the menu; a click beside it only closes it (the chapter
        // behind it must not react); "New quest here" adds a quest.
        String newQuest = tr("questsicons.edit.new_quest");
        float[] empty = ctx.computeOnClient(mc -> new float[]{mc.getWindow().getGuiScaledWidth() - 70, mc.getWindow().getGuiScaledHeight() - 60});
        float[] chapterRow = {60, 37};   // the "Getting Started" row in the chapter list
        rightClick(empty[0], empty[1]);
        check("right click opens the editor menu", zone(newQuest) != null);
        ctx.takeScreenshot("qi_edit_menu");
        click(chapterRow[0], chapterRow[1]);
        check("a click beside the menu only closes it", zone(newQuest) == null && bookOpen());
        rightClick(empty[0], empty[1]);
        check("click New quest here", clickLabel(newQuest));
        ctx.waitTicks(10);
        check("the new quest is in the book", server.computeOnServer(s -> QuestServer.get().book.chapters.getFirst().quests.size()) == 3);
        server.runOnServer(s -> {   // keep the self-test's quest files as they were
            QuestServer qs = QuestServer.get();
            qs.book.chapters.getFirst().quests.removeIf(q -> q.id != welcome && q.id != wood);
            qs.book.quest(wood).rewards.removeIf(r -> r.id == QuestObject.hashId("wood/choice"));
            qs.bookChanged(true);
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> screen(mc).setEditMode(false));
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        check("Escape with nothing open closes the book", !bookOpen());
    }

    private static String name(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static QuestBookScreen screen(Minecraft mc) {
        return (QuestBookScreen) mc.gui.screen();
    }

    private boolean bookOpen() {
        return ctx.computeOnClient(mc -> mc.gui.screen() instanceof QuestBookScreen);
    }

    private long selected() {
        return ctx.computeOnClient(mc -> mc.gui.screen() instanceof QuestBookScreen s && s.selectedQuest() != null ? s.selectedQuest().id : 0L);
    }

    private float[] zone(String text) {
        return ctx.computeOnClient(mc -> mc.gui.screen() instanceof QuestBookScreen s ? s.zonePos(text) : null);
    }

    private void clickQuest(long id) {
        float[] pos = ctx.computeOnClient(mc -> mc.gui.screen() instanceof QuestBookScreen s ? s.questScreenPos(id) : null);
        if (pos == null) throw new AssertionError("quest not on the map: " + QuestObject.idToString(id));
        click(pos[0], pos[1]);
    }

    /** Clicks the button showing this text; false if there is none. */
    private boolean clickLabel(String text) {
        float[] pos = zone(text);
        if (pos == null) return false;
        click(pos[0], pos[1]);
        return true;
    }

    /** A real right click at a GUI position. */
    private void rightClick(double gx, double gy) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gx * scale, gy * scale);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        ctx.waitTicks(5);
    }

    /** A real left click at a GUI position. */
    private void click(double gx, double gy) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(gx * scale, gy * scale);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------------------- dream star

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static DreamStarPedestalBlockEntity pedestal(MinecraftServer s) {
        return s.overworld().getBlockEntity(PEDESTAL) instanceof DreamStarPedestalBlockEntity be ? be : null;
    }

    private static boolean hasStar(MinecraftServer s) {
        DreamStarPedestalBlockEntity be = pedestal(s);
        return be != null && be.getStar().is(ModItems.DREAM_STAR) && s.overworld().getBlockState(PEDESTAL).getValue(DreamStarPedestalBlock.HAS_STAR);
    }

    private static int count(MinecraftServer s, Item item, BlockPos at, double r) {
        int n = 0;
        for (ItemEntity e : s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(r))) {
            if (e.isAlive() && e.getItem().is(item)) n++;
        }
        return n;
    }

    private void dreamStar(TestServerContext server) {
        // A small stage high up, at night, so the pedestal's light shows.
        server.runCommand("gamemode survival @a");
        server.runCommand("fill -3 150 -3 3 150 3 minecraft:polished_deepslate");
        server.runCommand("fill -3 151 -3 3 156 3 minecraft:air");
        server.runCommand("setblock 0 151 0 questsicons:dream_star_pedestal");
        server.runCommand("time set midnight");
        server.runCommand("weather clear");
        server.runCommand("tp @a 0.5 151 -2.5 0 25");
        server.runCommand("clear @a");
        server.runCommand("give @a questsicons:dream_star");
        ctx.waitTicks(20);
        server.runOnServer(s -> player(s).getInventory().setSelectedSlot(0));
        // /give shows its pickup animation with a fake item that must vanish (it once stayed forever).
        check("no ghost star after /give", server.computeOnServer(s -> count(s, ModItems.DREAM_STAR, player(s).blockPosition(), 4)) == 0);
        check("the star is a unique, fire-proof trophy", server.computeOnServer(s -> {
            ItemStack star = player(s).getMainHandItem();
            ServerLevel level = s.overworld();
            return star.is(ModItems.DREAM_STAR) && star.getMaxStackSize() == 1 && star.hasFoil()
                    && !star.canBeHurtBy(level.damageSources().lava()) && !star.canBeHurtBy(level.damageSources().explosion(null, null))
                    && star.canBeHurtBy(level.damageSources().fellOutOfWorld());
        }));
        check("getting the star grants the advancement", server.computeOnServer(s -> {
            AdvancementHolder h = s.getAdvancements().get(Identifier.fromNamespaceAndPath("questsicons", "dream_star"));
            return h != null && player(s).getAdvancements().getOrStartProgress(h).isDone();
        }));
        ctx.getInput().lookAt(PEDESTAL);
        ctx.waitTicks(5);

        // Right-click with the star puts it on display; the pedestal lights up.
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        check("right-click with the star puts it on the pedestal", server.computeOnServer(s -> hasStar(s) && player(s).getMainHandItem().isEmpty()));
        check("the pedestal gives light 15", server.computeOnServer(s -> s.overworld().getBrightness(LightLayer.BLOCK, PEDESTAL.above()) >= 14));
        check("the client sees the star", ctx.computeOnClient(mc -> mc.level.getBlockEntity(PEDESTAL) instanceof DreamStarPedestalBlockEntity be
                && be.getStar().is(ModItems.DREAM_STAR)));
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        ctx.waitTicks(60);
        ctx.takeScreenshot("qi_pedestal_star");
        server.runCommand("tp @a 2.2 151 -1.8 50 20");
        ctx.waitTicks(30);
        ctx.takeScreenshot("qi_pedestal_star_side");
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        server.runCommand("tp @a 0.5 151 -2.5 0 25");
        ctx.waitTicks(5);
        ctx.getInput().lookAt(PEDESTAL);
        ctx.waitTicks(5);

        // Right-click with an empty hand takes it back.
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        check("right-click with an empty hand takes the star back", server.computeOnServer(s -> !hasStar(s)
                && player(s).getMainHandItem().is(ModItems.DREAM_STAR)
                && !s.overworld().getBlockState(PEDESTAL).getValue(DreamStarPedestalBlock.HAS_STAR)));
        check("the light goes out", server.computeOnServer(s -> s.overworld().getBrightness(LightLayer.BLOCK, PEDESTAL.above()) == 0));

        // The tooltip, in the real inventory.
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(5);
        int[] slot = ctx.computeOnClient(mc -> new int[]{(mc.getWindow().getGuiScaledWidth() - 176) / 2 + 8 + 8,
                (mc.getWindow().getGuiScaledHeight() - 166) / 2 + 142 + 8});
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(slot[0] * scale, slot[1] * scale);
        ctx.waitTicks(5);
        ctx.takeScreenshot("qi_star_tooltip");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);

        // Breaking the pedestal drops the star and the pedestal.
        ctx.getInput().lookAt(PEDESTAL);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(10);
        check("the star is back on the pedestal", server.computeOnServer(Selftest::hasStar));
        server.runCommand("setblock 0 151 0 minecraft:air destroy");
        ctx.waitTicks(10);
        String drops = server.computeOnServer(s -> count(s, ModItems.DREAM_STAR, PEDESTAL, 3) + " star(s), "
                + count(s, ModBlocks.DREAM_STAR_PEDESTAL_ITEM, PEDESTAL, 3) + " pedestal(s)");
        check("breaking the pedestal drops the star and the pedestal (" + drops + ")", drops.equals("1 star(s), 1 pedestal(s)"));
        check("a dropped star never despawns", server.computeOnServer(s -> {
            for (ItemEntity e : s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(PEDESTAL).inflate(3))) {
                if (e.getItem().is(ModItems.DREAM_STAR)) return e.getAge() == -32768;
            }
            return false;
        }));
        server.runCommand("kill @e[type=item]");

        // Lava and TNT: the star survives, a stick (the control) does not.
        BlockPos lava = new BlockPos(20, 151, 20);
        server.runCommand("fill 19 150 19 21 152 21 minecraft:glass");
        server.runCommand("fill 20 151 20 20 152 20 minecraft:lava");
        server.runCommand("summon item 20.5 153.5 20.5 {Item:{id:\"questsicons:dream_star\",count:1}}");
        server.runCommand("summon item 20.5 153.5 20.5 {Item:{id:\"minecraft:stick\",count:1}}");
        BlockPos tnt = new BlockPos(-20, 151, -20);
        server.runCommand("fill -23 150 -23 -17 150 -17 minecraft:obsidian");
        server.runCommand("summon item -19.5 151 -19.5 {Item:{id:\"questsicons:dream_star\",count:1}}");
        server.runCommand("summon item -19.5 151 -19.5 {Item:{id:\"minecraft:stick\",count:1}}");
        server.runCommand("summon tnt -19.5 151 -19.5 {fuse:10}");
        ctx.waitTicks(100);
        check("the star floats in lava", server.computeOnServer(s -> count(s, ModItems.DREAM_STAR, lava, 3) == 1));
        check("a stick burns in the same lava", server.computeOnServer(s -> count(s, Items.STICK, lava, 3) == 0));
        check("the star survives TNT", server.computeOnServer(s -> count(s, ModItems.DREAM_STAR, tnt, 12) == 1));
        check("a stick does not survive the same TNT", server.computeOnServer(s -> count(s, Items.STICK, tnt, 12) == 0));

        // Recipes: without the Dreamcraft mods only the vanilla fallback is loaded, and both patterns craft.
        check("Dreamcraft recipe is off without the Dreamcraft mods", server.computeOnServer(s -> !hasRecipe(s, "dream_star")));
        check("the vanilla fallback recipe is loaded", server.computeOnServer(s -> hasRecipe(s, "dream_star_vanilla")));
        check("the vanilla fallback crafts the star", server.computeOnServer(s -> crafts(s, ModItems.DREAM_STAR,
                Items.TOTEM_OF_UNDYING, Items.DRAGON_HEAD, Items.TOTEM_OF_UNDYING,
                Items.NETHERITE_BLOCK, Items.NETHER_STAR, Items.NETHERITE_BLOCK,
                Items.ENCHANTED_GOLDEN_APPLE, Items.HEAVY_CORE, Items.ENCHANTED_GOLDEN_APPLE)));
        check("the pedestal recipe crafts the pedestal", server.computeOnServer(s -> crafts(s, ModBlocks.DREAM_STAR_PEDESTAL_ITEM,
                Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.GOLD_INGOT,
                Items.AIR, Items.QUARTZ_PILLAR, Items.AIR,
                Items.POLISHED_BLACKSTONE, Items.POLISHED_BLACKSTONE, Items.POLISHED_BLACKSTONE)));
        server.runCommand("time set day");
    }

    private static boolean hasRecipe(MinecraftServer s, String path) {
        return s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("questsicons", path))).isPresent();
    }

    private static boolean crafts(MinecraftServer s, Item result, Item... grid) {
        List<ItemStack> items = new ArrayList<>();
        for (Item i : grid) items.add(i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i));
        CraftingInput input = CraftingInput.of(3, 3, items);
        return s.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, s.overworld())
                .map(h -> h.value().assemble(input).is(result)).orElse(false);
    }
}
