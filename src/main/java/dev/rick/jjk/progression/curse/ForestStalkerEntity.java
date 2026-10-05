package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.anim.Anim;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Hunter's Shade (Grade 3), the curse of the old hunting lodge: something that learned to stalk from the man who
 * stalked these woods. It hunts the way he did, and that is how it is beaten.
 *
 * <ul>
 *   <li><b>Stalking</b>: it keeps to the trees. Look straight at it and it slips behind a trunk, out of your line of
 *   sight; look away and it closes in, cover to cover.</li>
 *   <li><b>The tell</b>: before it commits it shows itself. It stops dead in the open, its eyes light, a dry rattle
 *   carries, and it lowers itself. That stillness is about a second long.</li>
 *   <li><b>The lunge</b>: then it springs straight along the line it locked onto. Step aside and it overshoots, crashes
 *   down and lies <b>exposed</b> (it takes much more damage) for two seconds.</li>
 *   <li><b>Too close</b>: crowd it and it rakes (a short, visible wind-up), then breaks away to the trees.</li>
 * </ul>
 * Nothing it does is unseen or unavoidable: it only attacks from where its prey can see it, every attack has its tell,
 * and the punish after a dodged lunge is long enough for a starter cursed tool. Its model is a placeholder (the School
 * Crawler's, darkened) until it has its own.
 */
public class ForestStalkerEntity extends CommonCurseEntity {
    static final int TELEGRAPH = 1, LUNGE = 2, EXPOSED = 3, RAKE = 4, BREAK_AWAY = 5;
    public static final int TELEGRAPH_TICKS = 22, LOCK_AT = 16, LUNGE_TICKS = 14, EXPOSED_TICKS = 44, RAKE_HIT = 9, RAKE_END = 16, BREAK_TICKS = 20;
    /** Damage it takes while exposed after a dodged lunge. */
    public static final float EXPOSED_TAKEN = 1.6f;
    private Vec3 aim = Vec3.ZERO;
    @Nullable private Vec3 cover;
    private int coverFor;
    /** Ticks spent closing in since the last attack (it commits once it has stalked long enough). */
    private int stalked;
    private boolean landed;

    public ForestStalkerEntity(EntityType<? extends ForestStalkerEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "forest_stalker";
    }

    /** Placeholder model: it borrows the School Crawler's model and clips. */
    @Override
    protected String clipPrefix() {
        return "school_crawler";
    }

    @Override
    protected double baseHealth() {
        return 22;
    }

    @Override
    protected float baseDamage() {
        return 3f;
    }

    public boolean exposed() {
        return action() == EXPOSED;
    }

    public boolean telegraphing() {
        return action() == TELEGRAPH;
    }

    /** Whether {@code e} is looking at it (within about 22 degrees) and can see it. */
    boolean watchedBy(LivingEntity e) {
        Vec3 to = position().add(0, getBbHeight() * 0.5, 0).subtract(e.getEyePosition());
        if (to.lengthSqr() < 1e-4) return true;
        return to.normalize().dot(e.getLookAngle()) > 0.927 && e.hasLineOfSight(this);
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        getLookControl().setLookAt(target);
        boolean seen = hasLineOfSight(target);
        if (restTicks <= 0 && d <= 2.3 && seen) {
            begin(RAKE, "attack");
            sound(level, SoundEvents.SPIDER_AMBIENT, 1.1f, 0.6f);
            fx(level, "curse_tell", position().add(forward().scale(0.6)).add(0, 0.7, 0), Vec3.ZERO, 0.7f);
            return;
        }
        // Commits from the open only: it must be visible to its prey, and at lunging range.
        if (restTicks <= 0 && stalked > 50 && d >= 4 && d <= 10 && seen && target.hasLineOfSight(this)) {
            begin(TELEGRAPH, null);
            stalked = 0;
            aim = flatDir(target.position());
            getNavigation().stop();
            sound(level, SoundEvents.SKELETON_AMBIENT, 1.4f, 0.5f);
            sound(level, SoundEvents.WARDEN_SNIFF, 1.2f, 1.4f);
            fx(level, "stalker_eyes", position().add(0, getBbHeight() * 0.7, 0), aim, 1f);
            fx(level, "curse_tell", position().add(forward().scale(0.6)).add(0, 0.6, 0), Vec3.ZERO, 1f);
            return;
        }
        stalked++;
        if (watchedBy(target) && d < 16) {
            // Seen: slip out of sight, behind the nearest cover from its prey's eye.
            if (cover == null || --coverFor <= 0 || !hidden(level, target, cover)) {
                cover = findCover(level, target);
                coverFor = 30;
            }
            if (cover != null) walkTo(cover, 1.35);
            else walkTo(position().add(flatDir(target.position()).scale(-4)), 1.2);
            return;
        }
        cover = null;
        // Unwatched: close in, but not straight at them: along the side, toward lunging range.
        Vec3 to = flatDir(target.position());
        Vec3 side = new Vec3(-to.z, 0, to.x).scale((tickCount / 80) % 2 == 0 ? 1 : -1);
        Vec3 spot = target.position().subtract(to.scale(6.5)).add(side.scale(2.5));
        walkTo(spot, d > 10 ? 1.25 : 0.95);
        turnToward(target.position(), 14f);
    }

    /** A spot within a few blocks that its prey can't see (a trunk between them), or null. */
    @Nullable
    private Vec3 findCover(ServerLevel level, LivingEntity target) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 + getRandom().nextDouble() * 0.3;
            double r = 2.5 + getRandom().nextDouble() * 5;
            Vec3 at = position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            if (!hidden(level, target, at)) continue;
            // Keep at a stalking distance, and prefer spots close by.
            double score = Math.abs(at.distanceTo(target.position()) - 8) + r * 0.4;
            if (score < bestScore) {
                bestScore = score;
                best = at;
            }
        }
        return best;
    }

    private boolean hidden(ServerLevel level, LivingEntity from, Vec3 at) {
        Vec3 eye = from.getEyePosition(), mid = at.add(0, 0.8, 0);
        var clip = level.clip(new ClipContext(eye, mid, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, from));
        return clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS && clip.getLocation().distanceToSqr(mid) > 1.0;
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case TELEGRAPH -> {
                // Frozen in the open, eyes lit, lowering itself; the line it will spring along locks before it goes.
                setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                if (target != null && actionTicks < LOCK_AT) {
                    turnToward(target.position(), 30f);
                    aim = flatDir(target.position());
                }
                if (actionTicks % 6 == 0) fx(level, "stalker_eyes", position().add(0, getBbHeight() * 0.7, 0), aim, 1f);
                if (actionTicks == LOCK_AT) sound(level, SoundEvents.SPIDER_STEP, 1.4f, 0.5f);
                if (actionTicks >= TELEGRAPH_TICKS) {
                    action = LUNGE;
                    actionTicks = 0;
                    landed = false;
                    Anim.playOn(this, clipPrefix() + "_attack");
                    setDeltaMovement(aim.x * 1.25, 0.32, aim.z * 1.25);
                    sound(level, SoundEvents.PHANTOM_SWOOP, 1.3f, 0.7f);
                    fx(level, "curse_pounce", position(), aim, 1.2f);
                }
            }
            case LUNGE -> {
                Vec3 from = position().add(0, 0.6, 0);
                for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(aim.scale(1.3)), 0.85), 0.1, true)) {
                    HitResult r = strike(e, Hit.builder(this, "stalker_lunge").damage(dmg(1.4f)).tag(AttackTag.MELEE)
                            .knockback(Knockback.directional(aim, 0.8, 0.3)).hitstun(12).origin(from).fx("curse_bite", 1f));
                    if (r != null && r.outcome() == HitResult.Outcome.HIT) landed = true;
                }
                if (landed || (actionTicks > 5 && onGround()) || actionTicks >= LUNGE_TICKS) {
                    actionTicks = 0;
                    if (landed) {
                        action = BREAK_AWAY;
                    } else {
                        // Overshot: down in the leaves, open.
                        action = EXPOSED;
                        sound(level, SoundEvents.SPIDER_HURT, 1f, 0.5f);
                        fx(level, "fb_stagger", position().add(0, 0.9, 0), Vec3.ZERO, 1f);
                    }
                }
            }
            case EXPOSED -> {
                setDeltaMovement(getDeltaMovement().multiply(0.4, 1, 0.4));
                if (actionTicks % 10 == 0) fx(level, "fb_stagger", position().add(0, 0.9, 0), Vec3.ZERO, 0.6f);
                if (actionTicks >= EXPOSED_TICKS) {
                    action = BREAK_AWAY;
                    actionTicks = 0;
                }
            }
            case RAKE -> {
                if (target != null && actionTicks < RAKE_HIT - 3) turnToward(target.position(), 20f);
                if (actionTicks == RAKE_HIT) {
                    Vec3 from = position().add(0, 0.6, 0), f = forward();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(f.scale(2.2)), 1.0), 0.1, true)) {
                        strike(e, Hit.builder(this, "stalker_rake").damage(dmg(0.9f)).tag(AttackTag.MELEE)
                                .knockback(Knockback.directional(f, 0.5, 0.15)).hitstun(8).origin(from).fx("curse_bite", 0.8f));
                    }
                    sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
                }
                if (actionTicks >= RAKE_END) {
                    action = BREAK_AWAY;
                    actionTicks = 0;
                }
            }
            case BREAK_AWAY -> {
                // Back to the trees.
                if (target != null && (cover == null || actionTicks == 1)) cover = findCover(level, target);
                Vec3 away = cover != null ? flatDir(cover) : target == null ? forward().scale(-1) : flatDir(target.position()).scale(-1);
                setDeltaMovement(away.x * 0.3, getDeltaMovement().y, away.z * 0.3);
                if (actionTicks >= BREAK_TICKS) finishAction(34);
            }
            default -> finishAction(10);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return super.hurtServer(level, source, action == EXPOSED ? damage * EXPOSED_TAKEN : damage);
    }

    /** Test hooks. */
    public void forceTelegraphForTest(LivingEntity target) {
        setTarget(target);
        stalked = 999;
        restTicks = 0;
        aim = flatDir(target.position());
        begin(TELEGRAPH, null);
    }

    public int actionTicksForTest() {
        return actionTicks;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
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
