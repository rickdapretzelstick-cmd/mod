package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika Slam (JJS awakened Rika, 13s). She hovers over to the target, grabs them by the leg and slams them into the
 * ground five times in anger (1 for the grab, 2 a slam, 3 for the last). Unblockable, uninterruptible, bypasses
 * ragdoll. They can evade out of her grasp early. Rika attacking them gives Yuta their technique.
 */
public final class RikaSlamAbility extends RikaMove {
    public static final String ID = "rika_slam";
    private static final int REACH = 16, SLAMS = 5, SLAM_EVERY = 9;

    public RikaSlamAbility() {
        super(ID, true);
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().rikaSlamCooldown;
    }

    @Override
    protected AbilityInstance create(AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity target) {
        return new RikaMove.Instance(this, ctx, rika, target, REACH + SLAMS * SLAM_EVERY + 10) {
            @Nullable private LivingEntity held;
            private int grabbedAt = -1;
            private int slams;
            private int endAt = -1;

            @Override
            protected void begin() {
                Anim.play(user, "yuta_rika_command");
                anim("rika_slam_reach");
            }

            @Override
            protected void step() {
                JJKConfig.Yuta cfg = YutaCombat.cfg();
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (held == null) {
                    if (target == null || !target.isAlive()) {
                        if (age > 6) finish();
                        return;
                    }
                    approach(target, 1.0, 0.3, 1.8);
                    if (rika.position().distanceTo(target.position()) < 2.6 + target.getBbWidth()) grab(cfg);
                    else if (age >= REACH) {
                        endAt = age + 6;
                    }
                    return;
                }
                if (!held.isAlive()) {
                    finish();
                    return;
                }
                // Evaded out of her grasp.
                if (Combat.state(held).isEvading()) {
                    Statuses.remove(held, CombatStatus.GRABBED);
                    held = null;
                    endAt = age + 6;
                    return;
                }
                int t = age - grabbedAt;
                Statuses.apply(held, CombatStatus.GRABBED, 4);
                rika.moveTo(rika.position(), 0.2, 2);
                // Swung up by the leg, then down into the ground.
                int phase = t % SLAM_EVERY;
                Vec3 f = facing();
                Vec3 hand = rika.position().add(f.scale(1.4));
                Vec3 at = phase < 5 ? hand.add(0, 2.4, 0).subtract(f.scale(phase < 3 ? 1.0 : 0)) : hand.add(f.scale(0.6));
                dev.rick.jjk.util.Motion.set(held, at.subtract(held.position()).scale(0.55));
                if (phase == SLAM_EVERY - 1) slam(cfg);
            }

            private void grab(JJKConfig.Yuta cfg) {
                HitResult r = HakariCombat.hit(YutaCombat.rika(user, rika, ID, cfg.rikaSlamGrabDamage, false)
                        .tag(AttackTag.UNBLOCKABLE, AttackTag.OTG).knockback(Knockback.HOLD).hitstun(20).fx("rika_grab", 0.8f).build(), target);
                copyOnHit(r);
                if (!r.connected()) {
                    endAt = age + 6;
                    return;
                }
                held = target;
                grabbedAt = age;
                anim("rika_slam");
                Statuses.apply(held, CombatStatus.GRABBED, 4);
            }

            private void slam(JJKConfig.Yuta cfg) {
                slams++;
                boolean last = slams >= SLAMS;
                Vec3 at = held.position();
                Fx.play(level, "rika_slam", at, new Vec3(0, -1, 0), last ? 1.3f : 1f, rika.getId());
                Fx.shake(level, at, 16, last ? 0.8f : 0.5f, 6);
                var b = YutaCombat.rika(user, rika, ID, last ? cfg.rikaSlamLastDamage : cfg.rikaSlamDamage, false)
                        .tag(AttackTag.UNBLOCKABLE, AttackTag.OTG).noComboScaling().fx("yuta_impact", last ? 1.3f : 0.9f);
                if (last) {
                    Statuses.remove(held, CombatStatus.GRABBED);
                    b.knockback(Knockback.set(facing().scale(0.6).add(0, 0.3, 0))).hitstun(30).status(CombatStatus.KNOCKDOWN, 30);
                } else {
                    b.knockback(Knockback.set(new Vec3(0, -0.4, 0))).hitstun(20);
                }
                HakariCombat.hit(b.build(), held);
                if (last) {
                    held = null;
                    endAt = age + 8;
                }
            }

            @Override
            public void end() {
                if (held != null) Statuses.remove(held, CombatStatus.GRABBED);
                super.end();
            }
        };
    }
}
