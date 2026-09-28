package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: a Rhythm beat press, stamped with the client's own clock (ticks since Rhythm started, sub-tick
 * precise) so the judgement isn't at the mercy of network jitter.
 */
public record RhythmInputPayload(float time) implements CustomPacketPayload {
    public static final Type<RhythmInputPayload> TYPE = new Type<>(JJK.id("rhythm_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RhythmInputPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeFloat(p.time), buf -> new RhythmInputPayload(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
