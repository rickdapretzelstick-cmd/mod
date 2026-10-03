package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Unsatisfied (JJS True Cannon 2, 20s). Unimpressed with their performance, he delivers three quick blows, then turns and
 * clashes his back into them in a Tetsuzanko, overpowering them with his output: 3 for each of the first three and the
 * back clash, 6 for the toss (18). The first three can't bypass a grounded ragdoll but end an airborne one to make way
 * for the rest. Each of the first three that lands takes 0.5s off Restyle's cooldown.
 */
public final class UnsatisfiedAbility extends Ability {
    public static final String ID = "unsatisfied";
    private static final int[] BLOWS = {5, 9, 13};
    private static final int CLASH = 20, TOSS = 26, END = 36;

    public UnsatisfiedAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public boolean isTechnique() {
        return false;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return RyuCombat.cfg().unsatisfiedCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Nullable private LivingEntity victim;
            private boolean whiffed;
            /** When it ends: at {@link #END}, or sooner once there is nobody left to hit. */
            private int endAt = END;

            @Override
            public void start() {
                Anim.play(user, "ryu_unsatisfied");
                RyuCombat.sfx(user, "ryu_weave", 1f);
                RyuCombat.sfx(user, "ryu_unsat_dash", 0.9f);
                setPhase(0, END);
            }

            @Override
            public void tick() {
                JJKConfig.Ryu cfg = RyuCombat.cfg();
                // Checked first, every tick: however the combo went (landed, tossed, finished them, lost them), it ends.
                if (age >= endAt) {
                    finish();
                    return;
                }
                if (whiffed) {
                    if (age >= BLOWS[0] + 10) finish();
                    return;
                }
                if (victim != null && (!victim.isAlive() || victim.isRemoved() || victim.level() != level || victim.distanceToSqr(user) > 36)) {
                    // Killed by a blow, gone, or knocked out of reach: nothing left to hit, so a short recovery and done.
                    victim = null;
                    endAt = Math.min(endAt, age + 8);
                    return;
                }
                HakariCombat.drive(user, HakariCombat.flat(user), age < BLOWS[0] ? 0.32 : 0.05);
                if (victim != null && victim.isAlive() && age < TOSS) HakariCombat.carry(user, victim, 1.6);
                for (int i = 0; i < BLOWS.length; i++) {
                    if (age != BLOWS[i]) continue;
                    LivingEntity t = victim != null ? victim : HakariCombat.firstInFront(user, 2.7, 1.8, 2.2);
                    if (t == null || (RyuCombat.ragdolled(t) && !Combat.isAirborne(t))) {
                        if (i == 0) {
                            whiffed = true;
                            Anim.play(user, "ryu_unsatisfied_whiff");
                        }
                        return;
                    }
                    if (i == 0 && Combat.isAirborne(t)) {
                        Combat.state(t).remove(CombatStatus.LAUNCHED);
                    }
                    HitResult r = HakariCombat.hit(RyuCombat.strike(user, ID, cfg.unsatisfiedHit, false).knockback(Knockback.HOLD).hitstun(14)
                            .fx(RyuCombat.hitFx("ryu_hit_" + (i + 1), false), 0.8f).build(), t);
                    if (r.connected()) {
                        victim = t;
                        refund(cfg.unsatisfiedRestyleRefund);
                    } else if (i == 0) {
                        whiffed = true;
                    }
                }
                if (victim == null) {
                    // Every blow so far missed after a first one connected and the target was then lost: stop.
                    if (age > BLOWS[0]) endAt = Math.min(endAt, age + 8);
                    return;
                }
                if (age == CLASH) {
                    // The Tetsuzanko: he turns his back into them, all his output behind it.
                    Fx.play(level, "ryu_tetsuzanko", victim.getBoundingBox().getCenter(), HakariCombat.flat(user), 1f, user.getId());
                    HakariCombat.hit(RyuCombat.strike(user, ID, cfg.unsatisfiedClash, false).knockback(Knockback.HOLD).hitstun(16)
                            .fx(RyuCombat.hitFx("ryu_final_hit_1", true), 1.1f).build(), victim);
                    Fx.shake(level, victim.position(), 16, 0.5f, 8);
                }
                if (age == TOSS) {
                    Vec3 toward = user.position().subtract(victim.position()).normalize();
                    if (RyuCombat.finishable(victim)) {
                        RyuCombat.execute(user, victim, ID, "ryu_punch_heavy");
                    } else {
                        // Semi-ragdoll: tossed up and in toward him.
                        HakariCombat.hit(RyuCombat.strike(user, ID, cfg.unsatisfiedToss, false)
                                .knockback(Knockback.set(new Vec3(toward.x * 0.25, 0.75, toward.z * 0.25))).hitstun(22)
                                .status(CombatStatus.LAUNCHED, 20).fx(RyuCombat.hitFx("ryu_final_hit_2", true), 1.3f).build(), victim);
                    }
                    victim = null;
                    endAt = Math.min(endAt, age + 10);
                }
            }

            private void refund(int ticks) {
                for (AbilitySlot s : AbilitySlot.values()) {
                    if (caster.ability(s) instanceof RestyleAbility && caster.cooldown(s) > 0) {
                        caster.startCooldown(s, Math.max(1, caster.cooldown(s) - ticks));
                    }
                }
            }

            @Override
            public float movementMultiplier() {
                return 0.15f;
            }
        };
    }
}
