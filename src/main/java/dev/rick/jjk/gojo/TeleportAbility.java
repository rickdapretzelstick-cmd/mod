package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Limitless, Gojo's Special (JJS). With an enemy under the crosshair he raises a hand and bends space, appearing right in
 * front of them when the glass shatters; turning the camera during the wind-up swings where he materialises around
 * them. Unblockable, reaches ragdolled targets, can be interrupted. On an airborne enemy he appears over them and kicks
 * them to the floor. In the base kit it costs 6% of the Awakening meter.
 *
 * Special variants: pressed during Reversal Red's wind-up (see {@link RedAbility}) and right after Rapid Punches lands —
 * Face Grater: he appears before the opponent, drags them along the floor and tosses them forward (360-blockable,
 * a longer wind-up on someone Rapid Punches didn't catch, heavy endlag if blocked).
 */
public final class TeleportAbility extends Ability {
    public static final String ID = "teleport";

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
        return JJKConfig.get().gojo.limitlessCooldown;
    }

    @Nullable
    private static LivingEntity target(AbilityContext ctx) {
        return Aim.target(ctx.user(), JJKConfig.get().gojo.limitlessRange, JJKConfig.get().teleport.targetAssistAngle, ctx.targetHint());
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        if (GojoState.of(ctx.user()).faceGraterTarget(ctx.level().getGameTime()) != null) return null;
        return target(ctx) == null ? "no_target" : null;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        AbilityCaster caster = ctx.caster();
        if (!caster.isAwakened() && !caster.noCost()) {
            caster.setAwakening(Math.max(0, caster.awakening() - JJKConfig.get().gojo.limitlessMeterCost));
        }
        GojoState gs = GojoState.of(ctx.user());
        LivingEntity punched = gs.faceGraterTarget(ctx.level().getGameTime());
        LivingEntity aimed = target(ctx);
        if (punched != null) {
            gs.punched = null;
            LivingEntity victim = aimed != null ? aimed : punched;
            return new FaceGrater(this, ctx, victim, victim == punched);
        }
        return aimed == null ? null : new Blink(this, ctx, aimed);
    }

    /** The plain Special: hand up, glass shatters, Gojo is there. */
    private static final class Blink extends AbilityInstance {
        private final LivingEntity target;
        private final float startYaw;

        Blink(Ability ability, AbilityContext ctx, LivingEntity target) {
            super(ability, ctx);
            this.target = target;
            this.startYaw = ctx.user().getYRot();
        }

        @Override
        public void start() {
            Anim.play(user, "limitless_raise");
            setPhase(0, JJKConfig.get().gojo.limitlessWindup);
            Fx.play(level, "teleport_out", user.position().add(0, 1, 0), target.position().subtract(user.position()), 0.6f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Gojo cfg = JJKConfig.get().gojo;
            if (age < cfg.limitlessWindup) return;
            if (!target.isAlive() || target.level() != level) {
                finish();
                return;
            }
            boolean air = Combat.isAirborne(target) && !target.onGround();
            float turned = Mth.wrapDegrees(user.getYRot() - startYaw);
            Vec3 dest = air ? above(user, target) : aroundFront(user, target, turned);
            if (dest == null) {
                finish();
                return;
            }
            arrive(level, user, dest, target);
            if (air) {
                // Air variant: over them, and a swift kick back to the floor.
                Anim.play(user, "limitless_air_kick");
                HitResolver.resolve(GojoCombat.strike(user, ID, cfg.limitlessAirKickDamage, true)
                        .knockback(Knockback.set(new Vec3(0, -1.3, 0))).hitstun(16).status(CombatStatus.SPIKED, 10).fx("hit_slam", 1f).build(), target);
            }
            finish();
        }
    }

    /** Face Grater: appears before the opponent, drags them along the floor, tosses them. */
    private static final class FaceGrater extends AbilityInstance {
        private final LivingEntity victim;
        private final int windup;
        private boolean grabbed;
        private int grabAt = -1;
        private int endAt = -1;

        FaceGrater(Ability ability, AbilityContext ctx, LivingEntity victim, boolean fromPunches) {
            super(ability, ctx);
            this.victim = victim;
            this.windup = fromPunches ? 3 : 9;
        }

        @Override
        public void start() {
            Anim.play(user, "limitless_raise");
            setPhase(0, windup);
        }

        @Override
        public void tick() {
            JJKConfig.Gojo cfg = JJKConfig.get().gojo;
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (!victim.isAlive()) {
                finish();
                return;
            }
            if (age == windup) {
                Vec3 dest = aroundFront(user, victim, 0);
                if (dest == null) {
                    finish();
                    return;
                }
                arrive(level, user, dest, victim);
                HitResult r = HitResolver.resolve(GojoCombat.strike(user, "face_grater", 0.5f, false).knockback(Knockback.HOLD)
                        .hitstun(cfg.faceGraterDragTicks + 10).fx("hit_heavy", 0.8f).build(), victim);
                if (!r.connected() || r.outcome() == HitResult.Outcome.BLOCKED) {
                    endAt = age + 30; // blocked: significant endlag
                    return;
                }
                grabbed = true;
                grabAt = age;
                Fx.play(level, "sfx:face_grater_drag", victim.position(), Vec3.ZERO, 1.2f, user.getId());
                Anim.play(user, "rushdown_drag");
                setPhase(1, cfg.faceGraterDragTicks);
                return;
            }
            if (!grabbed) return;
            Vec3 f = HakariCombat.flat(user);
            if (age - grabAt < cfg.faceGraterDragTicks) {
                HakariCombat.drive(user, f, 0.7);
                Statuses.apply(victim, CombatStatus.GRABBED, 3);
                HakariCombat.carry(user, victim, 1.2);
                if (age % 2 == 0) {
                    Fx.play(level, "rushdown_drag", victim.position(), f, 1f, user.getId());
                    if (Destruction.allowed(level)) {
                        Destruction.destroy(level, BlockPos.containing(victim.position().add(f.scale(0.6)).add(0, -0.5, 0)), 2f, user, "jjk:face_grater");
                    }
                }
                return;
            }
            Statuses.remove(victim, CombatStatus.GRABBED);
            Anim.play(user, "rushdown_throw");
            Fx.play(level, "sfx:face_grater_throw", victim.position(), Vec3.ZERO, 1.2f, user.getId());
            HitResolver.resolve(GojoCombat.strike(user, "face_grater", cfg.faceGraterDamage - 0.5f, false)
                    .knockback(Knockback.directional(f, 1.6, 0.6)).hitstun(28).status(CombatStatus.LAUNCHED, 26).fx("hit_launch", 1.3f).build(), victim);
            Fx.shake(level, victim.position(), 20, 0.8f, 10);
            grabbed = false;
            endAt = age + 8;
        }

        @Override
        public void end() {
            Statuses.remove(victim, CombatStatus.GRABBED);
        }
    }

    /** Teleports the user to {@code dest}, facing {@code target}, with the glass-shatter arrival. */
    static void arrive(ServerLevel level, LivingEntity user, Vec3 dest, LivingEntity target) {
        Vec3 from = user.position();
        Vec3 eye = dest.add(0, user.getEyeHeight(), 0);
        Vec3 look = target.getBoundingBox().getCenter().subtract(eye);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        user.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, pitch, false);
        Motion.set(user, Vec3.ZERO);
        user.resetFallDistance();
        Statuses.apply(user, CombatStatus.EVADING, JJKConfig.get().teleport.invulnerabilityTicks);
        if (level.noCollision(user, user.getBoundingBox().move(0, -0.25, 0))) Statuses.apply(user, CombatStatus.HOVER, JJKConfig.get().teleport.airHoverTicks);
        Anim.play(user, "teleport_strike");
        Fx.play(level, "teleport_in", dest.add(0, user.getBbHeight() / 2, 0), dest.subtract(from), 1f, user.getId());
    }

    /**
     * A spot right in front of the target (between it and Gojo), swung {@code turnedDegrees} around it by how far Gojo
     * turned his camera. Falls back to nearby free spots; null if there is none with a clear line to the target.
     */
    @Nullable
    static Vec3 aroundFront(LivingEntity user, LivingEntity target, float turnedDegrees) {
        Vec3 toUser = user.position().subtract(target.position());
        Vec3 dir = new Vec3(toUser.x, 0, toUser.z);
        if (dir.lengthSqr() < 1e-4) dir = HakariCombat.flat(target);
        dir = dir.normalize();
        double dist = target.getBbWidth() / 2 + 1.2;
        for (float extra : new float[] {0, 30, -30, 60, -60, 90, -90, 180}) {
            double a = Math.toRadians(turnedDegrees + extra);
            Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
            Vec3 feet = target.position().add(d.scale(dist));
            if (free(user, target, feet)) return feet;
            if (free(user, target, feet.add(0, 1, 0))) return feet.add(0, 1, 0);
        }
        return null;
    }

    /** Behind the target, facing it (Reversal Red's special variant). */
    @Nullable
    public static Vec3 behindSpot(LivingEntity user, LivingEntity target) {
        return aroundFront(user, target, 180);
    }

    @Nullable
    private static Vec3 above(LivingEntity user, LivingEntity target) {
        Vec3 feet = target.position().add(0, target.getBbHeight() + 0.3, 0);
        return free(user, target, feet) ? feet : aroundFront(user, target, 0);
    }

    private static boolean free(LivingEntity user, LivingEntity target, Vec3 feet) {
        AABB box = user.getDimensions(user.getPose()).makeBoundingBox(feet);
        if (!user.level().isLoaded(BlockPos.containing(feet)) || !user.level().noCollision(user, box)) return false;
        Vec3 eye = feet.add(0, user.getEyeHeight(), 0);
        return user.level().clip(new ClipContext(target.getBoundingBox().getCenter(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user))
                .getType() == Type.MISS;
    }
}
