package net.kasax.questsicons.client.gui;

import net.neoforged.neoforge.client.gui.GuiLayer;

import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.task.Task;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

/** Pinned quests in the top-left corner: title plus each task's progress. */
public class QuestHud implements GuiLayer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        QuestBook book = ClientQuests.book;
        if (book == null || ClientQuests.pinned.isEmpty() || mc.options.hideGui || mc.screen != null) return;
        Font font = mc.font;
        int x = 4;
        int y = 30;
        int shown = 0;
        for (long id : ClientQuests.pinned) {
            Quest q = book.quest(id);
            if (q == null || shown >= 4) continue;
            boolean done = ClientQuests.progress.isQuestCompleted(q.id);
            int lines = 1 + (done ? 0 : q.tasks.size());
            int w = 150;
            g.fill(x - 2, y - 2, x + w, y + lines * 10 + 1, 0x90000000);
            g.drawString(font, QuestBookScreen.clip(font, q.displayTitle(), w - 4), x, y, done ? 0xFF7CFC8C : 0xFFFFD86B, true);
            y += 10;
            if (!done) {
                for (Task t : q.tasks) {
                    long p = ClientQuests.progress.taskProgress(t.id);
                    Component line = Component.literal(" " + t.progressText(p) + "  ").append(t.title(book.registries));
                    g.drawString(font, QuestBookScreen.clip(font, line, w - 4), x, y, p >= t.maxProgress() ? 0xFF7CFC8C : 0xFFDDDDDD, true);
                    y += 10;
                }
            }
            y += 4;
            shown++;
        }
    }
}
