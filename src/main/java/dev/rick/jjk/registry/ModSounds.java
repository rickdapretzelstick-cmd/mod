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
            "red_compress", "teleport_out", "teleport_in", "max_blue_hum", "max_blue_collapse", "max_red_explosion", "purple_collision"
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
