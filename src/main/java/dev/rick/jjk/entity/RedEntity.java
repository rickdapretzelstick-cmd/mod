package dev.rick.jjk.entity;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Reversal: Red. A fast bolt of repulsion that detonates on contact, blasting everything away and upward.
 * Detonating inside an active Blue collides the two and produces a much larger blast.
 */
public class RedEntity extends TechniqueEntity {
    private Vec3 direction = Vec3.ZERO;
    private float charge;
    private double travelled;
    private boolean max;
    /** Damage override for this orb (Red's "Aka" variant), or -1. */
    private float damageOverride = -1;
    /** Red MAX: a piercing orb (not an explosion) that can rebound to Gojo. */
    private boolean piercing, rebound, returning;
    private final java.util.Set<java.util.UUID> pierced = new java.util.HashSet<>();
    @org.jetbrains.annotations.Nullable private LivingEntity caught;
    private int blocksCarved;

    public RedEntity(EntityType<? extends RedEntity> type, Level level) {
        super(type, level);
    }

    public static RedEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, float charge) {
        return fire(level, owner, from, dir, charge, false);
    }

    public static RedEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, float charge, boolean max) {
        RedEntity e = new RedEntity(ModEntities.RED, level);
        e.setOwner(owner);
        e.setPos(from.x, from.y, from.z);
        e.direction = dir.normalize();
        e.charge = charge;
        e.max = max;
        e.setScale((1f + charge) * (max ? 5f : 1f));
        e.setDeltaMovement(e.direction.scale(JJKConfig.get().red.speed));
        level.addFreshEntity(e);
        return e;
    }

    public RedEntity setDamage(float damage) {
        this.damageOverride = damage;
        return this;
    }

    /** Reversal Red MAX: pierces through everyone in its path for 100 studs; {@code rebound} brings it back to Gojo. */
    public static RedEntity fireMax(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, boolean rebound) {
        RedEntity e = fire(level, owner, from, dir, 0, true);
        e.piercing = true;
        e.rebound = rebound;
        e.setScale(3.5f);
        return e;
    }

    private void tickPiercing(ServerLevel level) {
        JJKConfig.MaxRed cfg = JJKConfig.get().maxRed;
        if (returning && owner != null) {
            Vec3 home = owner.getBoundingBox().getCenter().subtract(position());
            if (home.length() < 1.6 || tickCount > 200) {
                reboundHome(level, cfg);
                discard();
                return;
            }
            direction = home.normalize();
        }
        Vec3 from = position();
        Vec3 to = from.add(direction.scale(cfg.speed));
        if (!level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(to))) {
            // The edge of the simulated world (it would freeze there): a rebounding orb turns back, anything else fades.
            if (rebound && !returning && owner != null) returning = true;
            else discard();
            return;
        }
        // Through a Lapse Blue MAX left lingering after a kill: Unlimited Purple.
        BlueEntity blue = BlueEntity.findNear(level, to, 2.5);
        if (blue != null && blue.isLingering() && blue.owner() == owner) {
            dev.rick.jjk.gojo.UnlimitedPurple.start(level, owner, blue);
            discard();
            return;
        }
        float t = (float) Mth.clamp(travelled / cfg.range, 0, 1);
        float damage = Mth.lerp(t, cfg.nearDamage, cfg.farDamage);
        for (LivingEntity victim : HitboxQuery.targets(owner, HitShape.capsule(from, to, 1.2), 0.2, false)) {
            if (victim == owner || !pierced.add(victim.getUUID())) continue;
            Hit hit = Hit.builder(owner, "max_red").direct(this).type(ModDamageTypes.RED).damage(damage)
                    .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.PROJECTILE, AttackTag.UNBLOCKABLE, AttackTag.ULTIMATE, AttackTag.OTG)
                    .origin(from).knockback(Knockback.directional(direction, 0.9, 0.3)).hitstun(18).status(CombatStatus.LAUNCHED, 16)
                    .fx("red_hit", 1.5f).build();
            if (HitResolver.resolve(hit, victim).connected() && rebound && caught == null) caught = victim;
        }
        // It bores through what's in the way.
        if (Destruction.allowed(level) && blocksCarved < cfg.maxBlocksDestroyed
                && !level.getBlockState(net.minecraft.core.BlockPos.containing(to)).isAir()) {
            blocksCarved += Destruction.sphere(level, to, 1.4, 6f, Math.max(0, cfg.maxBlocksDestroyed - blocksCarved), owner, null, "jjk:max_red");
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(direction.scale(cfg.speed));
        if (!returning) travelled += cfg.speed;
        if (!returning && travelled >= cfg.range) {
            if (rebound && owner != null) {
                returning = true;
                Fx.play(level, "red_full", to, direction.scale(-1), 1.5f, owner.getId());
            } else {
                Fx.play(level, "red_fizzle", to, Vec3.ZERO, 2f, owner != null ? owner.getId() : -1);
                discard();
            }
        }
    }

    /** The rebound reaches Gojo: the target it caught gets a Black Flash; an empty return hits him instead. */
    private void reboundHome(ServerLevel level, JJKConfig.MaxRed cfg) {
        if (owner == null) return;
        if (caught != null && caught.isAlive()) {
            Vec3 front = owner.position().add(dev.rick.jjk.hakari.HakariCombat.flat(owner).scale(1.4));
            caught.teleportTo(front.x, front.y, front.z);
            dev.rick.jjk.core.combat.Statuses.apply(owner, CombatStatus.MELEE_ARMOR, 16);
            dev.rick.jjk.hakari.HakariCombat.faceTowards(owner, caught.getBoundingBox().getCenter());
            dev.rick.jjk.core.anim.Anim.play(owner, "black_flash");
            Fx.play(level, "sfx:red_max_blackflash", owner.position().add(0, 1, 0), Vec3.ZERO, 2f, owner.getId());
            Hit bf = Hit.builder(owner, "max_red").type(ModDamageTypes.MELEE).damage(cfg.blackFlashDamage)
                    .tag(AttackTag.MELEE, AttackTag.UNBLOCKABLE, AttackTag.ULTIMATE).origin(owner.getEyePosition())
                    .knockback(Knockback.directional(dev.rick.jjk.hakari.HakariCombat.flat(owner), 2.0, 0.6)).hitstun(30)
                    .status(CombatStatus.LAUNCHED, 30).fx("finisher", 0.9f).build();
            HitResolver.resolve(bf, caught);
            Fx.shake(level, owner.position(), 30, 1.2f, 14);
        } else {
            Fx.play(level, "red_explosion", owner.getBoundingBox().getCenter(), Vec3.ZERO, 1f, owner.getId());
            owner.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.RED, this, owner), cfg.reboundSelfDamage);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (ownerLost()) {
            discard();
            return;
        }
        if (piercing) {
            tickPiercing(level);
            return;
        }
        JJKConfig.Red cfg = JJKConfig.get().red;
        Vec3 from = position();
        Vec3 step = direction.scale(cfg.speed);
        Vec3 to = from.add(step);
        if (!level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(to))) {
            detonateSelf(level, from);
            discard();
            return;
        }
        // Entities along the swept path.
        HitShape sweep = HitShape.capsule(from, to, (0.6 + 0.3 * charge) * (max ? 4 : 1));
        List<LivingEntity> hits = HitboxQuery.targets(owner, sweep, 0.2, false);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 impact = null;
        if (!hits.isEmpty()) {
            LivingEntity first = hits.getFirst();
            impact = first.getBoundingBox().getCenter();
            if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceToSqr(from) < impact.distanceToSqr(from)) impact = null;
        }
        if (impact == null && block.getType() != HitResult.Type.MISS) impact = block.getLocation().subtract(direction.scale(0.3));
        // Passing through a Blue sets it off.
        BlueEntity blue = BlueEntity.findNear(level, to, 1.8);
        if (impact == null && blue != null && blue.owner() == owner) impact = blue.position();
        if (impact != null) {
            detonateSelf(level, impact);
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(step);
        travelled += cfg.speed;
        if (travelled >= cfg.range) {
            detonateSelf(level, to);
            discard();
        }
    }

    private void detonateSelf(ServerLevel level, Vec3 at) {
        detonate(level, owner, at, charge, this, max, damageOverride);
    }

    /**
     * The repulsion blast. Shared by the projectile and point-blank Red.
     * @param charge 0 = minimum charge, 1 = full charge
     */
    public static void detonate(ServerLevel level, LivingEntity owner, Vec3 pos, float charge, Entity direct) {
        detonate(level, owner, pos, charge, direct, false);
    }

    public static void detonate(ServerLevel level, LivingEntity owner, Vec3 pos, float charge, Entity direct, boolean max) {
        detonate(level, owner, pos, charge, direct, max, -1);
    }

    public static void detonate(ServerLevel level, LivingEntity owner, Vec3 pos, float charge, Entity direct, boolean max, float damageOverride) {
        JJKConfig.Red cfg = JJKConfig.get().red;
        JJKConfig.MaxRed mcfg = JJKConfig.get().maxRed;
        double radius = Mth.lerp(charge, cfg.radius, cfg.chargedRadius) * (max ? mcfg.radiusMultiplier : 1);
        float damage = damageOverride > 0 ? damageOverride : Mth.lerp(charge, cfg.damage, cfg.chargedDamage) * (max ? mcfg.damageMultiplier : 1);
        double knockback = cfg.knockback * (0.8 + 0.4 * charge) * (max ? mcfg.knockbackMultiplier : 1);
        int maxBlocks = max ? mcfg.maxBlocksDestroyed : cfg.maxBlocksDestroyed;
        String fx = max ? "max_red_explosion" : "red_explosion";
        BlueEntity blue = BlueEntity.findNear(level, pos, JJKConfig.get().blue.pullRadius * 0.6);
        if (blue != null && blue.owner() == owner) {
            // Lapse and Reversal collide: everything Blue gathered takes the full, amplified blast.
            float amp = cfg.blueAmplifyMultiplier;
            radius *= amp;
            damage *= amp;
            knockback *= 1.25;
            pos = blue.position();
            blue.consume();
            fx = max ? "max_red_explosion" : "red_amplified";
        }
        float scale = (float) (radius / cfg.radius);
        Fx.play(level, fx, pos, direct.getDeltaMovement().lengthSqr() > 1e-4 ? direct.getDeltaMovement().normalize() : owner.getLookAngle(), scale, owner.getId());
        // Max Red's blast radius scales far past base Red; the camera kick is capped so it stays playable.
        Fx.shake(level, pos, 24 + radius * 2, Math.min(1.6f, 0.9f * scale), 14);
        Fx.flash(level, pos, 10 + radius * 2, max ? 0x40FF3020 : 0x55FF2020, 5);

        final double r = radius;
        Hit hit = Hit.builder(owner, "red").direct(direct).type(ModDamageTypes.RED).damage(damage)
                .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.AREA, AttackTag.EXPLOSION, AttackTag.OTG)
                .origin(pos).knockback(Knockback.radial(pos, knockback, cfg.launch)).hitstun(cfg.hitstun)
                .status(CombatStatus.LAUNCHED, 28).guardDamage(3).tag(max ? AttackTag.ULTIMATE : AttackTag.LIMITLESS).fx("red_hit", 1.2f).build();
        for (LivingEntity t : HitboxQuery.targets(owner, HitShape.sphere(pos, r), 0.2, false)) {
            // Finisher: shattered by the blast.
            if (!max && dev.rick.jjk.gojo.GojoCombat.finishable(t)) {
                dev.rick.jjk.gojo.GojoCombat.execute(owner, t, "red", "red_hit");
                continue;
            }
            // Through a guard: half damage, and it can neither kill nor send them flying.
            if (dev.rick.jjk.core.combat.Combat.state(t).isGuarding()) {
                float half = Math.min(damage * 0.5f, Math.max(0, t.getHealth() - 1));
                HitResolver.resolve(hit.toBuilder().damage(half / Math.max(0.01f, JJKConfig.get().guard.blockedTechniqueDamageScale))
                        .knockback(Knockback.NONE).build(), t);
                continue;
            }
            // Falloff: full power in the core, 60% at the edge.
            double d = t.getBoundingBox().getCenter().distanceTo(pos);
            float f = (float) Mth.clamp(1.0 - 0.4 * d / r, 0.6, 1.0);
            HitResolver.resolve(hit.toBuilder().damage(damage * f).knockback(Knockback.radial(pos, knockback * f, cfg.launch * f)).build(), t);
        }
        // Loose items and falling blocks get thrown too.
        for (Entity e : level.getEntities(direct, new AABB(pos, pos).inflate(r), e -> e instanceof ItemEntity)) {
            Vec3 away = e.position().subtract(pos);
            if (away.lengthSqr() > 1e-4) Motion.set(e, away.normalize().scale(knockback * 0.6).add(0, 0.4, 0));
        }
        if (Destruction.allowed(level)) {
            Destruction.sphere(level, pos, r * 0.55, 6f, maxBlocks, owner, null, max ? "jjk:max_red" : "jjk:red");
        }
    }
}
