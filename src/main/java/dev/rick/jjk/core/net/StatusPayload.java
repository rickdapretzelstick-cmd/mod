package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: an entity's combat statuses changed. */
public record StatusPayload(int entityId, int[] ticks, boolean guarding) implements CustomPacketPayload {
    public static final Type<StatusPayload> TYPE = new Type<>(JJK.id("status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entityId); buf.writeVarIntArray(p.ticks); buf.writeBoolean(p.guarding);
    }, buf -> new StatusPayload(buf.readVarInt(), buf.readVarIntArray(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
