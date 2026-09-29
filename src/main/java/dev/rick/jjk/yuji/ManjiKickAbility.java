package dev.rick.jjk.yuji;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Manji Kick (JJS bullet counter, 20s). He raises an arm and one leg, ready to evade. A melee attack in the 0.6 s window
 * is answered with a Taido upward roundhouse kick that knocks the attacker to the side; a bullet is dodged, and he swoops
 * in on the shooter spinning through the air. Unblockable, bypasses ragdoll. The finisher: a kick to the face, his legs
 * locked around them, a spin and a slam that crushes them to bits. Combat Instincts can't feint it.
 */
public final class ManjiKickAbility extends Ability {
    public static final String ID = "manji_kick";

    public ManjiKickAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return YujiCombat.cfg().manjiCost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return YujiCombat.cfg().manjiCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    /** The counter stance is up for this entity right now. */
    static boolean raised(LivingEntity e) {
        long at = YujiState.of(e).manjiRaised;
        return at >= 0 && e.level().getGameTime() - at <= YujiCombat.cfg().manjiWindow
                && Casters.getOrNull(e) != null && Casters.getOrNull(e).cast() instanceof Instance i && !i.isFinished() && i.counterAt < 0;
    }

    /** Called by the defense when a hit lands in the window. */
    static void counter(LivingEntity defender, @Nullable LivingEntity attacker, boolean bullet) {
        var c = Casters.getOrNull(defender);
        if (c != null && c.cast() instanceof Instance i) i.trigger(attacker, bullet);
    }

    public static final class Instance extends AbilityInstance {
        private int counterAt = -1;
        @Nullable private LivingEntity attacker;
        private boolean bullet;
        private boolean finisher;
        private int endAt = -1;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        @Override
        public void start() {
            YujiState.of(user).manjiRaised = level.getGameTime();
            Statuses.apply(user, CombatStatus.MANJI, YujiCombat.cfg().manjiWindow);
            Anim.play(user, "manji_stance");
            setPhase(0, YujiCombat.cfg().manjiWindow);
            Fx.play(level, "manji_startup", user.position().add(0, 1, 0), Vec3.ZERO, 1f, user.getId());
        }

        void trigger(@Nullable LivingEntity from, boolean bullet) {
            if (counterAt >= 0) return;
            counterAt = age;
            attacker = from;
            this.bullet = bullet;
            finisher = from != null && YujiCombat.finishable(from);
            Statuses.remove(user, CombatStatus.MANJI);
            Statuses.apply(user, CombatStatus.EVADING, bullet ? 12 : 6);
            setPhase(1, 20);
            if (bullet) {
                Anim.play(user, "manji_dodge");
                Fx.play(level, "manji_dodge", user.position().add(0, 1, 0), Vec3.ZERO, 1f, user.getId());
            } else {
                Anim.play(user, finisher ? "manji_face_kick" : "manji_kick");
            }
        }

        @Override
        public void tick() {
            JJKConfig.Yuji cfg = YujiCombat.cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (counterAt < 0) {
                Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y), 0));
                if (age > cfg.manjiWindow) {
                    // Nothing came: the stance drops.
                    YujiState.of(user).manjiRaised = -1;
                    Anim.play(user, "manji_recover");
                    endAt = age + 8;
                }
                return;
            }
            if (attacker == null || !attacker.isAlive()) {
                endAt = age + 6;
                return;
            }
            int t = age - counterAt;
            if (bullet) {
                // Swooping in on the shooter, spinning through the air.
                if (t == 4) {
                    Anim.play(user, "manji_swoop");
                    Fx.play(level, "manji_swoop", user.position().add(0, 1, 0), attacker.position().subtract(user.position()), 1f, user.getId());
                }
                if (t >= 4 && t < 14) {
                    Vec3 to = attacker.position().add(0, 0.6, 0).subtract(user.position());
                    double d = to.length();
                    Statuses.apply(user, CombatStatus.HOVER, 2);
                    if (d > 1.8) {
                        Motion.set(user, to.normalize().scale(Math.min(1.8, d * 0.45)).add(0, 0.1, 0));
                        return;
                    }
                    kick(cfg);
                    return;
                }
                if (t >= 14) kick(cfg);
                return;
            }
            if (!finisher) {
                if (t == 2) kick(cfg);
                return;
            }
            // Finisher: the face kick, the leg lock, the spin, and the slam that crushes them.
            HakariCombat.faceTowards(user, attacker.getBoundingBox().getCenter());
            if (t == 2) {
                HakariCombat.hit(YujiCombat.strike(user, ID, 0, true).knockback(Knockback.HOLD).hitstun(40).fx("hit_heavy", 1f).build(), attacker);
                Statuses.apply(attacker, CombatStatus.GRABBED, 30);
            }
            if (t > 2 && t < 20) {
                Statuses.apply(user, CombatStatus.HOVER, 2);
                Vec3 at = user.position().add(HakariCombat.flat(user).scale(0.8)).add(0, t < 12 ? 0.8 : 0.2, 0);
                Motion.set(attacker, at.subtract(attacker.position()).scale(0.6));
                if (t == 8) Anim.play(user, "manji_grapple_spin");
            }
            if (t == 20) {
                Anim.play(user, "manji_slam");
                Statuses.remove(attacker, CombatStatus.GRABBED);
                Fx.play(level, "manji_crush", attacker.position(), new Vec3(0, -1, 0), 1.4f, user.getId());
                YujiCombat.execute(user, attacker, ID, "manji_crush");
                Fx.shake(level, attacker.position(), 24, 1f, 12);
                endAt = age + 12;
            }
        }

        private void kick(JJKConfig.Yuji cfg) {
            if (finisher && bullet) {
                // Arrived at the shooter: the finisher from here.
                bullet = false;
                counterAt = age - 1;
                Anim.play(user, "manji_face_kick");
                return;
            }
            // The upward roundhouse: knocked off to the side.
            Anim.play(user, "manji_kick");
            Vec3 f = HakariCombat.flat(user);
            Vec3 side = new Vec3(-f.z, 0, f.x);
            HakariCombat.faceTowards(user, attacker.getBoundingBox().getCenter());
            HakariCombat.hit(YujiCombat.strike(user, ID, cfg.manjiDamage, true)
                    .knockback(Knockback.set(side.scale(1.2).add(f.scale(0.4)).add(0, 0.55, 0))).hitstun(26)
                    .status(CombatStatus.LAUNCHED, 20).fx("manji_hit", 1.2f).build(), attacker);
            Fx.shake(level, attacker.position(), 14, 0.5f, 8);
            endAt = age + 10;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void end() {
            YujiState.of(user).manjiRaised = -1;
            Statuses.remove(user, CombatStatus.MANJI);
            if (attacker != null) Statuses.remove(attacker, CombatStatus.GRABBED);
        }
    }
}
