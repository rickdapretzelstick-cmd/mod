package dev.rick.jjk.yuta;

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
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Second Wind (JJS, 16s). Cursed energy in his feet, Yuta rushes 20 studs forward with melee i-frames; whoever he meets
 * is grabbed by the face (2) and slammed into the floor (8). 360-blockable; can't bypass ragdoll. A whiff leaves a
 * second try before the cooldown runs; on block the endlag is much longer.
 *
 * <p>Variant (Severing Path right as he collides with a defenseless standing target): he pummels them instead, five
 * blows of 3, the last throwing them away; with the katana out, a few swings and then a spinning axe kick that knocks
 * them down. It counts as a failed Second Wind, puts Severing Path on cooldown, and can't be done while Severing Path
 * is on cooldown.
 */
public final class SecondWindAbility extends Ability {
    public static final String ID = "second_wind";

    public SecondWindAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YutaCombat.cfg().secondWindCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YutaCombat.cfg().secondWindCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        YutaState s = YutaState.of(ctx.user());
        boolean retry = s.secondWindRetry && ctx.level().getGameTime() <= s.secondWindRetryUntil;
        s.secondWindRetry = false;
        return new Instance(this, ctx, retry);
    }

    public static final class Instance extends AbilityInstance {
        private final AbilitySlot slot;
        private final int mode;
        private final boolean secondTry;
        private final boolean katana;
        private final Vec3 dir;
        @Nullable private LivingEntity victim;
        private int metAt = -1;
        private int endAt = -1;
        private boolean pummel;
        private boolean failed;
        private int hits;

        Instance(Ability ability, AbilityContext ctx, boolean secondTry) {
            super(ability, ctx);
            slot = ctx.slot();
            mode = ctx.caster().mode();
            this.secondTry = secondTry;
            katana = Combat.has(ctx.user(), CombatStatus.KATANA);
            dir = HakariCombat.flat(ctx.user());
        }

        /** Severing Path pressed right as he collides with someone: the pummelling. */
        public boolean pummelPress(AbilityCaster caster, AbilitySlot severingSlot) {
            if (victim == null || pummel || age - metAt > 4 || !caster.isReady(severingSlot)) return false;
            if (Combat.isGuarding(victim) || YutaCombat.ragdolled(victim) || Combat.isAirborne(victim)) return false;
            pummel = true;
            failed = true;
            caster.startCooldown(severingSlot, YutaCombat.cfg().severingCooldown);
            metAt = age;
            Anim.play(user, katana ? "yuta_second_wind_swords" : "yuta_second_wind_pummel");
            setPhase(3, 30);
            return true;
        }

        @Override
        public void start() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            Anim.play(user, "yuta_second_wind");
            setPhase(0, cfg.secondWindTicks);
            Statuses.apply(user, CombatStatus.MELEE_ARMOR, cfg.secondWindTicks + 2);
            Fx.play(level, "second_wind", user.position().add(0, 0.2, 0), dir, 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuta cfg = YutaCombat.cfg();
            if (endAt >= 0) {
                if (victim == null) Motion.set(user, user.getDeltaMovement().multiply(0.4, 1, 0.4));
                if (age >= endAt) finish();
                return;
            }
            if (victim != null) {
                if (pummel) pummel(cfg);
                else grab(cfg);
                return;
            }
            HakariCombat.drive(user, dir, cfg.secondWindDistance / cfg.secondWindTicks);
            List<LivingEntity> met = HitboxQuery.targets(user, HitShape.sphere(user.position().add(0, 1.0, 0).add(dir.scale(0.8)), 1.2), 0.3, false);
            for (LivingEntity t : met) {
                if (YutaCombat.ragdolled(t)) continue;
                // 360-blockable: the grab is stopped by a guard from any side.
                HitResult r = HakariCombat.hit(YutaCombat.strike(user, ID, 0, false).tag(AttackTag.BLOCKABLE_360)
                        .knockback(Knockback.HOLD).hitstun(8).fx("hit_light", 0.6f).build(), t);
                if (r.outcome() == HitResult.Outcome.BLOCKED) {
                    Motion.set(user, dir.scale(-0.4).add(0, 0.15, 0));
                    Anim.play(user, "yuta_second_wind_blocked");
                    endAt = age + (secondTry ? 34 : 24);
                    return;
                }
                if (r.connected()) {
                    victim = t;
                    metAt = age;
                    YutaCombat.setTarget(user, t);
                    Motion.set(user, Vec3.ZERO);
                    Statuses.apply(t, CombatStatus.GRABBED, 6);
                    setPhase(1, 20);
                    return;
                }
            }
            if (age >= cfg.secondWindTicks) {
                failed = true;
                Anim.play(user, "yuta_second_wind_whiff");
                endAt = age + (secondTry ? 18 : 10);
            }
        }

        /** Grabbed by the face, lifted, and slammed into the floor. */
        private void grab(JJKConfig.Yuta cfg) {
            int t = age - metAt;
            if (!victim.isAlive()) {
                finish();
                return;
            }
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            Statuses.apply(victim, CombatStatus.GRABBED, 3);
            if (t < 5) {
                HakariCombat.carry(user, victim, 1.1);
                return;
            }
            if (t == 5) {
                Anim.play(user, "yuta_second_wind_slam");
                HakariCombat.hit(YutaCombat.strike(user, ID, cfg.secondWindGrabDamage, true).knockback(Knockback.HOLD).hitstun(20).fx("hit_light", 0.8f).build(), victim);
            }
            if (t < 12) {
                Vec3 at = user.position().add(dir.scale(1.2)).add(0, t < 9 ? 1.0 : 0.1, 0);
                Motion.set(victim, at.subtract(victim.position()).scale(0.6));
                return;
            }
            Statuses.remove(victim, CombatStatus.GRABBED);
            Fx.play(level, "second_wind_slam", victim.position(), new Vec3(0, -1, 0), 1f, user.getId());
            Fx.shake(level, victim.position(), 16, 0.7f, 8);
            if (YutaCombat.finishable(victim)) {
                YutaCombat.execute(user, victim, ID, "second_wind_finisher");
            } else {
                HakariCombat.hit(YutaCombat.strike(user, ID, cfg.secondWindSlamDamage, true).tag(AttackTag.OTG)
                        .knockback(Knockback.set(new Vec3(0, -0.6, 0))).hitstun(30).status(CombatStatus.KNOCKDOWN, 30).fx("hit_slam", 1.2f).build(), victim);
            }
            endAt = age + 10;
        }

        /** Five blows (or the katana's swings and an axe kick), the last one throwing them away. */
        private void pummel(JJKConfig.Yuta cfg) {
            int t = age - metAt;
            if (!victim.isAlive()) {
                finish();
                return;
            }
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            Statuses.apply(victim, CombatStatus.GRABBED, 3);
            HakariCombat.carry(user, victim, 1.5);
            if (t % 5 != 4) return;
            hits++;
            boolean last = hits >= 5;
            if (last) Statuses.remove(victim, CombatStatus.GRABBED);
            var b = YutaCombat.strike(user, ID, cfg.secondWindPummelDamage, false).tag(AttackTag.OTG).noComboScaling();
            if (!last) {
                b.knockback(Knockback.HOLD).hitstun(14).fx(katana ? "severing_swing" : "hit_light", 0.9f);
            } else if (katana) {
                // The spinning axe kick: knocked down.
                b.knockback(Knockback.set(dir.scale(0.2).add(0, -0.7, 0))).hitstun(30).status(CombatStatus.KNOCKDOWN, 34).fx("hit_slam", 1.2f);
            } else {
                b.knockback(Knockback.directional(dir, 1.4, 0.45)).hitstun(26).status(CombatStatus.LAUNCHED, 22).fx("hit_heavy", 1.2f);
            }
            HitResult r = HakariCombat.hit(b.build(), victim);
            if (last && YutaCombat.finishable(victim)) YutaCombat.execute(user, victim, ID, "second_wind_finisher");
            if (r.outcome() == HitResult.Outcome.BLOCKED || last) {
                Statuses.remove(victim, CombatStatus.GRABBED);
                endAt = age + 10;
            }
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
            if (failed && !secondTry) {
                // A failed first use: no cooldown, and a second try is ready.
                caster.resetSlot(slot, mode);
                YutaState s = YutaState.of(user);
                s.secondWindRetry = true;
                s.secondWindRetryUntil = level.getGameTime() + 200;
            }
        }
    }
}
