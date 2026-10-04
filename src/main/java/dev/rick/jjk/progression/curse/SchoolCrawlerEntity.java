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
 * </ul>
 */
public class SchoolCrawlerEntity extends CommonCurseEntity {
    static final int POUNCE_WINDUP = 1, POUNCE = 2, SPRAWL = 3, SWIPE = 4, BACK_OFF = 5;
    public static final int POUNCE_WINDUP_TICKS = 12, POUNCE_TICKS = 14, SPRAWL_TICKS = 30, SWIPE_HIT = 10, SWIPE_END = 18, BACK_OFF_TICKS = 16;
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
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 18f);
        getLookControl().setLookAt(target);
        if (restTicks <= 0) {
            if (d <= 2.4) {
                begin(SWIPE, "attack");
                sound(level, SoundEvents.SPIDER_AMBIENT, 1f, 1.3f);
                return;
            }
            if (d >= 3.5 && d <= 9 && hasLineOfSight(target)) {
                begin(POUNCE_WINDUP, null);
                aim = flatDir(target.position());
                sound(level, SoundEvents.SPIDER_STEP, 1.2f, 0.6f);
                fx(level, "curse_tell", position().add(forward().scale(0.6)).add(0, 0.6, 0), Vec3.ZERO, 0.8f);
                return;
            }
        }
        // Circle at about five blocks, switching direction now and then.
        if (--strafeFor <= 0) {
            strafeFor = 30 + getRandom().nextInt(30);
            strafeSign = getRandom().nextBoolean() ? 1 : -1;
        }
        Vec3 to = flatDir(target.position());
        Vec3 side = new Vec3(-to.z, 0, to.x).scale(strafeSign);
        double want = 5;
        Vec3 spot = target.position().subtract(to.scale(want)).add(side.scale(3));
        walkTo(spot, d > 8 ? 1.25 : 1.0);
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case POUNCE_WINDUP -> {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (target != null && actionTicks < POUNCE_WINDUP_TICKS - 3) {
                    turnToward(target.position(), 25f);
                    aim = flatDir(target.position());
                }
                if (actionTicks >= POUNCE_WINDUP_TICKS) {
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
                if (landed || (actionTicks > 4 && onGround()) || actionTicks >= POUNCE_TICKS) {
                    if (landed) {
                        action = BACK_OFF;
                        actionTicks = 0;
                    } else {
                        // Skidded past: sprawled and open.
                        action = SPRAWL;
                        actionTicks = 0;
                        fx(level, "fb_stagger", position().add(0, 0.9, 0), Vec3.ZERO, 1f);
                    }
                }
            }
            case SPRAWL -> {
                setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                if (actionTicks >= SPRAWL_TICKS) finishAction(30);
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
