package dev.rick.jjk.progression.mastery;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Mastery's stand-in. The mastery system (skill trees, unlocks and number tweaks, bought with contract rewards) is
 * archived under archive/survival; with it gone every move and every cursed-tool move comes whole and unmodified.
 * Abilities still ask these two questions, and the answers are now fixed.
 */
public final class Mastery {
    private Mastery() {}

    /** Every gated behaviour is available. */
    public static boolean unlocked(@Nullable LivingEntity e, String key) {
        return true;
    }

    /** No move's number is scaled. */
    public static double param(@Nullable LivingEntity e, String key) {
        return 1.0;
    }

    public static double scaled(@Nullable LivingEntity e, String key, double base) {
        return base;
    }
}
