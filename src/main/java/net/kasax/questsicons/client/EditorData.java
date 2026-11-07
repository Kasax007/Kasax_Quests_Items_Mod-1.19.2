package net.kasax.questsicons.client;

import net.kasax.questsicons.net.QuestNet;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.QuestBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The editor's client state: ids for the pickers (from the client's registries and, for what only the server knows,
 * from the server), the quest clipboard, the undo history and the snap setting.
 */
public final class EditorData {
    private EditorData() {
    }

    // ------------------------------------------------------------------------------------------------ server lists
    private static CompoundTag server = new CompoundTag();
    private static boolean requested;

    static void onLists(QuestNet.EditorLists payload) {
        server = payload.data();
    }

    public static void reset() {
        server = new CompoundTag();
        requested = false;
        UNDO.clear();
    }

    /** Asks the server for its lists once per connection (the editor calls it when edit mode opens). */
    public static void request() {
        if (requested) return;
        requested = true;
        CompoundTag op = new CompoundTag();
        op.putString("op", "lists");
        ClientNet.send(new QuestNet.Edit(op));
    }

    public static boolean hasServerLists() {
        return server.contains("advancements");
    }

    private static List<String> serverList(String key) {
        List<String> out = new ArrayList<>();
        for (Tag t : server.getListOrEmpty(key)) if (t instanceof StringTag s) out.add(s.value());
        return out;
    }

    /** The ids a picker of this kind offers. */
    public static List<String> ids(String kind) {
        Minecraft mc = Minecraft.getInstance();
        Set<String> out = new TreeSet<>();
        switch (kind) {
            case "entity" -> {
                BuiltInRegistries.ENTITY_TYPE.keySet().forEach(k -> out.add(k.toString()));
                BuiltInRegistries.ENTITY_TYPE.getTagNames().forEach(t -> out.add("#" + t.location()));
            }
            case "entity_only" -> BuiltInRegistries.ENTITY_TYPE.keySet().forEach(k -> out.add(k.toString()));
            case "block" -> {
                BuiltInRegistries.BLOCK.keySet().forEach(k -> out.add(k.toString()));
                BuiltInRegistries.BLOCK.getTagNames().forEach(t -> out.add("#" + t.location()));
            }
            case "stat" -> BuiltInRegistries.CUSTOM_STAT.keySet().forEach(k -> out.add(k.toString()));
            case "biome" -> {
                if (mc.level != null) {
                    mc.level.registryAccess().lookupOrThrow(Registries.BIOME).listElementIds().forEach(k -> out.add(k.location().toString()));
                    mc.level.registryAccess().lookupOrThrow(Registries.BIOME).listTagIds().forEach(k -> out.add("#" + k.location()));
                }
                out.addAll(serverList("biome_tags"));
            }
            case "dimension" -> {
                if (mc.getConnection() != null) for (ResourceKey<Level> k : mc.getConnection().levels()) out.add(k.location().toString());
                out.addAll(serverList("dimensions"));
            }
            case "structure" -> {
                out.addAll(serverList("structures"));
                out.addAll(serverList("structure_tags"));
            }
            case "advancement" -> out.addAll(serverList("advancements"));
            case "loot_table" -> out.addAll(serverList("loot_tables"));
            case "item_tag" -> BuiltInRegistries.ITEM.getTagNames().forEach(t -> out.add("#" + t.location()));
            case "table" -> {
                QuestBook b = ClientQuests.book;
                if (b != null) out.addAll(b.rewardTables.keySet());
            }
            default -> {
            }
        }
        return new ArrayList<>(out);
    }

    public static List<String> criteria(String advancement) {
        List<String> out = new ArrayList<>();
        for (Tag t : server.getCompoundOrEmpty("criteria").getListOrEmpty(advancement)) if (t instanceof StringTag s) out.add(s.value());
        return out;
    }

    /**
     * Is this id known for the kind? Null when it is; otherwise the reason. Lists that come from the server are only
     * checked once the server has sent them.
     */
    public static String check(String kind, String value) {
        String v = value.trim();
        if (v.isEmpty()) return "questsicons.form.error.empty";
        boolean tag = v.startsWith("#");
        String id = tag ? v.substring(1) : v;
        if (ResourceLocation.tryParse(id) == null) return "questsicons.form.error.bad_id";
        String full = (tag ? "#" : "") + ResourceLocation.parse(id);
        if (kind.equals("advancement") || kind.equals("loot_table") || kind.equals("structure")
                || kind.equals("dimension")) {
            if (!hasServerLists() && !kind.equals("dimension")) return null;
        }
        if (tag && (kind.equals("stat") || kind.equals("dimension") || kind.equals("advancement") || kind.equals("loot_table")
                || kind.equals("entity_only"))) {
            return "questsicons.form.error.no_tags";
        }
        Collection<String> known = ids(kind);
        if (known.isEmpty()) return null;
        return known.contains(full) ? null : tag ? "questsicons.form.error.unknown_tag" : "questsicons.form.error.unknown_" + kind;
    }

    // ------------------------------------------------------------------------------------------------ clipboard
    /** Copied quests (full tags, positions relative to the first one). */
    public static final List<CompoundTag> CLIPBOARD = new ArrayList<>();

    // ------------------------------------------------------------------------------------------------ undo
    /** One step of history: the "restore" op that puts things back as they were before an edit, and its name. */
    public record Step(String name, CompoundTag restore) {
    }

    private static final Deque<Step> UNDO = new ArrayDeque<>();
    private static final int MAX_UNDO = 30;

    public static void remember(String name, CompoundTag restore) {
        UNDO.push(new Step(name, restore));
        while (UNDO.size() > MAX_UNDO) UNDO.removeLast();
    }

    public static Step popUndo() {
        return UNDO.poll();
    }

    public static Step peekUndo() {
        return UNDO.peek();
    }

    public static int undoSize() {
        return UNDO.size();
    }

    /** A restore op for these chapters (as the client has them now; absent ones will be removed on undo). */
    public static CompoundTag snapshot(QuestBook book, Collection<Long> chapterIds, boolean groups, boolean tables, boolean data) {
        return net.kasax.questsicons.server.QuestEditor.restoreOp(book, chapterIds, groups, tables, data);
    }

    // ------------------------------------------------------------------------------------------------ settings
    /** Snap dragged quests to half grid steps (off: a tenth of a step). */
    public static boolean snap = true;
}
