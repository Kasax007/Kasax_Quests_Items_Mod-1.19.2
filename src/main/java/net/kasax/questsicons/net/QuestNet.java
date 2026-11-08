package net.kasax.questsicons.net;

import io.netty.buffer.Unpooled;
import net.kasax.questsicons.QuestsIcons;
import net.kasax.questsicons.client.ClientNet;
import net.kasax.questsicons.compat.MixedLists;
import net.kasax.questsicons.server.QuestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * All quest packets. The book travels gzip-compressed; progress as plain NBT.
 *
 * <p>Minecraft 1.20.1 has no CustomPacketPayload/StreamCodec yet: every payload is a record that writes itself to a
 * FriendlyByteBuf and is read back by its static {@code read}; Forge's SimpleChannel carries them.
 */
public final class QuestNet {
    private QuestNet() {
    }

    /** A quest packet. */
    public interface Payload {
        void write(FriendlyByteBuf buf);
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
            return MixedLists.unwrap(NbtIo.readCompressed(new ByteArrayInputStream(data)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CompoundTag readTag(FriendlyByteBuf buf) {
        CompoundTag t = buf.readAnySizeNbt();
        return MixedLists.unwrap(t == null ? new CompoundTag() : t);
    }

    /** Server -> client: the complete quest book. */
    public record BookSync(byte[] data, boolean canEdit) implements Payload {
        public void write(FriendlyByteBuf buf) {
            buf.writeByteArray(data);
            buf.writeBoolean(canEdit);
        }

        public static BookSync read(FriendlyByteBuf buf) {
            return new BookSync(buf.readByteArray(16 * 1024 * 1024), buf.readBoolean());
        }
    }

    /** Server -> client: the player's team progress. */
    public record TeamSync(CompoundTag data) implements Payload {
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(data);
        }

        public static TeamSync read(FriendlyByteBuf buf) {
            return new TeamSync(readTag(buf));
        }
    }

    /** Server -> client: something happened (kind 0 = quest done, 1 = chapter done, 2 = open book, 3 = reward claimed). */
    public record Notify(int kind, long id) implements Payload {
        public static final int QUEST = 0;
        public static final int CHAPTER = 1;
        public static final int OPEN = 2;
        public static final int CLAIMED = 3;

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeVarLong(id);
        }

        public static Notify read(FriendlyByteBuf buf) {
            return new Notify(buf.readVarInt(), buf.readVarLong());
        }
    }

    /** Client -> server: submit a task, claim a reward (extra = choice index), claim all. */
    public record Action(int action, long id, int extra) implements Payload {
        public static final int SUBMIT = 0;
        public static final int CLAIM = 1;
        public static final int CLAIM_ALL = 2;
        public static final int REQUEST_SYNC = 3;

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeVarLong(id);
            buf.writeVarInt(extra);
        }

        public static Action read(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readVarLong(), buf.readVarInt());
        }
    }

    /** Client -> server: an editor operation (needs operator rights). */
    public record Edit(CompoundTag op) implements Payload {
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(op);
        }

        public static Edit read(FriendlyByteBuf buf) {
            return new Edit(readTag(buf));
        }
    }

    /** Server -> client: ids for the editor's pickers that only the server knows (advancements, structures, ...). */
    public record EditorLists(CompoundTag data) implements Payload {
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(data);
        }

        public static EditorLists read(FriendlyByteBuf buf) {
            return new EditorLists(readTag(buf));
        }
    }

    // ------------------------------------------------------------------------------------------- large payloads
    // Minecraft caps one custom payload (client -> server 32 KiB, server -> client 1 MiB). A big book (an imported FTB
    // Quests book with thousands of quests), the editor's lists and edits that carry whole chapters (paste, undo)
    // therefore travel as numbered parts when they are too big.

    // -Dquestsicons.maxPayload=<bytes> lowers both limits (the self-test uses it to send even a small book in parts).
    public static final int MAX_TO_CLIENT = Math.min(1_000_000, Integer.getInteger("questsicons.maxPayload", 1_000_000));
    public static final int MAX_TO_SERVER = Math.min(30_000, Integer.getInteger("questsicons.maxPayload", 30_000));
    /** The most a reassembled payload may hold (the book may be 16 MiB, edits and lists 8 MiB). */
    private static final int MAX_ASSEMBLED = 17 * 1024 * 1024;

    /** One part of a payload that was too big for a single packet ({@code target}: the payload's name). */
    public record Part(String target, int index, int count, byte[] data) implements Payload {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(target);
            buf.writeVarInt(index);
            buf.writeVarInt(count);
            buf.writeByteArray(data);
        }

        public static Part read(FriendlyByteBuf buf) {
            return new Part(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_TO_CLIENT));
        }
    }

    /** The payloads that may be split, by name. */
    private static final Map<String, Function<FriendlyByteBuf, ? extends Payload>> SPLITTABLE = Map.of(
            "book", BookSync::read, "edit", Edit::read, "editor_lists", EditorLists::read);

    private static @Nullable String splitName(Payload p) {
        return p instanceof BookSync ? "book" : p instanceof Edit ? "edit" : p instanceof EditorLists ? "editor_lists" : null;
    }

    /** The payload itself if it fits into one packet of {@code max} bytes, else its parts. */
    public static List<Payload> split(Payload payload, int max) {
        String name = splitName(payload);
        if (name == null) return List.of(payload);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        if (bytes.length <= max) return List.of(payload);
        int count = (bytes.length + max - 1) / max;
        List<Payload> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parts.add(new Part(name, i, count, Arrays.copyOfRange(bytes, i * max, Math.min(bytes.length, (i + 1) * max))));
        }
        return parts;
    }

    /** Collects the parts of one sender; gives the whole payload back when its last part arrives. */
    public static final class Assembler {
        private String target;
        private int next;
        private ByteArrayOutputStream bytes;

        public @Nullable Payload accept(Part p) {
            Function<FriendlyByteBuf, ? extends Payload> reader = SPLITTABLE.get(p.target());
            if (reader == null || p.count() < 1 || p.index() >= p.count()) return null;
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
            return reader.apply(buf);
        }
    }

    // ------------------------------------------------------------------------------------------- loader glue (Forge)

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QuestsIcons.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** Registers every payload with its handler (client handlers only run on the client). */
    public static void register() {
        int id = 0;
        toClient(id++, BookSync.class, BookSync::read);
        toClient(id++, TeamSync.class, TeamSync::read);
        toClient(id++, Notify.class, Notify::read);
        toClient(id++, EditorLists.class, EditorLists::read);
        toServer(id++, Action.class, Action::read);
        toServer(id++, Edit.class, Edit::read);
        // Parts travel both ways: one registration (a message class may only be registered once), the side decides.
        CHANNEL.messageBuilder(Part.class, id++).encoder(Payload::write).decoder(Part::read)
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) QuestServer.received(sender, p);
                    else ClientNet.received(p);
                }).add();
    }

    private static <T extends Payload> void toClient(int id, Class<T> type, Function<FriendlyByteBuf, T> read) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT).encoder(Payload::write).decoder(read)
                .consumerMainThread((p, ctx) -> ClientNet.received(p)).add();
    }

    private static <T extends Payload> void toServer(int id, Class<T> type, Function<FriendlyByteBuf, T> read) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER).encoder(Payload::write).decoder(read)
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) QuestServer.received(sender, p);
                }).add();
    }

    /** Sends a payload to a player, in parts if it is too big for one packet. */
    public static void sendToPlayer(ServerPlayer player, Payload payload) {
        for (Payload p : split(payload, MAX_TO_CLIENT)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), p);
    }
}
