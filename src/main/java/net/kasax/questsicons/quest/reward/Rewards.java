package net.kasax.questsicons.quest.reward;

import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.quest.ItemSpec;
import net.kasax.questsicons.quest.RewardTable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Registry of reward types plus their implementations. */
public final class Rewards {
    public static final Map<String, Supplier<Reward>> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("item", ItemReward::new);
        TYPES.put("xp", XpReward::new);
        TYPES.put("xp_levels", XpLevelsReward::new);
        TYPES.put("command", CommandReward::new);
        TYPES.put("random", RandomReward::new);
        TYPES.put("choice", ChoiceReward::new);
        TYPES.put("loot", LootReward::new);
        TYPES.put("advancement", AdvancementReward::new);
    }

    private Rewards() {
    }

    public static Reward create(String type) {
        Supplier<Reward> s = TYPES.get(type);
        return s == null ? null : s.get();
    }

    public static final class ItemReward extends Reward {
        public ItemSpec item = ItemSpec.EMPTY;
        public int count = 1;

        @Override
        public String type() {
            return "item";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            item = ItemSpec.read(tag.get("item"));
            count = tag.getIntOr("count", item.count());
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.put("item", item.write());
            if (count != item.count()) tag.putInt("count", count);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            Component n = item.stack(r).getHoverName();
            return count > 1 ? Component.literal(count + "× ").append(n) : n.copy();
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return item.stack(r).copyWithCount(Math.max(1, count));
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            ItemStack s = item.stack(player.level().registryAccess());
            if (s.isEmpty()) return;
            give(player, s.copyWithCount(count));
        }
    }

    public static final class XpReward extends Reward {
        public int xp = 100;

        @Override
        public String type() {
            return "xp";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            xp = tag.getIntOr("xp", 100);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putInt("xp", xp);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.reward.xp", xp);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.EXPERIENCE_BOTTLE);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            player.giveExperiencePoints(xp);
        }
    }

    public static final class XpLevelsReward extends Reward {
        public int levels = 5;

        @Override
        public String type() {
            return "xp_levels";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            levels = tag.getIntOr("xp_levels", 5);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putInt("xp_levels", levels);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.reward.xp_levels", levels);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.EXPERIENCE_BOTTLE);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            player.giveExperienceLevels(levels);
        }
    }

    public static final class CommandReward extends Reward {
        public String command = "/say Hi, {p}!";
        public boolean elevatePerms = true;
        public boolean silent = true;

        @Override
        public String type() {
            return "command";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            command = tag.getStringOr("command", command);
            elevatePerms = tag.getBooleanOr("elevate_perms", true);
            silent = tag.getBooleanOr("silent", true);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("command", command);
            if (!elevatePerms) tag.putBoolean("elevate_perms", false);
            if (!silent) tag.putBoolean("silent", false);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.reward.command");
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.COMMAND_BLOCK);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            MinecraftServer server = player.level().getServer();
            String cmd = command.replace("{p}", player.getGameProfile().name())
                    .replace("{x}", String.valueOf(player.getBlockX()))
                    .replace("{y}", String.valueOf(player.getBlockY()))
                    .replace("{z}", String.valueOf(player.getBlockZ()))
                    .replace("{quest}", quest == null ? "" : quest.idString());
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            var source = player.createCommandSourceStack();
            if (elevatePerms) source = source.withPermission(LevelBasedPermissionSet.GAMEMASTER);
            if (silent) source = source.withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, cmd);
        }
    }

    /** One weighted pick from a reward table (FTB "random" reward / loot crate contents). */
    public static final class RandomReward extends Reward {
        public String table = "";
        public final List<RewardTable.Entry> inline = new ArrayList<>();

        @Override
        public String type() {
            return "random";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            table = tag.getStringOr("table", "");
            inline.clear();
            for (Tag t : tag.getListOrEmpty("items")) {
                if (t instanceof CompoundTag c) inline.add(RewardTable.Entry.read(c));
            }
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            if (!table.isEmpty()) tag.putString("table", table);
            if (!inline.isEmpty()) {
                ListTag l = new ListTag();
                for (RewardTable.Entry e : inline) l.add(e.write());
                tag.put("items", l);
            }
        }

        private List<RewardTable.Entry> entries() {
            if (!inline.isEmpty()) return inline;
            RewardTable t = quest == null ? null : quest.chapter.book.rewardTables.get(table);
            return t == null ? List.of() : t.entries;
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            RewardTable t = quest == null ? null : quest.chapter.book.rewardTables.get(table);
            if (t != null && !t.title.isEmpty()) return text(t.title);
            return Component.translatable("questsicons.reward.random");
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            RewardTable t = quest == null ? null : quest.chapter.book.rewardTables.get(table);
            if (t != null && !t.icon.isEmpty()) return t.icon.stack(r);
            return new ItemStack(Items.CHEST);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            RewardTable.Entry e = RewardTable.pick(entries(), player.getRandom());
            if (e != null) give(player, e.item.stack(player.level().registryAccess()).copyWithCount(e.count));
        }
    }

    /** The player picks exactly one of several items. */
    public static final class ChoiceReward extends Reward {
        public final List<ItemSpec> items = new ArrayList<>();

        @Override
        public String type() {
            return "choice";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            items.clear();
            for (Tag t : tag.getListOrEmpty("items")) items.add(ItemSpec.read(t));
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            ListTag l = new ListTag();
            for (ItemSpec s : items) l.add(s.write());
            tag.put("items", l);
        }

        @Override
        public boolean needsChoice() {
            return true;
        }

        @Override
        public List<ItemStack> choices(HolderLookup.Provider registries) {
            List<ItemStack> list = new ArrayList<>();
            for (ItemSpec s : items) list.add(s.stack(registries).copyWithCount(s.count()));
            return list;
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.reward.choice", items.size());
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return items.isEmpty() ? new ItemStack(Items.BUNDLE) : items.get(0).stack(r);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            if (choice < 0 || choice >= items.size()) return;
            ItemSpec s = items.get(choice);
            give(player, s.stack(player.level().registryAccess()).copyWithCount(s.count()));
        }
    }

    /** Rolls a vanilla (or data pack) loot table. */
    public static final class LootReward extends Reward {
        public String lootTable = "minecraft:chests/simple_dungeon";
        public int rolls = 1;

        @Override
        public String type() {
            return "loot";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            lootTable = tag.getStringOr("loot_table", lootTable);
            rolls = tag.getIntOr("rolls", 1);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("loot_table", lootTable);
            if (rolls != 1) tag.putInt("rolls", rolls);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.reward.loot", net.kasax.questsicons.quest.task.TasksNames.pretty(lootTable));
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.CHEST);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            try {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(lootTable));
                LootTable table = player.level().getServer().reloadableRegistries().getLootTable(key);
                for (int i = 0; i < Math.max(1, rolls); i++) {
                    LootParams params = new LootParams.Builder(player.level())
                            .withParameter(LootContextParams.ORIGIN, player.position())
                            .withParameter(LootContextParams.THIS_ENTITY, player)
                            .create(LootContextParamSets.CHEST);
                    table.getRandomItems(params, stack -> give(player, stack));
                }
            } catch (Exception e) {
                QuestsIcons.LOGGER.warn("Loot reward {} failed: {}", lootTable, e.toString());
            }
        }
    }

    public static final class AdvancementReward extends Reward {
        public String advancement = "";

        @Override
        public String type() {
            return "advancement";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            advancement = tag.getStringOr("advancement", "");
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("advancement", advancement);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.reward.advancement", net.kasax.questsicons.quest.task.TasksNames.pretty(advancement));
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.KNOWLEDGE_BOOK);
        }

        @Override
        public void claim(ServerPlayer player, int choice) {
            AdvancementHolder h = player.level().getServer().getAdvancements().get(Identifier.parse(advancement));
            if (h == null) return;
            for (String c : h.value().criteria().keySet()) player.getAdvancements().award(h, c);
        }
    }
}
