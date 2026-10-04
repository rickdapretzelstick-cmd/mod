package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → player: the notices on the news board they just read. {@code status}: 0 a fresh report, 1 still going on
 * (someone found it), 2 a follow-up saying it's over.
 */
public record NewsBoardPayload(String village, List<Note> notes) implements CustomPacketPayload {
    public record Note(String headline, String body, int status, int daysAgo) {}

    public static final Type<NewsBoardPayload> TYPE = new Type<>(JJK.id("news_board"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NewsBoardPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUtf(p.village, 128);
        buf.writeVarInt(p.notes.size());
        for (Note n : p.notes) {
            buf.writeUtf(n.headline, 256);
            buf.writeUtf(n.body, 4096);
            buf.writeVarInt(n.status);
            buf.writeVarInt(n.daysAgo);
        }
    }, buf -> {
        String v = buf.readUtf(128);
        int n = Math.min(16, buf.readVarInt());
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < n; i++) notes.add(new Note(buf.readUtf(256), buf.readUtf(4096), buf.readVarInt(), buf.readVarInt()));
        return new NewsBoardPayload(v, notes);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
