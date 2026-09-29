package dev.rick.jjk.core.combat.melee;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Presentation and scaling for a character's melee. The mechanics (chain, variants, timing) live in
 * {@link MeleeSystem} and are shared; a moveset changes feel: animations, effects and multipliers.
 *
 * @param rangeMultiplier  reach of every basic attack (Sukuna's Shrine slashes reach three times as far)
 * @param launchers        whether the 4th hit can be an uppercut (jump held) or a downslam (airborne)
 * @param blockable360     whether a guard stops these hits from any side
 * @param onSwing          extra work each swing does (Shrine's slashes cut through walls), or null
 */
public record MeleeMoveset(String animPrefix, String fxPrefix, float damageMultiplier, float knockbackMultiplier, float speedMultiplier,
                           float rangeMultiplier, boolean launchers, boolean blockable360, @Nullable Consumer<LivingEntity> onSwing) {
    public MeleeMoveset(String animPrefix, String fxPrefix, float damageMultiplier, float knockbackMultiplier, float speedMultiplier) {
        this(animPrefix, fxPrefix, damageMultiplier, knockbackMultiplier, speedMultiplier, 1f, true, false, null);
    }

    public String anim(String name) {
        return animPrefix + name;
    }

    public String fx(String name) {
        return fxPrefix + name;
    }
}
