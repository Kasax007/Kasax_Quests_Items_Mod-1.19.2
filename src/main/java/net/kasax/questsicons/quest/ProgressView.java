package net.kasax.questsicons.quest;

/** Read access to one team's progress; shared by the server data and the client cache. */
public interface ProgressView {
    long taskProgress(long taskId);

    boolean isQuestCompleted(long questId);

    /** Has THIS player (or the team, for team rewards) claimed the reward? */
    boolean isRewardClaimed(long rewardId, boolean teamReward);

    default boolean isTaskComplete(net.kasax.questsicons.quest.task.Task task) {
        return taskProgress(task.id) >= task.maxProgress();
    }

    /** Dependencies satisfied: the quest can be worked on. */
    default boolean canStart(Quest quest) {
        if (quest.dependencies.isEmpty()) return true;
        int done = 0;
        int known = 0;
        for (long dep : quest.dependencies) {
            Quest d = quest.chapter.book.quest(dep);
            if (d == null) continue;
            known++;
            if (isQuestCompleted(dep)) done++;
        }
        if (known == 0) return true;
        return quest.dependsOnAny ? done > 0 : done == known;
    }

    default boolean isVisible(Quest quest) {
        if (isQuestCompleted(quest.id)) return true;
        if (quest.invisible) return false;
        if (quest.hideUntilDepsComplete && !canStart(quest)) return false;
        return true;
    }

    /** Fraction of tasks done, 0..1. */
    default float questProgress(Quest quest) {
        if (isQuestCompleted(quest.id)) return 1f;
        if (quest.tasks.isEmpty()) return 0f;
        double sum = 0;
        for (var t : quest.tasks) sum += Math.min(1.0, taskProgress(t.id) / (double) Math.max(1, t.maxProgress()));
        return (float) (sum / quest.tasks.size());
    }

    default boolean hasUnclaimedRewards(Quest quest) {
        if (!isQuestCompleted(quest.id)) return false;
        for (var r : quest.rewards) if (!r.isExcludedFromClaimAll() && !isRewardClaimed(r.id, r.isTeamReward())) return true;
        return false;
    }

    default int chapterPercent(Chapter chapter) {
        int total = 0;
        int done = 0;
        for (Quest q : chapter.quests) {
            if (q.optional) continue;
            total++;
            if (isQuestCompleted(q.id)) done++;
        }
        // Rounded down: 199 of 200 quests is 99 %, not "complete".
        return total == 0 ? 100 : done * 100 / total;
    }

    /** All non-optional quests of the chapter are done (a chapter of only optional quests never "completes"). */
    default boolean isChapterComplete(Chapter chapter) {
        boolean any = false;
        for (Quest q : chapter.quests) {
            if (q.optional) continue;
            any = true;
            if (!isQuestCompleted(q.id)) return false;
        }
        return any;
    }
}
