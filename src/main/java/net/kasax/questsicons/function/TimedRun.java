package net.kasax.questsicons.function;

import net.kasax.questsicons.QuestsIcons;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimedRun {
    private ScheduledExecutorService executor;

    public void init() {
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::runClass, 0, 10, TimeUnit.SECONDS);
    }

    public void runClass() {
        List<Identifier> list = ItemListGenerator.generateItemList();
        int index = (int)(Math.random() * list.size());
        QuestsIcons.LOGGER.debug("More random items " + list.get(index));
    }
}