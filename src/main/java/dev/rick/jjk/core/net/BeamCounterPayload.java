package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: an ultimate beam is coming at you and your Ultimate key answers it with your own until {@code ticks}
 * from now (0 = the chance is gone). {@code attack} names what is coming, {@code answer} what you would fire.
 */
public record BeamCounterPayload(int attackerId, int ticks, String attack, String answer) implements CustomPacketPayload {
    public static final Type<BeamCounterPayload> TYPE = new Type<>(JJK.id("beam_counter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamCounterPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.attackerId); buf.writeVarInt(p.ticks); buf.writeUtf(p.attack, 48); buf.writeUtf(p.answer, 48);
    }, buf -> new BeamCounterPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(48), buf.readUtf(48)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
