package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Gojo's teleport. With an enemy near the crosshair he appears behind them, facing them, at their height (so a
 * launched target can be chased into the air). Otherwise he blinks in the direction of his movement input
 * (or where he's looking), stopping short of anything solid. He never lands inside blocks or passes through walls;
 * if there's nowhere to go nothing happens and nothing is spent.
 *
 * Uses charges, and can be used mid-cast (e.g. repositioning while charging Red).
 */
public final class TeleportAbility extends Ability {
    public static final String ID = "teleport";

    public record Plan(Vec3 position, float yaw, float pitch, @Nullable LivingEntity target) {}

    public TeleportAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().teleport.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().teleport.rechargeTicks;
    }

    @Override
    public int maxCharges(AbilityCaster caster) {
        return JJKConfig.get().teleport.charges;
    }

    @Override
    public int minInterval(AbilityCaster caster) {
        return JJKConfig.get().teleport.minInterval;
    }

    @Override
    public boolean usableWhileCasting() {
        return true;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return plan(ctx) == null ? "no_destination" : null;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        Plan plan = plan(ctx);
        LivingEntity user = ctx.user();
        if (plan == null) return null;
        JJKConfig.Teleport cfg = JJKConfig.get().teleport;
        Vec3 from = user.position();
        Fx.play(ctx.level(), "teleport_out", from.add(0, user.getBbHeight() / 2, 0), plan.position().subtract(from), 1f, user.getId());
        user.teleportTo(ctx.level(), plan.position().x, plan.position().y, plan.position().z, Set.of(), plan.yaw(), plan.pitch(), false);
        Motion.set(user, Vec3.ZERO);
        user.resetFallDistance();
        Statuses.apply(user, CombatStatus.EVADING, cfg.invulnerabilityTicks);
        // Nothing underfoot: hang in the air briefly so an aerial chase can continue.
        if (ctx.level().noCollision(user, user.getBoundingBox().move(0, -0.25, 0))) {
            Statuses.apply(user, CombatStatus.HOVER, cfg.airHoverTicks);
        }
        Anim.play(user, plan.target() != null ? "teleport_strike" : "teleport");
        Fx.play(ctx.level(), "teleport_in", plan.position().add(0, user.getBbHeight() / 2, 0), plan.position().subtract(from), 1f, user.getId());
        return null;
    }

    @Nullable
    public static Plan plan(AbilityContext ctx) {
        JJKConfig.Teleport cfg = JJKConfig.get().teleport;
        LivingEntity user = ctx.user();
        LivingEntity target = Aim.target(user, cfg.targetRange, cfg.targetAssistAngle, ctx.targetHint());
        if (target != null) {
            Plan p = behind(ctx, user, target);
            if (p != null) return p;
        }
        Vec3 dir = ctx.hasMovementInput() ? ctx.inputDirection() : user.getLookAngle();
        if (ctx.hasMovementInput() && Combat.isAirborne(user)) {
            // In the air, blink follows the camera's pitch so Gojo can go up or down.
            dir = new Vec3(dir.x, user.getLookAngle().y, dir.z);
        }
        dir = dir.normalize();
        for (double d = cfg.blinkDistance; d >= 1.5; d -= 0.5) {
            Vec3 feet = user.position().add(dir.scale(d));
            if (safe(ctx, user, feet)) return new Plan(feet, user.getYRot(), user.getXRot(), null);
        }
        return null;
    }

    @Nullable
    private static Plan behind(AbilityContext ctx, LivingEntity user, LivingEntity target) {
        Vec3 toTarget = target.position().subtract(user.position());
        Vec3 dir = new Vec3(toTarget.x, 0, toTarget.z);
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(user.getLookAngle().x, 0, user.getLookAngle().z);
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double behindDist = target.getBbWidth() / 2 + 1.1;
        Vec3 behind = target.position().add(dir.scale(behindDist));
        Vec3[] candidates = {behind, behind.add(side), behind.subtract(side), target.position().add(side.scale(behindDist)),
                target.position().subtract(side.scale(behindDist)), behind.add(0, 1, 0), target.position().subtract(dir.scale(behindDist))};
        for (Vec3 feet : candidates) {
            if (!safeSpot(ctx, user, feet)) continue;
            Vec3 eye = feet.add(0, user.getEyeHeight(), 0);
            if (ctx.level().clip(new ClipContext(target.getBoundingBox().getCenter(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)).getType() != HitResult.Type.MISS) continue;
            Vec3 look = target.getBoundingBox().getCenter().subtract(eye);
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
            return new Plan(feet, yaw, pitch, target);
        }
        return null;
    }

    private static boolean safeSpot(AbilityContext ctx, LivingEntity user, Vec3 feet) {
        AABB box = user.getDimensions(user.getPose()).makeBoundingBox(feet);
        return ctx.level().isLoaded(net.minecraft.core.BlockPos.containing(feet)) && ctx.level().noCollision(user, box);
    }

    /** Free landing spot with an unobstructed path from the eyes. */
    private static boolean safe(AbilityContext ctx, LivingEntity user, Vec3 feet) {
        if (!safeSpot(ctx, user, feet)) return false;
        Vec3 eye = user.getEyePosition();
        Vec3 destEye = feet.add(0, user.getEyeHeight(), 0);
        Vec3 destMid = feet.add(0, user.getBbHeight() * 0.3, 0);
        Vec3 mid = user.position().add(0, user.getBbHeight() * 0.3, 0);
        return ctx.level().clip(new ClipContext(eye, destEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)).getType() == HitResult.Type.MISS
                && ctx.level().clip(new ClipContext(mid, destMid, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)).getType() == HitResult.Type.MISS;
    }
}
