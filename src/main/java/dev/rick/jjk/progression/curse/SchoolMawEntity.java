package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * School Maw (Grade 3): a slow, top-heavy bruiser that is mostly mouth. It doesn't chase well, so it brings you to it.
 * <ul>
 *   <li><b>Tongue lash</b>: from a few blocks away its tongue (with the eye on it) shoots out along a straight line. Caught,
 *   you are yanked in front of its mouth, and the bite follows at once: get out of the line, or guard.</li>
 *   <li><b>Bite</b>: its jaw gapes for most of a second (the tell, and a sound), then slams shut on the space in front of
 *   it, heavy enough to hurt. Whiffed, it stays hunched over for over a second: the punish window.</li>
 *   <li>It shrugs off knockback and doesn't flinch mid-bite.</li>
 * </ul>
 */
public class SchoolMawEntity extends CommonCurseEntity {
    static final int BITE = 1, LASH = 2, RECOVER = 3;
    public static final int BITE_HIT = 14, BITE_END = 20, LASH_FIRE = 10, LASH_END = 18, RECOVER_TICKS = 28, PULLED_BITE_HIT = 7;
    public static final double LASH_RANGE = 8.5, BITE_REACH = 2.2;
    private Vec3 aim = Vec3.ZERO;
    private int biteAt = BITE_HIT;
    private boolean bitSomeone;

    public SchoolMawEntity(EntityType<? extends SchoolMawEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26).add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ARMOR, 2).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "school_maw";
    }

    @Override
    protected double baseHealth() {
        return 26;
    }

    @Override
    protected float baseDamage() {
        return 5f;
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 10f);
        getLookControl().setLookAt(target);
        if (restTicks <= 0) {
            if (d <= BITE_REACH + 0.4) {
                startBite(level, BITE_HIT);
                return;
            }
            if (d >= 3.5 && d <= LASH_RANGE && hasLineOfSight(target) && lineClear(level, target)) {
                begin(LASH, null);
                aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mouth()).normalize();
                sound(level, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
                fx(level, "curse_tell", mouth(), Vec3.ZERO, 0.8f);
                return;
            }
        }
        if (d > 2) walkTo(target.position(), 1.0);
        else getNavigation().stop();
    }

    private void startBite(ServerLevel level, int hitAt) {
        begin(BITE, "attack");
        biteAt = hitAt;
        bitSomeone = false;
        sound(level, SoundEvents.RAVAGER_ROAR, 0.6f, 1.5f);
        fx(level, "curse_tell", mouth(), Vec3.ZERO, 1f);
    }

    private Vec3 mouth() {
        return position().add(forward().scale(0.6)).add(0, 1.0, 0);
    }

    private boolean lineClear(ServerLevel level, LivingEntity target) {
        Vec3 a = mouth(), b = target.position().add(0, target.getBbHeight() * 0.5, 0);
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case BITE -> {
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                if (target != null && actionTicks < biteAt - 5) turnToward(target.position(), 14f);
                if (actionTicks == biteAt) {
                    Vec3 c = position().add(forward().scale(1.4)).add(0, 0.9, 0);
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.sphere(c, 1.3), 0.1, true)) {
                        HitResult r = strike(e, Hit.builder(this, "maw_bite").damage(dmg(1f)).tag(AttackTag.MELEE, AttackTag.HEAVY)
                                .knockback(Knockback.directional(forward(), 0.6, 0.25)).hitstun(12).guardDamage(3).origin(c).fx("curse_bite", 1.4f));
                        if (r != null && r.outcome() == HitResult.Outcome.HIT) bitSomeone = true;
                    }
                    Fx.sound(level, c, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
                    Fx.shake(level, c, 6, 0.3f, 5);
                }
                if (actionTicks >= biteAt + (BITE_END - BITE_HIT)) {
                    if (bitSomeone) finishAction(34);
                    else {
                        // Missed: hunched over, mouth hanging open.
                        action = RECOVER;
                        actionTicks = 0;
                        fx(level, "fb_stagger", position().add(0, 1.6, 0), Vec3.ZERO, 1f);
                    }
                }
            }
            case LASH -> {
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                if (actionTicks == LASH_FIRE) {
                    Vec3 from = mouth(), to = from.add(aim.scale(LASH_RANGE));
                    fx(level, "curse_tongue", from, to.subtract(from), 1f);
                    sound(level, SoundEvents.SLIME_ATTACK, 1.3f, 0.5f);
                    LivingEntity caught = null;
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, to, 0.55), 0.1, true)) {
                        HitResult r = strike(e, Hit.builder(this, "maw_tongue").damage(dmg(0.3f)).tag(AttackTag.MELEE)
                                .knockback(Knockback.HOLD).hitstun(12).origin(from).fx("curse_bite", 0.6f));
                        if (r != null && r.outcome() == HitResult.Outcome.HIT) {
                            caught = e;
                            break;
                        }
                    }
                    if (caught != null) {
                        // Reeled in to the mouth: the bite comes quicker.
                        Vec3 pull = mouth().add(forward().scale(1.0)).subtract(caught.position());
                        Motion.set(caught, new Vec3(pull.x * 0.33, 0.25, pull.z * 0.33));
                        startBite(level, PULLED_BITE_HIT);
                        return;
                    }
                }
                if (actionTicks >= LASH_END) finishAction(40);
            }
            case RECOVER -> {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (actionTicks >= RECOVER_TICKS) finishAction(24);
            }
            default -> finishAction(10);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 1.4f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }
}
