package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: a clash lane was pressed at this client game time (sub-tick precision). */
public record ClashInputPayload(int session, int lane, double time) implements CustomPacketPayload {
    public static final Type<ClashInputPayload> TYPE = new Type<>(JJK.id("clash_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClashInputPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeByte(p.lane); buf.writeDouble(p.time);
    }, buf -> new ClashInputPayload(buf.readVarInt(), buf.readByte(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
