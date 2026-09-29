package dev.rick.jjk.client.anim;

import java.util.function.Consumer;

import static dev.rick.jjk.client.anim.Moves.rest;
import static dev.rick.jjk.client.anim.Moves.stance;
import static dev.rick.jjk.client.anim.Part.*;

/**
 * Yuji's and Sukuna's moves animated after the JJS Vessel GIFs (see {@link Moves} for the conventions). They replace
 * the poses of the same names in {@link YujiPoses}; the timings match the server's hits.
 */
final class YujiMoves {
    private YujiMoves() {}

    static void register(Consumer<AnimDef> add) {
        // --- Cursed Strikes (GIF): eyes lit, a low sliding lunge, then hooks and crosses thrown with the hips, a calf kick ---
        add.accept(AnimDef.builder("cursed_strikes_ready", 8).hold().blend(1, 2)
                .snap(ROOT, 8, 16, -12, 0).snap(ROOT_POS, 8, 0, -3.5f, -1).snap(RIGHT_ARM, 8, -40, -10, 25).snap(LEFT_ARM, 8, -80, 25, -12)
                .snap(BODY, 8, 0, 18, 0).snap(RIGHT_LEG, 8, 28, 0, 6).snap(LEFT_LEG, 8, -30, 0, -6).snap(HEAD, 8, -8, 0, 0).build());
        add.accept(AnimDef.builder("cursed_strikes_slide", 4).hold().blend(0.5f, 2)
                .snap(ROOT, 4, 34, 6, 0).snap(ROOT_POS, 4, 0, -5, 3).snap(RIGHT_ARM, 4, -100, -30, 18).snap(LEFT_ARM, 4, 55, 20, -25)
                .snap(BODY, 4, 0, -14, 0).snap(RIGHT_LEG, 4, 55, 0, 8).snap(LEFT_LEG, 4, -70, 0, -4).snap(HEAD, 4, -28, 0, 0).build());
        AnimDef.Builder flurry = AnimDef.builder("cursed_strikes_flurry", 30).blend(0.5f, 3)
                .key(RIGHT_LEG, 0, 24, 0, 6).key(LEFT_LEG, 0, -24, 0, -6).key(HEAD, 0, 8, 0, 0);
        for (int i = 0; i < 6; i++) {
            float hit = 5 + i * 5, wind = hit - 3;
            boolean right = i % 2 == 0;
            float s = right ? 1 : -1;
            Part punch = right ? RIGHT_ARM : LEFT_ARM, other = right ? LEFT_ARM : RIGHT_ARM;
            boolean hookPunch = i % 3 == 1;
            if (hookPunch) {
                flurry.key(punch, wind, -78, 55 * s, 70 * s).strike(punch, hit, -94, -45 * s, 8 * s);
            } else {
                flurry.key(punch, wind, -40, -8 * s, 26 * s).strike(punch, hit, -102, -12 * s, 0);
            }
            flurry.settle(punch, hit + 1.5f, -88, -18 * s, 6 * s).key(other, hit, -62, 24 * s, -16 * s)
                    .key(BODY, wind, 0, 22 * s, 0).strike(BODY, hit, 0, -30 * s, 0)
                    .key(ROOT, wind, 10, -14 * s, 0).strike(ROOT, hit, 16, 16 * s, -3 * s)
                    .key(ROOT_POS, wind, 0, -3, 0.5f).strike(ROOT_POS, hit, 0.8f * s, -3, 3);
        }
        add.accept(flurry.build());
        add.accept(stance(AnimDef.builder("cursed_strikes_kick", 12).blend(0.3f, 4)
                // The right calf kick, swung low round into the back of their leg.
                .key(RIGHT_LEG, 0, 15, 0, 6).key(RIGHT_LEG, 1, 25, 0, 15).strike(RIGHT_LEG, 3, -40, 0, 62).settle(RIGHT_LEG, 6, -30, 0, 40)
                .key(ROOT, 0, 12, -10, 0).strike(ROOT, 3, 6, 40, -16).settle(ROOT, 6, 4, 30, -10)
                .key(ROOT_POS, 0, 0, -3, 0).strike(ROOT_POS, 3, 0, -2, 2)
                .strike(RIGHT_ARM, 3, 25, 0, 55).strike(LEFT_ARM, 3, -70, 0, -45).strike(LEFT_LEG, 3, -12, 0, -8), 12).build());
        add.accept(rest(AnimDef.builder("cursed_strikes_blocked", 8).blend(0.5f, 3)
                .key(ROOT, 0, 25, 0, 0).settle(ROOT, 3, -10, 0, 0).key(ROOT_POS, 0, 0, -4, 2).settle(ROOT_POS, 3, 0, -1, -2)
                .key(RIGHT_ARM, 0, -98, -20, 10).settle(RIGHT_ARM, 3, -40, 0, 25).key(LEFT_ARM, 0, -95, 20, -10).settle(LEFT_ARM, 3, -40, 0, -25), 8).build());
        add.accept(stance(AnimDef.builder("cursed_strikes_floor", 10).blend(0.3f, 3)
                .key(RIGHT_ARM, 0, -178, 0, 10).strike(RIGHT_ARM, 3, -25, 0, 6).key(ROOT, 0, -10, 0, 0).strike(ROOT, 3, 48, 0, 0)
                .key(ROOT_POS, 0, 0, 1, 0).strike(ROOT_POS, 3, 0, -7, 3).settle(ROOT, 7, 40, 0, 0)
                .strike(RIGHT_LEG, 3, -50, 0, 4).strike(LEFT_LEG, 3, 35, 0, -4).strike(LEFT_ARM, 3, 30, 0, -40), 10).build());
        add.accept(stance(AnimDef.builder("cursed_strikes_spin", 14).blend(0.3f, 4)
                // A full spin kick through them.
                .key(ROOT, 0, 4, 0, 0).spin(ROOT, 4, 0, -180, -12).spin(ROOT, 8, 0, -360, -6).settle(ROOT, 11, 4, -360, 0)
                .key(RIGHT_LEG, 0, 10, 0, 8).key(RIGHT_LEG, 3, -70, 0, 72).key(RIGHT_LEG, 8, -75, 0, 70).settle(RIGHT_LEG, 11, 10, 0, 6)
                .key(RIGHT_ARM, 3, -30, 0, 85).key(LEFT_ARM, 3, -30, 0, -85).key(ROOT_POS, 3, 0, 0, 1), 14).build());
        add.accept(AnimDef.builder("cursed_strikes_hop", 6).hold().blend(1, 2)
                .snap(ROOT, 6, 18, 0, 0).snap(RIGHT_LEG, 6, -95, 0, 6).snap(LEFT_LEG, 6, -70, 0, -6)
                .snap(RIGHT_ARM, 6, -155, 0, 32).snap(LEFT_ARM, 6, -155, 0, -32).snap(HEAD, 6, 10, 0, 0).build());
        add.accept(AnimDef.builder("cursed_strikes_dropkick", 4).hold().blend(0.5f, 2)
                .snap(ROOT, 4, -55, 0, 0).snap(HEAD, 4, 28, 0, 0).snap(RIGHT_LEG, 4, -30, 0, 6).snap(LEFT_LEG, 4, -26, 0, -6)
                .snap(RIGHT_ARM, 4, 45, 0, 65).snap(LEFT_ARM, 4, 45, 0, -65).build());
        add.accept(rest(AnimDef.builder("cursed_strikes_land", 12).blend(0.3f, 5)
                .key(ROOT, 0, 38, 0, 0).key(ROOT_POS, 0, 0, -8, 0).key(RIGHT_LEG, 0, -75, 0, 10).key(LEFT_LEG, 0, 30, 0, -10)
                .key(RIGHT_ARM, 0, -20, 0, 55).key(LEFT_ARM, 0, -20, 0, -55).settle(ROOT, 5, 20, 0, 0).settle(ROOT_POS, 5, 0, -3, 0), 12).build());

        // --- Crushing Blow: the fist cocked low with the cyan charge, the grab and two slams, the release skyward ---
        add.accept(AnimDef.builder("crushing_blow_charge", 7).hold().blend(1, 2)
                .snap(RIGHT_ARM, 7, 35, 20, 32).snap(LEFT_ARM, 7, -85, 22, -14).snap(BODY, 7, 5, 34, 0).snap(ROOT, 7, 18, -24, 0)
                .snap(ROOT_POS, 7, 0, -4, -1).snap(RIGHT_LEG, 7, 26, 0, 6).snap(LEFT_LEG, 7, -32, 0, -6).snap(HEAD, 7, 4, 10, 0).build());
        add.accept(AnimDef.builder("crushing_blow_air", 7).hold().blend(1, 2)
                .snap(RIGHT_ARM, 7, 45, 20, 36).snap(LEFT_ARM, 7, -100, 20, -15).snap(BODY, 7, 5, 30, 0).snap(ROOT, 7, 22, -20, 0)
                .snap(RIGHT_LEG, 7, -65, 0, 5).snap(LEFT_LEG, 7, -25, 0, -5).build());
        add.accept(AnimDef.builder("crushing_blow_dash", 4).hold().blend(0.5f, 2)
                .snap(RIGHT_ARM, 4, -100, -10, 0).snap(LEFT_ARM, 4, 45, 0, -30).snap(BODY, 4, 0, -25, 0).snap(ROOT, 4, 34, 14, 0)
                .snap(RIGHT_LEG, 4, 35, 0, 0).snap(LEFT_LEG, 4, -55, 0, 0).snap(HEAD, 4, -25, 0, 0).build());
        AnimDef.Builder slam = AnimDef.builder("crushing_blow_slam", 24).blend(0.3f, 3)
                .key(RIGHT_ARM, 0, -98, -12, 0).key(LEFT_ARM, 0, -98, 12, 0).key(RIGHT_LEG, 0, 26, 0, 6).key(LEFT_LEG, 0, -30, 0, -6);
        for (float hit : new float[] {3, 13}) {
            // Hauled up by the torso, then driven into the floor.
            slam.key(RIGHT_ARM, hit - 3, -150, -12, 0).key(LEFT_ARM, hit - 3, -150, 12, 0).key(ROOT, hit - 3, -12, 0, 0).key(ROOT_POS, hit - 3, 0, 1, 0)
                    .strike(RIGHT_ARM, hit, -25, -12, 5).strike(LEFT_ARM, hit, -25, 12, -5).strike(ROOT, hit, 50, 0, 0).strike(ROOT_POS, hit, 0, -8, 3)
                    .settle(ROOT, hit + 3, 40, 0, 0);
        }
        slam.key(RIGHT_ARM, 19, -95, -12, 0).key(LEFT_ARM, 19, -95, 12, 0).key(ROOT, 19, 10, 0, 0).key(ROOT_POS, 19, 0, -3, 0)
                .key(RIGHT_ARM, 24, -60, -10, 10).key(LEFT_ARM, 24, -60, 10, -10);
        add.accept(slam.build());
        add.accept(rest(AnimDef.builder("crushing_blow_release", 10).blend(0.3f, 3)
                .key(RIGHT_ARM, 0, -95, -12, 0).key(LEFT_ARM, 0, -95, 12, 0).key(ROOT, 0, 10, 0, 0).key(ROOT_POS, 0, 0, -3, 0)
                .strike(RIGHT_ARM, 3, -175, -8, 12).strike(LEFT_ARM, 3, -175, 8, -12).strike(ROOT, 3, -16, 0, 0).strike(ROOT_POS, 3, 0, 1, 0)
                .strike(HEAD, 3, -20, 0, 0), 10).build());
        add.accept(stance(AnimDef.builder("crushing_blow_whiff", 12).blend(0.3f, 4)
                .key(RIGHT_ARM, 0, 35, 20, 32).key(ROOT, 0, 18, -24, 0).key(ROOT_POS, 0, 0, -4, -1)
                .strike(RIGHT_ARM, 2, -25, 0, 10).strike(ROOT, 2, 55, 10, 0).strike(ROOT_POS, 2, 0, -9, 3).settle(ROOT, 6, 45, 8, 0)
                .strike(RIGHT_LEG, 2, 35, 0, 6).strike(LEFT_LEG, 2, -45, 0, -6).strike(LEFT_ARM, 2, 30, 0, -35), 12).build());

        // --- Divergent Fist (GIF): the arm drawn far back and low, the cyan energy on the fist; the lunging punch ---
        add.accept(AnimDef.builder("divergent_windup", 9).hold().blend(1, 2)
                .key(RIGHT_ARM, 0, -40, 0, 12).snap(RIGHT_ARM, 7, 50, 28, 38).key(RIGHT_ARM, 9, 55, 30, 40)
                .snap(LEFT_ARM, 7, -88, 34, -10).snap(BODY, 7, 6, 44, 0).snap(ROOT, 7, 10, -36, 0).snap(ROOT_POS, 7, 0, -4, -1.5f)
                .snap(RIGHT_LEG, 7, 30, 0, 6).snap(LEFT_LEG, 7, -32, 0, -6).snap(HEAD, 7, 4, 20, 0).build());
        AnimDef.Builder punch = AnimDef.builder("divergent_punch", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 55, 30, 40).key(LEFT_ARM, 0, -88, 34, -10).key(BODY, 0, 6, 44, 0).key(ROOT, 0, 10, -36, 0).key(ROOT_POS, 0, 0, -4, -1.5f)
                .key(RIGHT_LEG, 0, 30, 0, 6).key(LEFT_LEG, 0, -32, 0, -6).key(HEAD, 0, 4, 20, 0)
                .strike(RIGHT_ARM, 1.5f, -104, -10, 0).strike(LEFT_ARM, 1.5f, 42, 0, -26).strike(BODY, 1.5f, 10, -44, 0).strike(ROOT, 1.5f, 20, 26, 0)
                .strike(ROOT_POS, 1.5f, 0, -4, 9).strike(RIGHT_LEG, 1.5f, 44, 0, 6).strike(LEFT_LEG, 1.5f, -42, 0, -6).strike(HEAD, 1.5f, 10, 0, 0)
                .settle(RIGHT_ARM, 7, -98, -6, 2).settle(ROOT_POS, 7, 0, -3, 8).settle(ROOT, 7, 16, 22, 0);
        add.accept(stance(punch, 14).build());
        AnimDef.Builder bf = AnimDef.builder("black_flash_punch", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 55, 30, 40).key(LEFT_ARM, 0, -88, 34, -10).key(BODY, 0, 6, 44, 0).key(ROOT, 0, 10, -36, 0).key(ROOT_POS, 0, 0, -4, -1.5f)
                .key(RIGHT_LEG, 0, 30, 0, 6).key(LEFT_LEG, 0, -32, 0, -6)
                .strike(RIGHT_ARM, 1.5f, -108, -12, 0).strike(LEFT_ARM, 1.5f, 55, 0, -32).strike(BODY, 1.5f, 12, -50, 0).strike(ROOT, 1.5f, 24, 32, 0)
                .strike(ROOT_POS, 1.5f, 0, -5, 11).strike(RIGHT_LEG, 1.5f, 52, 0, 6).strike(LEFT_LEG, 1.5f, -48, 0, -6).strike(HEAD, 1.5f, 12, 20, 0)
                .settle(ROOT_POS, 8, 0, -4, 10).settle(ROOT, 8, 20, 28, 0);
        add.accept(stance(bf, 14).build());
        add.accept(stance(AnimDef.builder("black_flash_uppercut", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 40, 20, 30).key(ROOT, 0, 24, -24, 0).key(ROOT_POS, 0, 0, -6, 0).key(RIGHT_LEG, 0, -25, 0, 4).key(LEFT_LEG, 0, 18, 0, -4)
                .strike(RIGHT_ARM, 2, -175, -10, 0).strike(ROOT, 2, -18, 18, 0).strike(ROOT_POS, 2, 0, 3, 3).strike(HEAD, 2, -28, 0, 0)
                .strike(LEFT_ARM, 2, 30, 0, -25).strike(RIGHT_LEG, 2, 10, 0, 4).strike(LEFT_LEG, 2, -10, 0, -4)
                .settle(ROOT, 7, -12, 12, 0), 14).build());
        add.accept(rest(AnimDef.builder("black_flash_dropkick", 16).blend(0.3f, 5)
                .key(ROOT, 0, 12, 0, 0).strike(ROOT, 3, -60, 0, 0).key(ROOT, 9, -55, 0, 0).key(ROOT_POS, 0, 0, -2, 0).strike(ROOT_POS, 3, 0, 7, 6)
                .strike(RIGHT_LEG, 3, -35, 0, 6).strike(LEFT_LEG, 3, -30, 0, -6).key(RIGHT_LEG, 9, -35, 0, 6).key(LEFT_LEG, 9, -30, 0, -6)
                .strike(RIGHT_ARM, 3, 35, 0, 72).strike(LEFT_ARM, 3, 35, 0, -72), 16).build());

        // --- Manji Kick (GIF): arm raised, one knee up, weight back; the Taido roundhouse spinning upward ---
        add.accept(AnimDef.builder("manji_stance", 3).hold().blend(0.5f, 2)
                .snap(RIGHT_ARM, 3, -152, -20, 22).snap(LEFT_ARM, 3, -72, 30, -20).snap(BODY, 3, 0, 24, 0).snap(ROOT, 3, -8, -10, 4)
                .snap(ROOT_POS, 3, 0, 1, -1).snap(RIGHT_LEG, 3, -90, 0, 10).snap(LEFT_LEG, 3, 8, 0, -5).snap(HEAD, 3, 0, -18, 0).build());
        add.accept(rest(AnimDef.builder("manji_recover", 8).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, -152, -20, 22).key(RIGHT_LEG, 0, -90, 0, 10).key(ROOT, 0, -8, -10, 4), 8).build());
        add.accept(rest(AnimDef.builder("manji_kick", 14).blend(0.3f, 5)
                .key(RIGHT_LEG, 0, -90, 0, 10).strike(RIGHT_LEG, 3, -160, 0, 62).settle(RIGHT_LEG, 7, -120, 0, 80)
                .key(ROOT, 0, -8, -10, 4).spin(ROOT, 3, -28, -140, -30).settle(ROOT, 8, -10, -330, -8)
                .key(ROOT_POS, 0, 0, 1, -1).strike(ROOT_POS, 3, 0, 6, 1).settle(ROOT_POS, 8, 0, 2, 1)
                .strike(RIGHT_ARM, 3, 35, 0, 82).strike(LEFT_ARM, 3, 25, 0, -82).strike(LEFT_LEG, 3, 18, 0, -12), 14).build());
        add.accept(rest(AnimDef.builder("manji_dodge", 6).blend(0.2f, 2)
                .snap(ROOT, 3, 22, 0, 42).snap(ROOT_POS, 3, 3, -3, 0).snap(RIGHT_ARM, 3, -20, 0, 90).snap(LEFT_ARM, 3, -20, 0, -90)
                .snap(RIGHT_LEG, 3, -30, 0, 40).snap(LEFT_LEG, 3, 20, 0, -20), 6).build());
        add.accept(AnimDef.builder("manji_swoop", 8).hold().blend(0.5f, 2)
                .key(ROOT, 0, 22, 0, 42).key(ROOT, 4, 60, 0, -30).key(ROOT, 8, 40, 0, 20)
                .key(RIGHT_LEG, 4, -100, 0, 40).key(LEFT_LEG, 4, -40, 0, -20).key(RIGHT_ARM, 4, 0, 0, 100).key(LEFT_ARM, 4, 0, 0, -100).build());

        // --- Combat Instincts: the flinch that throws the move away; the punch that sends a prop flying ---
        add.accept(stance(AnimDef.builder("instincts_feint", 8).blend(0.2f, 4)
                .snap(ROOT, 2, -10, 12, 0).snap(ROOT_POS, 2, 0, -1, -2).snap(RIGHT_ARM, 2, -70, -30, 20).snap(LEFT_ARM, 2, -85, 30, -15)
                .snap(HEAD, 2, -6, 0, 0), 8).build());
        add.accept(stance(Moves.straight(AnimDef.builder("instincts_throw", 12).blend(0.3f, 4), 0, 2, 1.6f, 0), 12).build());

        // --- Shrine M1s (GIF): open-handed swipes that throw slashes, the whole body turning through them ---
        AnimDef swipe = stance(AnimDef.builder("shrine_light_1", 9).blend(0.5f, 3)
                .key(RIGHT_ARM, 0, -105, 60, 45).strike(RIGHT_ARM, 3, -88, -65, -10).settle(RIGHT_ARM, 5, -80, -75, -5)
                .key(BODY, 0, 0, 28, 0).strike(BODY, 3, 0, -34, 0).key(ROOT, 0, 2, -14, 0).strike(ROOT, 3, 8, 18, 0)
                .key(ROOT_POS, 0, 0, -1, 0).strike(ROOT_POS, 3, 0, -1.5f, 2).key(LEFT_ARM, 0, -40, 0, -18).strike(LEFT_ARM, 3, -20, 0, -30)
                .strike(RIGHT_LEG, 3, 18, 0, 4).strike(LEFT_LEG, 3, -18, 0, -4), 9).build();
        add.accept(swipe);
        add.accept(swipe.mirrored("shrine_light_2"));
        add.accept(stance(AnimDef.builder("shrine_light_3", 10).blend(0.5f, 3)
                .key(RIGHT_ARM, 0, -178, -10, 12).strike(RIGHT_ARM, 3, -35, -10, 10).key(ROOT, 0, -10, 0, 0).strike(ROOT, 3, 20, 0, 0)
                .key(ROOT_POS, 0, 0, 1, 0).strike(ROOT_POS, 3, 0, -2, 2).strike(LEFT_ARM, 3, -20, 0, -30), 10).build());
        add.accept(stance(AnimDef.builder("shrine_light_4", 14).blend(0.5f, 4)
                // Both hands flung out in an X, cutting everything in front of him.
                .key(RIGHT_ARM, 0, -65, 80, 65).key(LEFT_ARM, 0, -65, -80, -65).key(ROOT, 0, -6, 0, 0).key(ROOT_POS, 0, 0, 0, -1)
                .strike(RIGHT_ARM, 3, -98, -70, -20).strike(LEFT_ARM, 3, -98, 70, 20).strike(ROOT, 3, 16, 0, 0).strike(ROOT_POS, 3, 0, -2, 3)
                .strike(RIGHT_LEG, 3, 20, 0, 5).strike(LEFT_LEG, 3, -20, 0, -5), 14).build());
        for (int i = 1; i <= 4; i++) add.accept(copy(PoseLibrary.get("shrine_light_" + Math.min(3, i)), i < 4 ? "shrine_air_" + i : "shrine_stomp"));
        add.accept(stance(AnimDef.builder("shrine_sprint", 14).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, -65, 80, 65).key(LEFT_ARM, 0, -65, -80, -65).key(ROOT, 0, 20, 0, 0).key(ROOT_POS, 0, 0, -2, 0)
                .strike(RIGHT_ARM, 3, -98, -70, -20).strike(LEFT_ARM, 3, -98, 70, 20).strike(ROOT, 3, 24, 0, 0).strike(ROOT_POS, 3, 0, -3, 5), 14).build());
        add.accept(AnimDef.builder("shrine_heavy_charge", 6).hold()
                .snap(RIGHT_ARM, 6, -112, 80, 52).snap(BODY, 6, 0, 36, 0).snap(ROOT, 6, 4, -22, 0).snap(ROOT_POS, 6, 0, -2, -1)
                .snap(LEFT_ARM, 6, -35, 0, -18).snap(RIGHT_LEG, 6, 20, 0, 5).snap(LEFT_LEG, 6, -20, 0, -5).build());
        add.accept(stance(AnimDef.builder("shrine_heavy", 14).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, -112, 80, 52).key(BODY, 0, 0, 36, 0).key(ROOT, 0, 4, -22, 0).key(ROOT_POS, 0, 0, -2, -1)
                .strike(RIGHT_ARM, 3, -90, -72, -15).strike(BODY, 3, 4, -42, 0).strike(ROOT, 3, 14, 24, 0).strike(ROOT_POS, 3, 0, -2, 4)
                .strike(RIGHT_LEG, 3, 30, 0, 5).strike(LEFT_LEG, 3, -30, 0, -5), 14).build());

        // --- Dismantle (GIF): the arm drawn right across the body, then swept out ---
        add.accept(AnimDef.builder("dismantle_windup", 8).hold().blend(1, 2)
                .snap(RIGHT_ARM, 8, -112, 72, 62).snap(BODY, 8, 0, 36, 0).snap(ROOT, 8, 2, -18, 0).snap(ROOT_POS, 8, 0, -1.5f, 0)
                .snap(HEAD, 8, 0, -20, 0).snap(LEFT_ARM, 8, -25, 0, -16).snap(RIGHT_LEG, 8, 14, 0, 5).snap(LEFT_LEG, 8, -14, 0, -5).build());
        add.accept(stance(AnimDef.builder("dismantle_swing", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, -112, 72, 62).key(BODY, 0, 0, 36, 0).key(ROOT, 0, 2, -18, 0).key(ROOT_POS, 0, 0, -1.5f, 0)
                .strike(RIGHT_ARM, 2, -95, -72, -30).strike(BODY, 2, 0, -42, 0).strike(ROOT, 2, 8, 20, 0).strike(ROOT_POS, 2, 0, -2, 2)
                .key(RIGHT_ARM, 9, -90, -72, -20), 14).build());
        // Cleave: the grab forward; held; the release sweeping back across them.
        add.accept(AnimDef.builder("cleave_reach", 6).hold().blend(0.5f, 2)
                .key(RIGHT_ARM, 0, 40, 20, 30).strike(RIGHT_ARM, 4, -98, -5, 0).key(BODY, 0, 5, 30, 0).strike(BODY, 4, 5, -25, 0)
                .key(ROOT, 0, 4, -16, 0).strike(ROOT, 4, 16, 14, 0).strike(ROOT_POS, 4, 0, -2, 5)
                .strike(LEFT_ARM, 4, 25, 0, -22).strike(RIGHT_LEG, 4, 30, 0, 5).strike(LEFT_LEG, 4, -32, 0, -5).build());
        add.accept(AnimDef.builder("cleave_hold", 4).hold().blend(0.5f, 2)
                .snap(RIGHT_ARM, 4, -102, -10, 0).snap(BODY, 4, 5, -20, 0).snap(ROOT, 4, 10, 10, 0).snap(ROOT_POS, 4, 0, -2, 3)
                .snap(HEAD, 4, -10, 0, 0).snap(LEFT_ARM, 4, 10, 0, -15).build());
        add.accept(stance(AnimDef.builder("cleave_release", 12).blend(0.3f, 4)
                .key(RIGHT_ARM, 0, -102, -10, 0).key(ROOT, 0, 10, 10, 0).key(ROOT_POS, 0, 0, -2, 3)
                .strike(RIGHT_ARM, 3, -82, 72, 42).strike(BODY, 3, 0, 40, 0).strike(ROOT, 3, 6, -24, 0).strike(ROOT_POS, 3, 0, -2, 1), 12).build());
        // Rush: flat out, the knee, the leap and the hammer down.
        add.accept(AnimDef.builder("rush_run", 4).hold().blend(1, 2)
                .snap(ROOT, 4, 40, 0, 0).snap(ROOT_POS, 4, 0, -3, 2).snap(HEAD, 4, -32, 0, 0).snap(RIGHT_ARM, 4, 72, 0, 12).snap(LEFT_ARM, 4, 72, 0, -12)
                .snap(RIGHT_LEG, 4, -55, 0, 0).snap(LEFT_LEG, 4, 45, 0, 0).build());
        add.accept(stance(AnimDef.builder("rush_knee", 10).blend(0.3f, 3)
                .key(RIGHT_LEG, 0, 30, 0, 0).strike(RIGHT_LEG, 2, -125, 0, 0).settle(RIGHT_LEG, 5, -100, 0, 0)
                .key(ROOT, 0, 20, 0, 0).strike(ROOT, 2, -18, 0, 0).strike(ROOT_POS, 2, 0, 4, 2)
                .strike(RIGHT_ARM, 2, 40, 0, 32).strike(LEFT_ARM, 2, 40, 0, -32), 10).build());
        add.accept(AnimDef.builder("rush_leap", 6).hold().blend(0.5f, 2)
                .snap(ROOT, 6, -18, 0, 0).snap(RIGHT_ARM, 6, -175, 0, 15).snap(LEFT_ARM, 6, -175, 0, -15)
                .snap(RIGHT_LEG, 6, -45, 0, 0).snap(LEFT_LEG, 6, 25, 0, 0).snap(HEAD, 6, -10, 0, 0).build());
        add.accept(stance(AnimDef.builder("rush_slam", 12).blend(0.2f, 4)
                .key(RIGHT_ARM, 0, -175, 0, 15).key(LEFT_ARM, 0, -175, 0, -15).key(ROOT, 0, -18, 0, 0)
                .strike(RIGHT_ARM, 2, -28, 0, 5).strike(LEFT_ARM, 2, -28, 0, -5).strike(ROOT, 2, 40, 0, 0).strike(ROOT_POS, 2, 0, -6, 3)
                .settle(ROOT, 6, 30, 0, 0), 12).build());
    }

    /** A copy of a pose under another name: the air and stomp versions of the Shrine swipes. */
    private static AnimDef copy(AnimDef base, String name) {
        AnimDef.Builder b = AnimDef.builder(name, base.duration).blend(base.blendIn, base.blendOut);
        if (base.hold) b.hold();
        for (Part p : Part.values()) {
            if (!base.animates(p)) continue;
            for (AnimDef.Key k : base.tracks.get(p)) b.key(p, k.time(), k.x(), k.y(), k.z(), k.ease());
        }
        return b.build();
    }
}
