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
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jackpot 3 — Overwhelming Luck. A long forward march of alternating punches, each one a step further and a little
 * harder than the last, ending in a final punch that detonates in front of him. Anyone caught is carried along the
 * whole way.
 */
public final class OverwhelmingLuckAbility extends Ability {
    public static final String ID = "overwhelming_luck";
    private static final int START = 5;

    public OverwhelmingLuckAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.overwhelmCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private int punches;
            private int finalAt = -1;

            @Override
            public void start() {
                Anim.play(user, "overwhelm_ready");
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                setPhase(0, START + cfg.overwhelmPunches * cfg.overwhelmInterval + 12);
                Fx.play(level, "overwhelm_charge", user.position().add(0, 1.1, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                Vec3 f = HakariCombat.flat(user);
                if (age >= START && punches < cfg.overwhelmPunches && (age - START) % cfg.overwhelmInterval == 0) {
                    // Each punch steps forward; escalating force.
                    Anim.play(user, punches % 2 == 0 ? "overwhelm_left" : "overwhelm_right");
                    Motion.set(user, f.scale(0.42).add(0, Math.min(0, user.getDeltaMovement().y), 0));
                    float esc = 1f + punches * 0.12f;
                    Hit punch = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.overwhelmPunchDamage * esc).tag(AttackTag.MELEE)
                            .origin(user.getEyePosition()).knockback(Knockback.directional(f, 0.42, 0.05)).hitstun(cfg.overwhelmInterval + 8)
                            .noComboScaling().fx("overwhelm_hit", esc).build();
                    HakariCombat.hitAll(punch, HakariCombat.front(user, 2.6, 2.2, 2.4));
                    Fx.play(level, "overwhelm_punch", user.getEyePosition().add(f.scale(1.2)).add(0, -0.3, 0), f, esc, user.getId());
                    punches++;
                    if (punches == cfg.overwhelmPunches) finalAt = age + cfg.overwhelmInterval + 2;
                }
                if (age == finalAt) {
                    Anim.play(user, "overwhelm_final");
                    setPhase(1, 10);
                    Motion.set(user, f.scale(0.6));
                    Vec3 at = user.getEyePosition().add(f.scale(2.0)).add(0, -0.3, 0);
                    Hit last = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.overwhelmFinalDamage)
                            .tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.GUARD_BREAK).origin(user.getEyePosition())
                            .knockback(Knockback.directional(f, cfg.overwhelmFinalKnockback, 0.5)).hitstun(30).status(CombatStatus.LAUNCHED, 26)
                            .guardDamage(5).fx("overwhelm_final_hit", 1.6f).build();
                    HakariCombat.hitAll(last, HakariCombat.front(user, 3.4, 2.8, 2.8));
                    Fx.play(level, "overwhelm_final", at, f, 1f, user.getId());
                    Fx.shake(level, at, 28, 1.2f, 16);
                    Fx.flash(level, at, 20, 0x60A0FFB0, 5);
                    if (Destruction.allowed(level)) Destruction.sphere(level, at.add(f.scale(1.2)), 2.2, 6f, 40, user, null, "jjk:overwhelming_luck");
                }
                if (finalAt >= 0 && age >= finalAt + 10) finish();
            }

            @Override
            public float movementMultiplier() {
                return 0.2f;
            }
        };
    }
}
