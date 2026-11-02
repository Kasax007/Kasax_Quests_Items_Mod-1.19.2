package net.kasax.questsicons.progress;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kasax.questsicons.quest.ProgressView;
import net.kasax.questsicons.quest.QuestObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Quest progress of one team (a lone player is a team of one). */
public final class TeamData {
    public UUID id;
    public String name;
    public UUID owner;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final Map<UUID, String> memberNames = new LinkedHashMap<>();
    public final Set<UUID> invites = new LinkedHashSet<>();
    public final Long2LongOpenHashMap progress = new Long2LongOpenHashMap();
    public final Long2LongOpenHashMap completed = new Long2LongOpenHashMap();
    public final Map<UUID, LongOpenHashSet> claimed = new HashMap<>();
    public final LongOpenHashSet teamClaimed = new LongOpenHashSet();
    /** Players that already got the free quest book. */
    public final Set<UUID> gotBook = new LinkedHashSet<>();

    public TeamData(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
    }

    public boolean isRewardClaimed(UUID player, long rewardId, boolean teamReward) {
        if (teamReward) return teamClaimed.contains(rewardId);
        LongOpenHashSet s = claimed.get(player);
        return s != null && s.contains(rewardId);
    }

    public void markClaimed(UUID player, long rewardId, boolean teamReward) {
        if (teamReward) teamClaimed.add(rewardId);
        else claimed.computeIfAbsent(player, k -> new LongOpenHashSet()).add(rewardId);
    }

    /** A read-only view as seen by one member. */
    public ProgressView viewFor(UUID player) {
        return new ProgressView() {
            @Override
            public long taskProgress(long taskId) {
                return progress.get(taskId);
            }

            @Override
            public boolean isQuestCompleted(long questId) {
                return completed.containsKey(questId);
            }

            @Override
            public boolean isRewardClaimed(long rewardId, boolean teamReward) {
                return TeamData.this.isRewardClaimed(player, rewardId, teamReward);
            }
        };
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id.toString());
        t.putString("name", name);
        t.putString("owner", owner.toString());
        ListTag m = new ListTag();
        for (UUID u : members) {
            CompoundTag c = new CompoundTag();
            c.putString("uuid", u.toString());
            c.putString("name", memberNames.getOrDefault(u, "?"));
            m.add(c);
        }
        t.put("members", m);
        ListTag inv = new ListTag();
        for (UUID u : invites) inv.add(StringTag.valueOf(u.toString()));
        t.put("invites", inv);
        CompoundTag p = new CompoundTag();
        progress.long2LongEntrySet().forEach(e -> {
            if (e.getLongValue() > 0) p.putLong(QuestObject.idToString(e.getLongKey()), e.getLongValue());
        });
        t.put("progress", p);
        CompoundTag c = new CompoundTag();
        completed.long2LongEntrySet().forEach(e -> c.putLong(QuestObject.idToString(e.getLongKey()), e.getLongValue()));
        t.put("completed", c);
        CompoundTag cl = new CompoundTag();
        claimed.forEach((u, set) -> {
            ListTag l = new ListTag();
            for (long r : set) l.add(StringTag.valueOf(QuestObject.idToString(r)));
            cl.put(u.toString(), l);
        });
        t.put("claimed", cl);
        ListTag tc = new ListTag();
        for (long r : teamClaimed) tc.add(StringTag.valueOf(QuestObject.idToString(r)));
        t.put("team_claimed", tc);
        ListTag gb = new ListTag();
        for (UUID u : gotBook) gb.add(StringTag.valueOf(u.toString()));
        t.put("got_book", gb);
        return t;
    }

    public static TeamData read(CompoundTag t) {
        TeamData d = new TeamData(UUID.fromString(t.getStringOr("id", UUID.randomUUID().toString())),
                t.getStringOr("name", "Team"), UUID.fromString(t.getStringOr("owner", new UUID(0, 0).toString())));
        for (Tag x : t.getListOrEmpty("members")) {
            if (x instanceof CompoundTag c) {
                UUID u = UUID.fromString(c.getStringOr("uuid", ""));
                d.members.add(u);
                d.memberNames.put(u, c.getStringOr("name", "?"));
            }
        }
        for (Tag x : t.getListOrEmpty("invites")) {
            if (x instanceof StringTag s) d.invites.add(UUID.fromString(s.value()));
        }
        CompoundTag p = t.getCompoundOrEmpty("progress");
        for (String k : p.keySet()) d.progress.put(QuestObject.parseId(k), p.getLongOr(k, 0));
        CompoundTag c = t.getCompoundOrEmpty("completed");
        for (String k : c.keySet()) d.completed.put(QuestObject.parseId(k), c.getLongOr(k, 0));
        CompoundTag cl = t.getCompoundOrEmpty("claimed");
        for (String u : cl.keySet()) {
            LongOpenHashSet set = new LongOpenHashSet();
            for (Tag x : cl.getListOrEmpty(u)) {
                if (x instanceof StringTag s) set.add(QuestObject.parseId(s.value()));
            }
            d.claimed.put(UUID.fromString(u), set);
        }
        for (Tag x : t.getListOrEmpty("team_claimed")) {
            if (x instanceof StringTag s) d.teamClaimed.add(QuestObject.parseId(s.value()));
        }
        for (Tag x : t.getListOrEmpty("got_book")) {
            if (x instanceof StringTag s) d.gotBook.add(UUID.fromString(s.value()));
        }
        return d;
    }

    /** What a member's client needs: progress, completion and that member's claims. */
    public CompoundTag writeForClient(UUID player) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id.toString());
        t.putString("name", name);
        t.putString("owner", owner.toString());
        ListTag m = new ListTag();
        for (UUID u : members) m.add(StringTag.valueOf(memberNames.getOrDefault(u, "?")));
        t.put("members", m);
        t.put("progress", write().getCompoundOrEmpty("progress"));
        t.put("completed", write().getCompoundOrEmpty("completed"));
        ListTag mine = new ListTag();
        LongOpenHashSet set = claimed.get(player);
        if (set != null) for (long r : set) mine.add(StringTag.valueOf(QuestObject.idToString(r)));
        t.put("claimed", mine);
        ListTag tc = new ListTag();
        for (long r : teamClaimed) tc.add(StringTag.valueOf(QuestObject.idToString(r)));
        t.put("team_claimed", tc);
        return t;
    }
}
