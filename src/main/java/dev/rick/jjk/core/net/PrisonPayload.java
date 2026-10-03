package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the sealed player: whether they are inside the Prison Realm, where the realm lies outside (for the outside
 * view), and how far their escape has got (stage 0-3, the locks broken this stage as bits, someone's rescue 0-100).
 */
public record PrisonPayload(boolean sealed, long realmPos, int stage, int broken, int rescue) implements CustomPacketPayload {
    public static final Type<PrisonPayload> TYPE = new Type<>(JJK.id("prison"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PrisonPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.sealed);
        buf.writeLong(p.realmPos);
        buf.writeByte(p.stage);
        buf.writeByte(p.broken);
        buf.writeByte(p.rescue);
    }, buf -> new PrisonPayload(buf.readBoolean(), buf.readLong(), buf.readByte(), buf.readByte(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
