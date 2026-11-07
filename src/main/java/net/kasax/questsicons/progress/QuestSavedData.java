package net.kasax.questsicons.progress;

import com.mojang.serialization.Codec;
import net.kasax.questsicons.QuestsIcons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** All teams and their quest progress, stored with the world ({@code data/questsicons_quest_progress.dat}). */
public final class QuestSavedData extends SavedData {
    public final Map<UUID, TeamData> teams = new LinkedHashMap<>();
    public final Map<UUID, UUID> playerTeam = new HashMap<>();

    private static final Codec<QuestSavedData> CODEC = CompoundTag.CODEC.xmap(QuestSavedData::fromTag, QuestSavedData::toTag);

    public static final SavedDataType<QuestSavedData> TYPE = new SavedDataType<>(
            QuestsIcons.MOD_ID + "_quest_progress", QuestSavedData::new, CODEC, DataFixTypes.LEVEL);

    public static QuestSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static QuestSavedData fromTag(CompoundTag tag) {
        QuestSavedData d = new QuestSavedData();
        for (Tag t : tag.getListOrEmpty("teams")) {
            if (!(t instanceof CompoundTag c)) continue;
            try {
                TeamData team = TeamData.read(c);
                d.teams.put(team.id, team);
            } catch (RuntimeException e) {
                QuestsIcons.LOGGER.error("Skipping a broken quest team entry: {}", e.toString());
            }
        }
        // Up to 1.0.1, joining a team also left the player in a new personal team. Each player belongs to exactly
        // one team: keep the shared one (they never left it) and drop the stray copies.
        for (TeamData team : d.teams.values()) {
            for (UUID m : team.members) {
                TeamData other = d.teams.get(d.playerTeam.get(m));
                if (other == null || team.members.size() > other.members.size()) d.playerTeam.put(m, team.id);
            }
        }
        boolean repaired = false;
        for (var it = d.teams.values().iterator(); it.hasNext(); ) {
            TeamData team = it.next();
            if (team.members.removeIf(m -> !team.id.equals(d.playerTeam.get(m)))) {
                repaired = true;
                team.memberNames.keySet().retainAll(team.members);
                if (team.members.isEmpty()) it.remove();
                else if (!team.members.contains(team.owner)) team.owner = team.members.iterator().next();
            }
        }
        if (repaired) {
            QuestsIcons.LOGGER.info("Repaired quest team membership (players listed in more than one team)");
            d.setDirty();
        }
        return d;
    }

    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag l = new ListTag();
        for (TeamData t : teams.values()) l.add(t.write());
        tag.put("teams", l);
        return tag;
    }

    public TeamData teamOf(UUID player, String playerName) {
        UUID teamId = playerTeam.get(player);
        TeamData team = teamId == null ? null : teams.get(teamId);
        if (team == null) {
            team = new TeamData(player, playerName, player);
            team.members.add(player);
            team.memberNames.put(player, playerName);
            teams.put(team.id, team);
            playerTeam.put(player, team.id);
            setDirty();
        } else if (!playerName.equals(team.memberNames.get(player))) {
            team.memberNames.put(player, playerName);
            setDirty();
        }
        return team;
    }
}
