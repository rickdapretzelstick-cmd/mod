package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.mastery.MasteryData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → a player: their Mastery record, the kit they legitimately own ("" for none), and whether Mastery gates them, and whether the trees are on at all. */
public record MasterySyncPayload(MasteryData data, String kit, boolean gated, boolean enabled) implements CustomPacketPayload {
    public static final Type<MasterySyncPayload> TYPE = new Type<>(JJK.id("mastery_sync"));
    private static final StreamCodec<io.netty.buffer.ByteBuf, MasteryData> DATA = ByteBufCodecs.fromCodec(MasteryData.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, MasterySyncPayload> CODEC = StreamCodec.of((buf, p) -> {
        DATA.encode(buf, p.data);
        buf.writeUtf(p.kit, 64);
        buf.writeBoolean(p.gated);
        buf.writeBoolean(p.enabled);
    }, buf -> new MasterySyncPayload(DATA.decode(buf), buf.readUtf(64), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
