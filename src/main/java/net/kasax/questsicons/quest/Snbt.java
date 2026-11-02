package net.kasax.questsicons.quest;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lenient SNBT in the style of FTB Quests files: entries may be separated by commas OR line breaks,
 * keys may be quoted or bare, {@code true}/{@code false} are booleans, numbers may carry the usual
 * suffixes (b, s, l, f, d). The writer produces the same human-friendly layout (tabs, one entry per
 * line, a stable key order) so files stay diff-friendly when the in-game editor saves them.
 */
public final class Snbt {
    private final String s;
    private int pos;
    private int line = 1;

    private Snbt(String s) {
        this.s = s;
    }

    public static CompoundTag read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static CompoundTag parse(String text) {
        Snbt p = new Snbt(text);
        p.skipWs();
        Tag t = p.value();
        p.skipWs();
        if (!(t instanceof CompoundTag c)) {
            throw p.error("Root must be a compound {...}");
        }
        return c;
    }

    public static Tag parseValue(String text) {
        Snbt p = new Snbt(text);
        p.skipWs();
        return p.value();
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " (line " + line + ")");
    }

    private void skipWs() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
            } else if (Character.isWhitespace(c) || c == ',') {
                pos++;
            } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
                while (pos < s.length() && s.charAt(pos) != '\n') pos++;
            } else if (c == '#' && (pos == 0 || s.charAt(pos - 1) == '\n')) {
                while (pos < s.length() && s.charAt(pos) != '\n') pos++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (pos >= s.length()) throw error("Unexpected end of file");
        return s.charAt(pos);
    }

    private Tag value() {
        char c = peek();
        if (c == '{') return compound();
        if (c == '[') return list();
        if (c == '"' || c == '\'') return StringTag.valueOf(quoted());
        return literal(bare());
    }

    private CompoundTag compound() {
        pos++;
        CompoundTag tag = new CompoundTag();
        while (true) {
            skipWs();
            if (peek() == '}') {
                pos++;
                return tag;
            }
            String key = (peek() == '"' || peek() == '\'') ? quoted() : bareKey();
            if (key.isEmpty()) throw error("Expected a key");
            skipWsNoComma();
            if (peek() != ':' && peek() != '=') throw error("Expected ':' after key '" + key + "'");
            pos++;
            skipWsNoComma();
            tag.put(key, value());
        }
    }

    private void skipWsNoComma() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            if (s.charAt(pos) == '\n') line++;
            pos++;
        }
    }

    private Tag list() {
        pos++;
        skipWsNoComma();
        if (pos + 1 < s.length() && s.charAt(pos + 1) == ';' && "BIL".indexOf(Character.toUpperCase(s.charAt(pos))) >= 0) {
            char type = Character.toUpperCase(s.charAt(pos));
            pos += 2;
            List<Long> values = new ArrayList<>();
            while (true) {
                skipWs();
                if (peek() == ']') {
                    pos++;
                    break;
                }
                Tag t = literal(bare());
                if (!(t instanceof NumericTag n)) throw error("Typed array needs numbers");
                values.add(n.longValue());
            }
            return switch (type) {
                case 'B' -> {
                    byte[] a = new byte[values.size()];
                    for (int i = 0; i < a.length; i++) a[i] = values.get(i).byteValue();
                    yield new ByteArrayTag(a);
                }
                case 'I' -> new IntArrayTag(values.stream().mapToInt(Long::intValue).toArray());
                default -> new LongArrayTag(values.stream().mapToLong(Long::longValue).toArray());
            };
        }
        ListTag list = new ListTag();
        while (true) {
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            list.add(value());
        }
    }

    private String quoted() {
        char q = s.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = peek();
            pos++;
            if (c == q) return sb.toString();
            if (c == '\\') {
                char e = peek();
                pos++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(e);
                }
            } else {
                if (c == '\n') line++;
                sb.append(c);
            }
        }
    }

    private String bareKey() {
        int start = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (Character.isLetterOrDigit(c) || "_-.+".indexOf(c) >= 0) {
                pos++;
            } else {
                break;
            }
        }
        return s.substring(start, pos);
    }

    private String bare() {
        int start = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (Character.isLetterOrDigit(c) || "_-.+:/#$@!*&%?".indexOf(c) >= 0) {
                pos++;
            } else {
                break;
            }
        }
        return s.substring(start, pos);
    }

    private Tag literal(String v) {
        if (v.isEmpty()) throw error("Unexpected character '" + peek() + "'");
        if (v.equals("true")) return ByteTag.valueOf(true);
        if (v.equals("false")) return ByteTag.valueOf(false);
        try {
            char last = Character.toLowerCase(v.charAt(v.length() - 1));
            String num = v.substring(0, v.length() - 1);
            switch (last) {
                case 'b':
                    if (isNumber(num)) return ByteTag.valueOf(Byte.parseByte(num));
                    break;
                case 's':
                    if (isNumber(num)) return ShortTag.valueOf(Short.parseShort(num));
                    break;
                case 'l':
                    if (isNumber(num)) return LongTag.valueOf(Long.parseLong(num));
                    break;
                case 'f':
                    if (isNumber(num)) return FloatTag.valueOf(Float.parseFloat(num));
                    break;
                case 'd':
                    if (isNumber(num)) return DoubleTag.valueOf(Double.parseDouble(num));
                    break;
                default:
            }
            if (v.matches("[-+]?\\d+")) {
                long l = Long.parseLong(v);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return IntTag.valueOf((int) l);
                return LongTag.valueOf(l);
            }
            if (v.matches("[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][-+]?\\d+)?")) {
                return DoubleTag.valueOf(Double.parseDouble(v));
            }
        } catch (NumberFormatException ignored) {
        }
        return StringTag.valueOf(v);
    }

    private static boolean isNumber(String n) {
        return n.matches("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    }

    // ------------------------------------------------------------------------------------------
    // Writer

    /** Preferred key order: identity first, layout next, content last. Unknown keys go alphabetically. */
    private static final List<String> ORDER = List.of(
            "id", "type", "filename", "title", "subtitle", "icon", "group", "order_index", "default_quest_shape",
            "x", "y", "shape", "size", "dependencies", "dependency_requirement", "optional", "hide_until_deps_complete",
            "hide_dependency_lines", "description", "tasks", "rewards", "quests", "images");

    private static final Comparator<String> KEY_ORDER = (a, b) -> {
        int ia = ORDER.indexOf(a);
        int ib = ORDER.indexOf(b);
        if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
        if (ia >= 0) return -1;
        if (ib >= 0) return 1;
        return a.compareTo(b);
    };

    public static String write(CompoundTag tag) {
        StringBuilder sb = new StringBuilder();
        write(sb, tag, 0);
        sb.append('\n');
        return sb.toString();
    }

    public static void write(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, write(tag), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);   // some file systems cannot
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("\t".repeat(depth));
    }

    private static void write(StringBuilder sb, Tag tag, int depth) {
        if (tag instanceof CompoundTag c) {
            if (c.isEmpty()) {
                sb.append("{ }");
                return;
            }
            boolean inline = depth > 0 && c.size() <= 3 && c.keySet().stream().allMatch(k -> isSimple(c.get(k)));
            List<String> keys = new ArrayList<>(c.keySet());
            keys.sort(KEY_ORDER);
            if (inline) {
                sb.append("{ ");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(key(keys.get(i))).append(": ");
                    write(sb, c.get(keys.get(i)), depth + 1);
                }
                sb.append(" }");
                return;
            }
            sb.append("{\n");
            for (String k : keys) {
                indent(sb, depth + 1);
                sb.append(key(k)).append(": ");
                write(sb, c.get(k), depth + 1);
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append('}');
        } else if (tag instanceof ListTag l) {
            if (l.isEmpty()) {
                sb.append("[ ]");
                return;
            }
            boolean simple = l.stream().allMatch(Snbt::isSimple);
            if (simple && l.stream().noneMatch(t -> t instanceof StringTag st && st.value().length() > 24)) {
                sb.append("[");
                for (int i = 0; i < l.size(); i++) {
                    sb.append(i > 0 ? ", " : "");
                    write(sb, l.get(i), depth + 1);
                }
                sb.append("]");
                return;
            }
            sb.append("[\n");
            for (Tag t : l) {
                indent(sb, depth + 1);
                write(sb, t, depth + 1);
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
        } else if (tag instanceof StringTag st) {
            sb.append(StringTag.quoteAndEscape(st.value()));
        } else if (tag instanceof ByteTag b) {
            sb.append(b.byteValue() == 0 ? "false" : b.byteValue() == 1 ? "true" : b.byteValue() + "b");
        } else if (tag instanceof DoubleTag d) {
            double v = d.doubleValue();
            sb.append(v == Math.rint(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) + ".0d" : v + "d");
        } else if (tag instanceof FloatTag f) {
            sb.append(f.floatValue()).append('f');
        } else if (tag instanceof LongTag lt) {
            sb.append(lt.longValue()).append('L');
        } else if (tag instanceof ShortTag sh) {
            sb.append(sh.shortValue()).append('s');
        } else if (tag instanceof IntTag i) {
            sb.append(i.intValue());
        } else {
            sb.append(tag.toString());
        }
    }

    private static boolean isSimple(Tag t) {
        return !(t instanceof CompoundTag) && !(t instanceof ListTag);
    }

    private static String key(String k) {
        return k.matches("[A-Za-z0-9_\\-.+]+") ? k : StringTag.quoteAndEscape(k);
    }
}
