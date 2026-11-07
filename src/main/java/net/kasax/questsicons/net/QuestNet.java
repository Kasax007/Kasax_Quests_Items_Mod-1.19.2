package net.kasax.questsicons.net;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.compat.MixedLists;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** All quest packets. The book travels gzip-compressed; progress as plain NBT. */
public final class QuestNet {
    private QuestNet() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(QuestsIcons.MOD_ID, path);
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
            return MixedLists.unwrap(NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap()));
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
        public static final StreamCodec<FriendlyByteBuf, TeamSync> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(t -> new TeamSync(MixedLists.unwrap(t)), TeamSync::data).cast();

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
        public static final StreamCodec<FriendlyByteBuf, Edit> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(t -> new Edit(MixedLists.unwrap(t)), Edit::op).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: ids for the editor's pickers that only the server knows (advancements, structures, ...). */
    public record EditorLists(CompoundTag data) implements CustomPacketPayload {
        public static final Type<EditorLists> TYPE = new Type<>(rl("editor_lists"));
        public static final StreamCodec<FriendlyByteBuf, EditorLists> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(t -> new EditorLists(MixedLists.unwrap(t)), EditorLists::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------------------------------- large payloads
    // Minecraft 1.21.1 caps one custom payload (client -> server 32 KiB, server -> client 1 MiB) and this Fabric API
    // cannot split one. A big book (an imported FTB Quests book with thousands of quests), the editor's lists and edits
    // that carry whole chapters (paste, undo) therefore travel as numbered parts when they are too big.

    public static final int MAX_TO_CLIENT = 1_000_000;
    public static final int MAX_TO_SERVER = 30_000;
    /** The most a reassembled payload may hold (the book may be 16 MiB, edits and lists 8 MiB). */
    private static final int MAX_ASSEMBLED = 17 * 1024 * 1024;

    /** One part of a payload that was too big for a single packet. */
    public record Part(ResourceLocation target, int index, int count, byte[] data) implements CustomPacketPayload {
        public static final Type<Part> TYPE = new Type<>(rl("part"));
        public static final StreamCodec<FriendlyByteBuf, Part> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Part::target, ByteBufCodecs.VAR_INT, Part::index, ByteBufCodecs.VAR_INT, Part::count,
                ByteBufCodecs.byteArray(MAX_TO_CLIENT), Part::data, Part::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The payloads that may be split, by type id. */
    private static final Map<ResourceLocation, StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload>> SPLITTABLE = Map.of(
            BookSync.TYPE.id(), BookSync.CODEC, Edit.TYPE.id(), Edit.CODEC, EditorLists.TYPE.id(), EditorLists.CODEC);

    /** The payload itself if it fits into one packet of {@code max} bytes, else its parts. */
    @SuppressWarnings("unchecked")
    public static List<CustomPacketPayload> split(CustomPacketPayload payload, int max) {
        StreamCodec<FriendlyByteBuf, CustomPacketPayload> codec =
                (StreamCodec<FriendlyByteBuf, CustomPacketPayload>) SPLITTABLE.get(payload.type().id());
        if (codec == null) return List.of(payload);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, payload);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        if (bytes.length <= max) return List.of(payload);
        int count = (bytes.length + max - 1) / max;
        List<CustomPacketPayload> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parts.add(new Part(payload.type().id(), i, count, Arrays.copyOfRange(bytes, i * max, Math.min(bytes.length, (i + 1) * max))));
        }
        return parts;
    }

    /** Collects the parts of one sender; gives the whole payload back when its last part arrives. */
    public static final class Assembler {
        private ResourceLocation target;
        private int next;
        private ByteArrayOutputStream bytes;

        public @Nullable CustomPacketPayload accept(Part p) {
            StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload> codec = SPLITTABLE.get(p.target());
            if (codec == null || p.count() < 1 || p.index() >= p.count()) return null;
            if (p.index() == 0) {
                target = p.target();
                next = 0;
                bytes = new ByteArrayOutputStream();
            }
            if (bytes == null || !p.target().equals(target) || p.index() != next || bytes.size() + p.data().length > MAX_ASSEMBLED) {
                bytes = null;   // out of order or too big: drop it
                return null;
            }
            bytes.writeBytes(p.data());
            next++;
            if (next < p.count()) return null;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes.toByteArray()));
            bytes = null;
            return codec.decode(buf);
        }
    }

    // ------------------------------------------------------------------------------------------- loader glue (Fabric)

    public static void register() {
        PayloadTypeRegistry.playS2C().register(BookSync.TYPE, BookSync.CODEC);
        PayloadTypeRegistry.playS2C().register(TeamSync.TYPE, TeamSync.CODEC);
        PayloadTypeRegistry.playS2C().register(Notify.TYPE, Notify.CODEC);
        PayloadTypeRegistry.playS2C().register(EditorLists.TYPE, EditorLists.CODEC);
        PayloadTypeRegistry.playS2C().register(Part.TYPE, Part.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.playC2S().register(Edit.TYPE, Edit.CODEC);
        PayloadTypeRegistry.playC2S().register(Part.TYPE, Part.CODEC);
    }

    /** Sends a payload to a player, in parts if it is too big for one packet. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        for (CustomPacketPayload p : split(payload, MAX_TO_CLIENT)) QuestNet.sendToPlayer(player, p);
    }
}
