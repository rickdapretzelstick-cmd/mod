package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: start a pose animation on an entity ("" stops the current one). */
public record AnimPayload(int entityId, String anim, float speed) implements CustomPacketPayload {
    public static final Type<AnimPayload> TYPE = new Type<>(JJK.id("anim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnimPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entityId); buf.writeUtf(p.anim); buf.writeFloat(p.speed);
    }, buf -> new AnimPayload(buf.readVarInt(), buf.readUtf(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
