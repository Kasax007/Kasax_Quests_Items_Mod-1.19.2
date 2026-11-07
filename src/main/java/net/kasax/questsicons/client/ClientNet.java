package net.kasax.questsicons.client;

import net.kasax.questsicons.net.QuestNet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of {@link QuestNet}: sending (split into parts when too big) and every payload from the server. */
public final class ClientNet {
    private static final QuestNet.Assembler PARTS = new QuestNet.Assembler();

    private ClientNet() {
    }

    public static void send(CustomPacketPayload payload) {
        for (CustomPacketPayload p : QuestNet.split(payload, QuestNet.MAX_TO_SERVER)) PacketDistributor.sendToServer(p);
    }

    /** Called on the client thread for every quest payload (QuestNet.register). */
    public static void received(CustomPacketPayload payload) {
        if (payload instanceof QuestNet.Part part) payload = PARTS.accept(part);
        if (payload instanceof QuestNet.BookSync b) ClientQuests.onBook(b);
        else if (payload instanceof QuestNet.TeamSync t) ClientQuests.onTeam(t);
        else if (payload instanceof QuestNet.Notify n) ClientQuests.onNotify(n);
        else if (payload instanceof QuestNet.EditorLists l) EditorData.onLists(l);
    }
}
