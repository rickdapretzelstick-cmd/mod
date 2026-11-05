package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Brood Mother (Grade 3): a stooped, spindly thing dragging a huge pale egg sac in which shapes move. It fights from
 * the back of the room and keeps the swarm coming; kill it first.
 * <ul>
 *   <li><b>Birth</b>: the sac swells and glows (the tell, over a second), then splits and Fly Heads burst out (two at a
 *   time, never more than {@link #MAX_BROOD} alive). The sac hangs empty afterwards and it is <b>exposed</b>.</li>
 *   <li><b>Acid</b>: it rears and lobs a gob of acid at where its prey stands (the spot is marked); it leaves a pool
 *   that burns whoever stands in it for five seconds.</li>
 *   <li><b>Slap</b>: crowd it and it swats with its long arms.</li>
 *   <li>Its brood is its own: when it dies, the Fly Heads it birthed die with it.</li>
 * </ul>
 */
public class BroodMotherEntity extends CommonCurseEntity {
    static final int BIRTH = 1, RECOIL = 2, SPIT = 3, SLAP = 4;
    public static final int BIRTH_TICKS = 24, RECOIL_TICKS = 36, SPIT_FIRE = 14, SPIT_END = 20, SLAP_HIT = 10, SLAP_END = 16,
            BIRTH_COOLDOWN = 180, POOL_TICKS = 100, MAX_BROOD = 3;
    public static final double POOL_RADIUS = 1.8;
    private final Set<UUID> brood = new HashSet<>();
    private int birthCooldown = 40;
    private Vec3 spitAt = Vec3.ZERO;

    private record Pool(Vec3 at, int[] left) {}

    /** A gob in flight toward its spot. */
    private record Gob(Vec3[] pos, Vec3 target, int[] age) {}

    private final List<Pool> pools = new ArrayList<>();
    private final List<Gob> gobs = new ArrayList<>();

    public BroodMotherEntity(EntityType<? extends BroodMotherEntity> type, Level level) {
        super(type, level, CurseGrade.GRADE_3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 34).add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 28).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String curseKind() {
        return "brood_mother";
    }

    @Override
    protected double baseHealth() {
        return 34;
    }

    @Override
    protected float baseDamage() {
        return 4f;
    }

    @Override
    protected boolean windingUp(int a) {
        return a == BIRTH || a == SPIT && actionTicks < SPIT_FIRE || a == SLAP && actionTicks < SLAP_HIT;
    }

    @Override
    protected boolean committing(int a) {
        return a == SPIT || a == SLAP;
    }

    public int broodAlive(ServerLevel level) {
        brood.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
        return brood.size();
    }

    public int poolsForTest() {
        return pools.size();
    }

    @Override
    protected void fight(ServerLevel level, LivingEntity target, double d) {
        turnToward(target.position(), 10f);
        getLookControl().setLookAt(target);
        if (birthCooldown > 0) birthCooldown--;
        if (restTicks <= 0 && birthCooldown <= 0 && broodAlive(level) < MAX_BROOD) {
            startBirth(level);
            return;
        }
        if (restTicks <= 0 && mayCommit(level, target)) {
            if (d <= 2.6) {
                begin(SLAP, "slap");
                sound(level, SoundEvents.SPIDER_AMBIENT, 1f, 0.5f);
                return;
            }
            if (d >= 4 && d <= 14 && hasLineOfSight(target)) {
                startSpit(level, target.position());
                return;
            }
        }
        // It keeps its distance: back off if crowded, close in if far.
        if (d < 5) walkTo(position().add(flatDir(target.position()).scale(-4)), 1.0);
        else if (d > 10) walkTo(target.position(), 0.9);
        else getNavigation().stop();
    }

    private void startBirth(ServerLevel level) {
        begin(BIRTH, "birth");
        birthCooldown = BIRTH_COOLDOWN;
        sound(level, SoundEvents.TURTLE_EGG_CRACK, 1.2f, 0.5f);
        fx(level, "curse_tell", sac(), Vec3.ZERO, 1.2f);
    }

    private void startSpit(ServerLevel level, Vec3 at) {
        begin(SPIT, "spit");
        spitAt = at;
        sound(level, SoundEvents.LLAMA_SPIT, 0.8f, 0.4f);
        fx(level, "curse_tell", position().add(forward().scale(0.6)).add(0, getBbHeight() * 0.8, 0), Vec3.ZERO, 0.8f);
        warnRing(level, at.add(0, 0.05, 0), (float) POOL_RADIUS, SPIT_FIRE + 14);
    }

    /** Test hooks. */
    public void forceBirthForTest() {
        if (level() instanceof ServerLevel level) startBirth(level);
    }

    public void forceSpitForTest(Vec3 at) {
        if (level() instanceof ServerLevel level) startSpit(level, at);
    }

    private Vec3 sac() {
        return position().add(forward().scale(-0.6)).add(0, getBbHeight() * 0.55, 0);
    }

    @Override
    protected void tickAction(ServerLevel level, @Nullable LivingEntity target) {
        switch (action) {
            case BIRTH -> {
                setDeltaMovement(getDeltaMovement().multiply(0.1, 1, 0.1));
                if (actionTicks % 6 == 0) sound(level, SoundEvents.SLIME_SQUISH, 0.8f, 0.4f + actionTicks * 0.02f);
                if (actionTicks >= BIRTH_TICKS) {
                    int n = Math.min(2, MAX_BROOD - broodAlive(level));
                    for (int i = 0; i < n; i++) hatch(level, target, i);
                    fx(level, "curse_spit_burst", sac(), Vec3.ZERO, 2f);
                    fx(level, "fb_manifest", sac(), Vec3.ZERO, 0.6f);
                    sound(level, SoundEvents.SLIME_DEATH, 1.4f, 0.5f);
                    action = RECOIL;
                    actionTicks = 0;
                    Anim.playOn(this, clipPrefix() + "_recoil");
                    expose(level, RECOIL_TICKS);
                }
            }
            case RECOIL -> {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                if (actionTicks >= RECOIL_TICKS) finishAction(30);
            }
            case SPIT -> {
                setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
                turnToward(spitAt, 20f);
                if (actionTicks == SPIT_FIRE) {
                    Vec3 mouth = position().add(forward().scale(0.6)).add(0, getBbHeight() * 0.8, 0);
                    gobs.add(new Gob(new Vec3[] {mouth}, spitAt, new int[] {0}));
                    sound(level, SoundEvents.LLAMA_SPIT, 1.2f, 0.6f);
                }
                if (actionTicks >= SPIT_END) finishAction(36);
            }
            case SLAP -> {
                if (target != null && actionTicks < SLAP_HIT - 3) turnToward(target.position(), 20f);
                if (actionTicks == SLAP_HIT) {
                    Vec3 from = position().add(0, 1.1, 0), f = forward();
                    for (LivingEntity e : HitboxQuery.targets(this, HitShape.capsule(from, from.add(f.scale(2.4)), 1.0), 0.1, true)) {
                        strike(e, Hit.builder(this, "brood_slap").damage(dmg(1f)).tag(AttackTag.MELEE)
                                .knockback(Knockback.directional(f, 0.8, 0.3)).hitstun(10).origin(from).fx("curse_bite", 0.8f));
                    }
                    sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.5f);
                }
                if (actionTicks >= SLAP_END) finishAction(26);
            }
            default -> finishAction(10);
        }
    }

    private void hatch(ServerLevel level, @Nullable LivingEntity target, int i) {
        FlyHeadEntity f = ModEntities.FLY_HEAD.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (f == null) return;
        Vec3 at = sac().add((i == 0 ? 0.6 : -0.6), 0.4, 0);
        f.snapTo(at.x, at.y, at.z, getYRot(), 0);
        f.setGrade(CurseGrade.GRADE_4);
        BlockPos h = home();
        f.setHome(h != null ? h : blockPosition(), 16);
        if (target != null) f.setTarget(target);
        level.addFreshEntity(f);
        brood.add(f.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // Gobs in flight: an arc to their spot, then a pool.
        for (var it = gobs.iterator(); it.hasNext(); ) {
            Gob g = it.next();
            int a = ++g.age()[0];
            Vec3 start = g.pos()[0];
            float u = Math.min(1f, a / 14f);
            Vec3 p = start.lerp(g.target(), u).add(0, 2.5 * 4 * u * (1 - u) * (1 - u * 0.0), 0);
            fx(level, "curse_spit", p, g.target().subtract(start).scale(0.05), 1f);
            if (u >= 1f) {
                it.remove();
                pools.add(new Pool(g.target(), new int[] {POOL_TICKS}));
                fx(level, "curse_spit_burst", g.target().add(0, 0.3, 0), Vec3.ZERO, 1.4f);
                fx(level, "curse_pool", g.target().add(0, 0.05, 0), new Vec3(POOL_TICKS, 0, 0), (float) POOL_RADIUS);
                sound(level, SoundEvents.LAVA_EXTINGUISH, 0.8f, 0.6f);
            }
        }
        for (var it = pools.iterator(); it.hasNext(); ) {
            Pool p = it.next();
            int left = --p.left()[0];
            if (left % 10 == 0) {
                AABB box = new AABB(p.at().add(-POOL_RADIUS, -0.5, -POOL_RADIUS), p.at().add(POOL_RADIUS, 1.5, POOL_RADIUS));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !(e instanceof CommonCurseEntity))) {
                    if (e.position().distanceToSqr(p.at().x, e.getY(), p.at().z) > POOL_RADIUS * POOL_RADIUS) continue;
                    struck.remove(e.getUUID());
                    strike(e, Hit.builder(this, "brood_acid").damage(dmg(0.3f)).tag(AttackTag.AREA).knockback(Knockback.HOLD).hitstun(0)
                            .origin(p.at()).fx("curse_spit_burst", 0.4f));
                }
            }
            if (left <= 0) it.remove();
        }
    }

    @Override
    public void die(DamageSource source) {
        boolean was = dead || isRemoved();
        super.die(source);
        if (was || !(level() instanceof ServerLevel level)) return;
        // Its brood dies with it.
        for (UUID id : brood) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity l && l.isAlive()) l.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        }
        brood.clear();
        pools.clear();
        gobs.clear();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }
}
