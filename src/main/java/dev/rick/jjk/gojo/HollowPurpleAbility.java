package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.entity.HollowPurpleEntity;
import dev.rick.jjk.util.Motion;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hollow Purple as a committed sequence:
 * <ol>
 *   <li>Blue forms in one hand,</li>
 *   <li>Red forms in the other,</li>
 *   <li>the two are brought together and fuse,</li>
 *   <li>the fused mass can be held (growing slightly) until release,</li>
 *   <li>release fires it; it erases everything in its path (see {@link HollowPurpleEntity}).</li>
 * </ol>
 * Releasing early doesn't cancel: it fires as soon as fusion completes. Getting stunned during the sequence
 * interrupts it; half the energy is refunded and the cooldown shortened.
 */
public final class HollowPurpleAbility extends Ability {
    public static final String ID = "hollow_purple";
    public static final int PHASE_BLUE = 0, PHASE_RED = 1, PHASE_FUSION = 2, PHASE_CHARGED = 3, PHASE_FIRED = 4;

    public HollowPurpleAbility() {
        super(ID);
    }

    @Override
    public Kind kind() {
        return Kind.HOLD;
    }

    @Override
    public float cost(AbilityCaster caster) {
        return JJKConfig.get().purple.cost;
    }

    @Override
    public int cooldown(AbilityCaster caster) {
        return JJKConfig.get().purple.cooldown;
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Instance(this, ctx);
    }

    private static final class Instance extends AbilityInstance {
        private int chargedTicks;
        private boolean fired;

        Instance(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        private int blueEnd() {
            return JJKConfig.get().purple.blueFormTicks;
        }

        private int redEnd() {
            return blueEnd() + JJKConfig.get().purple.redFormTicks;
        }

        private int fusionEnd() {
            return redEnd() + JJKConfig.get().purple.fusionTicks;
        }

        @Override
        public void start() {
            Anim.play(user, "purple_blue");
            setPhase(PHASE_BLUE, blueEnd());
            Fx.play(level, "purple_blue", hand(true), Vec3.ZERO, 1f, user.getId());
        }

        /** Approximate hand positions (left = Blue, right = Red), matching the cast animation. */
        private Vec3 hand(boolean left) {
            float yaw = user.getYRot() * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            return user.position().add(0, 1.35, 0).add(fwd.scale(0.5)).add(right.scale(left ? -0.75 : 0.75));
        }

        @Override
        public void tick() {
            JJKConfig.Purple cfg = JJKConfig.get().purple;
            if (age == blueEnd()) {
                Anim.play(user, "purple_red");
                setPhase(PHASE_RED, cfg.redFormTicks);
                Fx.play(level, "purple_red", hand(false), Vec3.ZERO, 1f, user.getId());
            } else if (age == redEnd()) {
                Anim.play(user, "purple_fusion");
                setPhase(PHASE_FUSION, cfg.fusionTicks);
                Fx.play(level, "purple_fusion", user.getEyePosition().add(user.getLookAngle().scale(1.2)), user.getLookAngle(), 1f, user.getId());
            } else if (age == fusionEnd()) {
                setPhase(PHASE_CHARGED, cfg.maxHoldTicks);
                Fx.play(level, "purple_charged", user.getEyePosition().add(user.getLookAngle().scale(1.5)), user.getLookAngle(), 1f, user.getId());
                Fx.shake(level, user.position(), 24, 0.4f, 10);
            }
            // Airborne: Gojo hovers and aims freely (the look direction already carries pitch).
            if (dev.rick.jjk.core.combat.Combat.isAirborne(user)) dev.rick.jjk.core.combat.Statuses.apply(user, dev.rick.jjk.core.combat.CombatStatus.HOVER, 3);
            if (age >= fusionEnd()) {
                chargedTicks++;
                // JJS: it's released about three seconds in, however the key is held.
                if (chargedTicks >= cfg.maxHoldTicks) fire(cfg);
            }
        }

        private void fire(JJKConfig.Purple cfg) {
            if (fired) return;
            fired = true;
            setPhase(PHASE_FIRED, 0);
            float growth = Mth.clamp(chargedTicks / (float) Math.max(1, cfg.maxHoldTicks), 0f, 1f);
            double radius = Mth.lerp(growth, cfg.radius, cfg.chargedRadius);
            Vec3 look = user.getLookAngle();
            Vec3 from = user.getEyePosition().add(look.scale(1.2 + radius * 0.6));
            Anim.play(user, "purple_release");
            Fx.play(level, "purple_fire", from, look, (float) radius, user.getId());
            Fx.flash(level, from, 48, 0x66A040FF, 6);
            Fx.shake(level, from, 48, 1.0f, 14);
            HollowPurpleEntity.fire(level, user, from, look, radius);
            Motion.set(user, user.getDeltaMovement().add(look.scale(-0.6)).add(0, 0.1, 0));
            finish();
        }

        @Override
        public float movementMultiplier() {
            return JJKConfig.get().purple.casterMoveSpeed;
        }

        @Override
        public void interrupt(String reason) {
            if (!fired) {
                Fx.play(level, "purple_fizzle", user.getEyePosition().add(user.getLookAngle()), Vec3.ZERO, 1f, user.getId());
                // Unfinished technique: part of the energy comes back and the cooldown is shorter.
                caster.setEnergy(caster.energy() + JJKConfig.get().purple.cost * 0.5f);
                for (AbilitySlot s : AbilitySlot.values()) {
                    if (caster.ability(s) == ability) {
                        caster.resetSlot(s);
                        caster.startCooldown(s, JJKConfig.get().purple.cooldown / 3);
                    }
                }
            }
            super.interrupt(reason);
        }
    }
}
