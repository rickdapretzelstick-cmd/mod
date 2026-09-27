package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Client → server: melee input. */
public record MeleeInputPayload(int kind, int flags, int targetHint) implements CustomPacketPayload {
    public static final Type<MeleeInputPayload> TYPE = new Type<>(JJK.id("melee_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeInputPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeByte(p.kind); buf.writeByte(p.flags); buf.writeVarInt(p.targetHint + 1);
    }, buf -> new MeleeInputPayload(buf.readByte(), buf.readByte(), buf.readVarInt() - 1));
    public static final int LIGHT = 0, HEAVY_START = 1, HEAVY_RELEASE = 2;
    public static final int FLAG_JUMP = 1, FLAG_SPRINT = 2, FLAG_AIRBORNE = 4, FLAG_LOOK_DOWN = 8;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
