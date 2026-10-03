package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → a player: what Survival progression lets their screens show. {@code governed} means the character select
 * screen can't hand out kits (Survival with progression on); {@code kit} and {@code owned} are their own earned kits.
 */
public record ProgressionPayload(boolean governed, String kit, List<String> owned) implements CustomPacketPayload {
    public static final Type<ProgressionPayload> TYPE = new Type<>(JJK.id("progression"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressionPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.governed);
        buf.writeUtf(p.kit, 64);
        buf.writeVarInt(p.owned.size());
        for (String s : p.owned) buf.writeUtf(s, 64);
    }, buf -> {
        boolean governed = buf.readBoolean();
        String kit = buf.readUtf(64);
        int n = Math.min(buf.readVarInt(), 64);
        List<String> owned = new ArrayList<>(n);
        for (int i = 0; i < n; i++) owned.add(buf.readUtf(64));
        return new ProgressionPayload(governed, kit, owned);
    });

    public ProgressionPayload {
        owned = List.copyOf(owned);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
