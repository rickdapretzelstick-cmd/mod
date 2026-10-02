package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rika Downslam (JJS awakened Rika, 13s). Yuta motions and Rika slams her arm down on the target, pressing them into
 * the floor (8 on contact, 4 for the second impact). Airborne, she hits them with both arms and sends them to the floor
 * (8). Unblockable, uninterruptible, bypasses ragdoll. The finisher crushes them under her arms until they burst. Rika
 * attacking them gives Yuta their technique.
 */
public final class RikaDownslamAbility extends RikaMove {
    public static final String ID = "rika_downslam";
    private static final int WINDUP = 10;

    public RikaDownslamAbility() {
        super(ID, true);
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().downslamCooldown;
    }

    @Override
    protected AbilityInstance create(AbilityContext ctx, RikaEntity rika, @Nullable LivingEntity target) {
        return new RikaMove.Instance(this, ctx, rika, target, WINDUP + 18) {
            private boolean air;
            @Nullable private LivingEntity pinned;
            private Vec3 spot = Vec3.ZERO;

            @Override
            protected void begin() {
                air = target != null && airborne(target);
                Anim.play(user, "yuta_rika_command");
                anim(air ? "rika_downslam_air" : "rika_downslam");
            }

            @Override
            protected void step() {
                JJKConfig.Yuta cfg = YutaCombat.cfg();
                if (age < WINDUP) {
                    if (target != null && target.isAlive()) {
                        approach(target, 1.5, air ? 0.8 : 0.2, 1.8);
                        spot = target.position();
                    } else {
                        spot = front(2.0);
                    }
                    return;
                }
                if (age == WINDUP) contact(cfg);
                if (pinned != null && age > WINDUP && age < WINDUP + 10) {
                    Statuses.apply(pinned, CombatStatus.GRABBED, 3);
                    Vec3 at = new Vec3(spot.x, pinned.getY(), spot.z);
                    dev.rick.jjk.util.Motion.set(pinned, at.subtract(pinned.position()).scale(0.5).add(0, -0.3, 0));
                }
                if (age == WINDUP + 10 && pinned != null) second(cfg);
                if (age >= WINDUP + 18) finish();
            }

            private void contact(JJKConfig.Yuta cfg) {
                if (target != null && target.isAlive() && target.position().distanceTo(spot) < 2.4) spot = target.position();
                Fx.play(level, air ? "rika_double_slam" : "rika_downslam", spot.add(0, 0.2, 0), new Vec3(0, -1, 0), 1f, rika.getId());
                Fx.shake(level, spot, 20, 0.8f, 10);
                for (LivingEntity t : HitboxQuery.targets(user, HitShape.sphere(spot.add(0, 0.9, 0), 1.9), 0.3, false)) {
                    var b = YutaCombat.rika(user, rika, ID, air ? cfg.downslamAirDamage : cfg.downslamDamage, false)
                            .tag(AttackTag.UNBLOCKABLE, AttackTag.OTG).noComboScaling();
                    if (air) {
                        b.knockback(Knockback.set(new Vec3(0, -1.6, 0))).hitstun(36).status(CombatStatus.SPIKED, 50).fx("yuta_impact", 1.4f);
                    } else {
                        b.knockback(Knockback.set(new Vec3(0, -0.5, 0))).hitstun(30).fx("yuta_impact", 1.2f);
                    }
                    HitResult r = HakariCombat.hit(b.build(), t);
                    copyOnHit(r);
                    if (!air && r.connected() && pinned == null) pinned = t;
                }
            }

            /** The pressure on them: a second impact, or the crush that bursts them. */
            private void second(JJKConfig.Yuta cfg) {
                Statuses.remove(pinned, CombatStatus.GRABBED);
                if (!pinned.isAlive()) return;
                Fx.play(level, "rika_downslam", pinned.position(), new Vec3(0, -1, 0), 1.2f, rika.getId());
                if (YutaCombat.finishable(pinned)) {
                    YutaCombat.execute(user, pinned, ID, "rika_crush_finisher");
                    return;
                }
                HakariCombat.hit(YutaCombat.rika(user, rika, ID, cfg.downslamSecondDamage, false).tag(AttackTag.UNBLOCKABLE, AttackTag.OTG)
                        .knockback(Knockback.set(new Vec3(0, 0.25, 0))).hitstun(30).status(CombatStatus.KNOCKDOWN, 30)
                        .noComboScaling().fx("yuta_impact", 1.3f).build(), pinned);
            }

            @Override
            public void end() {
                if (pinned != null) Statuses.remove(pinned, CombatStatus.GRABBED);
                super.end();
            }
        };
    }
}
