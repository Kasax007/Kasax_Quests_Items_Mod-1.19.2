package net.kasax.questsicons.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the importer did and could not do. Every task that became a checkmark, every skipped reward and every
 * approximation is listed with where it is, so a pack maker can fix the few spots by hand.
 */
public final class ImportReport {
    public enum Kind {
        CHECKMARK("Tasks that became a checkmark task (players tick them)"),
        SKIPPED("Skipped (Quest Icons has no counterpart)"),
        APPROXIMATED("Approximated (converted, but not exactly the same)"),
        UNKNOWN("Ids that do not exist in this game (missing mods or renamed)"),
        NOTE("Notes"),
        ERROR("Files that could not be read");

        public final String heading;

        Kind(String heading) {
            this.heading = heading;
        }
    }

    public record Entry(Kind kind, String what, String where, String detail) {
    }

    /** Counts of what the FTB book contains. */
    public final Map<String, Integer> source = new LinkedHashMap<>();
    /** Counts of what the Quest Icons book contains after the import. */
    public final Map<String, Integer> result = new LinkedHashMap<>();
    public final Map<String, Integer> sourceTaskTypes = new TreeMap<>();
    public final Map<String, Integer> sourceRewardTypes = new TreeMap<>();
    public final List<Entry> entries = new ArrayList<>();
    public final List<String> header = new ArrayList<>();

    public void add(Kind kind, String what, String where, String detail) {
        entries.add(new Entry(kind, what, where, detail));
    }

    public void count(Map<String, Integer> map, String key, int n) {
        map.merge(key, n, Integer::sum);
    }

    public int count(Kind kind) {
        int n = 0;
        for (Entry e : entries) if (e.kind == kind) n++;
        return n;
    }

    public int count(Kind kind, String what) {
        int n = 0;
        for (Entry e : entries) if (e.kind == kind && e.what.equals(what)) n++;
        return n;
    }

    public int source(String key) {
        return source.getOrDefault(key, 0);
    }

    public int result(String key) {
        return result.getOrDefault(key, 0);
    }

    /** "what" -> how often, per kind, most frequent first. */
    public Map<String, Integer> summary(Kind kind) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Entry e : entries) if (e.kind == kind) m.merge(e.what, 1, Integer::sum);
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var e : l) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static String num(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Quest Icons - FTB Quests import report\n");
        sb.append("=======================================\n\n");
        for (String h : header) sb.append(h).append('\n');
        sb.append("\nWhat the FTB Quests book contains\n");
        for (var e : source.entrySet()) sb.append(String.format(Locale.ROOT, "  %-26s %s%n", e.getKey(), num(e.getValue())));
        if (!sourceTaskTypes.isEmpty()) sb.append("  Task types: ").append(types(sourceTaskTypes)).append('\n');
        if (!sourceRewardTypes.isEmpty()) sb.append("  Reward types: ").append(types(sourceRewardTypes)).append('\n');
        sb.append("\nWhat the Quest Icons book contains after the import\n");
        for (var e : result.entrySet()) sb.append(String.format(Locale.ROOT, "  %-26s %s%n", e.getKey(), num(e.getValue())));
        for (Kind kind : Kind.values()) {
            int n = count(kind);
            if (n == 0) continue;
            sb.append("\n").append(kind.heading).append(": ").append(num(n)).append('\n');
            sb.append("-".repeat(Math.min(100, kind.heading.length() + 2 + num(n).length()))).append('\n');
            Map<String, Integer> sum = summary(kind);
            for (var e : sum.entrySet()) sb.append(String.format(Locale.ROOT, "  %6s x %s%n", num(e.getValue()), e.getKey()));
            sb.append('\n');
            for (Entry e : entries) {
                if (e.kind != kind) continue;
                sb.append("  - ").append(e.what);
                if (!e.where.isEmpty()) sb.append(" | ").append(e.where);
                if (!e.detail.isEmpty()) sb.append(" | ").append(e.detail);
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String types(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (var e : l) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(e.getKey()).append(' ').append(num(e.getValue()));
        }
        return sb.toString();
    }
}
