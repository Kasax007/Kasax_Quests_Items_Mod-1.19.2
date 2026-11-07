package net.kasax.questsicons.client.gui;

import net.kasax.questsicons.quest.Snbt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Raw editor: shows an object as SNBT (the same text as in the quest files) and sends it back on save. */
public class SnbtEditScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final Consumer<CompoundTag> onSave;
    private MultiLineEditBox box;
    private String error = "";

    public SnbtEditScreen(Screen parent, Component title, CompoundTag tag, Consumer<CompoundTag> onSave) {
        super(title);
        this.parent = parent;
        this.initial = Snbt.write(tag);
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        String text = box != null ? box.getValue() : initial;
        box = new MultiLineEditBox(font, 20, 30, width - 40, height - 70, Component.empty(), Component.empty());
        box.setCharacterLimit(200000);
        box.setValue(text);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("questsicons.edit.save"), b -> save())
                .bounds(width / 2 - 104, height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 + 4, height - 32, 100, 20).build());
    }

    private void save() {
        try {
            CompoundTag tag = Snbt.parse(box.getValue());
            onSave.accept(tag);
            minecraft.setScreen(parent);
        } catch (Exception e) {
            error = e.getMessage();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, QuestBookScreen.BG_TOP, QuestBookScreen.BG_BOTTOM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
        super.render(g, mouseX, mouseY, a);
        g.drawCenteredString(font, title, width / 2, 12, QuestBookScreen.TEXT);
        if (!error.isEmpty()) g.drawCenteredString(font, Component.literal(error), width / 2, height - 46, QuestBookScreen.RED);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
