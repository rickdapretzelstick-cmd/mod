package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the shooter and everyone tracking them: a rifle's phase (RifleServer.Phase ordinal) and how many ticks it
 * lasts (clients play the matching clip, at the speed that fits), its beam output, and for the shooter's own HUD the
 * reserve, whether the beam is learned, and the aim's sway and settle time (the reticle drifts as the server's aim does).
 */
public record RifleStatePayload(int entity, int phase, int duration, float output, float energy, float capacity, boolean beam,
                                float sway, int settle) implements CustomPacketPayload {
    public static final Type<RifleStatePayload> TYPE = new Type<>(JJK.id("rifle_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RifleStatePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entity);
        buf.writeVarInt(p.phase);
        buf.writeVarInt(p.duration);
        buf.writeFloat(p.output);
        buf.writeFloat(p.energy);
        buf.writeFloat(p.capacity);
        buf.writeBoolean(p.beam);
        buf.writeFloat(p.sway);
        buf.writeVarInt(p.settle);
    }, buf -> new RifleStatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readBoolean(), buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
