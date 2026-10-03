package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → everyone near: the physical state of a beam clash, a few times a second (the clients ease between them).
 * {@code collision} is how far along the line from beam A's source to beam B's the two meet (0..1); it IS the clash's
 * score, the same number the meter shows. {@code phase} is {@link dev.rick.jjk.core.clash.BeamClashSession.Phase},
 * {@code outcome} its result once decided (0 none, 1 A won, 2 B won, 3 tie, 4 cancelled).
 */
public record BeamClashStatePayload(int session, int phase, int phaseAge, int aId, int bId, String aKind, String bKind, Vec3 aOrigin,
                                    Vec3 bOrigin, float collision, float push, int powerA, int powerB, float intensity, int outcome)
        implements CustomPacketPayload {
    public static final Type<BeamClashStatePayload> TYPE = new Type<>(JJK.id("beam_clash_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashStatePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeByte(p.phase); buf.writeVarInt(p.phaseAge); buf.writeVarInt(p.aId); buf.writeVarInt(p.bId);
        buf.writeUtf(p.aKind, 16); buf.writeUtf(p.bKind, 16);
        buf.writeDouble(p.aOrigin.x); buf.writeDouble(p.aOrigin.y); buf.writeDouble(p.aOrigin.z);
        buf.writeDouble(p.bOrigin.x); buf.writeDouble(p.bOrigin.y); buf.writeDouble(p.bOrigin.z);
        buf.writeFloat(p.collision); buf.writeFloat(p.push); buf.writeVarInt(p.powerA + 1000); buf.writeVarInt(p.powerB + 1000);
        buf.writeFloat(p.intensity); buf.writeByte(p.outcome);
    }, buf -> new BeamClashStatePayload(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(16), buf.readUtf(16), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1000,
            buf.readVarInt() - 1000, buf.readFloat(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
