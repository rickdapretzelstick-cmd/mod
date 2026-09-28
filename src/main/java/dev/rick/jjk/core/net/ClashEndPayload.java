package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the clash is over. Winner is a participant index, or -1 if it was called off. */
public record ClashEndPayload(int session, int winner, int outcome) implements CustomPacketPayload {
    public static final Type<ClashEndPayload> TYPE = new Type<>(JJK.id("clash_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClashEndPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeByte(p.winner + 1); buf.writeByte(p.outcome);
    }, buf -> new ClashEndPayload(buf.readVarInt(), buf.readByte() - 1, buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
