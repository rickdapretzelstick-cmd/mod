package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: whether the player's JJK Combat Mode is on (sent on joining and on every toggle). Off, a drawn cursed
 * tool is holstered, and it comes back out when Combat Mode does.
 */
public record CombatModePayload(boolean on) implements CustomPacketPayload {
    public static final Type<CombatModePayload> TYPE = new Type<>(JJK.id("combat_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatModePayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, CombatModePayload::on, CombatModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
