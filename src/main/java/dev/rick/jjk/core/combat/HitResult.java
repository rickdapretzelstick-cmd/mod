package dev.rick.jjk.core.combat;

import net.minecraft.world.entity.LivingEntity;

/** What happened when a hit was resolved against one target. */
public record HitResult(Hit hit, LivingEntity target, Outcome outcome, float damageDealt, int comboCount) {
    public enum Outcome {
        /** Target wasn't a legal target (ally, spectator, dead...). */
        INVALID,
        /** Passed through: dodged, invulnerable, or knocked down vs a normal hit. */
        WHIFF,
        /** Stopped completely by a defense (e.g. Infinity). */
        NEGATED,
        /** Guarded. */
        BLOCKED,
        /** Guarded at the last moment; attacker punished. */
        PARRIED,
        /** Broke the target's guard. */
        GUARD_BROKEN,
        HIT;

        public boolean connected() {
            return this == HIT || this == GUARD_BROKEN;
        }

        /** The attack reached the target's defenses (as opposed to missing or being invalid). */
        public boolean contacted() {
            return this != INVALID && this != WHIFF;
        }
    }

    public boolean connected() {
        return outcome.connected();
    }
}
