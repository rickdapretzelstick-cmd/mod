package dev.rick.jjk.core.clash;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.BeamClashCheckPayload;
import dev.rick.jjk.core.net.BeamClashJudgePayload;
import dev.rick.jjk.core.net.BeamClashStatePayload;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * One beam clash: two ultimate beams meeting head-on, and the power struggle between their casters. The one authority
 * on it: who is in it, where the two beams meet, every skill check and how it was judged, the score, and the result.
 *
 * <p>Its life: {@link Phase#COUNTER} (one beam is out or charging, the other answering it: the first to fire is cut off
 * short of the other's source until the second meets it) → {@link Phase#INTRO} (both meet: the collision mass forms
 * and slides to the middle while the camera pulls out) → {@link Phase#DUEL} (repeated circular skill checks for both
 * at once; GREAT +2, GOOD +1, MISS -1; the collision point is the score) → {@link Phase#RESOLVE} (a beat of
 * hesitation, then the winner's surge drives the collision into the loser and punches through; a true tie collapses both
 * into one explosion) → {@link Phase#ENDED}.
 *
 * <p>When the two meet, a beam that is far stronger than the other ({@link ClashBeam#beamStrength}: one fired long
 * before and nearly spent, met by a fresh one) overpowers it at once: no struggle, straight to the breakthrough. Two
 * beams near enough in strength struggle for the full duel ({@link #DUEL_TICKS}, twice the original length). The two
 * sides are judged by the same rules whoever they are, the same character on both sides included: nothing favours the
 * side that fired first.
 *
 * <p>Nothing here trusts a client: a client only says how long after it first saw a check its player pressed, and that
 * is checked against the server's own clock and the player's measured latency before the server judges it.
 */
public final class BeamClashSession {
    public enum Phase { COUNTER, INTRO, DUEL, RESOLVE, ENDED }

    public enum Outcome { NONE, A, B, TIE, CANCELLED }

    public enum Judgement { GREAT, GOOD, MISS }

    // Timing (ticks). The sustained struggle is twice the original 104-tick (5 s) duel: about ten seconds of checks.
    public static final int INTRO_TICKS = 32, DUEL_TICKS = 2 * 104, HESITATE_TICKS = 10, BREAK_TICKS = 16, AFTER_TICKS = 14;
    /** The shortest the first beam waits, cut off, for its answer to fire (longer when answered early in a charge). */
    static final int COUNTER_WAIT = 70;
    /** One beam this many times stronger than the other overpowers it the moment they meet. */
    public static final float OVERPOWER_RATIO = 2f;
    static final int SCORE_GREAT = 2, SCORE_GOOD = 1, SCORE_MISS = -1;
    /** Power difference that drives the collision all the way to one side's source. */
    static final double FULL_SWING = 8;
    /** How much of the line between the two sources the collision can travel either way from the middle. */
    static final double SWING = 0.4;

    public final int id;
    public final ServerLevel level;
    final Side[] sides = new Side[2];
    final Random rng;
    Phase phase = Phase.COUNTER;
    int phaseAge;
    int age;
    Outcome outcome = Outcome.NONE;
    /** Where the beams meet, 0 (at A's source) .. 1 (at B's). */
    double collision;
    /** The collision eased toward the score (what's shown), and the score it aims for. */
    double target;
    /** How hard the struggle presses right now (0..1+): camera shake, sound, light. */
    float intensity;
    /** Which way the last judgement pushed (-1 toward A, +1 toward B), fading out: for the surge visuals. */
    float push;
    private int nextCheckId = 1;
    /** Why it ended, if not decided (tests, debugging). */
    String reason = "";
    /** How long the first beam out may wait for the second (ticks). */
    int counterWait = COUNTER_WAIT;
    /** Decided the moment the beams met, one overpowering the other. */
    boolean overpowered;

    /** One duellist. */
    public static final class Side {
        final int index;
        public final LivingEntity entity;
        /** Their beam, once it exists (the answering beam is created a moment after the clash). */
        @Nullable ClashBeam beam;
        /** Their beam is firing (and so held by the clash). */
        boolean out;
        /** Where their beam comes from (predicted until it fires). */
        Vec3 origin;
        int power;
        int greats, goods, misses;
        @Nullable Check check;
        /** When (ms, server clock) the next check is due. */
        long nextCheckAt;
        int checksDone;
        @Nullable final Float bot;

        Side(int index, LivingEntity entity, @Nullable ClashBeam beam, Vec3 origin) {
            this.index = index;
            this.entity = entity;
            this.beam = beam;
            this.origin = origin;
            this.bot = entity instanceof ServerPlayer ? null : dev.rick.jjk.core.domain.clash.ClashManager.botSkillOf(entity);
        }
    }

    /** A skill check in progress: the needle goes round once in {@code periodMs} from {@code startMs}. */
    static final class Check {
        final int id;
        final long startMs;
        final int periodMs;
        final float zoneStart, goodWidth, greatWidth;
        /** A bot's chosen press, in ms from the start (or -1: it lets it pass). */
        final long botPressMs;

        Check(int id, long startMs, int periodMs, float zoneStart, float goodWidth, float greatWidth, long botPressMs) {
            this.id = id;
            this.startMs = startMs;
            this.periodMs = periodMs;
            this.zoneStart = zoneStart;
            this.goodWidth = goodWidth;
            this.greatWidth = greatWidth;
            this.botPressMs = botPressMs;
        }

        float angleAt(double ms) {
            return (float) (ms / periodMs * 360.0);
        }

        double msAt(float angle) {
            return angle / 360.0 * periodMs;
        }

        Judgement judge(float angle) {
            if (angle >= zoneStart && angle <= zoneStart + greatWidth) return Judgement.GREAT;
            if (angle >= zoneStart && angle <= zoneStart + goodWidth) return Judgement.GOOD;
            return Judgement.MISS;
        }
    }

    BeamClashSession(int id, ServerLevel level, LivingEntity a, ClashBeam aBeam, LivingEntity b, @Nullable ClashBeam bBeam, Vec3 bOrigin) {
        this.id = id;
        this.level = level;
        this.rng = new Random(level.getGameTime() * 1_000_003L + id * 31L + a.getId() * 17L + b.getId());
        sides[0] = new Side(0, a, aBeam, aBeam.beamOrigin());
        sides[1] = new Side(1, b, bBeam, bBeam != null ? bBeam.beamOrigin() : bOrigin);
        collision = target = 0.5;
    }

    public Phase phase() {
        return phase;
    }

    public Outcome outcome() {
        return outcome;
    }

    public List<LivingEntity> entities() {
        return List.of(sides[0].entity, sides[1].entity);
    }

    @Nullable
    public Side sideOf(LivingEntity e) {
        for (Side s : sides) if (s.entity == e) return s;
        return null;
    }

    @Nullable
    Side sideOf(ClashBeam beam) {
        for (Side s : sides) if (s.beam == beam) return s;
        return null;
    }

    Vec3 a() {
        return sides[0].origin;
    }

    Vec3 b() {
        return sides[1].origin;
    }

    /** Where the two beams meet right now. */
    public Vec3 point() {
        return a().add(b().subtract(a()).scale(collision));
    }

    /** How far {@code beam} reaches: to the collision point, a little into it (so the two merge in the mass). */
    public double reach(ClashBeam beam) {
        Side s = sideOf(beam);
        if (s == null) return beam.beamRange();
        return Math.max(1.5, s.origin.distanceTo(point()) + 0.6);
    }

    /**
     * The cut-off point while only one beam is out: a few blocks short of the side still answering, short of both its
     * source and its caster's body (Yuta stands in front of Rika's mouth).
     */
    private double pinFor(int firedSide) {
        Side fired = sides[firedSide], waiting = sides[1 - firedSide];
        Vec3 axis = waiting.origin.subtract(fired.origin);
        double span = Math.max(1, axis.length());
        axis = axis.scale(1 / span);
        double body = waiting.entity.getBoundingBox().getCenter().subtract(fired.origin).dot(axis);
        double reach = Mth.clamp(Math.min(span - 4.5, body - 2.5), span * 0.2, span * 0.85);
        double f = reach / span;
        return firedSide == 0 ? f : 1 - f;
    }

    // --- Lifecycle ---

    /**
     * {@code beam} has fired. The first out is cut off a few blocks short of the other's source (the answer is still
     * coming); the second meets it and the clash proper begins.
     */
    void beamOut(ClashBeam beam) {
        Side s = sideOf(beam.beamOwner());
        if (s == null || s.out || phase != Phase.COUNTER) return;
        s.beam = beam;
        s.out = true;
        s.origin = beam.beamOrigin();
        Side other = sides[1 - s.index];
        if (!other.out) {
            collision = target = pinFor(s.index);
            beam.enterClash(this, s.origin, other.origin.subtract(s.origin));
            Fx.play(level, "bclash_pin", point(), other.origin.subtract(s.origin), 1f, s.entity.getId());
            sync();
            return;
        }
        bothOut();
    }

    /** The answering beam has fired: both meet. */
    void bothOut() {
        Vec3 axis = b().subtract(a()).normalize();
        for (Side s : sides) {
            if (s.beam == null) continue;
            s.origin = s.beam.beamOrigin();
        }
        // Both re-aimed down one line so they meet head-on, and their casters turned to face each other.
        sides[0].beam.enterClash(this, a(), axis);
        sides[1].beam.enterClash(this, b(), axis.reverse());
        collision = target = Mth.clamp(collision, 0.1, 0.9);
        ClashCommon.face(sides[0].entity, sides[1].entity.getEyePosition());
        ClashCommon.face(sides[1].entity, sides[0].entity.getEyePosition());
        Fx.play(level, "bclash_collide", point(), axis, 1f, sides[0].entity.getId());
        Fx.shake(level, point(), 72, 1.6f, 30);
        float sa = Math.max(0.01f, sides[0].beam.beamStrength()), sb = Math.max(0.01f, sides[1].beam.beamStrength());
        if (sa >= sb * OVERPOWER_RATIO || sb >= sa * OVERPOWER_RATIO) {
            // One is all but spent against a fresh one: it is overpowered on contact, no struggle.
            overpowered = true;
            outcome = sa > sb ? Outcome.A : Outcome.B;
            reason = "overpowered (" + sa + " against " + sb + ")";
            setPhase(Phase.RESOLVE);
            phaseAge = HESITATE_TICKS;
            Fx.play(level, "bclash_decide", point(), b().subtract(a()), outcome.ordinal(), sides[0].entity.getId());
            return;
        }
        setPhase(Phase.INTRO);
    }

    void setPhase(Phase p) {
        phase = p;
        phaseAge = 0;
        sync();
    }

    void tick() {
        age++;
        phaseAge++;
        for (Side s : sides) {
            if (ClashCommon.forfeit(s.entity, level)) {
                forfeit(s);
                return;
            }
        }
        if (phase != Phase.COUNTER) for (Side s : sides) ClashCommon.hold(s.entity);
        switch (phase) {
            case COUNTER -> {
                if (phaseAge > counterWait) {
                    // The answer never came: the beam that is out goes on, uncontested.
                    reason = "no answer in time (" + (sides[0].out ? "A out" : "A not out") + ", " + (sides[1].out ? "B out" : "B not out") + ")";
                    cancel(true);
                    return;
                }
            }
            case INTRO -> {
                // The mass forms where they met and slides to the middle.
                double k = Mth.clamp(phaseAge / (double) INTRO_TICKS, 0, 1);
                k = k * k * (3 - 2 * k);
                collision = Mth.lerp(0.18 + 0.82 * k, collision, 0.5);
                target = collision;
                intensity = 0.6f + 0.4f * (float) k;
                if (phaseAge >= INTRO_TICKS) {
                    collision = target = 0.5;
                    long now = System.currentTimeMillis();
                    for (Side s : sides) s.nextCheckAt = now + 250 + rng.nextInt(200);
                    setPhase(Phase.DUEL);
                }
            }
            case DUEL -> {
                long now = System.currentTimeMillis();
                for (Side s : sides) duelTick(s, now);
                double diff = sides[0].power - sides[1].power;
                target = 0.5 + Mth.clamp(diff / FULL_SWING, -1, 1) * SWING;
                collision = Mth.lerp(0.22, collision, target);
                intensity = 0.8f + 0.5f * (float) Math.abs(collision - 0.5) / (float) SWING + 0.3f * phaseAge / DUEL_TICKS;
                if (phaseAge >= DUEL_TICKS && sides[0].check == null && sides[1].check == null || phaseAge >= DUEL_TICKS + 30) {
                    for (Side s : sides) s.check = null;
                    decide();
                    setPhase(Phase.RESOLVE);
                    Fx.play(level, "bclash_decide", point(), b().subtract(a()), outcome.ordinal(), sides[0].entity.getId());
                }
            }
            case RESOLVE -> resolveTick();
            case ENDED -> {
                return;
            }
        }
        push *= 0.85f;
        if (age % 2 == 0) sync();
        if (phase != Phase.COUNTER && age % 5 == 0) {
            // The collision tearing at everything round it.
            Destruction.sphere(level, point(), 2.2 + intensity, 50f, 18, sides[0].entity, null, "beam_clash");
        }
    }

    private void duelTick(Side s, long now) {
        Check c = s.check;
        if (c == null) {
            // A new check once the last has had its moment (none started in the last second of the duel).
            if (now >= s.nextCheckAt && phaseAge < DUEL_TICKS - 14) startCheck(s, now);
            return;
        }
        if (s.bot != null && c.botPressMs >= 0 && now - c.startMs >= c.botPressMs) {
            judge(s, c, c.angleAt(c.botPressMs));
            return;
        }
        // Let it pass: the needle has gone beyond the zone (allowing for the player's latency).
        double rtt = s.entity instanceof ServerPlayer sp ? Math.min(400, sp.connection.latency()) : 0;
        double limit = c.msAt(c.zoneStart + c.goodWidth) + rtt + 140;
        if (now - c.startMs > limit) judge(s, c, -1);
    }

    private void startCheck(Side s, long now) {
        // Faster as it goes on, and the harder it presses.
        int period = (int) Mth.clamp(1150 - s.checksDone * 70 - 160 * Math.abs(collision - 0.5) / SWING, 720, 1150);
        float zone = 110 + rng.nextFloat() * 180;
        float good = Mth.clamp(56 - s.checksDone * 3, 40, 56);
        float great = 13;
        long bot = -1;
        if (s.bot != null) {
            float skill = Mth.clamp(s.bot, 0f, 1f);
            if (rng.nextFloat() < 0.3f + 0.7f * skill) {
                double aim = zone + great * 0.5 + rng.nextGaussian() * (6 + (1 - skill) * 40);
                bot = (long) Math.max(0, aim / 360.0 * period);
            }
        }
        Check c = new Check(nextCheckId++, now, period, zone, good, great, bot);
        s.check = c;
        if (s.entity instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new BeamClashCheckPayload(id, c.id, period, zone, good, great));
        }
        Fx.play(level, "bclash_check", s.entity.getEyePosition(), Vec3.ZERO, 1f, s.entity.getId());
    }

    /** A press from {@code player}, {@code elapsedMs} after their client began showing {@code check}. */
    void input(ServerPlayer player, int checkId, int elapsedMs) {
        if (phase != Phase.DUEL) return;
        Side s = sideOf(player);
        if (s == null || s.check == null || s.check.id != checkId) return;
        Check c = s.check;
        long now = System.currentTimeMillis();
        double serverElapsed = now - c.startMs;
        double rtt = Math.min(400, Math.max(0, player.connection.latency()));
        // The client's claim is believed only within what the round trip can explain: it can't claim a press earlier
        // than its latency allows, nor later than the server saw it.
        double used = Mth.clamp(elapsedMs, serverElapsed - rtt - 60, serverElapsed + 15);
        judge(s, c, c.angleAt(Math.max(0, used)));
    }

    private void judge(Side s, Check c, float angle) {
        if (s.check != c) return;
        Judgement j = angle < 0 ? Judgement.MISS : c.judge(angle);
        s.check = null;
        s.checksDone++;
        s.nextCheckAt = System.currentTimeMillis() + 300 + rng.nextInt(260);
        switch (j) {
            case GREAT -> {
                s.power += SCORE_GREAT;
                s.greats++;
            }
            case GOOD -> {
                s.power += SCORE_GOOD;
                s.goods++;
            }
            case MISS -> {
                s.power += SCORE_MISS;
                s.misses++;
            }
        }
        // A side that does well pushes the collision toward the other; a miss lets it back toward itself.
        float dir = s.index == 0 ? 1 : -1;
        push = j == Judgement.MISS ? -dir * 0.6f : dir * (j == Judgement.GREAT ? 1f : 0.6f);
        ClashCommon.broadcast(level, point(), entities(), new BeamClashJudgePayload(id, s.index, c.id, j.ordinal(), angle));
        Fx.play(level, "bclash_" + j.name().toLowerCase(java.util.Locale.ROOT), point(), b().subtract(a()).scale(dir), s.index, s.entity.getId());
        Fx.shake(level, point(), 48, j == Judgement.GREAT ? 0.7f : j == Judgement.GOOD ? 0.4f : 0.3f, 6);
    }

    private void decide() {
        int diff = sides[0].power - sides[1].power;
        outcome = diff > 0 ? Outcome.A : diff < 0 ? Outcome.B : Outcome.TIE;
    }

    private void resolveTick() {
        int t = phaseAge;
        if (t <= HESITATE_TICKS) {
            // A beat of stillness, the mass swelling and shuddering.
            intensity = 1.6f + 0.06f * t;
            return;
        }
        int k = t - HESITATE_TICKS;
        if (outcome == Outcome.TIE) {
            // Both crushed into the middle: the mass flares and bursts.
            collision = Mth.lerp(0.3, collision, 0.5);
            intensity = 2.4f + 0.1f * k;
            if (k == BREAK_TICKS / 2) explode();
            if (k >= BREAK_TICKS / 2 + AFTER_TICKS) end();
            return;
        }
        Side win = sides[outcome == Outcome.A ? 0 : 1], lose = sides[outcome == Outcome.A ? 1 : 0];
        // The winner's surge: the collision accelerates into the loser's source.
        double goal = win.index == 0 ? 0.985 : 0.015;
        double f = Mth.clamp(k / (double) BREAK_TICKS, 0, 1);
        collision = Mth.lerp(0.06 + 0.5 * f * f, collision, goal);
        intensity = 2f + 0.08f * k;
        push = win.index == 0 ? 1f : -1f;
        if (k == BREAK_TICKS) breakthrough(win, lose);
        if (k >= BREAK_TICKS + AFTER_TICKS) end();
    }

    private void breakthrough(Side win, Side lose) {
        // The loser's beam collapses; the winner's goes on through, full length, and hits as itself.
        if (lose.beam != null && lose.out) lose.beam.leaveClash(false, 0);
        if (win.beam != null && win.out) win.beam.leaveClash(true, 40);
        lose.beam = null;
        win.beam = null;
        Fx.play(level, "bclash_break", lose.entity.getEyePosition(), lose.origin.subtract(win.origin), win.index, win.entity.getId());
        Fx.shake(level, lose.entity.position(), 96, 2.2f, 36);
        // Whatever the winning beam's own damage is, the loser is driven back by the burst too.
        Vec3 away = lose.entity.position().subtract(win.entity.position()).normalize();
        var b = Hit.builder(win.entity, "beam_clash").type(ModDamageTypes.TECHNIQUE).damage(10f)
                .tag(AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.OTG, AttackTag.ULTIMATE)
                .origin(point()).knockback(Knockback.set(away.scale(1.2).add(0, 0.5, 0))).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                .noComboScaling().fx("beam_hit", 1.6f);
        ClashCommon.release(lose.entity);
        ClashCommon.release(win.entity);
        dev.rick.jjk.hakari.HakariCombat.hit(b.build(), lose.entity);
    }

    private void explode() {
        Vec3 c = point();
        for (Side s : sides) {
            if (s.beam != null && s.out) s.beam.leaveClash(false, 0);
            s.beam = null;
        }
        Fx.play(level, "bclash_tie", c, b().subtract(a()), 1f, sides[0].entity.getId());
        Fx.shake(level, c, 110, 2.4f, 40);
        Destruction.sphere(level, c, 5.5, 60f, 160, sides[0].entity, null, "beam_clash");
        for (Side s : sides) {
            ClashCommon.release(s.entity);
            Vec3 away = s.entity.position().subtract(c);
            away = new Vec3(away.x, 0, away.z);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            LivingEntity other = sides[1 - s.index].entity;
            var b = Hit.builder(other, "beam_clash").type(ModDamageTypes.TECHNIQUE).damage(18f)
                    .tag(AttackTag.TECHNIQUE, AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION, AttackTag.OTG)
                    .origin(c).knockback(Knockback.set(away.scale(1.4).add(0, 0.7, 0))).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                    .noComboScaling().fx("beam_hit", 1.4f);
            dev.rick.jjk.hakari.HakariCombat.hit(b.build(), s.entity);
        }
    }

    /** One side can't go on: the other's beam goes through (if it is out), or it all just stops. */
    private void forfeit(Side gone) {
        Side other = sides[1 - gone.index];
        reason = "side " + gone.index + " forfeit";
        if (phase == Phase.RESOLVE && outcome != Outcome.NONE) {
            end();
            return;
        }
        outcome = Outcome.CANCELLED;
        if (gone.beam != null && gone.out) gone.beam.leaveClash(false, 0);
        if (other.beam != null && other.out && !ClashCommon.forfeit(other.entity, level)) other.beam.leaveClash(true, 30);
        gone.beam = null;
        other.beam = null;
        end();
    }

    /** Called off: {@code keepFired} lets the beam that is out go on uncontested; otherwise every beam collapses. */
    void cancel(boolean keepFired) {
        if (phase == Phase.ENDED) return;
        outcome = Outcome.CANCELLED;
        for (Side s : sides) {
            if (s.beam == null || !s.out) continue;
            if (keepFired && !ClashCommon.forfeit(s.entity, level)) s.beam.leaveClash(true, 30);
            else s.beam.leaveClash(false, 0);
            s.beam = null;
        }
        end();
    }

    /** A beam ended on its own (its caster was stopped some other way). */
    void beamGone(ClashBeam beam) {
        Side s = sideOf(beam);
        if (s == null) return;
        reason = "side " + s.index + "'s beam ended in phase " + phase + " at " + phaseAge;
        s.beam = null;
        if (phase == Phase.RESOLVE) return;
        Side other = sides[1 - s.index];
        outcome = Outcome.CANCELLED;
        if (other.beam != null && other.out) other.beam.leaveClash(true, 30);
        other.beam = null;
        end();
    }

    private void end() {
        if (phase == Phase.ENDED) return;
        phase = Phase.ENDED;
        phaseAge = 0;
        for (Side s : sides) {
            s.check = null;
            ClashCommon.release(s.entity);
        }
        sync();
    }

    void sync() {
        ClashCommon.broadcast(level, point(), entities(), new BeamClashStatePayload(id, phase.ordinal(), phaseAge, sides[0].entity.getId(),
                sides[1].entity.getId(), kind(sides[0]), kind(sides[1]), a(), b(), (float) collision, push, sides[0].power, sides[1].power,
                intensity, outcome.ordinal()));
    }

    private String kind(Side s) {
        return s.beam != null ? s.beam.beamKind() : BeamCounters.kindOf(s.entity);
    }

    // --- For tests ---

    public String reason() {
        return reason;
    }

    public boolean overpowered() {
        return overpowered;
    }

    public int phaseAge() {
        return phaseAge;
    }

    /** The two beams the clash holds (null once released): tests. */
    @Nullable
    public ClashBeam beam(int side) {
        return sides[side].beam;
    }

    public int power(int side) {
        return sides[side].power;
    }

    public double collision() {
        return collision;
    }

    public LivingEntity entity(int side) {
        return sides[side].entity;
    }
}
