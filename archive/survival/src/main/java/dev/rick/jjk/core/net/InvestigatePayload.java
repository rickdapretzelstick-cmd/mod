package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: "I'll look into this one" (a notice on a news board), or "" to stop. The server decides. */
public record InvestigatePayload(String incident) implements CustomPacketPayload {
    public static final Type<InvestigatePayload> TYPE = new Type<>(JJK.id("investigate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InvestigatePayload> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.incident, 64),
            buf -> new InvestigatePayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
