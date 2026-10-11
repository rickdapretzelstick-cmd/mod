package dev.rick.jjk.progression.moment;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingEvaluator;
import dev.rick.jjk.progression.training.TrainingNet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One interactive moment: a player (and perhaps a foe) briefly locked into a server-authoritative skill check on the
 * shared gauges, then a result that feeds back into the fight. What every moment shares lives here: its id, the intro
 * then the timed challenge on the server's own clock, the client's READY handshake (no answer, no moment), judged key
 * timings, the heartbeat, the result's hold, and the clean end. What a moment means (its scoring, its result, who may
 * touch whom meanwhile) is its subclass's.
 *
 * <p>Moments are run by {@link Moments}: one per player at a time, reserved atomically together with its foe.
 */
public abstract class Moment {
    public final int id;
    public final UUID player;
    @Nullable public final UUID foe;
    protected int state = TrainingNet.INTRO, stateTicks;
    protected double clock;
    long lastNanos = System.nanoTime();
    protected List<TrainingCheck> checks = List.of();
    protected TrainingEvaluator eval;
    protected boolean ready;

    protected Moment(UUID player, @Nullable UUID foe) {
        this.id = Moments.nextId();
        this.player = player;
        this.foe = foe;
    }

    // --- What a moment is ---

    /** The client presentation it uses (the HUD, camera and scene are chosen by it). */
    public abstract String kit();

    /** The settings its checks are judged with (windows, latency, sync timeout). */
    public abstract JJKConfig.Training rules();

    /** A verdict on one check (the client has already been told). */
    protected abstract void onJudged(ServerPlayer p, TrainingEvaluator.Outcome o);

    /** Each tick while active, before the expired checks are judged ({@code dtMs} of the challenge just passed). */
    protected void beforeChecks(ServerPlayer p, double dtMs) {}

    /** Each tick while active, after the expired checks are judged: where a moment decides it is over. */
    protected void afterChecks(ServerPlayer p) {}

    /** Ticks its result holds before the moment ends. */
    protected int resultTicks(int state) {
        return 24;
    }

    /** What the player is told when the result's hold ends (null: nothing). */
    @Nullable
    protected String resultMessage(int state) {
        return null;
    }

    /** Null while the moment can go on; otherwise why it can't (said to the player; "" says nothing). */
    @Nullable
    protected String broken(MinecraftServer server, @Nullable ServerPlayer p) {
        return p == null || !p.isAlive() ? "" : null;
    }

    /** What CANCEL / LEAVE from the client does: null ignores it (you can't leave), else the message it ends with. */
    @Nullable
    protected String cancelled() {
        return null;
    }

    /** Whether {@code entity} (the player or the foe) may take this damage while the moment runs. */
    protected boolean allowDamage(LivingEntity entity, DamageSource source) {
        return false;
    }

    /** Holds the player (and whatever else) each tick while it runs. */
    protected void hold(ServerPlayer p) {
        ClashCommon.hold(p);
    }

    /** Whether the hold applies in the current state (by default through the intro and the challenge). */
    protected boolean holding() {
        return state == TrainingNet.INTRO || state == TrainingNet.ACTIVE;
    }

    /** It is over (finished, broken, cancelled, a disconnect): let go of everything it held. */
    protected void onEnd(MinecraftServer server, @Nullable ServerPlayer p) {}

    /** How the end message is shown. */
    protected void say(ServerPlayer p, String message) {
        p.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // --- Running it ---

    public int state() {
        return state;
    }

    public double clock() {
        return clock;
    }

    public List<TrainingCheck> checks() {
        return checks;
    }

    public TrainingEvaluator evaluator() {
        return eval;
    }

    public int attempt() {
        return 1;
    }

    /** Sets its checks and evaluator and starts its intro now. */
    public void prepare(List<TrainingCheck> checks, int maxMisses, int introMs) {
        JJKConfig.Training r = rules();
        this.checks = checks;
        this.eval = new TrainingEvaluator(checks, maxMisses, r.maxLatencyMs, r.clockSlackMs);
        this.clock = -introMs;
        this.state = TrainingNet.INTRO;
        this.stateTicks = 0;
        this.ready = false;
        this.lastNanos = System.nanoTime();
    }

    public void setState(ServerPlayer p, int state) {
        this.state = state;
        this.stateTicks = 0;
        ServerPlayNetworking.send(p, new TrainingNet.State(id, attempt(), state, eval == null ? 0 : eval.misses(), (int) Math.round(clock), 0));
    }

    final void input(ServerPlayer p, TrainingNet.Input in) {
        if (in.session() != id || in.attempt() != attempt()) return;
        if (in.action() == TrainingNet.READY) {
            ready = true;
            return;
        }
        if (in.action() == TrainingNet.CANCEL || in.action() == TrainingNet.LEAVE) {
            String why = cancelled();
            if (why != null) Moments.end(p.level().getServer(), this, p, why);
            return;
        }
        if (state != TrainingNet.ACTIVE) return;
        // Releases always go through: they let the key go (a tap check ignores them, a hold check is decided by them),
        // and a key never let go would refuse every later press on it.
        if (in.action() == TrainingNet.PRESS || in.action() == TrainingNet.RELEASE) {
            TrainingEvaluator.Outcome o = eval.input(in.seq(), in.action(), in.gauge(), in.check(), in.clientMs(), clock);
            if (o != null) judge(p, o);
        }
    }

    final void judge(ServerPlayer p, TrainingEvaluator.Outcome o) {
        ServerPlayNetworking.send(p, new TrainingNet.Judge(id, attempt(), o.check(), o.result(), eval.misses()));
        onJudged(p, o);
    }

    final void step(MinecraftServer server, ServerPlayer p, double d) {
        stateTicks++;
        if (holding()) hold(p);
        // A heartbeat, so the client knows the moment is still going.
        if (stateTicks % 20 == 0) ServerPlayNetworking.send(p, new TrainingNet.State(id, attempt(), state, eval.misses(), (int) Math.round(clock), stateTicks));
        switch (state) {
            case TrainingNet.INTRO -> {
                clock += d;
                if (clock >= 0) setState(p, TrainingNet.ACTIVE);
            }
            case TrainingNet.ACTIVE -> {
                clock += d;
                beforeChecks(p, d);
                if (state != TrainingNet.ACTIVE) return;
                for (TrainingEvaluator.Outcome o : eval.expire(clock)) {
                    judge(p, o);
                    if (state != TrainingNet.ACTIVE || Moments.of(p) != this) return;
                }
                if (!ready && clock > rules().syncTimeoutMs) {
                    // The client never acknowledged it: no moment (and no penalty for a dropped connection).
                    Moments.end(server, this, p, syncLost());
                    return;
                }
                afterChecks(p);
            }
            case TrainingNet.SUCCESS, TrainingNet.FAILURE -> {
                if (stateTicks >= resultTicks(state)) Moments.end(server, this, p, resultMessage(state));
            }
            default -> {}
        }
    }

    /** What the player is told when their client never acknowledged the moment. */
    protected String syncLost() {
        return "The moment passes (connection).";
    }
}
