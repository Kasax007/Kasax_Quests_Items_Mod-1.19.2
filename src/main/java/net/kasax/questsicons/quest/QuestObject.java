package net.kasax.questsicons.quest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import java.util.Locale;

/** Common base: an id (16 hex digits, like FTB Quests), a title and an icon. */
public abstract class QuestObject {
    public long id;
    public String title = "";
    public ItemSpec icon = ItemSpec.EMPTY;

    public String idString() {
        return QuestObject.idToString(id);
    }

    public static String idToString(long id) {
        return String.format(Locale.ROOT, "%016X", id);
    }

    public static long parseId(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return Long.parseUnsignedLong(s.trim(), 16);
        } catch (NumberFormatException e) {
            // Human-readable ids ("getting_wood") are hashed so authors may use names.
            return QuestObject.hashId(s);
        }
    }

    public static long hashId(String s) {
        long h = 0xcbf29ce484222325L;
        for (char c : s.toCharArray()) {
            h ^= c;
            h *= 0x100000001b3L;
        }
        return h == 0 ? 1 : h;
    }

    protected void readBase(CompoundTag tag) {
        id = parseId(tag.getStringOr("id", ""));
        title = tag.getStringOr("title", "");
        icon = ItemSpec.read(tag.get("icon"));
    }

    protected void writeBase(CompoundTag tag) {
        tag.putString("id", idString());
        if (!title.isEmpty()) tag.putString("title", title);
        if (!icon.isEmpty()) tag.put("icon", icon.write());
    }

    /** The title shown to players; subclasses fall back to a generated one. */
    public Component displayTitle() {
        return title.isEmpty() ? Component.literal("?") : text(title);
    }

    public ItemStack displayIcon(HolderLookup.Provider registries) {
        return icon.stack(registries);
    }

    /**
     * Quest text, read like FTB Quests reads it: JSON text components when the string starts with '{' or '[' and
     * parses; otherwise '&amp;' formatting codes (also '&amp;#RRGGBB' colours, '\&amp;' for a plain ampersand) and
     * translation keys in braces, either the whole text ({@code "{dreamcraft.quest.x}"}) or inside it
     * ({@code "Find &6{mypack.item.star}"}); a translation may itself contain '&amp;' codes.
     */
    public static MutableComponent text(String raw) {
        if (raw.length() > 2 && raw.startsWith("{") && raw.endsWith("}") && !raw.contains("\"") && !raw.contains(" ")
                && isKey(raw.substring(1, raw.length() - 1))) {
            return translated(raw.substring(1, raw.length() - 1), Style.EMPTY, 0);
        }
        String t = raw.trim();
        if ((t.startsWith("{\"") || t.startsWith("[\"") || t.startsWith("[{")) && t.length() > 3) {
            try {
                return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(t)).getOrThrow().copy();
            } catch (Exception ignored) {
            }
        }
        return formatted(raw, Style.EMPTY, 0);
    }

    private static boolean isKey(String s) {
        return !s.isEmpty() && s.matches("[A-Za-z0-9_.\\-]+") && !s.startsWith(".") && !s.startsWith("@");
    }

    private static MutableComponent translated(String key, Style style, int depth) {
        Language lang = Language.getInstance();
        if (depth < 4 && lang.has(key)) {
            String value = lang.getOrDefault(key);
            if (value.indexOf('&') >= 0 || value.indexOf('{') >= 0) return formatted(value, style, depth + 1);
        }
        return Component.translatable(key).withStyle(style);
    }

    private static MutableComponent formatted(String s, Style base, int depth) {
        // Plain '&' codes stay a literal with section signs, exactly as before 1.2.0 (titles keep their colours
        // wherever they are drawn as a string); hex colours, escapes and inline keys need styled parts.
        if (s.indexOf('{') < 0 && !s.contains("&#") && !s.contains("\\&")) return Component.literal(ampersand(s)).withStyle(base);
        MutableComponent root = Component.empty();
        StringBuilder buf = new StringBuilder();
        Style style = base;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char n = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (c == '\\' && (n == '&' || n == '{' || n == '}')) {
                buf.append(n);
                i++;
                continue;
            }
            if (c == '&' && n == '&') {
                buf.append('&');
                i++;
                continue;
            }
            if ((c == '&' || c == '§') && n != 0) {
                if (n == '#' && i + 7 < s.length() && s.substring(i + 2, i + 8).matches("[0-9A-Fa-f]{6}")) {
                    flush(root, buf, style);
                    style = style.withColor(TextColor.fromRgb(Integer.parseInt(s.substring(i + 2, i + 8), 16)));
                    i += 7;
                    continue;
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(n));
                if (f != null) {
                    flush(root, buf, style);
                    style = f == ChatFormatting.RESET ? base : style.applyFormat(f);
                    i++;
                    continue;
                }
            }
            if (c == '{') {
                int end = s.indexOf('}', i + 1);
                String inner = end > i + 1 ? s.substring(i + 1, end) : "";
                if (isKey(inner)) {
                    flush(root, buf, style);
                    root.append(translated(inner, style, depth));
                    i = end;
                    continue;
                }
                if (inner.startsWith("image:") || inner.startsWith("@pagebreak")) {
                    i = end;   // pictures are drawn on their own line; FTB's page breaks have no counterpart
                    continue;
                }
                if (inner.startsWith("open_url:")) {
                    int tx = inner.indexOf(" text:");
                    flush(root, buf, style);
                    String label = tx >= 0 ? inner.substring(tx + 6) : inner.substring(9);
                    root.append(formatted(label, style.withColor(ChatFormatting.AQUA).withUnderlined(true), depth + 1));
                    i = end;
                    continue;
                }
            }
            buf.append(c);
        }
        flush(root, buf, style);
        return root;
    }

    private static void flush(MutableComponent root, StringBuilder buf, Style style) {
        if (buf.isEmpty()) return;
        root.append(Component.literal(buf.toString()).withStyle(style));
        buf.setLength(0);
    }

    /** Converts '&amp;' codes to section signs (keeps "&amp;&amp;" as a literal ampersand). */
    public static String ampersand(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '&') {
                    sb.append('&');
                    i++;
                    continue;
                }
                if (ChatFormatting.getByCode(Character.toLowerCase(n)) != null) {
                    sb.append('§');
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
