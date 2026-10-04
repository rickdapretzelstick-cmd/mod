package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lapse Blue (JJS). Aimed at an opponent within 35 studs, a vacuum pulls them in (360-blockable); caught, they hang
 * suspended in front of Gojo, who gains melee i-frames and follows with an unblockable kick. A target low enough is
 * finished instead: kicked up and crushed by the pulled rubble. Aimed at nobody, it's a whiff with endlag.
 *
 * Lapse Blue MAX (the awakened version) is the steerable vortex: it appears where you aim and follows your crosshair
 * while the key is held; Gojo can walk (or hang in the air) while guiding it.
 */
public final class BlueAbility extends Ability {
    public static final String ID = "blue";
    public static final String MAX_ID = "max_blue";
    private final boolean max;

    public BlueAbility() {
        this(false);
    }

    /** @param max Lapse Blue MAX: the awakened version (much bigger, longer startup, costs Awakening). */
    public BlueAbility(boolean max) {
        super(max ? MAX_ID : ID);
        this.max = max;
    }

    int startup() {
        return max ? JJKConfig.get().maxBlue.startup : 5;
    }

    @Override
    public Kind kind() {
        return max ? Kind.HOLD : Kind.INSTANT;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return max ? 0 : JJKConfig.get().blue.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return max ? JJKConfig.get().maxBlue.cooldown : JJKConfig.get().gojo.blueCooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return max ? new Instance(this, ctx, true) : new Lapse(this, ctx);
    }

    /** Base Lapse Blue: wind-up, pull, suspend, kick. */
    private static final class Lapse extends AbilityInstance {
        private static final int WHIFF = 0, PULL = 1, SUSPEND = 2, DONE = 3;
        @Nullable private final Entity hint;
        @Nullable private net.minecraft.world.entity.LivingEntity target;
        private int state = -1;
        private int stateAge;
        private int endAt = -1;

        Lapse(BlueAbility ability, AbilityContext ctx) {
            super(ability, ctx);
            this.hint = ctx.targetHint();
        }

        private static JJKConfig.Gojo cfg() {
            return JJKConfig.get().gojo;
        }

        @Override
        public void start() {
            Anim.play(user, "blue_cast");
            setPhase(0, cfg().blueWindup);
            Fx.play(level, "blue_cast", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
            if (dev.rick.jjk.core.combat.Combat.isAirborne(user)) dev.rick.jjk.core.combat.Statuses.apply(user, dev.rick.jjk.core.combat.CombatStatus.HOVER, cfg().blueWindup + 4);
        }

        private Vec3 holdPoint() {
            return user.position().add(dev.rick.jjk.hakari.HakariCombat.flat(user).scale(1.7)).add(0, 0.2, 0);
        }

        @Override
        public void tick() {
            JJKConfig.Gojo cfg = cfg();
            if (endAt >= 0) {
                if (age >= endAt) finish();
                return;
            }
            if (age == cfg.blueWindup) {
                target = Aim.target(user, cfg.blueRange * dev.rick.jjk.progression.mastery.Mastery.param(user, "blue.range"), 12, hint);
                if (target == null) {
                    // A vacuum over nothing: a whiff with a little endlag.
                    Aim.Target t = Aim.point(user, cfg.blueRange * dev.rick.jjk.progression.mastery.Mastery.param(user, "blue.range"), 6, null);
                    Fx.play(level, "blue_spawn", t.point(), Vec3.ZERO, 0.6f, user.getId());
                    endAt = age + 10;
                    return;
                }
                Fx.play(level, "blue_spawn", target.getBoundingBox().getCenter(), Vec3.ZERO, 0.7f, user.getId());
                dev.rick.jjk.core.combat.Hit pull = dev.rick.jjk.core.combat.Hit.builder(user, ID).type(dev.rick.jjk.registry.ModDamageTypes.BLUE)
                        .damage(cfg.bluePullDamage).tag(dev.rick.jjk.core.combat.AttackTag.TECHNIQUE, dev.rick.jjk.core.combat.AttackTag.LIMITLESS,
                                dev.rick.jjk.core.combat.AttackTag.PROJECTILE)
                        .origin(user.getEyePosition()).knockback(dev.rick.jjk.core.combat.Knockback.HOLD)
                        .hitstun(cfg.bluePullTicks + cfg.blueSuspendTicks + 12).fx("blue_hit", 1f).build();
                if (!dev.rick.jjk.core.combat.HitResolver.resolve(pull, target).connected()) {
                    target = null;
                    endAt = age + 12;
                    return;
                }
                state = PULL;
                stateAge = 0;
                setPhase(1, cfg.bluePullTicks);
                return;
            }
            if (state < 0) return;
            if (target == null || !target.isAlive()) {
                finish();
                return;
            }
            stateAge++;
            Vec3 hold = holdPoint();
            if (state == PULL) {
                Vec3 d = hold.subtract(target.position());
                Motion.set(target, d.scale(Math.min(1, 0.45 + stateAge * 0.06)));
                dev.rick.jjk.core.combat.Statuses.apply(target, dev.rick.jjk.core.combat.CombatStatus.PULLED, 3);
                if (d.length() < 0.8 || stateAge >= cfg.bluePullTicks) {
                    state = SUSPEND;
                    stateAge = 0;
                    setPhase(2, cfg.blueSuspendTicks);
                    // Caught: Gojo can't be touched by melee while he lines up the kick.
                    dev.rick.jjk.core.combat.Statuses.apply(user, dev.rick.jjk.core.combat.CombatStatus.MELEE_ARMOR, cfg.blueSuspendTicks + 8);
                    Anim.play(user, "blue_kick");
                }
                return;
            }
            if (state == SUSPEND) {
                Motion.set(target, hold.subtract(target.position()).scale(0.5));
                dev.rick.jjk.core.combat.Statuses.apply(target, dev.rick.jjk.core.combat.CombatStatus.GRABBED, 3);
                if (stateAge < cfg.blueSuspendTicks) return;
                dev.rick.jjk.core.combat.Statuses.remove(target, dev.rick.jjk.core.combat.CombatStatus.GRABBED);
                Vec3 f = dev.rick.jjk.hakari.HakariCombat.flat(user);
                if (GojoCombat.finishable(target)) {
                    // The finisher: kicked up, crushed in the rubble Blue tore loose, then obliterated.
                    Motion.set(target, new Vec3(0, 0.9, 0));
                    Fx.play(level, "max_blue_collapse", target.getBoundingBox().getCenter().add(0, 1.5, 0), Vec3.ZERO, 0.8f, user.getId());
                    GojoCombat.execute(user, target, ID, "finisher");
                } else {
                    dev.rick.jjk.core.combat.Hit kick = GojoCombat.strike(user, ID, cfg.blueKickDamage, true)
                            .knockback(dev.rick.jjk.core.combat.Knockback.directional(f, cfg.blueKickKnockback, 0.45)).hitstun(22)
                            .status(dev.rick.jjk.core.combat.CombatStatus.LAUNCHED, 18).fx("hit_launch", 1.2f).build();
                    dev.rick.jjk.core.combat.HitResolver.resolve(kick, target);
                }
                Fx.shake(level, target.position(), 14, 0.5f, 8);
                state = DONE;
                endAt = age + 6;
            }
        }

        @Override
        public float movementMultiplier() {
            return state == PULL || state == SUSPEND ? 0.2f : 0.5f;
        }

        @Override
        public void end() {
            if (target != null) {
                dev.rick.jjk.core.combat.Statuses.remove(target, dev.rick.jjk.core.combat.CombatStatus.GRABBED);
                dev.rick.jjk.core.combat.Statuses.remove(target, dev.rick.jjk.core.combat.CombatStatus.PULLED);
            }
        }
    }

    private static final class Instance extends AbilityInstance {
        @Nullable private final Entity hint;
        private final boolean max;
        private final int startup;
        @Nullable private BlueEntity blue;
        private double steerDistance;
        private int steered;

        Instance(BlueAbility ability, AbilityContext ctx, boolean max) {
            super(ability, ctx);
            this.hint = ctx.targetHint();
            this.max = max;
            this.startup = ability.startup();
        }

        @Override
        public void start() {
            Anim.play(user, max ? "max_blue_cast" : "blue_cast");
            setPhase(0, startup);
            Fx.play(level, max ? "max_blue_cast" : "blue_cast", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Blue cfg = JJKConfig.get().blue;
            if (age == startup) {
                Aim.Target t = Aim.point(user, max ? JJKConfig.get().maxBlue.range : cfg.range, 6, hint);
                Vec3 pos = t.point();
                if (t.entity() == null && !t.hitBlock()) pos = pos.add(0, -0.3, 0);
                blue = BlueEntity.spawn(level, user, pos, max ? BlueEntity.Params.max() : BlueEntity.Params.normal());
                steerDistance = pos.distanceTo(user.getEyePosition());
                setPhase(1, cfg.maxSteerTicks);
                if (!held) finish();
                return;
            }
            if (age < startup) return;
            if (blue == null || blue.isRemoved() || blue.isCollapsing()) {
                finish();
                return;
            }
            if (!held || steered >= cfg.maxSteerTicks || !caster.drain(cfg.steerCostPerSecond / 20f)) {
                finish();
                return;
            }
            steered++;
            // Guiding it from the air keeps Gojo up there.
            if (dev.rick.jjk.core.combat.Combat.isAirborne(user)) dev.rick.jjk.core.combat.Statuses.apply(user, dev.rick.jjk.core.combat.CombatStatus.HOVER, 3);
            if (!blue.isLingering()) blue.extend(1);
            blue.steer(user.getEyePosition().add(user.getLookAngle().scale(steerDistance)));
        }

        @Override
        public boolean exclusive() {
            // Only the hand sign is committed; steering leaves Gojo free to fight.
            return age < startup;
        }

        @Override
        public float movementMultiplier() {
            return age < startup ? 0.5f : 1f;
        }

        @Override
        public void interrupt(String reason) {
            super.interrupt(reason);
        }
    }
}
