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
            "cursed_strikes_start", "cursed_strikes_slide", "cursed_strikes_hit", "cursed_strikes_spin", "cursed_strikes_impact", "crushing_charge", "crushing_fist", "crushing_impact", "crushing_hit", "divergent_charge", "divergent", "divergent_hit", "black_flash", "black_flash_chain", "black_flash_windup", "black_flash_heavy", "kokusen", "entrusted_music", "manji_startup", "manji_dodge", "manji_swing", "manji_slam", "manji_crush", "sukuna_awaken", "dismantle_slash", "dismantle_finish", "dismantle_spin", "wcs_line", "wcs_line_1", "wcs_line_2", "wcs_line_3", "open_hands", "open_clap", "open_arrow", "open_idle", "open_fire", "open_explode", "rush_start", "rush_hit", "rush_break", "rush_slam", "shrine_voice", "shrine_expand", "shrine_ring", "shrine_music", "shrine_ready", "shrine_splash"
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
