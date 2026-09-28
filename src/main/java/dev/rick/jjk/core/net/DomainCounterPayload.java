package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: you can counter this opening domain with your Awakening button until {@code ticks} from now (0 = closed). */
public record DomainCounterPayload(int openerId, int ticks, String domain) implements CustomPacketPayload {
    public static final Type<DomainCounterPayload> TYPE = new Type<>(JJK.id("domain_counter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DomainCounterPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.openerId); buf.writeVarInt(p.ticks); buf.writeUtf(p.domain, 64);
    }, buf -> new DomainCounterPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
