package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Energy Ripple (JJS True Love, 18s). He pulls out his katana and drives it into the floor, imbuing it with cursed
 * energy: a field that pushes every enemy within 27 studs away (19). Unblockable, uninterruptible, bypasses ragdoll; an
 * explosion. The stab itself landing on someone knocked down only lengthens their ragdoll. He fights with the katana
 * again afterward.
 *
 * <p>Fakeout (used again before the blade reaches the floor): a sudden swing instead, its imbued energy transferred into
 * the enemy (7 for the swing, 12 for the burst), knocking them toward where he faces. Its finisher cuts them in half and
 * the burst implodes the pieces.
 */
public final class EnergyRippleAbility extends Ability {
    public static final String ID = "energy_ripple";

    public EnergyRippleAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return 80f;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().rippleCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaCombat.drawKatana(ctx.user());
        return new Instance(this, ctx);
    }

    public static final class Instance extends AbilityInstance {
        private boolean fakeout;
        private int fakeoutAt = -1;
        private int endAt = -1;
        @Nullable private LivingEntity swung;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        /** Used again before the blade hits the floor: the Fakeout. */
        public boolean fakeoutPress() {
            if (fakeout || endAt >= 0 || age >= YutaCombat.cfg().rippleWindup) return false;
            fakeout = true;
            fakeoutAt = age;
            Anim.play(user, "yuta_fakeout");
            setPhase(1, 14);
            return true;
        }

        @Override
        public void start() {
            Anim.play(user, "yuta_energy_ripple");
            setPhase(0, YutaCombat.cfg().rippleWindup);
            Fx.play(level, "ripple_charge", user.position().add(0, 1.0, 0), HakariCombat.flat(user), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (fakeout) {
                fakeout(cfg);
                return;
            }
            if (age == cfg.rippleWindup) ripple(cfg);
        }

        private void ripple(JJKConfig.Yuta cfg) {
            Vec3 tip = user.position().add(HakariCombat.flat(user).scale(0.9));
            Fx.play(level, "energy_ripple", tip.add(0, 0.1, 0), Vec3.ZERO, (float) cfg.rippleRadius, user.getId());
            Fx.shake(level, tip, cfg.rippleRadius * 3, 0.9f, 14);
            // The stab: someone lying right under the blade only stays down longer.
            LivingEntity stabbed = null;
            for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(tip, 1.0), 0.3, false)) {
                if (Combat.isDowned(t)) {
                    stabbed = t;
                    Statuses.apply(t, CombatStatus.KNOCKDOWN, 40);
                    break;
                }
            }
            for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(tip.add(0, 0.8, 0), cfg.rippleRadius), 0.3, false)) {
                if (t == stabbed) continue;
                Vec3 away = t.position().subtract(tip);
                Vec3 flat = new Vec3(away.x, 0, away.z);
                flat = flat.lengthSqr() < 1e-4 ? HakariCombat.flat(user) : flat.normalize();
                HakariCombat.hit(YutaCombat.strike(user, ID, cfg.rippleDamage, true).tag(AttackTag.EXPLOSION, AttackTag.AREA, AttackTag.OTG)
                        .knockback(Knockback.set(flat.scale(1.6).add(0, 0.55, 0))).hitstun(28).status(CombatStatus.LAUNCHED, 30)
                        .noComboScaling().fx("ripple_hit", 1f).build(), t);
            }
            endAt = age + 16;
        }

        private void fakeout(JJKConfig.Yuta cfg) {
            int t = age - fakeoutAt;
            Vec3 ahead = HakariCombat.flat(user);
            if (t == 2) {
                Fx.play(level, "fakeout_swing", user.position().add(0, 1.1, 0), ahead, 1f, user.getId());
                LivingEntity v = HakariCombat.firstInFront(user, 2.8, 2.4, 2.4);
                if (v == null) return;
                swung = v;
                if (YutaCombat.finishable(v)) return;
                HakariCombat.hit(YutaCombat.strike(user, ID, cfg.fakeoutSwingDamage, true).tag(AttackTag.OTG)
                        .knockback(Knockback.HOLD).hitstun(16).fx("severing_swing", 1.2f).build(), v);
            }
            if (t == 6) {
                endAt = age + 12;
                if (swung == null || !swung.isAlive()) return;
                Fx.play(level, "fakeout_burst", swung.getBoundingBox().getCenter(), ahead, 1f, user.getId());
                Fx.shake(level, swung.position(), 20, 0.8f, 10);
                if (YutaCombat.finishable(swung)) {
                    YutaCombat.execute(user, swung, ID, "fakeout_finisher");
                    return;
                }
                HakariCombat.hit(YutaCombat.strike(user, ID, cfg.fakeoutBurstDamage, true).tag(AttackTag.EXPLOSION, AttackTag.OTG)
                        .knockback(Knockback.set(ahead.scale(1.8).add(0, 0.5, 0))).hitstun(28).status(CombatStatus.LAUNCHED, 30)
                        .noComboScaling().fx("ripple_hit", 1.2f).build(), swung);
            }
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }
    }
}
