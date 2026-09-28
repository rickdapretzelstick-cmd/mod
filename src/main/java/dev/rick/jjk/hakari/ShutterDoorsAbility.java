package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.entity.HakariDoorEntity;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Aim;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 2 — Shutter Doors. Two steel shutter doors burst up out of the ground on either side of the target and slam together
 * on them: doors appear → the attack connects → the doors grind open and sink away. It is a pressure tool from range:
 * whoever is caught is pinned between the doors long enough for Hakari to close in.
 */
public final class ShutterDoorsAbility extends Ability {
    public static final String ID = "shutter_doors";

    public ShutterDoorsAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.shutterCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.shutterCooldown;
    }

    /** The ground under a point (the doors rise out of it). */
    static Vec3 ground(net.minecraft.world.level.Level level, Vec3 p) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(p.add(0, 0.5, 0), p.add(0, -6, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? p : hit.getLocation();
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        Aim.Target aim = Aim.point(ctx.user(), cfg.shutterRange, 12, ctx.targetHint());
        LivingEntity target = aim.entity();
        Vec3 spot = target != null ? target.position() : ground(ctx.level(), aim.point());
        return new AbilityInstance(this, ctx) {
            private HakariDoorEntity left, right;
            private Vec3 center = spot;
            private Vec3 across;
            private float yaw;
            private boolean slammed;

            @Override
            public void start() {
                Anim.play(user, "shutter_sign");
                // Doors stand across the line from Hakari to the target and close sideways onto it.
                Vec3 toward = center.subtract(user.position());
                Vec3 f = new Vec3(toward.x, 0, toward.z);
                f = f.lengthSqr() < 1e-4 ? HakariCombat.flat(user) : f.normalize();
                across = new Vec3(-f.z, 0, f.x);
                yaw = (float) (Mth.atan2(f.z, f.x) * Mth.RAD_TO_DEG) - 90f;
                int life = cfg.shutterRiseTicks + cfg.shutterCloseTicks + cfg.shutterHoldTicks + 12;
                left = HakariDoorEntity.spawn(level, user, HakariDoorEntity.SHUTTER, doorPos(-1, 0), yaw, life);
                right = HakariDoorEntity.spawn(level, user, HakariDoorEntity.SHUTTER, doorPos(1, 0), yaw, life);
                setPhase(0, life);
                Fx.play(level, "shutter_appear", center, across, 1f, user.getId());
            }

            /** Where a door stands: {@code side} of the center, {@code close} 0 = wide open .. 1 = shut. */
            private Vec3 doorPos(int side, float close) {
                double gap = Mth.lerp(close, 2.4, 0.55);
                // Rising out of the ground during the first ticks.
                double rise = Math.min(1, age / (double) Math.max(1, cfg.shutterRiseTicks));
                double sink = slammed ? Math.max(0, (age - cfg.shutterRiseTicks - cfg.shutterCloseTicks - cfg.shutterHoldTicks) / 8.0) : 0;
                return center.add(across.scale(side * gap)).add(0, -2.6 * (1 - rise) - 2.6 * Math.min(1, sink), 0);
            }

            @Override
            public void tick() {
                // A pinned target keeps the doors on them.
                if (target != null && target.isAlive() && !slammed) center = target.position();
                int rise = cfg.shutterRiseTicks, close = cfg.shutterCloseTicks, hold = cfg.shutterHoldTicks;
                float c = age < rise ? 0 : Math.min(1, (age - rise) / (float) close);
                if (slammed && age > rise + close + hold) c = Math.max(0, 1 - (age - rise - close - hold) / 6f);
                if (left != null && !left.isRemoved()) left.place(doorPos(-1, c), yaw);
                if (right != null && !right.isRemoved()) right.place(doorPos(1, c), yaw);
                if (!slammed && age >= rise + close) {
                    slammed = true;
                    Fx.play(level, "shutter_slam", center.add(0, 1.2, 0), across, 1f, user.getId());
                    Fx.shake(level, center, 16, 0.5f, 8);
                    Hit hit = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(cfg.shutterDamage)
                            .tag(AttackTag.TECHNIQUE).origin(center).knockback(Knockback.NONE).hitstun(cfg.shutterHitstun)
                            .guardDamage(3).fx("shutter_crush", 1f).build();
                    var shape = HitShape.orientedBox(center.subtract(across.scale(1.4)).add(0, 1.2, 0), across, 2.8, 1.6, 2.6);
                    HakariCombat.hitAll(hit, HitboxQuery.targets(user, shape, 0.3, false));
                }
                if (age >= rise + close + hold + 10) finish();
            }

            @Override
            public boolean exclusive() {
                // Hakari is free once the sign is thrown; the doors do the rest.
                return age < 6;
            }

            @Override
            public void end() {
                if (left != null) left.discard();
                if (right != null) right.discard();
            }
        };
    }
}
