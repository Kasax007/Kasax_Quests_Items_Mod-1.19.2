package net.kasax.questsicons.quest.task;

import net.kasax.questsicons.quest.ItemSpec;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Registry of all task types plus their implementations. */
public final class Tasks {
    public static final Map<String, Supplier<Task>> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("item", ItemTask::new);
        TYPES.put("checkmark", CheckmarkTask::new);
        TYPES.put("kill", KillTask::new);
        TYPES.put("advancement", AdvancementTask::new);
        TYPES.put("dimension", DimensionTask::new);
        TYPES.put("biome", BiomeTask::new);
        TYPES.put("structure", StructureTask::new);
        TYPES.put("xp", XpTask::new);
        TYPES.put("stat", StatTask::new);
        TYPES.put("observation", ObservationTask::new);
        TYPES.put("location", LocationTask::new);
    }

    private Tasks() {
    }

    public static Task create(String type) {
        Supplier<Task> s = TYPES.get(type);
        return s == null ? null : s.get();
    }

    static Component name(String id) {
        try {
            Identifier rl = Identifier.parse(id.startsWith("#") ? id.substring(1) : id);
            String path = rl.getPath();
            String pretty = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
            pretty = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
            return Component.literal(id.startsWith("#") ? pretty + " (any)" : pretty);
        } catch (Exception e) {
            return Component.literal(id);
        }
    }

    // -------------------------------------------------------------------------------------------

    public static final class ItemTask extends Task {
        public ItemSpec item = ItemSpec.EMPTY;
        public long count = 1;
        public boolean consume;
        public boolean matchComponents;

        @Override
        public String type() {
            return "item";
        }

        @Override
        public long maxProgress() {
            return Math.max(1, count);
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            item = ItemSpec.read(tag.get("item"));
            count = tag.getLongOr("count", item.count());
            consume = tag.getBooleanOr("consume_items", false);
            matchComponents = tag.getBooleanOr("match_components", false);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.put("item", item.write());
            if (count != 1) tag.putLong("count", count);
            if (consume) tag.putBoolean("consume_items", true);
            if (matchComponents) tag.putBoolean("match_components", true);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            Component n = item.isTag() ? name(item.idString()) : item.stack(r).getHoverName();
            return count > 1 ? Component.literal(count + "× ").append(n) : n.copy();
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return item.stack(r);
        }

        @Override
        public boolean hasSubmitButton() {
            return consume;
        }

        @Override
        public boolean autoDetect() {
            return !consume;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            long have = 0;
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (item.matches(s, matchComponents, ctx.registries())) have += s.getCount();
            }
            if (have > ctx.get()) ctx.set(Math.min(have, maxProgress()));
        }

        @Override
        public void submit(ServerPlayer player, TaskContext ctx) {
            if (!consume) {
                detect(player, ctx);
                return;
            }
            long need = maxProgress() - ctx.get();
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (item.matches(s, matchComponents, ctx.registries())) {
                    int take = (int) Math.min(need, s.getCount());
                    s.shrink(take);
                    need -= take;
                    ctx.add(take);
                }
            }
            inv.setChanged();
        }
    }

    public static final class CheckmarkTask extends Task {
        @Override
        public String type() {
            return "checkmark";
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.checkmark");
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.WRITABLE_BOOK);
        }

        @Override
        public boolean hasSubmitButton() {
            return true;
        }

        @Override
        public Component submitLabel() {
            return Component.translatable("questsicons.gui.check");
        }

        @Override
        public void submit(ServerPlayer player, TaskContext ctx) {
            ctx.set(1);
        }
    }

    public static final class KillTask extends Task {
        public String entity = "minecraft:zombie";
        public long value = 1;

        @Override
        public String type() {
            return "kill";
        }

        @Override
        public long maxProgress() {
            return Math.max(1, value);
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            entity = tag.getStringOr("entity", "minecraft:zombie");
            value = tag.getLongOr("value", 1);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("entity", entity);
            tag.putLong("value", value);
        }

        public boolean matches(Entity e) {
            if (entity.startsWith("#")) {
                return e.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(entity.substring(1))));
            }
            return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(entity);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            Component n;
            if (entity.startsWith("#")) {
                n = name(entity);
            } else {
                EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entity));
                n = t.getDescription();
            }
            return Component.translatable("questsicons.task.kill", value, n);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            if (!entity.startsWith("#")) {
                EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entity));
                var egg = SpawnEggItem.byId(t);
                if (egg.isPresent()) return new ItemStack(egg.get());
            }
            return new ItemStack(Items.IRON_SWORD);
        }
    }

    public static final class AdvancementTask extends Task {
        public String advancement = "minecraft:story/root";
        public String criterion = "";

        @Override
        public String type() {
            return "advancement";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            advancement = tag.getStringOr("advancement", advancement);
            criterion = tag.getStringOr("criterion", "");
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("advancement", advancement);
            if (!criterion.isEmpty()) tag.putString("criterion", criterion);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.advancement", name(advancement));
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.WHEAT);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public int detectInterval() {
            return 40;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            AdvancementHolder h = player.level().getServer().getAdvancements().get(Identifier.parse(advancement));
            if (h == null) return;
            AdvancementProgress p = player.getAdvancements().getOrStartProgress(h);
            if (criterion.isEmpty() ? p.isDone() : isCriterionDone(p)) ctx.set(1);
        }

        private boolean isCriterionDone(AdvancementProgress p) {
            CriterionProgress c = p.getCriterion(criterion);
            return c != null && c.isDone();
        }
    }

    public static final class DimensionTask extends Task {
        public String dimension = "minecraft:the_nether";

        @Override
        public String type() {
            return "dimension";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            dimension = tag.getStringOr("dimension", dimension);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("dimension", dimension);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.dimension", name(dimension));
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(dimension.contains("nether") ? Items.NETHERRACK : dimension.contains("end") ? Items.END_STONE : Items.GRASS_BLOCK);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            if (player.level().dimension().identifier().toString().equals(dimension)) ctx.set(1);
        }
    }

    public static final class BiomeTask extends Task {
        public String biome = "minecraft:plains";

        @Override
        public String type() {
            return "biome";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            biome = tag.getStringOr("biome", biome);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("biome", biome);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.biome", name(biome));
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.FILLED_MAP);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            Holder<Biome> b = player.level().getBiome(player.blockPosition());
            boolean ok = biome.startsWith("#")
                    ? b.is(TagKey.create(Registries.BIOME, Identifier.parse(biome.substring(1))))
                    : b.is(ResourceKey.create(Registries.BIOME, Identifier.parse(biome)));
            if (ok) ctx.set(1);
        }
    }

    public static final class StructureTask extends Task {
        public String structure = "minecraft:village_plains";

        @Override
        public String type() {
            return "structure";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            structure = tag.getStringOr("structure", structure);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("structure", structure);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.structure", name(structure));
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.COMPASS);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public int detectInterval() {
            return 30;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            ServerLevel level = player.level();
            BlockPos pos = player.blockPosition();
            boolean found;
            if (structure.startsWith("#")) {
                TagKey<Structure> key = TagKey.create(Registries.STRUCTURE, Identifier.parse(structure.substring(1)));
                found = level.structureManager().getStructureWithPieceAt(pos, key).isValid();
            } else {
                ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.parse(structure));
                found = level.structureManager().getStructureWithPieceAt(pos, h -> h.is(key)).isValid();
            }
            if (found) ctx.set(1);
        }
    }

    public static final class XpTask extends Task {
        public long value = 5;
        public boolean points;
        public boolean consume;

        @Override
        public String type() {
            return "xp";
        }

        @Override
        public long maxProgress() {
            return Math.max(1, value);
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            value = tag.getLongOr("value", 5);
            points = tag.getBooleanOr("points", false);
            consume = tag.getBooleanOr("consume", false);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putLong("value", value);
            if (points) tag.putBoolean("points", true);
            if (consume) tag.putBoolean("consume", true);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable(points ? "questsicons.task.xp_points" : "questsicons.task.xp_levels", value);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.EXPERIENCE_BOTTLE);
        }

        @Override
        public boolean hasSubmitButton() {
            return consume;
        }

        @Override
        public boolean autoDetect() {
            return !consume;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            long have = points ? player.totalExperience : player.experienceLevel;
            if (have > ctx.get()) ctx.set(Math.min(have, maxProgress()));
        }

        @Override
        public void submit(ServerPlayer player, TaskContext ctx) {
            if (!consume) {
                detect(player, ctx);
                return;
            }
            long need = maxProgress() - ctx.get();
            if (points) {
                int take = (int) Math.min(need, player.totalExperience);
                player.giveExperiencePoints(-take);
                ctx.add(take);
            } else {
                int take = (int) Math.min(need, player.experienceLevel);
                player.giveExperienceLevels(-take);
                ctx.add(take);
            }
        }
    }

    public static final class StatTask extends Task {
        public String stat = "minecraft:jump";
        public long value = 1;

        @Override
        public String type() {
            return "stat";
        }

        @Override
        public long maxProgress() {
            return Math.max(1, value);
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            stat = tag.getStringOr("stat", stat);
            value = tag.getLongOr("value", 1);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("stat", stat);
            tag.putLong("value", value);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.stat", Component.translatable("stat." + stat.replace(':', '.')), value);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.PAPER);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public int detectInterval() {
            return 40;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            Identifier key = Identifier.parse(stat);
            if (!BuiltInRegistries.CUSTOM_STAT.containsKey(key)) return;
            int v = player.getStats().getValue(Stats.CUSTOM.get(BuiltInRegistries.CUSTOM_STAT.getValue(key)));
            if (v > ctx.get()) ctx.set(Math.min(v, maxProgress()));
        }
    }

    /** Look at a block or entity for a while (FTB "observation"). */
    public static final class ObservationTask extends Task {
        public String observe = "minecraft:diamond_ore";
        public boolean entity;
        public long timer = 20;

        @Override
        public String type() {
            return "observation";
        }

        @Override
        public long maxProgress() {
            return Math.max(1, timer);
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            observe = tag.getStringOr("observe", observe);
            entity = tag.getBooleanOr("entity", false);
            timer = tag.getLongOr("timer", 20);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("observe", observe);
            if (entity) tag.putBoolean("entity", true);
            tag.putLong("timer", timer);
        }

        @Override
        public String progressText(long progress) {
            return progress >= maxProgress() ? "✔" : Math.round(100.0 * progress / maxProgress()) + "%";
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            Component n = name(observe);
            if (!entity && !observe.startsWith("#")) {
                Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(observe));
                n = b.getName();
            }
            return Component.translatable("questsicons.task.observe", n);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            if (!entity && !observe.startsWith("#")) {
                ItemStack s = new ItemStack(BuiltInRegistries.BLOCK.getValue(Identifier.parse(observe)).asItem());
                if (!s.isEmpty()) return s;
            }
            return new ItemStack(Items.SPYGLASS);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public int detectInterval() {
            return 5;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            double reach = 8;
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1f);
            Vec3 end = eye.add(look.scale(reach));
            boolean seen = false;
            if (entity) {
                AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
                EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box, e -> !e.isSpectator(), reach * reach);
                if (hit != null) {
                    String id = BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()).toString();
                    seen = observe.startsWith("#")
                            ? hit.getEntity().getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(observe.substring(1))))
                            : id.equals(observe);
                }
            } else {
                HitResult hit = player.pick(reach, 1f, false);
                if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                    var state = player.level().getBlockState(bh.getBlockPos());
                    seen = observe.startsWith("#")
                            ? state.is(TagKey.create(Registries.BLOCK, Identifier.parse(observe.substring(1))))
                            : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(observe);
                }
            }
            if (seen) ctx.set(Math.min(maxProgress(), ctx.get() + detectInterval()));
        }
    }

    public static final class LocationTask extends Task {
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        public int radius = 8;

        @Override
        public String type() {
            return "location";
        }

        @Override
        public void read(CompoundTag tag) {
            super.read(tag);
            dimension = tag.getStringOr("dimension", dimension);
            x = tag.getIntOr("x", 0);
            y = tag.getIntOr("y", 0);
            z = tag.getIntOr("z", 0);
            radius = tag.getIntOr("radius", 8);
        }

        @Override
        public void write(CompoundTag tag) {
            super.write(tag);
            tag.putString("dimension", dimension);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("radius", radius);
        }

        @Override
        protected Component defaultTitle(HolderLookup.Provider r) {
            return Component.translatable("questsicons.task.location", x, y, z);
        }

        @Override
        protected ItemStack defaultIcon(HolderLookup.Provider r) {
            return new ItemStack(Items.COMPASS);
        }

        @Override
        public boolean autoDetect() {
            return true;
        }

        @Override
        public void detect(ServerPlayer player, TaskContext ctx) {
            if (!player.level().dimension().identifier().toString().equals(dimension)) return;
            if (player.blockPosition().distSqr(new BlockPos(x, y, z)) <= (double) radius * radius) ctx.set(1);
        }
    }
}
