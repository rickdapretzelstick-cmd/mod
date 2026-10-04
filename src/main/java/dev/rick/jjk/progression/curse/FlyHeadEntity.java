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
 *   <li><b>Scatter</b>: struck, it flees for a second before rejoining (they don't stay to be swatted).</li>
 * </ul>
 */
public class FlyHeadEntity extends CommonCurseEntity {
    static final int WINDUP = 1, DIVE = 2, SCATTER = 3;
    public static final int WINDUP_TICKS = 9, DIVE_TICKS = 14, SCATTER_TICKS = 22;
    private Vec3 diveDir = Vec3.ZERO;
    private Vec3 fleeDir = Vec3.ZERO;

    public FlyHeadEntity(EntityType<? extends FlyHeadEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_4);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, 1);
    }

    @Override
    public String curseKind() {
        return "fly_head";
    }

    @Override
    protected double baseHealth() {
        return 6;
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
        if (restTicks <= 0 && distance < 9 && hasLineOfSight(target) && noOtherDiving(level)) {
            begin(WINDUP, "attack");
            sound(level, SoundEvents.SILVERFISH_AMBIENT, 0.8f, 1.6f);
            fx(level, "curse_tell", position().add(forward().scale(0.3)).add(0, 0.4, 0), Vec3.ZERO, 0.5f);
        }
    }

    /** One diver at a time per swarm: the others keep circling (a readable rhythm instead of a blender). */
    private boolean noOtherDiving(ServerLevel level) {
        AABB box = getBoundingBox().inflate(10);
        for (FlyHeadEntity o : level.getEntitiesOfClass(FlyHeadEntity.class, box, e -> e != this && e.isAlive())) {
            if (o.action == WINDUP || o.action == DIVE) return false;
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
                if (actionTicks >= DIVE_TICKS || horizontalCollision || verticalCollision) retreat(new Vec3(-diveDir.x, 1, -diveDir.z));
            }
            case SCATTER -> {
                setDeltaMovement(getDeltaMovement().scale(0.7).add(fleeDir.scale(0.12)));
                if (actionTicks >= SCATTER_TICKS) finishAction(30);
            }
            default -> finishAction(10);
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
