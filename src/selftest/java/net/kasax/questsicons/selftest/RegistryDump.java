package net.kasax.questsicons.selftest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

/**
 * Writes every id the mod adds (all built-in and data-pack registries, recipes, advancements, loot tables, the block
 * states and every tag that is the mod's or holds one of its entries) as sorted lines, so that the dumps of two builds
 * (Fabric and NeoForge) can be compared with diff. Only vanilla code: the same class runs on both loaders.
 */
public final class RegistryDump {
    private static final String NS = "questsicons";

    private RegistryDump() {
    }

    public static void write(MinecraftServer server, Path out) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            dump(lines, "builtin", registry);
        }
        server.registryAccess().registries().forEach(e -> dump(lines, "dynamic", e.value()));
        server.getRecipeManager().getRecipes().stream().map(r -> r.id().identifier()).filter(RegistryDump::ours)
                .forEach(id -> lines.add("recipe " + id));
        server.getAdvancements().getAllAdvancements().stream().map(a -> a.id()).filter(RegistryDump::ours)
                .forEach(id -> lines.add("advancement " + id));
        server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(k -> k.identifier())
                .filter(RegistryDump::ours).forEach(id -> lines.add("loot_table " + id));
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (ours(id)) {
                lines.add("blockstate " + id + " " + block.getStateDefinition().getProperties().stream()
                        .map(p -> p.getName() + "=" + p.getPossibleValues()).sorted().collect(Collectors.joining(",")));
            }
        }
        lines.sort(null);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, lines, StandardCharsets.UTF_8);
    }

    private static boolean ours(Identifier id) {
        return id != null && NS.equals(id.getNamespace());
    }

    private static <T> void dump(List<String> lines, String kind, Registry<T> registry) {
        String name = registry.key().identifier().toString();
        registry.keySet().stream().filter(RegistryDump::ours).forEach(id -> lines.add(kind + " " + name + " " + id));
        registry.getTags().forEach(set -> {
            var tag = set.key();
            List<String> members = set.stream().map(Holder::unwrapKey).filter(k -> k.isPresent())
                    .map(k -> k.get().identifier()).filter(RegistryDump::ours).map(Object::toString).sorted().toList();
            if (ours(tag.location()) || !members.isEmpty()) {
                lines.add("tag " + name + " #" + tag.location() + " " + String.join(",", members)
                        + (ours(tag.location()) ? " (" + set.size() + " entries)" : ""));
            }
        });
    }
}
