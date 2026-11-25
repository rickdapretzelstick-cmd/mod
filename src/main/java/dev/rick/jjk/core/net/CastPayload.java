package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: an entity started/advanced/stopped casting (ability "" = stopped). Drives charge visuals for everyone. */
public record CastPayload(int entityId, String ability, int phase, int duration) implements CustomPacketPayload {
    public static final Type<CastPayload> TYPE = new Type<>(JJK.id("cast"));
    /** The phase of the marker sent just before a cast starts (not a real phase: no charge visuals read it). */
    public static final int BEGIN = -1;
    public static final StreamCodec<RegistryFriendlyByteBuf, CastPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entityId); buf.writeUtf(p.ability); buf.writeVarInt(p.phase + 1); buf.writeVarInt(p.duration);
    }, buf -> new CastPayload(buf.readVarInt(), buf.readUtf(), buf.readVarInt() - 1, buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
