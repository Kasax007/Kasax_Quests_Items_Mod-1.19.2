package net.kasax.questsicons.importer;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A complete JSON5 reader (comments, unquoted keys, single quotes, trailing commas, hex numbers, Infinity/NaN, escapes
 * and line continuations) for the files FTB Quests 26.1 writes. The result is NBT so all FTB formats share one model:
 * objects become compounds, arrays lists, booleans bytes; integers stay exact as int or long (FTB stores reward table
 * ids as 64-bit numbers), other numbers become doubles, and {@code null} values are left out.
 */
public final class Json5Reader {
    private final String s;
    private int pos;

    private Json5Reader(String s) {
        this.s = s;
    }

    public static CompoundTag read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static CompoundTag parse(String text) {
        Json5Reader r = new Json5Reader(text);
        r.ws();
        Tag t = r.value();
        r.ws();
        if (r.pos < r.s.length()) throw r.error("Unexpected text after the end");
        if (!(t instanceof CompoundTag c)) throw r.error("The file must start with {");
        return c;
    }

    private IllegalArgumentException error(String msg) {
        int row = 1;
        int col = 1;
        for (int i = 0; i < Math.min(pos, s.length()); i++) {
            if (s.charAt(i) == '\n') {
                row++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException(msg + " @ " + row + ":" + col);
    }

    private char peek() {
        if (pos >= s.length()) throw error("Unexpected end of file");
        return s.charAt(pos);
    }

    private static boolean space(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '\f' || c == ' '
                || c == '﻿' || c == ' ' || c == ' ' || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    private void ws() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (space(c)) {
                pos++;
            } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
                while (pos < s.length() && s.charAt(pos) != '\n' && s.charAt(pos) != '\r') pos++;
            } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '*') {
                int end = s.indexOf("*/", pos + 2);
                if (end < 0) throw error("Unclosed comment");
                pos = end + 2;
            } else {
                break;
            }
        }
    }

    private @Nullable Tag value() {
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"' || c == '\'') return StringTag.valueOf(string());
        if (s.startsWith("true", pos)) {
            pos += 4;
            return ByteTag.valueOf((byte) 1);
        }
        if (s.startsWith("false", pos)) {
            pos += 5;
            return ByteTag.valueOf((byte) 0);
        }
        if (s.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return number();
    }

    private CompoundTag object() {
        pos++;
        CompoundTag tag = new CompoundTag();
        while (true) {
            ws();
            if (peek() == '}') {
                pos++;
                return tag;
            }
            String key = peek() == '"' || peek() == '\'' ? string() : identifier();
            ws();
            if (peek() != ':') throw error("Expected ':' after key '" + key + "'");
            pos++;
            ws();
            Tag v = value();
            if (v != null) tag.put(key, v);
            ws();
            if (peek() == ',') {
                pos++;
            } else if (peek() != '}') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private ListTag array() {
        pos++;
        ListTag list = new ListTag();
        while (true) {
            ws();
            if (peek() == ']') {
                pos++;
                return list;
            }
            Tag v = value();
            if (v != null) list.add(v);
            ws();
            if (peek() == ',') {
                pos++;
            } else if (peek() != ']') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String identifier() {
        StringBuilder sb = new StringBuilder();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '\\' && pos + 1 < s.length() && s.charAt(pos + 1) == 'u') {
                sb.append((char) Integer.parseInt(s.substring(pos + 2, pos + 6), 16));
                pos += 6;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || Character.getType(c) == Character.CONNECTOR_PUNCTUATION
                    || Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.COMBINING_SPACING_MARK) {
                sb.append(c);
                pos++;
            } else {
                break;
            }
        }
        if (sb.isEmpty()) throw error("Expected a key");
        return sb.toString();
    }

    private String string() {
        char q = s.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = peek();
            pos++;
            if (c == q) return sb.toString();
            if (c == '\n' || c == '\r') throw error("New line inside a string");
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = peek();
            pos++;
            switch (e) {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> {
                    sb.append('\r');
                }
                case 't' -> sb.append('\t');
                case 'v' -> sb.append('\u000B');
                case '0' -> sb.append('\0');
                case 'x' -> {
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 2), 16));
                    pos += 2;
                }
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                case '\r' -> {
                    if (pos < s.length() && s.charAt(pos) == '\n') pos++;   // line continuation
                }
                case '\n', ' ', ' ' -> {
                    // line continuation
                }
                default -> sb.append(e);
            }
        }
    }

    private Tag number() {
        int start = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '+' || c == '-') {
                pos++;
            } else {
                break;
            }
        }
        String w = s.substring(start, pos);
        if (w.isEmpty()) throw error("Unexpected character '" + peek() + "'");
        boolean neg = w.startsWith("-");
        String body = w.startsWith("-") || w.startsWith("+") ? w.substring(1) : w;
        if (body.equals("Infinity")) return DoubleTag.valueOf(neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        if (body.equals("NaN")) return DoubleTag.valueOf(Double.NaN);
        try {
            if (body.startsWith("0x") || body.startsWith("0X")) {
                BigInteger v = new BigInteger(body.substring(2), 16);
                return integer(neg ? v.negate() : v);
            }
            if (body.matches("\\d+")) return integer(new BigInteger(neg ? "-" + body : body));
            if (body.matches("(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?")) return DoubleTag.valueOf(Double.parseDouble(w));
        } catch (NumberFormatException ignored) {
        }
        throw error("Not a value: '" + w + "'");
    }

    private static Tag integer(BigInteger v) {
        if (v.bitLength() < 32) return IntTag.valueOf(v.intValue());
        if (v.bitLength() < 64) return LongTag.valueOf(v.longValue());
        return DoubleTag.valueOf(v.doubleValue());
    }
}
