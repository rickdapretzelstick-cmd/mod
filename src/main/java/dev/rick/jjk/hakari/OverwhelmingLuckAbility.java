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
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Jackpot 3 — Overwhelming Luck. Hakari charges his cursed energy, rushes forward and lands a devastating strike that
 * tosses the target away, then sprints after them, grabs them — everyone the strike caught — lands a string of hits and
 * a final punch that sends them flying. Unblockable throughout.
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
            /** Everyone the opening strike tossed: he runs them down and grabs them all. */
            private final java.util.List<net.minecraft.world.entity.LivingEntity> caught = new java.util.ArrayList<>();
            private int punches;
            private int grabAt = -1, finalAt = -1;

            @Override
            public void start() {
                Anim.play(user, "overwhelm_ready");
                setPhase(0, START + 60);
                Fx.play(level, "overwhelm_charge", user.position().add(0, 1.1, 0), HakariCombat.flat(user), 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Hakari cfg = JJKConfig.get().hakari;
                Vec3 f = HakariCombat.flat(user);
                if (age < START) return;
                if (age == START) {
                    // The forward rush and the devastating strike that tosses them away.
                    Anim.play(user, "overwhelm_left");
                    Motion.set(user, f.scale(0.9).add(0, Math.min(0, user.getDeltaMovement().y), 0));
                    Hit open = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.overwhelmOpenerDamage)
                            .tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.UNBLOCKABLE).origin(user.getEyePosition())
                            .knockback(Knockback.directional(f, 0.95, 0.3)).hitstun(30).fx("overwhelm_hit", 1.3f).build();
                    for (var t : HakariCombat.front(user, 3.0, 2.4, 2.4)) if (HakariCombat.hit(open, t).connected()) caught.add(t);
                    Fx.play(level, "overwhelm_punch", user.getEyePosition().add(f.scale(1.2)).add(0, -0.3, 0), f, 1.3f, user.getId());
                    if (caught.isEmpty()) {
                        finalAt = age + 8;
                        return;
                    }
                    grabAt = age + 10;
                    setPhase(1, 10);
                    return;
                }
                caught.removeIf(t -> !t.isAlive());
                if (grabAt >= 0 && age < grabAt && !caught.isEmpty()) {
                    // Sprinting after them.
                    var lead = caught.getFirst();
                    HakariCombat.faceTowards(user, lead.getBoundingBox().getCenter());
                    HakariCombat.drive(user, lead.position().subtract(user.position()), 1.1);
                    if (user.distanceTo(lead) < 2.2) grabAt = age;
                }
                if (grabAt >= 0 && age >= grabAt && punches < cfg.overwhelmPunches && finalAt < 0) {
                    for (int k = 0; k < caught.size(); k++) {
                        var t = caught.get(k);
                        Statuses.apply(t, CombatStatus.GRABBED, 4);
                        HakariCombat.carry(user, t, 1.4 + k * 0.4);
                    }
                    Motion.set(user, f.scale(0.12).add(0, Math.min(0, user.getDeltaMovement().y), 0));
                    if ((age - grabAt) % cfg.overwhelmInterval == 0) {
                        Anim.play(user, punches % 2 == 0 ? "overwhelm_right" : "overwhelm_left");
                        float esc = 1f + punches * 0.1f;
                        Hit punch = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.overwhelmPunchDamage).tag(AttackTag.MELEE, AttackTag.UNBLOCKABLE)
                                .origin(user.getEyePosition()).knockback(Knockback.HOLD).hitstun(cfg.overwhelmInterval + 8)
                                .noComboScaling().fx("overwhelm_hit", esc).build();
                        HakariCombat.hitAll(punch, caught);
                        Fx.play(level, "overwhelm_punch", user.getEyePosition().add(f.scale(1.2)).add(0, -0.3, 0), f, esc, user.getId());
                        punches++;
                        if (punches == cfg.overwhelmPunches) finalAt = age + cfg.overwhelmInterval + 2;
                    }
                }
                if (age == finalAt) {
                    Anim.play(user, "overwhelm_final");
                    setPhase(2, 10);
                    for (var t : caught) Statuses.remove(t, CombatStatus.GRABBED);
                    Motion.set(user, f.scale(0.6));
                    Vec3 at = user.getEyePosition().add(f.scale(2.0)).add(0, -0.3, 0);
                    Hit last = Hit.builder(user, ID).type(ModDamageTypes.MELEE).damage(cfg.overwhelmFinalDamage)
                            .tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.UNBLOCKABLE).origin(user.getEyePosition())
                            .knockback(Knockback.directional(f, cfg.overwhelmFinalKnockback, 0.5)).hitstun(30).status(CombatStatus.LAUNCHED, 26)
                            .guardDamage(5).fx("overwhelm_final_hit", 1.6f).build();
                    java.util.List<net.minecraft.world.entity.LivingEntity> targets = new java.util.ArrayList<>(caught);
                    for (var t : HakariCombat.front(user, 3.4, 2.8, 2.8)) if (!targets.contains(t)) targets.add(t);
                    HakariCombat.hitAll(last, targets);
                    Fx.play(level, "overwhelm_final", at, f, 1f, user.getId());
                    Fx.shake(level, at, 28, 1.2f, 16);
                    Fx.flash(level, at, 20, 0x60A0FFB0, 5);
                    if (Destruction.allowed(level)) Destruction.sphere(level, at.add(f.scale(1.2)), 2.2, 6f, 40, user, null, "jjk:overwhelming_luck");
                    caught.clear();
                }
                if (finalAt >= 0 && age >= finalAt + 10) finish();
                if (age > START + 90) finish();
            }

            @Override
            public void end() {
                for (var t : caught) Statuses.remove(t, CombatStatus.GRABBED);
            }

            @Override
            public float movementMultiplier() {
                return 0.2f;
            }
        };
    }
}
