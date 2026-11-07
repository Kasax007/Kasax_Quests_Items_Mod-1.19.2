package net.kasax.questsicons.client.gui;

import net.kasax.questsicons.client.ClientQuests;
import net.kasax.questsicons.quest.QuestBook;
import net.kasax.questsicons.quest.RewardTable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The book's reward tables (the "random" rewards and the four loot crates draw from them): edit, delete (click twice)
 * or create one.
 */
public class TablesScreen extends QiScreen {
    private final QuestBookScreen book;
    private String confirm = "";

    public TablesScreen(QuestBookScreen parent) {
        super(Component.translatable("questsicons.tables.title"), parent);
        this.book = parent;
    }

    @Override
    protected void draw(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.centeredText(font, title, width / 2, 10, QuestBookScreen.TEXT);
        QuestBook b = ClientQuests.book;
        int w = Math.min(width - 40, 360);
        int x = (width - w) / 2;
        int y = 30;
        if (b == null) return;
        List<String> keys = new ArrayList<>(b.rewardTables.keySet());
        for (String crate : List.of("common_crate", "rare_crate", "epic_crate", "legendary_crate")) {
            if (!keys.contains(crate)) keys.add(crate);
        }
        for (String key : keys) {
            RewardTable t = b.rewardTables.get(key);
            g.fill(x, y, x + w, y + 18, 0x30FFFFFF);
            zone(x, y, w, 18, () -> {
            }, null, "table:" + key);
            Component name = Component.literal(key).withStyle(t == null ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.WHITE);
            if (t != null) name = name.copy().append(Component.literal("  " + Component.translatable("questsicons.tables.entries", t.entries.size()).getString())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            else name = name.copy().append(Component.literal("  " + Component.translatable("questsicons.tables.missing").getString())
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            int right = x + w - 2;
            if (t != null) {
                Component del = Component.translatable(confirm.equals(key) ? "questsicons.edit.confirm_delete" : "questsicons.tables.delete");
                int dw = font.width(del) + 10;
                right -= dw;
                button(g, right, y + 1, dw, 16, del, mx, my, () -> {
                    if (!confirm.equals(key)) {
                        confirm = key;
                        return;
                    }
                    confirm = "";
                    CompoundTag op = new CompoundTag();
                    op.putString("op", "table_delete");
                    op.putString("key", key);
                    book.editTables("delete table", op);
                });
                right -= 2;
            }
            Component ed = Component.translatable(t == null ? "questsicons.tables.create" : "questsicons.form.edit");
            int ew = font.width(ed) + 10;
            right -= ew;
            button(g, right, y + 1, ew, 16, ed, mx, my, () -> edit(key, t));
            g.text(font, QuestBookScreen.clip(font, name, right - x - 8), x + 4, y + 5, QuestBookScreen.TEXT, false);
            y += 20;
        }
        button(g, x, y + 4, 120, 16, Component.translatable("questsicons.tables.new"), mx, my, () -> {
            CompoundTag t = new CompoundTag();
            minecraft.gui.setScreen(Forms.table(this, "", t, true, saved -> {
                String key = saved.getStringOr("__key", "");
                saved.remove("__key");
                send(key, saved);
            }));
        });
        button(g, width / 2 - 50, height - 24, 100, 18, Component.translatable("gui.done"), mx, my, this::back);
    }

    private void edit(String key, RewardTable t) {
        CompoundTag tag = t == null ? new CompoundTag() : t.write();
        minecraft.gui.setScreen(Forms.table(this, key, tag, false, saved -> send(key, saved)));
    }

    private void send(String key, CompoundTag table) {
        CompoundTag op = new CompoundTag();
        op.putString("op", "table_set");
        op.putString("key", key);
        op.put("table", table);
        book.editTables("edit table", op);
    }
}
