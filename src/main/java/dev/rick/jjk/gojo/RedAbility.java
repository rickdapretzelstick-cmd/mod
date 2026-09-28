package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.RedEntity;
import dev.rick.jjk.util.Aim;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hold to charge, release to fire. Red gathers at Gojo's fingertip; the longer it charges, the bigger the blast.
 * Released with an enemy right in front of him, it detonates point-blank instead of flying.
 */
public final class RedAbility extends Ability {
    public static final String ID = "red";
    public static final String MAX_ID = "max_red";
    private static final double POINT_BLANK = 2.8;
    private final boolean max;

    public RedAbility() {
        this(false);
    }

    /** @param max Reversal Red MAX: the awakened version (longer charge, far bigger blast, costs Awakening). */
    public RedAbility(boolean max) {
        super(max ? MAX_ID : ID);
        this.max = max;
    }

    int minCharge() {
        return max ? JJKConfig.get().maxRed.minCharge : JJKConfig.get().red.minCharge;
    }

    int maxCharge() {
        return max ? JJKConfig.get().maxRed.maxCharge : JJKConfig.get().red.maxCharge;
    }

    @Override
    public float awakeningCost(AbilityCaster caster) {
        return max ? JJKConfig.get().awakening.maxRedCost : 0;
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return max ? 0 : JJKConfig.get().red.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return max ? JJKConfig.get().maxRed.cooldown : JJKConfig.get().red.cooldown;
    }

    @Override
    public boolean cooldownOnEnd() {
        return true;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    private static final class Instance extends AbilityInstance {
        @Nullable private final Entity hint;
        private final boolean max;
        private final int minCharge, maxCharge;
        private boolean fired;

        Instance(RedAbility ability, AbilityContext ctx) {
            super(ability, ctx);
            this.hint = ctx.targetHint();
            this.max = ability.max;
            this.minCharge = ability.minCharge();
            this.maxCharge = ability.maxCharge();
        }

        @Override
        public void start() {
            Anim.play(user, max ? "max_red_charge" : "red_charge");
            setPhase(0, maxCharge);
            Fx.play(level, max ? "max_red_charge" : "red_charge", fingertip(), user.getLookAngle(), 1f, user.getId());
        }

        private Vec3 fingertip() {
            return user.getEyePosition().add(user.getLookAngle().scale(0.9)).add(0, -0.25, 0);
        }

        @Override
        public void tick() {
            if (age == maxCharge) {
                setPhase(1, 0);
                Fx.play(level, "red_full", fingertip(), user.getLookAngle(), 1f, user.getId());
            }
            boolean ready = age >= minCharge;
            // Released early: fires the moment minimum charge is reached. Held too long: fires by itself.
            if (ready && (!held || age >= maxCharge + 40)) fire();
        }

        private void fire() {
            if (fired) return;
            fired = true;
            float charge = Mth.clamp((age - minCharge) / (float) Math.max(1, maxCharge - minCharge), 0f, 1f);
            Vec3 look = user.getLookAngle();
            Anim.play(user, "red_release");
            LivingEntity target = Aim.target(user, POINT_BLANK + 1, 25, hint);
            if (target != null && target.getBoundingBox().distanceToSqr(user.getEyePosition()) <= POINT_BLANK * POINT_BLANK) {
                Vec3 at = user.getEyePosition().add(look.scale(1.6)).add(0, -0.3, 0);
                Fx.play(level, "red_pointblank", at, look, 1f + charge, user.getId());
                RedEntity.detonate(level, user, at, charge, user, max);
            } else {
                Fx.play(level, max ? "max_red_fire" : "red_fire", fingertip(), look, (1f + charge) * (max ? 2 : 1), user.getId());
                RedEntity.fire(level, user, fingertip(), look, charge, max);
            }
            // Recoil.
            Motion.set(user, user.getDeltaMovement().add(look.scale((-0.35 - 0.3 * charge) * (max ? 1.8 : 1))).add(0, 0.05, 0));
            finish();
        }

        @Override
        public float movementMultiplier() {
            return 0.55f;
        }

        @Override
        public void interrupt(String reason) {
            if (!fired) Fx.play(level, "red_fizzle", fingertip(), Vec3.ZERO, 1f, user.getId());
            super.interrupt(reason);
        }
    }
}
