package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: camera feedback (shake, flash, fov punch, hit confirm). */
public record CameraPayload(int kind, float intensity, int duration, int color) implements CustomPacketPayload {
    public static final Type<CameraPayload> TYPE = new Type<>(JJK.id("camera"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeByte(p.kind); buf.writeFloat(p.intensity); buf.writeVarInt(p.duration); buf.writeInt(p.color);
    }, buf -> new CameraPayload(buf.readByte(), buf.readFloat(), buf.readVarInt(), buf.readInt()));
    public static final int SHAKE = 0, FLASH = 1, FOV = 2, HITSTOP = 3, IMPACT = 4;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
