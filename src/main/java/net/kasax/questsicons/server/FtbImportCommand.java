package net.kasax.questsicons.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Dynamic;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.importer.FtbFolder;
import net.kasax.questsicons.importer.FtbImporter;
import net.kasax.questsicons.importer.ImportReport;
import net.kasax.questsicons.importer.ImportReport.Kind;
import net.kasax.questsicons.quest.Chapter;
import net.kasax.questsicons.quest.ChapterGroup;
import net.kasax.questsicons.quest.QuestBook;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * {@code /quests import ftbquests [preview|run|merge] [folder]}: turns an FTB Quests book into this mod's quest book.
 * <ul>
 *   <li>{@code preview} (the default) only reads and writes the report;</li>
 *   <li>{@code run} backs up {@code config/questsicons/quests} and replaces the book with the imported one;</li>
 *   <li>{@code merge} backs up, then adds the imported chapters and keeps the book's other chapters.</li>
 * </ul>
 * The folder defaults to {@code config/ftbquests/quests} of the game or server directory; an instance or config folder
 * works too. Reading and converting runs off the server thread; writing and reloading happen on it.
 */
public final class FtbImportCommand {
    public enum Mode { PREVIEW, RUN, MERGE }

    public static final String DEFAULT_FOLDER = "config/ftbquests/quests";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** The last finished import (for tests and the "what happened" question). */
    public static volatile @Nullable Finished last;

    public record Finished(Mode mode, Path source, ImportReport report, @Nullable Path backup, boolean ok, String error) {
    }

    private FtbImportCommand() {
    }

    public static Path reportFile() {
        return QuestServer.DIR.getParent().resolve("ftbquests-import-report.txt");
    }

    public static Path backupDir() {
        return QuestServer.DIR.getParent().resolve("backups");
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        var ftb = Commands.literal("ftbquests").executes(c -> start(c, Mode.PREVIEW, null));
        for (Mode m : Mode.values()) {
            String name = m.name().toLowerCase(Locale.ROOT);
            ftb.then(Commands.literal(name).executes(c -> start(c, m, null))
                    .then(Commands.argument("folder", StringArgumentType.greedyString()).executes(c -> start(c, m, StringArgumentType.getString(c, "folder")))));
        }
        return Commands.literal("import").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(ftb);
    }

    private static int start(CommandContext<CommandSourceStack> c, Mode mode, @Nullable String folder) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        QuestServer qs = QuestServer.get();
        if (qs == null) return 0;
        Path gameDir = server.getServerDirectory().toAbsolutePath().normalize();
        String given = folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder.trim();
        if (given.length() > 1 && (given.startsWith("\"") && given.endsWith("\"") || given.startsWith("'") && given.endsWith("'"))) {
            given = given.substring(1, given.length() - 1);
        }
        Path path = gameDir.resolve(given).normalize();
        Path dir = FtbFolder.locate(path);
        if (dir == null) {
            src.sendFailure(Component.translatable("questsicons.import.not_found", path.toString()));
            return 0;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            src.sendFailure(Component.translatable("questsicons.import.busy"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("questsicons.import.reading", shown(gameDir, dir),
                Component.translatable("questsicons.import.mode." + mode.name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY), true);
        FtbImporter.Options opt = new FtbImporter.Options();
        opt.game = gameData(server);
        opt.fixer = vanillaFixer();
        long t0 = System.currentTimeMillis();
        CompletableFuture.supplyAsync(() -> {
            try {
                return FtbImporter.convert(FtbFolder.load(dir), opt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((result, error) -> {
            try {
                if (error != null) {
                    QuestsIcons.LOGGER.error("FTB Quests import from {} failed", dir, error);
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    src.sendFailure(Component.translatable("questsicons.import.failed", String.valueOf(cause.getMessage())));
                    last = new Finished(mode, dir, new ImportReport(), null, false, String.valueOf(cause));
                    return;
                }
                finish(src, server, gameDir, dir, mode, result, System.currentTimeMillis() - t0, folder);
            } catch (Exception e) {
                QuestsIcons.LOGGER.error("FTB Quests import from {} failed", dir, e);
                src.sendFailure(Component.translatable("questsicons.import.failed", String.valueOf(e.getMessage())));
                last = new Finished(mode, dir, result == null ? new ImportReport() : result.report(), null, false, String.valueOf(e));
            } finally {
                RUNNING.set(false);
            }
        }, server);
        return 1;
    }

    private static String shown(Path gameDir, Path p) {
        return p.startsWith(gameDir) ? gameDir.relativize(p).toString().replace('\\', '/') : p.toString();
    }

    // ------------------------------------------------------------------------------------------- apply

    private static void finish(CommandSourceStack src, MinecraftServer server, Path gameDir, Path dir, Mode mode, FtbImporter.Result result,
                               long ms, @Nullable String folder) throws IOException {
        QuestServer qs = QuestServer.get();
        if (qs == null) return;
        ImportReport report = result.report();
        QuestBook imported = result.book();
        Path backup = null;
        report.header.addFirst("Source: " + dir + " (" + (result.source().isJson5() ? "FTB Quests 26.1, JSON5 files" : "FTB Quests, SNBT files") + ")");
        report.header.add(1, "Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (mode == Mode.PREVIEW) {
            report.header.add(2, "Mode: preview - nothing was changed. Run \"/quests import ftbquests run\" to write the book.");
        } else {
            backup = backup();
            QuestBook book = mode == Mode.RUN ? imported : merge(qs.book, imported);
            book.giveBookOnFirstJoin = qs.book.giveBookOnFirstJoin;
            if (book.background.isEmpty()) book.background = qs.book.background;
            if (mode == Mode.RUN) clearBookFolder();
            book.save(QuestServer.DIR);
            qs.load();
            report.header.add(2, "Mode: " + (mode == Mode.RUN ? "run - the quest book was replaced by the imported one" : "merge - the imported chapters were added")
                    + (backup == null ? "" : "; the previous files are in " + backup));
            report.header.add(3, "Result: " + qs.book.chapters.size() + " chapters and " + qs.book.questCount() + " quests are loaded now");
        }
        report.header.add("Took " + ms + " ms");
        Path file = reportFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, report.toText(), StandardCharsets.UTF_8);
        last = new Finished(mode, dir, report, backup, true, "");
        summary(src, gameDir, dir, mode, report, file, backup, folder);
    }

    /** Replaces chapters, groups and reward tables of {@code into} that the import also has (same id or name). */
    static QuestBook merge(QuestBook into, QuestBook imported) {
        QuestBook b = QuestBook.readAll(into.writeAll());
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Chapter c : imported.chapters) ids.add(c.id);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Chapter c : imported.chapters) names.add(c.filename);
        b.chapters.removeIf(c -> ids.contains(c.id) || names.contains(c.filename));
        java.util.Set<Long> gids = new java.util.HashSet<>();
        for (ChapterGroup g : imported.groups) gids.add(g.id);
        b.groups.removeIf(g -> gids.contains(g.id));
        b.groups.addAll(imported.groups);
        int order = 0;
        for (Chapter c : b.chapters) order = Math.max(order, c.orderIndex + 1);
        for (Chapter c : imported.chapters) {
            Chapter copy = new Chapter();
            copy.book = b;
            copy.read(c.write());
            copy.orderIndex = order + c.orderIndex;
            b.chapters.add(copy);
        }
        b.rewardTables.putAll(imported.rewardTables);
        b.rebuild();
        return b;
    }

    private static @Nullable Path backup() throws IOException {
        Path dir = QuestServer.DIR;
        if (!Files.isDirectory(dir)) return null;
        try (Stream<Path> s = Files.walk(dir)) {
            if (s.noneMatch(Files::isRegularFile)) return null;
        }
        Path target = backupDir().resolve("quests-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        for (int n = 2; Files.exists(target); n++) target = target.resolveSibling(target.getFileName() + "-" + n);
        Path to = target;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.toList()) {
                Path dest = to.resolve(dir.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        }
        return target;
    }

    /** Removes the old chapter and reward table files (they are in the backup) so no chapter of the old book stays. */
    private static void clearBookFolder() throws IOException {
        for (String sub : new String[]{"chapters", "reward_tables"}) {
            Path d = QuestServer.DIR.resolve(sub);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.list(d)) {
                for (Path p : s.toList()) if (p.toString().endsWith(".snbt")) Files.delete(p);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- chat

    private static void summary(CommandSourceStack src, Path gameDir, Path dir, Mode mode, ImportReport r, Path file, @Nullable Path backup,
                                @Nullable String folder) {
        String m = mode.name().toLowerCase(Locale.ROOT);
        src.sendSuccess(() -> Component.translatable("questsicons.import.done." + m, shown(gameDir, dir)).withStyle(ChatFormatting.GOLD), true);
        src.sendSuccess(() -> Component.translatable("questsicons.import.found", num(r.source("Chapter groups")), num(r.source("Chapters")),
                num(r.source("Quests")), num(r.source("Tasks")), num(r.source("Rewards")), num(r.source("Reward tables"))), false);
        src.sendSuccess(() -> Component.translatable("questsicons.import.result", num(r.result("Quests")), num(r.result("Tasks")),
                num(r.result("Rewards")), num(r.count(Kind.CHECKMARK)), num(r.count(Kind.SKIPPED)), num(r.count(Kind.APPROXIMATED))), false);
        if (r.count(Kind.UNKNOWN) > 0) {
            src.sendSuccess(() -> Component.translatable("questsicons.import.unknown", num(r.count(Kind.UNKNOWN))).withStyle(ChatFormatting.RED), false);
        }
        List<String> top = new ArrayList<>();
        for (Kind k : new Kind[]{Kind.CHECKMARK, Kind.SKIPPED}) {
            for (Map.Entry<String, Integer> e : r.summary(k).entrySet()) {
                if (top.size() >= 4) break;
                top.add(e.getKey() + " (" + num(e.getValue()) + ")");
            }
        }
        if (!top.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("questsicons.import.top", String.join(", ", top)).withStyle(ChatFormatting.GRAY), false);
        }
        String reportPath = shown(gameDir, file.toAbsolutePath().normalize());
        MutableComponent link = Component.literal(reportPath).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(file.toAbsolutePath().toString()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("questsicons.import.open_report"))));
        src.sendSuccess(() -> Component.translatable("questsicons.import.report", link), false);
        if (backup != null) {
            src.sendSuccess(() -> Component.translatable("questsicons.import.backup", shown(gameDir, backup.toAbsolutePath().normalize())).withStyle(ChatFormatting.GRAY), false);
        }
        if (mode == Mode.PREVIEW) {
            String cmd = "/quests import ftbquests run" + (folder == null || folder.isBlank() ? "" : " " + folder.trim());
            src.sendSuccess(() -> Component.translatable("questsicons.import.next", Component.literal(cmd).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.SuggestCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd))))), false);
        }
    }

    private static String num(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // ------------------------------------------------------------------------------------------- game data

    /** Upgrades FTB's 1.20/1.21 item stacks with Minecraft's own data fixer (enchantments, potions, names, renames). */
    static FtbImporter.ItemFixer vanillaFixer() {
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        return (stack, from) -> (CompoundTag) DataFixers.getDataFixer()
                .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, stack), from, current).getValue();
    }

    static FtbImporter.GameData gameData(MinecraftServer server) {
        var access = server.registryAccess();
        return new FtbImporter.GameData() {
            private <T> boolean has(Registry<T> reg, String id) {
                Identifier rl = Identifier.tryParse(id);
                return rl != null && reg.containsKey(rl);
            }

            private <T> boolean hasTag(net.minecraft.core.HolderLookup.RegistryLookup<T> reg, ResourceKey<? extends Registry<T>> key, String id) {
                Identifier rl = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
                return rl != null && reg.get(TagKey.create(key, rl)).isPresent();
            }

            private <T> boolean hasKey(ResourceKey<? extends Registry<T>> key, String id) {
                Identifier rl = Identifier.tryParse(id);
                return rl != null && access.lookupOrThrow(key).get(ResourceKey.create(key, rl)).isPresent();
            }

            @Override
            public boolean item(String id) {
                return has(BuiltInRegistries.ITEM, id);
            }

            @Override
            public boolean itemTag(String id) {
                return hasTag(access.lookupOrThrow(Registries.ITEM), Registries.ITEM, id);
            }

            @Override
            public boolean entity(String id) {
                return has(BuiltInRegistries.ENTITY_TYPE, id);
            }

            @Override
            public boolean entityTag(String id) {
                return hasTag(access.lookupOrThrow(Registries.ENTITY_TYPE), Registries.ENTITY_TYPE, id);
            }

            @Override
            public boolean advancement(String id) {
                Identifier rl = Identifier.tryParse(id);
                return rl != null && server.getAdvancements().get(rl) != null;
            }

            @Override
            public boolean dimension(String id) {
                Identifier rl = Identifier.tryParse(id);
                return rl != null && server.levelKeys().contains(ResourceKey.create(Registries.DIMENSION, rl));
            }

            @Override
            public boolean biome(String id) {
                return hasKey(Registries.BIOME, id);
            }

            @Override
            public boolean biomeTag(String id) {
                return hasTag(access.lookupOrThrow(Registries.BIOME), Registries.BIOME, id);
            }

            @Override
            public boolean structure(String id) {
                return hasKey(Registries.STRUCTURE, id);
            }

            @Override
            public boolean structureTag(String id) {
                return hasTag(access.lookupOrThrow(Registries.STRUCTURE), Registries.STRUCTURE, id);
            }

            @Override
            public boolean block(String id) {
                return has(BuiltInRegistries.BLOCK, id);
            }

            @Override
            public boolean blockTag(String id) {
                return hasTag(access.lookupOrThrow(Registries.BLOCK), Registries.BLOCK, id);
            }

            @Override
            public boolean stat(String id) {
                return has(BuiltInRegistries.CUSTOM_STAT, id);
            }

            @Override
            public boolean lootTable(String id) {
                Identifier rl = Identifier.tryParse(id);
                return rl != null && server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE)
                        .get(ResourceKey.create(Registries.LOOT_TABLE, rl)).isPresent();
            }

            @Override
            public List<String> itemIds() {
                List<String> l = new ArrayList<>();
                for (Identifier rl : BuiltInRegistries.ITEM.keySet()) l.add(rl.toString());
                return l;
            }
        };
    }
}
