package dev.rick.jjk.client.anim;

import java.util.function.Consumer;

import static dev.rick.jjk.client.anim.Part.*;

/**
 * The fighting animations, animated like JJS moves rather than posed: every strike has an anticipation (the body coils
 * and dips), a whip into contact (the {@code strike} ease, the whole body turning and lunging behind the blow through
 * {@link Part#ROOT} and {@link Part#ROOT_POS}), a follow-through past the target and a settle back into a fighting
 * stance. Timings match the server: the key a blow lands on is the tick its hit resolves. Registered after the older
 * poses, so anything defined here replaces them.
 *
 * <p>Angles, as in {@link PoseLibrary}: arm X -90 points forward, -180 straight up; right arm +Z swings out from the
 * body; negative arm Y turns a forward arm in across the chest. Body Y twists the torso (negative brings the right
 * shoulder forward) and the arms ride it. ROOT Y turns the whole body (positive to the left, which also brings the
 * right shoulder forward), ROOT X leans it forward, ROOT Z leans it right. ROOT_POS moves it, in pixels.
 */
final class Moves {
    private Moves() {}

    static void register(Consumer<AnimDef> add) {
        common(add);
        gojo(add);
        hakari(add);
    }

    // --- building blocks ---

    /** The fighting stance a move settles back into: fists up, lead hand forward, knees soft, weight low. */
    static AnimDef.Builder stance(AnimDef.Builder b, float t) {
        return b.settle(RIGHT_ARM, t, -62, -22, 12).settle(LEFT_ARM, t, -78, 22, -10).settle(BODY, t, 0, 12, 0)
                .settle(ROOT, t, 4, 0, 0).settle(ROOT_POS, t, 0, -1, 0).settle(RIGHT_LEG, t, 10, 0, 4).settle(LEFT_LEG, t, -12, 0, -4)
                .settle(HEAD, t, 4, 0, 0);
    }

    /** Back to standing, so the blend out to walking is invisible. */
    static AnimDef.Builder rest(AnimDef.Builder b, float t) {
        return b.settle(RIGHT_ARM, t, -8, 0, 6).settle(LEFT_ARM, t, -8, 0, -6).settle(BODY, t, 0, 0, 0).settle(ROOT, t, 0, 0, 0)
                .settle(ROOT_POS, t, 0, 0, 0).settle(RIGHT_LEG, t, 0, 0, 0).settle(LEFT_LEG, t, 0, 0, 0).settle(HEAD, t, 0, 0, 0);
    }

    /**
     * A straight right thrown with the whole body: chambered at {@code from}, landing at {@code hit}, recoiling by
     * {@code hit + 2}. {@code power} scales the coil, the lunge and the lean (1 for a jab, 2 for a heavy).
     */
    static AnimDef.Builder straight(AnimDef.Builder b, float from, float hit, float power, float height) {
        float lunge = 2 + 2.5f * power, coil = 12 * power;
        return b.key(RIGHT_ARM, from, -45 + 50 * (power - 1), -5, 22).strike(RIGHT_ARM, hit, -98 + height, -10, 0).settle(RIGHT_ARM, hit + 2, -92 + height, -6, 2)
                .key(LEFT_ARM, from, -85, 25, -10).strike(LEFT_ARM, hit, -35, 10, -18)
                .key(BODY, from, 0, coil, 0).strike(BODY, hit, 0, -18 - 8 * power, 0)
                .key(ROOT, from, 2, -coil * 0.7f, 0).strike(ROOT, hit, 6 + 5 * power, 10 * power, 0)
                .key(ROOT_POS, from, 0, -1 - power * 0.6f, -1).strike(ROOT_POS, hit, 0, -1 - power, lunge).settle(ROOT_POS, hit + 2, 0, -1 - power, lunge * 0.8f)
                .key(RIGHT_LEG, from, 8, 0, 3).strike(RIGHT_LEG, hit, 18 + 10 * power, 0, 3)
                .key(LEFT_LEG, from, -10, 0, -3).strike(LEFT_LEG, hit, -20 - 8 * power, 0, -3)
                .key(HEAD, from, 6, 0, 0).strike(HEAD, hit, 8, 0, 0);
    }

    /** A hook: the arm swings round from out wide, the torso and hips wrenching through. */
    static AnimDef.Builder hook(AnimDef.Builder b, float from, float hit, float power) {
        return b.key(RIGHT_ARM, from, -75, 55, 70).strike(RIGHT_ARM, hit, -92, -45, 8).settle(RIGHT_ARM, hit + 3, -80, -60, 5)
                .key(LEFT_ARM, from, -80, 25, -12).strike(LEFT_ARM, hit, -40, 15, -25)
                .key(BODY, from, 0, 30 * power, 0).strike(BODY, hit, 0, -38 * power, 0)
                .key(ROOT, from, 3, -18 * power, 4).strike(ROOT, hit, 10, 22 * power, -4)
                .key(ROOT_POS, from, 0, -2, 0).strike(ROOT_POS, hit, 0, -2, 3 * power)
                .key(RIGHT_LEG, from, 10, 0, 5).strike(RIGHT_LEG, hit, 25, 0, 5).key(LEFT_LEG, from, -10, 0, -5).strike(LEFT_LEG, hit, -22, 0, -5);
    }

    /** Legs tucked up under him and no stepping: the same strike thrown in the air. */
    private static AnimDef.Builder tucked(AnimDef.Builder b, float end) {
        return b.key(RIGHT_LEG, 0, -55, 0, 6).key(RIGHT_LEG, end, -55, 0, 6).key(LEFT_LEG, 0, -20, 0, -6).key(LEFT_LEG, end, -20, 0, -6)
                .key(ROOT_POS, end, 0, 0, 0).key(ROOT_POS, 0, 0, 0, 0);
    }

    // --- everyone ---

    private static void common(Consumer<AnimDef> add) {
        // M1 chain (JJS): jab, jab, hook, then a heavy straight that knocks them back.
        AnimDef jab = stance(straight(AnimDef.builder("light_1", 9).blend(1, 3), 0, 2, 1, 0), 9).build();
        add.accept(jab);
        add.accept(jab.mirrored("light_2"));
        add.accept(stance(hook(AnimDef.builder("light_3", 10).blend(1, 3), 0, 3, 1), 10).build());
        AnimDef.Builder fin = AnimDef.builder("light_4", 14).blend(0.5f, 4)
                // A deep coil (the right shoulder all the way back), then everything goes through the punch.
                .key(RIGHT_ARM, 0, 40, 10, 28).key(LEFT_ARM, 0, -95, 30, -10).key(BODY, 0, 5, 35, 0).key(ROOT, 0, 0, -28, 0)
                .key(ROOT_POS, 0, 0, -3, -2).key(RIGHT_LEG, 0, 18, 0, 5).key(LEFT_LEG, 0, -18, 0, -5)
                .strike(RIGHT_ARM, 3, -108, -8, 0).strike(LEFT_ARM, 3, 30, 5, -25).strike(BODY, 3, 0, -40, 0).strike(ROOT, 3, 16, 24, 0)
                .strike(ROOT_POS, 3, 0, -3, 7).strike(RIGHT_LEG, 3, 38, 0, 5).strike(LEFT_LEG, 3, -35, 0, -5)
                .settle(RIGHT_ARM, 7, -100, -4, 2).settle(ROOT_POS, 7, 0, -2.5f, 6).settle(ROOT, 7, 12, 20, 0);
        add.accept(stance(fin, 14).build());
        add.accept(stance(AnimDef.builder("uppercut", 13).blend(0.5f, 4)
                // Dropped low, then rising up through the chin onto his toes.
                .key(RIGHT_ARM, 0, 25, 0, 18).key(LEFT_ARM, 0, -70, 20, -12).key(BODY, 0, 0, 20, 0).key(ROOT, 0, 16, -15, 0).key(ROOT_POS, 0, 0, -5, 0)
                .key(RIGHT_LEG, 0, -25, 0, 3).key(LEFT_LEG, 0, 15, 0, -3)
                .strike(RIGHT_ARM, 3, -172, -12, -6).strike(LEFT_ARM, 3, -20, 0, -30).strike(BODY, 3, 0, -22, 0).strike(ROOT, 3, -14, 14, 0)
                .strike(ROOT_POS, 3, 0, 2, 2).strike(RIGHT_LEG, 3, 10, 0, 3).strike(LEFT_LEG, 3, -10, 0, -3).strike(HEAD, 3, -22, 0, 0)
                .settle(RIGHT_ARM, 7, -160, -5, 0).settle(ROOT, 7, -10, 10, 0), 13).build());
        add.accept(stance(AnimDef.builder("downslam", 13).blend(0.5f, 4)
                // Both fists raised over the head, the body arched back, then hammered down.
                .key(RIGHT_ARM, 0, -178, -10, 12).key(LEFT_ARM, 0, -178, 10, -12).key(ROOT, 0, -14, 0, 0).key(ROOT_POS, 0, 0, 2, 0).key(HEAD, 0, -15, 0, 0)
                .key(RIGHT_LEG, 0, -30, 0, 0).key(LEFT_LEG, 0, 10, 0, 0)
                .strike(RIGHT_ARM, 3, -35, -18, 5).strike(LEFT_ARM, 3, -35, 18, -5).strike(ROOT, 3, 32, 0, 0).strike(ROOT_POS, 3, 0, -4, 3)
                .strike(HEAD, 3, 20, 0, 0).strike(RIGHT_LEG, 3, -35, 0, 0).strike(LEFT_LEG, 3, 20, 0, 0)
                .settle(ROOT, 7, 26, 0, 0), 13).build());
        // In the air: the same strikes with the legs tucked under.
        AnimDef air = tucked(straight(AnimDef.builder("air_1", 9).blend(1, 3), 0, 2, 1, 0), 9).build();
        add.accept(air);
        add.accept(air.mirrored("air_2"));
        add.accept(tucked(hook(AnimDef.builder("air_3", 10).blend(1, 3), 0, 3, 1), 10).build());
        // Heavy (M1 held): a coil held as long as the button is, then the release.
        add.accept(AnimDef.builder("heavy_charge", 7).hold().blend(1.5f, 3)
                .snap(RIGHT_ARM, 7, 55, 25, 38).snap(LEFT_ARM, 7, -85, 32, -8).snap(BODY, 7, 5, 38, 0).snap(ROOT, 7, 6, -34, 0)
                .snap(ROOT_POS, 7, 0, -3.5f, -1.5f).snap(RIGHT_LEG, 7, 28, 0, 6).snap(LEFT_LEG, 7, -26, 0, -6).snap(HEAD, 7, 6, 0, 0).build());
        add.accept(stance(AnimDef.builder("heavy", 16).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, 55, 25, 38).key(LEFT_ARM, 0, -85, 32, -8).key(BODY, 0, 5, 38, 0).key(ROOT, 0, 6, -34, 0).key(ROOT_POS, 0, 0, -3.5f, -1.5f)
                .key(RIGHT_LEG, 0, 28, 0, 6).key(LEFT_LEG, 0, -26, 0, -6)
                .strike(RIGHT_ARM, 3, -104, -12, 0).strike(LEFT_ARM, 3, 38, 0, -22).strike(BODY, 3, 8, -42, 0).strike(ROOT, 3, 18, 28, 0)
                .strike(ROOT_POS, 3, 0, -3.5f, 9).strike(RIGHT_LEG, 3, 42, 0, 6).strike(LEFT_LEG, 3, -38, 0, -6)
                .settle(RIGHT_ARM, 9, -98, -6, 2).settle(ROOT_POS, 9, 0, -3, 8).settle(ROOT, 9, 14, 24, 0), 16).build());
        // Block (JJS): forearms crossed in front of the face, chin tucked, sunk into the knees.
        add.accept(AnimDef.builder("guard", 3).hold().blend(1, 2)
                .snap(RIGHT_ARM, 3, -118, -52, 8).snap(LEFT_ARM, 3, -108, 50, -8).snap(BODY, 3, 0, 6, 0).snap(ROOT, 3, 10, 0, 0)
                .snap(ROOT_POS, 3, 0, -2.5f, -0.5f).snap(RIGHT_LEG, 3, 16, 0, 5).snap(LEFT_LEG, 3, -18, 0, -5).snap(HEAD, 3, 14, 0, 0).build());
        // Gojo doesn't need to block (JJS Infinity): he floats at ease, one hand raised idly by his head, a knee up.
        add.accept(AnimDef.builder("guard_gojo", 5).hold().blend(2, 3)
                .snap(RIGHT_ARM, 5, -150, -20, -30).snap(LEFT_ARM, 5, -6, 0, -10).snap(BODY, 5, 0, -8, 0).snap(ROOT, 5, -4, 0, 3)
                .snap(ROOT_POS, 5, 0, 3, 0).snap(RIGHT_LEG, 5, -35, 0, 4).snap(LEFT_LEG, 5, 4, 0, -2).snap(HEAD, 5, -4, 0, 5).build());
        // Dashes: thrown into the direction, low and leaning, arms trailing, then caught and back upright.
        add.accept(rest(AnimDef.builder("dash_forward", 9).blend(0.5f, 3)
                .snap(ROOT, 2, 34, 0, 0).snap(ROOT_POS, 2, 0, -3, 3).snap(RIGHT_ARM, 2, 65, 0, 18).snap(LEFT_ARM, 2, 65, 0, -18)
                .snap(RIGHT_LEG, 2, 45, 0, 0).snap(LEFT_LEG, 2, -50, 0, 0).snap(HEAD, 2, -25, 0, 0).snap(BODY, 2, 0, 0, 0)
                .key(ROOT, 6, 26, 0, 0).key(ROOT_POS, 6, 0, -2, 2), 9).build());
        add.accept(rest(AnimDef.builder("dash_back", 9).blend(0.5f, 3)
                .snap(ROOT, 2, -20, 0, 0).snap(ROOT_POS, 2, 0, 2, -2).snap(RIGHT_ARM, 2, -55, 0, 35).snap(LEFT_ARM, 2, -55, 0, -35)
                .snap(RIGHT_LEG, 2, -40, 0, 4).snap(LEFT_LEG, 2, 15, 0, -4).snap(HEAD, 2, 10, 0, 0).snap(BODY, 2, 0, 0, 0)
                .key(ROOT, 6, -12, 0, 0).key(ROOT_POS, 6, 0, 0, -1), 9).build());
        AnimDef side = rest(AnimDef.builder("dash_left", 9).blend(0.5f, 3)
                // Pushing off the right foot: the body leans hard into the dash, legs split, the outside arm flung up.
                .snap(ROOT, 2, 8, 20, -28).snap(ROOT_POS, 2, -2, -2, 0).snap(RIGHT_ARM, 2, -20, 0, 70).snap(LEFT_ARM, 2, -30, 0, -15)
                .snap(RIGHT_LEG, 2, 0, 0, 30).snap(LEFT_LEG, 2, -20, 0, -10).snap(HEAD, 2, 0, 10, 12).snap(BODY, 2, 0, 0, 0)
                .key(ROOT, 6, 5, 12, -18).key(ROOT_POS, 6, -1, -1, 0), 9).build();
        add.accept(side);
        add.accept(side.mirrored("dash_right"));
    }

    // --- Gojo (JJS Honored One GIFs) ---

    private static void gojo(Consumer<AnimDef> add) {
        // Lapse Blue: the right hand thrust out, fingers spread at the target, the body leaning away against the pull.
        add.accept(rest(AnimDef.builder("blue_cast", 20).blend(1.5f, 5)
                .key(RIGHT_ARM, 0, -40, 0, 20).snap(RIGHT_ARM, 4, -100, -14, 0).key(RIGHT_ARM, 16, -96, -10, 0)
                .key(LEFT_ARM, 0, 0, 0, -5).snap(LEFT_ARM, 4, 15, 0, -20)
                .snap(BODY, 4, 0, -16, 0).snap(ROOT, 4, -6, 14, 0).snap(ROOT_POS, 4, 0, -1, -1).key(ROOT_POS, 16, 0, -1, -1.5f)
                .snap(RIGHT_LEG, 4, -14, 0, 3).snap(LEFT_LEG, 4, 16, 0, -3).snap(HEAD, 4, 4, 0, 0), 20).build());
        add.accept(rest(AnimDef.builder("blue_kick", 12).blend(0.5f, 4)
                // The target held in front of him: he steps in and snaps a front kick through it.
                .key(RIGHT_LEG, 0, 10, 0, 0).key(RIGHT_LEG, 5, 30, 0, 0).strike(RIGHT_LEG, 8, -100, 0, 0).settle(RIGHT_LEG, 10, -80, 0, 0)
                .key(ROOT, 0, 0, 8, 0).key(ROOT, 5, 6, 10, 0).strike(ROOT, 8, -18, -6, 0).key(ROOT_POS, 5, 0, -2, 0).strike(ROOT_POS, 8, 0, 1, 3)
                .key(RIGHT_ARM, 5, -60, 0, 25).strike(RIGHT_ARM, 8, 25, 0, 45).key(LEFT_ARM, 5, -80, 0, -10).strike(LEFT_ARM, 8, -30, 0, -55)
                .key(LEFT_LEG, 5, -10, 0, 0).strike(LEFT_LEG, 8, 12, 0, 0), 12).build());
        // Reversal Red: the right arm levelled at the target, the left hand bracing the wrist, feet wide; the release
        // throws his arm up and rocks him back on his heels.
        add.accept(AnimDef.builder("red_charge", 6).hold().blend(1.5f, 3)
                .snap(RIGHT_ARM, 6, -92, -8, 0).snap(LEFT_ARM, 6, -80, 44, 0).snap(BODY, 6, 0, -14, 0).snap(ROOT, 6, 6, 16, 0)
                .snap(ROOT_POS, 6, 0, -2.5f, 0).snap(RIGHT_LEG, 6, 20, 0, 6).snap(LEFT_LEG, 6, -22, 0, -6).snap(HEAD, 6, 4, 0, 0).build());
        add.accept(rest(AnimDef.builder("red_release", 12).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, -92, -8, 0).key(LEFT_ARM, 0, -80, 44, 0).key(ROOT, 0, 6, 16, 0).key(ROOT_POS, 0, 0, -2.5f, 0)
                .settle(RIGHT_ARM, 2, -140, -8, 12).settle(LEFT_ARM, 2, -40, 20, -25).settle(ROOT, 2, -12, 10, 0).settle(ROOT_POS, 2, 0, -1, -3)
                .settle(HEAD, 2, -10, 0, 0).key(RIGHT_LEG, 2, 8, 0, 6).key(LEFT_LEG, 2, -8, 0, -6), 12).build());
        // Limitless (JJS): the right hand comes up by his face, two fingers raised, and holds until the glass breaks.
        add.accept(AnimDef.builder("limitless_raise", 12).blend(1, 3)
                .key(RIGHT_ARM, 0, -20, 0, 10).snap(RIGHT_ARM, 5, -148, -14, -16).key(RIGHT_ARM, 12, -156, -14, -18)
                .snap(BODY, 5, 0, -12, 0).snap(ROOT, 5, -3, 8, 0).snap(HEAD, 5, -6, 0, 0).snap(ROOT_POS, 5, 0, 0.5f, 0)
                .snap(LEFT_ARM, 5, -5, 0, -8).key(RIGHT_LEG, 5, 6, 0, 2).key(LEFT_LEG, 5, -6, 0, -2).build());
        add.accept(rest(AnimDef.builder("limitless_air_kick", 10).blend(0.5f, 4)
                // Over them in the air: the heel comes down on them.
                .key(RIGHT_LEG, 0, -150, 0, 0).strike(RIGHT_LEG, 3, 30, 0, 0).key(ROOT, 0, -25, 0, 0).strike(ROOT, 3, 30, 0, 0)
                .key(RIGHT_ARM, 0, -60, 0, 40).strike(RIGHT_ARM, 3, -160, 0, 35).key(LEFT_ARM, 0, -60, 0, -40).strike(LEFT_ARM, 3, -160, 0, -35)
                .key(LEFT_LEG, 0, 20, 0, 0).strike(LEFT_LEG, 3, -30, 0, 0), 10).build());
        add.accept(rest(AnimDef.builder("red_upside_down", 14).blend(0.5f, 4)
                // Behind them, upside down in the air, a hand at point blank.
                .snap(ROOT, 4, 180, 0, 0).key(ROOT, 11, 180, 0, 0).settle(ROOT, 14, 360, 0, 0)
                .snap(ROOT_POS, 4, 0, 26, 0).key(ROOT_POS, 11, 0, 26, 0).settle(ROOT_POS, 14, 0, 0, 0)
                .snap(RIGHT_ARM, 4, -92, -6, 0).key(RIGHT_ARM, 11, -92, -6, 0).snap(LEFT_ARM, 4, 20, 0, -35)
                .snap(RIGHT_LEG, 4, -10, 0, 14).snap(LEFT_LEG, 4, 10, 0, -14), 14).build());
        // Rapid Punches (JJS GIF): a full spinning roundhouse that locks them...
        add.accept(stance(AnimDef.builder("spin_kick", 13).blend(0.5f, 3)
                .key(ROOT, 0, 0, 0, 0).key(ROOT, 2, 4, -25, 0).spin(ROOT, 7, 0, 180, -14).spin(ROOT, 11, 0, 360, -8).settle(ROOT, 13, 4, 360, 0)
                .key(ROOT_POS, 0, 0, 0, 0).key(ROOT_POS, 2, 0, -3, 0).key(ROOT_POS, 7, 0, 1, 1).settle(ROOT_POS, 13, 0, -1, 2)
                .key(RIGHT_LEG, 2, 10, 0, 4).key(RIGHT_LEG, 4, -60, 0, 50).key(RIGHT_LEG, 7, -85, 0, 80).key(RIGHT_LEG, 10, -85, 0, 80).settle(RIGHT_LEG, 13, 10, 0, 4)
                .key(LEFT_LEG, 2, -10, 0, 0).key(LEFT_LEG, 7, 8, 0, -8)
                .key(RIGHT_ARM, 2, -40, 0, 30).key(RIGHT_ARM, 5, -30, 0, 85).key(RIGHT_ARM, 10, -30, 0, 80)
                .key(LEFT_ARM, 2, -60, 0, -20).key(LEFT_ARM, 5, -30, 0, -85).key(LEFT_ARM, 10, -30, 0, -80)
                .key(HEAD, 2, 5, 0, 0).key(HEAD, 7, 0, 0, 10), 13).build());
        // ...then 15 punches, one landing every 2 ticks (the first on the 2nd): he's right on top of them, leaning in,
        // the fists hammering from shoulder and hip at different heights, torso snapping side to side with each.
        AnimDef.Builder barrage = AnimDef.builder("rapid_barrage", 32).blend(1, 2)
                .key(ROOT, 0, 12, 0, 0).key(ROOT_POS, 0, 0, -2.5f, 1.5f).key(RIGHT_LEG, 0, 22, 0, 6).key(LEFT_LEG, 0, -22, 0, -6)
                .key(HEAD, 0, 10, 0, 0).key(RIGHT_ARM, 0, -60, -20, 14).key(LEFT_ARM, 0, -60, 20, -14);
        float[] heights = {0, -10, 6, -4, 10, -8, 2, -12, 8, -2, 4, -10, 0, 6, -6, 0};
        for (int n = 0; n < 16; n++) {
            float hit = 2 + n * 2, back = hit - 1;
            boolean right = n % 2 == 0;
            float h = heights[n];
            Part punch = right ? RIGHT_ARM : LEFT_ARM, guard = right ? LEFT_ARM : RIGHT_ARM;
            float s = right ? 1 : -1;
            barrage.key(punch, back, -62, -22 * s, 18 * s).strike(punch, hit, -100 + h, -14 * s, 0)
                    .key(guard, hit, -58, 24 * s, -16 * s)
                    .strike(BODY, hit, 0, -24 * s, 0).strike(ROOT, hit, 13, 8 * s, -2 * s).strike(ROOT_POS, hit, 0.6f * s, -2.5f, 2 + (n % 3) * 0.6f);
        }
        add.accept(barrage.build());
        // ...3 heavy punches, each wound up from the shoulder, right-left-right (landing on the 4th, 9th and 14th ticks),
        // and a last, deeper coil held for the final blow.
        AnimDef.Builder heavy = AnimDef.builder("rapid_heavy", 21).blend(0.5f, 2)
                .key(RIGHT_LEG, 0, 24, 0, 6).key(LEFT_LEG, 0, -24, 0, -6).key(HEAD, 0, 8, 0, 0);
        for (int k = 0; k < 3; k++) {
            float hit = 4 + k * 5, wind = hit - 3;
            boolean right = k % 2 == 0;
            float s = right ? 1 : -1;
            Part punch = right ? RIGHT_ARM : LEFT_ARM, other = right ? LEFT_ARM : RIGHT_ARM;
            heavy.key(punch, wind, 45, 20 * s, 30 * s).strike(punch, hit, -106, -10 * s, 0).settle(punch, hit + 1.5f, -96, -8 * s, 2 * s)
                    .key(other, wind, -85, 30 * s, -10 * s).strike(other, hit, 30, 0, -20 * s)
                    .key(BODY, wind, 4, 36 * s, 0).strike(BODY, hit, 6, -40 * s, 0)
                    .key(ROOT, wind, 6, -30 * s, 0).strike(ROOT, hit, 18, 22 * s, 0)
                    .key(ROOT_POS, wind, 0, -3.5f, -1).strike(ROOT_POS, hit, 0, -3, 6);
        }
        heavy.key(RIGHT_ARM, 18, 58, 25, 40).key(LEFT_ARM, 18, -95, 35, -8).key(BODY, 18, 5, 42, 0).key(ROOT, 18, 8, -40, 0)
                .key(ROOT_POS, 18, 0, -4, -2).key(RIGHT_LEG, 18, 34, 0, 7).key(LEFT_LEG, 18, -30, 0, -7)
                .key(RIGHT_ARM, 21, 62, 25, 42).key(ROOT, 21, 8, -44, 0);
        add.accept(heavy.build());
        // The final blow: everything unloads through one straight that sends them flying.
        add.accept(stance(AnimDef.builder("rapid_final", 16).blend(0.3f, 5)
                .key(RIGHT_ARM, 0, 62, 25, 42).key(LEFT_ARM, 0, -95, 35, -8).key(BODY, 0, 5, 42, 0).key(ROOT, 0, 8, -44, 0)
                .key(ROOT_POS, 0, 0, -4, -2).key(RIGHT_LEG, 0, 34, 0, 7).key(LEFT_LEG, 0, -30, 0, -7)
                .strike(RIGHT_ARM, 2, -110, -8, 0).strike(LEFT_ARM, 2, 40, 0, -28).strike(BODY, 2, 8, -46, 0).strike(ROOT, 2, 22, 30, 0)
                .strike(ROOT_POS, 2, 0, -4, 11).strike(RIGHT_LEG, 2, 48, 0, 7).strike(LEFT_LEG, 2, -42, 0, -7).strike(HEAD, 2, 12, 0, 0)
                .settle(RIGHT_ARM, 8, -104, -4, 2).settle(ROOT_POS, 8, 0, -3.5f, 10).settle(ROOT, 8, 18, 26, 0), 16).build());
        // Twofold Kick (JJS GIF): low, then the leg whips straight up through them (contact on the 8th tick)...
        add.accept(AnimDef.builder("twofold_1", 14).blend(0.5f, 3)
                .key(ROOT, 0, 8, 10, 0).key(ROOT, 5, 16, 18, 0).strike(ROOT, 8, -24, -8, 0).settle(ROOT, 14, -10, 0, 0)
                .key(ROOT_POS, 0, 0, -1, 0).key(ROOT_POS, 5, 0, -4, -1).strike(ROOT_POS, 8, 0, 2, 2).settle(ROOT_POS, 14, 0, 0, 1)
                .key(RIGHT_LEG, 0, 20, 0, 0).key(RIGHT_LEG, 5, 35, 0, 2).strike(RIGHT_LEG, 8, -170, 0, 0).settle(RIGHT_LEG, 14, -70, 0, 0)
                .key(LEFT_LEG, 5, -20, 0, 0).strike(LEFT_LEG, 8, 15, 0, 0)
                .key(RIGHT_ARM, 5, 25, 0, 25).strike(RIGHT_ARM, 8, 35, 0, 50).key(LEFT_ARM, 5, -60, 0, -20).strike(LEFT_ARM, 8, 35, 0, -50)
                .strike(HEAD, 8, -18, 0, 0).build());
        // ...then spins on into a second kick that bounces them higher.
        add.accept(rest(AnimDef.builder("twofold_2", 12).blend(0.3f, 4)
                .key(ROOT, 0, -10, -150, 0).spin(ROOT, 2, -18, 0, 10).settle(ROOT, 6, -10, 50, 0)
                .key(ROOT_POS, 0, 0, 2, 1).strike(ROOT_POS, 2, 0, 3, 2)
                .key(LEFT_LEG, 0, -110, 0, 0).strike(LEFT_LEG, 2, -178, 0, -10).settle(LEFT_LEG, 7, -60, 0, 0)
                .key(RIGHT_LEG, 0, 20, 0, 0).strike(RIGHT_LEG, 2, 25, 0, 0)
                .strike(RIGHT_ARM, 2, 40, 0, 65).strike(LEFT_ARM, 2, 40, 0, -65).strike(HEAD, 2, -15, 0, 0), 12).build());
        // Black Flash off Red MAX's rebound: wound all the way back, then the punch as the target is pulled in.
        add.accept(stance(AnimDef.builder("black_flash", 16).blend(0.5f, 6)
                .key(RIGHT_ARM, 0, 55, 22, 36).key(BODY, 0, 6, 45, 0).key(ROOT, 0, 6, -38, 0).key(ROOT_POS, 0, 0, -4, -2)
                .key(LEFT_ARM, 0, -95, 32, -8).key(RIGHT_LEG, 0, 30, 0, 6).key(LEFT_LEG, 0, -28, 0, -6)
                .strike(RIGHT_ARM, 5, -108, -6, 0).strike(BODY, 5, 10, -46, 0).strike(ROOT, 5, 20, 28, 0).strike(ROOT_POS, 5, 0, -4, 9)
                .strike(LEFT_ARM, 5, 40, 0, -28).strike(RIGHT_LEG, 5, 45, 0, 6).strike(LEFT_LEG, 5, -40, 0, -6)
                .settle(ROOT_POS, 10, 0, -3, 8), 16).build());
        // Teleport strike: arriving mid-swing.
        add.accept(stance(hook(AnimDef.builder("teleport_strike", 10).blend(0.3f, 3), 0, 2, 1.2f), 10).build());
        // Hollow Purple (JJS GIF): blue in the left hand held out wide, red in the right, then drawn together in front
        // of him, the mass held, and thrust forward with his whole weight behind it.
        add.accept(AnimDef.builder("purple_blue", 10).hold().blend(2, 3)
                .snap(LEFT_ARM, 10, -88, 22, -50).snap(RIGHT_ARM, 10, -10, 0, 12).snap(BODY, 10, 0, 12, 0).snap(ROOT, 10, 0, -8, 0)
                .snap(ROOT_POS, 10, 0, -1.5f, 0).snap(RIGHT_LEG, 10, 12, 0, 6).snap(LEFT_LEG, 10, -12, 0, -6).snap(HEAD, 10, 2, 0, 0).build());
        add.accept(AnimDef.builder("purple_red", 10).hold().blend(0.5f, 3)
                .key(LEFT_ARM, 0, -88, 22, -50).snap(LEFT_ARM, 10, -88, 22, -55).key(RIGHT_ARM, 0, -10, 0, 12).snap(RIGHT_ARM, 10, -88, -22, 55)
                .snap(BODY, 10, 0, 0, 0).snap(ROOT, 10, 2, 0, 0).snap(ROOT_POS, 10, 0, -2.5f, 0)
                .snap(RIGHT_LEG, 10, 14, 0, 8).snap(LEFT_LEG, 10, -14, 0, -8).build());
        add.accept(AnimDef.builder("purple_fusion", 14).hold().blend(0.5f, 3)
                .key(LEFT_ARM, 0, -88, 22, -55).key(RIGHT_ARM, 0, -88, -22, 55).strike(LEFT_ARM, 14, -96, -20, 0).strike(RIGHT_ARM, 14, -96, 20, 0)
                .key(ROOT, 0, 2, 0, 0).strike(ROOT, 14, 10, 0, 0).key(ROOT_POS, 0, 0, -2.5f, 0).strike(ROOT_POS, 14, 0, -3, 0.5f)
                .strike(HEAD, 14, 10, 0, 0).key(RIGHT_LEG, 14, 18, 0, 8).key(LEFT_LEG, 14, -18, 0, -8).build());
        add.accept(stance(AnimDef.builder("purple_release", 18).blend(0.5f, 6)
                .key(RIGHT_ARM, 0, -96, 20, 0).key(LEFT_ARM, 0, -96, -20, 0).key(ROOT, 0, 10, 0, 0).key(ROOT_POS, 0, 0, -3, 0.5f)
                .strike(RIGHT_ARM, 3, -102, -4, 0).strike(LEFT_ARM, 3, 30, 0, -28).strike(BODY, 3, 0, -28, 0).strike(ROOT, 3, 14, 18, 0)
                .strike(ROOT_POS, 3, 0, -3, 5).strike(RIGHT_LEG, 3, 35, 0, 6).strike(LEFT_LEG, 3, -35, 0, -6)
                .settle(ROOT_POS, 10, 0, -2.5f, 4), 18).build());
        // Max Blue: both hands pushed forward, the attraction gathered between them, the body leaning back against it.
        add.accept(rest(AnimDef.builder("max_blue_cast", 22).blend(1.5f, 6)
                .key(RIGHT_ARM, 0, -40, 0, 30).snap(RIGHT_ARM, 8, -100, -25, 0).key(RIGHT_ARM, 16, -96, -15, 0)
                .key(LEFT_ARM, 0, -40, 0, -30).snap(LEFT_ARM, 8, -100, 25, 0).key(LEFT_ARM, 16, -96, 15, 0)
                .snap(ROOT, 8, -8, 0, 0).snap(ROOT_POS, 8, 0, -2, -1.5f).snap(RIGHT_LEG, 8, -18, 0, 6).snap(LEFT_LEG, 8, 18, 0, -6)
                .snap(HEAD, 8, 6, 0, 0), 22).build());
        // Max Red: a deep, braced charge (the wind whipping round the arm is drawn over it).
        add.accept(AnimDef.builder("max_red_charge", 10).hold().blend(1.5f, 3)
                .snap(RIGHT_ARM, 10, -94, -8, 0).snap(LEFT_ARM, 10, -82, 46, 0).snap(BODY, 10, 0, -18, 0).snap(ROOT, 10, 12, 20, 0)
                .snap(ROOT_POS, 10, 0, -4, 0).snap(RIGHT_LEG, 10, 30, 0, 8).snap(LEFT_LEG, 10, -30, 0, -8).snap(HEAD, 10, 6, 0, 0).build());
        // Infinite Void: the crossed-fingers sign held at the chest, head down; then arms flung wide as it opens.
        add.accept(AnimDef.builder("domain_sign", 8).hold().blend(2, 4)
                .snap(RIGHT_ARM, 8, -112, -38, 0).snap(LEFT_ARM, 8, 5, 0, -8).snap(HEAD, 8, 14, 0, 0).snap(BODY, 8, 3, -8, 0)
                .snap(ROOT, 8, 2, 6, 0).snap(ROOT_POS, 8, 0, -0.5f, 0).build());
        add.accept(rest(AnimDef.builder("domain_release", 24).blend(0.5f, 8)
                .key(RIGHT_ARM, 0, -112, -38, 0).snap(RIGHT_ARM, 5, -60, 0, 72).key(RIGHT_ARM, 16, -55, 0, 66)
                .key(LEFT_ARM, 0, 5, 0, -8).snap(LEFT_ARM, 5, -60, 0, -72).key(LEFT_ARM, 16, -55, 0, -66)
                .snap(ROOT, 5, -10, 0, 0).snap(ROOT_POS, 5, 0, 1, 0).snap(HEAD, 5, -16, 0, 0), 24).build());
    }

    // --- Hakari (JJS Restless Gambler GIFs) ---

    private static void hakari(Consumer<AnimDef> add) {
        add.accept((AnimDef.builder("rough_charge", 8).hold().blend(1.5f, 3)
                // Rough Energy: the fist cocked right back past the hip, the whole body wound like a spring.
                .snap(RIGHT_ARM, 8, 55, 22, 32).snap(LEFT_ARM, 8, -88, 32, -8).snap(BODY, 8, 8, 42, 0).snap(ROOT, 8, 10, -34, 0)
                .snap(ROOT_POS, 8, 0, -4, -2).snap(RIGHT_LEG, 8, 32, 0, 7).snap(LEFT_LEG, 8, -34, 0, -7).snap(HEAD, 8, 6, 0, 0)).build());
        add.accept(stance(AnimDef.builder("rough_strike", 14).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, 55, 22, 32).key(LEFT_ARM, 0, -88, 32, -8).key(BODY, 0, 8, 42, 0).key(ROOT, 0, 10, -34, 0).key(ROOT_POS, 0, 0, -4, -2)
                .key(RIGHT_LEG, 0, 32, 0, 7).key(LEFT_LEG, 0, -34, 0, -7)
                .strike(RIGHT_ARM, 2, -104, -6, 0).strike(LEFT_ARM, 2, 35, 0, -22).strike(BODY, 2, 10, -42, 0).strike(ROOT, 2, 18, 26, 0)
                .strike(ROOT_POS, 2, 0, -4, 9).strike(RIGHT_LEG, 2, 45, 0, 7).strike(LEFT_LEG, 2, -42, 0, -7)
                .settle(ROOT_POS, 8, 0, -3, 7), 14).build());
        add.accept(stance(AnimDef.builder("fever_kick", 12).blend(1, 4)
                // Fever Breaker: knee up, the foot driven straight out through them.
                .key(RIGHT_LEG, 0, 0, 0, 0).key(RIGHT_LEG, 3, -75, 0, 0).strike(RIGHT_LEG, 5, -98, 0, 0).settle(RIGHT_LEG, 8, -90, 0, 0)
                .key(ROOT, 3, 6, 0, 0).strike(ROOT, 5, -22, 6, 0).settle(ROOT, 8, -18, 4, 0)
                .key(ROOT_POS, 3, 0, 0, 0).strike(ROOT_POS, 5, 0, 1, 3)
                .strike(RIGHT_ARM, 5, 28, 0, 32).strike(LEFT_ARM, 5, -45, 0, -38).strike(LEFT_LEG, 5, 10, 0, 0), 12).build());
        add.accept(AnimDef.builder("fever_rush", 6).hold().blend(1, 3)
                .snap(ROOT, 6, 34, 0, 0).snap(ROOT_POS, 6, 0, -3, 2).snap(RIGHT_ARM, 6, 60, 0, 16).snap(LEFT_ARM, 6, 60, 0, -16)
                .snap(RIGHT_LEG, 6, -48, 0, 0).snap(LEFT_LEG, 6, 40, 0, 0).snap(HEAD, 6, -26, 0, 0).build());
        add.accept(rest(AnimDef.builder("fever_finish", 14).blend(0.5f, 5)
                // The flying dropkick: laid right back in the air, both feet driving through the doors.
                .key(ROOT, 0, 15, 0, 0).strike(ROOT, 2, -62, 0, 0).key(ROOT, 7, -58, 0, 0).key(ROOT_POS, 0, 0, 0, 0).strike(ROOT_POS, 2, 0, 8, 4)
                .strike(RIGHT_LEG, 2, -40, 0, 6).strike(LEFT_LEG, 2, -36, 0, -6).key(RIGHT_LEG, 7, -40, 0, 6).key(LEFT_LEG, 7, -36, 0, -6)
                .strike(RIGHT_ARM, 2, -40, 0, 72).strike(LEFT_ARM, 2, -40, 0, -72), 14).build());
        // Fever Crush (the doors added mid wind-up): he plants and gathers himself; the raise lifts the knee high; the axe
        // kick drops the heel straight down onto them.
        add.accept(AnimDef.builder("fever_crush", 6).blend(0.5f, 3)
                .snap(ROOT, 3, 10, -10, 0).snap(ROOT_POS, 3, 0, -3, 0).snap(RIGHT_ARM, 3, -30, 0, 45).snap(LEFT_ARM, 3, -30, 0, -45)
                .snap(RIGHT_LEG, 3, 18, 0, 5).snap(LEFT_LEG, 3, -18, 0, -5).key(ROOT, 6, 8, -8, 0).build());
        add.accept(AnimDef.builder("fever_raise", 12).hold().blend(1, 2)
                .key(RIGHT_LEG, 0, 10, 0, 4).key(RIGHT_LEG, 6, -120, 0, 6).snap(RIGHT_LEG, 12, -168, 0, 6)
                .key(ROOT, 6, -10, 0, 0).snap(ROOT, 12, -22, 0, 0).key(ROOT_POS, 6, 0, 1, 0).snap(ROOT_POS, 12, 0, 2, -1)
                .key(RIGHT_ARM, 6, -60, 0, 55).snap(RIGHT_ARM, 12, -50, 0, 70).key(LEFT_ARM, 6, -60, 0, -55).snap(LEFT_ARM, 12, -50, 0, -70)
                .snap(LEFT_LEG, 12, 12, 0, -4).snap(HEAD, 12, 10, 0, 0).build());
        add.accept(stance(AnimDef.builder("fever_axe", 12).blend(0.3f, 4)
                .key(RIGHT_LEG, 0, -168, 0, 6).strike(RIGHT_LEG, 2, 12, 0, 4).key(ROOT, 0, -22, 0, 0).strike(ROOT, 2, 28, 0, 0)
                .key(ROOT_POS, 0, 0, 2, -1).strike(ROOT_POS, 2, 0, -4, 3).settle(ROOT, 6, 22, 0, 0)
                .strike(RIGHT_ARM, 2, 35, 0, 40).strike(LEFT_ARM, 2, 35, 0, -40).strike(LEFT_LEG, 2, -25, 0, -4).strike(HEAD, 2, 22, 0, 0), 12).build());
        // Lucky Rushdown's finisher: the leap after the hurled target, drawn back for the punch.
        add.accept(AnimDef.builder("energy_leap", 7).blend(0.5f, 2)
                .snap(ROOT, 3, 20, -20, 0).snap(RIGHT_ARM, 3, 50, 20, 30).snap(LEFT_ARM, 3, -100, 25, -20)
                .snap(RIGHT_LEG, 3, -60, 0, 4).snap(LEFT_LEG, 3, 30, 0, -4).snap(BODY, 3, 0, 30, 0).snap(HEAD, 3, -10, 0, 0)
                .key(RIGHT_ARM, 7, 55, 22, 32).key(ROOT, 7, 18, -26, 0).build());
        add.accept(AnimDef.builder("door_guard", 4).hold().blend(1, 3)
                .snap(RIGHT_ARM, 4, -102, -50, 0).snap(LEFT_ARM, 4, -62, 30, -20).snap(BODY, 4, 0, -10, 0).snap(ROOT, 4, 12, 8, 0)
                .snap(ROOT_POS, 4, 0, -2.5f, -0.5f).snap(RIGHT_LEG, 4, 22, 0, 5).snap(LEFT_LEG, 4, -22, 0, -5).snap(HEAD, 4, 10, 0, 0).build());
        add.accept(stance(straight(AnimDef.builder("volley_open", 8).blend(1, 3), 0, 3, 1, 0), 8).build());
        AnimDef.Builder fl = AnimDef.builder("volley_flurry", 20).blend(1, 3)
                .key(ROOT, 0, 10, 0, 0).key(ROOT_POS, 0, 0, -2, 1).key(RIGHT_LEG, 0, 20, 0, 6).key(LEFT_LEG, 0, -20, 0, -6);
        for (int i = 0; i <= 10; i++) {
            boolean r = i % 2 == 0;
            float s = r ? 1 : -1, t = i * 2;
            Part punch = r ? RIGHT_ARM : LEFT_ARM, other = r ? LEFT_ARM : RIGHT_ARM;
            fl.strike(punch, t, -100 + (i % 3) * 5, -12 * s, 0).key(other, t, -58, 24 * s, -16 * s)
                    .strike(BODY, t, 0, -22 * s, 0).strike(ROOT, t, 11, 7 * s, 0).strike(ROOT_POS, t, 0.5f * s, -2, 1.5f + (i % 2));
        }
        add.accept(fl.build());
        AnimDef.Builder vf = AnimDef.builder("volley_final", 12).blend(0.5f, 4)
                .key(RIGHT_ARM, 0, 45, 15, 30).key(BODY, 0, 5, 36, 0).key(ROOT, 0, 6, -30, 0).key(ROOT_POS, 0, 0, -3, -1)
                .strike(RIGHT_ARM, 2, -106, -6, 0).strike(LEFT_ARM, 2, 35, 0, -25).strike(BODY, 2, 8, -40, 0).strike(ROOT, 2, 18, 24, 0)
                .strike(ROOT_POS, 2, 0, -3, 8).strike(RIGHT_LEG, 2, 40, 0, 6).strike(LEFT_LEG, 2, -38, 0, -6);
        add.accept(stance(vf, 12).build());
        add.accept(AnimDef.builder("rushdown_run", 6).hold().blend(1, 3)
                .snap(ROOT, 6, 34, 0, 0).snap(ROOT_POS, 6, 0, -2.5f, 2).snap(HEAD, 6, -26, 0, 0).snap(RIGHT_ARM, 6, 60, 0, 12).snap(LEFT_ARM, 6, -75, 0, -12)
                .snap(RIGHT_LEG, 6, -50, 0, 0).snap(LEFT_LEG, 6, 38, 0, 0).build());
        add.accept(AnimDef.builder("rushdown_drag", 4).hold().blend(1, 2)
                .snap(ROOT, 4, 30, -18, 0).snap(ROOT_POS, 4, 0, -3, 2).snap(RIGHT_ARM, 4, -98, -20, 0).snap(LEFT_ARM, 4, -92, 30, 0)
                .snap(RIGHT_LEG, 4, -38, 0, 0).snap(LEFT_LEG, 4, 32, 0, 0).snap(HEAD, 4, -12, 0, 0).build());
        add.accept(rest(AnimDef.builder("rushdown_throw", 14).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, -98, -20, 0).key(LEFT_ARM, 0, -92, 30, 0).key(ROOT, 0, 30, -18, 0).key(ROOT_POS, 0, 0, -3, 2)
                .strike(RIGHT_ARM, 3, -175, 0, 22).strike(LEFT_ARM, 3, -175, 0, -22).strike(ROOT, 3, -24, 10, 0).strike(ROOT_POS, 3, 0, 1, 1)
                .strike(HEAD, 3, -20, 0, 0).strike(RIGHT_LEG, 3, -10, 0, 0).strike(LEFT_LEG, 3, 20, 0, 0), 14).build());
        add.accept(AnimDef.builder("overwhelm_ready", 5).blend(1, 2)
                .snap(RIGHT_ARM, 5, -70, -22, 12).snap(LEFT_ARM, 5, -82, 22, -10).snap(ROOT, 5, 10, 0, 0).snap(ROOT_POS, 5, 0, -2, 0)
                .snap(RIGHT_LEG, 5, 18, 0, 6).snap(LEFT_LEG, 5, -18, 0, -6).build());
        AnimDef right = AnimDef.builder("overwhelm_right", 4).blend(0.5f, 2)
                .key(RIGHT_ARM, 0, -60, -18, 20).strike(RIGHT_ARM, 2, -102, -10, 0).settle(RIGHT_ARM, 4, -72, -12, 10)
                .key(LEFT_ARM, 0, -85, 18, -10).key(LEFT_ARM, 4, -70, 22, -10)
                .key(BODY, 0, 0, 14, 0).strike(BODY, 2, 0, -24, 0).key(ROOT, 0, 10, -8, 0).strike(ROOT, 2, 14, 14, 0)
                .key(ROOT_POS, 0, 0, -2, 0).strike(ROOT_POS, 2, 0, -2, 4).settle(ROOT_POS, 4, 0, -2, 2.5f)
                .strike(RIGHT_LEG, 2, 26, 0, 6).strike(LEFT_LEG, 2, -24, 0, -6).build();
        add.accept(right);
        add.accept(right.mirrored("overwhelm_left"));
        add.accept(stance(AnimDef.builder("overwhelm_final", 16).blend(0.5f, 5)
                .key(RIGHT_ARM, 0, 50, 22, 32).key(LEFT_ARM, 0, -85, 32, -10).key(BODY, 0, 8, 45, 0).key(ROOT, 0, 8, -36, 0).key(ROOT_POS, 0, 0, -4, -2)
                .key(RIGHT_LEG, 0, 30, 0, 7).key(LEFT_LEG, 0, -30, 0, -7)
                .strike(RIGHT_ARM, 3, -106, 0, 0).strike(LEFT_ARM, 3, 38, 0, -26).strike(BODY, 3, 12, -46, 0).strike(ROOT, 3, 22, 28, 0)
                .strike(ROOT_POS, 3, 0, -4, 10).strike(RIGHT_LEG, 3, 48, 0, 7).strike(LEFT_LEG, 3, -44, 0, -7)
                .settle(ROOT_POS, 10, 0, -3, 9), 16).build());
        add.accept(AnimDef.builder("surge_dash", 8).hold().blend(1, 3)
                .snap(ROOT, 8, 42, 0, 0).snap(ROOT_POS, 8, 0, -4, 3).snap(RIGHT_ARM, 8, 65, 0, 20).snap(LEFT_ARM, 8, 65, 0, -20)
                .snap(RIGHT_LEG, 8, -36, 0, 0).snap(LEFT_LEG, 8, 46, 0, 0).snap(HEAD, 8, -30, 0, 0).build());
        add.accept(stance(AnimDef.builder("surge_kick", 14).blend(0.5f, 5)
                // Energy Surge: the heel raised high over them, then the axe kick.
                .key(RIGHT_LEG, 0, -160, 0, 4).key(RIGHT_LEG, 4, -170, 0, 4).strike(RIGHT_LEG, 7, 15, 0, 4)
                .key(ROOT, 0, -22, 0, 0).key(ROOT, 4, -26, 0, 0).strike(ROOT, 7, 30, 0, 0).key(ROOT_POS, 4, 0, 2, 0).strike(ROOT_POS, 7, 0, -4, 3)
                .key(RIGHT_ARM, 0, -45, 0, 62).key(LEFT_ARM, 0, -45, 0, -62).strike(RIGHT_ARM, 7, 30, 0, 40).strike(LEFT_ARM, 7, 30, 0, -40)
                .strike(HEAD, 7, 20, 0, 0), 14).build());
        add.accept(rest(AnimDef.builder("reserve_balls", 16).blend(1, 4)
                // Reserve Balls: over the shoulder, then the flick (the ball leaves on the 7th tick).
                .key(RIGHT_ARM, 0, -20, 0, 20).key(RIGHT_ARM, 4, -178, 0, 25).strike(RIGHT_ARM, 7, -112, -12, 5).settle(RIGHT_ARM, 11, -45, 0, 10)
                .key(LEFT_ARM, 4, -45, 0, -30).key(ROOT, 4, -10, -16, 0).strike(ROOT, 7, 14, 18, 0).key(ROOT_POS, 4, 0, 0, -1).strike(ROOT_POS, 7, 0, -1.5f, 2)
                .key(RIGHT_LEG, 4, 10, 0, 3).strike(RIGHT_LEG, 7, 24, 0, 3).strike(LEFT_LEG, 7, -22, 0, -3), 16).build());
    }
}
