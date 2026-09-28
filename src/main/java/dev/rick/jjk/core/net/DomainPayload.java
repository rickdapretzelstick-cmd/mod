package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: domain state. Phase: 0 forming, 1 active, 2 clashing, 3 collapsing, 4 removed.
 * {@code split}: during a clash, how far toward domain {@code splitWith}'s center this domain's side reaches (0..1 of the
 * way; below 0 or above 1 while one side is consuming the other), {@link #CONSUMED} once consumed. {@code splitWith} is
 * -1 when the domain isn't split. {@code annex}: conquered spheres as x,y,z,radius.
 */
public record DomainPayload(int id, int ownerId, String definition, Vec3 center, float radius, int phase, int age, int duration, int clashWith,
                            int formationTicks, int thickness, float split, int splitWith, float[] annex) implements CustomPacketPayload {
    public static final Type<DomainPayload> TYPE = new Type<>(JJK.id("domain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DomainPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.id); buf.writeVarInt(p.ownerId); buf.writeUtf(p.definition);
        buf.writeDouble(p.center.x); buf.writeDouble(p.center.y); buf.writeDouble(p.center.z);
        buf.writeFloat(p.radius); buf.writeByte(p.phase); buf.writeVarInt(p.age); buf.writeVarInt(p.duration); buf.writeVarInt(p.clashWith + 1);
        buf.writeVarInt(p.formationTicks); buf.writeVarInt(p.thickness);
        buf.writeFloat(p.split); buf.writeVarInt(p.splitWith + 1);
        buf.writeVarInt(p.annex.length);
        for (float f : p.annex) buf.writeFloat(f);
    }, buf -> {
        int id = buf.readVarInt(), owner = buf.readVarInt();
        String def = buf.readUtf();
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float radius = buf.readFloat();
        int phase = buf.readByte(), age = buf.readVarInt(), duration = buf.readVarInt(), clashWith = buf.readVarInt() - 1;
        int formation = buf.readVarInt(), thickness = buf.readVarInt();
        float split = buf.readFloat();
        int splitWith = buf.readVarInt() - 1;
        float[] annex = new float[Math.min(64, buf.readVarInt())];
        for (int i = 0; i < annex.length; i++) annex[i] = buf.readFloat();
        return new DomainPayload(id, owner, def, center, radius, phase, age, duration, clashWith, formation, thickness, split, splitWith, annex);
    });
    public static final float CONSUMED = -1000f;
    public static final int FORMING = 0, ACTIVE = 1, CLASHING = 2, COLLAPSING = 3, REMOVED = 4;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
