package dev.rick.jjk.entity;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.IdleDeathGamble;
import dev.rick.jjk.hakari.ReserveBallsAbility;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One Reserve Ball: a small steel pachinko ball flicked hard enough to hurt. It flies about 65 studs, ricocheting off
 * every surface it meets while it has distance left (inside Hakari's domain it keeps going far longer, like a ball
 * rattling through a pachinko machine). It stuns whoever it hits, or ragdolls them if it hit within 15 studs. It doesn't
 * break blocks.
 */
public class PachinkoBallEntity extends TechniqueEntity {
    private Vec3 velocity = Vec3.ZERO;
    private double travelled;
    private int life;
    private boolean doors;

    public PachinkoBallEntity(EntityType<? extends PachinkoBallEntity> type, Level level) {
        super(type, level);
    }

    /** {@code doors}: Shutter Doors were combined in, so the doors manifest where the ball lands. */
    public static PachinkoBallEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, boolean doors) {
        PachinkoBallEntity e = new PachinkoBallEntity(ModEntities.PACHINKO_BALL, level);
        e.setOwner(owner);
        e.setPos(from.x, from.y, from.z);
        e.velocity = dir.normalize().scale(JJKConfig.get().hakari.ballSpeed);
        e.doors = doors;
        e.setScale(doors ? 1.25f : 1f);
        e.setDeltaMovement(e.velocity);
        level.addFreshEntity(e);
        return e;
    }

    private double range() {
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        var g = owner != null ? IdleDeathGamble.gambleOf(owner) : null;
        DomainInstance d = g != null ? g.domain : null;
        return d != null && d.contains(position()) ? cfg.ballRange * cfg.ballDomainRangeMultiplier : cfg.ballRange;
    }

    private void land(ServerLevel level, @Nullable LivingEntity stunned) {
        if (owner != null) ReserveBallsAbility.landed(level, owner, position(), doors, stunned);
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        if (ownerLost() || ++life > 400) {
            discard();
            return;
        }
        if (travelled >= range()) {
            Fx.play(level, "ball_ricochet", position(), velocity.normalize(), 0.5f, owner.getId());
            land(level, null);
            return;
        }
        velocity = velocity.add(0, -0.02, 0);
        Vec3 from = position(), to = from.add(velocity);
        List<LivingEntity> hits = HitboxQuery.targets(owner, HitShape.capsule(from, to, 0.45 * scale()), 0.2, false);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (!hits.isEmpty()) {
            LivingEntity t = hits.getFirst();
            if (block.getType() == HitResult.Type.MISS || block.getLocation().distanceToSqr(from) >= t.getBoundingBox().getCenter().distanceToSqr(from)) {
                Vec3 dir = velocity.normalize();
                travelled += t.getBoundingBox().getCenter().distanceTo(from);
                // Close range knocks them flat; from further it only stuns them for a moment.
                boolean ragdoll = travelled <= cfg.ballRagdollRange;
                Hit.Builder b = Hit.builder(owner, ReserveBallsAbility.ID).direct(this).type(ModDamageTypes.TECHNIQUE)
                        .damage(cfg.ballDamage).tag(AttackTag.TECHNIQUE, AttackTag.PROJECTILE).origin(from).guardDamage(1);
                if (ragdoll) {
                    b.knockback(Knockback.directional(dir, cfg.ballRagdollKnockback, 0.45)).hitstun(cfg.ballHitstun).status(CombatStatus.LAUNCHED, 18).fx("ball_hit", 1.3f);
                } else {
                    b.knockback(Knockback.HOLD).hitstun(cfg.ballHitstun).fx("ball_hit", 1f);
                }
                boolean connected = HitResolver.resolve(b.build(), t).connected();
                land(level, connected && !ragdoll ? t : null);
                return;
            }
        }
        if (block.getType() != HitResult.Type.MISS) {
            Vec3 at = block.getLocation();
            travelled += at.distanceTo(from);
            // Off the pin: reflect across the surface and keep going while there is distance left.
            Vec3 n = Vec3.atLowerCornerOf(block.getDirection().getUnitVec3i());
            velocity = velocity.subtract(n.scale(2 * velocity.dot(n))).scale(0.92);
            if (velocity.lengthSqr() < 0.05) {
                land(level, null);
                return;
            }
            Fx.play(level, "ball_ricochet", at, velocity.normalize(), 1f, owner.getId());
            setPos(at.x + n.x * 0.1, at.y + n.y * 0.1, at.z + n.z * 0.1);
            setDeltaMovement(velocity);
            return;
        }
        travelled += velocity.length();
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
    }
}
