package net.kasax.questsicons;

import net.fabricmc.api.ClientModInitializer;
import net.kasax.questsicons.client.ClientQuests;

public class QuestsIconsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientQuests.init();
    }
}
