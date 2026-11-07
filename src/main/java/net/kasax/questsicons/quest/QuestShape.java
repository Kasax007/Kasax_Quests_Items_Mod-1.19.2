package net.kasax.questsicons.quest;

import java.util.Locale;

/** Node shapes on the quest map (each has a texture under textures/gui/shapes/; NONE draws only the icon). */
public enum QuestShape {
    CIRCLE, SQUARE, RSQUARE, DIAMOND, PENTAGON, HEXAGON, OCTAGON, HEART, GEAR, STAR, NONE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static QuestShape byName(String s, QuestShape fallback) {
        if (s == null || s.isEmpty() || s.equals("default")) return fallback;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public QuestShape next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
