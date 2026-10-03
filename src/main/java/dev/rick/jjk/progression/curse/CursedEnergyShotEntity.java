package dev.rick.jjk.progression.curse;

import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.entity.TechniqueEntity;
import dev.rick.jjk.progression.CursedSpirit;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Finger Bearer's raw cursed energy in flight: the quick Shot or the slow, heavy Blast. It travels (it can be
 * sidestepped), stops at the first wall (never hits through one), and lands on the first fair target in its path (the
 * owner's {@link Targeting} rule: someone who can't perceive the curse and gave it no reason is passed through). The Blast
 * bursts where it lands, its splash reaching only those in sight of the burst. It is a curse's energy, hidden like the
 * curse ({@link CursedSpirit}), and vanishes with its owner.
 */
public class CursedEnergyShotEntity extends TechniqueEntity implements CursedSpirit {
    public enum Kind {
        SHOT(0.95, 0.45, 26, FingerBearerEntity.SHOT_DAMAGE, 0, 0),
        BLAST(0.5, 0.95, 44, FingerBearerEntity.BLAST_DAMAGE, 2.6, FingerBearerEntity.BLAST_SPLASH);

        final double speed, radius, range, splash;
        final float damage, splashDamage;

        Kind(double speed, double radius, double range, float damage, double splash, float splashDamage) {
            this.speed = speed;
            this.radius = radius;
            this.range = range;
            this.damage = damage;
            this.splash = splash;
            this.splashDamage = splashDamage;
        }
    }

    private Kind kind = Kind.SHOT;
    private Vec3 velocity = Vec3.ZERO;
    private double travelled;
    private boolean done;

    public CursedEnergyShotEntity(EntityType<? extends CursedEnergyShotEntity> type, Level level) {
        super(type, level);
    }

    public static CursedEnergyShotEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, Kind kind) {
        CursedEnergyShotEntity e = new CursedEnergyShotEntity(ModEntities.CURSED_ENERGY_SHOT, level);
        e.setOwner(owner);
        e.kind = kind;
        e.setScale((float) kind.radius);
        e.setPhase(kind.ordinal());
        e.setPos(from.x, from.y, from.z);
        e.velocity = (dir.lengthSqr() < 1e-6 ? owner.getLookAngle() : dir.normalize()).scale(kind.speed);
        e.setDeltaMovement(e.velocity);
        level.addFreshEntity(e);
        return e;
    }

    public Kind kind() {
        return phase() == 1 ? Kind.BLAST : Kind.SHOT;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || done) return;
        if (ownerLost()) {
            // Its curse died or left: the energy goes with it, harmlessly.
            Fx.play(level, "fb_fizzle", position(), Vec3.ZERO, (float) kind.radius, ownerId());
            discard();
            return;
        }
        Vec3 from = position(), to = from.add(velocity);
        if (travelled >= kind.range || !level.isPositionEntityTicking(BlockPos.containing(to))) {
            land(level, from, null);
            return;
        }
        // The wall first: nothing behind it can be hit this tick.
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : block.getLocation();
        List<LivingEntity> hits = HitboxQuery.targets(owner, HitShape.capsule(from, end, kind.radius), 0.1, false);
        if (!hits.isEmpty()) {
            land(level, hits.getFirst().getBoundingBox().getCenter(), hits.getFirst());
            return;
        }
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            land(level, block.getLocation().subtract(velocity.normalize().scale(0.2)), null);
            return;
        }
        travelled += velocity.length();
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
    }

    private void land(ServerLevel level, Vec3 at, LivingEntity direct) {
        done = true;
        boolean landed = false;
        Vec3 dir = velocity.normalize();
        if (direct != null) {
            HitResult r = HitResolver.resolve(Hit.builder(owner, kind == Kind.BLAST ? "fb_blast" : "fb_shot").direct(this)
                    .type(ModDamageTypes.TECHNIQUE).damage(FingerBearerEntity.dmg(kind.damage)).tag(AttackTag.PROJECTILE, AttackTag.TECHNIQUE)
                    .knockback(Knockback.directional(dir, kind == Kind.BLAST ? 1.2 : 0.55, kind == Kind.BLAST ? 0.4 : 0.15))
                    .hitstun(kind == Kind.BLAST ? 12 : 6).guardDamage(kind == Kind.BLAST ? 3 : 1).origin(position()).noComboScaling()
                    .fx("fb_impact", kind == Kind.BLAST ? 1.5f : 0.9f).build(), direct);
            landed = r.outcome() != HitResult.Outcome.INVALID && r.outcome() != HitResult.Outcome.WHIFF;
        }
        if (kind.splash > 0) {
            // The blast bursts: everyone else close enough and in sight of it is caught at the edge.
            List<LivingEntity> caught = HitboxQuery.query(level, HitShape.sphere(at, kind.splash), 0.1,
                    e -> e != direct && Targeting.canTarget(owner, e) && HitboxQuery.hasLineOfSight(level, at, e));
            for (LivingEntity e : caught) {
                HitResult r = HitResolver.resolve(Hit.builder(owner, "fb_blast_splash").direct(this).type(ModDamageTypes.TECHNIQUE)
                        .damage(FingerBearerEntity.dmg(kind.splashDamage)).tag(AttackTag.TECHNIQUE, AttackTag.AREA, AttackTag.EXPLOSION)
                        .knockback(Knockback.radial(at, 0.9, 0.35)).hitstun(6).origin(at).noComboScaling().fx("fb_impact", 0.8f).build(), e);
                landed |= r.outcome() == HitResult.Outcome.HIT || r.outcome() == HitResult.Outcome.BLOCKED;
            }
            Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.9f);
            Fx.shake(level, at, 14, 0.5f, 8);
        }
        Fx.play(level, kind == Kind.BLAST ? "fb_blast_land" : "fb_shot_land", at, dir, (float) Math.max(kind.radius, kind.splash), ownerId());
        if (landed && owner instanceof FingerBearerEntity bearer && kind == Kind.BLAST) bearer.blastLanded();
        discard();
    }
}
