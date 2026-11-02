package net.kasax.questsicons.selftest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.gui.QuestBookScreen;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/** Opens the quest book in a fresh world and takes screenshots of the map, a quest and the editor. */
public class Selftest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
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
            ctx.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            ctx.waitTicks(5);
            ctx.takeScreenshot("qi_inventory_button");
            ctx.setScreen(QuestBookScreen::new);
            ctx.waitTicks(10);
            ctx.takeScreenshot("qi_book_map");
            long welcome = QuestObject.hashId("quest/welcome");
            sp.getServer().runCommand("quests complete " + QuestObject.idToString(welcome) + " @a");
            ctx.waitTicks(20);
            ctx.takeScreenshot("qi_book_completed");
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof QuestBookScreen s) s.openQuest(welcome);
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("qi_book_quest");
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof QuestBookScreen s) s.openQuest(QuestObject.hashId("quest/wood"));
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("qi_book_quest_wood");
            ctx.setScreen(QuestBookScreen::new);
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof QuestBookScreen s) s.setEditMode(true);
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("qi_book_edit");
            ctx.setScreen(() -> null);
            ctx.waitTicks(40);
            ctx.takeScreenshot("qi_after");
        }
    }
}
