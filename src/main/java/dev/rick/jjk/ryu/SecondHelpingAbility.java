package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
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
 * Second Helping (JJS True Cannon 3, 15s). Aimed with the cursor: he treats himself to a new opponent within 70 studs,
 * dashing up over them with melee i-frames to deliver a merciless slam that bounces them skywards (12, unblockable,
 * bypasses ragdoll). If they were airborne they lose their ragdoll cancel once he locks on: he appears over them and
 * punches them down with a delayed impact (6 + 6). Landing it takes 3s off Restyle. The finisher crushes them like jelly.
 */
public final class SecondHelpingAbility extends Ability {
    public static final String ID = "second_helping";

    public SecondHelpingAbility() {
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
        return RyuCombat.cfg().secondHelpingCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        LivingEntity target = HakariCombat.aim(ctx.user(), RyuCombat.cfg().secondHelpingRange, ctx.targetHint());
        return new AbilityInstance(this, ctx) {
            private final boolean air = target != null && Combat.isAirborne(target);
            private Vec3 from = Vec3.ZERO;
            private int slamAt = -1, endAt = -1;

            @Override
            public void start() {
                from = user.position();
                if (target == null) {
                    // Nobody to treat himself to: a hop, nothing more.
                    Anim.play(user, "ryu_second_helping_whiff");
                    Motion.set(user, new Vec3(user.getDeltaMovement().x, 0.5, user.getDeltaMovement().z));
                    endAt = 12;
                    return;
                }
                Anim.play(user, air ? "ryu_second_helping_air" : "ryu_second_helping");
                Fx.play(level, "second_helping_lock", target.getBoundingBox().getCenter(), Vec3.ZERO, 1f, user.getId());
                Statuses.apply(user, CombatStatus.MELEE_ARMOR, 12);
                if (air) Statuses.apply(target, CombatStatus.TRUE_RAGDOLL, 20);
                setPhase(0, 14);
            }

            @Override
            public void tick() {
                JJKConfig.Ryu cfg = RyuCombat.cfg();
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (target == null || !target.isAlive() || target.level() != level) {
                    finish();
                    return;
                }
                if (slamAt < 0) {
                    // Over them: up above, then down.
                    Vec3 over = target.position().add(0, target.getBbHeight() + (air ? 0.6 : 1.4), 0).subtract(HakariCombat.flat(user).scale(0.4));
                    Vec3 to = over.subtract(user.position());
                    double dist = to.length();
                    if (dist > 1.1 && age < 12) {
                        Motion.set(user, to.normalize().scale(Math.min(dist, 2.2)));
                        HakariCombat.faceTowards(user, target.getEyePosition());
                        return;
                    }
                    slamAt = age;
                    Motion.set(user, new Vec3(0, -0.6, 0));
                    Anim.play(user, air ? "ryu_second_helping_punch" : "ryu_second_helping_slam");
                }
                int k = age - slamAt;
                if (k == 2) {
                    HitResult r;
                    if (RyuCombat.finishable(target)) {
                        r = RyuCombat.execute(user, target, ID, "second_helping_finisher");
                    } else if (air) {
                        // The punch, its impact delayed: they go down to the floor.
                        r = HakariCombat.hit(RyuCombat.strike(user, ID, cfg.secondHelpingAirPunch, true).tag(AttackTag.OTG)
                                .knockback(Knockback.HOLD).hitstun(14).fx("ryu_punch_heavy", 1f).build(), target);
                    } else {
                        r = HakariCombat.hit(RyuCombat.strike(user, ID, cfg.secondHelpingDamage, true).tag(AttackTag.OTG)
                                .knockback(Knockback.set(new Vec3(0, 1.15, 0))).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                                .fx("ryu_slam", 1.4f).build(), target);
                        Fx.play(level, "ryu_slam_ground", target.position(), Vec3.ZERO, 1f, user.getId());
                        Fx.shake(level, target.position(), 24, 0.9f, 10);
                    }
                    if (r.connected()) refundRestyle(cfg.secondHelpingRestyleRefund);
                    if (!air) endAt = age + 12;
                }
                if (air && k == 9 && target.isAlive()) {
                    Fx.play(level, "ryu_delayed_impact", target.getBoundingBox().getCenter(), new Vec3(0, -1, 0), 1f, user.getId());
                    HakariCombat.hit(RyuCombat.strike(user, ID, cfg.secondHelpingAirImpact, true).tag(AttackTag.OTG)
                            .knockback(Knockback.set(new Vec3(0, -1.6, 0))).hitstun(24).status(CombatStatus.SPIKED, 24)
                            .fx("ryu_slam", 1.2f).build(), target);
                    Fx.shake(level, target.position(), 24, 0.8f, 10);
                    endAt = age + 10;
                }
            }

            private void refundRestyle(int ticks) {
                for (AbilitySlot s : AbilitySlot.values()) {
                    if (caster.ability(s) instanceof RestyleAbility && caster.cooldown(s) > 0) {
                        caster.startCooldown(s, Math.max(1, caster.cooldown(s) - ticks));
                    }
                }
            }

            @Override
            public float movementMultiplier() {
                return 0f;
            }
        };
    }
}
