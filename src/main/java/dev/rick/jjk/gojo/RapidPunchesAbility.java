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
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rapid Punches (JJS 3). A spinning kick tries to lock a nearby enemy in place (unblockable, but it can't catch someone
 * who is ragdolled). Caught: Gojo gains bullet i-frames and stuns them with a barrage of 15 punches, then 3 heavy
 * punches, then a final blow that ragdolls them out of the grab — nearly twice as far if they had only just got up.
 * A target low enough takes a Black Flash on the final blow instead. Limitless right after it lands is Face Grater.
 */
public final class RapidPunchesAbility extends Ability {
    public static final String ID = "rapid_punches";

    public RapidPunchesAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().gojo.punchesCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().gojo.punchesCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Nullable private LivingEntity victim;
            private boolean wokeUp;
            private int caughtAt = -1;
            private int endAt = -1;

            private JJKConfig.Gojo cfg() {
                return JJKConfig.get().gojo;
            }

            @Override
            public void start() {
                Anim.play(user, "spin_kick");
                setPhase(0, cfg().punchesWindup);
                Fx.play(level, "swing", user.position().add(0, 0.9, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            private int barrageEnd() {
                return caughtAt + cfg().punchesBarrage * 2;
            }

            private int heavyEnd() {
                return barrageEnd() + cfg().punchesHeavy * 5;
            }

            @Override
            public void tick() {
                JJKConfig.Gojo cfg = cfg();
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (age == cfg.punchesWindup) {
                    LivingEntity t = null;
                    for (LivingEntity c : HakariCombat.front(user, cfg.punchesReach, 1.8, 2.2)) {
                        if (GojoCombat.ragdolled(c)) continue; // cannot bypass ragdoll
                        if (t == null || c.distanceToSqr(user) < t.distanceToSqr(user)) t = c;
                    }
                    if (t == null) {
                        endAt = age + 10;
                        return;
                    }
                    wokeUp = Combat.state(t).has(CombatStatus.WAKEUP);
                    var grab = GojoCombat.strike(user, ID, cfg.punchesGrabDamage, true).knockback(Knockback.HOLD).hitstun(20).fx("hit_light", 0.8f).build();
                    if (!HitResolver.resolve(grab, t).connected()) {
                        endAt = age + 10;
                        return;
                    }
                    victim = t;
                    caughtAt = age;
                    Anim.play(user, "rapid_barrage");
                    setPhase(1, cfg.punchesBarrage * 2 + cfg.punchesHeavy * 5 + 6);
                    return;
                }
                if (victim == null) return;
                if (!victim.isAlive()) {
                    finish();
                    return;
                }
                Statuses.apply(user, CombatStatus.BULLET_ARMOR, 3);
                Statuses.apply(victim, CombatStatus.GRABBED, 3);
                HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
                HakariCombat.carry(user, victim, 1.3);
                int since = age - caughtAt;
                if (age <= barrageEnd()) {
                    if (since > 0 && since % 2 == 0) {
                        HitResolver.resolve(GojoCombat.strike(user, ID, cfg.punchesBarrageDamage, true).knockback(Knockback.HOLD).hitstun(10)
                                .noComboScaling().fx("hit_light", 0.6f).build(), victim);
                    }
                    return;
                }
                if (age <= heavyEnd()) {
                    if (age == barrageEnd() + 1) Anim.play(user, "rapid_heavy");
                    if ((age - barrageEnd()) % 5 == 0) {
                        HitResolver.resolve(GojoCombat.strike(user, ID, cfg.punchesHeavyDamage, true).knockback(Knockback.HOLD).hitstun(12)
                                .noComboScaling().fx("hit_heavy", 0.9f).build(), victim);
                        Fx.shake(level, victim.position(), 10, 0.3f, 4);
                    }
                    return;
                }
                if (age == heavyEnd() + 6) {
                    Statuses.remove(victim, CombatStatus.GRABBED);
                    Anim.play(user, "rapid_final");
                    GojoState gs = GojoState.of(user);
                    if (GojoCombat.finishable(victim)) {
                        // The finisher: the final punch lands as a Black Flash.
                        gs.punchesKilled = true;
                        GojoCombat.execute(user, victim, ID, "finisher");
                    } else {
                        double kb = cfg.punchesFinalKnockback * (wokeUp ? 1.9 : 1);
                        HitResolver.resolve(GojoCombat.strike(user, ID, cfg.punchesFinalDamage, true)
                                .knockback(Knockback.directional(HakariCombat.flat(user), kb, 0.5)).hitstun(26)
                                .status(CombatStatus.LAUNCHED, 24).fx("hit_launch", 1.2f).build(), victim);
                        gs.punchesKilled = false;
                        gs.punched = victim;
                        gs.faceGraterUntil = level.getGameTime() + cfg.faceGraterWindow;
                    }
                    Fx.shake(level, victim.position(), 18, 0.7f, 10);
                    victim = null;
                    endAt = age + 8;
                }
            }

            @Override
            public float movementMultiplier() {
                return victim != null ? 0f : 0.4f;
            }

            @Override
            public void end() {
                if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            }
        };
    }
}
