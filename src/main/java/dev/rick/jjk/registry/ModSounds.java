package dev.rick.jjk.registry;

import dev.rick.jjk.JJK;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every custom sound. Files live in assets/jjk/sounds/<name>.ogg (see sounds.json). */
public final class ModSounds {
    public static final String[] NAMES = {
            "ryu_granite_charge", "ryu_granite_fire", "ryu_granite_fire_hard", "ryu_granite_hit", "ryu_weave", "ryu_unsat_dash", "ryu_hit_1", "ryu_hit_2", "ryu_hit_3", "ryu_final_hit_1", "ryu_final_hit_2", "ryu_helping_start", "ryu_helping_slam", "ryu_helping_hit", "ryu_helping_air", "ryu_helping_air_2", "ryu_comb", "ryu_hair", "ryu_sweet_comb", "ryu_sweet", "ryu_recovery", "ryu_ult_start", "ryu_ult_fire", "ryu_ult_hit", "ryu_jingle", "ryu_music", "ryu_after_swing", "ryu_after_slam", "ryu_after_dash", "ryu_after_hit", "ryu_after_first_hit", "ryu_after_dismember", "ryu_noidea_start", "ryu_noidea_hit", "ryu_noidea_second", "ryu_noidea_hit_1", "ryu_dessert_start", "ryu_dessert_dash", "ryu_dessert_hit", "ryu_dessert_slide", "ryu_dessert_long_hit", "ryu_dodge", "ryu_dessert_impact", "ryu_dessert_final", "ryu_invited_windup", "ryu_invited_swing", "ryu_invited_charge", "ryu_invited_power_dash", "ryu_invited_full_charge", "ryu_invited_dash", "ryu_invited_hit",
            "swing", "swing_heavy", "hit_light", "hit_heavy", "hit_slam", "block", "parry", "guard_break", "dash", "ground_impact",
            "heavy_charge", "no_energy",
            "infinity_on", "infinity_off", "infinity_ripple", "infinity_hold",
            "blue_cast", "blue_spawn", "blue_hum", "blue_collapse",
            "red_charge", "red_fire", "red_explosion", "red_amplified",
            "purple_form", "purple_fusion", "purple_fire", "purple_travel", "purple_end",
            "teleport",
            "domain_charge", "domain_expand", "domain_ambient", "domain_collapse", "domain_surehit", "domain_clash",
            "awaken", "awaken_end", "finisher", "domain_block", "max_charge",
            "red_compress", "teleport_out", "teleport_in", "max_blue_hum", "max_blue_collapse", "max_red_explosion", "purple_collision",
            "clash_start", "clash_perfect", "clash_hit", "clash_miss", "clash_win", "clash_beat", "clash_countdown",
            // Hakari
            "ball_throw", "ball_hit", "ball_ricochet", "shutter_rise", "shutter_slam", "rough_charge", "rough_impact", "fever_kick", "fever_rush", "fever_break", "door_open", "door_block", "door_slam", "gamble_visual", "gamble_spin", "gamble_riichi", "gamble_signal", "gamble_stop", "gamble_miss", "jackpot", "jackpot_heal", "jackpot_end", "lucky_hit", "lucky_final", "surge_vanish", "surge_appear", "rhythm_beat", "rhythm_tick", "idg_ambient",
            // Per-move sounds from the JJS audio (tools/roblox_sounds.py)
            "side_dash", "purple_music", "uv_music", "awaken_grab", "max_blue_wind", "max_blue_absorb", "max_red_charge", "max_red_fire",
            "shutter_divide", "rough_air", "fever_hit", "fever_crush", "idg_voice", "idg_sealed", "idg_music", "jackpot_music",
            "rushdown_rush", "rushdown_grab", "overwhelm_fist", "overwhelm_swing", "surge_dash", "surge_hit", "surge_launch",
            "clash_music", "twofold_swing1", "twofold_swing2", "twofold_hit1", "twofold_hit2", "face_grater_drag", "face_grater_throw",
            "red_max_blackflash", "unlimited_purple_start", "unlimited_purple_explode", "zero_two_open", "zero_two_music", "zero_two_hit",
            "zero_two_barrage", "zero_two_boost", "zero_two_slowdown", "zero_two_breathe", "ragdoll_fall",
            // Yuji / Vessel and the King of Curses
            "cursed_strikes_start", "cursed_strikes_slide", "cursed_strikes_hit", "cursed_strikes_spin", "cursed_strikes_impact", "crushing_charge", "crushing_fist", "crushing_impact", "crushing_hit", "divergent_charge", "divergent", "divergent_hit", "black_flash", "black_flash_chain", "black_flash_windup", "black_flash_heavy", "kokusen", "entrusted_music", "manji_startup", "manji_dodge", "manji_swing", "manji_slam", "manji_crush", "sukuna_awaken", "dismantle_slash", "dismantle_finish", "dismantle_spin", "wcs_line", "wcs_line_1", "wcs_line_2", "wcs_line_3", "open_hands", "open_clap", "open_arrow", "open_idle", "open_fire", "open_explode", "rush_start", "rush_hit", "rush_break", "rush_slam", "shrine_voice", "shrine_expand", "shrine_ring", "shrine_music", "shrine_ready", "shrine_splash",
            // Yuta / Cursed Partners
            "yuta_swing_1", "yuta_swing_2", "yuta_swing_3", "yuta_swing_4", "yuta_hit_1", "yuta_hit_2", "yuta_hit_3", "yuta_hit_down", "yuta_hit_up", "yuta_metal_hit_1", "yuta_metal_hit_2", "yuta_metal_hit_3", "yuta_heavy_hit", "yuta_slam", "yuta_crush", "severing_start", "severing_swing_1", "severing_swing_2", "severing_swing_3", "severing_hit_1", "severing_hit_2", "severing_hit_3", "severing_hit_4", "veilstep", "resolute_grab", "resolute_leap", "resolute_swing", "resolute_slash", "resolute_bf_windup", "resolute_bf_land", "resolute_bf_finish", "outburst_start", "outburst_charge", "outburst_swing", "outburst_explosion", "outburst_hit", "outburst_parry", "second_wind_dash", "second_wind_grab", "rika_summon", "rika_voice", "rika_move", "rika_launch", "rika_haymaker", "rika_throw_start", "rika_throw_finish", "true_love_start", "true_love_ring", "true_love_arm", "elbow_dash", "elbow_hit", "elbow_teleport", "elbow_barrage", "elbow_final", "speech_start", "speech_dont_move", "speech_plummet", "speech_die", "ripple_start", "ripple_bomb", "fakeout_swing", "fakeout_hit", "fakeout_burst", "tlb_charge", "tlb_power", "tlb_small", "aml_voice", "aml_start", "aml_music", "aml_sword_ground", "aml_pickup", "aml_run_slash", "aml_cleave", "aml_dismantle", "aml_glass", "aml_clairvoyance", "aml_mini_rika", "aml_mini_fly", "jacobs_ladder"
    };
    private static final Map<String, SoundEvent> SOUNDS = new LinkedHashMap<>();

    private ModSounds() {}

    public static void init() {
        for (String n : NAMES) {
            SOUNDS.put(n, Registry.register(BuiltInRegistries.SOUND_EVENT, JJK.id(n), SoundEvent.createVariableRangeEvent(JJK.id(n))));
        }
    }

    public static SoundEvent get(String name) {
        SoundEvent s = SOUNDS.get(name);
        if (s == null) throw new IllegalArgumentException("Unknown sound " + name);
        return s;
    }
}
