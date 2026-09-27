package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: play a named visual effect. */
public record FxPayload(String id, Vec3 pos, Vec3 dir, float scale, int entityId) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(JJK.id("fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUtf(p.id);
        buf.writeDouble(p.pos.x); buf.writeDouble(p.pos.y); buf.writeDouble(p.pos.z);
        buf.writeFloat((float) p.dir.x); buf.writeFloat((float) p.dir.y); buf.writeFloat((float) p.dir.z);
        buf.writeFloat(p.scale);
        buf.writeVarInt(p.entityId + 1);
    }, buf -> new FxPayload(buf.readUtf(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readFloat(), buf.readVarInt() - 1));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
