package dev.rick.jjk.yuta;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
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
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Rika Throw (JJS awakened Rika, 13s). He crosses his arms for Rika to pick him up; she winds her arm back and throws
 * him with force. Crashing into an enemy ragdolls them for damage that grows with his airtime (8 to 18). Hitting no one,
 * he takes the damage himself and is truly ragdolled, both growing with the airtime (0.5 to 22); aiming too high cuts
 * the flight short, and reaching the maximum airtime is the full miss. Blockable, uninterruptible, bypasses ragdoll.
 * The finisher: from at least 60 studs away, a sparking Black Flash erupts on contact.
 */
public final class RikaThrowAbility extends Ability {
    public static final String ID = "rika_throw";
    /** She picks him up, then winds back. */
    static final int PICKUP = 8, THROW = 18;

    public RikaThrowAbility() {
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
        return YutaCombat.cfg().throwCooldown;
    }

    @Override
    public @Nullable String checkActivation(AbilityContext ctx) {
        return YutaState.of(ctx.user()).rikaBusy(ctx.level().getGameTime()) ? "rika_busy" : null;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaCombat.backToYuta(ctx.caster());
        return new Instance(this, ctx);
    }

    static final class Instance extends AbilityInstance {
        @Nullable private final RikaEntity rika;
        private Vec3 start = Vec3.ZERO;
        private Vec3 vel = Vec3.ZERO;
        private int flying = -1;
        private int endAt = -1;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            rika = YutaCombat.summonRika(ctx.user());
        }

        @Override
        public void start() {
            YutaCombat.busy(user, THROW + 4);
            Anim.play(user, "yuta_rika_throw");
            if (rika != null) Anim.playOn(rika, "rika_throw");
            setPhase(0, THROW);
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (age < THROW) {
                // In her hand: picked up, then wound back over her shoulder.
                Statuses.apply(user, CombatStatus.HOVER, 3);
                Vec3 f = HakariCombat.flat(user);
                if (rika != null) {
                    rika.moveTo(user.position().subtract(f.scale(1.2)).add(0, age < PICKUP ? -0.8 : -1.2, 0), 1.6, 2);
                    Vec3 hand = rika.position().add(f.scale(0.9)).add(0, age < PICKUP ? 1.6 : 2.8, 0).subtract(f.scale(age < PICKUP ? 0 : 1.0));
                    if (age >= 4) Motion.set(user, hand.subtract(user.position()).scale(0.5));
                }
                if (age == PICKUP) setPhase(1, THROW - PICKUP);
                return;
            }
            if (age == THROW) {
                vel = user.getLookAngle().normalize().scale(cfg.throwSpeed);
                start = user.position();
                flying = 0;
                YutaCombat.free(user);
                setPhase(2, cfg.throwMaxAirtime);
                Fx.play(level, "rika_throw", user.position().add(0, 1, 0), vel, 1f, user.getId());
            }
            fly(cfg);
        }

        private void fly(JJKConfig.Yuta cfg) {
            flying++;
            // Aimed upward, gravity takes its toll sooner.
            vel = vel.add(0, -0.03 - Math.max(0, vel.y) * 0.04, 0);
            Motion.set(user, vel);
            Statuses.apply(user, CombatStatus.MELEE_ARMOR, 2);
            user.resetFallDistance();
            float t = Mth.clamp(flying / (float) cfg.throwMaxAirtime, 0f, 1f);
            List<LivingEntity> met = HitboxQuery.targets(user, HitShape.sphere(user.position().add(0, 0.9, 0).add(vel.normalize().scale(0.6)), 1.3), 0.3, false);
            for (LivingEntity v : met) {
                crash(cfg, v, t);
                return;
            }
            boolean landed = flying > 3 && (user.onGround() || user.horizontalCollision || user.verticalCollision);
            if (landed || flying >= cfg.throwMaxAirtime) miss(cfg, flying >= cfg.throwMaxAirtime ? 1f : t);
        }

        private void crash(JJKConfig.Yuta cfg, LivingEntity v, float t) {
            Vec3 d = vel.normalize();
            Motion.set(user, d.scale(-0.3).add(0, 0.35, 0));
            Fx.shake(level, v.position(), 24, 0.6f + t * 0.6f, 10);
            YutaCombat.setTarget(user, v);
            // From 60 studs away and more: the sparking Black Flash.
            if (YutaCombat.finishable(v) && v.position().distanceTo(start) >= 16.7) {
                Fx.play(level, "resolute_black_flash", v.getBoundingBox().getCenter(), d, 1.4f, user.getId());
                YutaCombat.execute(user, v, ID, "rika_throw_finisher");
                endAt = age + 12;
                return;
            }
            float dmg = Mth.lerp(t, cfg.throwMinDamage, cfg.throwMaxDamage);
            HitResult r = HakariCombat.hit(YutaCombat.strike(user, ID, dmg, false).tag(AttackTag.OTG, AttackTag.HEAVY)
                    .knockback(Knockback.set(d.scale(1.2 + t).add(0, 0.5, 0))).hitstun(30).status(CombatStatus.LAUNCHED, 30)
                    .noComboScaling().fx("hit_heavy", 1.2f + t * 0.6f).build(), v);
            if (r.outcome() == HitResult.Outcome.BLOCKED) Motion.set(user, d.scale(-0.6).add(0, 0.3, 0));
            Anim.play(user, "yuta_rika_throw_hit");
            endAt = age + 12;
        }

        /** No one hit: the damage and a true ragdoll are his, growing with the airtime. */
        private void miss(JJKConfig.Yuta cfg, float t) {
            float dmg = Mth.lerp(t, cfg.throwMissMinDamage, cfg.throwMissMaxDamage);
            Fx.play(level, "rika_throw_crash", user.position(), vel.normalize(), 1f + t, user.getId());
            Fx.shake(level, user.position(), 16, 0.4f + t * 0.5f, 8);
            if (user.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                user.hurtServer(sl, ModDamageTypes.source(sl, ModDamageTypes.MELEE, user, user), dmg);
            }
            Motion.set(user, vel.multiply(0.3, 0, 0.3).add(0, 0.2, 0));
            Statuses.apply(user, CombatStatus.KNOCKDOWN, 20 + Math.round(30 * t));
            Statuses.apply(user, CombatStatus.TRUE_RAGDOLL, 20 + Math.round(30 * t));
            endAt = age + 4;
        }

        @Override
        public boolean uninterruptible() {
            return true;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            YutaCombat.free(user);
        }
    }
}
