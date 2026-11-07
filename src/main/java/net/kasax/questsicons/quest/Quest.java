package net.kasax.questsicons.quest;

import net.kasax.questsicons.compat.QiListTag;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.quest.reward.Reward;
import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.quest.task.Task;
import net.kasax.questsicons.quest.task.Tasks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** A node on a chapter's quest map. */
public final class Quest extends QuestObject {
    public Chapter chapter;
    public String subtitle = "";
    public final List<String> description = new ArrayList<>();
    public double x;
    public double y;
    public String shape = "";
    public double size = 1.0;
    public final LongArrayList dependencies = new LongArrayList();
    public boolean dependsOnAny;
    public boolean optional;
    public boolean hideUntilDepsComplete;
    public boolean hideDependencyLines;
    /** No lines from this quest to the quests that depend on it. */
    public boolean hideDependentLines;
    public boolean invisible;
    public boolean disableToast;
    public final List<Task> tasks = new ArrayList<>();
    public final List<Reward> rewards = new ArrayList<>();

    public QuestShape shape() {
        return QuestShape.byName(shape, QuestShape.byName(chapter.defaultQuestShape, QuestShape.byName(chapter.book.defaultShape, QuestShape.CIRCLE)));
    }

    public void read(CompoundTag tag) {
        readBase(tag);
        // A hand-written quest without an id gets a stable one from its place in the file (all of them sharing id 0
        // made them overwrite each other). The id must exist before the task and reward ids are derived from it.
        if (id == 0 && chapter != null) id = hashId("quest/" + chapter.filename + "/" + chapter.quests.size());
        subtitle = tag.getStringOr("subtitle", "");
        description.clear();
        Tag d = tag.get("description");
        if (d instanceof ListTag l) {
            for (Tag t : l) description.add(t instanceof StringTag s ? s.value() : t.toString());
        } else if (d instanceof StringTag s) {
            description.addAll(List.of(s.value().split("\n", -1)));
        }
        x = tag.getDoubleOr("x", 0);
        y = tag.getDoubleOr("y", 0);
        shape = tag.getStringOr("shape", "");
        size = tag.getDoubleOr("size", 1.0);
        dependencies.clear();
        for (Tag t : tag.getListOrEmpty("dependencies")) {
            if (t instanceof StringTag s) dependencies.add(parseId(s.value()));
        }
        dependsOnAny = tag.getStringOr("dependency_requirement", "all_completed").startsWith("one");
        optional = tag.getBooleanOr("optional", false);
        hideUntilDepsComplete = tag.getBooleanOr("hide_until_deps_complete", tag.getBooleanOr("hide_until_deps_visible", false));
        hideDependencyLines = tag.getBooleanOr("hide_dependency_lines", false);
        hideDependentLines = tag.getBooleanOr("hide_dependent_lines", false);
        invisible = tag.getBooleanOr("invisible", false);
        disableToast = tag.getBooleanOr("disable_toast", false);
        tasks.clear();
        int n = 0;
        for (Tag t : tag.getListOrEmpty("tasks")) {
            if (!(t instanceof CompoundTag c)) continue;
            Task task = Tasks.create(c.getStringOr("type", "item"));
            if (task == null) {
                QuestsIcons.LOGGER.warn("Quest {}: unknown task type {}", idString(), c.getStringOr("type", "?"));
                continue;
            }
            task.quest = this;
            task.read(c);
            if (task.id == 0) task.id = hashId(idString() + "/task/" + n);
            n++;
            tasks.add(task);
        }
        rewards.clear();
        n = 0;
        for (Tag t : tag.getListOrEmpty("rewards")) {
            if (!(t instanceof CompoundTag c)) continue;
            Reward r = Rewards.create(c.getStringOr("type", "item"));
            if (r == null) {
                QuestsIcons.LOGGER.warn("Quest {}: unknown reward type {}", idString(), c.getStringOr("type", "?"));
                continue;
            }
            r.quest = this;
            r.read(c);
            if (r.id == 0) r.id = hashId(idString() + "/reward/" + n);
            n++;
            rewards.add(r);
        }
    }

    /**
     * Are the quest's tasks done? Every task that is not optional must be; if all of them are optional, one is
     * enough (FTB Quests' "either/or" quests). False for a quest without tasks (those finish on their own).
     */
    public boolean tasksComplete(java.util.function.Predicate<Task> done) {
        boolean anyRequired = false;
        boolean anyDone = false;
        for (Task t : tasks) {
            boolean d = done.test(t);
            anyDone |= d;
            if (!t.optional) {
                anyRequired = true;
                if (!d) return false;
            }
        }
        return anyRequired || anyDone;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        writeBase(tag);
        if (!subtitle.isEmpty()) tag.putString("subtitle", subtitle);
        if (!description.isEmpty()) {
            ListTag l = QiListTag.mixed();
            for (String s : description) l.add(StringTag.valueOf(s));
            tag.put("description", l);
        }
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        if (!shape.isEmpty()) tag.putString("shape", shape);
        if (size != 1.0) tag.putDouble("size", size);
        if (!dependencies.isEmpty()) {
            ListTag l = QiListTag.mixed();
            for (long dep : dependencies) l.add(StringTag.valueOf(idToString(dep)));
            tag.put("dependencies", l);
        }
        if (dependsOnAny) tag.putString("dependency_requirement", "one_completed");
        if (optional) tag.putBoolean("optional", true);
        if (hideUntilDepsComplete) tag.putBoolean("hide_until_deps_complete", true);
        if (hideDependencyLines) tag.putBoolean("hide_dependency_lines", true);
        if (hideDependentLines) tag.putBoolean("hide_dependent_lines", true);
        if (invisible) tag.putBoolean("invisible", true);
        if (disableToast) tag.putBoolean("disable_toast", true);
        ListTag tl = QiListTag.mixed();
        for (Task t : tasks) tl.add(t.write());
        tag.put("tasks", tl);
        ListTag rl = QiListTag.mixed();
        for (Reward r : rewards) rl.add(r.write());
        if (!rl.isEmpty()) tag.put("rewards", rl);
        return tag;
    }

    @Override
    public Component displayTitle() {
        if (!title.isEmpty()) return text(title);
        if (!tasks.isEmpty()) return tasks.get(0).title(chapter.book.registries);
        return Component.translatable("questsicons.quest.unnamed");
    }

    @Override
    public ItemStack displayIcon(HolderLookup.Provider registries) {
        if (!icon.isEmpty()) return icon.stack(registries);
        if (!tasks.isEmpty()) return tasks.get(0).displayIcon(registries);
        return new ItemStack(Items.BOOK);
    }
}
