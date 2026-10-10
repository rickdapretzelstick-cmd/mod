package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: one beat of a character storyline's presentation, drawn by the client's story overlay.
 * <ul>
 *   <li>{@link #CARD}: a title card (a big word, a line under it) in {@code color}, held {@code ticks}.</li>
 *   <li>{@link #BLIND}: the Infused Blindfold's vision on ({@code ticks} > 0) or off: the world nearly black, cursed
 *   energy the only light.</li>
 *   <li>{@link #SIX_EYES}: the blindfold comes off: a white-out, an iris of blue light opening, everything seen at once.</li>
 *   <li>{@link #FILM}: the screen becomes an old film for {@code ticks}: letterbox, grain, a flicker; a line of dialogue.</li>
 *   <li>{@link #METER}: a small persistent line at the top of the screen (a trial's wave, trust, the wager), "" clears.</li>
 * </ul>
 */
public record StoryPayload(int kind, String title, String subtitle, int color, int ticks) implements CustomPacketPayload {
    public static final int CARD = 0, BLIND = 1, SIX_EYES = 2, FILM = 3, METER = 4;

    public static final Type<StoryPayload> TYPE = new Type<>(JJK.id("story"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoryPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.kind);
        buf.writeUtf(p.title, 128);
        buf.writeUtf(p.subtitle, 256);
        buf.writeInt(p.color);
        buf.writeVarInt(p.ticks);
    }, buf -> new StoryPayload(buf.readVarInt(), buf.readUtf(128), buf.readUtf(256), buf.readInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
