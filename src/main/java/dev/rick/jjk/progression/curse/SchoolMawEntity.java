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
 *   <li><b>Ground slam</b>: crowd it, or get round its side, and it rears up (a red ring on the floor around it), then
 *   slams the ground: everything on the floor in the ring is thrown back. Jump it, or be outside the ring.</li>
 *   <li><b>Enraged</b> (below half health, it runs red): it rests less, and a whiffed bite snaps a second time before
 *   it hunches over. Grade 3 and up, a lash that misses is thrown again.</li>
 *   <li>It shrugs off knockback and doesn't flinch mid-bite or mid-slam (its poise still drops: it reels once the move
 *   is done). The bite's reach and the lash's line are marked on the floor while it winds up.</li>
 * </ul>
 */
public class SchoolMawEntity extends CommonCurseEntity {
    static final int BITE = 1, LASH = 2, RECOVER = 3, SLAM = 4, SLAM_RECOVER = 5;
    public static final int BITE_HIT = 14, BITE_END = 20, LASH_FIRE = 10, LASH_END = 18, RECOVER_TICKS = 28, PULLED_BITE_HIT = 7;
    public static final int SLAM_HIT = 18, SLAM_RECOVER_TICKS = 18, CROWDED_TICKS = 40;
    public static final double LASH_RANGE = 8.5, BITE_REACH = 2.2, SLAM_RADIUS = 3.2;
    private Vec3 aim = Vec3.ZERO;
    private int biteAt = BITE_HIT;
    private boolean bitSomeone, secondBite, secondLash;
    /** Ticks its target has stayed right up against it. */
    private int crowded;

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
    protected boolean windingUp(int a) {
        return a == BITE && actionTicks < biteAt || a == LASH && actionTicks < LASH_FIRE || a == SLAM && actionTicks < SLAM_HIT;
    }

    @Override
    protected boolean committing(int a) {
        return a == BITE || a == LASH || a == SLAM;
    }

    @Override
    protected boolean armored(int a) {
        return a == BITE || a == SLAM;
    }

    @Override
    protected boolean enraged() {
        return getHealth() <= getMaxHealth() * 0.5f && isAlive();
    }

    @Override
    protected void rest(int ticks) {
        super.rest(enraged() ? Math.round(ticks * 0.6f) : ticks);
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 10f);
        getLookControl().setLookAt(target);
        crowded = d <= SLAM_RADIUS ? crowded + 1 : Math.max(0, crowded - 2);
        if (restTicks <= 0 && mayCommit(level, target)) {
            // Crowded too long, or got round its side: it slams the floor around it.
            boolean flanked = d <= SLAM_RADIUS && forward().dot(flatDir(target.position())) < 0.35;
            if (d <= SLAM_RADIUS && (crowded >= CROWDED_TICKS || flanked)) {
                startSlam(level);
                return;
            }
            if (d <= BITE_REACH + 0.4) {
                secondBite = false;
                startBite(level, BITE_HIT);
                return;
            }
            if (d >= 3.5 && d <= LASH_RANGE && hasLineOfSight(target) && lineClear(level, target)) {
                secondLash = false;
                startLash(level, target);
                return;
            }
        } else if (restTicks <= 0 && d < 4) {
            // Another curse has the opening: it holds back and menaces.
            walkTo(position().add(flatDir(target.position()).scale(-2.5)), 0.8);
            return;
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
        warnRing(level, position().add(forward().scale(1.4)).add(0, 0.05, 0), 1.3f, hitAt);
    }

    private void startLash(ServerLevel level, LivingEntity target) {
        begin(LASH, null);
        aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mouth()).normalize();
        sound(level, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
        fx(level, "curse_tell", mouth(), Vec3.ZERO, 0.8f);
        warnLine(level, new Vec3(aim.x, 0, aim.z).normalize().scale(LASH_RANGE), LASH_FIRE);
    }

    private void startSlam(ServerLevel level) {
        begin(SLAM, "attack");
        crowded = 0;
        sound(level, SoundEvents.RAVAGER_ROAR, 0.9f, 0.8f);
        fx(level, "curse_tell", position().add(0, getBbHeight() + 0.2, 0), Vec3.ZERO, 1.2f);
        warnRing(level, position().add(0, 0.05, 0), (float) SLAM_RADIUS, SLAM_HIT);
    }

    /** Test hook: starts the slam now. */
    public void forceSlamForTest() {
        if (level() instanceof ServerLevel level) startSlam(level);
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
                    else if (poiseBroken()) finishAction(0);
                    else if (enraged() && !secondBite && target != null && distanceTo(target) < BITE_REACH + 1.5) {
                        // Enraged: it snaps again before it gives up.
                        secondBite = true;
                        startBite(level, 10);
                    } else {
                        // Missed: hunched over, mouth hanging open.
                        action = RECOVER;
                        actionTicks = 0;
                        expose(level, RECOVER_TICKS);
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
                if (actionTicks >= LASH_END) {
                    if (curseGrade().rank() >= 1 && !secondLash && target != null && distanceTo(target) >= 3.5 && distanceTo(target) <= LASH_RANGE
                            && lineClear(level, target)) {
                        // Grade 3 and up: a missed lash is thrown again.
                        secondLash = true;
                        startLash(level, target);
                    } else {
                        finishAction(40);
                    }
                }
            }
            case SLAM -> {
                setDeltaMovement(getDeltaMovement().multiply(0.1, 1, 0.1));
                if (actionTicks == SLAM_HIT) {
                    Vec3 c = position();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.sphere(c.add(0, 0.6, 0), SLAM_RADIUS), 0.1, true)) {
                        // Only what's on the floor: a jump clears it.
                        if (e.getY() - getY() > 1.1) continue;
                        Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
                        out = out.lengthSqr() < 1e-4 ? forward() : out.normalize();
                        strike(e, Hit.builder(this, "maw_slam").damage(dmg(1.1f)).tag(AttackTag.MELEE, AttackTag.HEAVY)
                                .knockback(Knockback.directional(out, 0.9, 0.45)).hitstun(12).guardDamage(3).origin(c).fx("curse_bite", 1.2f));
                    }
                    fx(level, "fb_smash", c.add(0, 0.05, 0), Vec3.ZERO, (float) SLAM_RADIUS);
                    Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.7f);
                    Fx.shake(level, c, 10, 0.45f, 8);
                }
                if (actionTicks >= SLAM_HIT + 2) {
                    action = SLAM_RECOVER;
                    actionTicks = 0;
                }
            }
            case SLAM_RECOVER -> {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (actionTicks >= SLAM_RECOVER_TICKS) finishAction(30);
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
