package net.kasax.questsicons.importer;

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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads SNBT exactly the way FTB Library's {@code SNBTParser} does (FTB Quests 1.16 to 1.21): lines whose trimmed text
 * starts with {@code //} or {@code #} are dropped, entries are separated by commas or line breaks, unquoted words use
 * FTB's "simple" characters, {@code true}/{@code false} become bytes, numbers follow FTB's suffix rules (a plain number
 * that is not an int is a double), and an unknown escape drops the escaped character.
 */
public final class FtbSnbtReader {
    private final char[] buf;
    private int pos;

    private FtbSnbtReader(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (!t.startsWith("//") && !t.startsWith("#")) sb.append(line);
            sb.append('\n');
        }
        if (sb.length() > 0 && sb.charAt(0) == '﻿') sb.deleteCharAt(0);
        buf = sb.toString().toCharArray();
    }

    public static CompoundTag read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static CompoundTag parse(String text) {
        FtbSnbtReader r = new FtbSnbtReader(text);
        if (r.buf.length < 2) throw new IllegalArgumentException("File has to have at least two characters");
        Tag t = r.readTag(r.nextNs());
        if (!(t instanceof CompoundTag c)) throw r.error("The file must start with {");
        return c;
    }

    private IllegalArgumentException error(String msg) {
        int row = 1;
        int col = 1;
        for (int i = 0; i < Math.min(pos, buf.length); i++) {
            if (buf[i] == '\n') {
                row++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException(msg + " @ " + row + ":" + col);
    }

    private char next() {
        if (pos >= buf.length) throw error("Unexpected end of file");
        return buf[pos++];
    }

    private char nextNs() {
        while (true) {
            char c = next();
            if (c > ' ') return c;
        }
    }

    /** May return null for FTB's {@code null}/{@code end} words, which hold no value. */
    private @Nullable Tag readTag(char first) {
        switch (first) {
            case '{':
                return readCompound();
            case '[':
                return readCollection();
            case '"':
            case '\'':
                return StringTag.valueOf(readQuoted(first));
            default:
        }
        String s = readWord(first);
        return switch (s) {
            case "true" -> ByteTag.valueOf((byte) 1);
            case "false" -> ByteTag.valueOf((byte) 0);
            case "null", "end", "END" -> null;
            case "Infinity", "Infinityd", "+Infinity", "+Infinityd", "∞", "∞d", "+∞", "+∞d" -> DoubleTag.valueOf(Double.POSITIVE_INFINITY);
            case "-Infinity", "-Infinityd", "-∞", "-∞d" -> DoubleTag.valueOf(Double.NEGATIVE_INFINITY);
            case "NaN", "NaNd" -> DoubleTag.valueOf(Double.NaN);
            case "Infinityf", "+Infinityf", "∞f", "+∞f" -> FloatTag.valueOf(Float.POSITIVE_INFINITY);
            case "-Infinityf", "-∞f" -> FloatTag.valueOf(Float.NEGATIVE_INFINITY);
            case "NaNf" -> FloatTag.valueOf(Float.NaN);
            default -> number(s);
        };
    }

    /** FTB's {@code SNBTUtils.getNumberType}: int, a suffixed number, a plain floating number (double), else a string. */
    static Tag number(String s) {
        if (s.isEmpty()) return StringTag.valueOf(s);
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (Character.isDigit(last) && isInt(s)) return IntTag.valueOf(Integer.parseInt(s));
        String start = s.substring(0, s.length() - 1);
        try {
            if (last == 'b' && isInt(start)) return ByteTag.valueOf((byte) Integer.parseInt(start));
            if (last == 's' && isInt(start)) return ShortTag.valueOf((short) Integer.parseInt(start));
            if (last == 'l' && isLong(start)) return LongTag.valueOf(Long.parseLong(start));
            if (last == 'f' && isFloating(start)) return FloatTag.valueOf(Float.parseFloat(start));
            if (last == 'd' && isFloating(start)) return DoubleTag.valueOf(Double.parseDouble(start));
            if (isFloating(s)) return DoubleTag.valueOf(Double.parseDouble(s));
        } catch (NumberFormatException ignored) {
        }
        return StringTag.valueOf(s);
    }

    private static boolean isInt(String s) {
        if (!s.matches("-?\\d+")) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLong(String s) {
        if (!s.matches("-?\\d+")) return false;
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isFloating(String s) {
        return s.matches("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    }

    private CompoundTag readCompound() {
        CompoundTag tag = new CompoundTag();
        while (true) {
            char c = nextNs();
            if (c == '}') return tag;
            if (c == ',' || c == '\n') continue;
            String key = c == '"' || c == '\'' ? readQuoted(c) : readWord(c);
            char n = nextNs();
            if (n != ':' && n != '=') throw error("Expected ':', got '" + n + "'");
            Tag t = readTag(nextNs());
            if (t != null) tag.put(key, t);
        }
    }

    private Tag readCollection() {
        int prev = pos;
        char a = nextNs();
        char b = nextNs();
        if (b == ';' && "IiLlBb".indexOf(a) >= 0) return readArray(Character.toLowerCase(a));
        pos = prev;
        ListTag list = new ListTag();
        while (true) {
            char c = nextNs();
            if (c == ']') return list;
            if (c == ',') continue;
            Tag t = readTag(c);
            if (t != null) list.add(t);
        }
    }

    private Tag readArray(char type) {
        List<Long> values = new ArrayList<>();
        while (true) {
            char c = nextNs();
            if (c == ']') break;
            if (c == ',') continue;
            Tag t = readTag(c);
            if (!(t instanceof NumericTag n)) throw error("Expected a number in a typed array");
            values.add(n.longValue());
        }
        return switch (type) {
            case 'i' -> new IntArrayTag(values.stream().mapToInt(Long::intValue).toArray());
            case 'l' -> new LongArrayTag(values.stream().mapToLong(Long::longValue).toArray());
            default -> {
                byte[] bytes = new byte[values.size()];
                for (int i = 0; i < bytes.length; i++) bytes[i] = values.get(i).byteValue();
                yield new ByteArrayTag(bytes);
            }
        };
    }

    private static boolean simple(char c) {
        return Character.isAlphabetic(c) || Character.isDigit(c) || c == '.' || c == '_' || c == '-' || c == '+' || c == '∞';
    }

    private String readWord(char first) {
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        while (true) {
            char c = next();
            if (simple(c)) {
                sb.append(c);
            } else {
                pos--;
                return sb.toString();
            }
        }
    }

    private String readQuoted(char stop) {
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        while (true) {
            char c = next();
            if (c == '\n') throw error("New line inside a string");
            if (escape) {
                escape = false;
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 'f' -> sb.append('\f');
                    default -> {
                        // FTB drops an unknown escaped character (a backslash before u, a quote, ...)
                    }
                }
            } else if (c == '\\') {
                escape = true;
            } else if (c == stop) {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
    }
}
