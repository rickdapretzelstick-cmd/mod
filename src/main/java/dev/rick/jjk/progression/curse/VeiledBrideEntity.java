package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.grade.CurseGrade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Veiled Bride (Grade 3): a tall, thin figure in a rotted wedding veil, arms long enough to reach the floor. It is slow
 * on its feet and doesn't need to be quick.
 * <ul>
 *   <li><b>Flicker</b>: its veil lifts and shivers and it glows (the tell, most of a second), then it is gone and
 *   standing right behind its prey, and its arms close a beat later. Turn round or step away when it flickers. A grasp
 *   that closes on nothing leaves it standing there <b>exposed</b>.</li>
 *   <li><b>Wail</b>: it throws its head back (the tell, with the cone marked on the floor), then screams: everything in
 *   the cone in front of it is hurt and thrown back. Afterwards it hunches, catching its breath, <b>exposed</b>.</li>
 *   <li><b>Grasp</b>: crowd it and it sweeps its arms across in front of it.</li>
 * </ul>
 */
public class VeiledBrideEntity extends CommonCurseEntity {
    static final int FLICKER = 1, GRASP = 2, WAIL = 3, BREATH = 4, LINGER = 5;
    public static final int FLICKER_TICKS = 16, GRASP_HIT = 8, GRASP_END = 14, WAIL_HIT = 14, WAIL_END = 18, BREATH_TICKS = 30, LINGER_TICKS = 30;
    public static final double WAIL_RANGE = 6.5, WAIL_HALF_ANGLE = 35, BEHIND = 2.2;
    private boolean grasped;
    private Vec3 wailDir = Vec3.ZERO;

    public VeiledBrideEntity(EntityType<? extends VeiledBrideEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 28).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "veiled_bride";
    }

    @Override
    protected double baseHealth() {
        return 30;
    }

    @Override
    protected float baseDamage() {
        return 4.5f;
    }

    @Override
    protected boolean windingUp(int a) {
        return a == FLICKER || a == GRASP && actionTicks < GRASP_HIT || a == WAIL && actionTicks < WAIL_HIT;
    }

    @Override
    protected boolean committing(int a) {
        return a == FLICKER || a == GRASP || a == WAIL;
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 12f);
        getLookControl().setLookAt(target);
        if (restTicks <= 0 && mayCommit(level, target)) {
            if (d <= 2.6) {
                startGrasp(level, GRASP_HIT);
                return;
            }
            boolean seen = hasLineOfSight(target);
            if (seen && d <= WAIL_RANGE - 0.5 && getRandom().nextFloat() < 0.5f) {
                startWail(level, target);
                return;
            }
            if (seen && d >= 4 && d <= 16) {
                begin(FLICKER, "flicker");
                sound(level, SoundEvents.ENDERMAN_STARE, 0.5f, 1.6f);
                fx(level, "curse_tell", position().add(0, getBbHeight() * 0.85, 0), Vec3.ZERO, 1f);
                return;
            }
        }
        if (d > 4.5) walkTo(target.position(), 1.0);
        else getNavigation().stop();
    }

    private void startGrasp(ServerLevel level, int hitAt) {
        begin(GRASP, "grasp");
        grasped = false;
        sound(level, SoundEvents.WITCH_AMBIENT, 1f, 0.5f);
        fx(level, "curse_tell", position().add(forward().scale(0.5)).add(0, 1.4, 0), Vec3.ZERO, 0.8f);
    }

    private void startWail(ServerLevel level, LivingEntity target) {
        begin(WAIL, "wail");
        wailDir = flatDir(target.position());
        sound(level, SoundEvents.WITCH_CELEBRATE, 1f, 0.5f);
        fx(level, "curse_tell", position().add(0, getBbHeight() * 0.85, 0), Vec3.ZERO, 1f);
        for (int s = -1; s <= 1; s++) {
            double a = Math.toRadians(WAIL_HALF_ANGLE * s);
            Vec3 d = new Vec3(wailDir.x * Math.cos(a) - wailDir.z * Math.sin(a), 0, wailDir.x * Math.sin(a) + wailDir.z * Math.cos(a));
            warnLine(level, d.scale(WAIL_RANGE), WAIL_HIT);
        }
    }

    /** Test hooks. */
    public void forceFlickerForTest(LivingEntity target) {
        setTarget(target);
        begin(FLICKER, "flicker");
    }

    public void forceWailForTest(LivingEntity target) {
        setTarget(target);
        if (level() instanceof ServerLevel level) startWail(level, target);
    }

    public int actionForTest() {
        return action;
    }

    /** Where it reappears: just behind its prey (from where it faces), on open ground, or null. */
    @Nullable
    private Vec3 behind(ServerLevel level, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0, -look.z);
        if (back.lengthSqr() < 1e-4) back = flatDir(target.position());
        back = back.normalize();
        // Behind where they face, or else on the far side of them from where it stood.
        for (Vec3 dir : new Vec3[] {back, flatDir(target.position())}) {
            for (double k : new double[] {BEHIND, BEHIND - 0.8, BEHIND + 0.8}) {
                Vec3 at = target.position().add(dir.scale(k));
                BlockPos p = BlockPos.containing(at);
                if (level.noCollision(this, getBoundingBox().move(at.subtract(position())))
                        && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) return at;
            }
        }
        return null;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case FLICKER -> {
                setDeltaMovement(getDeltaMovement().multiply(0.1, 1, 0.1));
                if (actionTicks % 4 == 0) fx(level, "curse_scatter", position().add(0, getBbHeight() * 0.6, 0), Vec3.ZERO, 1f);
                if (actionTicks >= FLICKER_TICKS) {
                    Vec3 at = target == null ? null : behind(level, target);
                    if (at != null) {
                        fx(level, "fb_manifest", position().add(0, 1, 0), Vec3.ZERO, 0.6f);
                        teleportTo(at.x, at.y, at.z);
                        turnToward(target.position(), 360f);
                        fx(level, "fb_manifest", at.add(0, 1, 0), Vec3.ZERO, 0.6f);
                        sound(level, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
                        startGrasp(level, GRASP_HIT);
                    } else {
                        finishAction(20);
                    }
                }
            }
            case GRASP -> {
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                if (target != null && actionTicks < GRASP_HIT - 3) turnToward(target.position(), 30f);
                if (actionTicks == GRASP_HIT) {
                    Vec3 from = position().add(0, 1.2, 0), f = forward();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(f.scale(2.6)), 1.1), 0.1, true)) {
                        HitResult r = strike(e, Hit.builder(this, "bride_grasp").damage(dmg(1f)).tag(AttackTag.MELEE)
                                .knockback(Knockback.toward(position().add(f.scale(0.8)), 0.4)).hitstun(14).origin(from).fx("curse_bite", 1f));
                        if (r != null && r.outcome() == HitResult.Outcome.HIT) grasped = true;
                    }
                    sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f);
                }
                if (actionTicks >= GRASP_END) {
                    if (grasped) finishAction(36);
                    else {
                        action = LINGER;
                        actionTicks = 0;
                        Anim.playOn(this, clipPrefix() + "_breath");
                        expose(level, LINGER_TICKS);
                    }
                }
            }
            case WAIL -> {
                setDeltaMovement(getDeltaMovement().multiply(0.1, 1, 0.1));
                if (actionTicks == WAIL_HIT) {
                    Vec3 from = position().add(0, getBbHeight() * 0.8, 0);
                    Vec3 dir = new Vec3(wailDir.x, -0.15, wailDir.z).normalize();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.cone(position().add(0, 1.0, 0), wailDir, WAIL_RANGE, WAIL_HALF_ANGLE), 0.2, true)) {
                        strike(e, Hit.builder(this, "bride_wail").damage(dmg(0.7f)).tag(AttackTag.AREA)
                                .knockback(Knockback.directional(wailDir, 1.0, 0.35)).hitstun(10).origin(from).fx("curse_bite", 0.6f));
                    }
                    fx(level, "fb_roar", from, dir, 1.4f);
                    sound(level, SoundEvents.GHAST_SCREAM, 1.6f, 1.3f);
                    dev.rick.jjk.core.fx.Fx.shake(level, from, 10, 0.35f, 8);
                }
                if (actionTicks >= WAIL_END) {
                    action = BREATH;
                    actionTicks = 0;
                    Anim.playOn(this, clipPrefix() + "_breath");
                    expose(level, BREATH_TICKS);
                }
            }
            case BREATH, LINGER -> {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (actionTicks >= (action == BREATH ? BREATH_TICKS : LINGER_TICKS)) finishAction(30);
            }
            default -> finishAction(10);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.55f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITCH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITCH_DEATH;
    }
}
