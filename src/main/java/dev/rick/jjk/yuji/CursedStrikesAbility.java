package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
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
import dev.rick.jjk.core.hitbox.HitShape;
import dev.rick.jjk.core.hitbox.HitboxQuery;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cursed Strikes (JJS, 14s). Eyes glowing red, Vessel slides forward with his hands poised; whoever he meets eats a
 * flurry of punches (he has bullet i-frames through it) finished by a right calf kick that leaves them stunned in place.
 * His M1 count is kept for after, and his front dash is shut off for a moment. A guard cuts the slide short. 360
 * blockable; can't bypass ragdoll. The finisher kicks again, strikes the floor to launch them and spin-kicks them away.
 *
 * <p>Airborne: a hop, then a cursed-energy dropkick diving at the ground that grounds whoever it lands on (unblockable);
 * it can't be aimed straight down, nor feinted after the hop. Its finisher is a Black Flash on landing.
 */
public final class CursedStrikesAbility extends Ability {
    public static final String ID = "cursed_strikes";
    private static final int HOP = 6;

    public CursedStrikesAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YujiCombat.cfg().strikesCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().strikesCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return Combat.isAirborne(ctx.user()) ? new Air(this, ctx) : new Ground(this, ctx);
    }

    /** Targets in front the move can take (not ragdolled). */
    static List<LivingEntity> takeable(LivingEntity user, double reach, double width, double height) {
        List<LivingEntity> out = HakariCombat.front(user, reach, width, height);
        out.removeIf(YujiCombat::ragdolled);
        return out;
    }

    private static final class Ground extends AbilityInstance implements Feintable {
        private final int savedChain;
        @Nullable private LivingEntity victim;
        private int grabbedAt = -1;
        private boolean finisher;
        private int endAt = -1;

        Ground(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            this.savedChain = ctx.caster().melee.chainIndex();
        }

        @Override
        public boolean feintable() {
            return age < YujiCombat.cfg().strikesWindup;
        }

        @Override
        public void start() {
            Anim.play(user, "cursed_strikes_ready");
            setPhase(0, YujiCombat.cfg().strikesWindup);
            Fx.play(level, "cursed_strikes_start", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (age < cfg.strikesWindup) {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                return;
            }
            if (victim == null) {
                slide(cfg);
                return;
            }
            flurry(cfg);
        }

        private void slide(JJKConfig.Yuji cfg) {
            if (age == cfg.strikesWindup) {
                Anim.play(user, "cursed_strikes_slide");
                setPhase(1, cfg.strikesSlideTicks);
                Fx.play(level, "cursed_strikes_slide", user.position().add(0, 0.9, 0), HakariCombat.flat(user), 1f, user.getId());
            }
            HakariCombat.drive(user, HakariCombat.flat(user), cfg.strikesSlideSpeed);
            List<LivingEntity> met = takeable(user, 1.6, 1.4, 2.0);
            if (!met.isEmpty()) {
                LivingEntity t = met.getFirst();
                HitResult r = HakariCombat.hit(YujiCombat.strike(user, ID, cfg.strikesGrabDamage, false).tag(AttackTag.BLOCKABLE_360)
                        .knockback(Knockback.HOLD).hitstun(40).fx("hit_light", 1f).build(), t);
                if (r.connected()) {
                    victim = t;
                    grabbedAt = age;
                    finisher = YujiCombat.finishable(t);
                    Motion.set(user, Vec3.ZERO);
                    Statuses.apply(user, CombatStatus.BULLET_ARMOR, cfg.strikesPunches * cfg.strikesPunchInterval + 14);
                    Anim.play(user, "cursed_strikes_flurry");
                    setPhase(2, cfg.strikesPunches * cfg.strikesPunchInterval + 10);
                    return;
                }
                if (r.outcome().contacted()) {
                    // Blocked: the slide is cut short.
                    Motion.set(user, user.getDeltaMovement().scale(0.1));
                    Anim.play(user, "cursed_strikes_blocked");
                    endAt = age + 8;
                    return;
                }
            }
            if (age >= cfg.strikesWindup + cfg.strikesSlideTicks) {
                Motion.set(user, user.getDeltaMovement().scale(0.25));
                endAt = age + 6;
            }
        }

        private void flurry(JJKConfig.Yuji cfg) {
            if (!victim.isAlive()) {
                finish();
                return;
            }
            int t = age - grabbedAt;
            int punches = cfg.strikesPunches * cfg.strikesPunchInterval;
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
            HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
            Statuses.apply(victim, CombatStatus.HITSTUN, 8);
            if (t < punches) {
                if (t > 0 && t % cfg.strikesPunchInterval == 0) {
                    HakariCombat.hit(YujiCombat.strike(user, ID, cfg.strikesPunchDamage, true).knockback(Knockback.HOLD).hitstun(12)
                            .fx("cursed_strikes_punch", 1f).build(), victim);
                }
                return;
            }
            if (!finisher) {
                if (t == punches) {
                    // The right calf kick: stunned where they stand.
                    Anim.play(user, "cursed_strikes_kick");
                    HakariCombat.hit(YujiCombat.strike(user, ID, cfg.strikesKickDamage, true).knockback(Knockback.NONE).hitstun(cfg.strikesKickStun)
                            .fx("cursed_strikes_kick", 1.2f).build(), victim);
                    landed(cfg);
                    endAt = age + 6;
                }
                return;
            }
            // The finisher: another kick, the floor struck to launch them, and the spin kick that sends them flying.
            if (t == punches) {
                Anim.play(user, "cursed_strikes_kick");
                HakariCombat.hit(YujiCombat.strike(user, ID, cfg.strikesFinisherKickDamage, true).knockback(Knockback.NONE).hitstun(30)
                        .fx("cursed_strikes_kick", 1f).build(), victim);
            } else if (t == punches + 10) {
                Anim.play(user, "cursed_strikes_floor");
                Fx.play(level, "cursed_strikes_floor", user.position().add(HakariCombat.flat(user).scale(1.2)), new Vec3(0, 1, 0), 1f, user.getId());
                HakariCombat.hit(YujiCombat.strike(user, ID, cfg.strikesFinisherLaunchDamage, true).knockback(Knockback.set(new Vec3(0, 1.0, 0)))
                        .hitstun(40).status(CombatStatus.LAUNCHED, 40).fx("hit_launch", 1.2f).build(), victim);
            } else if (t == punches + 22) {
                Anim.play(user, "cursed_strikes_spin");
                HakariCombat.faceTowards(user, victim.getBoundingBox().getCenter());
                YujiCombat.execute(user, victim, ID, "cursed_strikes_finisher");
                Fx.shake(level, victim.position(), 24, 0.9f, 12);
                landed(cfg);
                endAt = age + 12;
            }
        }

        /** Landed: his M1 count carries over, and his front dash is shut off for a moment. */
        private void landed(JJKConfig.Yuji cfg) {
            caster.melee.setChainIndex(savedChain, level.getGameTime());
            YujiState.of(user).frontDashLockedUntil = level.getGameTime() + cfg.strikesFrontDashLock;
        }

        @Override
        public float movementMultiplier() {
            return 0.2f;
        }

        @Override
        public void end() {
            if (victim != null) Statuses.remove(victim, CombatStatus.GRABBED);
        }
    }

    private static final class Air extends AbilityInstance implements Feintable {
        private Vec3 dir = Vec3.ZERO;
        private boolean landedHit;

        Air(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        @Override
        public boolean feintable() {
            return age < HOP;
        }

        @Override
        public void start() {
            Anim.play(user, "cursed_strikes_hop");
            setPhase(0, HOP);
            Motion.set(user, new Vec3(user.getDeltaMovement().x * 0.3, 0.42, user.getDeltaMovement().z * 0.3));
            Fx.play(level, "cursed_strikes_spin", user.position().add(0, 0.9, 0), Vec3.ZERO, 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (age < HOP) return;
            if (age == HOP) {
                // Can't be aimed straight down: always a forward dive.
                Vec3 f = HakariCombat.flat(user);
                double down = Math.max(0.45, Math.min(0.8, -user.getLookAngle().y + 0.35));
                dir = new Vec3(f.x, -down, f.z).normalize();
                Anim.play(user, "cursed_strikes_dropkick");
                setPhase(1, cfg.strikesAirMaxTicks);
                Fx.play(level, "cursed_strikes_dive", user.position().add(0, 0.9, 0), dir, 1f, user.getId());
            }
            Motion.set(user, dir.scale(cfg.strikesAirSpeed));
            Statuses.apply(user, CombatStatus.HOVER, 2);
            Vec3 feet = user.position().add(0, 0.4, 0);
            List<LivingEntity> hits = HitboxQuery.targets(user, HitShape.capsule(feet, feet.add(dir.scale(1.6)), 1.0), 0.3, false);
            if (!hits.isEmpty()) {
                LivingEntity t = hits.getFirst();
                if (YujiCombat.finishable(t)) {
                    // A Black Flash surges as it lands, confirming the kill.
                    Fx.play(level, "black_flash", t.getBoundingBox().getCenter(), dir, 1.3f, user.getId());
                    YujiCombat.execute(user, t, ID, "black_flash_finisher");
                } else {
                    HakariCombat.hit(YujiCombat.strike(user, ID, cfg.strikesAirDamage, true)
                            .knockback(Knockback.set(new Vec3(dir.x * 0.2, -1.3, dir.z * 0.2))).hitstun(34)
                            .status(CombatStatus.SPIKED, 30).fx("hit_slam", 1.4f).build(), t);
                }
                landedHit = true;
                impact();
                return;
            }
            if (user.onGround() || age > HOP + cfg.strikesAirMaxTicks) impact();
        }

        private void impact() {
            Motion.set(user, new Vec3(0, 0.25, 0));
            Anim.play(user, "cursed_strikes_land");
            Fx.play(level, "cursed_strikes_impact", user.position(), new Vec3(0, 1, 0), landedHit ? 1.3f : 1f, user.getId());
            Fx.shake(level, user.position(), 16, landedHit ? 0.8f : 0.4f, 8);
            finish();
        }

        @Override
        public float movementMultiplier() {
            return 0.3f;
        }
    }
}
