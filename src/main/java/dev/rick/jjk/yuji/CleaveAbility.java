package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Cleave (JJS King of Curses Special, 12s). He winds his arm back and grabs forward; after a short pause the second blade
 * of his technique goes off — a storm of slashes through whoever he's holding, then they're thrown away. It takes 40% of
 * their current health, never less than 10. Unblockable; can't bypass ragdoll. The finisher dices them into pieces.
 */
public final class CleaveAbility extends Ability {
    public static final String ID = "cleave";

    public CleaveAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().cleaveCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            @Nullable private LivingEntity victim;
            private int grabbedAt = -1;
            private int endAt = -1;

            @Override
            public void start() {
                Anim.play(user, "cleave_reach");
                setPhase(0, YujiCombat.cfg().cleaveWindup);
                Fx.play(level, "cleave_reach", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Yuji cfg = YujiCombat.cfg();
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (victim == null) {
                    if (age < cfg.cleaveWindup) {
                        HakariCombat.drive(user, HakariCombat.flat(user), 0.25);
                        return;
                    }
                    LivingEntity t = HakariCombat.firstInFront(user, cfg.cleaveReach, 1.6, 2.2);
                    if (t != null && !YujiCombat.ragdolled(t)) {
                        HitResult r = HakariCombat.hit(YujiCombat.strike(user, ID, 0, true).knockback(Knockback.HOLD).hitstun(cfg.cleavePause + 10)
                                .fx("cleave_grab", 1f).build(), t);
                        if (r.connected()) {
                            victim = t;
                            grabbedAt = age;
                            Statuses.apply(t, CombatStatus.GRABBED, cfg.cleavePause + 6);
                            Anim.play(user, "cleave_hold");
                            setPhase(1, cfg.cleavePause);
                            return;
                        }
                    }
                    Anim.play(user, "cleave_whiff");
                    endAt = age + 10;
                    return;
                }
                if (!victim.isAlive()) {
                    finish();
                    return;
                }
                Motion.set(user, Vec3.ZERO);
                Vec3 hold = user.position().add(HakariCombat.flat(user).scale(1.1));
                Motion.set(victim, hold.subtract(victim.position()).scale(0.5));
                if (age - grabbedAt < cfg.cleavePause) return;
                // The second blade.
                Statuses.remove(victim, CombatStatus.GRABBED);
                Anim.play(user, "cleave_release");
                Vec3 at = victim.getBoundingBox().getCenter();
                if (YujiCombat.finishable(victim)) {
                    Fx.play(level, "cleave_finisher", at, HakariCombat.flat(user), 1.5f, user.getId());
                    YujiCombat.execute(user, victim, ID, "cleave_dice");
                } else {
                    Fx.play(level, "cleave", at, HakariCombat.flat(user), 1f, user.getId());
                    float damage = Math.max(cfg.cleaveMinDamage, victim.getHealth() * cfg.cleaveShare);
                    HakariCombat.hit(YujiCombat.slash(user, ID, damage).tag(dev.rick.jjk.core.combat.AttackTag.UNBLOCKABLE).noComboScaling()
                            .knockback(Knockback.directional(HakariCombat.flat(user), cfg.cleaveKnockback, 0.5)).hitstun(30)
                            .status(CombatStatus.LAUNCHED, 26).fx("cleave_hit", 1.4f).build(), victim);
                }
                Fx.shake(level, at, 28, 1.0f, 12);
                victim = null;
                endAt = age + 12;
            }

            @Override
            public float movementMultiplier() {
                return 0.2f;
            }

            @Override
            public void end() {
                if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            }
        };
    }
}
