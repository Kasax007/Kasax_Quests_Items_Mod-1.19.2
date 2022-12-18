package net.kasax.questsicons.function;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public class ItemListGenerator {
    public static List<Identifier> generateItemList() {
        List<Identifier> itemList = new ArrayList<>();

        for (Item id : Registry.ITEM) {
            Identifier item = Registry.ITEM.getId(id);
            itemList.add(item);
        }
        //QuestsIcons.LOGGER.debug("Item List of every item in the game " + itemList);
        int index = (int)(Math.random() * itemList.size());
        //QuestsIcons.LOGGER.debug("A random item is for example " + itemList.get(index));
        return itemList;
    }
}


