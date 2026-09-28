package dev.rick.jjk.hakari;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 3 — Rough Energy. Hakari winds his fist up, packing sharp, coarse cursed energy around it, and punches with enough
 * force to send the target flying. Unblockable, but the wind-up and the recovery are long and punishable.
 * <ul>
 *   <li>In the air: he hovers for a split second to gather the energy, then stomps the ground; the shockwave launches
 *       everyone around upward (blockable from any side).</li>
 *   <li>From higher than a jump (off the Shutter Doors, say): the stomp is unblockable and does double damage.</li>
 * </ul>
 */
public final class RoughEnergyAbility extends Ability {
    public static final String ID = "rough_energy";

    public RoughEnergyAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.roughCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.roughCooldown;
    }

    /** Height of the user's feet above the ground under them (capped). */
    static double heightAboveGround(net.minecraft.world.entity.LivingEntity user) {
        Vec3 feet = user.position();
        Vec3 ground = ShutterDoorsAbility.ground(user.level(), feet.add(0, -0.1, 0));
        if (ground.equals(feet.add(0, -0.1, 0))) {
            var hit = user.level().clip(new net.minecraft.world.level.ClipContext(feet, feet.add(0, -24, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, user));
            return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 24 : feet.y - hit.getLocation().y;
        }
        return feet.y - ground.y;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        boolean air = !ctx.user().onGround() && heightAboveGround(ctx.user()) > 0.6;
        return air ? stomp(ctx) : punch(ctx);
    }

    private AbilityInstance punch(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Override
            public void start() {
                Anim.play(user, "rough_charge");
                setPhase(0, JJKConfig.get().hakari.roughWindup);
                Fx.play(level, "rough_charge", user.position().add(0, 1.1, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                if (age == cfg.roughWindup) {
                    setPhase(1, 14);
                    Anim.play(user, "rough_strike");
                    Vec3 f = HakariCombat.flat(user);
                    Motion.set(user, f.scale(0.55).add(0, Math.min(0, user.getDeltaMovement().y), 0));
                    Vec3 at = user.position().add(0, 1.1, 0).add(f.scale(1.6));
                    Hit hit = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(cfg.roughDamage)
                            .tag(AttackTag.TECHNIQUE, AttackTag.MELEE, AttackTag.HEAVY, AttackTag.UNBLOCKABLE)
                            .origin(user.getEyePosition()).knockback(Knockback.directional(f, cfg.roughKnockback, 0.4))
                            .hitstun(cfg.roughHitstun).status(CombatStatus.LAUNCHED, 18).guardDamage(4).fx("rough_hit", 1.2f).build();
                    boolean landed = HakariCombat.landed(HakariCombat.hitAll(hit, HakariCombat.front(user, cfg.roughReach, 2.2, 2.4)));
                    Fx.play(level, "rough_impact", at, f, landed ? 1.2f : 0.9f, user.getId());
                    Fx.shake(level, at, 20, landed ? 0.8f : 0.45f, 10);
                    if (Destruction.allowed(level)) {
                        Destruction.sphere(level, at.add(f.scale(0.8)).add(0, -0.9, 0), 1.6, 5f, 18, user, null, "jjk:rough_energy");
                    }
                }
                // The long recovery is part of the move.
                if (age >= cfg.roughWindup + 12) finish();
            }

            @Override
            public float movementMultiplier() {
                return age < JJKConfig.get().hakari.roughWindup ? 0.35f : 0.1f;
            }
        };
    }

    private AbilityInstance stomp(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private static final int HOVER = 6;
            private boolean high, slammed;
            private int slamAge;
            private double startY;

            @Override
            public void start() {
                high = heightAboveGround(user) > JJKConfig.get().hakari.roughHighAirHeight;
                startY = user.getY();
                Anim.play(user, "rough_charge");
                setPhase(0, HOVER);
                // Up for a split second to gather the energy.
                Motion.set(user, new Vec3(0, 0.28, 0));
                Statuses.apply(user, CombatStatus.HOVER, HOVER);
                Fx.play(level, "rough_charge", user.position().add(0, 1.1, 0), new Vec3(0, -1, 0), high ? 1.4f : 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                if (age < HOVER) {
                    Motion.set(user, new Vec3(0, age < 3 ? 0.18 : 0, 0));
                    return;
                }
                if (!slammed) {
                    if (age == HOVER) {
                        setPhase(1, 30);
                        Anim.play(user, "rough_strike");
                    }
                    Motion.set(user, new Vec3(0, -1.6, 0));
                    if (user.onGround() || age > HOVER + 30) {
                        slammed = true;
                        slamAge = age;
                        setPhase(2, 10);
                        user.resetFallDistance();
                        // Measured from where he started: a stomp from high up is the stronger variant.
                        boolean strong = high || startY - user.getY() > cfg.roughHighAirHeight + 0.5;
                        Vec3 at = user.position();
                        Hit.Builder b = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE)
                                .damage(strong ? cfg.roughStompDamage * 2 : cfg.roughStompDamage)
                                .tag(AttackTag.TECHNIQUE, AttackTag.MELEE, AttackTag.HEAVY).origin(at)
                                .knockback(Knockback.set(new Vec3(0, strong ? 1.0 : 0.8, 0))).hitstun(20)
                                .status(CombatStatus.LAUNCHED, 20).guardDamage(3).fx("rough_hit", strong ? 1.4f : 1f);
                        if (strong) b.tag(AttackTag.UNBLOCKABLE);
                        HakariCombat.hitAll(b.build(), HitboxQuery.targets(user, HitShape.sphere(at.add(0, 0.6, 0), cfg.roughStompRadius), 0.3, false));
                        Fx.play(level, "rough_stomp", at.add(0, 0.1, 0), Vec3.ZERO, strong ? 1.5f : 1f, user.getId());
                        Fx.shake(level, at, 24, strong ? 1f : 0.7f, 12);
                        if (Destruction.allowed(level)) Destruction.sphere(level, at.add(0, -0.6, 0), strong ? 2.2 : 1.6, 5f, 24, user, null, "jjk:rough_energy");
                    }
                    return;
                }
                if (age >= slamAge + 10) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0.1f;
            }
        };
    }
}
