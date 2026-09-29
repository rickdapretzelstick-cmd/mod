package dev.rick.jjk.core.anim;

import dev.rick.jjk.core.fx.Fx;
import dev.rick.jjk.core.net.AnimPayload;
import net.minecraft.world.entity.LivingEntity;

/** Server-side animation triggers. Animations themselves are defined client-side (see client anim package). */
public final class Anim {
    private Anim() {}

    public static void play(LivingEntity entity, String anim) {
        play(entity, anim, 1f);
    }

    /** Animations that hold their last pose until replaced (mirrors the client's held defs). */
    private static final java.util.Set<String> HELD = java.util.Set.of("heavy_charge", "guard", "red_charge", "purple_blue", "purple_red",
            "purple_fusion", "max_red_charge", "domain_sign", "rough_charge", "fever_rush", "door_guard", "idg_sign", "rushdown_run",
            "rushdown_drag", "surge_dash",
            // Vessel / King of Curses
            "cursed_strikes_ready", "cursed_strikes_slide", "cursed_strikes_hop", "cursed_strikes_dropkick", "crushing_blow_charge",
            "crushing_blow_air", "crushing_blow_dash", "divergent_windup", "manji_stance", "manji_swoop", "sukuna_faint", "shrine_heavy_charge",
            "cleave_reach", "cleave_hold", "dismantle_windup", "dismantle_air", "wcs_chant_1", "wcs_chant_2", "wcs_chant_3", "open_flames",
            "open_clap", "open_draw", "open_aim", "rush_run", "rush_chase", "rush_leap", "shrine_sign");
    /** Entities currently showing a held pose. */
    private static final java.util.Set<LivingEntity> HOLDING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void play(LivingEntity entity, String anim, float speed) {
        if (entity.level().isClientSide()) return;
        if (HELD.contains(anim)) HOLDING.add(entity);
        else HOLDING.remove(entity);
        Fx.toTrackers(entity, new AnimPayload(entity.getId(), anim, speed), true);
    }

    public static void stop(LivingEntity entity) {
        play(entity, "", 1f);
    }

    /** Releases a held pose left over from a move that has ended, so the user isn't left frozen in it. */
    public static void releaseHeld(LivingEntity entity) {
        if (HOLDING.contains(entity)) stop(entity);
    }

    public static boolean isHolding(LivingEntity entity) {
        return HOLDING.contains(entity);
    }
}
