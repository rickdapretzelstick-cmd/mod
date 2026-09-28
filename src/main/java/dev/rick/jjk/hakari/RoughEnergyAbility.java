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
 * 3 — Rough Energy. Hakari's cursed energy is coarse and jagged; he packs it around his fist through a committed
 * wind-up, then drives it forward at the very end of it. The strike breaks guards, blasts the target away and gouges
 * the ground in front of him.
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

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
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
                    setPhase(1, 10);
                    Anim.play(user, "rough_strike");
                    Vec3 f = HakariCombat.flat(user);
                    // A short committed step into the strike.
                    Motion.set(user, f.scale(0.55).add(0, Math.min(0, user.getDeltaMovement().y), 0));
                    Vec3 at = user.position().add(0, 1.1, 0).add(f.scale(1.6));
                    Hit hit = Hit.builder(user, ID).type(ModDamageTypes.TECHNIQUE).damage(cfg.roughDamage)
                            .tag(AttackTag.TECHNIQUE, AttackTag.MELEE, AttackTag.HEAVY, AttackTag.GUARD_BREAK)
                            .origin(user.getEyePosition()).knockback(Knockback.directional(f, cfg.roughKnockback, 0.35))
                            .hitstun(cfg.roughHitstun).status(CombatStatus.LAUNCHED, 16).guardDamage(4).fx("rough_hit", 1.2f).build();
                    boolean landed = HakariCombat.landed(HakariCombat.hitAll(hit, HakariCombat.front(user, cfg.roughReach, 2.2, 2.4)));
                    Fx.play(level, "rough_impact", at, f, landed ? 1.2f : 0.9f, user.getId());
                    Fx.shake(level, at, 20, landed ? 0.8f : 0.45f, 10);
                    if (Destruction.allowed(level)) {
                        Destruction.sphere(level, at.add(f.scale(0.8)).add(0, -0.9, 0), 1.6, 5f, 18, user, null, "jjk:rough_energy");
                    }
                }
                if (age >= cfg.roughWindup + 8) finish();
            }

            @Override
            public float movementMultiplier() {
                return age < JJKConfig.get().hakari.roughWindup ? 0.35f : 0.2f;
            }
        };
    }
}
