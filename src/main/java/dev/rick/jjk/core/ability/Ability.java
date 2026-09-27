package dev.rick.jjk.core.ability;

import org.jetbrains.annotations.Nullable;

/**
 * Definition of an ability (stateless, shared by all casters). Each use creates an {@link AbilityInstance}
 * that carries the per-cast state through its phases.
 */
public abstract class Ability {
    public enum Kind {
        /** Fires on press. May still run a multi-tick instance (startup/recovery). */
        INSTANT,
        /** Starts on press, the instance receives the key release (charge/steer). */
        HOLD,
        /** On/off state. */
        TOGGLE
    }

    public final String id;

    protected Ability(String id) {
        this.id = id;
    }

    public abstract Kind kind();

    /** Cursed energy spent on activation. */
    public float cost(AbilityCaster caster) {
        return 0;
    }

    /** Cooldown in ticks, applied on activation unless {@link #cooldownOnEnd()}. */
    public int cooldown(AbilityCaster caster) {
        return 0;
    }

    /** Start the cooldown when the cast ends (charged abilities) instead of on press. */
    public boolean cooldownOnEnd() {
        return false;
    }

    /** Abilities with more than one charge regain one charge every {@link #cooldown} ticks. */
    public int maxCharges(AbilityCaster caster) {
        return 1;
    }

    /** Minimum ticks between two uses when using charges. */
    public int minInterval(AbilityCaster caster) {
        return 0;
    }

    /** Cursed techniques are blocked by burnout and technique-lock statuses; physical actions are not. */
    public boolean isTechnique() {
        return true;
    }

    /** Can be used while another cast is running (e.g. teleport out of a charge). Runs without replacing the cast. */
    public boolean usableWhileCasting() {
        return false;
    }

    /** Extra activation conditions. Return a short reason to refuse, or null to allow. */
    @Nullable
    public String checkActivation(AbilityContext ctx) {
        return null;
    }

    /** Starts the ability. Return a running instance, or null if it completed instantly. */
    @Nullable
    public abstract AbilityInstance activate(AbilityContext ctx);

    /** TOGGLE abilities: whether currently on. */
    public boolean isToggled(AbilityCaster caster) {
        return false;
    }

    /** TOGGLE abilities: turn off. */
    public void toggleOff(AbilityCaster caster, String reason) {}
}
