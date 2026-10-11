package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.core.combat.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The rifle's one aiming solution, shared by every precision shot (Snap Shot, Aimed Shot, the volley) and the beam.
 *
 * <ol>
 *   <li>The <b>aim</b> is the shooter's view: from the eye along their look (the crosshair, and the scope's centre, are
 *   exactly this). No sway, no spread, nothing from the rifle model or the animation.</li>
 *   <li>That ray finds the <b>aim point</b>: the first living thing or block it meets, else a point at full range.</li>
 *   <li>The <b>muzzle</b> is only where the round is seen to leave: the visible shot runs from it to the aim point.</li>
 *   <li>If something stands between the muzzle and the aim point (a wall the eye sees past, a muzzle pressed into a
 *   block), the round stops there instead: a rifle never shoots through what its barrel is blocked by.</li>
 * </ol>
 */
public final class RifleAim {
    private RifleAim() {}

    /** One shot's aiming, as the server decides it. */
    public record Solution(Vec3 eye, Vec3 dir, Vec3 muzzle, Vec3 point, @Nullable LivingEntity target, boolean struck, boolean blocked) {
        /** The visible shot's direction: from the muzzle to where it lands. */
        public Vec3 visualDir() {
            Vec3 d = point.subtract(muzzle);
            return d.lengthSqr() < 1e-8 ? dir : d.normalize();
        }
    }

    /** The aim: the view direction, exactly. */
    public static Vec3 direction(LivingEntity p) {
        return Vec3.directionFromRotation(p.getXRot(), p.getYRot());
    }

    public static Solution solve(ServerLevel level, ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), dir = direction(p);
        Vec3 far = eye.add(dir.scale(range));
        var clip = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = clip.getType() == HitResult.Type.MISS ? range : clip.getLocation().distanceTo(eye);
        LivingEntity target = firstAlong(level, p, eye, dir, reach);
        double at = target == null ? reach
                : target.getBoundingBox().inflate(0.25).clip(eye, eye.add(dir.scale(reach))).map(v -> v.distanceTo(eye)).orElse(reach);
        Vec3 point = eye.add(dir.scale(at));
        boolean struck = target != null || clip.getType() != HitResult.Type.MISS;
        Vec3 muzzle = RifleServer.muzzle(p, dir);
        // The barrel's own way to the point: the eye to the muzzle (a muzzle inside a wall), then the muzzle onward.
        var inWall = level.clip(new ClipContext(eye, muzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (inWall.getType() != HitResult.Type.MISS) {
            // The muzzle is inside what it's pressed against: the round strikes that, from there.
            Vec3 stop = inWall.getLocation();
            return new Solution(eye, dir, stop, stop, null, true, true);
        }
        var cover = level.clip(new ClipContext(muzzle, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (cover.getType() != HitResult.Type.MISS && cover.getLocation().distanceToSqr(point) > 0.3 * 0.3) {
            return new Solution(eye, dir, muzzle, cover.getLocation(), null, true, true);
        }
        return new Solution(eye, dir, muzzle, point, target, struck, false);
    }

    /** The first living thing along {@code dir} before {@code reach}. */
    @Nullable
    static LivingEntity firstAlong(ServerLevel level, ServerPlayer p, Vec3 from, Vec3 dir, double reach) {
        Vec3 to = from.add(dir.scale(reach));
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
                e -> e != p && e.isAlive() && !e.isSpectator() && Targeting.canTarget(p, e));
        for (LivingEntity e : near) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.25).clip(from, to);
            if (hit.isEmpty()) continue;
            double t = hit.get().distanceToSqr(from);
            if (t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }
}
