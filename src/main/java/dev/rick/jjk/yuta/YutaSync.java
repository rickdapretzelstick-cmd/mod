package dev.rick.jjk.yuta;

import dev.rick.jjk.core.net.YutaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Sends a Cursed Partners player their own state for the HUD (Rika's mode, the Copy Wheel, Jacob's Ladder, Outburst). */
public final class YutaSync {
    private YutaSync() {}

    public static void send(LivingEntity user) {
        if (!(user instanceof ServerPlayer sp)) return;
        YutaState s = YutaState.of(user);
        List<Long> ready = new ArrayList<>();
        for (String t : s.copied) ready.add(s.copyReadyAt.getOrDefault(t, 0L));
        RikaEntity r = s.rika();
        ServerPlayNetworking.send(sp, new YutaPayload(r == null ? -1 : r.getId(), s.rikaMode, s.wheelOpen, s.page, List.copyOf(s.copied), s.selected,
                ready, s.ladderHits, YutaCombat.cfg().ladderHits, OutburstAbility.stage(user)));
    }
}
