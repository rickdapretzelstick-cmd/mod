package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → attacker: hit confirm with combo count, for the combo counter and hit feedback. */
public record ComboPayload(int targetId, int count, float damage, int outcome) implements CustomPacketPayload {
    public static final Type<ComboPayload> TYPE = new Type<>(JJK.id("combo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComboPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.targetId); buf.writeVarInt(p.count); buf.writeFloat(p.damage); buf.writeByte(p.outcome);
    }, buf -> new ComboPayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
