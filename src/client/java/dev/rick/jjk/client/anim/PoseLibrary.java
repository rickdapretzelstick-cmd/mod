package dev.rick.jjk.client.anim;

import java.util.HashMap;
import java.util.Map;

import static dev.rick.jjk.client.anim.Part.*;

/**
 * Every pose animation in the mod. Angles are degrees on the vanilla humanoid model:
 * arm X -90 = pointing forward, -180 = straight up; right arm +Z = away from the body (left arm mirrored);
 * body Y = twist (negative turns the right shoulder forward).
 */
public final class PoseLibrary {
    private static final Map<String, AnimDef> ANIMS = new HashMap<>();

    private PoseLibrary() {}

    public static AnimDef get(String name) {
        return ANIMS.get(name);
    }

    private static void add(AnimDef def) {
        ANIMS.put(def.name, def);
    }

    static {
        // --- Light chain: jab, jab, hook, finisher ---
        AnimDef jab = AnimDef.builder("light_1", 9)
                .key(RIGHT_ARM, 0, -30, 0, 12).key(RIGHT_ARM, 2, -96, -8, 0).key(RIGHT_ARM, 5, -88, -4, 2).key(RIGHT_ARM, 9, -40, 0, 8)
                .key(LEFT_ARM, 0, -50, 20, -10).key(LEFT_ARM, 9, -50, 20, -10)
                .key(BODY, 0, 0, 5, 0).key(BODY, 2, 0, -18, 0).key(BODY, 9, 0, -5, 0).build();
        add(jab);
        add(jab.mirrored("light_2"));
        add(AnimDef.builder("light_3", 10)
                .key(RIGHT_ARM, 0, -70, 55, 40).key(RIGHT_ARM, 3, -92, -35, 5).key(RIGHT_ARM, 6, -85, -45, 0).key(RIGHT_ARM, 10, -40, 0, 10)
                .key(LEFT_ARM, 0, -45, 25, -10).key(LEFT_ARM, 10, -45, 25, -10)
                .key(BODY, 0, 0, 20, 0).key(BODY, 3, 0, -28, 0).key(BODY, 10, 0, -8, 0).build());
        add(AnimDef.builder("light_4", 14).blend(1, 4)
                .key(RIGHT_ARM, 0, 35, 10, 25).key(RIGHT_ARM, 3, -104, -5, 0).key(RIGHT_ARM, 8, -96, 0, 0).key(RIGHT_ARM, 14, -30, 0, 8)
                .key(LEFT_ARM, 0, -70, 25, -15).key(LEFT_ARM, 3, 30, 0, -20).key(LEFT_ARM, 14, -20, 0, -8)
                .key(BODY, 0, 5, 30, 0).key(BODY, 3, 10, -35, 0).key(BODY, 14, 0, -5, 0)
                .key(RIGHT_LEG, 0, 10, 0, 0).key(RIGHT_LEG, 3, 25, 0, 0).key(RIGHT_LEG, 14, 0, 0, 0)
                .key(LEFT_LEG, 3, -25, 0, 0).key(LEFT_LEG, 14, 0, 0, 0).build());
        add(AnimDef.builder("uppercut", 13).blend(1, 4)
                .key(RIGHT_ARM, 0, 25, 0, 15).key(RIGHT_ARM, 3, -165, -10, -5).key(RIGHT_ARM, 8, -160, 0, 0).key(RIGHT_ARM, 13, -40, 0, 8)
                .key(LEFT_ARM, 0, -40, 0, -10).key(LEFT_ARM, 13, -20, 0, -8)
                .key(BODY, 0, 18, 10, 0).key(BODY, 3, -12, -15, 0).key(BODY, 13, 0, 0, 0)
                .key(HEAD, 0, 10, 0, 0).key(HEAD, 3, -20, 0, 0).key(HEAD, 13, 0, 0, 0).build());
        add(AnimDef.builder("downslam", 13).blend(1, 4)
                .key(RIGHT_ARM, 0, -170, 0, 15).key(RIGHT_ARM, 3, -35, 0, 5).key(RIGHT_ARM, 13, -20, 0, 5)
                .key(LEFT_ARM, 0, -170, 0, -15).key(LEFT_ARM, 3, -35, 0, -5).key(LEFT_ARM, 13, -20, 0, -5)
                .key(BODY, 0, -10, 0, 0).key(BODY, 3, 28, 0, 0).key(BODY, 13, 5, 0, 0)
                .key(RIGHT_LEG, 0, -50, 0, 0).key(RIGHT_LEG, 13, -10, 0, 0).key(LEFT_LEG, 0, -30, 0, 0).key(LEFT_LEG, 13, -5, 0, 0).build());
        // Air chain: same strikes with tucked legs.
        for (int i = 1; i <= 3; i++) {
            AnimDef base = ANIMS.get("light_" + i);
            AnimDef.Builder b = AnimDef.builder("air_" + i, base.duration);
            for (Part p : Part.values()) {
                if (!base.animates(p)) continue;
                for (AnimDef.Key k : base.tracks.get(p)) b.key(p, k.time(), k.x(), k.y(), k.z());
            }
            b.key(RIGHT_LEG, 0, -45, 0, 5).key(RIGHT_LEG, base.duration, -45, 0, 5).key(LEFT_LEG, 0, -15, 0, -5).key(LEFT_LEG, base.duration, -15, 0, -5);
            add(b.build());
        }
        add(AnimDef.builder("sprint", 16).blend(1, 4)
                .key(RIGHT_ARM, 0, -40, 0, 20).key(RIGHT_ARM, 3, -100, -5, 0).key(RIGHT_ARM, 10, -95, 0, 0).key(RIGHT_ARM, 16, -30, 0, 8)
                .key(LEFT_ARM, 0, 20, 0, -15).key(LEFT_ARM, 3, 45, 0, -20).key(LEFT_ARM, 16, 0, 0, -8)
                .key(BODY, 0, 10, 10, 0).key(BODY, 3, 25, -20, 0).key(BODY, 16, 0, 0, 0)
                .key(RIGHT_LEG, 3, 30, 0, 0).key(RIGHT_LEG, 16, 0, 0, 0).key(LEFT_LEG, 3, -40, 0, 0).key(LEFT_LEG, 16, 0, 0, 0).build());
        add(AnimDef.builder("stomp", 13)
                .key(RIGHT_LEG, 0, 0, 0, 0).key(RIGHT_LEG, 2, -70, 0, 5).key(RIGHT_LEG, 4, 15, 0, 0).key(RIGHT_LEG, 13, 0, 0, 0)
                .key(BODY, 0, 0, 0, 0).key(BODY, 4, 18, 0, 0).key(BODY, 13, 0, 0, 0)
                .key(RIGHT_ARM, 2, -20, 0, 45).key(RIGHT_ARM, 13, 0, 0, 5).key(LEFT_ARM, 2, -20, 0, -45).key(LEFT_ARM, 13, 0, 0, -5).build());
        // Heavy: wind-up (held) then release.
        add(AnimDef.builder("heavy_charge", 6).hold()
                .key(RIGHT_ARM, 0, -40, 0, 10).key(RIGHT_ARM, 6, 55, 25, 35)
                .key(LEFT_ARM, 0, -40, 0, -10).key(LEFT_ARM, 6, -75, 30, -10)
                .key(BODY, 0, 0, 0, 0).key(BODY, 6, 5, 35, 0)
                .key(RIGHT_LEG, 6, 20, 0, 0).key(LEFT_LEG, 6, -20, 0, 0).build());
        add(AnimDef.builder("heavy", 16).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, 55, 25, 35).key(RIGHT_ARM, 3, -100, -12, 0).key(RIGHT_ARM, 10, -95, -5, 0).key(RIGHT_ARM, 16, -30, 0, 8)
                .key(LEFT_ARM, 0, -75, 30, -10).key(LEFT_ARM, 3, 35, 0, -20).key(LEFT_ARM, 16, 0, 0, -8)
                .key(BODY, 0, 5, 35, 0).key(BODY, 3, 12, -40, 0).key(BODY, 16, 0, -5, 0)
                .key(RIGHT_LEG, 3, 25, 0, 0).key(RIGHT_LEG, 16, 0, 0, 0).key(LEFT_LEG, 3, -30, 0, 0).key(LEFT_LEG, 16, 0, 0, 0).build());
        add(AnimDef.builder("guard", 3).hold().blend(1, 2)
                .key(RIGHT_ARM, 0, -60, 0, 0).key(RIGHT_ARM, 3, -95, -42, 0)
                .key(LEFT_ARM, 0, -60, 0, 0).key(LEFT_ARM, 3, -95, 42, 0)
                .key(BODY, 3, 8, 0, 0).key(HEAD, 3, 8, 0, 0).build());
        // Dashes: lean into the motion, arms trailing.
        add(AnimDef.builder("dash_forward", 9).key(BODY, 0, 25, 0, 0).key(BODY, 9, 0, 0, 0)
                .key(RIGHT_ARM, 0, 50, 0, 15).key(RIGHT_ARM, 9, 0, 0, 5).key(LEFT_ARM, 0, 50, 0, -15).key(LEFT_ARM, 9, 0, 0, -5)
                .key(RIGHT_LEG, 0, 30, 0, 0).key(RIGHT_LEG, 9, 0, 0, 0).key(LEFT_LEG, 0, -30, 0, 0).key(LEFT_LEG, 9, 0, 0, 0).build());
        add(AnimDef.builder("dash_back", 9).key(BODY, 0, -18, 0, 0).key(BODY, 9, 0, 0, 0)
                .key(RIGHT_ARM, 0, -60, 0, 25).key(RIGHT_ARM, 9, 0, 0, 5).key(LEFT_ARM, 0, -60, 0, -25).key(LEFT_ARM, 9, 0, 0, -5).build());
        add(AnimDef.builder("dash_left", 9).key(BODY, 0, 0, 0, 20).key(BODY, 9, 0, 0, 0)
                .key(RIGHT_ARM, 0, 0, 0, 40).key(RIGHT_ARM, 9, 0, 0, 5).key(LEFT_ARM, 0, 0, 0, -10).key(LEFT_ARM, 9, 0, 0, -5)
                .key(RIGHT_LEG, 0, 0, 0, 25).key(RIGHT_LEG, 9, 0, 0, 0).build());
        add(ANIMS.get("dash_left").mirrored("dash_right"));
        // --- Techniques ---
        add(AnimDef.builder("infinity_on", 18).blend(2, 5)
                .key(RIGHT_ARM, 0, -20, 0, 5).key(RIGHT_ARM, 4, -115, -15, 0).key(RIGHT_ARM, 12, -110, -15, 0).key(RIGHT_ARM, 18, -20, 0, 5)
                .key(HEAD, 4, 5, 0, 0).key(HEAD, 18, 0, 0, 0).build());
        add(AnimDef.builder("blue_cast", 16).blend(1.5f, 5)
                .key(RIGHT_ARM, 0, -40, 0, 20).key(RIGHT_ARM, 4, -95, -12, 0).key(RIGHT_ARM, 12, -92, -10, 0).key(RIGHT_ARM, 16, -40, 0, 8)
                .key(LEFT_ARM, 0, 0, 0, -5).key(LEFT_ARM, 16, 0, 0, -5)
                .key(BODY, 4, 0, -12, 0).key(BODY, 16, 0, 0, 0).key(HEAD, 4, 3, 0, 0).build());
        add(AnimDef.builder("red_charge", 6).hold().blend(1.5f, 3)
                .key(RIGHT_ARM, 0, -60, 0, 10).key(RIGHT_ARM, 6, -92, -6, 0)
                .key(LEFT_ARM, 0, -30, 0, -5).key(LEFT_ARM, 6, -78, 42, 0)
                .key(BODY, 6, 4, -10, 0).key(RIGHT_LEG, 6, 12, 0, 0).key(LEFT_LEG, 6, -12, 0, 0).build());
        add(AnimDef.builder("red_release", 12).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, -92, -6, 0).key(RIGHT_ARM, 2, -135, -6, 10).key(RIGHT_ARM, 12, -40, 0, 8)
                .key(LEFT_ARM, 0, -78, 42, 0).key(LEFT_ARM, 12, -10, 0, -5)
                .key(BODY, 0, 4, -10, 0).key(BODY, 2, -12, 5, 0).key(BODY, 12, 0, 0, 0).build());
        add(AnimDef.builder("purple_blue", 10).hold().blend(2, 3)
                .key(LEFT_ARM, 0, -30, 0, -10).key(LEFT_ARM, 10, -85, 25, -35)
                .key(RIGHT_ARM, 10, -10, 0, 10).key(BODY, 10, 0, 10, 0).build());
        add(AnimDef.builder("purple_red", 10).hold().blend(0.5f, 3)
                .key(LEFT_ARM, 0, -85, 25, -35).key(LEFT_ARM, 10, -85, 25, -35)
                .key(RIGHT_ARM, 0, -10, 0, 10).key(RIGHT_ARM, 10, -85, -25, 35)
                .key(BODY, 0, 0, 10, 0).key(BODY, 10, 0, 0, 0).build());
        add(AnimDef.builder("purple_fusion", 14).hold().blend(0.5f, 3)
                .key(LEFT_ARM, 0, -85, 25, -35).key(LEFT_ARM, 14, -95, -18, 0)
                .key(RIGHT_ARM, 0, -85, -25, 35).key(RIGHT_ARM, 14, -95, 18, 0)
                .key(BODY, 14, 5, 0, 0).key(HEAD, 14, 8, 0, 0).build());
        add(AnimDef.builder("purple_release", 18).blend(0.5f, 6)
                .key(RIGHT_ARM, 0, -95, 18, 0).key(RIGHT_ARM, 3, -100, -5, 0).key(RIGHT_ARM, 12, -95, 0, 0).key(RIGHT_ARM, 18, -30, 0, 8)
                .key(LEFT_ARM, 0, -95, -18, 0).key(LEFT_ARM, 3, 30, 0, -25).key(LEFT_ARM, 18, 0, 0, -8)
                .key(BODY, 0, 5, 0, 0).key(BODY, 3, -10, -25, 0).key(BODY, 18, 0, 0, 0)
                .key(RIGHT_LEG, 3, 25, 0, 0).key(RIGHT_LEG, 18, 0, 0, 0).key(LEFT_LEG, 3, -30, 0, 0).key(LEFT_LEG, 18, 0, 0, 0).build());
        add(AnimDef.builder("teleport", 8).key(BODY, 0, 15, 0, 0).key(BODY, 8, 0, 0, 0)
                .key(RIGHT_ARM, 0, -30, 0, 30).key(RIGHT_ARM, 8, 0, 0, 5).key(LEFT_ARM, 0, -30, 0, -30).key(LEFT_ARM, 8, 0, 0, -5).build());
        add(AnimDef.builder("teleport_strike", 10)
                .key(RIGHT_ARM, 0, 30, 0, 20).key(RIGHT_ARM, 10, -60, 0, 10).key(LEFT_ARM, 0, -60, 20, -10).key(LEFT_ARM, 10, -30, 0, -5)
                .key(BODY, 0, 12, 20, 0).key(BODY, 10, 0, 0, 0).build());
        // Awakening: right hand reaches up to the blindfold, drags it down, then the arms throw wide as the energy erupts.
        add(AnimDef.builder("awaken", 34).blend(2, 6)
                .key(RIGHT_ARM, 0, -20, 0, 10).key(RIGHT_ARM, 6, -150, -35, 0).key(RIGHT_ARM, 11, -150, -35, 0).key(RIGHT_ARM, 15, -60, -20, 10)
                .key(RIGHT_ARM, 19, -70, 0, 75).key(RIGHT_ARM, 30, -60, 0, 70).key(RIGHT_ARM, 34, 0, 0, 5)
                .key(LEFT_ARM, 0, 0, 0, -5).key(LEFT_ARM, 15, 0, 0, -8).key(LEFT_ARM, 19, -70, 0, -75).key(LEFT_ARM, 30, -60, 0, -70).key(LEFT_ARM, 34, 0, 0, -5)
                .key(HEAD, 0, 0, 0, 0).key(HEAD, 8, 15, 0, 0).key(HEAD, 15, 10, 0, 0).key(HEAD, 19, -25, 0, 0).key(HEAD, 34, 0, 0, 0)
                .key(BODY, 15, 5, 0, 0).key(BODY, 19, -12, 0, 0).key(BODY, 34, 0, 0, 0).build());
        // Max Blue: both hands forward, gathering the attraction between them.
        add(AnimDef.builder("max_blue_cast", 22).blend(1.5f, 6)
                .key(RIGHT_ARM, 0, -40, 0, 30).key(RIGHT_ARM, 8, -100, -25, 0).key(RIGHT_ARM, 14, -95, -15, 0).key(RIGHT_ARM, 22, -30, 0, 8)
                .key(LEFT_ARM, 0, -40, 0, -30).key(LEFT_ARM, 8, -100, 25, 0).key(LEFT_ARM, 14, -95, 15, 0).key(LEFT_ARM, 22, -30, 0, -8)
                .key(BODY, 8, 8, 0, 0).key(BODY, 22, 0, 0, 0).key(HEAD, 8, 6, 0, 0).build());
        // Max Red: a deeper, braced charge.
        add(AnimDef.builder("max_red_charge", 10).hold().blend(1.5f, 3)
                .key(RIGHT_ARM, 0, -60, 0, 10).key(RIGHT_ARM, 10, -95, -8, 0)
                .key(LEFT_ARM, 0, -30, 0, -5).key(LEFT_ARM, 10, -80, 45, 0)
                .key(BODY, 10, 10, -18, 0).key(RIGHT_LEG, 10, 25, 0, 5).key(LEFT_LEG, 10, -25, 0, -5).build());

        // Domain: the crossed-fingers hand sign held at chest height.
        add(AnimDef.builder("domain_sign", 8).hold().blend(2, 4)
                .key(RIGHT_ARM, 0, -40, 0, 10).key(RIGHT_ARM, 8, -112, -38, 0)
                .key(LEFT_ARM, 0, 0, 0, -5).key(LEFT_ARM, 8, 5, 0, -8)
                .key(HEAD, 8, 12, 0, 0).key(BODY, 8, 3, -8, 0).build());
        add(AnimDef.builder("domain_release", 24).blend(0.5f, 8)
                .key(RIGHT_ARM, 0, -112, -38, 0).key(RIGHT_ARM, 5, -60, 0, 70).key(RIGHT_ARM, 16, -55, 0, 65).key(RIGHT_ARM, 24, 0, 0, 5)
                .key(LEFT_ARM, 0, 5, 0, -8).key(LEFT_ARM, 5, -60, 0, -70).key(LEFT_ARM, 16, -55, 0, -65).key(LEFT_ARM, 24, 0, 0, -5)
                .key(BODY, 5, -10, 0, 0).key(BODY, 24, 0, 0, 0).key(HEAD, 5, -15, 0, 0).key(HEAD, 24, 0, 0, 0).build());
        // ===================== Hakari =====================
        // Reserve Balls: a sidearm flick, the hand snapping out three times.
        add(AnimDef.builder("reserve_balls", 16).blend(1, 4)
                .key(RIGHT_ARM, 0, -20, 0, 30).key(RIGHT_ARM, 3, -95, -30, 10).key(RIGHT_ARM, 5, -70, 10, 20).key(RIGHT_ARM, 7, -98, -25, 8)
                .key(RIGHT_ARM, 9, -72, 10, 20).key(RIGHT_ARM, 11, -100, -20, 5).key(RIGHT_ARM, 16, -30, 0, 10)
                .key(LEFT_ARM, 0, -30, 0, -20).key(LEFT_ARM, 16, -20, 0, -10)
                .key(BODY, 0, 0, 15, 0).key(BODY, 3, 0, -15, 0).key(BODY, 16, 0, 0, 0).build());
        // Shutter Doors: both palms slam together in front of him (the doors follow the gesture).
        add(AnimDef.builder("shutter_sign", 12).blend(1, 4)
                .key(RIGHT_ARM, 0, -60, 0, 60).key(RIGHT_ARM, 5, -90, -40, 0).key(RIGHT_ARM, 8, -92, -35, 0).key(RIGHT_ARM, 12, -30, 0, 10)
                .key(LEFT_ARM, 0, -60, 0, -60).key(LEFT_ARM, 5, -90, 40, 0).key(LEFT_ARM, 8, -92, 35, 0).key(LEFT_ARM, 12, -30, 0, -10)
                .key(BODY, 5, 8, 0, 0).key(BODY, 12, 0, 0, 0).build());
        // Rough Energy: a deep coil with the fist cocked back (held through the wind-up), then a driving straight.
        add(AnimDef.builder("rough_charge", 8).hold().blend(1.5f, 3)
                .key(RIGHT_ARM, 0, -40, 0, 10).key(RIGHT_ARM, 8, 50, 20, 30)
                .key(LEFT_ARM, 0, -40, 0, -10).key(LEFT_ARM, 8, -85, 30, -10)
                .key(BODY, 0, 0, 0, 0).key(BODY, 8, 10, 40, 0)
                .key(RIGHT_LEG, 8, 25, 0, 5).key(LEFT_LEG, 8, -30, 0, -5).build());
        add(AnimDef.builder("rough_strike", 14).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, 50, 20, 30).key(RIGHT_ARM, 2, -100, -5, 0).key(RIGHT_ARM, 8, -95, 0, 0).key(RIGHT_ARM, 14, -30, 0, 8)
                .key(LEFT_ARM, 0, -85, 30, -10).key(LEFT_ARM, 2, 30, 0, -20).key(LEFT_ARM, 14, -10, 0, -8)
                .key(BODY, 0, 10, 40, 0).key(BODY, 2, 15, -40, 0).key(BODY, 14, 0, 0, 0)
                .key(RIGHT_LEG, 2, 35, 0, 0).key(RIGHT_LEG, 14, 0, 0, 0).key(LEFT_LEG, 2, -35, 0, 0).key(LEFT_LEG, 14, 0, 0, 0).build());
        // Fever Breaker: a spinning roundhouse, the rush (leaning sprint), and the breaking second kick.
        add(AnimDef.builder("fever_kick", 12).blend(1, 4)
                .key(RIGHT_LEG, 0, 0, 0, 0).key(RIGHT_LEG, 3, -80, 0, 60).key(RIGHT_LEG, 6, -85, 0, 70).key(RIGHT_LEG, 12, 0, 0, 0)
                .key(BODY, 0, 0, 40, 0).key(BODY, 3, -5, -60, -10).key(BODY, 6, -5, -70, -15).key(BODY, 12, 0, 0, 0)
                .key(RIGHT_ARM, 3, -40, 0, 70).key(RIGHT_ARM, 12, 0, 0, 5).key(LEFT_ARM, 3, -40, 0, -70).key(LEFT_ARM, 12, 0, 0, -5).build());
        add(AnimDef.builder("fever_rush", 6).hold().blend(1, 3)
                .key(BODY, 6, 30, 0, 0).key(RIGHT_ARM, 6, 50, 0, 15).key(LEFT_ARM, 6, 50, 0, -15)
                .key(RIGHT_LEG, 6, -40, 0, 0).key(LEFT_LEG, 6, 30, 0, 0).key(HEAD, 6, -20, 0, 0).build());
        add(AnimDef.builder("fever_finish", 14).blend(0.5f, 5)
                .key(LEFT_LEG, 0, 30, 0, 0).key(LEFT_LEG, 2, -110, 0, -10).key(LEFT_LEG, 6, -105, 0, -10).key(LEFT_LEG, 14, 0, 0, 0)
                .key(BODY, 0, 20, 0, 0).key(BODY, 2, -20, 25, 0).key(BODY, 14, 0, 0, 0)
                .key(RIGHT_ARM, 2, -30, 0, 60).key(RIGHT_ARM, 14, 0, 0, 5).key(LEFT_ARM, 2, 20, 0, -40).key(LEFT_ARM, 14, 0, 0, -5).build());
        // Door Guard: forearm up behind the door, braced.
        add(AnimDef.builder("door_guard", 4).hold().blend(1, 3)
                .key(RIGHT_ARM, 0, -40, 0, 10).key(RIGHT_ARM, 4, -100, -50, 0)
                .key(LEFT_ARM, 0, -40, 0, -10).key(LEFT_ARM, 4, -60, 30, -20)
                .key(BODY, 4, 12, -10, 0).key(RIGHT_LEG, 4, 20, 0, 0).key(LEFT_LEG, 4, -20, 0, 0).build());
        // Idle Death Gamble: his hand sign, both hands pressed together at the chest.
        add(AnimDef.builder("idg_sign", 8).hold().blend(2, 4)
                .key(RIGHT_ARM, 0, -40, 0, 10).key(RIGHT_ARM, 8, -85, -45, 0)
                .key(LEFT_ARM, 0, -40, 0, -10).key(LEFT_ARM, 8, -85, 45, 0)
                .key(HEAD, 8, 10, 0, 0).key(BODY, 8, 5, 0, 0).build());
        // Jackpot: arms thrown wide, head back.
        add(AnimDef.builder("jackpot", 30).blend(0.5f, 8)
                .key(RIGHT_ARM, 0, -60, 0, 20).key(RIGHT_ARM, 4, -160, 0, 30).key(RIGHT_ARM, 22, -150, 0, 35).key(RIGHT_ARM, 30, 0, 0, 5)
                .key(LEFT_ARM, 0, -60, 0, -20).key(LEFT_ARM, 4, -160, 0, -30).key(LEFT_ARM, 22, -150, 0, -35).key(LEFT_ARM, 30, 0, 0, -5)
                .key(HEAD, 4, -30, 0, 0).key(HEAD, 22, -25, 0, 0).key(HEAD, 30, 0, 0, 0).key(BODY, 4, -12, 0, 0).key(BODY, 30, 0, 0, 0).build());
        // Lucky Volley: an opener, a blur of alternating jabs, and a final straight.
        add(AnimDef.builder("volley_open", 8).blend(1, 3)
                .key(RIGHT_ARM, 0, -30, 0, 12).key(RIGHT_ARM, 3, -96, -6, 0).key(RIGHT_ARM, 8, -70, 0, 5)
                .key(LEFT_ARM, 0, -50, 20, -10).key(LEFT_ARM, 8, -60, 20, -10).key(BODY, 3, 0, -18, 0).key(BODY, 8, 0, -5, 0).build());
        AnimDef.Builder fl = AnimDef.builder("volley_flurry", 20).blend(1, 3);
        for (int i = 0; i <= 10; i++) {
            boolean r = i % 2 == 0;
            fl.key(RIGHT_ARM, i * 2, r ? -98 : -55, r ? -6 : 10, r ? 0 : 20).key(LEFT_ARM, i * 2, r ? -55 : -98, r ? -10 : 6, r ? -20 : 0)
                    .key(BODY, i * 2, 5, r ? -14 : 14, 0);
        }
        add(fl.build());
        add(AnimDef.builder("volley_final", 12).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, 40, 10, 25).key(RIGHT_ARM, 2, -104, -5, 0).key(RIGHT_ARM, 8, -96, 0, 0).key(RIGHT_ARM, 12, -30, 0, 8)
                .key(BODY, 0, 5, 30, 0).key(BODY, 2, 10, -35, 0).key(BODY, 12, 0, 0, 0)
                .key(RIGHT_LEG, 2, 25, 0, 0).key(LEFT_LEG, 2, -25, 0, 0).key(RIGHT_LEG, 12, 0, 0, 0).key(LEFT_LEG, 12, 0, 0, 0).build());
        // Lucky Rushdown: sprinting charge, a shoulder-first drag, and the heave.
        add(AnimDef.builder("rushdown_run", 6).hold().blend(1, 3)
                .key(BODY, 6, 35, 0, 0).key(HEAD, 6, -25, 0, 0).key(RIGHT_ARM, 6, 60, 0, 10).key(LEFT_ARM, 6, -70, 0, -10)
                .key(RIGHT_LEG, 6, -45, 0, 0).key(LEFT_LEG, 6, 35, 0, 0).build());
        add(AnimDef.builder("rushdown_drag", 4).hold().blend(1, 2)
                .key(BODY, 4, 28, -20, 0).key(RIGHT_ARM, 4, -95, -20, 0).key(LEFT_ARM, 4, -90, 30, 0)
                .key(RIGHT_LEG, 4, -35, 0, 0).key(LEFT_LEG, 4, 30, 0, 0).build());
        add(AnimDef.builder("rushdown_throw", 14).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, -95, -20, 0).key(RIGHT_ARM, 3, -170, 0, 20).key(RIGHT_ARM, 14, -20, 0, 5)
                .key(LEFT_ARM, 0, -90, 30, 0).key(LEFT_ARM, 3, -170, 0, -20).key(LEFT_ARM, 14, -20, 0, -5)
                .key(BODY, 0, 28, 0, 0).key(BODY, 3, -20, 0, 0).key(BODY, 14, 0, 0, 0).build());
        // Overwhelming Luck: ready stance, alternating marching punches, a planted final punch.
        add(AnimDef.builder("overwhelm_ready", 5).blend(1, 2)
                .key(RIGHT_ARM, 5, -70, -20, 10).key(LEFT_ARM, 5, -80, 20, -10).key(BODY, 5, 10, 0, 0).build());
        AnimDef right = AnimDef.builder("overwhelm_right", 4).blend(0.5f, 2)
                .key(RIGHT_ARM, 0, -60, 0, 20).key(RIGHT_ARM, 2, -100, -8, 0).key(RIGHT_ARM, 4, -70, -10, 10)
                .key(LEFT_ARM, 0, -85, 15, -10).key(LEFT_ARM, 4, -70, 20, -10)
                .key(BODY, 0, 8, 12, 0).key(BODY, 2, 10, -20, 0).key(BODY, 4, 8, -10, 0)
                .key(RIGHT_LEG, 2, -25, 0, 0).key(LEFT_LEG, 2, 20, 0, 0).build();
        add(right);
        add(right.mirrored("overwhelm_left"));
        add(AnimDef.builder("overwhelm_final", 16).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, 45, 20, 30).key(RIGHT_ARM, 3, -104, 0, 0).key(RIGHT_ARM, 10, -98, 0, 0).key(RIGHT_ARM, 16, -30, 0, 8)
                .key(LEFT_ARM, 0, -80, 30, -10).key(LEFT_ARM, 3, 35, 0, -25).key(LEFT_ARM, 16, -10, 0, -8)
                .key(BODY, 0, 10, 45, 0).key(BODY, 3, 18, -45, 0).key(BODY, 16, 0, 0, 0)
                .key(RIGHT_LEG, 3, 40, 0, 0).key(LEFT_LEG, 3, -40, 0, 0).key(RIGHT_LEG, 16, 0, 0, 0).key(LEFT_LEG, 16, 0, 0, 0).build());
        // Energy Surge: a low dash, then the descending axe kick.
        add(AnimDef.builder("surge_dash", 8).hold().blend(1, 3)
                .key(BODY, 8, 40, 0, 0).key(RIGHT_ARM, 8, 60, 0, 20).key(LEFT_ARM, 8, 60, 0, -20)
                .key(RIGHT_LEG, 8, -30, 0, 0).key(LEFT_LEG, 8, 40, 0, 0).build());
        add(AnimDef.builder("surge_kick", 14).blend(0.5f, 5)
                .key(RIGHT_LEG, 0, -150, 0, 0).key(RIGHT_LEG, 4, -160, 0, 0).key(RIGHT_LEG, 7, 10, 0, 0).key(RIGHT_LEG, 14, 0, 0, 0)
                .key(BODY, 0, -20, 0, 0).key(BODY, 7, 25, 0, 0).key(BODY, 14, 0, 0, 0)
                .key(RIGHT_ARM, 0, -40, 0, 60).key(LEFT_ARM, 0, -40, 0, -60).key(RIGHT_ARM, 14, 0, 0, 5).key(LEFT_ARM, 14, 0, 0, -5).build());
        // Rhythm: the Jackpot dance — hips and arms swinging to the beat.
        AnimDef.Builder dance = AnimDef.builder("rhythm_dance", 60).blend(2, 6);
        for (int i = 0; i <= 12; i++) {
            boolean a = i % 2 == 0;
            int tt = i * 5;
            dance.key(RIGHT_ARM, tt, a ? -150 : -40, 0, a ? 20 : 60).key(LEFT_ARM, tt, a ? -40 : -150, 0, a ? -60 : -20)
                    .key(BODY, tt, 0, a ? 15 : -15, a ? 6 : -6).key(HEAD, tt, a ? -10 : 5, a ? -10 : 10, 0)
                    .key(RIGHT_LEG, tt, a ? -20 : 10, 0, a ? 8 : 0).key(LEFT_LEG, tt, a ? 10 : -20, 0, a ? 0 : -8);
        }
        add(dance.build());
    }
}
