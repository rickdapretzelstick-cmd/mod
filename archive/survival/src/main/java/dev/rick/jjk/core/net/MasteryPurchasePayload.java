package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: "buy this node". The server decides ({@link dev.rick.jjk.progression.mastery.Mastery#purchase}). */
public record MasteryPurchasePayload(String tree, String node) implements CustomPacketPayload {
    public static final Type<MasteryPurchasePayload> TYPE = new Type<>(JJK.id("mastery_purchase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MasteryPurchasePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUtf(p.tree, 96);
        buf.writeUtf(p.node, 96);
    }, buf -> new MasteryPurchasePayload(buf.readUtf(96), buf.readUtf(96)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
