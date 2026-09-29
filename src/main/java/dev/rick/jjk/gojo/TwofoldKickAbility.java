package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Twofold Kick (JJS 4). Gojo swings his leg up into a kick (semi-blockable: a guard stops the follow-up); on hit he
 * gains melee i-frames and the enemy is anchored in the air for an unblockable second kick that bounces them even
 * higher. A target low enough is then held in the air and hit with a point-blank Reversal Red.
 */
public final class TwofoldKickAbility extends Ability {
    public static final String ID = "twofold_kick";

    public TwofoldKickAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().gojo.twofoldCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().gojo.twofoldCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Nullable private LivingEntity victim;
            private int firstAt = -1;
            private int endAt = -1;

            @Override
            public void start() {
                Anim.play(user, "twofold_1");
                setPhase(0, JJKConfig.get().gojo.twofoldWindup);
            }

            @Override
            public void tick() {
                JJKConfig.Gojo cfg = JJKConfig.get().gojo;
                if (endAt >= 0) {
                    if (victim != null && victim.isAlive() && age < endAt) Statuses.apply(victim, CombatStatus.HOVER, 2);
                    if (age >= endAt) finish();
                    return;
                }
                if (age == cfg.twofoldWindup) {
                    Fx.play(level, "swing", user.position().add(0, 1, 0), new Vec3(0, 1, 0), 1f, user.getId());
                    LivingEntity t = HakariCombat.firstInFront(user, cfg.twofoldReach, 1.8, 2.4);
                    if (t == null) {
                        endAt = age + 10;
                        return;
                    }
                    HitResult r = HitResolver.resolve(GojoCombat.strike(user, ID, cfg.twofoldFirstDamage, false)
                            .knockback(Knockback.set(new Vec3(0, 0.75, 0))).hitstun(cfg.twofoldAnchorTicks + 14)
                            .status(CombatStatus.LAUNCHED, 20).fx("hit_launch", 1f).build(), t);
                    if (!r.connected() || r.outcome() == HitResult.Outcome.BLOCKED) {
                        endAt = age + 12; // blocked: no second kick
                        return;
                    }
                    victim = t;
                    firstAt = age;
                    Statuses.apply(user, CombatStatus.MELEE_ARMOR, cfg.twofoldAnchorTicks + 10);
                    setPhase(1, cfg.twofoldAnchorTicks);
                    return;
                }
                if (victim == null) return;
                if (!victim.isAlive()) {
                    finish();
                    return;
                }
                int since = age - firstAt;
                if (since > 4 && since < cfg.twofoldAnchorTicks) {
                    // Anchored in the air for the second kick.
                    Statuses.apply(victim, CombatStatus.HOVER, 3);
                    dev.rick.jjk.util.Motion.set(victim, victim.getDeltaMovement().multiply(0.4, 0.2, 0.4));
                }
                if (since == cfg.twofoldAnchorTicks) {
                    Anim.play(user, "twofold_2");
                    HitResolver.resolve(GojoCombat.strike(user, ID, cfg.twofoldSecondDamage, true)
                            .knockback(Knockback.set(new Vec3(0, 1.15, 0).add(HakariCombat.flat(user).scale(0.2)))).hitstun(30)
                            .status(CombatStatus.LAUNCHED, 30).fx("hit_launch", 1.2f).build(), victim);
                    Fx.shake(level, victim.position(), 16, 0.5f, 8);
                    if (GojoCombat.finishable(victim)) {
                        // The finisher: held up there, and a point-blank Red.
                        Anim.play(user, "red_release");
                        GojoCombat.pointBlankRed(user, victim, cfg.twofoldRedDamage, true);
                    }
                    endAt = age + 8;
                }
            }

            @Override
            public float movementMultiplier() {
                return 0.3f;
            }
        };
    }
}
