package net.kasax.questsicons.selftest;

import com.mojang.blaze3d.platform.InputConstants;
import net.kasax.questsicons.selftest.harness.FabricClientGameTest;
import net.kasax.questsicons.selftest.harness.ClientGameTestContext;
import net.kasax.questsicons.selftest.harness.TestDedicatedServerConnection;
import net.kasax.questsicons.selftest.harness.TestDedicatedServerContext;
import net.kasax.questsicons.selftest.harness.TestServerContext;
import net.kasax.questsicons.selftest.harness.TestSingleplayerContext;
import net.neoforged.fml.loading.FMLPaths;
import net.kasax.questsicons.importer.ImportReport;
import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.task.Tasks;
import net.kasax.questsicons.server.FtbImportCommand;
import net.kasax.questsicons.block.DreamStarPedestalBlock;
import net.kasax.questsicons.block.DreamStarPedestalBlockEntity;
import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.client.gui.FormScreen;
import net.kasax.questsicons.client.gui.PickerScreen;
import net.kasax.questsicons.client.gui.QiScreen;
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
import net.minecraft.resources.ResourceLocation;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Opens the quest book in a fresh world and clicks through it with real mouse clicks and keys (1.21.1: GLFW numbering),
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
            mc.resizeDisplay();
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
            editor(sp.getServer());
            dreamStar(sp.getServer());
            ftbImport(sp.getServer());
        }
        ftbImportOnDedicatedServer();
        editorOnDedicatedServer();
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
        // A real left click on the first quest must open its panel (1.21.1 reports left = 0 (GLFW); 26.3 reports 1).
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

        // Escape closes only the panel (1.21.1: Escape is key 256 (GLFW)), a second Escape the book.
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
        check("the new quest's form opens; Save", press(tr("questsicons.edit.save")));
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

    // ------------------------------------------------------------------------------- in-game editor

    private boolean onScreen(Class<?> type) {
        return ctx.computeOnClient(mc -> type.isInstance(mc.screen));
    }

    private float[] findZone(String text) {
        return ctx.computeOnClient(mc -> mc.screen instanceof QuestBookScreen s ? s.zonePos(text)
                : mc.screen instanceof QiScreen s ? s.zonePos(text) : null);
    }

    /** Scrolls a long form or list with the mouse wheel until the condition holds (down first, then up). */
    private boolean scrollUntil(java.util.function.BooleanSupplier found) {
        if (found.getAsBoolean()) return true;
        if (!onScreen(QiScreen.class)) return false;
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        float[] c = ctx.computeOnClient(mc -> new float[]{mc.getWindow().getGuiScaledWidth() / 2f, mc.getWindow().getGuiScaledHeight() / 2f});
        ctx.getInput().setCursorPos(c[0] * scale, c[1] * scale);
        for (int dir : new int[]{-1, 1}) {
            for (int i = 0; i < 25; i++) {
                ctx.getInput().scroll(dir);
                ctx.waitTicks(2);
                if (found.getAsBoolean()) return true;
            }
        }
        return false;
    }

    /** Clicks a button, menu entry, tab or picker cell (by text, tooltip or value) on whatever screen is open. */
    private boolean press(String text) {
        scrollUntil(() -> findZone(text) != null);
        float[] pos = findZone(text);
        if (pos == null) {
            System.out.println("[selftest] no button '" + text + "' on " + ctx.computeOnClient(mc -> String.valueOf(mc.screen)));
            return false;
        }
        click(pos[0], pos[1]);
        return true;
    }

    /** Clicks into a form's text field (by its label), selects everything and types the new text. */
    private boolean type(String fieldLabel, String text) {
        scrollUntil(() -> ctx.computeOnClient(mc -> mc.screen instanceof FormScreen f && f.fieldPos(fieldLabel) != null));
        float[] pos = ctx.computeOnClient(mc -> mc.screen instanceof FormScreen f ? f.fieldPos(fieldLabel) : null);
        if (pos == null) {
            System.out.println("[selftest] no field '" + fieldLabel + "'");
            return false;
        }
        click(pos[0], pos[1]);
        // Empty the field like a player would: End, then Backspace for every character.
        int len = ctx.computeOnClient(mc -> mc.screen instanceof FormScreen f ? f.fieldText(fieldLabel).length() : 0);
        ctx.getInput().pressKey(InputConstants.KEY_END);
        for (int i = 0; i < len; i++) ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
        ctx.getInput().typeChars(text);
        ctx.waitTicks(3);
        return true;
    }

    private void typeSearch(String text) {
        ctx.getInput().typeChars(text);
        ctx.waitTicks(3);
    }

    private float[] map(double x, double y) {
        return ctx.computeOnClient(mc -> screen(mc).mapPos(x, y));
    }

    private void rightClickMap(double x, double y) {
        float[] p = map(x, y);
        rightClick(p[0], p[1]);
    }

    private Chapter serverChapter(TestServerContext server, long id) {
        return server.computeOnServer(s -> QuestServer.get().book.chapter(id));
    }

    private Quest serverQuest(TestServerContext server, String title) {
        return server.computeOnServer(s -> {
            for (Quest q : QuestServer.get().book.allQuests()) if (q.title.equals(title)) return q;
            return null;
        });
    }

    /** Drags with the left mouse button from one GUI position to another (optionally with Shift held). */
    private void drag(float[] from, float[] to, boolean shift) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        if (shift) ctx.getInput().holdShift();
        ctx.getInput().setCursorPos(from[0] * scale, from[1] * scale);
        ctx.waitTicks(2);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        for (int i = 1; i <= 5; i++) {
            ctx.getInput().setCursorPos((from[0] + (to[0] - from[0]) * i / 5.0) * scale, (from[1] + (to[1] - from[1]) * i / 5.0) * scale);
            ctx.waitTicks(1);
        }
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        if (shift) ctx.getInput().releaseShift();
        ctx.waitTicks(10);
    }

    /**
     * Builds a small chapter only with the editor and real clicks: a chapter, three quests (item, kill and checkmark
     * tasks; item, XP and command rewards), a library icon, links, a move, copy and paste, delete, undo, a picture,
     * a reward table and a group; then reloads the files and checks that everything is still there.
     */
    private void editor(TestServerContext server) {
        ctx.setScreen(QuestBookScreen::new);
        ctx.waitTicks(10);
        check("editor: click Edit", press(tr("questsicons.edit.off")));
        check("editor: edit mode is on", ctx.computeOnClient(mc -> screen(mc).editMode()));
        int chaptersBefore = server.computeOnServer(s -> QuestServer.get().book.chapters.size());
        check("editor: click + Chapter", press(tr("questsicons.edit.new_chapter")));
        ctx.waitTicks(15);
        check("editor: the new chapter exists and is shown", server.computeOnServer(s -> QuestServer.get().book.chapters.size()) == chaptersBefore + 1
                && ctx.computeOnClient(mc -> screen(mc).selectedChapter() != null && screen(mc).selectedChapter().quests.isEmpty()));
        long chapterId = ctx.computeOnClient(mc -> screen(mc).selectedChapter().id);
        // Chapter settings through the form.
        float[] row = ctx.computeOnClient(mc -> screen(mc).rowPos(chapterId));
        rightClick(row[0], row[1]);
        check("editor: chapter menu -> Chapter settings", press(tr("questsicons.edit.chapter_settings")));
        check("editor: the chapter form is open", onScreen(FormScreen.class));
        type(tr("questsicons.form.title"), "Iron Age");
        ctx.takeScreenshot("qi_editor_chapter_form");
        check("editor: save the chapter", press(tr("questsicons.edit.save")));
        ctx.waitTicks(10);
        check("editor: the chapter is renamed", "Iron Age".equals(serverChapter(server, chapterId).title));

        // Quest 1: item task (4 iron ingots from the item picker) with an item reward (diamond) and 100 XP.
        rightClickMap(0, 0);
        check("editor: map menu -> New quest here", press(tr("questsicons.edit.new_quest")));
        check("editor: the quest form is open", onScreen(FormScreen.class));
        type(tr("questsicons.form.title"), "Get Iron");
        check("editor: + Add task", press(tr("questsicons.form.add_task")));
        check("editor: pick task type Item", press("item"));
        check("editor: + Add item", press(tr("questsicons.form.add_item")));
        check("editor: the item picker is open", onScreen(PickerScreen.class));
        check("editor: picker tab All items", press("tab:items"));
        typeSearch("iron ingot");
        ctx.takeScreenshot("qi_editor_item_picker");
        check("editor: pick the iron ingot", press("minecraft:iron_ingot"));
        type(tr("questsicons.form.count"), "4");
        ctx.takeScreenshot("qi_editor_task_form");
        check("editor: save the task", press(tr("questsicons.edit.save")));
        check("editor: + Add reward", press(tr("questsicons.form.add_reward")));
        check("editor: pick reward type Item", press("item"));
        check("editor: reward item Pick", press(tr("questsicons.form.pick")));
        check("editor: picker tab All items (reward)", press("tab:items"));
        typeSearch("diamond");
        check("editor: pick the diamond", press("minecraft:diamond"));
        check("editor: save the item reward", press(tr("questsicons.edit.save")));
        check("editor: + Add reward (XP)", press(tr("questsicons.form.add_reward")));
        check("editor: pick reward type XP", press("xp"));
        check("editor: save the XP reward", press(tr("questsicons.edit.save")));
        ctx.takeScreenshot("qi_editor_quest_form");
        check("editor: save the quest", press(tr("questsicons.edit.save")));
        ctx.waitTicks(10);
        Quest q1 = serverQuest(server, "Get Iron");
        check("editor: quest 1 has 4 iron ingots and diamond + 100 XP", q1 != null && q1.tasks.size() == 1
                && q1.tasks.getFirst() instanceof Tasks.ItemTask it && it.count == 4 && it.item.toString().contains("iron_ingot")
                && q1.rewards.size() == 2 && q1.rewards.get(0) instanceof Rewards.ItemReward ir && ir.item.toString().contains("diamond")
                && q1.rewards.get(1) instanceof Rewards.XpReward xr && xr.xp == 100);

        // Quest 2: kill task (a zombie by default) and a command reward.
        rightClickMap(3, 0);
        press(tr("questsicons.edit.new_quest"));
        type(tr("questsicons.form.title"), "Zombie Hunter");
        press(tr("questsicons.form.add_task"));
        press("kill");
        type(tr("questsicons.form.entity"), "minecraft:zombiee");
        press(tr("questsicons.edit.save"));
        check("editor: a typo in the mob is refused (the form stays open)", onScreen(FormScreen.class)
                && ctx.computeOnClient(mc -> mc.screen instanceof FormScreen f && !f.valid()));
        ctx.takeScreenshot("qi_editor_validation");
        type(tr("questsicons.form.entity"), "minecraft:zombie");
        type(tr("questsicons.form.amount"), "3");
        check("editor: save the kill task", press(tr("questsicons.edit.save")));
        press(tr("questsicons.form.add_reward"));
        press("command");
        check("editor: save the command reward", press(tr("questsicons.edit.save")));
        check("editor: save quest 2", press(tr("questsicons.edit.save")));
        ctx.waitTicks(10);
        Quest q2 = serverQuest(server, "Zombie Hunter");
        check("editor: quest 2 has a kill task (3 zombies) and a command reward", q2 != null && q2.tasks.size() == 1
                && q2.tasks.getFirst() instanceof Tasks.KillTask kt && kt.value == 3 && kt.entity.equals("minecraft:zombie")
                && q2.rewards.size() == 1 && q2.rewards.getFirst() instanceof Rewards.CommandReward);

        // Quest 3: checkmark task.
        rightClickMap(6, 0);
        press(tr("questsicons.edit.new_quest"));
        type(tr("questsicons.form.title"), "Report Back");
        press(tr("questsicons.form.add_task"));
        press("checkmark");
        press(tr("questsicons.edit.save"));
        check("editor: save quest 3", press(tr("questsicons.edit.save")));
        ctx.waitTicks(10);
        Quest q3 = serverQuest(server, "Report Back");
        check("editor: quest 3 has a checkmark task", q3 != null && q3.tasks.size() == 1 && q3.tasks.getFirst() instanceof Tasks.CheckmarkTask);
        if (q1 == null || q2 == null || q3 == null) return;

        // The icon picker: library first, by category.
        float[] p3 = ctx.computeOnClient(mc -> screen(mc).questScreenPos(q3.id));
        rightClick(p3[0], p3[1]);
        check("editor: quest menu -> Choose icon", press(tr("questsicons.edit.choose_icon")));
        check("editor: the icon picker is open", onScreen(PickerScreen.class));
        check("editor: icon picker tab Letters", press("tab:letters"));
        ctx.takeScreenshot("qi_editor_icon_picker");
        check("editor: pick Letter R", press("questsicons:letter_r"));
        ctx.waitTicks(10);
        check("editor: quest 3 shows the letter icon", server.computeOnServer(s -> QuestServer.get().book.quest(q3.id).icon.toString().contains("letter_r")));

        // Links: shift-drag from quest 1 to quest 2; the menu's "Add dependency" from quest 3 to quest 2.
        float[] p1 = ctx.computeOnClient(mc -> screen(mc).questScreenPos(q1.id));
        float[] p2 = ctx.computeOnClient(mc -> screen(mc).questScreenPos(q2.id));
        drag(p1, p2, true);
        check("editor: shift-drag links quest 2 to quest 1", server.computeOnServer(s -> QuestServer.get().book.quest(q2.id).dependencies.contains(q1.id)));
        rightClick(p3[0], p3[1]);
        press(tr("questsicons.edit.link"));
        click(p2[0], p2[1]);
        ctx.waitTicks(10);
        check("editor: link from the menu (quest 3 needs quest 2)", server.computeOnServer(s -> QuestServer.get().book.quest(q3.id).dependencies.contains(q2.id)));

        // Move: drag quest 3 down two steps.
        drag(p3, map(6, 2), false);
        check("editor: dragging moves quest 3 to 6, 2", server.computeOnServer(s -> {
            Quest q = QuestServer.get().book.quest(q3.id);
            return q.x == 6 && q.y == 2;
        }));
        ctx.takeScreenshot("qi_editor_chapter_built");

        // Copy and paste, delete with confirmation, undo.
        rightClick(p1[0], p1[1]);
        check("editor: quest menu -> Copy", press(tr("questsicons.edit.copy")));
        rightClickMap(0, 3);
        check("editor: map menu -> Paste", press(Component.translatable("questsicons.edit.paste", 1).getString()));
        ctx.waitTicks(10);
        check("editor: the pasted quest is a new quest", server.computeOnServer(s -> serverQuestsIn(s, chapterId)) == 4);
        long pasted = server.computeOnServer(s -> {
            for (Quest q : QuestServer.get().book.chapter(chapterId).quests) if (q.y == 3) return q.id;
            return 0L;
        });
        float[] pp = ctx.computeOnClient(mc -> screen(mc).questScreenPos(pasted));
        check("editor: the copy is placed where it was pasted", pp != null);
        if (pp != null) {
            rightClick(pp[0], pp[1]);
            check("editor: quest menu -> Delete", press(tr("questsicons.edit.delete")));
            check("editor: Delete asks again", press(tr("questsicons.edit.confirm_delete")));
            ctx.waitTicks(10);
            check("editor: the copy is deleted", server.computeOnServer(s -> serverQuestsIn(s, chapterId)) == 3);
            check("editor: Undo", press(tr("questsicons.edit.undo")));
            ctx.waitTicks(10);
            check("editor: undo brings the deleted quest back", server.computeOnServer(s -> serverQuestsIn(s, chapterId)) == 4
                    && server.computeOnServer(s -> QuestServer.get().book.quest(pasted) != null));
            press(tr("questsicons.edit.undo"));
            ctx.waitTicks(10);
            check("editor: a second undo takes the paste back", server.computeOnServer(s -> serverQuestsIn(s, chapterId)) == 3);
        }

        // A click on a line removes the link (after a confirmation in the menu).
        float[] mid = {(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2};
        click(mid[0], mid[1]);
        check("editor: click on a line -> Remove link", press(Component.translatable("questsicons.edit.remove_link", "Get Iron", "Zombie Hunter").getString()));
        ctx.waitTicks(10);
        check("editor: the link is gone", server.computeOnServer(s -> !QuestServer.get().book.quest(q2.id).dependencies.contains(q1.id)));
        press(tr("questsicons.edit.undo"));
        ctx.waitTicks(10);
        check("editor: undo puts the link back", server.computeOnServer(s -> QuestServer.get().book.quest(q2.id).dependencies.contains(q1.id)));

        // A picture from the library, a reward table and a chapter group.
        rightClickMap(3, -3);
        check("editor: map menu -> Add picture", press(tr("questsicons.edit.add_image")));
        check("editor: the picture picker is open", onScreen(PickerScreen.class));
        ctx.takeScreenshot("qi_editor_image_picker");
        check("editor: pick the START banner", press("questsicons:textures/gui/map/banner_start.png"));
        check("editor: save the picture", press(tr("questsicons.edit.save")));
        ctx.waitTicks(10);
        check("editor: the chapter has the picture", server.computeOnServer(s -> QuestServer.get().book.chapter(chapterId).images.size()) == 1);
        check("editor: Tables", press(tr("questsicons.edit.tables")));
        check("editor: create the common crate table", press(tr("questsicons.tables.create")));
        check("editor: + Add entry", press(tr("questsicons.form.add_entry")));
        press(tr("questsicons.form.pick"));
        press("tab:items");
        typeSearch("emerald");
        check("editor: pick an emerald for the table", press("minecraft:emerald"));
        type(tr("questsicons.form.count"), "3");
        check("editor: save the entry", press(tr("questsicons.edit.save")));
        check("editor: save the table", press(tr("questsicons.edit.save")));
        ctx.waitTicks(10);
        check("editor: the table is in the book", server.computeOnServer(s -> {
            var t = QuestServer.get().book.rewardTables.get("common_crate");
            return t != null && t.entries.size() == 1 && t.entries.getFirst().count == 3;
        }));
        ctx.takeScreenshot("qi_editor_tables");
        press(tr("gui.done"));
        int groups = server.computeOnServer(s -> QuestServer.get().book.groups.size());
        check("editor: + Group", press(tr("questsicons.edit.new_group")));
        ctx.waitTicks(10);
        check("editor: the group exists", server.computeOnServer(s -> QuestServer.get().book.groups.size()) == groups + 1);

        // The files: reload them from disk and check that the chapter came back the same.
        String before = server.computeOnServer(s -> QuestServer.get().book.chapter(chapterId).write().toString());
        server.runCommand("quests reload");
        ctx.waitTicks(20);
        String after = server.computeOnServer(s -> QuestServer.get().book.chapter(chapterId) == null ? "" : QuestServer.get().book.chapter(chapterId).write().toString());
        check("editor: the chapter reloads from its SNBT file unchanged", before.equals(after));
        check("editor: the client has the edited chapter", ctx.computeOnClient(mc -> ClientQuests.book.chapter(chapterId) != null
                && ClientQuests.book.chapter(chapterId).quests.size() == 3));
        ctx.takeScreenshot("qi_editor_done");

        // Clean up: remove what the editor test added, so the next tests start from the starter book.
        server.runOnServer(s -> {
            QuestServer qs = QuestServer.get();
            qs.book.chapters.removeIf(c -> c.id == chapterId);
            qs.book.rewardTables.remove("common_crate");
            if (qs.book.groups.size() > groups) qs.book.groups.removeLast();
            qs.bookChanged(true);
        });
        ctx.runOnClient(mc -> screen(mc).setEditMode(false));
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(5);
    }

    private static int serverQuestsIn(MinecraftServer s, long chapterId) {
        Chapter c = QuestServer.get().book.chapter(chapterId);
        return c == null ? -1 : c.quests.size();
    }

    /** The editor on a dedicated server: an operator's edits are saved by the server and reach the client live. */
    private void editorOnDedicatedServer() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        try {
            Files.writeString(gameDir.resolve("eula.txt"), "eula=true\n");
        } catch (IOException e) {
            check("eula: " + e, false);
            return;
        }
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
            try (TestDedicatedServerConnection connection = server.connect()) {
                ctx.waitFor(mc -> ClientQuests.book != null, 1200);
                String name = ctx.computeOnClient(mc -> mc.player.getGameProfile().getName());
                server.runCommand("op " + name);
                ctx.waitFor(mc -> ClientQuests.canEdit, 600);
                check("dedicated server: the operator may edit", ctx.computeOnClient(mc -> ClientQuests.canEdit));
                ctx.setScreen(QuestBookScreen::new);
                ctx.waitTicks(10);
                press(tr("questsicons.edit.off"));
                int chapters = server.computeOnServer(s -> QuestServer.get().book.chapters.size());
                check("dedicated server: + Chapter", press(tr("questsicons.edit.new_chapter")));
                ctx.waitFor(mc -> ClientQuests.book.chapters.size() == chapters + 1, 200);
                check("dedicated server: the new chapter is on the server and back on the client",
                        server.computeOnServer(s -> QuestServer.get().book.chapters.size()) == chapters + 1
                                && ctx.computeOnClient(mc -> ClientQuests.book.chapters.size()) == chapters + 1);
                ctx.waitTicks(10);
                rightClickMap(0, 0);
                press(tr("questsicons.edit.new_quest"));
                type(tr("questsicons.form.title"), "Server Quest");
                press(tr("questsicons.form.add_task"));
                press("checkmark");
                press(tr("questsicons.edit.save"));
                check("dedicated server: save the quest", press(tr("questsicons.edit.save")));
                ctx.waitFor(mc -> {
                    for (Quest q : ClientQuests.book.allQuests()) if (q.title.equals("Server Quest")) return true;
                    return false;
                }, 200);
                check("dedicated server: the quest is saved on the server", server.computeOnServer(s -> {
                    for (Quest q : QuestServer.get().book.allQuests()) if (q.title.equals("Server Quest")) return true;
                    return false;
                }));
                check("dedicated server: the quest file is written", server.computeOnServer(s -> {
                    try (Stream<Path> files = Files.list(QuestServer.DIR.resolve("chapters"))) {
                        for (Path f : files.toList()) if (Files.readString(f).contains("Server Quest")) return true;
                    } catch (IOException e) {
                        return false;
                    }
                    return false;
                }));
                ctx.takeScreenshot("qi_server_editor");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitTicks(3);
            }
            server.runOnServer(s -> {
                try {
                    deleteTree(QuestServer.DIR);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                QuestServer.get().load();
            });
        }
    }

    private static String name(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static QuestBookScreen screen(Minecraft mc) {
        return (QuestBookScreen) mc.screen;
    }

    private boolean bookOpen() {
        return ctx.computeOnClient(mc -> mc.screen instanceof QuestBookScreen);
    }

    private long selected() {
        return ctx.computeOnClient(mc -> mc.screen instanceof QuestBookScreen s && s.selectedQuest() != null ? s.selectedQuest().id : 0L);
    }

    private float[] zone(String text) {
        return ctx.computeOnClient(mc -> mc.screen instanceof QuestBookScreen s ? s.zonePos(text) : null);
    }

    private void clickQuest(long id) {
        float[] pos = ctx.computeOnClient(mc -> mc.screen instanceof QuestBookScreen s ? s.questScreenPos(id) : null);
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

    // ------------------------------------------------------------------------------- FTB Quests import

    /**
     * Puts an FTB Quests book where a modpack has it (config/ftbquests/quests), previews and runs
     * "/quests import ftbquests", then opens the imported book and finishes a quest with real clicks. The book is
     * -Dqi.selftestFtb (default: the JSON5 test book of the converter tests). Afterwards the starter book is restored.
     */
    private void ftbImport(TestServerContext server) {
        Path sample = Path.of(System.getProperty("qi.selftestFtb", ""));
        if (!Files.isDirectory(sample)) {
            check("FTB Quests sample folder exists (" + sample + ")", false);
            return;
        }
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path target = gameDir.resolve("config/ftbquests/quests");
        try {
            deleteTree(gameDir.resolve("config/ftbquests"));
            copyTree(sample, target);
        } catch (IOException e) {
            check("copy the FTB Quests sample: " + e, false);
            return;
        }
        server.runCommand("gamemode survival @a");
        int before = server.computeOnServer(s -> QuestServer.get().book.questCount());

        FtbImportCommand.last = null;
        server.runCommand("quests import ftbquests preview");
        ctx.waitFor(mc -> FtbImportCommand.last != null, 6000);
        FtbImportCommand.Finished preview = FtbImportCommand.last;
        int quests = preview.report().source("Quests");
        check("preview reads the FTB book (" + preview.report().source("Chapters") + " chapters, " + quests + " quests)", preview.ok() && quests > 0);
        check("preview changes nothing", server.computeOnServer(s -> QuestServer.get().book.questCount()) == before);
        check("preview writes the report", Files.isRegularFile(FtbImportCommand.reportFile()));

        FtbImportCommand.last = null;
        server.runCommand("quests import ftbquests run");
        ctx.waitFor(mc -> FtbImportCommand.last != null, 6000);
        FtbImportCommand.Finished run = FtbImportCommand.last;
        check("run imports every quest (" + quests + ")", run.ok() && server.computeOnServer(s -> QuestServer.get().book.questCount()) == quests);
        check("run backs up the old book", run.backup() != null && Files.isRegularFile(run.backup().resolve("data.snbt")));
        ctx.waitFor(mc -> ClientQuests.book != null && ClientQuests.book.questCount() == quests, 1200);
        check("the client gets the imported book", ctx.computeOnClient(mc -> ClientQuests.book.questCount()) == quests);
        System.out.println("[selftest] imported: " + run.report().result + "; checkmarks " + run.report().count(ImportReport.Kind.CHECKMARK)
                + ", skipped " + run.report().count(ImportReport.Kind.SKIPPED) + ", approximated " + run.report().count(ImportReport.Kind.APPROXIMATED)
                + ", unknown ids " + run.report().count(ImportReport.Kind.UNKNOWN));

        // A startable quest of only checkmark tasks (unsupported FTB tasks became checkmarks, too).
        long[] pick = server.computeOnServer(s -> {
            QuestBook b = QuestServer.get().book;
            for (Chapter c : b.chapters) {
                if (c.alwaysInvisible) continue;
                for (Quest q : c.quests) {
                    if (q.dependencies.isEmpty() && !q.invisible && !q.tasks.isEmpty() && q.tasks.size() <= 4
                            && q.tasks.stream().allMatch(t -> t instanceof Tasks.CheckmarkTask) && q.rewards.size() <= 4) {
                        return new long[]{c.id, q.id};
                    }
                }
            }
            return null;
        });
        ctx.setScreen(QuestBookScreen::new);
        ctx.waitTicks(10);
        ctx.takeScreenshot("qi_import_book");
        if (pick == null) {
            check("the imported book has a startable checkmark quest", false);
        } else {
            check("click the imported chapter", clickLabel("chapter:" + QuestObject.idToString(pick[0])));
            ctx.waitTicks(10);
            check("the chapter is shown", ctx.computeOnClient(mc -> screen(mc).selectedChapter() != null && screen(mc).selectedChapter().id == pick[0]));
            ctx.takeScreenshot("qi_import_chapter");
            clickQuest(pick[1]);
            check("left click opens the imported quest", selected() == pick[1]);
            ctx.takeScreenshot("qi_import_quest");
            int ticks = server.computeOnServer(s -> QuestServer.get().book.quest(pick[1]).tasks.size());
            for (int i = 0; i < ticks; i++) {
                clickLabel(tr("questsicons.gui.check"));
                ctx.waitTicks(10);
            }
            check("ticking its tasks completes the imported quest", ClientQuests.progress.isQuestCompleted(pick[1]));
            int rewards = server.computeOnServer(s -> QuestServer.get().book.quest(pick[1]).rewards.size());
            for (int i = 0; i < rewards && clickLabel(tr("questsicons.gui.claim")); i++) ctx.waitTicks(10);
            check("its rewards can be claimed", server.computeOnServer(s -> {
                QuestServer qs = QuestServer.get();
                var team = qs.team(player(s));
                for (var r : qs.book.quest(pick[1]).rewards) {
                    if (!r.needsChoice() && !team.isRewardClaimed(player(s).getUUID(), r.id, r.isTeamReward())) return false;
                }
                return true;
            }));
            ctx.takeScreenshot("qi_import_done");
        }

        // The JSON5 test book: an item tag task (FTB filter) completes from the inventory, the reward has a bonus.
        long logs = QuestObject.parseId("3000000000000001");
        if (server.computeOnServer(s -> QuestServer.get().book.quest(logs) != null)) {
            server.runCommand("clear @a");
            server.runCommand("give @a minecraft:oak_log 8");
            ctx.waitTicks(60);
            check("8 oak logs finish the \"#minecraft:logs\" quest", ClientQuests.progress.isQuestCompleted(logs));
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);   // close the quest panel that covers the map
            ctx.waitTicks(3);
            clickQuest(logs);
            check("click the logs quest", selected() == logs);
            for (int i = 0; i < 2 && clickLabel(tr("questsicons.gui.claim")); i++) ctx.waitTicks(10);
            int apples = server.computeOnServer(s -> player(s).getInventory().countItem(Items.APPLE));
            check("the apple reward gives 4 to 6 apples (" + apples + ")", apples >= 4 && apples <= 6);
            ctx.takeScreenshot("qi_import_claimed");
        }
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);

        // Back to the starter book, so the next run starts the same way.
        server.runOnServer(s -> {
            try {
                deleteTree(QuestServer.DIR);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            QuestServer.get().load();
        });
        try {
            deleteTree(gameDir.resolve("config/ftbquests"));
            deleteTree(FtbImportCommand.backupDir());
            Files.deleteIfExists(FtbImportCommand.reportFile());
        } catch (IOException e) {
            check("clean up after the import: " + e, false);
        }
        ctx.waitFor(mc -> ClientQuests.book != null && ClientQuests.book.quest(QuestObject.hashId("quest/welcome")) != null, 600);
        check("the starter book is back", ctx.computeOnClient(mc -> ClientQuests.book.quest(QuestObject.hashId("quest/welcome")) != null));
    }

    /**
     * The import on a dedicated server with a real network connection: the imported book must reach the client even
     * when it is big (-Dqi.selftestFtbLarge, e.g. a modpack's quest folder with thousands of quests; default: the
     * test book). The client then opens it and finishes a quest of checkmark tasks with real clicks.
     */
    private void ftbImportOnDedicatedServer() {
        Path sample = Path.of(System.getProperty("qi.selftestFtbLarge", System.getProperty("qi.selftestFtb", "")));
        if (!Files.isDirectory(sample)) return;
        Path gameDir = FMLPaths.GAMEDIR.get();
        try {
            deleteTree(gameDir.resolve("config/ftbquests"));
            copyTree(sample, gameDir.resolve("config/ftbquests/quests"));
            // The test server of this run directory (a dev environment) only starts with the EULA accepted.
            Files.writeString(gameDir.resolve("eula.txt"), "eula=true\n");
        } catch (IOException e) {
            check("copy the FTB Quests sample: " + e, false);
            return;
        }
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
            FtbImportCommand.last = null;
            server.runCommand("quests import ftbquests run");
            ctx.waitFor(mc -> FtbImportCommand.last != null, 12000);
            FtbImportCommand.Finished run = FtbImportCommand.last;
            int quests = run.report().source("Quests");
            check("dedicated server: the import runs (" + quests + " quests)", run.ok() && quests > 0
                    && server.computeOnServer(s -> QuestServer.get().book.questCount()) == quests);
            System.out.println("[selftest] dedicated server import: " + run.report().result + "; unknown ids " + run.report().count(ImportReport.Kind.UNKNOWN));
            try (TestDedicatedServerConnection connection = server.connect()) {
                ctx.waitFor(mc -> ClientQuests.book != null && ClientQuests.book.questCount() == quests, 2400);
                int size = server.computeOnServer(s -> QuestNet.compress(QuestServer.get().book.writeAll()).length);
                check("dedicated server: the client gets the whole book over the network (" + size / 1024 + " KiB packed)",
                        ctx.computeOnClient(mc -> ClientQuests.book != null && ClientQuests.book.questCount() == quests));
                long[] pick = server.computeOnServer(s -> {
                    QuestBook b = QuestServer.get().book;
                    for (Chapter c : b.chapters) {
                        if (c.alwaysInvisible) continue;
                        for (Quest q : c.quests) {
                            if (q.dependencies.isEmpty() && !q.invisible && !q.tasks.isEmpty() && q.tasks.size() <= 4
                                    && q.tasks.stream().allMatch(t -> t instanceof Tasks.CheckmarkTask) && q.rewards.size() <= 4) {
                                return new long[]{c.id, q.id};
                            }
                        }
                    }
                    return null;
                });
                ctx.setScreen(QuestBookScreen::new);
                ctx.waitTicks(20);
                ctx.takeScreenshot("qi_server_import_book");
                if (pick != null) {
                    check("dedicated server: click the chapter", clickLabel("chapter:" + QuestObject.idToString(pick[0])));
                    ctx.waitTicks(20);
                    ctx.takeScreenshot("qi_server_import_chapter");
                    clickQuest(pick[1]);
                    check("dedicated server: the quest opens", selected() == pick[1]);
                    ctx.takeScreenshot("qi_server_import_quest");
                    int ticks = server.computeOnServer(s -> QuestServer.get().book.quest(pick[1]).tasks.size());
                    for (int i = 0; i < ticks; i++) {
                        clickLabel(tr("questsicons.gui.check"));
                        ctx.waitTicks(10);
                    }
                    check("dedicated server: ticking its tasks completes the quest", ClientQuests.progress.isQuestCompleted(pick[1]));
                    ctx.takeScreenshot("qi_server_import_done");
                }
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitTicks(3);
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitTicks(3);
            }
            server.runOnServer(s -> {
                try {
                    deleteTree(QuestServer.DIR);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                QuestServer.get().load();
            });
        }
        try {
            deleteTree(gameDir.resolve("config/ftbquests"));
            deleteTree(FtbImportCommand.backupDir());
            Files.deleteIfExists(FtbImportCommand.reportFile());
        } catch (IOException e) {
            check("clean up after the import: " + e, false);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
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
        server.runOnServer(s -> player(s).getInventory().selected = 0);
        ctx.runOnClient(mc -> mc.player.getInventory().selected = 0);   // 1.21.1: the client owns the selected slot
        // /give shows its pickup animation with a fake item that must vanish (it once stayed forever).
        check("no ghost star after /give", server.computeOnServer(s -> count(s, ModItems.DREAM_STAR, player(s).blockPosition(), 4)) == 0);
        check("the star is a unique, fire-proof trophy", server.computeOnServer(s -> {
            ItemStack star = player(s).getMainHandItem();
            ServerLevel level = s.overworld();
            return star.is(ModItems.DREAM_STAR) && star.getMaxStackSize() == 1 && star.hasFoil()
                    // 1.21.1: the stack is fire resistant; explosions, cactus and lightning are kept off by the item
                    // entity (invulnerable, see the TNT check below), the void still takes it.
                    && !star.canBeHurtBy(level.damageSources().lava()) && star.canBeHurtBy(level.damageSources().fellOutOfWorld());
        }));
        check("getting the star grants the advancement", server.computeOnServer(s -> {
            AdvancementHolder h = s.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("questsicons", "dream_star"));
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
            mc.options.hideGui = true;
        });
        ctx.waitTicks(60);
        ctx.takeScreenshot("qi_pedestal_star");
        server.runCommand("tp @a 2.2 151 -1.8 50 20");
        ctx.waitTicks(30);
        ctx.takeScreenshot("qi_pedestal_star_side");
        ctx.runOnClient(mc -> {
            mc.options.hideGui = false;
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
        return s.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("questsicons", path)).isPresent();
    }

    private static boolean crafts(MinecraftServer s, Item result, Item... grid) {
        List<ItemStack> items = new ArrayList<>();
        for (Item i : grid) items.add(i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i));
        CraftingInput input = CraftingInput.of(3, 3, items);
        return s.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, s.overworld())
                .map(h -> h.value().assemble(input, s.registryAccess()).is(result)).orElse(false);
    }
}
