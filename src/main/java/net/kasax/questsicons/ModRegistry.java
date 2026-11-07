package net.kasax.questsicons;

import net.kasax.questsicons.block.ModBlocks;
import net.kasax.questsicons.item.ModItemGroup;
import net.kasax.questsicons.item.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration for Forge 1.20.1, which locks the vanilla registries: ModBlocks, ModItems and ModItemGroup call
 * {@link #register} (on Fabric it is a plain {@code Registry.register}); the entries are handed to Forge in the
 * RegisterEvent of their registry.
 */
public final class ModRegistry {
    private static final Map<ResourceKey<?>, List<Map.Entry<ResourceLocation, Object>>> PENDING = new LinkedHashMap<>();

    private ModRegistry() {
    }

    public static <T> T register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, T value) {
        PENDING.computeIfAbsent(registry, k -> new ArrayList<>()).add(Map.entry(id, value));
        return value;
    }

    /** Mod bus, for every registry. The block event comes first: it creates every block, item and tab. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegister(RegisterEvent e) {
        if (e.getRegistryKey().equals(Registries.BLOCK)) {
            ModBlocks.registerBlocks();
            ModItems.registerModItems();
            ModItemGroup.registerItemGroups();
        }
        List<Map.Entry<ResourceLocation, Object>> entries = PENDING.remove(e.getRegistryKey());
        if (entries == null) return;
        for (Map.Entry<ResourceLocation, Object> entry : entries) {
            e.register((ResourceKey) e.getRegistryKey(), entry.getKey(), entry::getValue);
        }
    }
}
