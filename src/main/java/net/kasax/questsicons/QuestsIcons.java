package net.kasax.questsicons;

import net.fabricmc.api.ModInitializer;
import net.kasax.questsicons.function.ItemListGenerator;
import net.kasax.questsicons.function.TimedRun;
import net.kasax.questsicons.item.ModItems;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QuestsIcons implements ModInitializer {
	public static final String MOD_ID = "questsicons";
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private final net.kasax.questsicons.function.TimedRun TimedRun = new TimedRun();

	@Override
	public void onInitialize() {
		ModItems.registerModItems();
		ItemListGenerator.generateItemList();
		TimedRun.init();
		List<Identifier> itemList = ItemListGenerator.generateItemList();
		QuestsIcons.LOGGER.debug("Item List of every item in the game " + itemList);
	}

}
