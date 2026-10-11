package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.progression.grade.CurseGrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Fly Head (Grade 4): the weakest curse there is, never alone. A swarm of them circles its prey just out of reach, each
 * on its own erratic orbit, and one at a time they dive in to bite. Their danger is the number and the angle: one is a
 * nuisance, five from five directions keep a fighter turning.
 * <ul>
 *   <li><b>Orbit</b>: a wobbling circle around the target, a couple of blocks up, never still.</li>
 *   <li><b>Dive</b>: it stops in the air for a beat (its jaw opens: the tell), then darts at where the target was. A
 *   sidestep makes it overshoot; a hit is a small bite.</li>
 *   <li><b>Spit</b>: from its orbit it may instead puff up (the same glow) and spit a slow gob of bile at you: step
 *   out of its way. Grade 3 and up spit three in a fan.</li>
 *   <li><b>Scatter</b>: struck, it flees for a second before rejoining (they don't stay to be swatted).</li>
 *   <li><b>Swatted down</b>: hit it mid-dive, or let it dive into the floor, and it drops to the ground and flounders
 *   there, open, for two seconds.</li>
 * </ul>
 */
public class FlyHeadEntity extends CommonCurseEntity {
    static final int WINDUP = 1, DIVE = 2, SCATTER = 3, SPIT_WINDUP = 4, GROUNDED = 5;
    public static final int WINDUP_TICKS = 9, DIVE_TICKS = 14, SCATTER_TICKS = 22, SPIT_WINDUP_TICKS = 12, GROUNDED_TICKS = 40, SPIT_LIFE = 40;
    public static final double SPIT_SPEED = 0.42;
    private Vec3 diveDir = Vec3.ZERO;
    private Vec3 fleeDir = Vec3.ZERO;

    /** A gob of bile in flight (it outlives the action that spat it). */
    private static final class Spit {
        Vec3 pos, vel;
        int age;

        Spit(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
        }
    }

    private final java.util.List<Spit> spits = new java.util.ArrayList<>();

    public FlyHeadEntity(EntityType<? extends FlyHeadEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_4);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, 1);
    }

    @Override
    public String curseKind() {
        return "fly_head";
    }

    @Override
    protected double baseHealth() {
        return 10;
    }

    @Override
    protected boolean windingUp(int a) {
        return a == WINDUP || a == SPIT_WINDUP;
    }

    @Override
    protected boolean committing(int a) {
        return a == WINDUP || a == DIVE || a == SPIT_WINDUP;
    }

    /** It never staggers: it scatters, or is swatted down. */
    @Override
    protected float maxPoise() {
        return 1e6f;
    }

    public boolean grounded() {
        return action() == GROUNDED;
    }

    public int spitsInFlight() {
        return spits.size();
    }

    public boolean diving() {
        return action() == DIVE;
    }

    /** Test hooks. */
    public void forceSpitForTest(LivingEntity target) {
        setTarget(target);
        begin(SPIT_WINDUP, "attack");
    }

    public void forceDiveForTest(LivingEntity target) {
        setTarget(target);
        diveDir = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(position()).normalize();
        begin(DIVE, "attack");
    }

    @Override
    protected float baseDamage() {
        return 1.5f;
    }

    /** Where in the swarm this one circles (spread by id so a group never stacks). */
    private double phase() {
        return getId() * 2.399 + tickCount * 0.045 * (getId() % 2 == 0 ? 1 : -1);
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double distance) {
        // Orbit: a wobbling circle a few blocks out and up.
        double a = phase();
        double radius = 3.2 + 0.8 * Mth.sin((float) (tickCount * 0.11 + getId()));
        Vec3 want = target.position().add(Math.cos(a) * radius, 1.8 + 0.7 * Math.sin(tickCount * 0.17 + getId()), Math.sin(a) * radius);
        steer(want, 0.32);
        turnToward(target.position(), 20f);
        getLookControl().setLookAt(target);
        if (restTicks <= 0 && distance < 10 && hasLineOfSight(target) && noOtherDiving(level) && mayCommit(level, target)) {
            if (distance > 4 && getRandom().nextFloat() < 0.4f) {
                begin(SPIT_WINDUP, "attack");
                sound(level, SoundEvents.LLAMA_SPIT, 0.6f, 0.6f);
            } else {
                begin(WINDUP, "attack");
                sound(level, SoundEvents.SILVERFISH_AMBIENT, 0.8f, 1.6f);
            }
            fx(level, "curse_tell", position().add(forward().scale(0.3)).add(0, 0.4, 0), Vec3.ZERO, 0.5f);
        }
    }

    /** One diver at a time per swarm: the others keep circling (a readable rhythm instead of a blender). */
    private boolean noOtherDiving(ServerLevel level) {
        AABB box = getBoundingBox().inflate(10);
        for (FlyHeadEntity o : level.getEntitiesOfClass(FlyHeadEntity.class, box, e -> e != this && e.isAlive())) {
            if (o.action == WINDUP || o.action == DIVE || o.action == SPIT_WINDUP) return false;
        }
        return true;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case WINDUP -> {
                // Hangs in the air, tracking until the last moment.
                setDeltaMovement(getDeltaMovement().scale(0.6));
                if (target != null && actionTicks < WINDUP_TICKS - 2) {
                    turnToward(target.position(), 30f);
                    diveDir = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(position()).normalize();
                }
                if (actionTicks >= WINDUP_TICKS) {
                    action = DIVE;
                    actionTicks = 0;
                    sound(level, SoundEvents.PHANTOM_SWOOP, 0.7f, 1.8f);
                }
            }
            case DIVE -> {
                setDeltaMovement(diveDir.scale(0.75));
                AABB reach = getBoundingBox().inflate(0.35);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, e -> e != this && e.isAlive())) {
                    if (e instanceof CommonCurseEntity) continue;
                    var r = strike(e, Hit.builder(this, "fly_head_bite").damage(dmg(1f)).tag(AttackTag.MELEE)
                            .knockback(Knockback.directional(diveDir, 0.25, 0.05)).hitstun(4).origin(position()).fx("curse_bite", 0.5f));
                    if (r != null) {
                        sound(level, SoundEvents.PHANTOM_BITE, 0.8f, 1.6f);
                        retreat(diveDir.scale(-1).add(0, 0.8, 0));
                        return;
                    }
                }
                if (verticalCollision && diveDir.y < -0.2) {
                    // Dove into the floor: down and floundering.
                    swatDown(level);
                    return;
                }
                if (actionTicks >= DIVE_TICKS || horizontalCollision || verticalCollision) retreat(new Vec3(-diveDir.x, 1, -diveDir.z));
            }
            case SPIT_WINDUP -> {
                setDeltaMovement(getDeltaMovement().scale(0.6));
                if (target != null) turnToward(target.position(), 30f);
                if (actionTicks >= SPIT_WINDUP_TICKS) {
                    if (target != null) spitAt(level, target);
                    finishAction(34);
                }
            }
            case GROUNDED -> {
                setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                if (actionTicks >= GROUNDED_TICKS) {
                    setNoGravity(true);
                    setDeltaMovement(0, 0.35, 0);
                    finishAction(20);
                }
            }
            case SCATTER -> {
                setDeltaMovement(getDeltaMovement().scale(0.7).add(fleeDir.scale(0.12)));
                if (actionTicks >= SCATTER_TICKS) finishAction(30);
            }
            default -> finishAction(10);
        }
    }

    private void spitAt(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(forward().scale(0.3)).add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 dir = to.subtract(from).normalize();
        int n = curseGrade().rank() >= 1 ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double yaw = (i - (n - 1) / 2.0) * 0.32;
            double c = Math.cos(yaw), sn = Math.sin(yaw);
            Vec3 d = new Vec3(dir.x * c - dir.z * sn, dir.y, dir.x * sn + dir.z * c);
            spits.add(new Spit(from, d.scale(SPIT_SPEED)));
        }
        sound(level, SoundEvents.LLAMA_SPIT, 1f, 1.3f);
    }

    /** Knocked out of the air: on the floor, open. */
    private void swatDown(ServerLevel level) {
        action = GROUNDED;
        actionTicks = 0;
        struck.clear();
        setNoGravity(false);
        setDeltaMovement(getDeltaMovement().multiply(0.2, 0, 0.2).add(0, -0.4, 0));
        sound(level, SoundEvents.SILVERFISH_HURT, 1f, 0.7f);
        expose(level, GROUNDED_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || spits.isEmpty()) return;
        // The spits in flight: straight, a little drop, gone on whatever they touch.
        for (var it = spits.iterator(); it.hasNext(); ) {
            Spit sp = it.next();
            Vec3 next = sp.pos.add(sp.vel);
            sp.vel = sp.vel.add(0, -0.002, 0);
            boolean done = ++sp.age > SPIT_LIFE;
            var clip = level.clip(new net.minecraft.world.level.ClipContext(sp.pos, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                next = clip.getLocation();
                done = true;
            }
            AABB box = new AABB(sp.pos, next).inflate(0.35);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !(e instanceof CommonCurseEntity))) {
                struck.remove(e.getUUID());
                if (strike(e, Hit.builder(this, "fly_head_spit").damage(dmg(0.8f)).tag(AttackTag.PROJECTILE)
                        .knockback(Knockback.directional(sp.vel.normalize(), 0.2, 0.05)).hitstun(6).origin(sp.pos).fx("curse_spit_burst", 0.6f)) != null) {
                    done = true;
                    break;
                }
            }
            sp.pos = next;
            if (done) {
                fx(level, "curse_spit_burst", sp.pos, Vec3.ZERO, 0.6f);
                it.remove();
            } else {
                fx(level, "curse_spit", sp.pos, sp.vel, 1f);
            }
        }
    }

    private void retreat(Vec3 dir) {
        fleeDir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : dir.normalize();
        action = SCATTER;
        actionTicks = 0;
        struck.clear();
    }

    @Override
    protected void onHurt(ServerLevel level, DamageSource source) {
        if (action == GROUNDED) return;
        if (action == DIVE) {
            // Hit mid-dive: knocked out of the air.
            swatDown(level);
            return;
        }
        // Swatted: it scatters away from whoever hit it, then rejoins the swarm.
        Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : position();
        Vec3 away = position().subtract(from);
        retreat(new Vec3(away.x, 0.6, away.z));
        fx(level, "curse_scatter", position(), Vec3.ZERO, 1f);
    }

    @Override
    protected void wander(ServerLevel level) {
        // Idle: a lazy hover, drifting back toward its home.
        Vec3 h = home() == null ? position() : Vec3.atCenterOf(home()).add(0, 2, 0);
        double a = phase();
        steer(h.add(Math.cos(a) * 2.5, Math.sin(tickCount * 0.07 + getId()) * 0.6, Math.sin(a) * 2.5), 0.12);
    }

    /** Accelerates toward a point, capped (they flit: quick to change direction, never blindingly fast). */
    private void steer(Vec3 want, double max) {
        Vec3 d = want.subtract(position());
        double len = d.length();
        Vec3 v = len < 1e-4 ? Vec3.ZERO : d.scale(Math.min(max, len * 0.15) / len);
        setDeltaMovement(getDeltaMovement().scale(0.75).add(v.scale(0.4)));
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        setNoGravity(true);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP_AGGRESSIVE;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }
}
