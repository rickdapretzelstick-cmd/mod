package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player picked a character on the select screen ("" = none). */
public record CharacterSelectPayload(String character) implements CustomPacketPayload {
    public static final Type<CharacterSelectPayload> TYPE = new Type<>(JJK.id("character_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSelectPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.character, 64), buf -> new CharacterSelectPayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
