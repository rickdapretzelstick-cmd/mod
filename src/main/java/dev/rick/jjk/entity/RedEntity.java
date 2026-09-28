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

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (ownerLost()) {
            discard();
            return;
        }
        JJKConfig.Red cfg = JJKConfig.get().red;
        Vec3 from = position();
        Vec3 step = direction.scale(cfg.speed);
        Vec3 to = from.add(step);
        if (!level.isLoaded(net.minecraft.core.BlockPos.containing(to))) {
            detonate(level, owner, from, charge, this, max);
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
            detonate(level, owner, impact, charge, this, max);
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(step);
        travelled += cfg.speed;
        if (travelled >= cfg.range) {
            detonate(level, owner, to, charge, this, max);
            discard();
        }
    }

    /**
     * The repulsion blast. Shared by the projectile and point-blank Red.
     * @param charge 0 = minimum charge, 1 = full charge
     */
    public static void detonate(ServerLevel level, LivingEntity owner, Vec3 pos, float charge, Entity direct) {
        detonate(level, owner, pos, charge, direct, false);
    }

    public static void detonate(ServerLevel level, LivingEntity owner, Vec3 pos, float charge, Entity direct, boolean max) {
        JJKConfig.Red cfg = JJKConfig.get().red;
        JJKConfig.MaxRed mcfg = JJKConfig.get().maxRed;
        double radius = Mth.lerp(charge, cfg.radius, cfg.chargedRadius) * (max ? mcfg.radiusMultiplier : 1);
        float damage = Mth.lerp(charge, cfg.damage, cfg.chargedDamage) * (max ? mcfg.damageMultiplier : 1);
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
