package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → everyone near: a skill check was judged (0 GREAT, 1 GOOD, 2 MISS) for side {@code side} (0 = beam A, 1 = B).
 * Everyone sees that beam surge or falter; only its duellist's own dial reacts to {@code check}.
 */
public record BeamClashJudgePayload(int session, int side, int check, int judgement, float angle) implements CustomPacketPayload {
    public static final Type<BeamClashJudgePayload> TYPE = new Type<>(JJK.id("beam_clash_judge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashJudgePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeByte(p.side); buf.writeVarInt(p.check); buf.writeByte(p.judgement); buf.writeFloat(p.angle);
    }, buf -> new BeamClashJudgePayload(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readByte(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
