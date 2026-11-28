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
    private static final java.util.Set<String> HELD = java.util.Set.of("heavy_charge", "guard", "guard_gojo", "fever_raise", "red_charge", "max_blue_cast", "purple_blue", "purple_red",
            "purple_fusion", "max_red_charge", "domain_sign", "rough_charge", "fever_rush", "door_guard", "idg_sign", "rushdown_run",
            "rushdown_drag", "surge_dash",
            // Vessel / King of Curses
            "cursed_strikes_ready", "cursed_strikes_slide", "cursed_strikes_hop", "cursed_strikes_dropkick", "crushing_blow_charge",
            "crushing_blow_air", "crushing_blow_dash", "divergent_windup", "manji_stance", "manji_swoop", "sukuna_faint", "shrine_heavy_charge",
            "cleave_reach", "cleave_hold", "dismantle_windup", "dismantle_air", "wcs_chant_1", "wcs_chant_2", "wcs_chant_3", "open_flames",
            "open_clap", "open_draw", "open_aim", "rush_run", "rush_chase", "rush_leap", "shrine_sign",
            // Cursed Partners
            "yuta_guard_katana", "yuta_heavy_charge", "yuta_steel_heavy_charge", "yuta_outburst_grip", "yuta_domain_sign",
            "yuta_jacobs_ladder",
            // The Cursed Rifle braced to guard (its stance draws the pose)
            "rf_guard",
            // Gojo's barrage, Hakari's rhythm and volley, Yuta's blade run
            "rapid_barrage", "rhythm_dance", "volley_flurry", "yuta_blade_run", "overwhelm_left", "overwhelm_right",
            // Ryu
            "ryu_after_leap", "ryu_after_lunge", "ryu_after_slam", "ryu_dessert_run", "ryu_eld_charge", "ryu_eld_fire", "ryu_eld_point",
            "ryu_granite_aim", "ryu_invited_lunge", "ryu_invited_windup", "ryu_no_idea_punch", "ryu_no_idea_windup", "ryu_second_helping",
            "ryu_second_helping_air",
            // The cursed tools' kits
            "cb_guard", "cb_ult_raise", "cb_wave_charge", "cl_charge", "cl_exec_leap", "cl_heavy_charge", "cl_wall", "sd_parry_stance",
            "sd_sever_windup");
    // (The strength contests' grapple poses were archived with the survival system: archive/survival.)
    /** Entities currently showing a held pose. */
    private static final java.util.Set<LivingEntity> HOLDING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void play(LivingEntity entity, String anim, float speed) {
        if (entity.level().isClientSide()) return;
        if (dev.rick.jjk.core.debug.Trace.on())
            dev.rick.jjk.core.debug.Trace.event(entity, "anim", (anim.isEmpty() ? "(release)" : anim) + (speed != 1f ? " x" + speed : ""));
        if (HELD.contains(anim)) HOLDING.add(entity);
        else HOLDING.remove(entity);
        Fx.toTrackers(entity, new AnimPayload(entity.getId(), anim, speed), true);
    }

    /** Plays a clip on any entity (a manifestation like Rika, which animates a model of its own). */
    public static void playOn(net.minecraft.world.entity.Entity entity, String anim, float speed) {
        if (entity.level().isClientSide()) return;
        if (dev.rick.jjk.core.debug.Trace.on()) dev.rick.jjk.core.debug.Trace.event(entity, "anim", (anim.isEmpty() ? "(release)" : anim) + (speed != 1f ? " x" + speed : ""));
        Fx.toTrackers(entity, new AnimPayload(entity.getId(), anim, speed), true);
    }

    public static void playOn(net.minecraft.world.entity.Entity entity, String anim) {
        playOn(entity, anim, 1f);
    }

    public static void stop(LivingEntity entity) {
        play(entity, "", 1f);
    }

    /** Releases a held pose left over from a move that has ended, so the user isn't left frozen in it. */
    public static void releaseHeld(LivingEntity entity) {
        if (HOLDING.contains(entity)) stop(entity);
    }

    /** Whether the server treats {@code clip} as a held pose (kept until released, released when its move ends). */
    public static boolean isHeldClip(String clip) {
        return HELD.contains(clip);
    }

    public static boolean isHolding(LivingEntity entity) {
        return HOLDING.contains(entity);
    }

    /** Forgets any held pose without telling anyone (the entity is gone, or a fresh one replaced it). */
    static void forget(LivingEntity entity) {
        HOLDING.remove(entity);
    }
}
