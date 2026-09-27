package dev.rick.jjk.util;

import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Crosshair targeting with a small aim-assist cone. */
public final class Aim {
    private Aim() {}

    public record Target(Vec3 point, @Nullable LivingEntity entity, boolean hitBlock) {}

    /**
     * Picks the legal target nearest the crosshair within {@code range} and {@code assistDegrees}, with line of sight.
     * {@code hint} is the client's crosshair entity, preferred when valid.
     */
    @Nullable
    public static LivingEntity target(LivingEntity user, double range, double assistDegrees, @Nullable Entity hint) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        if (hint instanceof LivingEntity h && Targeting.canTarget(user, h) && h.getBoundingBox().getCenter().distanceTo(eye) <= range + 2
                && HitboxQuery.hasLineOfSight(user.level(), eye, h)) {
            return h;
        }
        double cos = Math.cos(Math.toRadians(assistDegrees));
        LivingEntity best = null;
        double bestScore = -1;
        AABB area = new AABB(eye, eye.add(look.scale(range))).inflate(range * Math.tan(Math.toRadians(assistDegrees)) + 2);
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, area, e -> Targeting.canTarget(user, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1e-3) continue;
            // Allow a wider angle for big or close targets: compare against the angular size of the hitbox too.
            double angularSlack = Math.atan2(e.getBbWidth(), dist);
            double dot = to.dot(look) / dist;
            double threshold = Math.cos(Math.min(Math.PI / 2, Math.acos(cos) + angularSlack));
            if (dot < threshold) continue;
            double score = dot - dist * 0.002;
            if (score > bestScore && HitboxQuery.hasLineOfSight(user.level(), eye, e)) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    /** Where the crosshair lands: a target entity, a block surface, or the max-range point. */
    public static Target point(LivingEntity user, double range, double assistDegrees, @Nullable Entity hint) {
        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getLookAngle().scale(range));
        BlockHitResult block = user.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        double blockDist = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        LivingEntity e = target(user, Math.min(range, blockDist + 1.0), assistDegrees, hint);
        if (e != null) return new Target(e.getBoundingBox().getCenter(), e, false);
        if (block.getType() != HitResult.Type.MISS) {
            return new Target(block.getLocation().subtract(user.getLookAngle().scale(0.6)), null, true);
        }
        return new Target(end, null, false);
    }
}
