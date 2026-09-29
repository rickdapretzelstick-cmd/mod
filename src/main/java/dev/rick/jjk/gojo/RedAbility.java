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
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.RedEntity;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Reversal Red (JJS). After a short wind-up a red orb flies 40 studs and explodes in a 15-stud blast of repulsion
 * (half damage and no knockback through a guard). Limitless pressed during the wind-up turns it into a special
 * variant (both moves go on cooldown):
 * <ul>
 *   <li>Gojo phases behind the target, upside down, and fires it point-blank.</li>
 *   <li>Airborne target: he appears over them, kicks, then fires it.</li>
 *   <li>A target that was in the middle of a move or a dash is frozen in place, and Gojo announces "Aka" before
 *       releasing an enhanced orb at them.</li>
 * </ul>
 * A target low enough is finished by it.
 *
 * Reversal Red MAX (awakened): a little over a second of charge, then a piercing orb that travels 100 studs, weaker the
 * farther it goes; airborne, Gojo hangs in the air and aims freely. With Limitless used during the charge it rebounds
 * to him at the end of its range: a target it caught is pulled in for a Black Flash, an empty return hurts him.
 */
public final class RedAbility extends Ability {
    public static final String ID = "red";
    public static final String MAX_ID = "max_red";
    private static final double POINT_BLANK = 2.8;
    private final boolean max;

    public RedAbility() {
        this(false);
    }

    public RedAbility(boolean max) {
        super(max ? MAX_ID : ID);
        this.max = max;
    }

    int windup() {
        return max ? JJKConfig.get().maxRed.minCharge : JJKConfig.get().red.minCharge;
    }

    @Override
    public float awakeningCost(AbilityCaster caster) {
        return max ? JJKConfig.get().awakening.maxRedCost : 0;
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return max ? 0 : JJKConfig.get().red.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return max ? JJKConfig.get().maxRed.cooldown : JJKConfig.get().red.cooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    private enum Variant { NONE, BEHIND, AIR, INTERRUPT }

    private static final class Instance extends AbilityInstance implements LimitlessCombo {
        @Nullable private final Entity hint;
        private final boolean max;
        private final int windup;
        private boolean fired;
        private Variant variant = Variant.NONE;
        @Nullable private LivingEntity target;
        /** Red MAX: Limitless was used during the charge. */
        private boolean rebound;

        Instance(RedAbility ability, AbilityContext ctx) {
            super(ability, ctx);
            this.hint = ctx.targetHint();
            this.max = ability.max;
            this.windup = ability.windup();
        }

        @Override
        public void start() {
            Anim.play(user, max ? "max_red_charge" : "red_charge");
            setPhase(0, windup);
            Fx.play(level, max ? "max_red_charge" : "red_charge", fingertip(), user.getLookAngle(), 1f, user.getId());
        }

        private Vec3 fingertip() {
            return user.getEyePosition().add(user.getLookAngle().scale(0.9)).add(0, -0.25, 0);
        }

        @Override
        public boolean acceptsLimitless() {
            return !fired && variant == Variant.NONE && !rebound;
        }

        @Override
        public boolean limitless(@Nullable Entity targetHint) {
            if (max) {
                // Red MAX: the orb will come back. Free: no cooldown needed or spent.
                rebound = true;
                Fx.play(level, "teleport_out", user.getEyePosition(), user.getLookAngle(), 0.6f, user.getId());
                return false;
            }
            JJKConfig.Gojo g = JJKConfig.get().gojo;
            LivingEntity t = Aim.target(user, g.limitlessRange, JJKConfig.get().teleport.targetAssistAngle, targetHint != null ? targetHint : hint);
            if (t == null) return false;
            target = t;
            if (GojoCombat.acting(t)) {
                // Caught mid-action: frozen, unable to react to what's coming.
                variant = Variant.INTERRUPT;
                Statuses.apply(t, CombatStatus.HITSTUN, g.redInterruptStun);
                Statuses.apply(t, CombatStatus.GRABBED, g.redInterruptStun);
                Fx.play(level, "red_amplified", fingertip(), user.getLookAngle(), 0.8f, user.getId());
                HakariCombat.faceTowards(user, t.getBoundingBox().getCenter());
                return true;
            }
            boolean air = Combat.isAirborne(t);
            variant = air ? Variant.AIR : Variant.BEHIND;
            Vec3 from = user.position();
            Vec3 dest = air ? t.position().add(0, t.getBbHeight() + 0.4, 0) : TeleportAbility.behindSpot(user, t);
            if (dest == null) dest = t.position().add(HakariCombat.flat(t).scale(-1.4));
            Fx.play(level, "teleport_out", from.add(0, 1, 0), dest.subtract(from), 1f, user.getId());
            user.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), user.getYRot(), user.getXRot(), false);
            HakariCombat.faceTowards(user, t.getBoundingBox().getCenter());
            Motion.set(user, Vec3.ZERO);
            Statuses.apply(user, CombatStatus.HOVER, windup + 6);
            Fx.play(level, "teleport_in", dest.add(0, 1, 0), dest.subtract(from), 1f, user.getId());
            Anim.play(user, air ? "limitless_air_kick" : "red_upside_down");
            if (air) {
                // The air kick keeps its properties, but the Red that follows overrides its ragdoll.
                HitResolver.resolve(GojoCombat.strike(user, TeleportAbility.ID, g.redAirKickDamage, true).knockback(Knockback.NONE).hitstun(windup + 8)
                        .fx("hit_heavy", 1f).build(), t);
                Statuses.apply(t, CombatStatus.HOVER, windup + 4);
            }
            return true;
        }

        @Override
        public void tick() {
            if (max && Combat.isAirborne(user)) Statuses.apply(user, CombatStatus.HOVER, 3);
            if (age >= windup) fire();
        }

        private void fire() {
            if (fired) return;
            fired = true;
            Vec3 look = user.getLookAngle();
            Anim.play(user, "red_release");
            if (max) {
                Fx.play(level, "max_red_fire", fingertip(), look, 2f, user.getId());
                RedEntity.fireMax(level, user, fingertip(), look, rebound);
                Motion.set(user, user.getDeltaMovement().add(look.scale(-0.5)));
                finish();
                return;
            }
            JJKConfig.Gojo g = JJKConfig.get().gojo;
            if (variant != Variant.NONE && target != null && target.isAlive()) {
                if (variant == Variant.INTERRUPT) {
                    // "Aka": the enhanced orb, straight at them.
                    Vec3 dir = target.getBoundingBox().getCenter().subtract(fingertip()).normalize();
                    Fx.play(level, "red_fire", fingertip(), dir, 1.5f, user.getId());
                    RedEntity.fire(level, user, fingertip(), dir, 0, false).setDamage(g.redInterruptDamage);
                } else {
                    GojoCombat.pointBlankRed(user, target, JJKConfig.get().red.damage, GojoCombat.finishable(target));
                }
                finish();
                return;
            }
            LivingEntity close = Aim.target(user, POINT_BLANK + 1, 25, hint);
            if (close != null && close.getBoundingBox().distanceToSqr(user.getEyePosition()) <= POINT_BLANK * POINT_BLANK) {
                Vec3 at = user.getEyePosition().add(look.scale(1.6)).add(0, -0.3, 0);
                Fx.play(level, "red_pointblank", at, look, 1f, user.getId());
                RedEntity.detonate(level, user, at, 0, user, false);
            } else {
                Fx.play(level, "red_fire", fingertip(), look, 1f, user.getId());
                RedEntity.fire(level, user, fingertip(), look, 0, false);
            }
            Motion.set(user, user.getDeltaMovement().add(look.scale(-0.35)).add(0, 0.05, 0));
            finish();
        }

        @Override
        public float movementMultiplier() {
            return 0.55f;
        }

        @Override
        public void end() {
            if (variant == Variant.INTERRUPT && target != null) Statuses.remove(target, CombatStatus.GRABBED);
        }

        @Override
        public void interrupt(String reason) {
            if (!fired) Fx.play(level, "red_fizzle", fingertip(), Vec3.ZERO, 1f, user.getId());
            super.interrupt(reason);
        }
    }
}
