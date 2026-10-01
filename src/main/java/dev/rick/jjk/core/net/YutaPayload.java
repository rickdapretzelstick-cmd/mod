package dev.rick.jjk.core.net;

import dev.rick.jjk.JJK;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: Cursed Partners' state for its own HUD. Rika's entity and whether her moveset is up; the Copy Wheel
 * (open or not, its page, the copied techniques oldest first, the one selected and when each is ready again, in game
 * ticks); progress toward Jacob's Ladder; and Outburst's charge stage while it is held (-1 when not charging).
 */
public record YutaPayload(int rikaId, boolean rikaMode, boolean wheelOpen, int page, List<String> copied, String selected,
                          List<Long> readyAt, int ladderHits, int ladderNeeded, int outburstStage) implements CustomPacketPayload {
    public static final Type<YutaPayload> TYPE = new Type<>(JJK.id("yuta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, YutaPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.rikaId);
        buf.writeBoolean(p.rikaMode);
        buf.writeBoolean(p.wheelOpen);
        buf.writeByte(p.page);
        buf.writeVarInt(p.copied.size());
        for (String s : p.copied) buf.writeUtf(s, 32);
        buf.writeUtf(p.selected, 32);
        buf.writeVarInt(p.readyAt.size());
        for (long l : p.readyAt) buf.writeVarLong(l);
        buf.writeByte(p.ladderHits);
        buf.writeByte(p.ladderNeeded);
        buf.writeByte(p.outburstStage);
    }, buf -> {
        int rika = buf.readVarInt();
        boolean mode = buf.readBoolean(), open = buf.readBoolean();
        int page = buf.readByte();
        int n = buf.readVarInt();
        List<String> copied = new ArrayList<>();
        for (int i = 0; i < n; i++) copied.add(buf.readUtf(32));
        String sel = buf.readUtf(32);
        int m = buf.readVarInt();
        List<Long> ready = new ArrayList<>();
        for (int i = 0; i < m; i++) ready.add(buf.readVarLong());
        return new YutaPayload(rika, mode, open, page, copied, sel, ready, buf.readByte(), buf.readByte(), buf.readByte());
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
