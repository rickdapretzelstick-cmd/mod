package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Granite Blast (JJS True Cannon 1, 0.5s). He aims the cannon of his pompadour and fires a long beam of concentrated
 * energy, 78.5 studs, that stuns the first person it meets (or ragdolls them if they were already stunned): 5.5, 20%
 * Overheat. Usable in the air, to aim better.
 * <ul>
 *   <li>Held about 1.1 seconds: charged further, it pierces anything for 100 studs, unblockable, and ragdolls whoever it
 *   meets back (with an instant wake-up); 12 falling to 5.5 with distance, 40% Overheat.</li>
 *   <li>During a front dash: he stops, lets out a blast that loops round him, and dashes forward again (4, 6s).</li>
 * </ul>
 * Shut off at 100% Overheat. The finisher fries them to a crisp.
 */
public final class GraniteBlastAbility extends Ability {
    public static final String ID = "granite_blast";

    public GraniteBlastAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    static boolean dashVariant(LivingEntity user) {
        return user.level().getGameTime() - RyuState.of(user).frontDashAt <= RyuCombat.cfg().graniteDashWindow
                && dev.rick.jjk.progression.mastery.Mastery.unlocked(user, "granite_blast.dash");
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return dashVariant(caster.owner) ? RyuCombat.cfg().graniteDashCooldown : RyuCombat.cfg().graniteCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return RyuCombat.canDischarge(ctx.user()) ? null : "overheated";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        boolean dash = dashVariant(ctx.user());
        RyuState.of(ctx.user()).frontDashAt = -1000;
        return dash ? new DashLoop(this, ctx) : new Blast(this, ctx);
    }

    /** Tap or hold. */
    static final class Blast extends AbilityInstance {
        private boolean charged;
        private int fireAt = -1;

        Blast(Ability a, AbilityContext ctx) {
            super(a, ctx);
        }

        @Override
        public void start() {
            Anim.play(user, "ryu_granite_aim");
            Fx.play(level, "granite_charge", RyuCombat.cannon(user), user.getLookAngle(), 0f, user.getId());
            setPhase(0, RyuCombat.cfg().graniteHoldTicks);
        }

        @Override
        public void tick() {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            if (!Combat.isAirborne(user)) Motion.set(user, user.getDeltaMovement().multiply(0.4, 1, 0.4));
            else Statuses.apply(user, CombatStatus.HOVER, 3);
            if (fireAt >= 0) {
                if (age >= fireAt + 8) finish();
                return;
            }
            if (!charged && age >= cfg.graniteHoldTicks && dev.rick.jjk.progression.mastery.Mastery.unlocked(user, "granite_blast.charged")) {
                charged = true;
                setPhase(1, 20);
                // Warning before the charged shot.
                Fx.play(level, "granite_charge", RyuCombat.cannon(user), user.getLookAngle(), 1f, user.getId());
            }
            if (!held && age >= 2 || age >= cfg.graniteHoldTicks + (charged ? 20 : 0)) {
                fire(charged);
                fireAt = age;
            }
        }

        private void fire(boolean charged) {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            Vec3 from = RyuCombat.cannon(user);
            Vec3 dir = user.getLookAngle().normalize();
            Anim.play(user, charged ? "ryu_granite_fire_held" : "ryu_granite_fire");
            RyuCombat.addHeat(user, charged ? cfg.heatGraniteHeld : cfg.heatGranite);
            double range = charged ? cfg.graniteHeldRange : cfg.graniteRange;
            Vec3 end = charged ? from.add(dir.scale(range)) : RyuCombat.rayEnd(user, from, dir, range);
            Fx.play(level, charged ? "granite_blast_held" : "granite_blast", from, end.subtract(from), 1f, user.getId());
            Fx.shake(level, from, charged ? 24 : 12, charged ? 0.5f : 0.2f, 6);
            Motion.add(user, dir.scale(charged ? -0.35 : -0.12));
            List<RyuCombat.RayHit> hits = RyuCombat.ray(user, from, dir, range, charged ? 0.6 : 0.35, charged);
            if (charged) {
                for (double d = 2; d < range; d += 2.5) Destruction.sphere(level, from.add(dir.scale(d)), 0.9, 20f, 4, user, null);
                for (RyuCombat.RayHit h : hits) {
                    float dmg = Mth.lerp((float) Mth.clamp(h.along() / range, 0, 1), cfg.graniteHeldDamage, cfg.graniteHeldMinDamage);
                    hit(h.target(), dmg, true, dir);
                }
            } else if (!hits.isEmpty()) {
                hit(hits.getFirst().target(), cfg.graniteDamage, false, dir);
            }
        }

        private void hit(LivingEntity t, float dmg, boolean charged, Vec3 dir) {
            if (RyuCombat.finishable(t) && dmg >= t.getHealth()) {
                RyuCombat.execute(user, t, ID, "granite_finisher");
                return;
            }
            boolean stunned = Combat.state(t).has(CombatStatus.HITSTUN);
            var b = RyuCombat.blast(user, ID, dmg, RyuCombat.cannon(user)).fx("ryu_ray_hit", charged ? 1.3f : 0.9f).noComboScaling();
            if (charged) {
                b.tag(AttackTag.UNBLOCKABLE, AttackTag.EXPLOSION).knockback(Knockback.set(dir.scale(1.1).add(0, 0.45, 0))).hitstun(14)
                        .status(CombatStatus.LAUNCHED, 14).status(CombatStatus.WAKEUP, 22);
            } else if (stunned) {
                b.knockback(Knockback.directional(dir, 0.8, 0.35)).hitstun(20).status(CombatStatus.LAUNCHED, 20);
            } else {
                b.knockback(Knockback.HOLD).hitstun(RyuCombat.cfg().graniteStun);
            }
            HitResult r = HakariCombat.hit(b.build(), t);
            if (r.connected() && t.level() == level) Fx.play(level, "granite_hit", t.getBoundingBox().getCenter(), dir, charged ? 1.3f : 1f, user.getId());
        }

        @Override
        public float movementMultiplier() {
            return fireAt >= 0 ? 0.2f : 0.35f;
        }
    }

    /** Out of a front dash: a blast looping round him, then a second dash on. */
    static final class DashLoop extends AbilityInstance {
        private Vec3 dir = Vec3.ZERO;

        DashLoop(Ability a, AbilityContext ctx) {
            super(a, ctx);
        }

        @Override
        public void start() {
            dir = HakariCombat.flat(user);
            Motion.set(user, new Vec3(0, user.getDeltaMovement().y * 0.3, 0));
            Anim.play(user, "ryu_granite_dash");
            RyuCombat.addHeat(user, RyuCombat.cfg().heatGranite);
            Fx.play(level, "granite_loop", user.position().add(0, 1.0, 0), dir, 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            if (age < 5) {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                return;
            }
            if (age == 5) {
                for (LivingEntity t : user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(2.6),
                        e -> dev.rick.jjk.core.combat.Targeting.canTarget(user, e))) {
                    HakariCombat.hit(RyuCombat.strike(user, ID, cfg.graniteDashDamage, false).knockback(Knockback.directional(dir, 0.5, 0.2))
                            .hitstun(12).fx("ryu_ray_hit", 0.7f).build(), t);
                }
                Statuses.apply(user, CombatStatus.EVADING, 6);
                Anim.play(user, "dash_forward");
                Fx.play(level, "dash", user.position().add(0, 0.9, 0), dir, 1f, user.getId());
            }
            if (age >= 5 && age < 13) Motion.set(user, new Vec3(dir.x * 1.25, Math.max(0.02, user.getDeltaMovement().y), dir.z * 1.25));
            if (age >= 16) finish();
        }
    }
}
