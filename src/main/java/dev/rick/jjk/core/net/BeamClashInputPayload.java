package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the clash key was pressed {@code elapsedMs} after this client started showing skill check
 * {@code check}. A timing only: the server checks it against its own clock and the player's latency, and judges it.
 */
public record BeamClashInputPayload(int session, int check, int elapsedMs) implements CustomPacketPayload {
    public static final Type<BeamClashInputPayload> TYPE = new Type<>(JJK.id("beam_clash_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamClashInputPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session); buf.writeVarInt(p.check); buf.writeVarInt(Math.max(0, p.elapsedMs));
    }, buf -> new BeamClashInputPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
