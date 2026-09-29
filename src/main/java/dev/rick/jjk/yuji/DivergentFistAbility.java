package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
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
 * Divergent Fist (JJS, 18s). He winds his arm back and lands a decent blow; a beat later the cursed energy lagging behind
 * it hits and launches them back. The punch is blockable and can't bypass ragdoll, the delayed impact is neither. If
 * that impact interrupts whatever they were doing, they're stunned by it instead of ragdolled.
 * <ul>
 *   <li>Black Flash: pressed again while his fist is pulled back and his body flashes white — the punch itself becomes a
 *       Black Flash that blasts them away (double damage if it interrupts an action; blocking doesn't count).</li>
 *   <li>Black Flash Chain: a Black Flash on someone's back stuns instead and Divergent Fist stays off cooldown, so another
 *       can follow; up to four, the last a heavy one ("KOKUSEN"). His side dash comes back after each; his front dash
 *       and M1s are shut off for a moment.</li>
 *   <li>Finishers: the delayed impact shatters the body; a finishing Black Flash launches them extremely far.</li>
 * </ul>
 */
public final class DivergentFistAbility extends Ability {
    public static final String ID = "divergent_fist";
    private static final String[] CHAIN_ANIMS = {"black_flash_punch", "black_flash_uppercut", "black_flash_dropkick"};

    public DivergentFistAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YujiCombat.cfg().divergentCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().divergentCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    public static final class Instance extends AbilityInstance implements Feintable {
        private boolean blackFlash;
        private boolean missedTiming;
        private int impactAt = -1;
        @Nullable private LivingEntity punched;
        private int endAt = -1;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        @Override
        public boolean feintable() {
            return age < YujiCombat.cfg().divergentWindup;
        }

        /** Divergent Fist pressed again during the wind-up: a Black Flash if it's while he flashes white. */
        public boolean blackFlashPress() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (age >= cfg.divergentWindup || blackFlash || missedTiming) return false;
            if (age >= cfg.blackFlashWindowStart && age <= cfg.blackFlashWindowEnd) {
                blackFlash = true;
                Fx.play(level, "black_flash_ready", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
            } else {
                missedTiming = true;
            }
            return true;
        }

        @Override
        public void start() {
            Anim.play(user, "divergent_windup");
            setPhase(0, YujiCombat.cfg().divergentWindup);
            Fx.play(level, "divergent_charge", user.position().add(0, 1, 0), user.getLookAngle(), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (endAt >= 0 && age >= endAt) {
                finish();
                return;
            }
            if (age < cfg.divergentWindup) {
                Motion.set(user, new Vec3(user.getDeltaMovement().x * 0.5, Math.min(0, user.getDeltaMovement().y), user.getDeltaMovement().z * 0.5));
                // The moment to press again: his body flashes white.
                if (age == cfg.blackFlashWindowStart) Fx.play(level, "divergent_flash", user.position().add(0, 1, 0), Vec3.ZERO, 1f, user.getId());
                return;
            }
            if (age == cfg.divergentWindup) {
                if (blackFlash) blackFlashPunch(cfg);
                else punch(cfg);
                return;
            }
            if (age == impactAt) impact(cfg);
        }

        private void punch(JJKConfig.Yuji cfg) {
            Anim.play(user, "divergent_punch");
            Motion.set(user, HakariCombat.flat(user).scale(0.4));
            LivingEntity t = HakariCombat.firstInFront(user, cfg.divergentReach, 1.6, 2.2);
            if (t != null && !YujiCombat.ragdolled(t)) {
                HitResult r = HakariCombat.hit(YujiCombat.strike(user, ID, cfg.divergentPunchDamage, false).knockback(Knockback.HOLD)
                        .hitstun(cfg.divergentImpactDelay + 6).fx("divergent_punch", 1f).build(), t);
                if (r.outcome().contacted()) punched = t;
            }
            impactAt = age + cfg.divergentImpactDelay;
            endAt = impactAt + 10;
        }

        /** The cursed energy arriving a beat after the fist. */
        private void impact(JJKConfig.Yuji cfg) {
            Vec3 fist = user.position().add(0, 1.1, 0).add(HakariCombat.flat(user).scale(1.6));
            LivingEntity t = punched;
            if (t == null || !t.isAlive() || t.distanceTo(user) > cfg.divergentReach + 1.5) {
                List<LivingEntity> near = HitboxQuery.targets(user, HitShape.sphere(fist, 1.6), 0.3, false);
                t = near.isEmpty() ? null : near.getFirst();
            }
            Fx.play(level, "divergent_impact", t != null ? t.getBoundingBox().getCenter() : fist, HakariCombat.flat(user), 1f, user.getId());
            if (t == null) return;
            if (YujiCombat.finishable(t)) {
                // The delayed energy courses through them and the body shatters.
                YujiCombat.execute(user, t, ID, "divergent_shatter");
                return;
            }
            boolean interrupted = YujiCombat.acting(t) && !Combat.isGuarding(t);
            var b = YujiCombat.strike(user, ID, cfg.divergentImpactDamage, true).fx("divergent_hit", 1.2f);
            if (interrupted) b.knockback(Knockback.directional(HakariCombat.flat(user), 0.25, 0)).hitstun(cfg.divergentInterruptStun);
            else b.knockback(Knockback.directional(HakariCombat.flat(user), 1.6, 0.45)).hitstun(24).status(CombatStatus.LAUNCHED, 22);
            HakariCombat.hit(b.build(), t);
            Fx.shake(level, t.position(), 16, 0.6f, 8);
        }

        private void blackFlashPunch(JJKConfig.Yuji cfg) {
            YujiState ys = YujiState.of(user);
            long now = level.getGameTime();
            LivingEntity t = HakariCombat.firstInFront(user, cfg.divergentReach + 0.4, 1.8, 2.4);
            if (t != null && YujiCombat.ragdolled(t)) t = null;
            boolean behind = t != null && YujiCombat.behind(user, t);
            boolean chaining = t != null && ys.chaining(t, now);
            int link = behind ? (chaining ? ys.chain + 1 : 1) : 0;
            boolean fourth = link >= cfg.blackFlashChainMax;
            // From behind it's one of three strikes at random (punch, uppercut, dropkick); the launch follows the strike.
            int style = link > 0 ? level.getRandom().nextInt(CHAIN_ANIMS.length) : 0;
            Anim.play(user, CHAIN_ANIMS[style]);
            Motion.set(user, HakariCombat.flat(user).scale(0.5));
            endAt = age + (fourth ? 30 : 12);
            if (t == null) {
                Fx.play(level, "black_flash", user.getEyePosition().add(user.getLookAngle().scale(1.6)), user.getLookAngle(), 0.8f, user.getId());
                YujiCharacter.endChain(caster);
                return;
            }
            Vec3 at = t.getBoundingBox().getCenter();
            boolean interrupted = YujiCombat.acting(t) && !Combat.isGuarding(t);
            if (YujiCombat.finishable(t)) {
                // A decisive Black Flash: the power amped, and the body sent extremely far.
                Fx.play(level, fourth ? "black_flash_kokusen" : "black_flash", at, HakariCombat.flat(user), fourth ? 2f : 1.5f, user.getId());
                if (fourth) Fx.play(level, "music:entrusted_music", at, Vec3.ZERO, 1f, user.getId());
                YujiCombat.execute(user, t, ID, "black_flash_finisher");
                Motion.set(t, HakariCombat.flat(user).scale(3.2).add(0, 1.1, 0));
                YujiCharacter.endChain(caster);
                return;
            }
            float damage = link == 0 ? cfg.blackFlashDamage : fourth ? cfg.blackFlashFourthDamage : cfg.blackFlashChainDamage;
            if (interrupted && !fourth) damage *= 2;
            var b = YujiCombat.strike(user, ID, damage, true).noComboScaling();
            Fx.play(level, fourth ? "black_flash_kokusen" : "black_flash", at, HakariCombat.flat(user), fourth ? 2f : 1f, user.getId());
            if (link > 0 && !fourth) {
                // On their back: stunned where they are, ready for the next one.
                b.knockback(Knockback.directional(HakariCombat.flat(user), 0.2, 0.05)).hitstun(cfg.blackFlashChainStun).fx("hit_heavy", 1.2f);
            } else {
                double power = fourth ? 3.2 : 2.2, lift = fourth ? 0.7 : 0.5;
                if (style == 1) {
                    power *= 0.35;
                    lift = fourth ? 1.9 : 1.4;
                } else if (style == 2) {
                    power *= 1.25;
                    lift *= 0.4;
                }
                b.knockback(Knockback.directional(HakariCombat.flat(user), power, lift)).hitstun(30)
                        .status(CombatStatus.LAUNCHED, fourth ? 40 : 26).fx("hit_heavy", 1.6f);
            }
            HitResult r = HakariCombat.hit(b.build(), t);
            Fx.shake(level, at, fourth ? 40 : 24, fourth ? 1.4f : 0.9f, fourth ? 18 : 10);
            if (!r.connected() || link == 0 || fourth) {
                YujiCharacter.endChain(caster);
                return;
            }
            // The chain goes on: Divergent Fist and the side dash come straight back; front dash and M1s wait a moment.
            ys.chain = link;
            ys.chainTarget = t;
            ys.chainUntil = now + cfg.blackFlashChainWindow;
            ys.frontDashLockedUntil = now + 24;
            caster.melee.cancel();
            for (AbilitySlot s : AbilitySlot.values()) {
                if (caster.ability(s) == ability || caster.ability(s) instanceof YujiDashAbility) caster.resetSlot(s);
            }
        }

        @Override
        public float movementMultiplier() {
            return 0.3f;
        }
    }
}
