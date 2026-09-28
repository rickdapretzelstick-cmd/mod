package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: a clash round begins. Carries everything the client needs to draw and locally predict the round:
 * who is playing (entity, domain, colour, name), when the chart clock starts, the chart itself and the timing windows.
 */
public record ClashStartPayload(int session, int round, long startTick, int[] entities, int[] domains, int[] colors, List<String> names,
                                float[] times, byte[] lanes, int perfectMs, int greatMs, int goodMs, float bpm) implements CustomPacketPayload {
    public static final Type<ClashStartPayload> TYPE = new Type<>(JJK.id("clash_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClashStartPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.session);
        buf.writeVarInt(p.round);
        buf.writeLong(p.startTick);
        buf.writeVarInt(p.entities.length);
        for (int i = 0; i < p.entities.length; i++) {
            buf.writeVarInt(p.entities[i]);
            buf.writeVarInt(p.domains[i]);
            buf.writeInt(p.colors[i]);
            buf.writeUtf(p.names.get(i), 64);
        }
        buf.writeVarInt(p.times.length);
        for (int i = 0; i < p.times.length; i++) {
            buf.writeFloat(p.times[i]);
            buf.writeByte(p.lanes[i]);
        }
        buf.writeVarInt(p.perfectMs);
        buf.writeVarInt(p.greatMs);
        buf.writeVarInt(p.goodMs);
        buf.writeFloat(p.bpm);
    }, buf -> {
        int session = buf.readVarInt(), round = buf.readVarInt();
        long start = buf.readLong();
        int n = Math.min(8, buf.readVarInt());
        int[] ents = new int[n], doms = new int[n], cols = new int[n];
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ents[i] = buf.readVarInt();
            doms[i] = buf.readVarInt();
            cols[i] = buf.readInt();
            names.add(buf.readUtf(64));
        }
        int notes = Math.min(512, buf.readVarInt());
        float[] times = new float[notes];
        byte[] lanes = new byte[notes];
        for (int i = 0; i < notes; i++) {
            times[i] = buf.readFloat();
            lanes[i] = buf.readByte();
        }
        return new ClashStartPayload(session, round, start, ents, doms, cols, names, times, lanes, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat());
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
