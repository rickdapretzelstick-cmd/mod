package dev.rick.jjk.client.anim;

import java.util.function.Consumer;

import static dev.rick.jjk.client.anim.Part.*;

/**
 * Yuji's and Sukuna's poses, after the Jujutsu Shenanigans wiki GIFs (50 fps: 2.5 frames to a tick). Same conventions as
 * {@link PoseLibrary}: arm X -90 points forward, -180 straight up; right arm +Z away from the body; body Y is the twist.
 */
final class YujiPoses {
    private YujiPoses() {}

    static void register(Consumer<AnimDef> add) {
        // --- Cursed Strikes: poised, hands up (GIF frames 0-22), the slide, the flurry, the calf kick ---
        add.accept(AnimDef.builder("cursed_strikes_ready", 8).hold().blend(1, 2)
                .key(RIGHT_ARM, 0, -30, 0, 10).key(RIGHT_ARM, 8, -110, -30, 10)
                .key(LEFT_ARM, 0, -30, 0, -10).key(LEFT_ARM, 8, -120, 25, -15)
                .key(BODY, 8, 12, 15, 0).key(HEAD, 8, -8, -10, 0)
                .key(RIGHT_LEG, 8, 20, 0, 5).key(LEFT_LEG, 8, -25, 0, -5).build());
        add.accept(AnimDef.builder("cursed_strikes_slide", 4).hold().blend(1, 2)
                .key(BODY, 4, 32, 10, 0).key(HEAD, 4, -25, 0, 0)
                .key(RIGHT_ARM, 4, -100, -35, 20).key(LEFT_ARM, 4, -95, 35, -20)
                .key(RIGHT_LEG, 4, 50, 0, 10).key(LEFT_LEG, 4, -60, 0, -5).build());
        AnimDef.Builder flurry = AnimDef.builder("cursed_strikes_flurry", 30).blend(1, 3);
        for (int i = 0; i <= 6; i++) {
            float t = i * 5;
            boolean r = i % 2 == 0;
            flurry.key(RIGHT_ARM, t, r ? -100 : -40, r ? -10 : 20, r ? 0 : 20).key(RIGHT_ARM, t + 2.5f, r ? -45 : -95, 10, 10)
                    .key(LEFT_ARM, t, r ? -45 : -100, r ? -20 : 10, r ? -20 : 0).key(LEFT_ARM, t + 2.5f, r ? -95 : -45, -10, -10)
                    .key(BODY, t, 5, r ? -25 : 25, 0);
        }
        add.accept(flurry.key(RIGHT_LEG, 0, 15, 0, 0).key(LEFT_LEG, 0, -15, 0, 0).build());
        add.accept(AnimDef.builder("cursed_strikes_kick", 12).blend(0.5f, 4)
                .key(RIGHT_LEG, 0, 10, 0, 0).key(RIGHT_LEG, 2, -30, 0, 55).key(RIGHT_LEG, 4, -60, 0, 25).key(RIGHT_LEG, 12, 0, 0, 0)
                .key(BODY, 0, 0, 0, 0).key(BODY, 3, 5, -30, -15).key(BODY, 12, 0, 0, 0)
                .key(RIGHT_ARM, 3, 20, 0, 50).key(RIGHT_ARM, 12, 0, 0, 5).key(LEFT_ARM, 3, -60, 0, -40).key(LEFT_ARM, 12, 0, 0, -5).build());
        add.accept(AnimDef.builder("cursed_strikes_blocked", 8).blend(0.5f, 3)
                .key(BODY, 0, 20, 0, 0).key(BODY, 8, -5, 0, 0).key(RIGHT_ARM, 0, -95, 0, 10).key(RIGHT_ARM, 8, -20, 0, 20)
                .key(LEFT_ARM, 0, -95, 0, -10).key(LEFT_ARM, 8, -20, 0, -20).build());
        add.accept(AnimDef.builder("cursed_strikes_floor", 10).blend(0.5f, 3)
                .key(BODY, 0, 10, 0, 0).key(BODY, 3, 50, 0, 0).key(BODY, 10, 10, 0, 0)
                .key(RIGHT_ARM, 0, -170, 0, 10).key(RIGHT_ARM, 3, -20, 0, 5).key(RIGHT_ARM, 10, -30, 0, 10)
                .key(RIGHT_LEG, 3, -40, 0, 0).key(LEFT_LEG, 3, 30, 0, 0).build());
        add.accept(AnimDef.builder("cursed_strikes_spin", 14).blend(0.5f, 4)
                .key(BODY, 0, 0, 60, 0).key(BODY, 4, 0, -80, -20).key(BODY, 14, 0, 0, 0)
                .key(RIGHT_LEG, 0, 0, 0, 10).key(RIGHT_LEG, 4, -80, 0, 70).key(RIGHT_LEG, 8, -70, 0, 50).key(RIGHT_LEG, 14, 0, 0, 0)
                .key(RIGHT_ARM, 4, 0, 0, 70).key(LEFT_ARM, 4, 0, 0, -70).key(RIGHT_ARM, 14, 0, 0, 5).key(LEFT_ARM, 14, 0, 0, -5).build());
        // Aerial: the hop tucks, then the dropkick (feet first, body laid back), the landing crouch.
        add.accept(AnimDef.builder("cursed_strikes_hop", 6).hold().blend(1, 2)
                .key(BODY, 6, 20, 0, 0).key(RIGHT_LEG, 6, -80, 0, 5).key(LEFT_LEG, 6, -60, 0, -5)
                .key(RIGHT_ARM, 6, -150, 0, 30).key(LEFT_ARM, 6, -150, 0, -30).build());
        add.accept(AnimDef.builder("cursed_strikes_dropkick", 4).hold().blend(1, 2)
                .key(BODY, 4, -30, 0, 0).key(HEAD, 4, 20, 0, 0)
                .key(RIGHT_LEG, 4, -75, 0, 5).key(LEFT_LEG, 4, -75, 0, -5)
                .key(RIGHT_ARM, 4, 40, 0, 60).key(LEFT_ARM, 4, 40, 0, -60).build());
        add.accept(AnimDef.builder("cursed_strikes_land", 12).blend(0.5f, 5)
                .key(BODY, 0, 35, 0, 0).key(BODY, 12, 0, 0, 0).key(RIGHT_LEG, 0, -70, 0, 10).key(RIGHT_LEG, 12, 0, 0, 0)
                .key(LEFT_LEG, 0, 20, 0, -10).key(LEFT_LEG, 12, 0, 0, 0).key(RIGHT_ARM, 0, -20, 0, 50).key(RIGHT_ARM, 12, 0, 0, 5)
                .key(LEFT_ARM, 0, -20, 0, -50).key(LEFT_ARM, 12, 0, 0, -5).build());

        // --- Crushing Blow: the fist cocked low with cyan energy, the grab, two slams, the release upward ---
        add.accept(AnimDef.builder("crushing_blow_charge", 7).hold().blend(1, 2)
                .key(RIGHT_ARM, 7, 30, 20, 30).key(LEFT_ARM, 7, -80, 20, -15).key(BODY, 7, 15, 30, 0)
                .key(RIGHT_LEG, 7, 20, 0, 5).key(LEFT_LEG, 7, -30, 0, -5).build());
        add.accept(AnimDef.builder("crushing_blow_air", 7).hold().blend(1, 2)
                .key(RIGHT_ARM, 7, 40, 20, 35).key(LEFT_ARM, 7, -100, 20, -15).key(BODY, 7, 20, 30, 0)
                .key(RIGHT_LEG, 7, -60, 0, 5).key(LEFT_LEG, 7, -20, 0, -5).build());
        add.accept(AnimDef.builder("crushing_blow_dash", 4).hold().blend(1, 2)
                .key(RIGHT_ARM, 4, -95, -10, 0).key(LEFT_ARM, 4, 40, 0, -30).key(BODY, 4, 30, -25, 0)
                .key(RIGHT_LEG, 4, 30, 0, 0).key(LEFT_LEG, 4, -50, 0, 0).build());
        AnimDef slam = AnimDef.builder("crushing_blow_slam", 24).blend(0.5f, 3)
                .key(RIGHT_ARM, 0, -95, -10, 0).key(RIGHT_ARM, 3, -30, -10, 5).key(RIGHT_ARM, 9, -140, -10, 0).key(RIGHT_ARM, 13, -30, -10, 5)
                .key(RIGHT_ARM, 19, -90, -10, 0).key(RIGHT_ARM, 24, -40, 0, 10)
                .key(LEFT_ARM, 0, -95, 10, 0).key(LEFT_ARM, 3, -30, 10, -5).key(LEFT_ARM, 9, -140, 10, 0).key(LEFT_ARM, 13, -30, 10, -5)
                .key(LEFT_ARM, 19, -90, 10, 0).key(LEFT_ARM, 24, -40, 0, -10)
                .key(BODY, 0, 10, 0, 0).key(BODY, 3, 45, 0, 0).key(BODY, 9, -10, 0, 0).key(BODY, 13, 45, 0, 0).key(BODY, 19, 5, 0, 0)
                .key(BODY, 24, 0, 0, 0).key(RIGHT_LEG, 3, 25, 0, 5).key(LEFT_LEG, 3, -35, 0, -5).key(RIGHT_LEG, 24, 0, 0, 0).key(LEFT_LEG, 24, 0, 0, 0)
                .build();
        add.accept(slam);
        add.accept(AnimDef.builder("crushing_blow_release", 10).blend(0.5f, 3)
                .key(RIGHT_ARM, 0, -90, -10, 0).key(RIGHT_ARM, 3, -170, -10, 10).key(RIGHT_ARM, 10, -40, 0, 10)
                .key(LEFT_ARM, 0, -90, 10, 0).key(LEFT_ARM, 3, -170, 10, -10).key(LEFT_ARM, 10, -40, 0, -10)
                .key(BODY, 3, -15, 0, 0).key(BODY, 10, 0, 0, 0).build());
        add.accept(AnimDef.builder("crushing_blow_whiff", 12).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, 30, 20, 30).key(RIGHT_ARM, 3, -30, 0, 10).key(RIGHT_ARM, 12, -20, 0, 8)
                .key(BODY, 0, 15, 30, 0).key(BODY, 3, 50, -10, 0).key(BODY, 12, 0, 0, 0)
                .key(RIGHT_LEG, 3, 30, 0, 0).key(LEFT_LEG, 3, -40, 0, 0).key(RIGHT_LEG, 12, 0, 0, 0).key(LEFT_LEG, 12, 0, 0, 0).build());
        add.accept(AnimDef.builder("crushing_blow_suplex", 20).blend(0.5f, 2)
                .key(RIGHT_ARM, 0, -95, -30, 0).key(RIGHT_ARM, 3, -30, -20, 0).key(RIGHT_ARM, 8, -80, -30, 0).key(RIGHT_ARM, 20, -150, -30, 0)
                .key(LEFT_ARM, 0, -95, 30, 0).key(LEFT_ARM, 3, -30, 20, 0).key(LEFT_ARM, 8, -80, 30, 0).key(LEFT_ARM, 20, -150, 30, 0)
                .key(BODY, 0, 10, 0, 0).key(BODY, 3, 45, 0, 0).key(BODY, 8, 0, 0, 0).key(BODY, 20, -25, 0, 0).build());
        add.accept(AnimDef.builder("crushing_blow_suplex_throw", 12).blend(0.3f, 4)
                .key(BODY, 0, -25, 0, 0).key(BODY, 4, -70, 0, 0).key(BODY, 12, 0, 0, 0).key(HEAD, 4, -40, 0, 0).key(HEAD, 12, 0, 0, 0)
                .key(RIGHT_ARM, 0, -150, -30, 0).key(RIGHT_ARM, 4, -190, -20, 0).key(RIGHT_ARM, 12, -30, 0, 8)
                .key(LEFT_ARM, 0, -150, 30, 0).key(LEFT_ARM, 4, -190, 20, 0).key(LEFT_ARM, 12, -30, 0, -8)
                .key(RIGHT_LEG, 4, 25, 0, 0).key(LEFT_LEG, 4, 25, 0, 0).key(RIGHT_LEG, 12, 0, 0, 0).key(LEFT_LEG, 12, 0, 0, 0).build());

        // --- Divergent Fist: the arm wound far back (GIF frame 13), the punch; the Black Flash variants ---
        add.accept(AnimDef.builder("divergent_windup", 9).hold().blend(1, 2)
                .key(RIGHT_ARM, 0, -40, 0, 10).key(RIGHT_ARM, 9, 55, 30, 40).key(LEFT_ARM, 9, -85, 35, -10)
                .key(BODY, 9, 10, 45, 0).key(HEAD, 9, 0, -30, 0).key(RIGHT_LEG, 9, 25, 0, 5).key(LEFT_LEG, 9, -30, 0, -5).build());
        AnimDef punch = AnimDef.builder("divergent_punch", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 55, 30, 40).key(RIGHT_ARM, 2, -100, -10, 0).key(RIGHT_ARM, 8, -95, -5, 0).key(RIGHT_ARM, 14, -30, 0, 8)
                .key(LEFT_ARM, 0, -85, 35, -10).key(LEFT_ARM, 2, 40, 0, -25).key(LEFT_ARM, 14, 0, 0, -8)
                .key(BODY, 0, 10, 45, 0).key(BODY, 2, 15, -40, 0).key(BODY, 14, 0, -5, 0)
                .key(RIGHT_LEG, 2, 30, 0, 0).key(LEFT_LEG, 2, -35, 0, 0).key(RIGHT_LEG, 14, 0, 0, 0).key(LEFT_LEG, 14, 0, 0, 0).build();
        add.accept(punch);
        add.accept(AnimDef.builder("black_flash_punch", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 55, 30, 40).key(RIGHT_ARM, 1.5f, -105, -12, 0).key(RIGHT_ARM, 9, -100, -8, 0).key(RIGHT_ARM, 14, -30, 0, 8)
                .key(LEFT_ARM, 0, -85, 35, -10).key(LEFT_ARM, 1.5f, 50, 0, -30).key(LEFT_ARM, 14, 0, 0, -8)
                .key(BODY, 0, 10, 45, 0).key(BODY, 1.5f, 22, -50, 0).key(BODY, 14, 0, -5, 0).key(HEAD, 1.5f, 10, 20, 0)
                .key(RIGHT_LEG, 1.5f, 40, 0, 0).key(LEFT_LEG, 1.5f, -45, 0, 0).key(RIGHT_LEG, 14, 0, 0, 0).key(LEFT_LEG, 14, 0, 0, 0).build());
        add.accept(AnimDef.builder("black_flash_uppercut", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 40, 20, 30).key(RIGHT_ARM, 2, -170, -10, 0).key(RIGHT_ARM, 9, -160, 0, 0).key(RIGHT_ARM, 14, -40, 0, 8)
                .key(BODY, 0, 25, 30, 0).key(BODY, 2, -15, -20, 0).key(BODY, 14, 0, 0, 0).key(HEAD, 2, -25, 0, 0).key(HEAD, 14, 0, 0, 0)
                .key(LEFT_ARM, 2, 30, 0, -20).key(LEFT_ARM, 14, 0, 0, -8).build());
        add.accept(AnimDef.builder("black_flash_dropkick", 16).blend(0.3f, 5)
                .key(BODY, 0, 10, 0, 0).key(BODY, 3, -45, 0, 0).key(BODY, 16, 0, 0, 0)
                .key(RIGHT_LEG, 0, 0, 0, 0).key(RIGHT_LEG, 3, -90, 0, 5).key(RIGHT_LEG, 16, 0, 0, 0)
                .key(LEFT_LEG, 3, -85, 0, -5).key(LEFT_LEG, 16, 0, 0, 0)
                .key(RIGHT_ARM, 3, 30, 0, 70).key(LEFT_ARM, 3, 30, 0, -70).key(RIGHT_ARM, 16, 0, 0, 5).key(LEFT_ARM, 16, 0, 0, -5).build());

        // --- Manji Kick: arm raised, one knee up (GIF frame 13); the Taido roundhouse upward ---
        add.accept(AnimDef.builder("manji_stance", 3).hold().blend(1, 2)
                .key(RIGHT_ARM, 3, -150, -20, 20).key(LEFT_ARM, 3, -70, 30, -20).key(BODY, 3, -5, 25, 5)
                .key(RIGHT_LEG, 3, -85, 0, 10).key(LEFT_LEG, 3, 5, 0, -5).key(HEAD, 3, 0, -20, 0).build());
        add.accept(AnimDef.builder("manji_recover", 8).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, -150, -20, 20).key(RIGHT_ARM, 8, 0, 0, 5).key(RIGHT_LEG, 0, -85, 0, 10).key(RIGHT_LEG, 8, 0, 0, 0).build());
        add.accept(AnimDef.builder("manji_kick", 14).blend(0.3f, 5)
                .key(RIGHT_LEG, 0, -85, 0, 10).key(RIGHT_LEG, 3, -150, 0, 60).key(RIGHT_LEG, 7, -120, 0, 80).key(RIGHT_LEG, 14, 0, 0, 0)
                .key(BODY, 0, -5, 25, 5).key(BODY, 3, -25, -60, -30).key(BODY, 14, 0, 0, 0)
                .key(RIGHT_ARM, 3, 30, 0, 80).key(LEFT_ARM, 3, 20, 0, -80).key(RIGHT_ARM, 14, 0, 0, 5).key(LEFT_ARM, 14, 0, 0, -5)
                .key(LEFT_LEG, 3, 15, 0, -10).key(LEFT_LEG, 14, 0, 0, 0).build());
        add.accept(AnimDef.builder("manji_dodge", 6).blend(0.2f, 2)
                .key(BODY, 0, 0, 0, 0).key(BODY, 3, 20, 0, 40).key(BODY, 6, 10, 0, 20)
                .key(RIGHT_ARM, 3, -20, 0, 90).key(LEFT_ARM, 3, -20, 0, -90).key(RIGHT_LEG, 3, -30, 0, 40).key(LEFT_LEG, 3, 20, 0, -20).build());
        add.accept(AnimDef.builder("manji_swoop", 8).hold().blend(0.5f, 2)
                .key(BODY, 0, 20, 0, 40).key(BODY, 4, 60, 0, -30).key(BODY, 8, 40, 0, 30)
                .key(RIGHT_LEG, 4, -100, 0, 40).key(LEFT_LEG, 4, -40, 0, -20).key(RIGHT_ARM, 4, 0, 0, 100).key(LEFT_ARM, 4, 0, 0, -100).build());
        add.accept(AnimDef.builder("manji_face_kick", 8).blend(0.3f, 2)
                .key(RIGHT_LEG, 0, -85, 0, 10).key(RIGHT_LEG, 3, -150, 0, 5).key(RIGHT_LEG, 8, -110, 0, 5)
                .key(BODY, 3, -30, 0, 0).key(RIGHT_ARM, 3, 30, 0, 60).key(LEFT_ARM, 3, 30, 0, -60).build());
        add.accept(AnimDef.builder("manji_grapple_spin", 12).blend(0.5f, 2)
                .key(BODY, 0, -30, 0, 0).key(BODY, 4, -60, 0, 40).key(BODY, 8, -40, 0, -40).key(BODY, 12, -30, 0, 0)
                .key(RIGHT_LEG, 0, -110, 0, 30).key(RIGHT_LEG, 12, -110, 0, 30).key(LEFT_LEG, 0, -110, 0, -30).key(LEFT_LEG, 12, -110, 0, -30)
                .key(RIGHT_ARM, 0, 0, 0, 80).key(LEFT_ARM, 0, 0, 0, -80).build());
        add.accept(AnimDef.builder("manji_slam", 14).blend(0.2f, 5)
                .key(BODY, 0, -30, 0, 0).key(BODY, 3, 60, 0, 0).key(BODY, 14, 0, 0, 0)
                .key(RIGHT_LEG, 0, -110, 0, 30).key(RIGHT_LEG, 3, 20, 0, 10).key(RIGHT_LEG, 14, 0, 0, 0)
                .key(LEFT_LEG, 0, -110, 0, -30).key(LEFT_LEG, 3, 20, 0, -10).key(LEFT_LEG, 14, 0, 0, 0)
                .key(RIGHT_ARM, 3, -150, 0, 20).key(LEFT_ARM, 3, -150, 0, -20).key(RIGHT_ARM, 14, 0, 0, 5).key(LEFT_ARM, 14, 0, 0, -5).build());

        // --- Combat Instincts: the white ring (GIF), a quick reset; the punch that sends a prop flying ---
        add.accept(AnimDef.builder("instincts_feint", 8).blend(0.2f, 4)
                .key(RIGHT_ARM, 0, -60, 0, 10).key(RIGHT_ARM, 8, 0, 0, 5).key(LEFT_ARM, 0, -60, 0, -10).key(LEFT_ARM, 8, 0, 0, -5)
                .key(BODY, 0, -8, 0, 0).key(BODY, 8, 0, 0, 0).build());
        add.accept(AnimDef.builder("instincts_throw", 12).blend(0.3f, 4)
                .key(RIGHT_ARM, 0, 40, 20, 30).key(RIGHT_ARM, 2, -95, -8, 0).key(RIGHT_ARM, 12, -30, 0, 8)
                .key(BODY, 0, 10, 35, 0).key(BODY, 2, 10, -35, 0).key(BODY, 12, 0, 0, 0)
                .key(LEFT_ARM, 2, 40, 0, -20).key(LEFT_ARM, 12, 0, 0, -8).build());

        // --- King of Curses: slumping as he faints, then Sukuna rising with a hand over his face (GIF) ---
        add.accept(AnimDef.builder("sukuna_faint", 8).hold().blend(1, 2)
                .key(HEAD, 8, 40, 0, 10).key(BODY, 8, 25, 0, 0)
                .key(RIGHT_ARM, 8, 10, 0, 10).key(LEFT_ARM, 8, 10, 0, -10).key(RIGHT_LEG, 8, -15, 0, 0).key(LEFT_LEG, 8, 10, 0, 0).build());
        add.accept(AnimDef.builder("sukuna_takeover", 36).blend(2, 8)
                .key(HEAD, 0, 40, 0, 10).key(HEAD, 6, -25, 0, 0).key(HEAD, 30, -10, 10, 0).key(HEAD, 36, 0, 0, 0)
                .key(BODY, 0, 25, 0, 0).key(BODY, 6, -12, 0, 0).key(BODY, 36, 0, 0, 0)
                .key(RIGHT_ARM, 0, 10, 0, 10).key(RIGHT_ARM, 6, -80, 0, 70).key(RIGHT_ARM, 10, -150, -45, 0).key(RIGHT_ARM, 30, -150, -45, 0)
                .key(RIGHT_ARM, 36, 0, 0, 5)
                .key(LEFT_ARM, 0, 10, 0, -10).key(LEFT_ARM, 6, -80, 0, -70).key(LEFT_ARM, 10, -10, 0, -20).key(LEFT_ARM, 36, 0, 0, -5).build());

        // --- Shrine M1s: open-handed swipes that throw slashes, far wider than punches (GIF) ---
        AnimDef swipe = AnimDef.builder("shrine_light_1", 9).blend(0.5f, 3)
                .key(RIGHT_ARM, 0, -100, 60, 40).key(RIGHT_ARM, 3, -90, -60, -10).key(RIGHT_ARM, 9, -40, 0, 10)
                .key(BODY, 0, 0, 25, 0).key(BODY, 3, 0, -30, 0).key(BODY, 9, 0, 0, 0)
                .key(LEFT_ARM, 0, -30, 0, -15).key(LEFT_ARM, 9, -20, 0, -10).build();
        add.accept(swipe);
        add.accept(swipe.mirrored("shrine_light_2"));
        add.accept(AnimDef.builder("shrine_light_3", 10).blend(0.5f, 3)
                .key(RIGHT_ARM, 0, -175, -10, 10).key(RIGHT_ARM, 3, -40, -10, 10).key(RIGHT_ARM, 10, -30, 0, 10)
                .key(BODY, 0, -10, 0, 0).key(BODY, 3, 20, 0, 0).key(BODY, 10, 0, 0, 0).build());
        add.accept(AnimDef.builder("shrine_light_4", 14).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, -60, 80, 60).key(RIGHT_ARM, 3, -95, -70, -20).key(RIGHT_ARM, 14, -30, 0, 10)
                .key(LEFT_ARM, 0, -60, -80, -60).key(LEFT_ARM, 3, -95, 70, 20).key(LEFT_ARM, 14, -30, 0, -10)
                .key(BODY, 0, 5, 0, 0).key(BODY, 3, 15, 0, 0).key(BODY, 14, 0, 0, 0).build());
        for (int i = 1; i <= 3; i++) add.accept(copy(i == 2 ? swipe.mirrored("x") : i == 1 ? swipe : null, "shrine_air_" + i, "shrine_light_" + i));
        add.accept(copy(null, "shrine_sprint", "shrine_light_4"));
        add.accept(copy(null, "shrine_stomp", "shrine_light_3"));
        add.accept(AnimDef.builder("shrine_heavy_charge", 6).hold()
                .key(RIGHT_ARM, 6, -110, 80, 50).key(BODY, 6, 0, 35, 0).key(LEFT_ARM, 6, -30, 0, -15).build());
        add.accept(AnimDef.builder("shrine_heavy", 14).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, -110, 80, 50).key(RIGHT_ARM, 3, -90, -70, -15).key(RIGHT_ARM, 14, -30, 0, 10)
                .key(BODY, 0, 0, 35, 0).key(BODY, 3, 5, -40, 0).key(BODY, 14, 0, 0, 0).build());

        // --- Cleave: the grab forward, held; the release ---
        add.accept(AnimDef.builder("cleave_reach", 6).hold().blend(0.5f, 2)
                .key(RIGHT_ARM, 0, 40, 20, 30).key(RIGHT_ARM, 6, -95, -5, 0).key(BODY, 0, 5, 30, 0).key(BODY, 6, 15, -25, 0)
                .key(LEFT_ARM, 6, 20, 0, -20).key(RIGHT_LEG, 6, 25, 0, 0).key(LEFT_LEG, 6, -30, 0, 0).build());
        add.accept(AnimDef.builder("cleave_hold", 4).hold().blend(0.5f, 2)
                .key(RIGHT_ARM, 4, -100, -10, 0).key(BODY, 4, 5, -20, 0).key(HEAD, 4, -10, 0, 0).key(LEFT_ARM, 4, 10, 0, -15).build());
        add.accept(AnimDef.builder("cleave_release", 12).blend(0.3f, 4)
                .key(RIGHT_ARM, 0, -100, -10, 0).key(RIGHT_ARM, 3, -80, 70, 40).key(RIGHT_ARM, 12, -20, 0, 8)
                .key(BODY, 0, 5, -20, 0).key(BODY, 3, 0, 40, 0).key(BODY, 12, 0, 0, 0).build());
        add.accept(AnimDef.builder("cleave_whiff", 10).blend(0.3f, 4)
                .key(RIGHT_ARM, 0, -95, -5, 0).key(RIGHT_ARM, 10, -20, 0, 8).key(BODY, 0, 15, -25, 0).key(BODY, 10, 0, 0, 0).build());

        // --- Dismantle: arm drawn across (GIF frames 12-18), swung out; aerial flip then the downward cut ---
        add.accept(AnimDef.builder("dismantle_windup", 8).hold().blend(1, 2)
                .key(RIGHT_ARM, 8, -110, 70, 60).key(BODY, 8, 0, 35, 0).key(HEAD, 8, 0, -20, 0).key(LEFT_ARM, 8, -20, 0, -15).build());
        add.accept(AnimDef.builder("dismantle_swing", 14).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, -110, 70, 60).key(RIGHT_ARM, 2, -95, -70, -30).key(RIGHT_ARM, 9, -90, -70, -20).key(RIGHT_ARM, 14, -30, 0, 10)
                .key(BODY, 0, 0, 35, 0).key(BODY, 2, 0, -40, 0).key(BODY, 14, 0, 0, 0).build());
        add.accept(AnimDef.builder("dismantle_air", 20).hold().blend(1, 2)
                .key(BODY, 0, 0, 0, 0).key(BODY, 7, -40, 0, 0).key(BODY, 13, 50, 0, 0).key(BODY, 20, 10, 0, 0)
                .key(RIGHT_LEG, 7, -90, 0, 5).key(LEFT_LEG, 7, -90, 0, -5).key(RIGHT_LEG, 20, -20, 0, 5).key(LEFT_LEG, 20, 10, 0, -5)
                .key(RIGHT_ARM, 7, -30, 0, 60).key(RIGHT_ARM, 20, -175, 0, 10).key(LEFT_ARM, 7, -30, 0, -60).key(LEFT_ARM, 20, -40, 0, -20).build());
        add.accept(AnimDef.builder("dismantle_air_swing", 12).blend(0.2f, 4)
                .key(RIGHT_ARM, 0, -175, 0, 10).key(RIGHT_ARM, 2, -20, 0, 10).key(RIGHT_ARM, 12, -20, 0, 10)
                .key(BODY, 0, 10, 0, 0).key(BODY, 2, 40, 0, 0).key(BODY, 12, 0, 0, 0).build());
        // World Cutting Slash: each line of the chant a hand sign (GIF), then the swing across everything.
        add.accept(AnimDef.builder("wcs_chant_1", 4).hold().blend(1, 2)
                .key(RIGHT_ARM, 4, -110, 60, 20).key(LEFT_ARM, 4, -100, -40, -10).key(BODY, 4, 0, 20, 0).key(HEAD, 4, 5, -10, 0).build());
        add.accept(AnimDef.builder("wcs_chant_2", 4).hold().blend(1, 2)
                .key(RIGHT_ARM, 4, -140, 10, 30).key(LEFT_ARM, 4, -95, 20, -20).key(BODY, 4, -5, 10, 0).build());
        add.accept(AnimDef.builder("wcs_chant_3", 4).hold().blend(1, 2)
                .key(RIGHT_ARM, 4, -30, 90, 80).key(LEFT_ARM, 4, -60, 30, -30).key(BODY, 4, 15, 50, 0)
                .key(RIGHT_LEG, 4, 30, 0, 10).key(LEFT_LEG, 4, -35, 0, -10).build());
        add.accept(AnimDef.builder("wcs_swing", 18).blend(0.2f, 6)
                .key(RIGHT_ARM, 0, -30, 90, 80).key(RIGHT_ARM, 2, -90, -80, -40).key(RIGHT_ARM, 12, -90, -80, -30).key(RIGHT_ARM, 18, -30, 0, 10)
                .key(BODY, 0, 15, 50, 0).key(BODY, 2, 20, -55, 0).key(BODY, 18, 0, 0, 0)
                .key(RIGHT_LEG, 2, 35, 0, 10).key(LEFT_LEG, 2, -40, 0, -10).key(RIGHT_LEG, 18, 0, 0, 0).key(LEFT_LEG, 18, 0, 0, 0).build());

        // --- Open: flames in both hands, raised; the clap; drawing the bow; aiming; the release (GIF) ---
        add.accept(AnimDef.builder("open_flames", 7).hold().blend(1, 2)
                .key(RIGHT_ARM, 7, -150, 0, 40).key(LEFT_ARM, 7, -150, 0, -40).key(HEAD, 7, -10, 0, 0).build());
        add.accept(AnimDef.builder("open_clap", 5).hold().blend(0.3f, 2)
                .key(RIGHT_ARM, 0, -150, 0, 40).key(RIGHT_ARM, 5, -95, -40, 0).key(LEFT_ARM, 0, -150, 0, -40).key(LEFT_ARM, 5, -95, 40, 0).build());
        add.accept(AnimDef.builder("open_draw", 10).hold().blend(0.5f, 2)
                .key(LEFT_ARM, 10, -92, 5, 0).key(RIGHT_ARM, 10, -92, 70, 10).key(BODY, 10, 0, 40, 0).key(HEAD, 10, 0, -40, 0)
                .key(RIGHT_LEG, 10, 20, 0, 10).key(LEFT_LEG, 10, -25, 0, -10).build());
        add.accept(AnimDef.builder("open_aim", 8).hold().blend(0.5f, 2)
                .key(LEFT_ARM, 8, -92, 5, 0).key(RIGHT_ARM, 8, -92, 85, 20).key(BODY, 8, 0, 45, 0).key(HEAD, 8, 0, -45, 0)
                .key(RIGHT_LEG, 8, 20, 0, 10).key(LEFT_LEG, 8, -25, 0, -10).build());
        add.accept(AnimDef.builder("open_release", 14).blend(0.2f, 5)
                .key(LEFT_ARM, 0, -92, 5, 0).key(LEFT_ARM, 14, -30, 0, -8).key(RIGHT_ARM, 0, -92, 85, 20).key(RIGHT_ARM, 2, -40, 90, 60)
                .key(RIGHT_ARM, 14, -20, 0, 8).key(BODY, 0, 0, 45, 0).key(BODY, 14, 0, 0, 0).key(HEAD, 0, 0, -45, 0).key(HEAD, 14, 0, 0, 0).build());

        // --- Rush: the sprint, the chase, the knee, the leap and the slam ---
        add.accept(AnimDef.builder("rush_run", 4).hold().blend(1, 2)
                .key(BODY, 4, 40, 0, 0).key(HEAD, 4, -30, 0, 0).key(RIGHT_ARM, 4, 70, 0, 10).key(LEFT_ARM, 4, 70, 0, -10)
                .key(RIGHT_LEG, 4, -50, 0, 0).key(LEFT_LEG, 4, 40, 0, 0).build());
        add.accept(AnimDef.builder("rush_chase", 4).hold().blend(1, 2)
                .key(BODY, 4, 35, 0, 0).key(HEAD, 4, -25, 0, 0).key(RIGHT_ARM, 4, 60, 0, 10).key(LEFT_ARM, 4, -70, 0, -10)
                .key(RIGHT_LEG, 4, 35, 0, 0).key(LEFT_LEG, 4, -45, 0, 0).build());
        add.accept(AnimDef.builder("rush_knee", 10).blend(0.3f, 3)
                .key(RIGHT_LEG, 0, 30, 0, 0).key(RIGHT_LEG, 2, -120, 0, 0).key(RIGHT_LEG, 10, -40, 0, 0)
                .key(BODY, 2, -15, 0, 0).key(RIGHT_ARM, 2, 40, 0, 30).key(LEFT_ARM, 2, 40, 0, -30).key(BODY, 10, 0, 0, 0).build());
        add.accept(AnimDef.builder("rush_leap", 6).hold().blend(0.5f, 2)
                .key(BODY, 6, -15, 0, 0).key(RIGHT_ARM, 6, -170, 0, 15).key(LEFT_ARM, 6, -170, 0, -15)
                .key(RIGHT_LEG, 6, -40, 0, 0).key(LEFT_LEG, 6, 20, 0, 0).build());
        add.accept(AnimDef.builder("rush_slam", 12).blend(0.2f, 4)
                .key(RIGHT_ARM, 0, -170, 0, 15).key(RIGHT_ARM, 2, -30, 0, 5).key(RIGHT_ARM, 12, -20, 0, 5)
                .key(LEFT_ARM, 0, -170, 0, -15).key(LEFT_ARM, 2, -30, 0, -5).key(LEFT_ARM, 12, -20, 0, -5)
                .key(BODY, 0, -15, 0, 0).key(BODY, 2, 35, 0, 0).key(BODY, 12, 0, 0, 0).build());

        // --- Malevolent Shrine: Sukuna's hand sign (the Enma-ten seal) ---
        add.accept(AnimDef.builder("shrine_sign", 8).hold().blend(1, 2)
                .key(RIGHT_ARM, 8, -80, -35, 0).key(LEFT_ARM, 8, -80, 35, 0).key(HEAD, 8, 8, 0, 0).key(BODY, 8, 3, 0, 0).build());
    }

    /** A copy of a registered pose under another name (the Shrine aerial and sprint variants reuse the ground swipes). */
    private static AnimDef copy(AnimDef src, String name, String fallback) {
        AnimDef base = src != null ? src : PoseLibrary.get(fallback);
        AnimDef.Builder b = AnimDef.builder(name, base.duration);
        if (base.hold) b.hold();
        b.blend(base.blendIn, base.blendOut);
        for (Part p : Part.values()) {
            if (!base.animates(p)) continue;
            for (AnimDef.Key k : base.tracks.get(p)) b.key(p, k.time(), k.x(), k.y(), k.z());
        }
        return b.build();
    }
}
