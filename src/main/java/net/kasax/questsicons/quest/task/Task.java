package net.kasax.questsicons.quest.task;

import net.kasax.questsicons.quest.Quest;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One requirement of a quest. Progress is a long per team; the task is done at {@link #maxProgress()}. */
public abstract class Task extends QuestObject {
    public Quest quest;
    /** Not needed to finish the quest (if all tasks are optional, one of them is enough), like FTB's optional tasks. */
    public boolean optional;

    public abstract String type();

    public long maxProgress() {
        return 1;
    }

    public void read(CompoundTag tag) {
        readBase(tag);
        optional = tag.getBooleanOr("optional_task", false);
    }

    public void write(CompoundTag tag) {
        writeBase(tag);
        tag.putString("type", type());
        if (optional) tag.putBoolean("optional_task", true);
    }

    public final CompoundTag write() {
        CompoundTag t = new CompoundTag();
        write(t);
        return t;
    }

    protected abstract Component defaultTitle(HolderLookup.Provider registries);

    protected abstract ItemStack defaultIcon(HolderLookup.Provider registries);

    public Component title(HolderLookup.Provider registries) {
        return title.isEmpty() ? defaultTitle(registries) : text(title);
    }

    @Override
    public ItemStack displayIcon(HolderLookup.Provider registries) {
        return icon.isEmpty() ? defaultIcon(registries) : icon.stack(registries);
    }

    /** Shows a button in the quest view (submit items, tick a checkbox, pay levels). */
    public boolean hasSubmitButton() {
        return false;
    }

    public Component submitLabel() {
        return Component.translatable("questsicons.gui.submit");
    }

    /** Checked periodically for every player who can work on the quest. */
    public boolean autoDetect() {
        return false;
    }

    public int detectInterval() {
        return 20;
    }

    public void detect(ServerPlayer player, TaskContext ctx) {
    }

    public void submit(ServerPlayer player, TaskContext ctx) {
    }

    public String progressText(long progress) {
        long max = maxProgress();
        if (max <= 1) return progress >= max ? "✔" : "✖";
        return Math.min(progress, max) + "/" + max;
    }

    /** Context the engine passes to detection: read and raise this task's progress for the team. */
    public interface TaskContext {
        long get();

        void set(long value);

        default void add(long amount) {
            set(get() + amount);
        }

        HolderLookup.Provider registries();
    }
}
