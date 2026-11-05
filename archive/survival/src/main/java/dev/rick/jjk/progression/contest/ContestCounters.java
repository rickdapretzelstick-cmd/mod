package dev.rick.jjk.progression.contest;

import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.defense.DefenseLayer;
import dev.rick.jjk.core.defense.DefenseResult;
import dev.rick.jjk.core.defense.Defenses;
import dev.rick.jjk.core.defense.IncomingAttack;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The offensive counter: while guarding, a fresh attack press opens a short window (7 ticks by default). A curse's
 * grapple-able blow that lands inside it is caught (no damage, consumed exactly once, ahead of the guard) and turned into
 * a strength contest the player starts ahead in ({@link StrengthContests.Trigger#COUNTER}). Nothing is grabbed without an
 * attack to catch.
 *
 * <p>The attempt goes on cooldown from the moment it opens (1.25 s); a window that catches nothing is a miss with a short
 * recovery (0.25 s) during which a block doesn't turn into a defensive contest either. Everything is server-side and kept
 * per player; it is dropped on death, disconnect and server stop.
 */
public final class ContestCounters {
    /** Per player: the open window's end, the cooldown's end, a miss's recovery end (game times), and the player. */
    static final class State {
        final ServerPlayer player;
        long windowUntil = Long.MIN_VALUE, cooldownUntil = Long.MIN_VALUE, recoveryUntil = Long.MIN_VALUE;

        State(ServerPlayer player) {
            this.player = player;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Work to run at the end of this server tick (after the blow that asked for it is settled). */
    private static final List<Runnable> LATER = new ArrayList<>();

    private ContestCounters() {}

    public static void init() {
        Defenses.register(new CounterDefense());
        ServerTickEvents.END_SERVER_TICK.register(ContestCounters::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            STATES.clear();
            LATER.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> STATES.remove(h.player.getUUID()));
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> STATES.remove(e.getUUID()));
    }

    /**
     * An attack press while guarding (from the melee input). Opens the window if counters are on and the attempt is off
     * cooldown; the guard stays up either way and nothing is spent. True if a window opened.
     */
    public static boolean attempt(ServerPlayer p) {
        JJKConfig.Training cfg = JJKConfig.get().training;
        if (!cfg.contests || !cfg.counters || !p.isAlive() || !Combat.isGuarding(p) || StrengthContests.active(p)) return false;
        long now = p.level().getGameTime();
        State s = STATES.computeIfAbsent(p.getUUID(), u -> new State(p));
        if (now < s.cooldownUntil || now < s.windowUntil) return false;
        s.windowUntil = now + Math.max(1, cfg.counterWindowTicks);
        s.cooldownUntil = now + Math.max(0, cfg.counterCooldownTicks);
        s.recoveryUntil = Long.MIN_VALUE;
        if (p.level() instanceof ServerLevel level) {
            Fx.play(level, "counter_ready", p.getEyePosition().add(p.getLookAngle().scale(0.6)).subtract(0, 0.35, 0), p.getLookAngle(), 1f, p.getId());
        }
        return true;
    }

    /** Whether this player's counter window is open right now. */
    public static boolean open(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && p.level().getGameTime() < s.windowUntil;
    }

    /** Whether this player is in a missed counter's recovery (a block then stays a block). */
    public static boolean recovering(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && p.level().getGameTime() < s.recoveryUntil;
    }

    /** Ticks until the next attempt may open (0 when ready), for tests and the HUD. */
    public static long cooldown(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : Math.max(0, s.cooldownUntil - p.level().getGameTime());
    }

    /** A contest began for this player (any trigger): an open window is spent, never a miss. */
    static void consumed(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s != null) s.windowUntil = Long.MIN_VALUE;
    }

    /** Runs {@code r} at the end of this server tick. */
    static void later(Runnable r) {
        LATER.add(r);
    }

    /** Runs what was left for the end of this tick now (tests). */
    public static void flushForTest() {
        List<Runnable> run = List.copyOf(LATER);
        LATER.clear();
        for (Runnable r : run) r.run();
    }

    /** Forgets everything about this player (tests). */
    public static void reset(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }

    private static void tick(MinecraftServer server) {
        if (!LATER.isEmpty()) {
            List<Runnable> run = List.copyOf(LATER);
            LATER.clear();
            for (Runnable r : run) r.run();
        }
        if (STATES.isEmpty()) return;
        JJKConfig.Training cfg = JJKConfig.get().training;
        STATES.values().removeIf(s -> s.player.isRemoved());
        for (State s : STATES.values()) {
            long now = s.player.level().getGameTime();
            if (s.windowUntil == Long.MIN_VALUE || now < s.windowUntil) continue;
            // The window ran out with nothing caught: a miss, with its short recovery.
            s.windowUntil = Long.MIN_VALUE;
            s.recoveryUntil = now + Math.max(0, cfg.counterMissRecoveryTicks);
            if (s.player.level() instanceof ServerLevel level) {
                Fx.play(level, "counter_miss", s.player.getEyePosition().add(s.player.getLookAngle().scale(0.6)).subtract(0, 0.35, 0),
                        s.player.getLookAngle(), 1f, s.player.getId());
            }
        }
    }

    /**
     * Ahead of the guard (priority 60 to its 50): during an open window, a curse's direct blow that could become a struggle
     * is caught and turned into a counter contest (no damage). Anything else (or a contest that can't start) passes on to
     * the guard as usual.
     */
    public static final class CounterDefense implements DefenseLayer {
        @Override
        public String id() {
            return "contest_counter";
        }

        @Override
        public int priority() {
            return 60;
        }

        @Override
        public boolean isActive(LivingEntity defender) {
            return defender instanceof ServerPlayer p && open(p) && Combat.isGuarding(p);
        }

        @Override
        public DefenseResult intercept(LivingEntity defender, IncomingAttack attack) {
            if (!(defender instanceof ServerPlayer p) || !(attack.attacker instanceof CommonCurseEntity c) || attack.hit == null) return DefenseResult.PASS;
            if (!StrengthContests.eligible(c) || !StrengthContests.grapplingBlow(attack.hit, c, p)) return DefenseResult.PASS;
            // Caught in front, as the guard would.
            Vec3 from = attack.directionFromTarget();
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (from.lengthSqr() > 1e-4 && flat.lengthSqr() > 1e-4 && from.normalize().dot(flat.normalize()) < -0.1) return DefenseResult.PASS;
            if (p.level().getServer() == null || !StrengthContests.begin(p.level().getServer(), c, p, StrengthContests.Trigger.COUNTER)) {
                return DefenseResult.PASS;
            }
            JJK.LOGGER.debug("[contests] counter caught {}'s {}", c.getName().getString(), attack.hit.source);
            return DefenseResult.negate("counter");
        }
    }
}
