package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
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
 * Bloat Toad (Grade 3): a swollen, warty toad the size of a cart with human hands for front feet. It barely walks; it
 * hops, and it means to land on you.
 * <ul>
 *   <li><b>Hop</b>: a short heavy hop toward its prey every second or so.</li>
 *   <li><b>Belly flop</b>: it squats, swelling (the tell), and the spot it will land on is marked with a ring on the
 *   floor the whole time. Then it leaps high and comes down there: everything on the floor in the ring is hurt and
 *   thrown out. Get out of the ring. It lands on its back and lies there kicking, <b>exposed</b>, for two seconds.</li>
 *   <li><b>Belch</b>: crowd it and it heaves a gust of foul air in front of it that shoves you away.</li>
 *   <li>It shrugs off most knockback and doesn't flinch mid-flop.</li>
 * </ul>
 */
public class BloatToadEntity extends CommonCurseEntity {
    static final int HOP = 1, FLOP_WINDUP = 2, FLOP = 3, ON_BACK = 4, BELCH = 5;
    public static final int HOP_TICKS = 10, FLOP_WINDUP_TICKS = 18, FLOP_TICKS = 16, ON_BACK_TICKS = 40, BELCH_HIT = 10, BELCH_END = 16;
    public static final double FLOP_RADIUS = 3.0, FLOP_HEIGHT = 4.5;
    private Vec3 from = Vec3.ZERO, landing = Vec3.ZERO;
    private int hopIn = 20;

    public BloatToadEntity(EntityType<? extends BloatToadEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.16)
                .add(Attributes.FOLLOW_RANGE, 28).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 3).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "bloat_toad";
    }

    @Override
    protected double baseHealth() {
        return 40;
    }

    @Override
    protected float baseDamage() {
        return 5.5f;
    }

    @Override
    protected boolean windingUp(int a) {
        return a == FLOP_WINDUP || a == BELCH && actionTicks < BELCH_HIT;
    }

    @Override
    protected boolean committing(int a) {
        return a == FLOP_WINDUP || a == FLOP || a == BELCH;
    }

    @Override
    protected boolean armored(int a) {
        return a == FLOP;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public boolean onBack() {
        return action() == ON_BACK;
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 8f);
        getLookControl().setLookAt(target);
        if (restTicks <= 0 && mayCommit(level, target)) {
            if (d <= 2.8) {
                begin(BELCH, "belch");
                sound(level, SoundEvents.FROG_AMBIENT, 1.4f, 0.4f);
                fx(level, "curse_tell", position().add(forward().scale(0.8)).add(0, 0.8, 0), Vec3.ZERO, 0.9f);
                return;
            }
            if (d >= 4 && d <= 12 && hasLineOfSight(target)) {
                startFlop(level, target.position());
                return;
            }
        }
        if (--hopIn <= 0 && d > 3 && onGround()) {
            hopIn = 22 + getRandom().nextInt(10);
            begin(HOP, "hop");
            Vec3 to = flatDir(target.position());
            setDeltaMovement(to.x * 0.38, 0.42, to.z * 0.38);
            sound(level, SoundEvents.FROG_LONG_JUMP, 1f, 0.5f);
        }
    }

    private void startFlop(ServerLevel level, Vec3 at) {
        begin(FLOP_WINDUP, "squat");
        landing = at;
        sound(level, SoundEvents.FROG_AMBIENT, 1.6f, 0.3f);
        fx(level, "curse_tell", position().add(0, getBbHeight(), 0), Vec3.ZERO, 1.1f);
        warnRing(level, at.add(0, 0.05, 0), (float) FLOP_RADIUS, FLOP_WINDUP_TICKS + FLOP_TICKS);
    }

    /** Test hooks. */
    public void forceFlopForTest(Vec3 at) {
        if (level() instanceof ServerLevel level) startFlop(level, at);
    }

    public Vec3 landingForTest() {
        return landing;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case HOP -> {
                if (actionTicks > 3 && onGround() || actionTicks >= HOP_TICKS) {
                    sound(level, SoundEvents.SLIME_SQUISH, 0.8f, 0.5f);
                    finishAction(0);
                }
            }
            case FLOP_WINDUP -> {
                setDeltaMovement(getDeltaMovement().multiply(0.1, 1, 0.1));
                turnToward(landing, 15f);
                if (actionTicks >= FLOP_WINDUP_TICKS) {
                    action = FLOP;
                    actionTicks = 0;
                    from = position();
                    setNoGravity(true);
                    Anim.playOn(this, clipPrefix() + "_flop");
                    sound(level, SoundEvents.FROG_LONG_JUMP, 1.6f, 0.4f);
                    fx(level, "curse_pounce", position(), Vec3.ZERO, 1.4f);
                }
            }
            case FLOP -> {
                // Along an arc to the marked spot, then down on it.
                float u = Math.min(1f, actionTicks / (float) FLOP_TICKS);
                float un = Math.min(1f, (actionTicks + 1) / (float) FLOP_TICKS);
                Vec3 p0 = arc(u), p1 = arc(un);
                setDeltaMovement(p1.subtract(p0));
                if (actionTicks >= FLOP_TICKS || (u > 0.6f && (onGround() || horizontalCollision))) land(level);
            }
            case ON_BACK -> {
                setDeltaMovement(getDeltaMovement().multiply(0.4, 1, 0.4));
                if (actionTicks >= ON_BACK_TICKS) finishAction(30);
            }
            case BELCH -> {
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                if (target != null && actionTicks < BELCH_HIT - 3) turnToward(target.position(), 15f);
                if (actionTicks == BELCH_HIT) {
                    Vec3 f = forward();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.cone(position().add(0, 0.7, 0), f, 4.0, 40), 0.2, true)) {
                        strike(e, Hit.builder(this, "toad_belch").damage(dmg(0.5f)).tag(AttackTag.AREA)
                                .knockback(Knockback.directional(f, 1.1, 0.3)).hitstun(8).origin(position()).fx("curse_bite", 0.5f));
                    }
                    fx(level, "curse_spit_burst", position().add(f.scale(1.4)).add(0, 0.8, 0), Vec3.ZERO, 1.5f);
                    fx(level, "fb_roar", position().add(f.scale(0.9)).add(0, 0.8, 0), f, 0.8f);
                    sound(level, SoundEvents.FROG_EAT, 1.6f, 0.4f);
                }
                if (actionTicks >= BELCH_END) finishAction(28);
            }
            default -> finishAction(10);
        }
    }

    private Vec3 arc(float u) {
        Vec3 flat = from.lerp(landing, u);
        return flat.add(0, 4 * FLOP_HEIGHT * u * (1 - u), 0);
    }

    private void land(ServerLevel level) {
        setNoGravity(false);
        setDeltaMovement(0, -0.3, 0);
        Vec3 c = position();
        for (LivingEntity e : HitboxQuery.targets(this, HitShape.sphere(c.add(0, 0.5, 0), FLOP_RADIUS), 0.1, false)) {
            if (e.getY() - c.y > 1.2) continue;
            Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            out = out.lengthSqr() < 1e-4 ? forward() : out.normalize();
            strike(e, Hit.builder(this, "toad_flop").damage(dmg(1.1f)).tag(AttackTag.AREA, AttackTag.HEAVY)
                    .knockback(Knockback.directional(out, 0.9, 0.5)).hitstun(14).guardDamage(3).origin(c).fx("curse_bite", 1.2f));
        }
        fx(level, "fb_smash", c.add(0, 0.05, 0), Vec3.ZERO, (float) FLOP_RADIUS);
        Fx.sound(level, c, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.6f);
        Fx.shake(level, c, 12, 0.5f, 8);
        action = ON_BACK;
        actionTicks = 0;
        Anim.playOn(this, clipPrefix() + "_on_back");
        expose(level, ON_BACK_TICKS);
    }

    @Override
    public void die(DamageSource source) {
        setNoGravity(false);
        super.die(source);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        setNoGravity(false);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FROG_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.45f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FROG_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FROG_DEATH;
    }
}
