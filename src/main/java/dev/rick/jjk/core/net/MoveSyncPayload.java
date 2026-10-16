package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server → client: the clips of the editor-made moves (core.move), as clip JSON, registered under their clip names. */
public record MoveSyncPayload(List<String> names, List<String> clips) implements CustomPacketPayload {
    public static final Type<MoveSyncPayload> TYPE = new Type<>(JJK.id("move_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoveSyncPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.names.size());
        for (int i = 0; i < p.names.size(); i++) {
            buf.writeUtf(p.names.get(i));
            buf.writeUtf(p.clips.get(i), 1 << 20);
        }
    }, buf -> {
        int n = buf.readVarInt();
        List<String> names = new ArrayList<>(n), clips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            names.add(buf.readUtf());
            clips.add(buf.readUtf(1 << 20));
        }
        return new MoveSyncPayload(names, clips);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
