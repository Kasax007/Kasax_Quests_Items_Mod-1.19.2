package net.kasax.questsicons.quest;

import net.minecraft.nbt.CompoundTag;

/** A decorative picture on a chapter's quest map (FTB Quests "images"). */
public final class ChapterImage {
    public String image = "";
    public double x;
    public double y;
    public double width = 1;
    public double height = 1;
    public double rotation;
    public int color = 0xFFFFFF;
    public int alpha = 255;
    /** Optional hover text. */
    public String hover = "";
    /** Draw in front of quests (default: behind). */
    public boolean front;
    /** Only shown once this quest is completed (0 = always). */
    public long dependency;

    public static ChapterImage read(CompoundTag tag) {
        ChapterImage i = new ChapterImage();
        i.image = tag.getStringOr("image", "");
        i.x = tag.getDoubleOr("x", 0);
        i.y = tag.getDoubleOr("y", 0);
        i.width = tag.getDoubleOr("width", 1);
        i.height = tag.getDoubleOr("height", 1);
        i.rotation = tag.getDoubleOr("rotation", 0);
        i.color = tag.getIntOr("color", 0xFFFFFF);
        i.alpha = tag.getIntOr("alpha", 255);
        i.hover = tag.getStringOr("hover", "");
        i.front = tag.getBooleanOr("front", false);
        i.dependency = QuestObject.parseId(tag.getStringOr("dependency", ""));
        return i;
    }

    public CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putString("image", image);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("width", width);
        t.putDouble("height", height);
        if (rotation != 0) t.putDouble("rotation", rotation);
        if (color != 0xFFFFFF) t.putInt("color", color);
        if (alpha != 255) t.putInt("alpha", alpha);
        if (!hover.isEmpty()) t.putString("hover", hover);
        if (front) t.putBoolean("front", true);
        if (dependency != 0) t.putString("dependency", QuestObject.idToString(dependency));
        return t;
    }
}
