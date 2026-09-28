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
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 4 — Fever Breaker. A spinning kick that knocks the target flying, a burst of pursuit that runs them down, and a second,
 * far heavier kick that breaks them — kick → knockback → rush → second impact. If the first kick whiffs, the combo
 * stops there.
 */
public final class FeverBreakerAbility extends Ability {
    public static final String ID = "fever_breaker";
    private static final int KICK = 5;

    public FeverBreakerAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().hakari.feverCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().hakari.feverCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private LivingEntity victim;
            private int rushStart = -1;
            private boolean finished, whiffed;
            private int recovery;

            @Override
            public void start() {
                Anim.play(user, "fever_kick");
                setPhase(0, 30);
                Motion.set(user, HakariCombat.flat(user).scale(0.35).add(0, user.getDeltaMovement().y, 0));
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                if (age == KICK) {
                    Vec3 f = HakariCombat.flat(user);
                    Hit kick = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.feverKickDamage)
                            .tag(AttackTag.MELEE, AttackTag.TECHNIQUE).origin(user.getEyePosition())
                            .knockback(Knockback.directional(f, cfg.feverKickKnockback, 0.3)).hitstun(24).guardDamage(2).fx("fever_kick", 1f).build();
                    victim = HakariCombat.firstInFront(user, 2.6, 1.8, 2.2);
                    Fx.play(level, "fever_swing", user.position().add(0, 1, 0).add(f.scale(1.2)), f, 1f, user.getId());
                    if (victim == null || !HakariCombat.hit(kick, victim).connected()) {
                        victim = null;
                        whiffed = true;
                        return;
                    }
                    rushStart = age + 2;
                }
                if (whiffed) {
                    if (age >= KICK + 6) finish();
                    return;
                }
                if (rushStart >= 0 && age >= rushStart && !finished) {
                    // The rush: run the target down.
                    if (age == rushStart) {
                        setPhase(1, cfg.feverRushTicks);
                        Anim.play(user, "fever_rush");
                        Fx.play(level, "fever_rush", user.position().add(0, 1, 0), HakariCombat.flat(user), 1f, user.getId());
                    }
                    if (!victim.isAlive()) {
                        finish();
                        return;
                    }
                    HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
                    Vec3 to = victim.position().subtract(user.position());
                    double dist = to.horizontalDistance();
                    HakariCombat.drive(user, to, Math.min(1.35, 0.4 + dist * 0.5));
                    if (dist < 2.2 || age - rushStart >= cfg.feverRushTicks) {
                        finished = true;
                        setPhase(2, 10);
                        Anim.play(user, "fever_finish");
                        Vec3 f = HakariCombat.flat(user);
                        Statuses.remove(victim, CombatStatus.LAUNCHED);
                        Hit breaker = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.feverFinishDamage)
                                .tag(AttackTag.MELEE, AttackTag.TECHNIQUE, AttackTag.HEAVY, AttackTag.OTG).origin(user.getEyePosition())
                                .knockback(Knockback.directional(f, cfg.feverFinishKnockback, 0.55)).hitstun(30).status(CombatStatus.LAUNCHED, 24)
                                .guardDamage(4).fx("fever_impact", 1.4f).build();
                        if (dist < 3.4) HakariCombat.hit(breaker, victim);
                        Fx.play(level, "fever_break", victim.getBoundingBox().getCenter(), f, 1f, user.getId());
                        Fx.shake(level, victim.position(), 22, 0.9f, 12);
                        Motion.set(user, user.getDeltaMovement().scale(0.2));
                        rushStart = -1;
                        victim = null;
                    }
                    return;
                }
                // Recovery after the second kick (or a safety stop if the rush never started).
                if (finished ? ++recovery >= 8 : age > 60) finish();
            }

            @Override
            public float movementMultiplier() {
                return rushStart >= 0 ? 1f : 0.3f;
            }
        };
    }
}
