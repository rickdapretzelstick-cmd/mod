package dev.rick.jjk.core.hitbox;

import dev.rick.jjk.core.combat.Targeting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds entities overlapping a {@link HitShape}. Broad phase uses the level's entity section lookup on the
 * shape's bounds; narrow phase tests each candidate's hitbox exactly.
 *
 * Reliability: a candidate's box is swept from its previous tick position to its current one, so fast
 * movers can't tunnel through a hitbox between ticks.
 */
public final class HitboxQuery {
    private HitboxQuery() {}

    public static List<LivingEntity> query(Level level, HitShape shape, Predicate<LivingEntity> filter) {
        return query(level, shape, 0, filter);
    }

    public static List<LivingEntity> query(Level level, HitShape shape, double tolerance, Predicate<LivingEntity> filter) {
        AABB broad = shape.bounds().inflate(1.0 + tolerance);
        return level.getEntitiesOfClass(LivingEntity.class, broad, e -> filter.test(e) && overlaps(shape, e, tolerance));
    }

    /** Legal targets for {@code attacker} inside the shape, nearest first. */
    public static List<LivingEntity> targets(LivingEntity attacker, HitShape shape, double tolerance, boolean requireLineOfSight) {
        Vec3 from = attacker.getEyePosition();
        List<LivingEntity> list = query(attacker.level(), shape, tolerance,
                e -> Targeting.canTarget(attacker, e) && (!requireLineOfSight || hasLineOfSight(attacker.level(), from, e)));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(attacker)));
        return list;
    }

    public static boolean overlaps(HitShape shape, LivingEntity e, double tolerance) {
        AABB box = e.getBoundingBox().inflate(e.getPickRadius() + tolerance);
        if (shape.intersects(box)) return true;
        Vec3 motion = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        if (motion.lengthSqr() > 0.04) return shape.intersects(box.expandTowards(motion.reverse()));
        return false;
    }

    /** True if any of the entity's eye, center or feet can be seen from {@code from}. */
    public static boolean hasLineOfSight(Level level, Vec3 from, LivingEntity e) {
        Vec3[] points = {e.getEyePosition(), e.getBoundingBox().getCenter(), e.position().add(0, 0.2, 0)};
        for (Vec3 p : points) {
            if (level.clip(new ClipContext(from, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
