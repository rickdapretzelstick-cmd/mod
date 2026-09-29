package dev.rick.jjk.gojo;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.util.Motion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Awakening. Only available with a full meter. A committed transformation: Gojo reaches for his blindfold, pulls it
 * off, and cursed energy erupts. He's untouchable for the transition, then enters the awakened moveset with the meter
 * as a draining timer.
 */
public final class AwakenAbility extends Ability {
    public static final String ID = "awaken";
    /** Tick of the transition at which the blindfold comes off and the eruption happens. */
    public static final int REVEAL_FRACTION_NUM = 1, REVEAL_FRACTION_DEN = 4;
    /** Ticks of the "DOMAIN EXPANSION" cut-in before the 0.2 Domain's sure hit. */
    private static final int CUT_IN = 14;

    public AwakenAbility() {
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
    public @Nullable String checkActivation(AbilityContext ctx) {
        AbilityCaster c = ctx.caster();
        if (c.isAwakened()) return "already_awakened";
        return c.noCost() || c.awakening() >= c.maxAwakening() ? null : "meter_not_full";
    }

    @Override
    public @Nullable AbilityInstance activate(AbilityContext ctx) {
        return new Transformation(this, ctx);
    }

    /**
     * Six Eyes: the blindfold comes off ("Let's get... a little crazy"), 25 HP healed, 60 seconds of Awakening.
     * The Special pressed during it is the 0.2 Domain instead (see {@link ZeroTwoDomain}).
     */
    private static final class Transformation extends AbilityInstance implements dev.rick.jjk.core.ability.LockedInputCast {
        private int reveal;
        private int total;
        private boolean zeroTwo;
        private int rushAt = -1;
        @Nullable private ZeroTwoDomain rush;

        Transformation(Ability ability, AbilityContext ctx) {
            super(ability, ctx);
        }

        @Override
        public void start() {
            // Someone nearby is opening a domain: this press is a counter (instant Awakening + domain), not a transformation.
            if (dev.rick.jjk.core.domain.DomainCounter.tryCounter(caster)) {
                finish();
                return;
            }
            total = JJKConfig.get().awakening.transitionTicks;
            reveal = total * REVEAL_FRACTION_NUM / REVEAL_FRACTION_DEN;
            Statuses.apply(user, CombatStatus.AWAKENING, total + 2);
            Anim.play(user, "awaken");
            setPhase(0, total);
            Fx.play(level, "awaken_start", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
        }

        @Override
        public boolean pressWhileLocked(dev.rick.jjk.core.ability.AbilitySlot slot) {
            if (slot != dev.rick.jjk.core.ability.AbilitySlot.SKILL_5 || zeroTwo || age >= total - 6) return false;
            zeroTwo = true;
            // Any time during the sequence: the cut-in, then Infinite Void for two tenths of a second.
            rushAt = Math.max(reveal, age + CUT_IN);
            Fx.play(level, "zero_two_cutin", user.position().add(0, 1.2, 0), Vec3.ZERO, 12f, user.getId());
            return true;
        }

        @Override
        public void tick() {
            if (rush != null) {
                if (rush.tick()) finish();
                return;
            }
            Motion.set(user, new Vec3(0, Math.min(0, user.getDeltaMovement().y) * 0.2, 0));
            if (age == reveal) {
                // The blindfold comes off: the Six Eyes open and everything around him feels it.
                caster.enterAwakening();
                setPhase(1, total - reveal);
                Fx.play(level, "awaken", user.position().add(0, 1.2, 0), Vec3.ZERO, 1f, user.getId());
                Fx.shake(level, user.position(), 48, 1.1f, 24);
                Fx.flash(level, user.position(), 40, 0xB0D8F0FF, 14);
                if (!zeroTwo) user.heal(user.getMaxHealth() * JJKConfig.get().gojo.awakenHealShare);
            }
            if (zeroTwo && age >= rushAt) {
                rush = new ZeroTwoDomain(level, caster, user);
                return;
            }
            if (age >= total) finish();
        }

        @Override
        public boolean exclusive() {
            return true;
        }

        @Override
        public float movementMultiplier() {
            return 0f;
        }

        @Override
        public void interrupt(String reason) {
            // Only death/disconnect interrupt the transformation (it is untouchable); make sure the state is sane.
            if (rush != null) rush.abort();
            else if (age >= reveal && !caster.isAwakened()) caster.enterAwakening();
            super.interrupt(reason);
        }

        @Override
        public void end() {
            if (rush != null) rush.abort();
        }
    }
}
