package net.kasax.questsicons;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Registration (Fabric: straight into the vanilla registry; Forge collects the entries for its RegisterEvent). */
public final class ModRegistry {
    private ModRegistry() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, T value) {
        Registry<T> r = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
        return Registry.register(r, id, value);
    }
}
