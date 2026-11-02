package net.kasax.questsicons.quest;

import net.kasax.questsicons.quest.reward.Rewards;
import net.kasax.questsicons.quest.task.Tasks;

/** The small example book written on first start when no quest files exist yet. */
public final class StarterBook {
    private StarterBook() {
    }

    public static QuestBook create() {
        QuestBook b = new QuestBook();
        b.title = "Quest Book";
        b.icon = ItemSpec.of("questsicons:quest_book");
        Chapter c = new Chapter();
        c.book = b;
        c.id = QuestObject.hashId("chapter/getting_started");
        c.filename = "getting_started";
        c.title = "&6Getting Started";
        c.icon = ItemSpec.of("questsicons:icon_compass");
        c.subtitle.add("An example chapter. Edit it in game (edit mode) or in config/questsicons/quests.");
        b.chapters.add(c);

        Quest welcome = quest(c, "welcome", "&eWelcome!", 0, 0, "questsicons:quest_book");
        welcome.description.add("This is your quest book. Click a quest to see what to do.");
        welcome.description.add("");
        welcome.description.add("&7Tick the box to finish this quest.");
        welcome.tasks.add(task(welcome, new Tasks.CheckmarkTask(), "welcome/check"));
        Rewards.XpReward xp = new Rewards.XpReward();
        xp.id = QuestObject.hashId("welcome/xp");
        xp.quest = welcome;
        xp.xp = 50;
        welcome.rewards.add(xp);

        Quest wood = quest(c, "wood", "Punch a Tree", 2, 0, "");
        wood.dependencies.add(welcome.id);
        wood.description.add("Every adventure starts with wood.");
        Tasks.ItemTask logs = new Tasks.ItemTask();
        logs.item = ItemSpec.of("#minecraft:logs");
        logs.count = 8;
        wood.tasks.add(task(wood, logs, "wood/logs"));
        Rewards.ItemReward apples = new Rewards.ItemReward();
        apples.id = QuestObject.hashId("wood/apples");
        apples.quest = wood;
        apples.item = ItemSpec.of("minecraft:apple");
        apples.count = 4;
        wood.rewards.add(apples);
        b.rebuild();
        return b;
    }

    private static Quest quest(Chapter c, String key, String title, double x, double y, String icon) {
        Quest q = new Quest();
        q.chapter = c;
        q.id = QuestObject.hashId("quest/" + key);
        q.title = title;
        q.x = x;
        q.y = y;
        if (!icon.isEmpty()) q.icon = ItemSpec.of(icon);
        c.quests.add(q);
        return q;
    }

    private static <T extends net.kasax.questsicons.quest.task.Task> T task(Quest q, T t, String key) {
        t.id = QuestObject.hashId(key);
        t.quest = q;
        return t;
    }
}
