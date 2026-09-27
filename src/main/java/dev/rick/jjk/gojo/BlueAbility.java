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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tap: a point of attraction appears where you aim (on the target near your crosshair, a surface, or at max range).
 * Hold: after it appears, it follows your crosshair at the same distance for as long as you hold (costs energy).
 * You're free to fight while it's out, so Blue → melee → Red flows naturally.
 */
public final class BlueAbility extends Ability {
    public static final String ID = "blue";
    private static final int STARTUP = 5;

    public BlueAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().blue.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().blue.cooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    private static final class Instance extends AbilityInstance {
        @Nullable private final Entity hint;
        @Nullable private BlueEntity blue;
        private double steerDistance;
        private int steered;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
            this.hint = ctx.targetHint();
        }

        @Override
        public void start() {
            Anim.play(user, "blue_cast");
            setPhase(0, STARTUP);
            Fx.play(level, "blue_cast", user.getEyePosition(), user.getLookAngle(), 1f, user.getId());
        }

        @Override
        public void tick() {
            JJKConfig.Blue cfg = JJKConfig.get().blue;
            if (age == STARTUP) {
                Aim.Target t = Aim.point(user, cfg.range, 6, hint);
                Vec3 pos = t.point();
                if (t.entity() == null && !t.hitBlock()) pos = pos.add(0, -0.3, 0);
                blue = BlueEntity.spawn(level, user, pos, 1f);
                steerDistance = pos.distanceTo(user.getEyePosition());
                setPhase(1, cfg.maxSteerTicks);
                if (!held) finish();
                return;
            }
            if (age < STARTUP) return;
            if (blue == null || blue.isRemoved() || blue.isCollapsing()) {
                finish();
                return;
            }
            if (!held || steered >= cfg.maxSteerTicks || !caster.drain(cfg.steerCostPerSecond / 20f)) {
                finish();
                return;
            }
            steered++;
            blue.extend(1);
            blue.steer(user.getEyePosition().add(user.getLookAngle().scale(steerDistance)));
        }

        @Override
        public boolean exclusive() {
            // Only the hand sign is committed; steering leaves Gojo free to fight.
            return age < STARTUP;
        }

        @Override
        public float movementMultiplier() {
            return age < STARTUP ? 0.5f : 1f;
        }

        @Override
        public void interrupt(String reason) {
            super.interrupt(reason);
        }
    }
}
