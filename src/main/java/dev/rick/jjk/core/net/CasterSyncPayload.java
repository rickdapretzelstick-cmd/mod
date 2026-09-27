package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → owner: resources, cooldowns and toggles for the HUD and client prediction. */
public record CasterSyncPayload(String character, float energy, float maxEnergy, int[] cooldowns, int[] maxCooldowns, int[] charges, int flags, String activeCast, int castTicks) implements CustomPacketPayload {
    public static final Type<CasterSyncPayload> TYPE = new Type<>(JJK.id("caster_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CasterSyncPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUtf(p.character); buf.writeFloat(p.energy); buf.writeFloat(p.maxEnergy);
        buf.writeVarIntArray(p.cooldowns); buf.writeVarIntArray(p.maxCooldowns); buf.writeVarIntArray(p.charges);
        buf.writeVarInt(p.flags); buf.writeUtf(p.activeCast); buf.writeVarInt(p.castTicks);
    }, buf -> new CasterSyncPayload(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
    public static final int FLAG_INFINITY = 1, FLAG_NO_COST = 2, FLAG_GUARDING = 4, FLAG_STANCE = 8;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
