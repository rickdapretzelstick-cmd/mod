package dev.rick.jjk.progression.curse;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.CurseAggro;
import dev.rick.jjk.progression.CursedSpirit;
import dev.rick.jjk.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Finger Bearer: the curse that waits in every cursed battle room with a Cursed Finger inside it. Drawn from
 * {@code models/bb/cursed_spirit.bbmodel}; seen only by players who perceive curses (it is a {@link CursedSpirit}).
 *
 * <p>It fights the way the anime shows it, with raw cursed energy and sudden brute force, no technique of its own. A small
 * state machine on the server runs one move at a time, each with a readable windup, a single damage frame (or a projectile
 * that lands once) and a recovery, then a cooldown:
 * <ul>
 *   <li><b>Cursed Energy Shot</b>: a quick palm thrust at range; the shot flies at a speed you can sidestep.</li>
 *   <li><b>Charged Blast</b>: two seconds gathering energy between its hands, then a slow, heavy orb that bursts on impact.
 *   It is left winded afterwards, longest when the blast missed.</li>
 *   <li><b>Point-Blank Burst</b>: when someone stays at arm's length (or crowds it while it charges), a ring on the floor
 *   shows the blast radius for most of a second before the energy erupts and throws everyone in it back.</li>
 *   <li><b>Brutal Rush</b>: it crouches, then charges in a straight line; the first person in its path takes a heavy
 *   blow. Missing, or running into a wall, leaves it staggered.</li>
 *   <li><b>Heavy Follow-Up Smash</b>: a two-handed hammer blow on a marked spot in front of it; it follows a rush that
 *   connected, never so fast that the victim can't get away, and it can be guarded or shielded.</li>
 * </ul>
 * It is a Grade 1 curse ({@link dev.rick.jjk.progression.grade.GradedCurse}), the wall at the start of progression: an
 * ordinary weapon can't touch it, and it fights where you stand:
 * <ul>
 *   <li><b>Leap</b>: keep your distance or climb above it and it crouches and comes down on the spot you were on (the ring
 *   shows where); the landing breaks soft ground and leaves it winded.</li>
 *   <li><b>Spin</b>: stay at its back and it wheels round with a backhand.</li>
 *   <li><b>Pools</b>: where a Charged Blast bursts the energy lingers on the floor, hurting and slowing.</li>
 *   <li><b>Enraged</b> below half health: it roars and throws everyone back, then fights faster, fires its shots in a fan of
 *   three and throws pools down on purpose.</li>
 * </ul>
 * Who it may fight is {@link CurseAggro}'s rule: it only takes a target who perceives it and is in sight (or one it is
 * already fighting: taking the glasses off doesn't end a fight), and every hit goes through {@link Targeting}, which
 * refuses anyone else. Leaving the room ends the chase. Clips ({@code animations/cursed_spirit/}) put their release and
 * impact keys on the same ticks as the code here.
 */
public class FingerBearerEntity extends Monster implements dev.rick.jjk.progression.grade.GradedCurse {
    public enum Move { NONE, SHOT, BLAST, BURST, RUSH, SMASH, LEAP, SPIN, POOL }

    // Move timings (ticks into the move). The clips use the same numbers (tools/gen_finger_bearer_anims.py).
    public static final int SHOT_FIRE = 12, SHOT_END = 24;
    public static final int BLAST_FIRE = 40, BLAST_END = 76, BLAST_LANDED_END = 56;
    public static final int BURST_FIRE = 18, BURST_END = 36;
    public static final int RUSH_WINDUP = 14, RUSH_RUN = 20, STRIKE_HIT = 4, STRIKE_END = 18, MISS_END = 36;
    public static final int SMASH_HIT = 22, SMASH_END = 44;
    public static final int ROAR_TICKS = 30;
    /** Reach of each move (blocks). */
    public static final double CLOSE = 3.6, BURST_RADIUS = 4.5, SMASH_RADIUS = 2.4, SMASH_REACH = 2.6, RUSH_SPEED = 0.62;
    /** Damage before {@link JJKConfig.Progression#fingerBearerDamage}: none of them can take a full-health player. */
    public static final float SHOT_DAMAGE = 5f, BLAST_DAMAGE = 11f, BLAST_SPLASH = 5f, BURST_DAMAGE = 7f, STRIKE_DAMAGE = 9f, SMASH_DAMAGE = 12f;
    private static final int[] COOLDOWN = {0, 50, 160, 110, 120, 90, 150, 70, 200};
    /** The leap: crouch, then a high arc onto where its target stood; the landing breaks soft ground and leaves it open. */
    public static final int LEAP_WINDUP = 14, LEAP_AIR = 16, LEAP_RECOVER = 30;
    public static final double LEAP_RADIUS = 3.4;
    public static final float LEAP_DAMAGE = 12f;
    /** The spin: someone lingering behind it gets a backhand. */
    public static final int SPIN_HIT = 8, SPIN_END = 20;
    public static final double SPIN_RADIUS = 3.2;
    public static final float SPIN_DAMAGE = 8f;
    /** Pools of lingering cursed energy (where a blast burst; thrown down on purpose once it is enraged). */
    public static final int POOL_FIRE = 18, POOL_END = 34, POOL_TICKS = 120;
    public static final double POOL_RADIUS = 1.8;
    public static final float POOL_DAMAGE = 2.5f;
    /** Below this share of its health it is enraged: faster, more shots, pools of its own. */
    public static final float ENRAGE_AT = 0.5f;

    /** The move under way, synced so the renderer can drop its walk cycle while a clip drives the legs. */
    private static final EntityDataAccessor<Byte> MOVE = SynchedEntityData.defineId(FingerBearerEntity.class, EntityDataSerializers.BYTE);

    private Move move = Move.NONE;
    /** Ticks into the current move; for the rush, into its current phase. */
    private int t;
    /** Rush phases: 0 windup, 1 running, 2 striking, 3 staggered after a miss. */
    private int rushPhase;
    private Vec3 aim = Vec3.ZERO;
    private Vec3 smashAt = Vec3.ZERO;
    private boolean landed;
    private final Set<UUID> struck = new HashSet<>();
    private final int[] cooldown = new int[Move.values().length];
    private Move last = Move.NONE;
    private int lastCount;
    /** Ticks before it may start another move (the gap a player gets between attacks). */
    private int rest;
    private int closeTicks;
    private int unseenTicks;
    private int repath;
    private int calmTicks;
    private int stunTicks, sinceStun;
    @Nullable private UUID lastTargetId;
    /** The seal of its room (saved): where it returns to, what bounds the fight, and where its defeat is recorded. */
    @Nullable private BlockPos home;
    private int homeRadius = 9;
    private boolean rewarded;
    private boolean enraged;
    private int behindTicks;
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private int leapPhase;
    private record Pool(Vec3 center, double radius, long until) {}
    private final List<Pool> pools = new ArrayList<>();

    public FingerBearerEntity(EntityType<? extends FingerBearerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 300).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.ATTACK_DAMAGE, 6);
    }

    /** Ties it to its room: it stays inside, and its defeat clears the room. */
    public void setHome(BlockPos seal, int radius) {
        this.home = seal.immutable();
        this.homeRadius = radius;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public Move move() {
        return move;
    }

    public int moveTick() {
        return t;
    }

    public int rushPhase() {
        return rushPhase;
    }

    public int cooldown(Move m) {
        return cooldown[m.ordinal()];
    }

    /** Test hook: makes a move ready (or not) right now. */
    public void setCooldown(Move m, int ticks) {
        cooldown[m.ordinal()] = ticks;
    }

    public void setRest(int ticks) {
        rest = ticks;
    }

    /** Test hook: starts a move at once against {@code target} (the AI's choice skipped; the move itself runs as normal). */
    public void forceMove(Move m, LivingEntity target) {
        if (level() instanceof ServerLevel level && m != Move.NONE) {
            if (m == Move.BURST) beginBurst(level);
            else if (m == Move.SMASH) beginSmash(level, target);
            else start(level, m, target);
        }
    }

    public boolean rewarded() {
        return rewarded;
    }

    @Override
    public dev.rick.jjk.progression.grade.CurseGrade curseGrade() {
        return dev.rick.jjk.progression.grade.CurseGrade.GRADE_1;
    }

    @Override
    public String curseKind() {
        return "finger_bearer";
    }

    /** Past half health: its second phase. */
    public boolean enraged() {
        return enraged;
    }

    public int pools() {
        return pools.size();
    }

    /** Test hook: how long its target has been behind it. */
    public void setBehindTicks(int t) {
        behindTicks = t;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOVE, (byte) 0);
    }

    /** The move as the client sees it. */
    public Move syncedMove() {
        int i = entityData.get(MOVE);
        return i >= 0 && i < Move.values().length ? Move.values()[i] : Move.NONE;
    }

    @Override
    protected void registerGoals() {
        // Everything is the state machine in customServerAiStep (no vanilla goals to fight it for control).
    }

    // --- The fight ---

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        for (int i = 0; i < cooldown.length; i++) if (cooldown[i] > 0) cooldown[i]--;
        if (rest > 0) rest--;
        tickPools(level);
        LivingEntity target = validTarget(level);
        if (move == Move.NONE && !enraged && getHealth() <= getMaxHealth() * ENRAGE_AT && isAlive()) {
            enrage(level);
            return;
        }
        if (move != Move.NONE) {
            tickMove(level, target);
            return;
        }
        if (target == null) {
            idle(level);
            return;
        }
        calmTicks = 0;
        if (!target.getUUID().equals(lastTargetId)) {
            // A new foe: it turns on them with a roar before the first attack (the encounter's opening tell).
            lastTargetId = target.getUUID();
            turnToward(target.position(), 30f);
            getNavigation().stop();
            Anim.playOn(this, "finger_bearer_roar");
            sound(level, SoundEvents.WARDEN_ROAR, 1.6f, 0.75f);
            fx(level, "fb_roar", getEyePosition(), getLookAngle(), 1f);
            rest = Math.max(rest, ROAR_TICKS);
            return;
        }
        engage(level, target);
    }

    /** The current target if it is still a fair one; otherwise looks for a new one. */
    @Nullable
    private LivingEntity validTarget(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null) {
            if (!stillValid(level, target)) {
                setTarget(null);
                target = null;
            } else if (hasLineOfSight(target)) {
                unseenTicks = 0;
            } else if (++unseenTicks > 100) {
                // Out of sight for five seconds (behind a pillar, up the shaft): it loses them.
                setTarget(null);
                target = null;
            }
        }
        if (target == null && tickCount % 10 == 0) target = acquire(level);
        return target;
    }

    private boolean stillValid(ServerLevel level, LivingEntity e) {
        if (!e.isAlive() || e.isRemoved() || e.level() != level || e.isSpectator()) return false;
        if (e instanceof ServerPlayer p && p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (!Targeting.canTarget(this, e)) return false;
        if (distanceToSqr(e) > 32 * 32) return false;
        // The fight belongs to the room: leaving it ends the chase.
        return home == null || e.position().distanceToSqr(Vec3.atBottomCenterOf(home)) <= (homeRadius + 3.0) * (homeRadius + 3.0);
    }

    /**
     * Vanilla's check asks {@code Player.isCreative()}, which follows abilities rather than the game mode; this asks the
     * game mode (and spectators are never valid). The perception rule is applied on top by the setTarget guard.
     */
    @Override
    @Nullable
    protected LivingEntity asValidTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return null;
        if (target instanceof ServerPlayer p && (p.isSpectator() || p.gameMode.getGameModeForPlayer() == GameType.CREATIVE)) return null;
        return target;
    }

    /** The nearest player in the room who perceives it (or is already its enemy) and is in plain sight. */
    @Nullable
    private LivingEntity acquire(ServerLevel level) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double d = distanceToSqr(p);
            if (d > 20 * 20 || d >= bestD) continue;
            if (!stillValid(level, p) || !CurseAggro.mayTarget(this, p) || !hasLineOfSight(p)) continue;
            best = p;
            bestD = d;
        }
        if (best == null) return null;
        setTarget(best);
        return getTarget() == best ? best : null;
    }

    private void idle(ServerLevel level) {
        lastTargetId = null;
        closeTicks = 0;
        if (home != null) {
            Vec3 h = Vec3.atBottomCenterOf(home).add(0, 1, 0);
            double d = position().distanceToSqr(h);
            if (d > 24 * 24) {
                // Pushed or knocked far out of its room: it is simply back where it belongs.
                teleportTo(h.x, h.y, h.z);
                getNavigation().stop();
            } else if (d > 3 * 3 && (getNavigation().isDone() || tickCount % 40 == 0)) {
                getNavigation().moveTo(h.x, h.y, h.z, 0.7);
            }
        }
        // Nobody left to fight: it slowly recovers (a fight abandoned is a fight started over).
        if (++calmTicks > 200 && getHealth() < getMaxHealth() && tickCount % 20 == 0) heal(getMaxHealth() * 0.02f);
    }

    private void engage(ServerLevel level, LivingEntity target) {
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        closeTicks = d <= CLOSE ? closeTicks + 1 : Math.max(0, closeTicks - 2);
        // Someone working its back: it notices.
        Vec3 to = flatDir(target.position());
        behindTicks = d <= 4.5 && forward().dot(to) < -0.35 ? behindTicks + 1 : Math.max(0, behindTicks - 2);
        if (getNavigation().isDone()) turnToward(target.position(), 12f);
        getLookControl().setLookAt(target, 30f, 30f);
        if (rest <= 0 && !Combat.actionsLocked(this)) {
            Move pick = choose(level, target, d, los);
            if (pick != Move.NONE) {
                start(level, pick, target);
                return;
            }
        }
        approach(target, d, los);
    }

    /** Picks the next move from distance, sight and cooldowns; never the same move three times running. */
    private Move choose(ServerLevel level, LivingEntity target, double d, boolean los) {
        List<Move> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        // Reactions to where its prey stands come first: behind it, it spins; far off or up high, it leaps on them.
        if (behindTicks >= 14 && d <= 4.5 && ready(Move.SPIN)) return Move.SPIN;
        boolean high = target.getY() > getY() + 2.5;
        if (ready(Move.LEAP) && d <= 22 && (high || d >= (enraged ? 9 : 12)) && getRandom().nextInt(high ? 1 : 2) == 0 && leapClear(level, target)) {
            return Move.LEAP;
        }
        if (enraged && los && d >= 5 && d <= 16 && ready(Move.POOL)) add(options, weights, Move.POOL, 2);
        if (d <= CLOSE) {
            if (ready(Move.SMASH) && closeTicks >= 6) add(options, weights, Move.SMASH, 3);
            if (ready(Move.BURST) && closeTicks >= 16) add(options, weights, Move.BURST, 2);
        } else if (los) {
            if (d <= 20 && ready(Move.SHOT)) add(options, weights, Move.SHOT, 3);
            if (d >= 6 && d <= 22 && ready(Move.BLAST)) add(options, weights, Move.BLAST, 2);
            if (d >= 4.5 && d <= 14 && ready(Move.RUSH) && laneClear(level, target)) add(options, weights, Move.RUSH, 3);
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            if (options.get(i) == last && lastCount >= 2) {
                options.remove(i);
                weights.remove(i);
            }
        }
        if (options.isEmpty()) return Move.NONE;
        int total = 0;
        for (int w : weights) total += w;
        int roll = getRandom().nextInt(total);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return options.get(i);
        }
        return options.getFirst();
    }

    private static void add(List<Move> options, List<Integer> weights, Move m, int w) {
        options.add(m);
        weights.add(w);
    }

    private boolean ready(Move m) {
        return cooldown[m.ordinal()] <= 0;
    }

    /** A straight run to the target meets no wall, at the knees or at the chest. */
    private boolean laneClear(ServerLevel level, LivingEntity target) {
        for (double y : new double[] {0.6, 2.0}) {
            Vec3 a = position().add(0, y, 0), b = target.position().add(0, Math.min(y, target.getBbHeight() * 0.8), 0);
            if (level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != Type.MISS) return false;
        }
        return true;
    }

    /** Walks in when it has nothing to throw from here, holds its ground at a sensible range otherwise. Re-paths only every half second. */
    private void approach(LivingEntity target, double d, boolean los) {
        boolean rangedSoon = los && (cooldown[Move.SHOT.ordinal()] < 20 || cooldown[Move.BLAST.ordinal()] < 20);
        boolean wantClose = !los || !rangedSoon || ready(Move.SMASH);
        double stopAt = wantClose ? 2.6 : 8;
        if (d <= stopAt) {
            getNavigation().stop();
            return;
        }
        if (--repath <= 0 || getNavigation().isDone()) {
            repath = 10;
            getNavigation().moveTo(target, wantClose ? 1.05 : 0.85);
        }
    }

    private void start(ServerLevel level, Move m, LivingEntity target) {
        move = m;
        t = 0;
        rushPhase = 0;
        landed = false;
        struck.clear();
        getNavigation().stop();
        aim = flatDir(target.position());
        if (m == last) lastCount++;
        else {
            last = m;
            lastCount = 1;
        }
        Vec3 chest = position().add(0, 2.0, 0);
        switch (m) {
            case SHOT -> {
                Anim.playOn(this, "finger_bearer_shot");
                sound(level, SoundEvents.EVOKER_PREPARE_ATTACK, 0.9f, 1.5f);
                fx(level, "fb_shot_windup", handPos(), Vec3.ZERO, 1f);
            }
            case BLAST -> {
                Anim.playOn(this, "finger_bearer_blast");
                sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 1.6f, 0.7f);
            }
            case BURST -> beginBurst(level);
            case RUSH -> {
                Anim.playOn(this, "finger_bearer_rush_windup");
                sound(level, SoundEvents.RAVAGER_ROAR, 1.3f, 0.8f);
                fx(level, "fb_rush_windup", chest, aim, 1f);
            }
            case SMASH -> beginSmash(level, target);
            case LEAP -> {
                leapPhase = 0;
                Anim.playOn(this, "finger_bearer_rush_windup");
                sound(level, SoundEvents.RAVAGER_ROAR, 1.2f, 0.6f);
                leapTo = target.position();
                // The landing ring shows from the start: get out of it.
                fx(level, "fb_smash_warn", leapTo.add(0, 0.05, 0), new Vec3(LEAP_WINDUP + LEAP_AIR, 0, 0), (float) LEAP_RADIUS);
            }
            case SPIN -> {
                Anim.playOn(this, "finger_bearer_rush_strike");
                sound(level, SoundEvents.RAVAGER_ATTACK, 1.1f, 0.9f);
                fx(level, "fb_burst_warn", position().add(0, 0.05, 0), new Vec3(SPIN_HIT, 0, 0), (float) SPIN_RADIUS);
            }
            case POOL -> {
                Anim.playOn(this, "finger_bearer_smash");
                sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 0.9f);
                fx(level, "fb_gather", position().add(forward().scale(1.2)).add(0, 1.6, 0), aim, 1f);
            }
            default -> {}
        }
    }

    private void beginBurst(ServerLevel level) {
        move = Move.BURST;
        t = 0;
        struck.clear();
        Anim.playOn(this, "finger_bearer_burst");
        sound(level, SoundEvents.WARDEN_HEARTBEAT, 2f, 0.6f);
        sound(level, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
        // The danger area, drawn on the floor for the whole windup.
        fx(level, "fb_burst_warn", position().add(0, 0.05, 0), new Vec3(BURST_FIRE, 0, 0), (float) BURST_RADIUS);
    }

    private void beginSmash(ServerLevel level, LivingEntity target) {
        move = Move.SMASH;
        t = 0;
        struck.clear();
        turnToward(target.position(), 40f);
        aim = flatDir(target.position());
        // The spot is chosen once: step off it and the blow lands on nothing.
        smashAt = position().add(aim.scale(SMASH_REACH));
        Anim.playOn(this, "finger_bearer_smash");
        sound(level, SoundEvents.RAVAGER_ATTACK, 1.2f, 0.6f);
        fx(level, "fb_smash_warn", smashAt.add(0, 0.05, 0), new Vec3(SMASH_HIT, 0, 0), (float) SMASH_RADIUS);
    }

    private void tickMove(ServerLevel level, @Nullable LivingEntity target) {
        t++;
        switch (move) {
            case SHOT -> tickShot(level, target);
            case BLAST -> tickBlast(level, target);
            case BURST -> tickBurst(level);
            case RUSH -> tickRush(level, target);
            case SMASH -> tickSmash(level);
            case LEAP -> tickLeap(level);
            case SPIN -> tickSpin(level, target);
            case POOL -> tickPool(level, target);
            default -> end(0, 0);
        }
    }

    private void tickShot(ServerLevel level, @Nullable LivingEntity target) {
        // It tracks during the windup but commits for the last few ticks: a late sidestep beats it.
        if (target != null && t <= SHOT_FIRE - 4) {
            turnToward(target.position(), 12f);
            aim = aimAt(target, handPos());
        }
        if (crowded(target) && t < SHOT_FIRE - 2) {
            beginBurst(level);
            return;
        }
        if (t == SHOT_FIRE) {
            Vec3 from = handPos();
            CursedEnergyShotEntity.fire(level, this, from, aim, CursedEnergyShotEntity.Kind.SHOT);
            if (enraged) {
                // Enraged: a fan of three.
                for (float yaw : new float[] {-14f, 14f}) CursedEnergyShotEntity.fire(level, this, from, aim.yRot(yaw * Mth.DEG_TO_RAD), CursedEnergyShotEntity.Kind.SHOT);
            }
            sound(level, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.7f);
            fx(level, "fb_shot_fire", from, aim, 1f);
        }
        if (t >= SHOT_END) end(COOLDOWN[Move.SHOT.ordinal()], 14);
    }

    private void tickBlast(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 hands = position().add(forward().scale(1.3)).add(0, 2.1, 0);
        if (t < BLAST_FIRE) {
            if (target != null && t <= BLAST_FIRE - 8) {
                turnToward(target.position(), 6f);
                aim = aimAt(target, hands);
            }
            if (t % 3 == 0) fx(level, "fb_gather", hands, aim, t / (float) BLAST_FIRE);
            if (t == BLAST_FIRE - 12) sound(level, SoundEvents.WARDEN_HEARTBEAT, 2f, 1.2f);
            // Crowding it while it charges is answered with the burst (that is the point of the burst).
            if (crowded(target) && t < BLAST_FIRE - 6) beginBurst(level);
            return;
        }
        if (t == BLAST_FIRE) {
            CursedEnergyShotEntity.fire(level, this, hands, aim, CursedEnergyShotEntity.Kind.BLAST);
            sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.6f);
            fx(level, "fb_blast_fire", hands, aim, 1f);
            Fx.shake(level, hands, 14, 0.5f, 8);
        }
        // Winded after the release; much longer when the blast found nobody.
        if (t >= BLAST_LANDED_END && landed) {
            Anim.playOn(this, "finger_bearer_recover");
            end(COOLDOWN[Move.BLAST.ordinal()], 16);
        } else if (t >= BLAST_END) {
            end(COOLDOWN[Move.BLAST.ordinal()], 16);
        }
    }

    /** The blast it fired reached someone (the shot reports back), so its recovery is the short one. */
    void blastLanded() {
        if (move == Move.BLAST) landed = true;
    }

    /** Someone has stood within arm's reach for half a second and the burst is ready. */
    private boolean crowded(@Nullable LivingEntity target) {
        if (target == null || !ready(Move.BURST)) return false;
        closeTicks = distanceTo(target) <= CLOSE ? closeTicks + 1 : 0;
        return closeTicks >= 10;
    }

    private void tickBurst(ServerLevel level) {
        if (t == BURST_FIRE) {
            Vec3 c = position().add(0, 1.2, 0);
            HitShape shape = HitShape.sphere(c, BURST_RADIUS);
            for (LivingEntity e : areaTargets(level, shape, c)) {
                strike(e, Hit.builder(this, "fb_burst").type(ModDamageTypes.TECHNIQUE).damage(dmg(BURST_DAMAGE))
                        .tag(AttackTag.TECHNIQUE, AttackTag.AREA).knockback(Knockback.radial(c, 1.7, 0.55)).hitstun(8).guardDamage(2)
                        .origin(c).fx("fb_impact", 1f));
            }
            sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 0.8f);
            Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 1f, 1.3f);
            fx(level, "fb_burst", position().add(0, 0.1, 0), Vec3.ZERO, (float) BURST_RADIUS);
            Fx.shake(level, c, 16, 0.6f, 8);
        }
        if (t >= BURST_END) end(COOLDOWN[Move.BURST.ordinal()], 18);
    }

    private void tickRush(ServerLevel level, @Nullable LivingEntity target) {
        switch (rushPhase) {
            case 0 -> {
                if (target != null && t <= RUSH_WINDUP - 3) {
                    turnToward(target.position(), 14f);
                    aim = flatDir(target.position());
                }
                if (t >= RUSH_WINDUP) phase(1, "finger_bearer_rush");
            }
            case 1 -> {
                faceDir(aim);
                if (t > 1 && (horizontalCollision || outsideRoom())) {
                    // Into a wall (or the edge of its room): it rebounds, dazed.
                    sound(level, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.2f, 0.6f);
                    // It hits hard enough to break what's soft (restored later, like any world damage).
                    dev.rick.jjk.util.Destruction.sphere(level, position().add(aim.scale(1.2)).add(0, 1.4, 0), 1.6, 0.9f, 20, this, null);
                    fx(level, "fb_wall", position().add(aim.scale(0.9)).add(0, 1.4, 0), aim, 1f);
                    Fx.shake(level, position(), 12, 0.4f, 6);
                    setDeltaMovement(aim.scale(-0.25).add(0, 0.15, 0));
                    phase(3, "finger_bearer_rush_miss");
                    return;
                }
                setDeltaMovement(aim.x * RUSH_SPEED, getDeltaMovement().y, aim.z * RUSH_SPEED);
                if (t % 3 == 0) {
                    sound(level, SoundEvents.RAVAGER_STEP, 1f, 0.8f);
                    fx(level, "fb_rush", position().add(0, 0.2, 0), aim, 1f);
                }
                Vec3 from = position().add(0, 1.4, 0);
                if (!HitboxQuery.targets(this, HitShape.capsule(from, from.add(aim.scale(1.9)), 1.0), 0.1, true).isEmpty()) {
                    phase(2, "finger_bearer_rush_strike");
                    return;
                }
                if (t >= RUSH_RUN) {
                    sound(level, SoundEvents.RAVAGER_STUNNED, 1f, 0.8f);
                    phase(3, "finger_bearer_rush_miss");
                }
            }
            case 2 -> {
                faceDir(aim);
                setDeltaMovement(getDeltaMovement().multiply(0.35, 1, 0.35));
                if (t == STRIKE_HIT) {
                    Vec3 from = position().add(0, 1.4, 0);
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(aim.scale(2.6)), 1.1), 0.1, true)) {
                        HitResult r = strike(e, Hit.builder(this, "fb_rush").damage(dmg(STRIKE_DAMAGE)).tag(AttackTag.MELEE, AttackTag.HEAVY)
                                .knockback(Knockback.directional(aim, 1.25, 0.35)).hitstun(10).guardDamage(3).origin(from).fx("fb_impact", 1.4f));
                        if (r != null && r.outcome() == HitResult.Outcome.HIT) landed = true;
                    }
                    sound(level, landed ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.7f);
                    if (landed) Fx.shake(level, position(), 10, 0.5f, 6);
                }
                if (t >= STRIKE_END) {
                    // Connected: the hammer blow follows, slow enough to get away from (hitstun ends long before it lands).
                    if (landed && ready(Move.SMASH) && target != null && distanceTo(target) <= 6) {
                        cooldown[Move.RUSH.ordinal()] = COOLDOWN[Move.RUSH.ordinal()];
                        beginSmash(level, target);
                    } else {
                        end(COOLDOWN[Move.RUSH.ordinal()], 14);
                    }
                }
            }
            default -> {
                // Staggered: open to punishment for most of two seconds.
                setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                if (t == 2) fx(level, "fb_stagger", getEyePosition(), Vec3.ZERO, 1f);
                if (t >= MISS_END) end(COOLDOWN[Move.RUSH.ordinal()], 10);
            }
        }
    }

    private void phase(int p, String clip) {
        rushPhase = p;
        t = 0;
        Anim.playOn(this, clip);
    }

    private boolean outsideRoom() {
        return home != null && position().distanceToSqr(Vec3.atBottomCenterOf(home)) > (homeRadius - 1.0) * (homeRadius - 1.0);
    }

    private void tickSmash(ServerLevel level) {
        if (t == SMASH_HIT - 6) sound(level, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.5f);
        if (t == SMASH_HIT) {
            Vec3 c = smashAt.add(0, 0.6, 0);
            for (LivingEntity e : areaTargets(level, HitShape.sphere(c, SMASH_RADIUS), c)) {
                strike(e, Hit.builder(this, "fb_smash").damage(dmg(SMASH_DAMAGE)).tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.AREA)
                        .knockback(Knockback.radial(c, 0.9, 0.7)).hitstun(12).guardDamage(4).origin(c).fx("fb_impact", 1.6f));
            }
            Fx.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4f, 0.7f);
            Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.8f);
            fx(level, "fb_smash", smashAt.add(0, 0.05, 0), Vec3.ZERO, (float) SMASH_RADIUS);
            Fx.shake(level, c, 18, 0.8f, 10);
        }
        if (t >= SMASH_END) end(COOLDOWN[Move.SMASH.ordinal()], 22);
    }

    /** Those caught in an area, seen from its centre (no damage through a wall or pillar). */
    private List<LivingEntity> areaTargets(ServerLevel level, HitShape shape, Vec3 center) {
        return HitboxQuery.query(level, shape, 0.1, e -> Targeting.canTarget(this, e) && HitboxQuery.hasLineOfSight(level, center, e));
    }

    /** Lands one hit, once per target per move. */
    @Nullable
    private HitResult strike(LivingEntity target, Hit.Builder hit) {
        if (!struck.add(target.getUUID())) return null;
        return HitResolver.resolve(hit.noComboScaling().build(), target);
    }

    static float dmg(float base) {
        return base * JJKConfig.get().progression.fingerBearerDamage;
    }

    private void end(int cooldownTicks, int restTicks) {
        if (enraged) {
            cooldownTicks = cooldownTicks * 3 / 4;
            restTicks = restTicks * 3 / 5;
        }
        if (move != Move.NONE) cooldown[move.ordinal()] = Math.max(cooldown[move.ordinal()], cooldownTicks);
        move = Move.NONE;
        rushPhase = 0;
        t = 0;
        rest = Math.max(rest, restTicks);
        closeTicks = 0;
        struck.clear();
    }

    /** Stops whatever it was doing (death, a reset): nothing it started lands afterwards. */
    public void cancelMove() {
        setNoGravity(false);
        move = Move.NONE;
        rushPhase = 0;
        t = 0;
        struck.clear();
    }

    // --- Phase two, the leap, the spin, the pools ---

    /** Half its health gone: it roars, throws everyone back and fights faster from here on. */
    private void enrage(ServerLevel level) {
        enraged = true;
        getNavigation().stop();
        Anim.playOn(this, "finger_bearer_roar");
        sound(level, SoundEvents.WARDEN_ROAR, 2f, 0.55f);
        fx(level, "fb_roar", getEyePosition(), getLookAngle(), 1.6f);
        Vec3 c = position().add(0, 1, 0);
        struck.clear();
        for (LivingEntity e : areaTargets(level, HitShape.sphere(c, 5.5), c)) {
            strike(e, Hit.builder(this, "fb_enrage").type(ModDamageTypes.TECHNIQUE).damage(dmg(3f)).tag(AttackTag.TECHNIQUE, AttackTag.AREA)
                    .knockback(Knockback.radial(c, 1.6, 0.5)).hitstun(6).origin(c).fx("fb_impact", 1f));
        }
        struck.clear();
        Fx.shake(level, c, 24, 0.9f, 14);
        rest = Math.max(rest, ROAR_TICKS + 10);
        for (int i = 0; i < cooldown.length; i++) cooldown[i] = Math.min(cooldown[i], 20);
    }

    /** A straight path for the leap (it won't jump through a ceiling). */
    private boolean leapClear(ServerLevel level, LivingEntity target) {
        Vec3 top = position().add(0, getBbHeight() + 3, 0);
        return level.clip(new ClipContext(position().add(0, getBbHeight(), 0), top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == Type.MISS;
    }

    private void tickLeap(ServerLevel level) {
        switch (leapPhase) {
            case 0 -> {
                turnToward(leapTo, 20f);
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                if (t >= LEAP_WINDUP) {
                    leapPhase = 1;
                    t = 0;
                    leapFrom = position();
                    setNoGravity(true);
                    sound(level, SoundEvents.RAVAGER_STEP, 1.6f, 0.5f);
                    fx(level, "fb_rush", position().add(0, 0.2, 0), flatDir(leapTo), 1.4f);
                }
            }
            case 1 -> {
                // A high arc onto the marked spot.
                float k = Math.min(1f, (t + 1) / (float) LEAP_AIR);
                Vec3 flat = leapFrom.lerp(leapTo, k);
                double span = Math.sqrt(leapFrom.distanceToSqr(leapTo.x, leapFrom.y, leapTo.z));
                double arc = Mth.clamp(span * 0.35, 1.2, 4.5) * Math.sin(Math.PI * k);
                double y = Mth.lerp(k, leapFrom.y, leapTo.y);
                faceDir(flatDir(leapTo));
                // Driven along the arc (not left to momentum). Under a low ceiling the arc flattens; only a wall ends it early.
                Vec3 next = new Vec3(flat.x, y + arc, flat.z);
                if (!level.noCollision(this, getBoundingBox().move(next.subtract(position())))) next = new Vec3(flat.x, Math.max(y, getY()), flat.z);
                Vec3 step = next.subtract(position());
                if (level.noCollision(this, getBoundingBox().move(step))) {
                    setPos(next);
                    setDeltaMovement(Vec3.ZERO);
                } else {
                    land(level);
                    return;
                }
                if (t >= LEAP_AIR) land(level);
            }
            default -> {
                // Landed hard: open for a moment.
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (t == 3) fx(level, "fb_stagger", getEyePosition(), Vec3.ZERO, 1f);
                if (t >= LEAP_RECOVER) end(COOLDOWN[Move.LEAP.ordinal()], 16);
            }
        }
    }

    private void land(ServerLevel level) {
        setNoGravity(false);
        setDeltaMovement(Vec3.ZERO);
        leapPhase = 2;
        t = 0;
        Vec3 c = position().add(0, 0.6, 0);
        for (LivingEntity e : areaTargets(level, HitShape.sphere(c, LEAP_RADIUS), c)) {
            strike(e, Hit.builder(this, "fb_leap").damage(dmg(LEAP_DAMAGE)).tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.AREA)
                    .knockback(Knockback.radial(c, 1.1, 0.7)).hitstun(12).guardDamage(4).origin(c).fx("fb_impact", 1.6f));
        }
        dev.rick.jjk.util.Destruction.sphere(level, position(), 2.4, 0.9f, 30, this, null);
        Fx.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6f, 0.6f);
        Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 1f, 0.7f);
        fx(level, "fb_smash", position().add(0, 0.05, 0), Vec3.ZERO, (float) LEAP_RADIUS);
        Fx.shake(level, c, 22, 1f, 12);
        Anim.playOn(this, "finger_bearer_recover");
    }

    private void tickSpin(ServerLevel level, @Nullable LivingEntity target) {
        // It wheels round on whoever is at its back.
        if (target != null && t < SPIN_HIT) turnToward(target.position(), 30f);
        if (t == SPIN_HIT) {
            Vec3 c = position().add(0, 1.4, 0);
            for (LivingEntity e : areaTargets(level, HitShape.sphere(c, SPIN_RADIUS), c)) {
                strike(e, Hit.builder(this, "fb_spin").damage(dmg(SPIN_DAMAGE)).tag(AttackTag.MELEE, AttackTag.AREA)
                        .knockback(Knockback.radial(c, 1.3, 0.4)).hitstun(10).guardDamage(3).origin(c).fx("fb_impact", 1.2f));
            }
            sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            fx(level, "fb_burst", position().add(0, 0.1, 0), Vec3.ZERO, (float) SPIN_RADIUS);
            behindTicks = 0;
        }
        if (t >= SPIN_END) end(COOLDOWN[Move.SPIN.ordinal()], 12);
    }

    private void tickPool(ServerLevel level, @Nullable LivingEntity target) {
        if (target != null && t < POOL_FIRE - 4) turnToward(target.position(), 12f);
        if (t == POOL_FIRE) {
            Vec3 at = target != null ? target.position() : position().add(forward().scale(6));
            Vec3 side = new Vec3(-forward().z, 0, forward().x).scale(3.5);
            leavePool(at, POOL_RADIUS + 0.4, POOL_TICKS);
            leavePool(at.add(side), POOL_RADIUS, POOL_TICKS);
            leavePool(at.subtract(side), POOL_RADIUS, POOL_TICKS);
            Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
            Fx.shake(level, position(), 14, 0.5f, 8);
        }
        if (t >= POOL_END) end(COOLDOWN[Move.POOL.ordinal()], 18);
    }

    /** Leaves a pool of cursed energy on the floor under {@code at}: standing in it hurts and slows. */
    public void leavePool(Vec3 at, double radius, int ticks) {
        if (!(level() instanceof ServerLevel level)) return;
        BlockPos b = BlockPos.containing(at);
        for (int i = 0; i < 4 && level.getBlockState(b.below()).isAir(); i++) b = b.below();
        Vec3 c = new Vec3(at.x, b.getY() + 0.05, at.z);
        if (pools.size() >= 6) pools.removeFirst();
        pools.add(new Pool(c, radius, level.getGameTime() + ticks));
        fx(level, "curse_pool", c, new Vec3(ticks, 0, 0), (float) radius);
    }

    private void tickPools(ServerLevel level) {
        if (pools.isEmpty()) return;
        long now = level.getGameTime();
        pools.removeIf(p -> p.until() <= now);
        if (now % 10 != 0) return;
        for (Pool p : pools) {
            for (LivingEntity e : HitboxQuery.query(level, HitShape.sphere(p.center(), p.radius()), 0.0, e -> Targeting.canTarget(this, e))) {
                if (Math.abs(e.getY() - p.center().y) > 1.2) continue;
                HitResolver.resolve(Hit.builder(this, "fb_pool").type(ModDamageTypes.TECHNIQUE).damage(dmg(POOL_DAMAGE)).tag(AttackTag.TECHNIQUE, AttackTag.AREA)
                        .knockback(Knockback.NONE).hitstun(0).origin(p.center()).noComboScaling().build(), e);
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 25, 1, false, true));
            }
        }
    }

    // --- Facing ---

    private Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /**
     * Where the thrusting palm is at the release (the model's right_hand bone, which the renderer's turn puts on the
     * entity's left), where shots leave from.
     */
    private Vec3 handPos() {
        Vec3 f = forward();
        Vec3 left = new Vec3(f.z, 0, -f.x);
        return position().add(f.scale(1.5)).add(left.scale(0.35)).add(0, 2.3, 0);
    }

    private Vec3 flatDir(Vec3 to) {
        Vec3 d = new Vec3(to.x - getX(), 0, to.z - getZ());
        return d.lengthSqr() < 1e-6 ? forward() : d.normalize();
    }

    /** Straight at their chest as they stand now: no leading, so moving sideways beats it. */
    private static Vec3 aimAt(LivingEntity target, Vec3 from) {
        Vec3 d = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(from);
        return d.lengthSqr() < 1e-6 ? Vec3.ZERO : d.normalize();
    }

    /** Turns toward a point at most {@code maxStep} degrees this tick (no snapping). */
    private void turnToward(Vec3 at, float maxStep) {
        float want = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90f;
        setFacing(Mth.approachDegrees(getYRot(), want, maxStep));
    }

    private void faceDir(Vec3 dir) {
        setFacing((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f);
    }

    private void setFacing(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // --- Effects ---

    private void sound(ServerLevel level, SoundEvent s, float volume, float pitch) {
        Fx.sound(level, position().add(0, 2, 0), s, volume, pitch);
    }

    /** Its effects carry its id, so a client that can't perceive it doesn't draw them either. */
    private void fx(ServerLevel level, String id, Vec3 at, Vec3 dir, float scale) {
        Fx.play(level, id, at, dir, scale, getId());
    }

    // --- Being hit, dying ---

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        // Flinches only between moves: its attacks are committed (heavy, unstable, but not interruptible by a poke).
        if (hurt && isAlive() && move == Move.NONE && getRandom().nextInt(3) == 0) Anim.playOn(this, "finger_bearer_hurt");
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (entityData.get(MOVE) != (byte) move.ordinal()) entityData.set(MOVE, (byte) move.ordinal());
        // Stagger resistance: the mod's hitstun stops it thinking, but never for long and never mid-move.
        if (Combat.has(this, CombatStatus.HITSTUN)) {
            sinceStun = 0;
            if (move != Move.NONE || ++stunTicks > 24) Combat.state(this).remove(CombatStatus.HITSTUN);
        } else if (++sinceStun > 40) {
            stunTicks = 0;
        }
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead || isRemoved();
        super.die(source);
        if (wasDead || !(level() instanceof ServerLevel level)) return;
        cancelMove();
        getNavigation().stop();
        Anim.playOn(this, "finger_bearer_death");
        sound(level, SoundEvents.WARDEN_DEATH, 1.5f, 0.7f);
        fx(level, "fb_death", position().add(0, 1.6, 0), Vec3.ZERO, 1f);
        FingerBearerEncounter.defeated(level, this);
    }

    /** Called once by the encounter when its room is cleared: the room's one Cursed Finger. */
    void markRewarded() {
        rewarded = true;
    }

    /** The death clip plays out (two seconds) before the body dissolves into cursed energy. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 44 && level() instanceof ServerLevel level && !isRemoved()) {
            Fx.play(level, "fb_dissolve", position().add(0, 1, 0), Vec3.ZERO, 1f, -1);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    // --- Collision: a heavy body that nothing shoves, and that doesn't shove those who can't perceive it ---

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {
        if (other instanceof LivingEntity l && !Targeting.canTarget(this, l)) return;
        super.doPush(other);
    }

    // --- Persistence: it belongs to its room and never despawns (the room respawns it if it is ever lost) ---

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Not even on Peaceful: a progression encounter isn't a spawned monster.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (home != null) out.store("JjkHome", BlockPos.CODEC, home);
        out.putInt("JjkHomeRadius", homeRadius);
        out.putBoolean("JjkRewarded", rewarded);
        out.putBoolean("JjkEnraged", enraged);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        home = in.read("JjkHome", BlockPos.CODEC).orElse(null);
        homeRadius = in.getIntOr("JjkHomeRadius", 9);
        rewarded = in.getBooleanOr("JjkRewarded", false);
        enraged = in.getBooleanOr("JjkEnraged", false);
    }
}
