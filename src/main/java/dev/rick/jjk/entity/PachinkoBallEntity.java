package dev.rick.jjk.entity;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One of Hakari's Reserve Balls: a steel pachinko ball flung hard enough to hurt. It flies almost straight, bounces
 * off the first surface it meets like a ball off a pachinko pin, and stops at the second. It doesn't break blocks.
 */
public class PachinkoBallEntity extends TechniqueEntity {
    private Vec3 velocity = Vec3.ZERO;
    private int life;
    private int bounces;
    private boolean last;

    public PachinkoBallEntity(EntityType<? extends PachinkoBallEntity> type, Level level) {
        super(type, level);
    }

    public static PachinkoBallEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, boolean last) {
        PachinkoBallEntity e = new PachinkoBallEntity(ModEntities.PACHINKO_BALL, level);
        e.setOwner(owner);
        e.setPos(from.x, from.y, from.z);
        e.velocity = dir.normalize().scale(JJKConfig.get().hakari.ballSpeed);
        e.last = last;
        e.setScale(last ? 1.35f : 1f);
        e.setDeltaMovement(e.velocity);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        if (ownerLost() || ++life > cfg.ballLife) {
            discard();
            return;
        }
        velocity = velocity.add(0, -0.03, 0);
        Vec3 from = position(), to = from.add(velocity);
        List<LivingEntity> hits = HitboxQuery.targets(owner, HitShape.capsule(from, to, 0.45 * scale()), 0.2, false);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (!hits.isEmpty()) {
            LivingEntity t = hits.getFirst();
            if (block.getType() == HitResult.Type.MISS || block.getLocation().distanceToSqr(from) >= t.getBoundingBox().getCenter().distanceToSqr(from)) {
                Vec3 dir = velocity.normalize();
                HitResolver.resolve(Hit.builder(owner, "reserve_balls").direct(this).type(ModDamageTypes.TECHNIQUE)
                        .damage(last ? cfg.lastBallDamage : cfg.ballDamage).tag(AttackTag.TECHNIQUE, AttackTag.PROJECTILE)
                        .origin(from).knockback(Knockback.directional(dir, last ? cfg.lastBallKnockback : cfg.ballKnockback, last ? 0.25 : 0.1))
                        .hitstun(cfg.ballHitstun).guardDamage(1).fx("ball_hit", last ? 1.3f : 1f).build(), t);
                discard();
                return;
            }
        }
        if (block.getType() != HitResult.Type.MISS) {
            Vec3 at = block.getLocation();
            if (bounces++ >= 1) {
                Fx.play(level, "ball_ricochet", at, velocity.normalize(), 0.7f, owner.getId());
                discard();
                return;
            }
            // Off the pin: reflect across the surface and lose some speed.
            Vec3 n = Vec3.atLowerCornerOf(block.getDirection().getUnitVec3i());
            velocity = velocity.subtract(n.scale(2 * velocity.dot(n))).scale(0.65);
            Fx.play(level, "ball_ricochet", at, velocity.normalize(), 1f, owner.getId());
            setPos(at.x + n.x * 0.1, at.y + n.y * 0.1, at.z + n.z * 0.1);
            setDeltaMovement(velocity);
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
    }
}
