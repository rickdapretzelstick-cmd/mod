package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: one judged input (note -1 for a press with no note) and the new state of the clash. */
public record ClashUpdatePayload(int session, int participant, int note, int judgement, float offsetMs, float meter, int score, int streak,
                                 float accuracy) implements CustomPacketPayload {
    public static final Type<ClashUpdatePayload> TYPE = new Type<>(JJK.id("clash_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClashUpdatePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeByte(p.participant); buf.writeVarInt(p.note + 1); buf.writeByte(p.judgement);
        buf.writeFloat(p.offsetMs); buf.writeFloat(p.meter); buf.writeVarInt(p.score); buf.writeVarInt(p.streak); buf.writeFloat(p.accuracy);
    }, buf -> new ClashUpdatePayload(buf.readVarInt(), buf.readByte(), buf.readVarInt() - 1, buf.readByte(), buf.readFloat(), buf.readFloat(),
            buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
