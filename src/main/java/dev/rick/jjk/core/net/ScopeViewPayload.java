package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → a player at a mounted scope: looking through it ({@code on}), how far the anomaly has resolved for them
 * (0..1; 1 once revealed) and whether it is revealed (then the next use steps through).
 */
public record ScopeViewPayload(boolean on, float progress, boolean revealed) implements CustomPacketPayload {
    public static final Type<ScopeViewPayload> TYPE = new Type<>(JJK.id("scope_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScopeViewPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.on);
        buf.writeFloat(p.progress);
        buf.writeBoolean(p.revealed);
    }, buf -> new ScopeViewPayload(buf.readBoolean(), buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
