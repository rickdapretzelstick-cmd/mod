package dev.rick.jjk.entity;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuji.OpenAbility;
import dev.rick.jjk.yuji.YujiCombat;
import net.minecraft.core.BlockPos;
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
 * Open's arrow of fire: fast and straight, a burning trail behind it. The first thing it meets (someone, a surface, or
 * the end of its range) is where the pillar of fire goes up.
 */
public class FireArrowEntity extends TechniqueEntity {
    private Vec3 velocity = Vec3.ZERO;
    private double travelled;

    public FireArrowEntity(EntityType<? extends FireArrowEntity> type, Level level) {
        super(type, level);
    }

    public static FireArrowEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 dir) {
        FireArrowEntity e = new FireArrowEntity(ModEntities.FIRE_ARROW, level);
        e.setOwner(owner);
        e.setPos(from.x, from.y, from.z);
        e.velocity = dir.normalize().scale(YujiCombat.cfg().openSpeed);
        e.setDeltaMovement(e.velocity);
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
        Vec3 from = position(), to = from.add(velocity);
        if (travelled >= YujiCombat.cfg().openRange || !level.isPositionEntityTicking(BlockPos.containing(to))) {
            burst(level, from);
            return;
        }
        List<LivingEntity> hits = HitboxQuery.targets(owner, HitShape.capsule(from, to, 0.8), 0.3, false);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (!hits.isEmpty()) {
            burst(level, hits.getFirst().position());
            return;
        }
        if (block.getType() != HitResult.Type.MISS) {
            burst(level, block.getLocation());
            return;
        }
        if (tickCount % 2 == 0) Fx.play(level, "open_trail", from, velocity.normalize(), 1f, owner.getId());
        travelled += velocity.length();
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
    }

    private void burst(ServerLevel level, Vec3 at) {
        OpenAbility.pillar(level, owner, at);
        discard();
    }
}
