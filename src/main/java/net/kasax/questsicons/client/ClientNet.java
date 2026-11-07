package net.kasax.questsicons.client;

import net.kasax.questsicons.net.QuestNet;

/** Client side of {@link QuestNet}: sending (split into parts when too big) and every payload from the server. */
public final class ClientNet {
    private static final QuestNet.Assembler PARTS = new QuestNet.Assembler();

    private ClientNet() {
    }

    public static void send(QuestNet.Payload payload) {
        for (QuestNet.Payload p : QuestNet.split(payload, QuestNet.MAX_TO_SERVER)) QuestNet.CHANNEL.sendToServer(p);
    }

    /** Called on the client thread for every quest payload (QuestNet.register). */
    public static void received(QuestNet.Payload payload) {
        if (payload instanceof QuestNet.Part part) payload = PARTS.accept(part);
        if (payload instanceof QuestNet.BookSync b) ClientQuests.onBook(b);
        else if (payload instanceof QuestNet.TeamSync t) ClientQuests.onTeam(t);
        else if (payload instanceof QuestNet.Notify n) ClientQuests.onNotify(n);
        else if (payload instanceof QuestNet.EditorLists l) EditorData.onLists(l);
    }
}
