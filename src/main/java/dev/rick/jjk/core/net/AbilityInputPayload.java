package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Client → server: an ability key was pressed or released. Carries movement input for directional abilities and the crosshair target as a hint. */
public record AbilityInputPayload(int slot, boolean pressed, float forward, float strafe, int targetHint) implements CustomPacketPayload {
    public static final Type<AbilityInputPayload> TYPE = new Type<>(JJK.id("ability_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityInputPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeByte(p.slot); buf.writeBoolean(p.pressed); buf.writeFloat(p.forward); buf.writeFloat(p.strafe); buf.writeVarInt(p.targetHint + 1);
    }, buf -> new AbilityInputPayload(buf.readByte(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
