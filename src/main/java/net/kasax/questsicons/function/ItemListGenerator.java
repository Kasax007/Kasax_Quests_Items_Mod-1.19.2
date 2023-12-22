package net.kasax.questsicons.function;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;


import java.util.ArrayList;
import java.util.List;

public class ItemListGenerator {
    public static List<Identifier> generateItemList() {
        List<Identifier> itemList = new ArrayList<>();

        for (Item id : Registries.ITEM) {
            Identifier item = Registries.ITEM.getId(id);
            itemList.add(item);
        }
        //QuestsIcons.LOGGER.debug("Item List of every item in the game " + itemList);
        int index = (int)(Math.random() * itemList.size());
        //QuestsIcons.LOGGER.debug("A random item is for example " + itemList.get(index));
        return itemList;
    }
}


