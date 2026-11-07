package net.kasax.questsicons.icons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kasax.questsicons.QuestsIcons;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The icon library index written by tools/icons/generate.py (assets/questsicons/icon_library.json): the icon items in
 * registration order, grouped into categories and creative tabs, and the pictures for quest maps. Registration, the
 * creative tabs and the editor's pickers all read it, so new icons need no code changes.
 */
public final class IconLibrary {
    public record Icon(String id, String category, String keywords) {
    }

    public record Category(String id, String tab, List<Icon> icons) {
    }

    public record Tab(String id, String icon) {
    }

    /** A quest-map picture: textures/gui/map/&lt;id&gt;.png, its pixel size and default size in quest units. */
    public record MapImage(String id, String category, int width, int height, double gridWidth, double gridHeight) {
        public String texture() {
            return QuestsIcons.MOD_ID + ":textures/gui/map/" + id + ".png";
        }
    }

    public static final List<Tab> TABS;
    public static final List<Category> CATEGORIES;
    public static final Map<String, Icon> ICONS;
    public static final List<MapImage> MAP_IMAGES;
    public static final List<String> MAP_IMAGE_CATEGORIES;

    static {
        List<Tab> tabs = new ArrayList<>();
        List<Category> cats = new ArrayList<>();
        Map<String, Icon> icons = new LinkedHashMap<>();
        List<MapImage> images = new ArrayList<>();
        List<String> imageCats = new ArrayList<>();
        try (InputStream in = IconLibrary.class.getResourceAsStream("/assets/questsicons/icon_library.json")) {
            if (in == null) throw new IllegalStateException("icon_library.json is missing from the mod jar");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("tabs")) {
                JsonObject o = e.getAsJsonObject();
                tabs.add(new Tab(o.get("id").getAsString(), o.get("icon").getAsString()));
            }
            for (JsonElement e : root.getAsJsonArray("categories")) {
                JsonObject o = e.getAsJsonObject();
                String cid = o.get("id").getAsString();
                List<Icon> list = new ArrayList<>();
                for (JsonElement ie : o.getAsJsonArray("icons")) {
                    JsonObject io = ie.getAsJsonObject();
                    Icon icon = new Icon(io.get("id").getAsString(), cid, io.has("keywords") ? io.get("keywords").getAsString() : "");
                    list.add(icon);
                    icons.put(icon.id(), icon);
                }
                cats.add(new Category(cid, o.get("tab").getAsString(), Collections.unmodifiableList(list)));
            }
            JsonArray imgs = root.getAsJsonArray("map_images");
            if (imgs != null) {
                for (JsonElement e : imgs) {
                    JsonObject o = e.getAsJsonObject();
                    images.add(new MapImage(o.get("id").getAsString(), o.get("category").getAsString(), o.get("width").getAsInt(),
                            o.get("height").getAsInt(), o.get("grid_width").getAsDouble(), o.get("grid_height").getAsDouble()));
                }
                for (JsonElement e : root.getAsJsonArray("map_image_categories")) imageCats.add(e.getAsString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the Quest Icons icon library", e);
        }
        TABS = Collections.unmodifiableList(tabs);
        CATEGORIES = Collections.unmodifiableList(cats);
        ICONS = Collections.unmodifiableMap(icons);
        MAP_IMAGES = Collections.unmodifiableList(images);
        MAP_IMAGE_CATEGORIES = Collections.unmodifiableList(imageCats);
    }

    private IconLibrary() {
    }

    public static List<Category> categoriesOfTab(String tab) {
        List<Category> out = new ArrayList<>();
        for (Category c : CATEGORIES) if (c.tab().equals(tab)) out.add(c);
        return out;
    }
}
