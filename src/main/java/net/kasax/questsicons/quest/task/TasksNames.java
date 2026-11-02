package net.kasax.questsicons.quest.task;

import net.minecraft.network.chat.Component;

/** Turns registry ids into readable names ("minecraft:chests/simple_dungeon" -> "Simple dungeon"). */
public final class TasksNames {
    private TasksNames() {
    }

    public static Component pretty(String id) {
        return Tasks.name(id);
    }
}
