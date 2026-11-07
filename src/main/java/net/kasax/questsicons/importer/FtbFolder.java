package net.kasax.questsicons.importer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * One FTB Quests book on disk, read without interpreting it. Three layouts exist:
 * <ul>
 *   <li>up to FTB Quests 2004 (Minecraft 1.20 and older): SNBT, texts inside the chapter files;</li>
 *   <li>2100 to 2111 (1.21.x): SNBT, texts in {@code lang/<locale>.snbt} (some packs split them into
 *       {@code lang/<locale>/**.snbt});</li>
 *   <li>26.1.2.x (Minecraft 26.1): JSON5 ({@code .json5}), texts split into {@code lang/<locale>/**.json5}.</li>
 * </ul>
 */
public final class FtbFolder {
    public final Path dir;
    /** ".snbt" or ".json5". */
    public final String ext;
    public CompoundTag data = new CompoundTag();
    public final List<CompoundTag> groups = new ArrayList<>();
    public final List<SourceFile> chapters = new ArrayList<>();
    public final List<SourceFile> rewardTables = new ArrayList<>();
    /** Locale -> merged text table (key "quest.0123456789ABCDEF.title" -> StringTag or ListTag). */
    public final Map<String, CompoundTag> lang = new HashMap<>();
    /** Files that could not be read (name: error). */
    public final List<String> errors = new ArrayList<>();
    /** How the texts are stored, for the report. */
    public String textLayout = "inline";

    public record SourceFile(String name, CompoundTag tag) {
    }

    private FtbFolder(Path dir, String ext) {
        this.dir = dir;
        this.ext = ext;
    }

    public boolean isJson5() {
        return ext.equals(".json5");
    }

    /**
     * Finds the quest folder in or below {@code given}: the folder itself, {@code quests/}, {@code ftbquests/quests/} or
     * {@code config/ftbquests/quests/} (so an instance or config folder works too). Null if there is none.
     */
    public static @Nullable Path locate(Path given) {
        for (String sub : new String[]{"", "quests", "ftbquests/quests", "config/ftbquests/quests"}) {
            Path p = sub.isEmpty() ? given : given.resolve(sub);
            if (Files.isRegularFile(p.resolve("data.snbt")) || Files.isRegularFile(p.resolve("data.json5"))) return p;
        }
        for (String sub : new String[]{"", "quests"}) {
            Path p = sub.isEmpty() ? given : given.resolve(sub);
            if (Files.isDirectory(p.resolve("chapters"))) return p;
        }
        return null;
    }

    public static FtbFolder load(Path dir) throws IOException {
        String ext = Files.isRegularFile(dir.resolve("data.json5")) && !Files.isRegularFile(dir.resolve("data.snbt")) ? ".json5" : ".snbt";
        if (!Files.exists(dir.resolve("data" + ext)) && Files.isDirectory(dir.resolve("chapters"))) {
            try (Stream<Path> s = Files.list(dir.resolve("chapters"))) {
                if (s.anyMatch(p -> p.toString().endsWith(".json5"))) ext = ".json5";
            }
        }
        FtbFolder f = new FtbFolder(dir, ext);
        Path data = dir.resolve("data" + ext);
        if (Files.isRegularFile(data)) {
            CompoundTag d = f.read(data);
            if (d != null) f.data = d;
        }
        Path groups = dir.resolve("chapter_groups" + ext);
        if (Files.isRegularFile(groups)) {
            CompoundTag g = f.read(groups);
            if (g != null) {
                for (Tag t : g.getListOrEmpty("chapter_groups")) if (t instanceof CompoundTag c) f.groups.add(c);
            }
        }
        f.readDir(dir.resolve("chapters"), f.chapters);
        f.readDir(dir.resolve("reward_tables"), f.rewardTables);
        f.readLang(dir.resolve("lang"));
        return f;
    }

    private @Nullable CompoundTag read(Path file) {
        try {
            return file.toString().endsWith(".json5") ? Json5Reader.read(file) : FtbSnbtReader.read(file);
        } catch (Exception e) {
            errors.add(dir.relativize(file) + ": " + e.getMessage());
            return null;
        }
    }

    private void readDir(Path folder, List<SourceFile> out) throws IOException {
        if (!Files.isDirectory(folder)) return;
        // FTB lower-cases file names and renames clashes to ".OLD"; reading in name order keeps the result stable.
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : s.filter(p -> p.getFileName().toString().endsWith(ext) && Files.isRegularFile(p)).sorted().toList()) {
                CompoundTag t = read(p);
                String name = p.getFileName().toString();
                if (t != null) out.add(new SourceFile(name.substring(0, name.length() - ext.length()), t));
            }
        }
    }

    private void readLang(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return;
        boolean split = false;
        boolean single = false;
        try (Stream<Path> s = Files.list(folder)) {
            for (Path p : s.sorted().toList()) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (!name.matches("\\w+")) continue;
                    CompoundTag table = lang.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new CompoundTag());
                    try (Stream<Path> w = Files.walk(p)) {
                        for (Path file : w.filter(x -> Files.isRegularFile(x) && (x.toString().endsWith(".snbt") || x.toString().endsWith(".json5"))).sorted().toList()) {
                            CompoundTag t = read(file);
                            if (t != null) table.merge(t);
                            split = true;
                        }
                    }
                } else if (name.matches("\\w+\\.(snbt|json5)")) {
                    CompoundTag t = read(p);
                    if (t != null) {
                        lang.computeIfAbsent(name.substring(0, name.indexOf('.')).toLowerCase(Locale.ROOT), k -> new CompoundTag()).merge(t);
                        single = true;
                    }
                }
            }
        }
        if (split || single) textLayout = split ? "lang/<locale>/ (split files)" : "lang/<locale>" + ext;
    }

    public List<String> locales() {
        return new ArrayList<>(new TreeSet<>(lang.keySet()));
    }

    /** Text lookup in the chosen locale, then the fallback locale. */
    public static final class Texts {
        private final @Nullable CompoundTag main;
        private final @Nullable CompoundTag fallback;

        public Texts(FtbFolder f, String locale, String fallbackLocale) {
            this.main = f.lang.get(locale);
            this.fallback = f.lang.get(fallbackLocale);
        }

        public @Nullable Tag get(String key) {
            Tag t = main == null ? null : main.get(key);
            if (t == null && fallback != null) t = fallback.get(key);
            return t;
        }

        public @Nullable String string(String key) {
            Tag t = get(key);
            if (t instanceof StringTag s) return s.value();
            if (t instanceof ListTag l && !l.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Tag e : l) parts.add(e instanceof StringTag s ? s.value() : e.toString());
                return String.join("\n", parts);
            }
            return null;
        }

        public @Nullable List<String> list(String key) {
            Tag t = get(key);
            if (t instanceof ListTag l) {
                List<String> out = new ArrayList<>();
                for (Tag e : l) out.add(e instanceof StringTag s ? s.value() : e.toString());
                return out;
            }
            if (t instanceof StringTag s) return new ArrayList<>(List.of(s.value()));
            return null;
        }
    }
}
