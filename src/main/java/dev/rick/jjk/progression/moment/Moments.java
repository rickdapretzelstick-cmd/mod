package dev.rick.jjk.progression.moment;

import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSessions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every {@link Moment}: one per player at a time, its foe (if any) reserved in the same step, so two moments can
 * never claim the same player or the same curse. Routes the client's skill-check input to the player's moment, ticks
 * them on the server's clock, keeps them apart from damage by their own rules, and ends them cleanly on a disconnect,
 * a death or the server stopping. Character training ({@link TrainingSessions}) runs its own longer sessions but counts
 * as busy here, so nothing else starts on top of it.
 */
public final class Moments {
    private static final Map<UUID, Moment> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Moment> BY_FOE = new HashMap<>();
    private static int nextId = 1_000_000;
    private static boolean manualClock;

    private Moments() {}

    static int nextId() {
        return nextId++;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Moments::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            BY_PLAYER.clear();
            BY_FOE.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            Moment m = BY_PLAYER.get(h.player.getUUID());
            if (m != null) end(server, m, null, null);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Moment m = BY_PLAYER.get(entity.getUUID());
            if (m == null) m = BY_FOE.get(entity.getUUID());
            return m == null || m.allowDamage(entity, source);
        });
    }

    // --- Queries ---

    @Nullable
    public static Moment of(ServerPlayer p) {
        return BY_PLAYER.get(p.getUUID());
    }

    /** The moment this foe is locked in, or null. */
    @Nullable
    public static Moment ofFoe(Entity foe) {
        return BY_FOE.get(foe.getUUID());
    }

    public static boolean active(ServerPlayer p) {
        return BY_PLAYER.containsKey(p.getUUID());
    }

    /** In a moment, in training, in a clash or being pulled into a realm: nothing else may start for them now. */
    public static boolean busy(ServerPlayer p) {
        return BY_PLAYER.containsKey(p.getUUID()) || TrainingSessions.inTraining(p) || ClashCommon.clashing(p) || CursedRealms.pulling(p);
    }

    // --- Running ---

    /**
     * Reserves the player (and the moment's foe) and starts it: false, and nothing is taken, if either is already in a
     * moment or the player is busy. The moment must already be {@link Moment#prepare prepared}; {@code start} sends its
     * presentation once it holds both.
     */
    public static boolean begin(ServerPlayer p, Moment m, Runnable start) {
        if (busy(p) || m.foe != null && BY_FOE.containsKey(m.foe)) return false;
        BY_PLAYER.put(m.player, m);
        if (m.foe != null) BY_FOE.put(m.foe, m);
        start.run();
        return true;
    }

    /** A key timing (or READY / CANCEL) from a client. False if this player isn't in a moment. */
    public static boolean input(ServerPlayer p, TrainingNet.Input in) {
        Moment m = BY_PLAYER.get(p.getUUID());
        if (m == null) return false;
        m.input(p, in);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) return;
        long n = System.nanoTime();
        for (Moment m : List.copyOf(BY_PLAYER.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.player);
            String why = m.broken(server, p);
            if (why != null) {
                end(server, m, p, why.isEmpty() ? null : why);
                continue;
            }
            double d = manualClock ? 0 : Math.min(100.0, (n - m.lastNanos) / 1_000_000.0);
            m.lastNanos = n;
            m.step(server, p, d);
        }
    }

    /** Ends a moment (once): everything it held is let go, and its client is told. */
    public static void end(MinecraftServer server, Moment m, @Nullable ServerPlayer p, @Nullable String message) {
        if (BY_PLAYER.get(m.player) != m) return;
        BY_PLAYER.remove(m.player);
        if (m.foe != null && BY_FOE.get(m.foe) == m) BY_FOE.remove(m.foe);
        m.onEnd(server, p);
        if (p != null) {
            ClashCommon.release(p);
            ServerPlayNetworking.send(p, new TrainingNet.State(m.id, m.attempt(), TrainingNet.ENDED, m.eval == null ? 0 : m.eval.misses(), 0, 0));
            if (message != null) m.say(p, message);
        }
    }

    // --- Tests ---

    public static void setManualClockForTest(boolean on) {
        manualClock = on;
    }

    /** Advances a player's moment by {@code ms} on its own clock (one step), as the server would. */
    public static void advanceForTest(ServerPlayer p, double ms) {
        Moment m = BY_PLAYER.get(p.getUUID());
        if (m == null) return;
        String why = m.broken(p.level().getServer(), p);
        if (why != null) {
            end(p.level().getServer(), m, p, why.isEmpty() ? null : why);
            return;
        }
        m.step(p.level().getServer(), p, ms);
    }

    public static void tickForTest(MinecraftServer server) {
        tick(server);
    }
}
