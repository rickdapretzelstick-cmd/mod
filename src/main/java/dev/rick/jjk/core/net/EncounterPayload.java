package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → one player in a realm fight: how the fight stands, for that player's HUD only (no one outside the fight gets
 * it). {@code remaining} curses are left ({@code -1}: not in a fight, clear the HUD). {@code stage}: {@link #NORMAL};
 * {@link #REVEALED} (the last curses are outlined and pointed at: {@code marks}); {@link #EMERGENCY} (the fight has
 * stalled and the Emergency Return is open). {@code channel} is the Emergency Return's progress (0-1), or -1.
 */
public record EncounterPayload(int remaining, int stage, List<Mark> marks, float channel) implements CustomPacketPayload {
    public static final int NORMAL = 0, REVEALED = 1, EMERGENCY = 2;

    /** A revealed curse: its entity id and where it is. */
    public record Mark(int entity, double x, double y, double z) {}

    public static final Type<EncounterPayload> TYPE = new Type<>(JJK.id("encounter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EncounterPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.remaining + 1);
        buf.writeVarInt(p.stage);
        buf.writeVarInt(p.marks.size());
        for (Mark m : p.marks) {
            buf.writeVarInt(m.entity);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
        }
        buf.writeFloat(p.channel);
    }, buf -> {
        int remaining = buf.readVarInt() - 1;
        int stage = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 64);
        List<Mark> marks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) marks.add(new Mark(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        return new EncounterPayload(remaining, stage, marks, buf.readFloat());
    });

    public static EncounterPayload clear() {
        return new EncounterPayload(-1, NORMAL, List.of(), -1f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
