package net.kasax.questsicons;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public class ItemListGenerator {
    public static List<Identifier> generateItemList() {
        List<Identifier> itemList = new ArrayList<>();

        for (Identifier id : Registry.ITEM) {
            Item item = Registry.ITEM.get(id);
            itemList.add(id);
        }

        return itemList;
    }
}


}
