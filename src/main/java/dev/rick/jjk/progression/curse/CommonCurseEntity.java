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
 */
public abstract class CommonCurseEntity extends Monster implements GradedCurse {
    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(CommonCurseEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> GRADE = SynchedEntityData.defineId(CommonCurseEntity.class, EntityDataSerializers.BYTE);

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

    /** Sets the grade and rescales health to it (full health). */
    public void setGrade(CurseGrade g) {
        grade = g;
        entityData.set(GRADE, (byte) g.rank());
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(baseHealth() * g.health);
        setHealth(getMaxHealth());
        xpReward = 3 + g.rank() * 4;
        graded = true;
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
        return baseDamage() * scale * grade.damage;
    }

    /** A rest between actions, shortened by the grade's aggression. */
    protected void rest(int ticks) {
        restTicks = Math.max(restTicks, Math.round(ticks / grade.aggression) + getRandom().nextInt(8));
    }

    public int action() {
        return level().isClientSide() ? entityData.get(ACTION) : action;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ACTION, (byte) 0);
        b.define(GRADE, (byte) 0);
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
        if (restTicks > 0) restTicks--;
        LivingEntity target = validTarget(level);
        if (action != 0) {
            actionTicks++;
            tickAction(level, target);
        } else if (target == null) {
            wander(level);
        } else if (!Combat.actionsLocked(this)) {
            fight(level, target, distanceTo(target));
        }
        if (entityData.get(ACTION) != (byte) action) entityData.set(ACTION, (byte) action);
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
        if (getHealth() < getMaxHealth() && tickCount % 40 == 0) heal(getMaxHealth() * 0.03f);
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
            } else if (++unseen > 120) {
                setTarget(null);
                t = null;
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

    /** Lands one hit, once per target per action. */
    @Nullable
    protected HitResult strike(LivingEntity target, Hit.Builder hit) {
        if (!struck.add(target.getUUID()) || !Targeting.canTarget(this, target)) return null;
        return HitResolver.resolve(hit.noComboScaling().build(), target);
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
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && isAlive()) {
            if (action == 0) Anim.playOn(this, clipPrefix() + "_hurt");
            onHurt(level, source);
        }
        return hurt;
    }

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

    /** Bound to an incident whose arena isn't here (or isn't open any more). */
    private boolean orphaned() {
        var arena = dev.rick.jjk.progression.investigation.CursedRealms.arenaHere(this);
        if (arena == null || !arena.incident.equals(incident) || level().getServer() == null) return true;
        // Its incident's arena, but not one of the curses this fight raised (one left over from an earlier attempt).
        var in = dev.rick.jjk.progression.investigation.InvestigationState.get(level().getServer()).incidents().get(incident);
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
    }
}
