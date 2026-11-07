package net.kasax.questsicons.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.questsicons.net.QuestNet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client side of {@link QuestNet}: sending (split into parts when too big) and the parts of big payloads. */
public final class ClientNet {
    private static final QuestNet.Assembler PARTS = new QuestNet.Assembler();

    private ClientNet() {
    }

    public static void send(CustomPacketPayload payload) {
        for (CustomPacketPayload p : QuestNet.split(payload, QuestNet.MAX_TO_SERVER)) ClientPlayNetworking.send(p);
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(QuestNet.Part.TYPE, (payload, ctx) -> {
            CustomPacketPayload whole = PARTS.accept(payload);
            if (whole instanceof QuestNet.BookSync b) ClientQuests.onBook(b);
            else if (whole instanceof QuestNet.EditorLists l) EditorData.onLists(l);
        });
    }
}
