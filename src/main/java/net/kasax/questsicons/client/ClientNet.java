package net.kasax.questsicons.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.net.QuestNet;
import net.minecraft.resources.ResourceLocation;

/** Client side of {@link QuestNet}: sending (split into parts when too big) and every payload from the server. */
public final class ClientNet {
    private static final QuestNet.Assembler PARTS = new QuestNet.Assembler();

    private ClientNet() {
    }

    public static void send(QuestNet.Payload payload) {
        for (QuestNet.Payload p : QuestNet.split(payload, QuestNet.MAX_TO_SERVER)) ClientPlayNetworking.send(QuestNet.channel(p), QuestNet.encode(p));
    }

    /** Registers the client-side receivers (read on the network thread, handled on the client thread). */
    public static void init() {
        QuestNet.toClient().forEach((name, read) -> ClientPlayNetworking.registerGlobalReceiver(new ResourceLocation(QuestsIcons.MOD_ID, name),
                (client, handler, buf, sender) -> {
                    QuestNet.Payload p = read.apply(buf);
                    client.execute(() -> received(p));
                }));
    }

    /** Called on the client thread for every quest payload. */
    public static void received(QuestNet.Payload payload) {
        if (payload instanceof QuestNet.Part part) payload = PARTS.accept(part);
        if (payload instanceof QuestNet.BookSync b) ClientQuests.onBook(b);
        else if (payload instanceof QuestNet.TeamSync t) ClientQuests.onTeam(t);
        else if (payload instanceof QuestNet.Notify n) ClientQuests.onNotify(n);
        else if (payload instanceof QuestNet.EditorLists l) EditorData.onLists(l);
    }
}
