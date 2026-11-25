package dev.rick.jjk.battle.game;

import dev.rick.jjk.battle.BattleAI;
import dev.rick.jjk.battle.BattleEngine;
import dev.rick.jjk.battle.BattleKit;
import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleState;
import dev.rick.jjk.battle.Beat;
import dev.rick.jjk.battle.Fighter;
import dev.rick.jjk.battle.Plan;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.CameraPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * One turn-based battle in the world: two bodies (players, or a training dummy the AI plays) standing on marks along a
 * line, the engine's state, and the turn loop: choose (both lock in, or the timer runs out), play (each beat as a
 * scene), repeat until a KO.
 *
 * <p>The session owns where the fighters stand: every tick they are kept on their marks (the distance follows the
 * battle's range) unless a scene is moving them, so nobody walks out of a fight.
 */
public final class BattleSession {
    public static final int CHOOSE_TICKS = 20 * 20, AI_THINK_TICKS = 20, END_TICKS = 80;
    private static int nextId = 1;

    public final int id = nextId++;
    public final ServerLevel level;
    final LivingEntity[] body;
    final boolean[] ai;
    final BattleState state;
    private final Vec3 center, axis;
    int phase = BattlePayloads.CHOOSING;
    private int timer;
    final Plan[] picks = new Plan[2];
    private final Deque<Beat> queue = new ArrayDeque<>();
    private int beatIndex, beatCount;
    private BattleScenes.Scene scene;
    private int sceneTick;
    // Where each fighter is, where it's going and how fast it gets there; how high it's lifted (a launch).
    private final Vec3[] pos = new Vec3[2], goal = new Vec3[2];
    private final double[] rate = {0.3, 0.3}, lift = new double[2], liftGoal = new double[2];
    boolean closed;

    BattleSession(ServerLevel level, LivingEntity a, LivingEntity b, BattleKit ka, BattleKit kb, boolean aiA, boolean aiB) {
        this.level = level;
        this.body = new LivingEntity[] {a, b};
        this.ai = new boolean[] {aiA, aiB};
        this.state = new BattleState(ka, kb, level.getRandom().nextLong());
        this.center = a.position().lerp(b.position(), 0.5);
        Vec3 d = b.position().subtract(a.position()).multiply(1, 0, 1);
        this.axis = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        for (int i = 0; i < 2; i++) {
            pos[i] = goal[i] = mark(i, state.range);
            if (body[i] instanceof Mob m) m.setNoAi(true);
        }
        startChoosing();
    }

    public BattleKit kit(int side) {
        return state.f[side].kit;
    }

    public int sideOf(LivingEntity e) {
        return body[0] == e ? 0 : body[1] == e ? 1 : -1;
    }

    /** How far each fighter stands from the middle at each range. */
    private static double half(Range r) {
        return switch (r) {
            case CLOSE -> 1.25;
            case MID -> 3.2;
            case FAR -> 6.5;
        };
    }

    private Vec3 mark(int side, Range r) {
        return center.add(axis.scale(side == 0 ? -half(r) : half(r)));
    }

    // ------------------------------------------------------------------------------------------------ turn loop

    void tick() {
        if (closed) return;
        for (int i = 0; i < 2; i++) {
            LivingEntity e = body[i];
            if (e.isRemoved() || !e.isAlive() || e.level() != level) {
                forfeit(i);
                return;
            }
        }
        switch (phase) {
            case BattlePayloads.CHOOSING -> {
                timer--;
                for (int i = 0; i < 2; i++) {
                    if (ai[i] && picks[i] == null && timer <= CHOOSE_TICKS - AI_THINK_TICKS)
                        picks[i] = BattleAI.choose(state, i, 2.5, level.getRandom().nextLong());
                }
                if ((picks[0] != null && picks[1] != null) || timer <= 0) resolveTurn();
                else if (timer % 20 == 0) sync();
            }
            case BattlePayloads.PLAYING -> playTick();
            case BattlePayloads.DONE -> {
                if (--timer <= 0) close();
            }
            default -> {}
        }
        hold();
    }

    private void startChoosing() {
        phase = BattlePayloads.CHOOSING;
        timer = CHOOSE_TICKS;
        picks[0] = picks[1] = null;
        settle();
        for (int i = 0; i < 2; i++) idle(i);
        sync();
    }

    /** A player locked in. Returns why it was refused, or null. */
    String choose(int side, Plan plan) {
        if (phase != BattlePayloads.CHOOSING || ai[side]) return "Not your turn to choose";
        String why = BattleEngine.validate(state, side, plan);
        if (why != null) return why;
        picks[side] = plan;
        sync();
        return null;
    }

    private void resolveTurn() {
        for (int i = 0; i < 2; i++) if (picks[i] == null) picks[i] = Plan.of(kit(i).guard.id);
        List<Beat> beats = BattleEngine.resolve(state, picks[0], picks[1]);
        queue.clear();
        queue.addAll(beats);
        beatIndex = 0;
        beatCount = beats.size();
        scene = null;
        phase = BattlePayloads.PLAYING;
        sync();
    }

    private void playTick() {
        if (scene == null || sceneTick >= scene.length()) {
            Beat b = queue.poll();
            if (b == null) {
                if (state.over()) {
                    phase = BattlePayloads.DONE;
                    timer = END_TICKS;
                    sync();
                } else {
                    startChoosing();
                }
                return;
            }
            scene = BattleScenes.build(this, b);
            sceneTick = 0;
            BattlePayloads.BeatStart p = new BattlePayloads.BeatStart(id, beatIndex++, beatCount, b.actor(), b.move(), b.kind().ordinal(),
                    b.damage(), b.link(), b.blackFlash(), b.note(), b.hp0(), b.hp1(), scene.length(), scene.contact());
            for (ServerPlayer p2 : viewers()) ServerPlayNetworking.send(p2, p);
        }
        for (BattleScenes.Cue c : scene.cues()) if (c.tick() == sceneTick) c.run().run();
        sceneTick++;
    }

    // ------------------------------------------------------------------------------------------------ staging

    /** Keeps both fighters where the session wants them, facing each other. */
    private void hold() {
        for (int i = 0; i < 2; i++) {
            pos[i] = pos[i].lerp(goal[i], rate[i]);
            lift[i] += (liftGoal[i] - lift[i]) * 0.35;
            if (liftGoal[i] > 0 && lift[i] > liftGoal[i] * 0.9) liftGoal[i] = 0; // up, then fall back
        }
        for (int i = 0; i < 2; i++) {
            LivingEntity e = body[i];
            Vec3 p = pos[i].add(0, lift[i], 0);
            Vec3 to = pos[1 - i].subtract(pos[i]);
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            if (e.position().distanceToSqr(p) > 0.0025 || Math.abs(net.minecraft.util.Mth.wrapDegrees(e.getYRot() - yaw)) > 2) {
                e.teleportTo(level, p.x, p.y, p.z, Set.of(), yaw, 0, false);
                e.setYHeadRot(yaw);
                e.setYBodyRot(yaw);
            }
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
        }
    }

    /** Both fighters ease to the marks for the current range. */
    void settle() {
        for (int i = 0; i < 2; i++) {
            goal[i] = mark(i, state.range);
            rate[i] = 0.25;
        }
    }

    void settleFast() {
        settle();
        rate[0] = rate[1] = 0.55;
    }

    /** {@code side} dashes in to strike range of the other (or to the middle, for a clash). */
    void lunge(int side, boolean toMiddle) {
        Vec3 target = toMiddle ? center.add(axis.scale(side == 0 ? -0.6 : 0.6)) : pos[1 - side].add(axis.scale(side == 0 ? -1.1 : 1.1));
        goal[side] = target;
        rate[side] = 0.6;
    }

    void lift(int side, double height) {
        liftGoal[side] = height;
    }

    void anim(int side, String clip, float speed) {
        Anim.play(body[side], clip, speed);
    }

    void stop(int side) {
        Anim.stop(body[side]);
    }

    private void idle(int side) {
        Anim.stop(body[side]);
    }

    String guardClip(int side) {
        return kit(side).character.equals("gojo") ? "guard_gojo" : "guard";
    }

    /** Plays an effect at a fighter (its chest when {@code chest}, its feet otherwise), aimed at the other one. */
    void fxAt(int side, String fx, boolean chest) {
        if (fx == null || fx.isEmpty()) return;
        LivingEntity e = body[side];
        Vec3 at = e.position().add(0, chest ? e.getBbHeight() * 0.6 : 0.1, 0);
        Vec3 dir = body[1 - side].position().subtract(e.position()).normalize();
        Fx.play(level, fx, at, dir, 1f, e.getId());
    }

    void sound(int side, SoundEvent s, float volume, float pitch) {
        Fx.sound(body[side], s, volume, pitch);
    }

    void sound(int side, net.minecraft.core.Holder<SoundEvent> s, float volume, float pitch) {
        Fx.sound(level, body[side].position(), s, volume, pitch);
    }

    void shake(float intensity, int duration) {
        for (ServerPlayer p : viewers()) Fx.camera(p, CameraPayload.SHAKE, intensity, duration, 0);
    }

    void flash(int argb) {
        for (ServerPlayer p : viewers()) Fx.camera(p, CameraPayload.FLASH, 0.8f, 6, argb);
    }

    // ------------------------------------------------------------------------------------------------ sync and end

    /** The players in the fight plus anyone close enough to watch. */
    List<ServerPlayer> viewers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(center) < 64 * 64) out.add(p);
        for (LivingEntity e : body) if (e instanceof ServerPlayer p && !out.contains(p)) out.add(p);
        return out;
    }

    void sync() {
        List<Integer> stats = new ArrayList<>();
        for (Fighter f : state.f) {
            stats.add(Math.max(0, f.hp)); stats.add(Math.max(0, f.ce)); stats.add(f.meter); stats.add(f.momentum);
            stats.add(f.stance.ordinal()); stats.add(f.slowTurns); stats.add(f.burnTurns); stats.add(f.zoneTurns); stats.add(f.domainTurns);
        }
        for (ServerPlayer p : viewers()) {
            int side = sideOf(p);
            ServerPlayNetworking.send(p, new BattlePayloads.State(id, side, phase, Math.max(0, timer), state.turn, state.range.ordinal(),
                    state.winner, side >= 0 && picks[side] != null, kit(0).character, kit(1).character, body[0].getId(), body[1].getId(), stats));
        }
    }

    void forfeit(int side) {
        if (closed) return;
        if (!state.over()) state.winner = 1 - side;
        phase = BattlePayloads.DONE;
        timer = 1;
        sync();
        close();
    }

    void close() {
        if (closed) return;
        closed = true;
        phase = BattlePayloads.CLOSED;
        sync();
        for (int i = 0; i < 2; i++) {
            LivingEntity e = body[i];
            Anim.stop(e);
            if (ai[i] && e instanceof Mob) e.discard();
        }
    }
}
