package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → a True Cannon player: their Overheat meter (0-100), and whether Decadence has them (awakened). */
public record RyuPayload(float heat, boolean decadence) implements CustomPacketPayload {
    public static final Type<RyuPayload> TYPE = new Type<>(JJK.id("ryu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RyuPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeFloat(p.heat); buf.writeBoolean(p.decadence);
    }, buf -> new RyuPayload(buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
