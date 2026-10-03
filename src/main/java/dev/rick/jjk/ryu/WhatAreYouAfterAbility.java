package dev.rick.jjk.ryu;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.combat.Targeting;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Destruction;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * "What are you after?" (JJS True Cannon awakened 1, 18s). He slams the floor under him with insane force, bouncing his
 * target up (10), leaps into the sky and makes an aimable lunge through the air; crashing into his opponent, the two
 * trade a punch (20) before he overpowers them and launches them away (5; 5 to himself). Airborne, he skips the slam.
 * Feintable until the lunge connects.
 */
public final class WhatAreYouAfterAbility extends Ability {
    public static final String ID = "what_are_you_after";

    public WhatAreYouAfterAbility() {
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
        return RyuCombat.cfg().afterCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    static final class Instance extends AbilityInstance implements Feintable {
        private final boolean air;
        private int leapAt, lungeAt;
        private int connectAt = -1, endAt = -1;
        @Nullable private LivingEntity victim;

        Instance(Ability a, AbilityContext ctx) {
            super(a, ctx);
            air = Combat.isAirborne(ctx.user());
            leapAt = air ? 0 : 9;
            lungeAt = air ? 3 : 18;
        }

        @Override
        public boolean feintable() {
            return connectAt < 0 && endAt < 0;
        }

        @Override
        public void start() {
            Anim.play(user, air ? "ryu_after_lunge" : "ryu_after_slam");
            RyuCombat.sfx(user, air ? "ryu_after_dash" : "ryu_after_swing", 1f);
            setPhase(0, lungeAt + 12);
        }

        @Override
        public void tick() {
            JJKConfig.Ryu cfg = RyuCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (!air && age == 6) {
                // The floor, slammed: everyone near bounces up.
                Fx.play(level, "ryu_floor_slam", user.position(), Vec3.ZERO, 1f, user.getId());
                Fx.shake(level, user.position(), 28, 1f, 12);
                Destruction.sphere(level, user.position().add(0, -0.6, 0), 2.2, 30f, 20, user, null);
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(3.2, 1, 3.2),
                        e -> Targeting.canTarget(user, e))) {
                    HakariCombat.hit(RyuCombat.strike(user, ID, cfg.afterSlam, true).tag(AttackTag.OTG)
                            .knockback(Knockback.set(new Vec3(0, 1.0, 0))).hitstun(26).status(CombatStatus.LAUNCHED, 26).fx(RyuCombat.hitFx("ryu_after_first_hit", true), 1f).build(), t);
                }
            }
            if (age == leapAt && !air) {
                Anim.play(user, "ryu_after_leap");
                RyuCombat.sfx(user, "ryu_after_dash", 1f);
                Motion.set(user, new Vec3(0, 1.05, 0));
            }
            if (age >= lungeAt && connectAt < 0) {
                if (age == lungeAt) Anim.play(user, "ryu_after_lunge");
                // The lunge goes where he aims.
                Statuses.apply(user, CombatStatus.HOVER, 3);
                Motion.set(user, user.getLookAngle().normalize().scale(1.25));
                LivingEntity t = HakariCombat.firstInFront(user, 1.6, 1.8, 2.4);
                if (t != null) {
                    victim = t;
                    connectAt = age;
                    Anim.play(user, "ryu_after_trade");
                    HakariCombat.hit(RyuCombat.strike(user, ID, cfg.afterPunch, true).knockback(Knockback.HOLD).hitstun(18).fx(RyuCombat.hitFx("ryu_after_hit", true), 1.3f).build(), t);
                    Fx.play(level, "ryu_trade", t.getBoundingBox().getCenter(), user.getLookAngle(), 1f, user.getId());
                } else if (age > lungeAt + 14 || !Combat.isAirborne(user) && age > lungeAt + 2) {
                    Anim.play(user, "ryu_after_miss");
                    endAt = age + 10;
                }
                return;
            }
            if (victim != null) {
                Motion.set(user, Vec3.ZERO);
                Statuses.apply(user, CombatStatus.HOVER, 3);
                HakariCombat.carry(user, victim, 1.4);
                if (age == connectAt + 8) {
                    Vec3 away = victim.position().subtract(user.position()).normalize();
                    if (RyuCombat.finishable(victim)) {
                        RyuCombat.execute(user, victim, ID, "ryu_punch_heavy");
                    } else {
                        HakariCombat.hit(RyuCombat.strike(user, ID, cfg.afterLaunch, true)
                                .knockback(Knockback.set(away.scale(1.8).add(0, 0.4, 0))).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                                .fx(RyuCombat.hitFx("ryu_after_dismember", true), 1.5f).build(), victim);
                    }
                    RyuCombat.selfDamage(user, cfg.afterSelf);
                    Fx.shake(level, user.position(), 28, 1.1f, 12);
                    victim = null;
                    endAt = age + 10;
                }
            }
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }
    }
}
