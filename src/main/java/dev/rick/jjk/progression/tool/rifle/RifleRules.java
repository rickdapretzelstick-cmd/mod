package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.tool.CursedTools;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The rifle's numbers for one shooter: the config's base values ({@link JJKConfig.Rifle}) under their Mastery of it
 * ({@code tool/cursed_rifle}), with the config's floors and caps so no upgrade removes a drawback entirely.
 */
public final class RifleRules {
    private RifleRules() {}

    static JJKConfig.Rifle cfg() {
        return JJKConfig.get().rifle;
    }

    private static double p(LivingEntity e, String name) {
        return Mastery.param(e, CursedTools.CURSED_RIFLE.paramKey(name));
    }

    public static boolean unlocked(LivingEntity e, String name) {
        return Mastery.unlocked(e, CursedTools.CURSED_RIFLE.unlockKey(name));
    }

    /** Scope sway, degrees (Steady Hands). */
    public static float sway(LivingEntity e) {
        return (float) (cfg().swayDegrees * Mth.clamp(p(e, "sway"), 0.35, 1.0));
    }

    /** Ticks for the aim to settle (Breath Control). */
    public static int settleTicks(LivingEntity e) {
        return Math.max(5, (int) Math.round(cfg().settleTicks * Mth.clamp(p(e, "stabilize"), 0.3, 1.0)));
    }

    /** Ticks between normal shots (Quick Bolt, Oiled Action), never under the config's floor. */
    public static int shotInterval(LivingEntity e) {
        return Math.max(cfg().minShotInterval, (int) Math.round(cfg().shotInterval * p(e, "recovery")));
    }

    public static float shotCost(LivingEntity e) {
        return (float) (cfg().shotCost * Math.max(cfg().minCostShare, p(e, "efficiency")));
    }

    /** A normal shot's damage (Heavy Rounds), before a curse's own grade rules. */
    public static float shotDamage(LivingEntity e) {
        return (float) (cfg().shotDamage * Mth.clamp(p(e, "damage"), 1.0, 1.5));
    }

    /** The unfolding-arm beam (Unfolding Array). */
    public static boolean beamUnlocked(LivingEntity e) {
        return unlocked(e, "beam");
    }

    public static boolean maximumOutput(LivingEntity e) {
        return unlocked(e, "maximum_output");
    }

    public static int chargeTicks(LivingEntity e) {
        return Math.max(cfg().minChargeTicks, (int) Math.round(cfg().chargeTicks * p(e, "beam_charge")));
    }

    public static float beamCost(LivingEntity e) {
        return (float) (cfg().beamCost * Math.max(cfg().minCostShare, p(e, "beam_efficiency")));
    }

    public static int beamTicks(LivingEntity e) {
        return Math.min(cfg().maxBeamTicks, (int) Math.round(cfg().beamTicks * Math.max(1.0, p(e, "beam_duration"))));
    }

    /**
     * The beam's output, 0..1: {@link JJKConfig.Rifle#baseOutput} at the first unlock, raised by the lens upgrades to at
     * most {@link JJKConfig.Rifle#outputCap}, and 1 (a True Love Beam's or Every Last Drop's equal) with Maximum Output.
     */
    public static float output(LivingEntity e) {
        if (maximumOutput(e)) return 1f;
        return (float) Mth.clamp(cfg().baseOutput * p(e, "output"), cfg().baseOutput, cfg().outputCap);
    }

    /** Half the beam's square side at an output (its drawn width and its hitbox are the same number). */
    public static double half(float output) {
        float k = Mth.clamp((output - cfg().baseOutput) / Math.max(0.01f, 1f - cfg().baseOutput), 0f, 1f);
        return Mth.lerp(k, cfg().baseHalf, cfg().maxHalf);
    }
}
