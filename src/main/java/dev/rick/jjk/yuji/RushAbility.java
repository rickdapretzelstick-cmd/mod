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

import java.util.List;

/**
 * Rush (JJS King of Curses, 15s). He tears forward in a straight line at incredible speed. Whoever he runs into is hurled
 * ahead; he chases them down for a knee that sends them skyward, then leaps up after them and slams them back down.
 * Unblockable; can't bypass ragdoll.
 */
public final class RushAbility extends Ability {
    public static final String ID = "rush";

    public RushAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().rushCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new AbilityInstance(this, ctx) {
            private final Vec3 dir = HakariCombat.flat(ctx.user());
            @Nullable private LivingEntity victim;
            private int hitAt = -1, kneeAt = -1, slamAt = -1;
            private int endAt = -1;

            @Override
            public void start() {
                Anim.play(user, "rush_run");
                setPhase(0, YujiCombat.cfg().rushTicks);
                Fx.play(level, "rush_start", user.position().add(0, 1, 0), dir, 1f, user.getId());
            }

            @Override
            public void tick() {
                JJKConfig.Yuji cfg = YujiCombat.cfg();
                if (endAt >= 0) {
                    if (age >= endAt) finish();
                    return;
                }
                if (victim == null) {
                    // Straight ahead, no steering.
                    HakariCombat.drive(user, dir, cfg.rushSpeed);
                    if (age % 2 == 0) Fx.play(level, "rush_step", user.position(), dir, 1f, user.getId());
                    List<LivingEntity> met = CursedStrikesAbility.takeable(user, 1.8, 1.6, 2.2);
                    if (!met.isEmpty()) {
                        LivingEntity t = met.getFirst();
                        HitResult r = HakariCombat.hit(YujiCombat.strike(user, ID, cfg.rushImpactDamage, true)
                                .knockback(Knockback.directional(dir, 1.6, 0.25)).hitstun(40).fx("rush_hit", 1.2f).build(), t);
                        if (r.connected()) {
                            victim = t;
                            hitAt = age;
                            Anim.play(user, "rush_chase");
                            setPhase(1, 30);
                            return;
                        }
                    }
                    if (age >= cfg.rushTicks) {
                        Motion.set(user, user.getDeltaMovement().scale(0.25));
                        endAt = age + 6;
                    }
                    return;
                }
                if (!victim.isAlive()) {
                    finish();
                    return;
                }
                HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
                if (kneeAt < 0) {
                    // Chasing after them.
                    Vec3 to = victim.position().subtract(user.position());
                    double d = Math.sqrt(to.x * to.x + to.z * to.z);
                    if (d > 1.8 && age - hitAt < 14) {
                        HakariCombat.drive(user, to, Math.min(cfg.rushSpeed, d * 0.5));
                        return;
                    }
                    kneeAt = age;
                    Anim.play(user, "rush_knee");
                    HakariCombat.hit(YujiCombat.strike(user, ID, cfg.rushKneeDamage, true).knockback(Knockback.set(new Vec3(dir.x * 0.1, 1.25, dir.z * 0.1)))
                            .hitstun(44).status(CombatStatus.LAUNCHED, 44).fx("rush_knee", 1.4f).build(), victim);
                    Fx.shake(level, victim.position(), 18, 0.7f, 8);
                    return;
                }
                int t = age - kneeAt;
                if (t == 6) {
                    // Up after them.
                    Anim.play(user, "rush_leap");
                    Vec3 to = victim.position().subtract(user.position());
                    Motion.set(user, new Vec3(to.x * 0.2, Math.max(0.9, to.y * 0.3 + 0.6), to.z * 0.2));
                }
                if (t > 6 && slamAt < 0) {
                    Statuses.apply(user, CombatStatus.HOVER, 2);
                    if (user.distanceTo(victim) < 2.6 || t > 18) {
                        slamAt = age;
                        Anim.play(user, "rush_slam");
                        HakariCombat.hit(YujiCombat.strike(user, ID, cfg.rushSlamDamage, true).knockback(Knockback.set(new Vec3(0, -1.6, 0)))
                                .hitstun(34).status(CombatStatus.SPIKED, 36).fx("hit_slam", 1.4f).build(), victim);
                        Fx.play(level, "rush_slam", victim.position(), new Vec3(0, -1, 0), 1f, user.getId());
                        endAt = age + 12;
                    }
                }
            }

            @Override
            public float movementMultiplier() {
                return 0.2f;
            }
        };
    }
}
