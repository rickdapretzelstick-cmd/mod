package dev.rick.jjk.core.domain.clash;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.ClashEndPayload;
import dev.rick.jjk.core.net.ClashStartPayload;
import dev.rick.jjk.core.net.ClashUpdatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Runs every domain clash: starts sessions, feeds them player input, keeps the duellists frozen in place, and turns
 * judgements into presentation (sync to clients, energy pulses, destabilising misses). Generic: any two domains can
 * clash, whoever owns them.
 */
public final class ClashManager {
    private static final Map<Integer, ClashSession> SESSIONS = new LinkedHashMap<>();
    private static final Map<Integer, Consumer<ClashSession>> ON_END = new LinkedHashMap<>();
    private static final Map<LivingEntity, Float> BOT_SKILL = new WeakHashMap<>();
    private static int nextId = 1;
    /** How far away players still see (and hear) a clash. */
    private static final double VIEW_RANGE = 160;

    private ClashManager() {}

    /** Starts a clash between two domains. {@code onEnd} receives the finished session (winner or cancellation). */
    public static ClashSession start(DomainInstance a, DomainInstance b, Consumer<ClashSession> onEnd) {
        int id = nextId++;
        long seed = a.level.getGameTime() * 31 + id * 1_000_003L + a.id * 17L + b.id;
        ClashSession s = new ClashSession(id, a.level, List.of(a, b), seed);
        s.listener = PRESENTATION;
        SESSIONS.put(id, s);
        ON_END.put(id, onEnd);
        // The first round was set up before the listener existed: announce it now.
        PRESENTATION.roundStarted(s);
        return s;
    }

    public static void tick(ServerLevel level) {
        if (SESSIONS.isEmpty()) return;
        for (ClashSession s : List.copyOf(SESSIONS.values())) {
            if (s.level != level) continue;
            if (s.phase != ClashSession.Phase.ENDED && JJKConfig.get().clash.freezeParticipants) {
                for (ClashParticipant p : s.participants) Statuses.apply(p.entity, CombatStatus.CLASHING, 3);
            }
            s.tick();
            if (s.phase == ClashSession.Phase.ENDED) finish(s);
        }
    }

    private static void finish(ClashSession s) {
        if (SESSIONS.remove(s.id) == null) return;
        for (ClashParticipant p : s.participants) Statuses.remove(p.entity, CombatStatus.CLASHING);
        Consumer<ClashSession> cb = ON_END.remove(s.id);
        if (cb != null) cb.accept(s);
    }

    /** Ends a clash without a winner (one side's domain vanished). */
    public static void cancel(@Nullable ClashSession s) {
        if (s == null) return;
        s.cancel();
        finish(s);
    }

    public static void input(ServerPlayer player, int sessionId, int lane, double time) {
        ClashSession s = SESSIONS.get(sessionId);
        if (s != null && s.level == player.level()) s.input(player, lane, time);
    }

    @Nullable
    public static ClashSession of(DomainInstance d) {
        for (ClashSession s : SESSIONS.values()) for (ClashParticipant p : s.participants) if (p.domain == d) return s;
        return null;
    }

    @Nullable
    public static ClashSession byId(int id) {
        return SESSIONS.get(id);
    }

    /** How well a non-player participant plays (tests and future NPC sorcerers). */
    public static void setBotSkill(LivingEntity e, float skill) {
        BOT_SKILL.put(e, skill);
    }

    public static float botSkillOf(LivingEntity e) {
        return BOT_SKILL.getOrDefault(e, JJKConfig.get().clash.botSkill);
    }

    // --- Presentation ---

    private static final ClashSession.ClashListener PRESENTATION = new ClashSession.ClashListener() {
        @Override
        public void roundStarted(ClashSession s) {
            if (s.listener != this) return;
            JJKConfig.Clash cfg = JJKConfig.get().clash;
            int n = s.participants.size();
            int[] ents = new int[n], doms = new int[n], cols = new int[n];
            List<String> names = new ArrayList<>(n);
            for (ClashParticipant p : s.participants) {
                ents[p.index] = p.entity.getId();
                doms[p.index] = p.domain.id;
                cols[p.index] = p.domain.definition.clashColor();
                names.add(p.entity.getName().getString());
            }
            send(s, new ClashStartPayload(s.id, s.round, s.startTick, ents, doms, cols, names, s.chart.times(), s.chart.lanes(), cfg.perfectWindowMs,
                    cfg.greatWindowMs, cfg.goodWindowMs, s.round == 0 ? cfg.bpm : cfg.bpm * 1.1f));
            Vec3 mid = midpoint(s);
            Fx.play(s.level, s.round == 0 ? "clash_start" : "clash_sudden_death", mid, Vec3.ZERO, (float) s.participants.getFirst().domain.radius, -1);
        }

        @Override
        public void judged(ClashSession s, ClashParticipant p, int note, ClashJudgement j, double offsetMs) {
            send(s, new ClashUpdatePayload(s.id, p.index, note, j.ordinal(), (float) offsetMs, s.meter, p.score, p.streak, p.accuracy()));
            Vec3 at = p.entity.position().add(0, p.entity.getBbHeight() * 0.55, 0);
            ClashParticipant other = s.participants.get(p.index == 0 ? 1 : 0);
            Vec3 toward = other.domain.center.subtract(p.domain.center);
            switch (j) {
                case PERFECT -> {
                    // The energy pulse: stronger the longer the streak.
                    float tier = 1f + (p.streak >= 3 ? 0.5f : 0) + (p.streak >= 5 ? 0.5f : 0) + (p.streak >= 10 ? 1f : 0);
                    Fx.play(s.level, "clash_perfect", at, toward, tier, p.entity.getId());
                    Fx.shake(s.level, at, p.domain.radius * 2, 0.18f * tier, 6);
                }
                case GREAT, GOOD -> Fx.play(s.level, "clash_hit", at, toward, j == ClashJudgement.GREAT ? 1f : 0.6f, p.entity.getId());
                default -> Fx.play(s.level, "clash_miss", at, toward, 1f, p.entity.getId());
            }
        }

        @Override
        public void ended(ClashSession s) {
            send(s, new ClashEndPayload(s.id, s.winner, s.outcome.ordinal()));
            // Resolve the domains right away, even when the decisive input arrived between ticks.
            finish(s);
        }
    };

    private static Vec3 midpoint(ClashSession s) {
        Vec3 sum = Vec3.ZERO;
        for (ClashParticipant p : s.participants) sum = sum.add(p.domain.center);
        return sum.scale(1.0 / s.participants.size());
    }

    private static void send(ClashSession s, CustomPacketPayload payload) {
        Vec3 mid = midpoint(s);
        for (ServerPlayer player : s.level.players()) {
            boolean participant = s.participant(player) != null;
            if (participant || player.position().distanceTo(mid) < VIEW_RANGE) ServerPlayNetworking.send(player, payload);
        }
    }
}
