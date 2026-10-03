package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → one duellist: a skill check starts now. The needle goes once round in {@code periodMs}; the success zone
 * starts {@code zoneStart} degrees round, {@code goodWidth} wide, its first {@code greatWidth} degrees the GREAT part.
 * Only shown to them: the server alone judges the press.
 */
public record BeamClashCheckPayload(int session, int check, int periodMs, float zoneStart, float goodWidth, float greatWidth)
        implements CustomPacketPayload {
    public static final Type<BeamClashCheckPayload> TYPE = new Type<>(JJK.id("beam_clash_check"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashCheckPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeVarInt(p.check); buf.writeVarInt(p.periodMs);
        buf.writeFloat(p.zoneStart); buf.writeFloat(p.goodWidth); buf.writeFloat(p.greatWidth);
    }, buf -> new BeamClashCheckPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
