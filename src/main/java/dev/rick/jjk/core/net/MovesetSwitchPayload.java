package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: switch between the innate technique's moveset and the equipped cursed tool's. The server decides. */
public record MovesetSwitchPayload() implements CustomPacketPayload {
    public static final Type<MovesetSwitchPayload> TYPE = new Type<>(JJK.id("moveset_switch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovesetSwitchPayload> CODEC = StreamCodec.unit(new MovesetSwitchPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
