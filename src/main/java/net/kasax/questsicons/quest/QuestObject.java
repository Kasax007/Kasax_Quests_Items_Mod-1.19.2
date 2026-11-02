package net.kasax.questsicons.quest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
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
     * Quest text: JSON text components when the string starts with '{' or '[' and parses, a
     * translation key in braces like FTB ({@code "{dreamcraft.quest.x}"}), otherwise '&amp;' formatting codes.
     */
    public static MutableComponent text(String raw) {
        if (raw.length() > 2 && raw.startsWith("{") && raw.endsWith("}") && !raw.contains("\"") && !raw.contains(" ")) {
            return Component.translatable(raw.substring(1, raw.length() - 1));
        }
        if ((raw.startsWith("{\"") || raw.startsWith("[\"") || raw.startsWith("[{")) && raw.length() > 3) {
            try {
                return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(raw)).getOrThrow().copy();
            } catch (Exception ignored) {
            }
        }
        return Component.literal(ampersand(raw));
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
