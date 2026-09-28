package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server → client: domain state. Phase: 0 forming, 1 active, 2 clashing, 3 collapsing, 4 removed. */
public record DomainPayload(int id, int ownerId, String definition, Vec3 center, float radius, int phase, int age, int duration, int clashWith,
                            int formationTicks, int thickness) implements CustomPacketPayload {
    public static final Type<DomainPayload> TYPE = new Type<>(JJK.id("domain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DomainPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.id); buf.writeVarInt(p.ownerId); buf.writeUtf(p.definition);
        buf.writeDouble(p.center.x); buf.writeDouble(p.center.y); buf.writeDouble(p.center.z);
        buf.writeFloat(p.radius); buf.writeByte(p.phase); buf.writeVarInt(p.age); buf.writeVarInt(p.duration); buf.writeVarInt(p.clashWith + 1);
        buf.writeVarInt(p.formationTicks); buf.writeVarInt(p.thickness);
    }, buf -> new DomainPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt()));
    public static final int FORMING = 0, ACTIVE = 1, CLASHING = 2, COLLAPSING = 3, REMOVED = 4;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
