package dev.rick.jjk.core.combat.melee;

/**
 * Presentation and scaling for a character's melee. The mechanics (chain, variants, timing) live in
 * {@link MeleeSystem} and are shared; a moveset changes feel: animations, effects and multipliers.
 */
public record MeleeMoveset(String animPrefix, String fxPrefix, float damageMultiplier, float knockbackMultiplier, float speedMultiplier) {
    public String anim(String name) {
        return animPrefix + name;
    }

    public String fx(String name) {
        return fxPrefix + name;
    }
}
