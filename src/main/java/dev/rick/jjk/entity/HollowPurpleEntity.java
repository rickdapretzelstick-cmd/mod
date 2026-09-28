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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hollow Purple: imaginary mass that erases what it passes through. Moves in a straight line, erasing terrain inside its
 * radius (within destruction limits) and hitting each entity once with heavy, unblockable damage that ignores Infinity.
 * Ends in a final detonation when it reaches its range, runs out of destruction budget or reaches unloaded chunks.
 */
public class HollowPurpleEntity extends TechniqueEntity {
    private Vec3 direction = Vec3.ZERO;
    private double radius;
    private double travelled;
    private int blocksDestroyed;
    private final Set<UUID> hit = new HashSet<>();
    private boolean ended;

    public HollowPurpleEntity(EntityType<? extends HollowPurpleEntity> type, Level level) {
        super(type, level);
    }

    public static HollowPurpleEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir, double radius) {
        HollowPurpleEntity e = new HollowPurpleEntity(ModEntities.HOLLOW_PURPLE, level);
        e.setOwner(owner);
        e.setPos(from.x, from.y, from.z);
        e.direction = dir.normalize();
        e.radius = radius;
        e.setScale((float) radius);
        e.setDeltaMovement(e.direction.scale(JJKConfig.get().purple.speed));
        level.addFreshEntity(e);
        return e;
    }

    public double radius() {
        return radius;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || ended) return;
        if (ownerLost()) {
            // The technique outlives a dead caster for its flight, but not a caster that left the world.
            if (owner == null || owner.isRemoved() && !owner.isDeadOrDying()) {
                discard();
                return;
            }
        }
        JJKConfig.Purple cfg = JJKConfig.get().purple;
        // Accelerates out of the caster's hands.
        double speed = cfg.speed * Math.min(1.0, 0.35 + tickCount * 0.12);
        Vec3 from = position();
        Vec3 to = from.add(direction.scale(speed));
        if (!level.isLoaded(BlockPos.containing(to))) {
            end(level, from, cfg);
            return;
        }

        Hit base = Hit.builder(owner, "hollow_purple").direct(this).type(ModDamageTypes.HOLLOW_PURPLE).damage(cfg.damage)
                .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.BYPASS_INFINITY, AttackTag.UNBLOCKABLE, AttackTag.OTG, AttackTag.ULTIMATE)
                .origin(from).hitstun(30).status(CombatStatus.LAUNCHED, 30).noComboScaling().fx("purple_hit", 1.5f).build();
        for (LivingEntity t : HitboxQuery.targets(owner, HitShape.capsule(from, to, radius), 0.3, false)) {
            if (!hit.add(t.getUUID())) continue;
            Vec3 away = t.getBoundingBox().getCenter().subtract(from);
            Vec3 side = away.subtract(direction.scale(away.dot(direction)));
            Vec3 kb = direction.scale(cfg.knockback).add(side.lengthSqr() > 1e-4 ? side.normalize().scale(cfg.knockback * 0.35) : Vec3.ZERO).add(0, 0.5, 0);
            HitResolver.resolve(base.toBuilder().knockback(Knockback.set(kb)).build(), t);
            Fx.shake(level, t.position(), 20, 1.0f, 10);
        }

        if (cfg.destroysBlocks && blocksDestroyed < cfg.maxBlocksDestroyed) {
            // Erase along the path in ~1 block steps so fast movement leaves no gaps.
            int steps = Math.max(1, (int) Math.ceil(speed));
            for (int i = 1; i <= steps && blocksDestroyed < cfg.maxBlocksDestroyed; i++) {
                Vec3 p = from.add(direction.scale(speed * i / steps));
                blocksDestroyed += Destruction.sphere(level, p, radius, 60f, cfg.maxBlocksDestroyed - blocksDestroyed, owner, null);
            }
        }

        setPos(to.x, to.y, to.z);
        setDeltaMovement(direction.scale(speed));
        travelled += speed;
        if (tickCount % 2 == 0) Fx.play(level, "purple_trail", to, direction, (float) radius, getId());
        if (travelled >= cfg.range || cfg.destroysBlocks && blocksDestroyed >= cfg.maxBlocksDestroyed) end(level, to, cfg);
    }

    private void end(ServerLevel level, Vec3 pos, JJKConfig.Purple cfg) {
        ended = true;
        Fx.play(level, "purple_end", pos, direction, (float) radius, getId());
        Fx.shake(level, pos, 40, 1.2f, 16);
        Fx.flash(level, pos, 30, 0x88B040FF, 8);
        Hit impact = Hit.builder(owner, "hollow_purple_impact").direct(this).type(ModDamageTypes.HOLLOW_PURPLE).damage(cfg.impactDamage)
                .tag(AttackTag.TECHNIQUE, AttackTag.LIMITLESS, AttackTag.BYPASS_INFINITY, AttackTag.AREA, AttackTag.EXPLOSION, AttackTag.OTG, AttackTag.ULTIMATE)
                .origin(pos).knockback(Knockback.radial(pos, cfg.knockback * 0.7, 0.8)).hitstun(24).status(CombatStatus.LAUNCHED, 24)
                .noComboScaling().fx("purple_hit", 1.2f).build();
        for (LivingEntity t : HitboxQuery.targets(owner, HitShape.sphere(pos, cfg.impactRadius), 0.3, false)) {
            if (hit.contains(t.getUUID())) continue;
            HitResolver.resolve(impact, t);
        }
        discard();
    }
}
