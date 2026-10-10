package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → a player holding a Cursed Compass: what it can feel. {@code state}: {@link #DORMANT} (no investigation),
 * {@link #FAINT} (investigating, but too far from the place for a trail), {@link #TRAIL} (the source is at {@code x,y,z}),
 * {@link #GONE} (what it followed has been exorcised). The source's position is only ever sent once the player is in
 * the reported area.
 */
public record CompassPayload(int state, double x, double y, double z) implements CustomPacketPayload {
    public static final int DORMANT = 0, FAINT = 1, TRAIL = 2, GONE = 3;
    public static final Type<CompassPayload> TYPE = new Type<>(JJK.id("cursed_compass"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompassPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.state);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
    }, buf -> new CompassPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
