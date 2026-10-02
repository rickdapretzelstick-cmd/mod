package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika Smash (JJS base Rika, 10s shared). Her fist swells as she raises it over the target, then slams down on them and
 * they bounce upward (10). 360-blockable, uninterruptible, bypasses ragdoll; a bullet. On an airborne target she hovers
 * above them and dunks them into the ground instead (8), a longer ragdoll. The finisher leaves only a puddle of blood.
 * A kill gives Yuta their technique.
 */
public final class RikaSmashAbility extends RikaMove {
    public static final String ID = "rika_smash";
    private static final int WINDUP = 14;

    public RikaSmashAbility() {
        super(ID, false);
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().rikaSmashCooldown;
    }

    @Override
    protected AbilityInstance create(AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity target) {
        return new Instance(this, ctx, rika, target) {
            private boolean air;
            private Vec3 spot = Vec3.ZERO;

            @Override
            protected void begin() {
                air = target != null && airborne(target);
                anim(air ? "rika_smash_air" : "rika_smash");
                Fx.play(level, "rika_fist_grow", rika.position().add(0, 2.6, 0), Vec3.ZERO, 1f, rika.getId());
            }

            @Override
            protected void step() {
                JJKConfig.Yuta cfg = YutaCombat.cfg();
                if (age < WINDUP) {
                    if (target != null && target.isAlive()) {
                        // Over them: raised above (or hovering right above an airborne one).
                        if (air) rika.moveTo(target.position().add(facing().scale(-0.6)).add(0, 1.6, 0), 1.4, 3);
                        else approach(target, 1.6, 0.4, 1.4);
                        spot = target.position();
                    } else {
                        spot = front(2.4);
                    }
                    return;
                }
                if (age == WINDUP) slam(cfg);
                if (age >= WINDUP + 10) finish();
            }

            private void slam(JJKConfig.Yuta cfg) {
                if (target != null && target.isAlive() && target.position().distanceTo(spot) < 2.2) spot = target.position();
                Fx.play(level, air ? "rika_dunk" : "rika_smash", spot.add(0, 0.2, 0), new Vec3(0, -1, 0), 1f, rika.getId());
                Fx.shake(level, spot, 20, 0.8f, 10);
                for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(spot.add(0, 0.8, 0), 1.9), 0.3, false)) {
                    if (YutaCombat.finishable(t)) {
                        HitResult r = YutaCombat.execute(user, t, ID, "rika_smash_finisher");
                        copyOnKill(r);
                        continue;
                    }
                    var b = YutaCombat.rika(user, rika, ID, air ? cfg.rikaSmashAirDamage : cfg.rikaSmashDamage, true)
                            .tag(AttackTag.BLOCKABLE_360, AttackTag.OTG).noComboScaling();
                    if (air) {
                        b.knockback(Knockback.set(new Vec3(0, -1.6, 0))).hitstun(34).status(CombatStatus.SPIKED, 50).fx("yuta_impact", 1.4f);
                    } else {
                        b.knockback(Knockback.set(new Vec3(0, 0.85, 0))).hitstun(28).status(CombatStatus.LAUNCHED, 30).fx("yuta_impact", 1.3f);
                    }
                    HitResult r = HakariCombat.hit(b.build(), t);
                    copyOnKill(r);
                }
            }
        };
    }

    private abstract static class Instance extends RikaMove.Instance {
        Instance(Ability ability, AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity target) {
            super(ability, ctx, rika, target, WINDUP + 10);
        }
    }
}
