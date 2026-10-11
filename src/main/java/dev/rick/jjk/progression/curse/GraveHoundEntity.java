package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.grade.CurseGrade;
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
 * Grave Hound (Grade 4): a flayed, eyeless dog of a curse that runs in packs. Alone it is a nuisance; two or three
 * keep a fighter turning, and its howl makes every curse around it quicker to attack.
 * <ul>
 *   <li><b>Circling</b>: it lopes round its prey at a few blocks, the pack spread out.</li>
 *   <li><b>Leap</b>: it drops low with its lane marked on the floor (the tell), then springs. Sidestep and it lands in
 *   a sprawl, <b>exposed</b>.</li>
 *   <li><b>Snap</b>: up close, a quick bite, then it backs off.</li>
 *   <li><b>Howl</b>: from the edge of the fight it throws its head back and howls (glowing: the tell). When it finishes,
 *   every curse within {@link #HOWL_RANGE} attacks again at once. Hit it hard enough while it howls and it is staggered
 *   out of it; that's the play.</li>
 * </ul>
 */
public class GraveHoundEntity extends CommonCurseEntity {
    static final int LEAP_WINDUP = 1, LEAP = 2, STUMBLE = 3, BITE = 4, HOWL = 5, BACK_OFF = 6;
    public static final int LEAP_WINDUP_TICKS = 10, LEAP_TICKS = 12, STUMBLE_TICKS = 26, BITE_HIT = 6, BITE_END = 12, HOWL_TICKS = 24,
            HOWL_AT = 18, BACK_OFF_TICKS = 10, HOWL_COOLDOWN = 240;
    public static final double HOWL_RANGE = 14;
    private Vec3 aim = Vec3.ZERO;
    private int strafeSign = 1, strafeFor, howlCooldown = 60;
    private boolean landed;

    public GraveHoundEntity(EntityType<? extends GraveHoundEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_4);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 28).add(Attributes.ATTACK_DAMAGE, 2.5).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "grave_hound";
    }

    @Override
    protected double baseHealth() {
        return 14;
    }

    @Override
    protected float baseDamage() {
        return 2.5f;
    }

    @Override
    protected boolean windingUp(int a) {
        return a == LEAP_WINDUP || a == BITE && actionTicks < BITE_HIT || a == HOWL && actionTicks < HOWL_AT;
    }

    @Override
    protected boolean committing(int a) {
        return a == LEAP_WINDUP || a == LEAP || a == BITE;
    }

    public boolean howling() {
        return action() == HOWL;
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 20f);
        getLookControl().setLookAt(target);
        if (howlCooldown > 0) howlCooldown--;
        if (restTicks <= 0 && howlCooldown <= 0 && d > 4 && packNearby(level) > 0) {
            startHowl(level);
            return;
        }
        if (restTicks <= 0 && mayCommit(level, target)) {
            if (d <= 2.2) {
                begin(BITE, "bite");
                sound(level, SoundEvents.HOGLIN_ANGRY, 1f, 1.4f);
                return;
            }
            if (d >= 3.5 && d <= 8 && hasLineOfSight(target) && gapReady()) {
                gapUsed();
                begin(LEAP_WINDUP, "crouch");
                aim = flatDir(target.position());
                sound(level, SoundEvents.HOGLIN_ANGRY, 1.2f, 1.2f);
                fx(level, "curse_tell", position().add(forward().scale(0.6)).add(0, 0.5, 0), Vec3.ZERO, 0.7f);
                warnLine(level, aim.scale(6), LEAP_WINDUP_TICKS);
                return;
            }
        }
        if (--strafeFor <= 0) {
            strafeFor = 25 + getRandom().nextInt(30);
            strafeSign = getRandom().nextBoolean() ? 1 : -1;
        }
        Vec3 to = flatDir(target.position());
        Vec3 side = new Vec3(-to.z, 0, to.x).scale(strafeSign);
        walkTo(target.position().subtract(to.scale(4.5)).add(side.scale(3)), d > 8 ? 1.3 : 1.05);
    }

    private int packNearby(ServerLevel level) {
        return level.getEntitiesOfClass(CommonCurseEntity.class, getBoundingBox().inflate(HOWL_RANGE), e -> e != this && e.isAlive() && !e.dormant()).size();
    }

    private void startHowl(ServerLevel level) {
        begin(HOWL, "howl");
        howlCooldown = HOWL_COOLDOWN;
        sound(level, SoundEvents.GHAST_SCREAM, 1.0f, 0.45f);
        fx(level, "curse_tell", position().add(0, getBbHeight() + 0.2, 0), Vec3.ZERO, 1f);
    }

    /** Test hooks. */
    public void forceHowlForTest() {
        if (level() instanceof ServerLevel level) startHowl(level);
    }

    public void forceLeapForTest(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)) return;
        setTarget(target);
        begin(LEAP_WINDUP, "crouch");
        aim = flatDir(target.position());
        warnLine(level, aim.scale(6), LEAP_WINDUP_TICKS);
    }

    public int actionForTest() {
        return action;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case LEAP_WINDUP -> {
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                if (target != null && actionTicks < LEAP_WINDUP_TICKS - 3) {
                    turnToward(target.position(), 25f);
                    aim = flatDir(target.position());
                }
                if (actionTicks >= LEAP_WINDUP_TICKS) {
                    action = LEAP;
                    actionTicks = 0;
                    landed = false;
                    dev.rick.jjk.core.anim.Anim.playOn(this, clipPrefix() + "_attack");
                    setDeltaMovement(aim.x * 1.05, 0.34, aim.z * 1.05);
                    sound(level, SoundEvents.HOGLIN_ATTACK, 1.2f, 1.5f);
                    fx(level, "curse_pounce", position(), aim, 0.8f);
                }
            }
            case LEAP -> {
                Vec3 from = position().add(0, 0.5, 0);
                for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(aim.scale(1.1)), 0.75), 0.1, true)) {
                    HitResult r = strike(e, Hit.builder(this, "hound_leap").damage(dmg(1.2f)).tag(AttackTag.MELEE)
                            .knockback(Knockback.directional(aim, 0.6, 0.2)).hitstun(10).origin(from).fx("curse_bite", 0.8f));
                    if (r != null && r.outcome() == HitResult.Outcome.HIT) landed = true;
                }
                if (landed || (actionTicks > 4 && onGround()) || actionTicks >= LEAP_TICKS) {
                    actionTicks = 0;
                    if (landed) {
                        action = BACK_OFF;
                    } else {
                        action = STUMBLE;
                        dev.rick.jjk.core.anim.Anim.playOn(this, clipPrefix() + "_stumble");
                        expose(level, STUMBLE_TICKS);
                    }
                }
            }
            case STUMBLE -> {
                setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                if (actionTicks >= STUMBLE_TICKS) finishAction(26);
            }
            case BITE -> {
                if (target != null && actionTicks < BITE_HIT - 2) turnToward(target.position(), 25f);
                if (actionTicks == BITE_HIT) {
                    Vec3 from = position().add(0, 0.5, 0), f = forward();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(f.scale(1.8)), 0.8), 0.1, true)) {
                        strike(e, Hit.builder(this, "hound_bite").damage(dmg(0.9f)).tag(AttackTag.MELEE)
                                .knockback(Knockback.directional(f, 0.35, 0.1)).hitstun(8).origin(from).fx("curse_bite", 0.7f));
                    }
                    sound(level, SoundEvents.HOGLIN_ATTACK, 0.8f, 1.6f);
                }
                if (actionTicks >= BITE_END) {
                    action = BACK_OFF;
                    actionTicks = 0;
                }
            }
            case HOWL -> {
                setDeltaMovement(getDeltaMovement().multiply(0.1, 1, 0.1));
                if (actionTicks == HOWL_AT) {
                    // The pack answers: every curse around attacks again at once.
                    for (CommonCurseEntity c : level.getEntitiesOfClass(CommonCurseEntity.class, getBoundingBox().inflate(HOWL_RANGE),
                            e -> e != this && e.isAlive() && !e.dormant())) {
                        c.restTicks = 0;
                    }
                    fx(level, "fb_roar", position().add(0, getBbHeight(), 0), new Vec3(0, 1, 0), 1f);
                    sound(level, SoundEvents.GHAST_SCREAM, 1.6f, 0.35f);
                }
                if (actionTicks >= HOWL_TICKS) finishAction(20);
            }
            case BACK_OFF -> {
                Vec3 back = target == null ? forward().scale(-1) : flatDir(target.position()).scale(-1);
                setDeltaMovement(back.x * 0.3, getDeltaMovement().y, back.z * 0.3);
                if (actionTicks >= BACK_OFF_TICKS) finishAction(24);
            }
            default -> finishAction(10);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HOGLIN_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 1.5f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }
}
