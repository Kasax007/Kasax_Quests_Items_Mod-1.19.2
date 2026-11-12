package net.kasax.questsicons.net;

import net.kasax.questsicons.server.QuestServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.kasax.questsicons.QuestsIcons;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** All quest packets. The book travels gzip-compressed; progress as plain NBT. */
public final class QuestNet {
    private QuestNet() {
    }

    private static Identifier rl(String path) {
        return Identifier.fromNamespaceAndPath(QuestsIcons.MOD_ID, path);
    }

    public static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CompoundTag decompress(byte[] data) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Server -> client: the complete quest book. */
    public record BookSync(byte[] data, boolean canEdit) implements CustomPacketPayload {
        public static final Type<BookSync> TYPE = new Type<>(rl("book"));
        public static final StreamCodec<FriendlyByteBuf, BookSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.byteArray(16 * 1024 * 1024), BookSync::data, ByteBufCodecs.BOOL, BookSync::canEdit, BookSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the player's team progress. */
    public record TeamSync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<TeamSync> TYPE = new Type<>(rl("team"));
        public static final StreamCodec<FriendlyByteBuf, TeamSync> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(TeamSync::new, TeamSync::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: something happened (kind 0 = quest done, 1 = chapter done, 2 = open book, 3 = reward claimed). */
    public record Notify(int kind, long id) implements CustomPacketPayload {
        public static final int QUEST = 0;
        public static final int CHAPTER = 1;
        public static final int OPEN = 2;
        public static final int CLAIMED = 3;
        public static final Type<Notify> TYPE = new Type<>(rl("notify"));
        public static final StreamCodec<FriendlyByteBuf, Notify> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Notify::kind, ByteBufCodecs.VAR_LONG, Notify::id, Notify::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: submit a task, claim a reward (extra = choice index), claim all. */
    public record Action(int action, long id, int extra) implements CustomPacketPayload {
        public static final int SUBMIT = 0;
        public static final int CLAIM = 1;
        public static final int CLAIM_ALL = 2;
        public static final int REQUEST_SYNC = 3;
        public static final Type<Action> TYPE = new Type<>(rl("action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::action, ByteBufCodecs.VAR_LONG, Action::id, ByteBufCodecs.VAR_INT, Action::extra, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: an editor operation (needs operator rights). */
    public record Edit(CompoundTag op) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(rl("edit"));
        public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(Edit::new, Edit::op).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: ids for the editor's pickers that only the server knows (advancements, structures, ...). */
    public record EditorLists(CompoundTag data) implements CustomPacketPayload {
        public static final Type<EditorLists> TYPE = new Type<>(rl("editor_lists"));
        public static final StreamCodec<FriendlyByteBuf, EditorLists> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(EditorLists::new, EditorLists::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Mod bus: the payloads and their server handlers (the client registers its handlers in QuestsIconsClient).
     * A big book (an imported FTB Quests book with thousands of quests) or a big edit can be larger than one custom
     * payload may be; NeoForge splits such packets into parts and joins them again on the other side.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(BookSync.TYPE, BookSync.CODEC);
        r.playToClient(TeamSync.TYPE, TeamSync.CODEC);
        r.playToClient(Notify.TYPE, Notify.CODEC);
        r.playToClient(EditorLists.TYPE, EditorLists.CODEC);
        r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> QuestServer.received((ServerPlayer) ctx.player(), p));
        r.playToServer(Edit.TYPE, Edit.CODEC, (p, ctx) -> QuestServer.received((ServerPlayer) ctx.player(), p));
    }
}
