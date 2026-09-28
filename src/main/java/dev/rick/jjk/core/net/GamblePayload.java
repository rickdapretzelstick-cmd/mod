package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the state of an Idle Death Gamble (drives the gamble HUD and the giant reels over the domain).
 * {@code state}: 0 spinning, 1 riichi, 2 miss, 3 jackpot, 4 done (no attempts left), 5 removed.
 * {@code scenario}: 0 Transit Card, 1 Travel Emergency. {@code signal}: 0 green (one star), 2 gold (two stars), 3 rainbow (pity jackpot).
 * {@code chance} and {@code bonus} are only filled in for the gambler.
 */
public record GamblePayload(int domainId, int ownerId, int state, int progress, int required, int attempt, int maxAttempts, int scenario,
                            int signal, int reel0, int reel1, int reel2, int stateAge, int stateDuration, float chance, String bonus)
        implements CustomPacketPayload {
    public static final int SPINNING = 0, RIICHI = 1, MISS = 2, JACKPOT = 3, DONE = 4, REMOVED = 5;
    public static final Type<GamblePayload> TYPE = new Type<>(JJK.id("gamble"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GamblePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.domainId); buf.writeVarInt(p.ownerId); buf.writeByte(p.state); buf.writeByte(p.progress); buf.writeByte(p.required);
        buf.writeByte(p.attempt); buf.writeByte(p.maxAttempts); buf.writeByte(p.scenario); buf.writeByte(p.signal);
        buf.writeByte(p.reel0); buf.writeByte(p.reel1); buf.writeByte(p.reel2); buf.writeVarInt(p.stateAge); buf.writeVarInt(p.stateDuration);
        buf.writeFloat(p.chance); buf.writeUtf(p.bonus, 96);
    }, buf -> new GamblePayload(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
            buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readUtf(96)));

    /** Ticks into a Riichi when the third reel stops (its length comes with the state: halved after an even jackpot). */
    public static int revealAt(GamblePayload p) {
        int len = p.state() == RIICHI && p.stateDuration() > 0 ? p.stateDuration() : dev.rick.jjk.config.JJKConfig.get().hakari.riichiTicks;
        return len - dev.rick.jjk.hakari.Gamble.REVEAL_OFFSET;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
