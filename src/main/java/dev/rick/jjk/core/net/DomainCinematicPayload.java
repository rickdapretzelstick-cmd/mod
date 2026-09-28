package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: a domain is opening. {@code kind}: SOLO (you are opening it), OBSERVE (someone nearby is),
 * VERSUS (a counter: two domains about to collide). {@code titleAt} is how many ticks from now the domain seals (when
 * "DOMAIN EXPANSION / name" is shown), {@code duration} how long the presentation lasts in total.
 */
public record DomainCinematicPayload(int kind, int[] entities, List<String> names, List<String> domains, int[] colors, int titleAt,
                                     int duration) implements CustomPacketPayload {
    public static final int SOLO = 0, OBSERVE = 1, VERSUS = 2;
    public static final Type<DomainCinematicPayload> TYPE = new Type<>(JJK.id("domain_cinematic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DomainCinematicPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeByte(p.kind);
        buf.writeVarInt(p.entities.length);
        for (int i = 0; i < p.entities.length; i++) {
            buf.writeVarInt(p.entities[i]);
            buf.writeUtf(p.names.get(i), 64);
            buf.writeUtf(p.domains.get(i), 64);
            buf.writeInt(p.colors[i]);
        }
        buf.writeVarInt(p.titleAt);
        buf.writeVarInt(p.duration);
    }, buf -> {
        int kind = buf.readByte();
        int n = Math.min(4, buf.readVarInt());
        int[] ents = new int[n], cols = new int[n];
        List<String> names = new ArrayList<>(n), doms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ents[i] = buf.readVarInt();
            names.add(buf.readUtf(64));
            doms.add(buf.readUtf(64));
            cols[i] = buf.readInt();
        }
        return new DomainCinematicPayload(kind, ents, names, doms, cols, buf.readVarInt(), buf.readVarInt());
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
