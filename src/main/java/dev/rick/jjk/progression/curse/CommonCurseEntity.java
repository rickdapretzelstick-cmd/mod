package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.progression.CurseAggro;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.grade.GradedCurse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The common curses' shared body: a graded cursed spirit (seen only by those who perceive curses, hurt only by cursed
 * means) with a small server-side action machine. Each kind (fly head, crawler, maw) is its own subclass with its own
 * combat identity; this class gives them what they all need:
 * <ul>
 *   <li><b>A grade</b> ({@link CurseGrade}), saved per entity: it scales health, damage and how quickly the curse
 *   attacks again ({@link #rest(int)}), so the same kind can appear weaker or stronger.</li>
 *   <li><b>A leash</b>: an incident site or a realm arena it never leaves ({@link #setHome}).</li>
 *   <li><b>Fair targeting</b>: like the Finger Bearer, it only picks someone who perceives it (or already fights it),
 *   and every hit goes through {@link Targeting}.</li>
 *   <li><b>An incident id</b> ({@link GradedCurse#incidentId()}): the investigation it belongs to, for the rewards and the
 *   investigation's completion.</li>
 *   <li><b>Clips</b>: {@code <kind>_attack/_hurt/_death} are played from the server; idle and move run on the client.</li>
 * </ul>
 *
 * <p>And the rules that make fighting one feel fair and worth getting good at:
 * <ul>
 *   <li><b>Readable wind-ups</b>: while a kind is winding up ({@link #windingUp(int)}) the client sees it glow a pulsing
 *   warning red; lunges and slams also mark their path on the floor ({@link #warnLine}, {@link #warnRing}).</li>
 *   <li><b>Hits that land</b>: a hit freezes it for a few ticks (hitstop) and shakes the attacker's view a touch; enough
 *   damage in a short time breaks its <b>poise</b> and <b>staggers</b> it, whatever it was doing (unless that move is
 *   armoured, {@link #armored(int)}).</li>
 *   <li><b>Punish windows</b>: a whiffed attack or a stagger leaves it <b>exposed</b> ({@link #expose}): it flashes,
 *   takes {@link #exposedTaken()} times the damage, and the first hit into the opening rings out and pays the attacker
 *   Awakening meter.</li>
 *   <li><b>Taking turns</b>: at most {@link #MAX_ATTACKERS} curses commit to an attack on the same target at once
 *   ({@link #mayCommit}); the rest keep their distance and menace.</li>
 *   <li>It only regains health after {@link #CALM_TICKS} with nothing to fight and no hits (no healing up behind a wall
 *   mid-fight).</li>
 * </ul>
 */
public abstract class CommonCurseEntity extends Monster implements GradedCurse {
    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(CommonCurseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> GRADE = SynchedEntityData.defineId(CommonCurseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(CommonCurseEntity.class, EntityDataSerializers.BYTE);
    public static final int FLAG_EXPOSED = 1, FLAG_WINDUP = 2, FLAG_FROZEN = 4, FLAG_ENRAGED = 8, FLAG_STAGGERED = 16, FLAG_DORMANT = 32;
    /** The shared stagger action (no kind uses this id for its own). */
    public static final int STAGGERED = 100;
    public static final int STAGGER_TICKS = 34, POISE_REGEN_DELAY = 50;
    /** How many curses may commit to attacking one target at the same moment. */
    public static final int MAX_ATTACKERS = 2;
    public static final float EXPOSED_TAKEN = 1.5f;
    /** How long it must go without a target or a hit before it mends. */
    public static final int CALM_TICKS = 200;

    private CurseGrade grade;
    private String incident = "";
    @Nullable private BlockPos home;
    private int homeRadius = 16;
    /** Ticks until it may start another action. */
    protected int restTicks;
    /** Ticks into the action under way (0 = none). */
    protected int actionTicks;
    protected int action;
    protected final Set<UUID> struck = new HashSet<>();
    private int unseen;
    private int repath;
    private boolean graded;
    private float poise = -1;
    private int sinceHit = 999;
    /** Ticks left exposed (taking extra damage), and whether the opening has been punished yet. */
    private int exposedTicks;
    private boolean punished;
    /** Hitstop: ticks its thinking is held after a hit. */
    private int freezeTicks;
    private int lastFreeze = -100;
    private int calm;
    /** Held back for a later wave: not there yet (unseen, untouchable, still). */
    private boolean dormant;
    /** Health multiplier for the players fighting it (multiplayer scaling; 1 alone). */
    private double playerScale = 1.0;
    /** Its kind's own movement speed, before the grade's pursuit bonus. */
    private double baseSpeed = -1;
    /** Ticks after a stagger in which it can't be staggered again. */
    private int staggerImmune;
    /** When it last used a gap closer (pounce, lunge, leap, flicker). */
    private int lastGapAt = -10000;
    /** Where it last saw its target, and how much longer it keeps hunting there after losing it. */
    @Nullable private Vec3 lastSeen;
    private int searchFor;
    /** Chasing without getting anywhere: where it was, and for how many checks. */
    @Nullable private Vec3 chaseAnchor;
    private int chaseStuck;
    /** Ticks until it may attack, after being brought back into the fight. */
    private int returnDelay;

    protected CommonCurseEntity(EntityType<? extends CommonCurseEntity> type, Level level, CurseGrade grade) {
        super(type, level);
        this.grade = grade;
        this.xpReward = 3 + grade.rank() * 4;
    }

    // --- Identity ---

    @Override
    public CurseGrade curseGrade() {
        return level().isClientSide() ? CurseGrade.byRank(entityData.get(GRADE)) : grade;
    }

    @Override
    public String incidentId() {
        return incident;
    }

    /** Whose clips it plays ({@code <prefix>_attack} and so on): its own kind, unless it borrows another's model. */
    protected String clipPrefix() {
        return curseKind();
    }

    /** The kind's base max health (before the grade). */
    protected abstract double baseHealth();

    /** The kind's base hit (before the grade). */
    protected abstract float baseDamage();

    private static dev.rick.jjk.config.JJKConfig.Curses tuning() {
        return dev.rick.jjk.config.JJKConfig.get().curses;
    }

    /** Its max health at its grade, tuning and player scaling. */
    private double gradedHealth(CurseGrade g) {
        var t = tuning();
        return baseHealth() * g.health * t.at(t.health, g.rank()) * playerScale;
    }

    /** Sets the grade and rescales health to it (full health). */
    public void setGrade(CurseGrade g) {
        grade = g;
        entityData.set(GRADE, (byte) g.rank());
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(gradedHealth(g));
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (baseSpeed < 0) baseSpeed = speed.getBaseValue();
            speed.setBaseValue(baseSpeed * tuning().at(tuning().speed, g.rank()));
        }
        setHealth(getMaxHealth());
        xpReward = 3 + g.rank() * 4;
        graded = true;
    }

    /**
     * Scales it for the number of players fighting it (1 alone): its max health changes, the share of health it has
     * left doesn't (a second player arriving mid-fight doesn't heal it).
     */
    public void setPlayerScale(double scale) {
        if (Math.abs(scale - playerScale) < 1e-3) return;
        float frac = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1f;
        playerScale = scale;
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(gradedHealth(grade));
        setHealth(Math.max(1f, getMaxHealth() * frac));
    }

    public double playerScale() {
        return playerScale;
    }

    /** Ties it to an investigation and a place: it stays within {@code radius} of {@code at}, and doesn't despawn. */
    public void bind(String incidentId, @Nullable BlockPos at, int radius) {
        incident = incidentId == null ? "" : incidentId;
        home = at == null ? null : at.immutable();
        homeRadius = radius;
        setPersistenceRequired();
    }

    public void setHome(@Nullable BlockPos at, int radius) {
        home = at == null ? null : at.immutable();
        homeRadius = radius;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Damage of one of its hits at its grade. */
    protected float dmg(float scale) {
        return (float) (baseDamage() * scale * grade.damage * tuning().at(tuning().damage, grade.rank()));
    }

    /** A rest between actions, shortened by the grade's aggression. */
    protected void rest(int ticks) {
        double aggression = grade.aggression * tuning().at(tuning().aggression, grade.rank());
        restTicks = Math.max(restTicks, (int) Math.round(ticks / aggression) + getRandom().nextInt(8));
    }

    public int action() {
        return level().isClientSide() ? entityData.get(ACTION) : action;
    }

    // --- Fight rules shared by every kind ---

    /** Whether {@code action} is a wind-up: the beat its prey should read and react to (drawn glowing). */
    protected boolean windingUp(int action) {
        return false;
    }

    /** Whether {@code action} commits it to an attack (counts against {@link #MAX_ATTACKERS}); recoveries don't. */
    protected boolean committing(int action) {
        return action != 0 && action != STAGGERED;
    }

    /** Whether {@code action} can't be broken by a stagger (the poise still drops; it staggers once it's done). */
    protected boolean armored(int action) {
        return false;
    }

    /** How much damage in a short time breaks its poise. */
    protected float maxPoise() {
        CurseGrade g = curseGrade();
        return (float) (baseHealth() * g.health * 0.4 * tuning().at(tuning().poise, g.rank()));
    }

    /** Damage taken while exposed or staggered, as a multiple. */
    protected float exposedTaken() {
        return EXPOSED_TAKEN;
    }

    /** Whether it's enraged (drawn with a darker, angrier glow); kinds decide when. */
    protected boolean enraged() {
        return false;
    }

    private int flags() {
        if (level().isClientSide()) return entityData.get(FLAGS);
        int f = 0;
        if (exposedTicks > 0) f |= FLAG_EXPOSED;
        if (action != 0 && windingUp(action)) f |= FLAG_WINDUP;
        if (freezeTicks > 0) f |= FLAG_FROZEN;
        if (enraged()) f |= FLAG_ENRAGED;
        if (action == STAGGERED) f |= FLAG_STAGGERED;
        if (dormant) f |= FLAG_DORMANT;
        return f;
    }

    public boolean isExposed() {
        return (flags() & FLAG_EXPOSED) != 0;
    }

    public boolean isWindingUp() {
        return (flags() & FLAG_WINDUP) != 0;
    }

    public boolean isFrozen() {
        return (flags() & FLAG_FROZEN) != 0;
    }

    public boolean isEnraged() {
        return (flags() & FLAG_ENRAGED) != 0;
    }

    public boolean isStaggered() {
        return (flags() & FLAG_STAGGERED) != 0;
    }

    /** Test hooks: its rest before it may act again. */
    public int restTicksForTest() {
        return restTicks;
    }

    public void setRestForTest(int ticks) {
        restTicks = ticks;
    }

    public boolean dormant() {
        return (flags() & FLAG_DORMANT) != 0;
    }

    /** Holds it back for a later wave of its incident. */
    public void setDormant(boolean d) {
        dormant = d;
        setInvisible(d);
        if (!level().isClientSide()) syncFlags();
    }

    /** Brings a held-back curse in: it manifests where it waited. */
    public void manifest(ServerLevel level) {
        if (!dormant) return;
        setDormant(false);
        restTicks = Math.max(restTicks, 20);
        fx(level, "fb_manifest", position().add(0, getBbHeight() * 0.5, 0), Vec3.ZERO, 0.6f);
        sound(level, net.minecraft.sounds.SoundEvents.WARDEN_EMERGE, 0.5f, 1.6f);
    }

    public boolean committing() {
        return committing(action);
    }

    /** Its poise broke during an armoured move (it staggers as soon as that move lets it). */
    protected boolean poiseBroken() {
        return poise == 0;
    }

    public float poise() {
        return poise < 0 ? maxPoise() : poise;
    }

    /** Opens it up for {@code ticks}: it takes {@link #exposedTaken()} times the damage, and glows so you know. */
    protected void expose(ServerLevel level, int ticks) {
        if (exposedTicks <= 0) punished = false;
        exposedTicks = Math.max(exposedTicks, ticks);
        fx(level, "curse_exposed", position().add(0, getBbHeight() * 0.6, 0), Vec3.ZERO, Math.max(0.6f, getBbWidth()));
        sound(level, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.35f, 1.9f);
    }

    /**
     * Whether it may commit to an attack on {@code target} now: fewer than {@link #MAX_ATTACKERS} others (of any kind)
     * are already winding up or attacking the same target.
     */
    protected boolean mayCommit(ServerLevel level, LivingEntity target) {
        int n = 0;
        for (CommonCurseEntity o : level.getEntitiesOfClass(CommonCurseEntity.class, getBoundingBox().inflate(24),
                e -> e != this && e.isAlive() && e.getTarget() == target)) {
            if (o.committing() && ++n >= MAX_ATTACKERS) return false;
        }
        return true;
    }

    /**
     * Whether a gap closer (pounce, lunge, leap, flicker) is ready: never two back to back, however aggressive the grade,
     * so its prey always gets a moment to recover and answer between them.
     */
    protected boolean gapReady() {
        double aggression = grade.aggression * tuning().at(tuning().aggression, grade.rank());
        return tickCount - lastGapAt >= tuning().gapCloserCooldown / aggression;
    }

    /** A gap closer has started (see {@link #gapReady()}). */
    protected void gapUsed() {
        lastGapAt = tickCount;
    }

    /**
     * Brought back into the fight by its encounter (it was stuck somewhere it couldn't get out of): whatever it was doing
     * is dropped, and it waits {@code delay} ticks before it may attack, so its return can't be an unavoidable hit.
     */
    public void returnedToFight(int delay) {
        if (action != STAGGERED) {
            action = 0;
            actionTicks = 0;
            struck.clear();
        }
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        returnDelay = Math.max(returnDelay, delay);
        restTicks = Math.max(restTicks, delay);
        chaseAnchor = null;
        chaseStuck = 0;
    }

    /** The floor marking of a straight attack, from its feet along {@code path}, for {@code ticks}. */
    protected void warnLine(ServerLevel level, Vec3 path, int ticks) {
        fx(level, "curse_warn_line", position().add(0, 0.05, 0), path, ticks);
    }

    /** The floor marking of an area attack around {@code at}, of {@code radius}, for {@code ticks}. */
    protected void warnRing(ServerLevel level, Vec3 at, float radius, int ticks) {
        fx(level, "fb_smash_warn", at, new Vec3(ticks, 0, 0), radius);
    }

    /** Breaks off whatever it was doing and reels for {@link #STAGGER_TICKS}, wide open. */
    protected void stagger(ServerLevel level, @Nullable Entity by) {
        action = STAGGERED;
        actionTicks = 0;
        struck.clear();
        getNavigation().stop();
        poise = maxPoise();
        Anim.playOn(this, clipPrefix() + "_stagger");
        if (by != null) {
            Vec3 away = flatDir(by.position()).scale(-1);
            setDeltaMovement(away.x * 0.45, Math.max(getDeltaMovement().y, 0.18), away.z * 0.45);
        }
        expose(level, STAGGER_TICKS);
        fx(level, "fb_stagger", position().add(0, getBbHeight() * 0.8, 0), Vec3.ZERO, 1f);
        sound(level, dev.rick.jjk.registry.ModSounds.get("guard_break"), 0.7f, 1.3f);
        onStaggered(level);
    }

    /** Staggered from outside (a counter after a perfect dodge: progression.finish.Finishers). */
    public void staggerBy(ServerLevel level, @Nullable Entity by) {
        if (isAlive()) stagger(level, by);
    }

    // --- Strength contests (progression.contest.StrengthContests) ---

    /** Ticks it is held in place by a strength contest (its telegraph, then the contest itself). */
    private int contestHold;

    /** Holds it still (no actions, no movement) for {@code ticks}; renewed while a contest lasts. */
    public void holdForContest(int ticks) {
        contestHold = Math.max(contestHold, ticks);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
    }

    public void releaseFromContest() {
        contestHold = 0;
    }

    /** Free to start a grab: awake, not mid-action, not resting, not held. */
    public boolean readyToGrab() {
        return !dormant && action == 0 && restTicks <= 0 && contestHold <= 0 && freezeTicks <= 0;
    }

    /** It lost a strength contest: thrown off balance and wide open, as a stagger. */
    public void lostContest(ServerLevel level, @Nullable Entity by) {
        contestHold = 0;
        stagger(level, by);
    }

    /** It won a strength contest: it rests a moment before it acts again (no free follow-up). */
    public void wonContest(int rest) {
        contestHold = 0;
        restTicks = Math.max(restTicks, rest);
    }

    /** It was just staggered (kinds tidy up their own state). */
    protected void onStaggered(ServerLevel level) {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ACTION, (byte) 0);
        b.define(GRADE, (byte) 0);
        b.define(FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        // The action machine in customServerAiStep runs everything.
    }

    // --- The fight ---

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!graded) setGrade(grade);
        if (dormant) {
            setDeltaMovement(Vec3.ZERO);
            syncFlags();
            return;
        }
        if (exposedTicks > 0) exposedTicks--;
        if (++sinceHit > POISE_REGEN_DELAY && poise >= 0 && poise < maxPoise()) poise = Math.min(maxPoise(), poise + maxPoise() / 40f);
        if (contestHold > 0) {
            // Locked in a strength contest (or its telegraph): nothing else happens.
            contestHold--;
            getNavigation().stop();
            setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
            syncFlags();
            return;
        }
        if (freezeTicks > 0) {
            // Hitstop: held in the moment of the hit (its timers wait too).
            freezeTicks--;
            syncFlags();
            return;
        }
        if (restTicks > 0) restTicks--;
        if (staggerImmune > 0) staggerImmune--;
        if (returnDelay > 0) returnDelay--;
        LivingEntity target = validTarget(level);
        calm = target != null || sinceHit < 2 ? 0 : calm + 1;
        if (action == STAGGERED) {
            actionTicks++;
            setDeltaMovement(getDeltaMovement().multiply(0.7, 1, 0.7));
            if (actionTicks >= STAGGER_TICKS) {
                finishAction(12);
                staggerImmune = tuning().staggerImmunityTicks;
            }
        } else if (action != 0) {
            actionTicks++;
            tickAction(level, target);
        } else if (target == null) {
            if (searchFor > 0 && lastSeen != null) {
                // Lost sight of its prey: it hunts where it saw them last before giving up.
                searchFor--;
                if (position().distanceToSqr(lastSeen) < 2.25) searchFor = Math.min(searchFor, 20);
                walkTo(lastSeen, 1.1);
            } else {
                wander(level);
            }
        } else if (!Combat.actionsLocked(this)) {
            if (returnDelay > 0) {
                // Just brought back: it finds its footing and faces them before it may attack.
                turnToward(target.position(), 10f);
                getNavigation().stop();
            } else {
                fight(level, target, distanceTo(target));
                unstick(target);
            }
        }
        if (entityData.get(ACTION) != (byte) action) entityData.set(ACTION, (byte) action);
        syncFlags();
    }

    private void syncFlags() {
        byte f = (byte) flags();
        if (entityData.get(FLAGS) != f) entityData.set(FLAGS, f);
    }

    /** No action under way and a target in reach: approach, position and start actions. */
    protected abstract void fight(ServerLevel level, LivingEntity target, double distance);

    /** The action under way (target may have been lost meanwhile). */
    protected abstract void tickAction(ServerLevel level, @Nullable LivingEntity target);

    /** Starts an action: its id, its clip (or null), and resets the per-action hit list. */
    protected void begin(int id, @Nullable String clip) {
        action = id;
        actionTicks = 0;
        struck.clear();
        getNavigation().stop();
        if (clip != null) Anim.playOn(this, clipPrefix() + "_" + clip);
    }

    protected void finishAction(int restAfter) {
        action = 0;
        actionTicks = 0;
        struck.clear();
        rest(restAfter);
        // Its poise broke during an armoured move: it reels now that the move is done.
        if (poise == 0 && level() instanceof ServerLevel level) stagger(level, null);
    }

    /** No one to fight: drift back toward home, or idle about. */
    protected void wander(ServerLevel level) {
        if (home == null) return;
        Vec3 h = Vec3.atBottomCenterOf(home);
        double d = position().distanceToSqr(h);
        if (d > (homeRadius + 24.0) * (homeRadius + 24.0)) {
            teleportTo(h.x, h.y + 1, h.z);
        } else if (d > homeRadius * homeRadius * 0.25 && (getNavigation().isDone() || tickCount % 60 == 0)) {
            getNavigation().moveTo(h.x, h.y, h.z, 0.8);
        }
        // It mends only once the fight is well over (breaking line of sight mid-fight doesn't buy it health).
        if (getHealth() < getMaxHealth() && tickCount % 40 == 0 && calm > CALM_TICKS) {
            heal(getMaxHealth() * 0.03f);
        }
    }

    /**
     * Chasing and getting nowhere (a ledge, a corner, a doorway it can't path through): it jumps and re-paths, and if
     * that doesn't help, tries another way round. Checked once a second while it isn't in the middle of an action.
     */
    private void unstick(LivingEntity target) {
        if (action != 0 || tickCount % 20 != 0) return;
        if (distanceTo(target) < 3.5 || isNoGravity()) {
            chaseAnchor = position();
            chaseStuck = 0;
            return;
        }
        if (chaseAnchor != null && position().distanceToSqr(chaseAnchor) < 0.36) {
            chaseStuck++;
            if (chaseStuck == 2) {
                if (onGround()) jumpFromGround();
                getNavigation().stop();
                repath = 0;
                getNavigation().moveTo(target, 1.2);
            } else if (chaseStuck >= 4) {
                // Another way round: a spot off to one side of the line to its prey.
                Vec3 to = flatDir(target.position());
                Vec3 side = new Vec3(-to.z, 0, to.x).scale(getRandom().nextBoolean() ? 4 : -4);
                Vec3 spot = position().add(side).add(to.scale(2));
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.2);
                repath = 30;
                chaseStuck = 0;
            }
        } else {
            chaseStuck = 0;
        }
        chaseAnchor = position();
    }

    @Nullable
    private LivingEntity validTarget(ServerLevel level) {
        LivingEntity t = getTarget();
        if (t != null) {
            if (!stillValid(level, t)) {
                setTarget(null);
                t = null;
            } else if (hasLineOfSight(t)) {
                unseen = 0;
                lastSeen = t.position();
                searchFor = 0;
            } else if (++unseen > 120) {
                setTarget(null);
                t = null;
                if (lastSeen != null) searchFor = tuning().searchTicks;
            }
        }
        if (t == null && tickCount % 10 == 0) t = acquire(level);
        return t;
    }

    protected boolean stillValid(ServerLevel level, LivingEntity e) {
        if (!e.isAlive() || e.isRemoved() || e.level() != level || e.isSpectator()) return false;
        if (e instanceof ServerPlayer p && p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        if (!Targeting.canTarget(this, e)) return false;
        if (distanceToSqr(e) > 32 * 32) return false;
        return home == null || e.position().distanceToSqr(Vec3.atBottomCenterOf(home)) <= (homeRadius + 8.0) * (homeRadius + 8.0);
    }

    @Override
    @Nullable
    protected LivingEntity asValidTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return null;
        if (target instanceof ServerPlayer p && (p.isSpectator() || p.gameMode.getGameModeForPlayer() == GameType.CREATIVE)) return null;
        return target;
    }

    @Nullable
    private LivingEntity acquire(ServerLevel level) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double d = distanceToSqr(p);
            if (d > 18 * 18 || d >= bestD) continue;
            if (!stillValid(level, p) || !CurseAggro.mayTarget(this, p) || !hasLineOfSight(p)) continue;
            best = p;
            bestD = d;
        }
        if (best == null) return null;
        setTarget(best);
        return getTarget() == best ? best : null;
    }

    /** Walks toward {@code at}, re-pathing every half second. */
    protected void walkTo(Vec3 at, double speed) {
        if (--repath <= 0 || getNavigation().isDone()) {
            repath = 10;
            getNavigation().moveTo(at.x, at.y, at.z, speed);
        }
    }

    /** Lands one hit, once per target per action (never through a wall). */
    @Nullable
    protected HitResult strike(LivingEntity target, Hit.Builder hit) {
        if (struck.contains(target.getUUID()) || !Targeting.canTarget(this, target)) return null;
        Hit h = hit.noComboScaling().build();
        if (tuning().blockedByWalls && !reachable(h.origin, target)) return null;
        struck.add(target.getUUID());
        return HitResolver.resolve(h, target);
    }

    /** Whether a blow from {@code origin} can reach {@code target}: nothing solid between them. */
    private boolean reachable(Vec3 origin, LivingEntity target) {
        Vec3 from = origin.distanceToSqr(position()) < 1e-4 ? getEyePosition() : origin;
        return dev.rick.jjk.core.hitbox.HitboxQuery.hasLineOfSight(level(), from, target) || hasLineOfSight(target);
    }

    // --- Facing ---

    protected Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    protected Vec3 flatDir(Vec3 to) {
        Vec3 d = new Vec3(to.x - getX(), 0, to.z - getZ());
        return d.lengthSqr() < 1e-6 ? forward() : d.normalize();
    }

    protected void turnToward(Vec3 at, float maxStep) {
        float want = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), want, maxStep);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // --- Effects ---

    protected void sound(ServerLevel level, SoundEvent s, float volume, float pitch) {
        Fx.sound(level, position().add(0, getBbHeight() * 0.6, 0), s, volume, pitch);
    }

    /** Its effects carry its id: a client that can't perceive it doesn't draw them. */
    protected void fx(ServerLevel level, String id, Vec3 at, Vec3 dir, float scale) {
        Fx.play(level, id, at, dir, scale, getId());
    }

    // --- Being hit, dying ---

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (dormant && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        boolean open = exposedTicks > 0;
        float dealt = open ? damage * exposedTaken() : damage;
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, dealt);
        if (hurt) {
            Entity by = source.getEntity();
            float lost = Math.max(0, before - getHealth());
            if (open && !punished && by instanceof LivingEntity attacker) {
                // Punished the opening: it rings out, and the attacker's meter is paid for reading it.
                punished = true;
                fx(level, "curse_punish", position().add(0, getBbHeight() * 0.6, 0), Vec3.ZERO, 1f);
                sound(level, dev.rick.jjk.registry.ModSounds.get("clash_perfect"), 0.8f, 1.2f);
                dev.rick.jjk.core.character.AwakeningGain.gain(attacker, PUNISH_METER);
            }
            if (isAlive()) {
                // Hitstop: a beat held on the impact, longer for a big hit; the attacker feels it too.
                // (Never back to back: a flurry doesn't hold it frozen.)
                if (tickCount - lastFreeze >= 8) {
                    freezeTicks = Math.max(freezeTicks, lost >= 6 || open ? 4 : 2);
                    lastFreeze = tickCount;
                }
                if (by instanceof ServerPlayer sp) {
                    Fx.camera(sp, dev.rick.jjk.core.net.CameraPayload.SHAKE, lost >= 6 || open ? 0.22f : 0.12f, 3, 0);
                }
                sinceHit = 0;
                if (poise < 0) poise = maxPoise();
                // Just out of a stagger: it can't be broken again at once (no stagger-locking it to death).
                poise = Math.max(staggerImmune > 0 ? 1 : 0, poise - lost);
                retaliate(level, by);
                if (poise <= 0 && action != STAGGERED && !armored(action)) {
                    stagger(level, by);
                } else if (action == 0) {
                    Anim.playOn(this, clipPrefix() + "_hurt");
                }
                onHurt(level, source);
            }
        }
        return hurt;
    }

    /**
     * Hit by someone it isn't fighting (from out of its sight, or while it chased someone else far off): it turns on
     * them. A curse never just stands there being hit.
     */
    private void retaliate(ServerLevel level, @Nullable Entity by) {
        if (!(by instanceof LivingEntity attacker) || attacker == this || attacker == getTarget()) return;
        if (attacker instanceof CommonCurseEntity || !stillValid(level, attacker)) return;
        LivingEntity t = getTarget();
        if (t == null || !hasLineOfSight(t) || distanceTo(t) > distanceTo(attacker) + 6) {
            setTarget(attacker);
            unseen = 0;
            lastSeen = attacker.position();
            searchFor = 0;
        }
    }

    /** Awakening meter paid for the first hit into an opening. */
    public static final float PUNISH_METER = 6f;

    /** A hit landed on it (for reactions: the fly heads scatter). */
    protected void onHurt(ServerLevel level, DamageSource source) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        // An incident's curse only exists in its realm arena: one found anywhere else (the overworld after a crash, an
        // arena that closed while its chunk was unloaded) is a leftover, and goes.
        if (!incident.isEmpty() && tickCount % 40 == 5 && orphaned()) {
            discard();
            return;
        }
        // The mod's hitstun stops it thinking for a moment, never long (a common curse is skittish, not helpless).
        if (Combat.has(this, CombatStatus.HITSTUN) && tickCount % 20 == 0) Combat.state(this).remove(CombatStatus.HITSTUN);
    }

    /**
     * Bound to an incident whose arena isn't open any more, or not one of the curses its fight raised (one left over
     * from an earlier attempt). One of the fight's own curses that has strayed out past the arena's edge is not an
     * orphan: the encounter brings it back ({@code EncounterWatch}).
     */
    private boolean orphaned() {
        var server = level().getServer();
        if (server == null || !dev.rick.jjk.progression.investigation.CursedRealms.inRealm(this)) return true;
        var st = dev.rick.jjk.progression.investigation.InvestigationState.get(server);
        if (dev.rick.jjk.progression.investigation.CursedRealms.arenaOf(st, incident) == null) return true;
        var in = st.incidents().get(incident);
        return in == null || !in.curses().contains(getUUID());
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead || isRemoved();
        super.die(source);
        if (wasDead || !(level() instanceof ServerLevel level)) return;
        action = 0;
        getNavigation().stop();
        Anim.playOn(this, clipPrefix() + "_death");
        fx(level, "fb_death", position().add(0, getBbHeight() * 0.5, 0), Vec3.ZERO, 0.5f);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 24 && level() instanceof ServerLevel level && !isRemoved()) {
            Fx.play(level, "fb_dissolve", position().add(0, 0.3, 0), Vec3.ZERO, 0.5f, -1);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void doPush(Entity other) {
        if (other instanceof LivingEntity l && !Targeting.canTarget(this, l)) return;
        super.doPush(other);
    }

    @Override
    public void playAmbientSound() {
        if (!dormant()) super.playAmbientSound();
    }

    @Override
    public boolean isPickable() {
        return !dormant() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !dormant() && super.isPushable();
    }

    @Override
    public boolean isNoGravity() {
        return dormant() || super.isNoGravity();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return incident.isEmpty() && super.removeWhenFarAway(distSqr);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("JjkGrade", grade.name());
        out.putString("JjkIncident", incident);
        if (home != null) out.store("JjkHome", BlockPos.CODEC, home);
        out.putInt("JjkHomeRadius", homeRadius);
        out.putBoolean("JjkDormant", dormant);
        out.putDouble("JjkPlayerScale", playerScale);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        try {
            grade = CurseGrade.valueOf(in.getStringOr("JjkGrade", grade.name()));
        } catch (IllegalArgumentException ignored) {
        }
        entityData.set(GRADE, (byte) grade.rank());
        graded = true;
        incident = in.getStringOr("JjkIncident", "");
        home = in.read("JjkHome", BlockPos.CODEC).orElse(null);
        homeRadius = in.getIntOr("JjkHomeRadius", 16);
        setDormant(in.getBooleanOr("JjkDormant", false));
        playerScale = in.getDoubleOr("JjkPlayerScale", 1.0);
    }
}
