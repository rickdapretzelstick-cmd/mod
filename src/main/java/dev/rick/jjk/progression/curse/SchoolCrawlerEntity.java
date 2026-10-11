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
 * School Crawler (a strong Grade 4): a many-armed thing that scuttles low along the floor. A skirmisher: it never stands
 * in front of you for long.
 * <ul>
 *   <li><b>Circling</b>: it keeps at mid range and strafes sideways around its prey, switching direction.</li>
 *   <li><b>Pounce</b>: it presses itself flat (the tell: its arms gather under it), then springs at you. Sidestep and it
 *   skids past and lies sprawled for a moment: that is when to hit it.</li>
 *   <li><b>Swipe</b>: up close, a quick raking swipe with the front arms, after which it scuttles back out.</li>
 *   <li><b>Feint</b>: now and then the gathered pounce is a bluff: it darts sideways instead, then pounces from there
 *   after a much shorter (still glowing) crouch. Dodge on the real spring, not the first crouch.</li>
 *   <li><b>Crash</b>: a pounce that runs into a wall leaves it stunned and open for far longer.</li>
 *   <li>It circles toward your blind side. Grade 3 and up, a missed pounce springs once more before it sprawls.</li>
 * </ul>
 */
public class SchoolCrawlerEntity extends CommonCurseEntity {
    static final int POUNCE_WINDUP = 1, POUNCE = 2, SPRAWL = 3, SWIPE = 4, BACK_OFF = 5, FEINT = 6;
    public static final int POUNCE_WINDUP_TICKS = 12, POUNCE_TICKS = 14, SPRAWL_TICKS = 30, SWIPE_HIT = 10, SWIPE_END = 18, BACK_OFF_TICKS = 16;
    public static final int FEINT_TICKS = 7, QUICK_WINDUP_TICKS = 7, CRASH_TICKS = 55;
    private int windupTicks = POUNCE_WINDUP_TICKS;
    private int sprawlTicks = SPRAWL_TICKS;
    private boolean feinting, chained;
    private Vec3 feintDir = Vec3.ZERO;
    private Vec3 aim = Vec3.ZERO;
    private int strafeSign = 1;
    private int strafeFor;
    private boolean landed;

    public SchoolCrawlerEntity(EntityType<? extends SchoolCrawlerEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_4);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "school_crawler";
    }

    @Override
    protected double baseHealth() {
        return 20;
    }

    @Override
    protected float baseDamage() {
        return 3f;
    }

    @Override
    protected boolean windingUp(int a) {
        return a == POUNCE_WINDUP || a == SWIPE && actionTicks < SWIPE_HIT;
    }

    @Override
    protected boolean committing(int a) {
        return a == POUNCE_WINDUP || a == POUNCE || a == SWIPE || a == FEINT;
    }

    /** How often a pounce is a bluff. */
    private float feintChance() {
        return curseGrade().rank() >= 1 ? 0.4f : 0.25f;
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 18f);
        getLookControl().setLookAt(target);
        if (restTicks <= 0 && mayCommit(level, target)) {
            if (d <= 2.4) {
                begin(SWIPE, "attack");
                sound(level, SoundEvents.SPIDER_AMBIENT, 1f, 1.3f);
                return;
            }
            if (d >= 3.5 && d <= 9 && hasLineOfSight(target)) {
                chained = false;
                startWindup(level, target, POUNCE_WINDUP_TICKS, getRandom().nextFloat() < feintChance());
                return;
            }
        }
        // Circle at about five blocks, switching direction now and then, mostly toward the side its prey isn't facing.
        Vec3 to = flatDir(target.position());
        if (--strafeFor <= 0) {
            strafeFor = 30 + getRandom().nextInt(30);
            Vec3 look = target.getLookAngle();
            Vec3 left = new Vec3(-to.z, 0, to.x);
            // Which way round takes it further out of its prey's view.
            Vec3 a = target.position().subtract(to.scale(5)).add(left.scale(3)).subtract(target.position());
            Vec3 b = target.position().subtract(to.scale(5)).add(left.scale(-3)).subtract(target.position());
            int blind = a.normalize().dot(look) < b.normalize().dot(look) ? 1 : -1;
            strafeSign = getRandom().nextFloat() < 0.7f ? blind : -blind;
        }
        Vec3 side = new Vec3(-to.z, 0, to.x).scale(strafeSign);
        double want = 5;
        Vec3 spot = target.position().subtract(to.scale(want)).add(side.scale(3));
        walkTo(spot, d > 8 ? 1.25 : 1.0);
    }

    private void startWindup(ServerLevel level, LivingEntity target, int ticks, boolean feint) {
        begin(POUNCE_WINDUP, null);
        windupTicks = ticks;
        feinting = feint;
        aim = flatDir(target.position());
        sound(level, SoundEvents.SPIDER_STEP, 1.2f, 0.6f);
        fx(level, "curse_tell", position().add(forward().scale(0.6)).add(0, 0.6, 0), Vec3.ZERO, 0.8f);
        if (!feint) warnLine(level, aim.scale(5), ticks);
    }

    /** Test hooks. */
    public void forceWindupForTest(LivingEntity target, boolean feint) {
        if (level() instanceof ServerLevel level) {
            setTarget(target);
            startWindup(level, target, POUNCE_WINDUP_TICKS, feint);
        }
    }

    public int actionForTest() {
        return action;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case POUNCE_WINDUP -> {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (target != null && actionTicks < windupTicks - 3) {
                    turnToward(target.position(), 25f);
                    aim = flatDir(target.position());
                }
                if (feinting && actionTicks >= windupTicks) {
                    // The bluff: a dart to the side instead of the spring.
                    action = FEINT;
                    actionTicks = 0;
                    feinting = false;
                    feintDir = new Vec3(-aim.z, 0, aim.x).scale(getRandom().nextBoolean() ? 1 : -1);
                    sound(level, SoundEvents.SPIDER_STEP, 1f, 1.5f);
                    fx(level, "curse_pounce", position(), feintDir, 0.6f);
                    return;
                }
                if (actionTicks >= windupTicks) {
                    action = POUNCE;
                    actionTicks = 0;
                    landed = false;
                    dev.rick.jjk.core.anim.Anim.playOn(this, curseKind() + "_attack");
                    setDeltaMovement(aim.x * 0.95, 0.38, aim.z * 0.95);
                    sound(level, SoundEvents.SPIDER_HURT, 1f, 0.7f);
                    fx(level, "curse_pounce", position(), aim, 1f);
                }
            }
            case POUNCE -> {
                Vec3 from = position().add(0, 0.6, 0);
                for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(aim.scale(1.2)), 0.9), 0.1, true)) {
                    HitResult r = strike(e, Hit.builder(this, "crawler_pounce").damage(dmg(1.2f)).tag(AttackTag.MELEE)
                            .knockback(Knockback.directional(aim, 0.7, 0.25)).hitstun(10).origin(from).fx("curse_bite", 0.9f));
                    if (r != null && r.outcome() == HitResult.Outcome.HIT) landed = true;
                }
                boolean crashed = !landed && horizontalCollision && actionTicks > 1;
                if (landed || crashed || (actionTicks > 4 && onGround()) || actionTicks >= POUNCE_TICKS) {
                    if (landed) {
                        action = BACK_OFF;
                        actionTicks = 0;
                    } else if (crashed) {
                        // Ran headlong into a wall: stunned, wide open.
                        action = SPRAWL;
                        actionTicks = 0;
                        sprawlTicks = CRASH_TICKS;
                        setDeltaMovement(aim.scale(-0.25).add(0, 0.2, 0));
                        sound(level, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 1.3f);
                        expose(level, CRASH_TICKS);
                    } else if (curseGrade().rank() >= 1 && !chained && target != null && distanceTo(target) <= 9) {
                        // Grade 3 and up: it springs once more straight away.
                        chained = true;
                        startWindup(level, target, QUICK_WINDUP_TICKS, false);
                    } else {
                        // Skidded past: sprawled and open.
                        action = SPRAWL;
                        actionTicks = 0;
                        sprawlTicks = SPRAWL_TICKS;
                        expose(level, SPRAWL_TICKS);
                    }
                }
            }
            case FEINT -> {
                setDeltaMovement(feintDir.x * 0.42, getDeltaMovement().y, feintDir.z * 0.42);
                if (actionTicks >= FEINT_TICKS) {
                    if (target != null) startWindup(level, target, QUICK_WINDUP_TICKS, false);
                    else finishAction(20);
                }
            }
            case SPRAWL -> {
                setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                if (actionTicks >= sprawlTicks) finishAction(30);
            }
            case SWIPE -> {
                if (target != null && actionTicks < SWIPE_HIT - 3) turnToward(target.position(), 20f);
                if (actionTicks == SWIPE_HIT) {
                    Vec3 from = position().add(0, 0.6, 0), f = forward();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(f.scale(2.2)), 1.0), 0.1, true)) {
                        strike(e, Hit.builder(this, "crawler_swipe").damage(dmg(1f)).tag(AttackTag.MELEE)
                                .knockback(Knockback.directional(f, 0.45, 0.15)).hitstun(8).origin(from).fx("curse_bite", 0.8f));
                    }
                    sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
                }
                if (actionTicks >= SWIPE_END) {
                    action = BACK_OFF;
                    actionTicks = 0;
                }
            }
            case BACK_OFF -> {
                // Scuttles back out of reach.
                Vec3 back = target == null ? forward().scale(-1) : flatDir(target.position()).scale(-1);
                setDeltaMovement(back.x * 0.28, getDeltaMovement().y, back.z * 0.28);
                if (actionTicks >= BACK_OFF_TICKS) finishAction(26);
            }
            default -> finishAction(10);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }
}
