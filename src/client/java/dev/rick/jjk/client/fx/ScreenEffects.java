package dev.rick.jjk.client.fx;

import dev.rick.jjk.config.JJKConfig;
import net.minecraft.util.Mth;

/** Camera shake (trauma based), colored flashes, FOV punches and hit-confirm kicks for the local player. */
public final class ScreenEffects {
    private static float trauma;
    private static float traumaDecay = 0.05f;
    private static float flashAlpha;
    private static int flashColor;
    private static float flashDecay;
    private static float fovKick;
    private static float fovTarget;
    private static long time;
    private static int impactTicks;
    private static int impactLength;
    private static int tintColor;
    private static float tintAlpha, tintTarget;
    private static int tintHold;

    private ScreenEffects() {}

    public static void shake(float intensity, int duration) {
        JJKConfig.Client c = JJKConfig.get().client;
        if (!c.screenShake) return;
        float t = Mth.clamp(intensity * c.screenShakeScale, 0, 1.5f);
        if (t > trauma) {
            trauma = t;
            traumaDecay = t / Math.max(4, duration);
        } else {
            trauma = Math.min(1.5f, trauma + t * 0.3f);
        }
    }

    public static void flash(int argb, int duration) {
        if (!JJKConfig.get().client.screenFlashes) return;
        float a = ((argb >>> 24) & 0xFF) / 255f;
        if (a < flashAlpha * 0.5f) return;
        flashColor = argb & 0xFFFFFF;
        flashAlpha = a;
        flashDecay = a / Math.max(2, duration);
    }

    /**
     * A wash of colour over the whole view that stays while something keeps calling this (the world lit magenta by Hollow
     * Purple, red by Max Red), easing in and, once the calls stop, fading back out over a few ticks.
     */
    public static void tint(int argb, int hold) {
        if (!JJKConfig.get().client.screenFlashes) return;
        float a = ((argb >>> 24) & 0xFF) / 255f;
        if (tintHold > 0 && a < tintTarget && (argb & 0xFFFFFF) != tintColor) return;
        tintColor = argb & 0xFFFFFF;
        tintTarget = a;
        tintHold = Math.max(tintHold, hold);
    }

    /** ARGB of the current tint (0 when none). */
    public static int tintColor() {
        return tintAlpha < 0.01f ? 0 : (Math.round(Math.min(1f, tintAlpha) * 255) << 24) | tintColor;
    }

    /** Positive widens the view (impacts), negative narrows it (charging). */
    public static void fovPunch(float amount) {
        if (!JJKConfig.get().client.fovEffects) return;
        fovKick += amount;
    }

    /** Sustained FOV change while something is held (e.g. charging Purple); reset to 0 when done. */
    public static void fovHold(float amount) {
        fovTarget = JJKConfig.get().client.fovEffects ? amount : 0;
    }

    /** Impact frame: a hard white flash then a dark hold, for ultimate-level finishes. */
    public static void impact(int duration) {
        impact(duration, 0);
    }

    /**
     * 0: white then dark. 1: Black Flash (red, then black). 2: the fourth Black Flash (black, white, red). 3: Unlimited
     * Purple's collision (black, white, then magenta).
     */
    private static int impactStyle;

    public static void impact(int duration, int style) {
        if (!JJKConfig.get().client.screenFlashes) return;
        impactStyle = style;
        impactLength = Math.max(3, duration);
        impactTicks = impactLength;
        shake(1.2f, 14);
    }

    /** ARGB overlay for the current impact frame (0 when none). */
    public static int impactColor() {
        if (impactTicks <= 0) return 0;
        int elapsed = impactLength - impactTicks;
        if (impactStyle == 1) return elapsed < 2 ? 0xD0E0101A : elapsed < 4 ? 0xC0000000 : 0x50B00010;
        if (impactStyle == 2) return elapsed < 2 ? 0xE0000000 : elapsed < 4 ? 0xE0FFFFFF : elapsed < 6 ? 0xC0D00012 : 0x60000000;
        if (impactStyle == 3) return elapsed < 2 ? 0xF0000000 : elapsed < 3 ? 0xF0FFFFFF : 0x90E020D0;
        if (elapsed < 2) return 0xE0FFFFFF;
        if (elapsed < 4) return 0xB0000000;
        return 0x60FFFFFF;
    }

    public static void tick() {
        time++;
        if (impactTicks > 0) impactTicks--;
        trauma = Math.max(0, trauma - traumaDecay);
        flashAlpha = Math.max(0, flashAlpha - flashDecay);
        if (tintHold > 0) {
            tintHold--;
            tintAlpha += (tintTarget - tintAlpha) * 0.35f;
        } else {
            tintAlpha *= 0.8f;
        }
        fovKick *= 0.78f;
        if (Math.abs(fovKick) < 1e-3) fovKick = 0;
    }

    private static float noise(float seed, float t) {
        return (float) (Math.sin(t * 1.7 + seed) * 0.6 + Math.sin(t * 3.1 + seed * 2.3) * 0.4);
    }

    /** Degrees to add to yaw/pitch this frame. */
    public static float yawOffset(float partial) {
        float s = trauma * trauma;
        return s * 5f * noise(11.3f, (time + partial) * 1.9f);
    }

    public static float pitchOffset(float partial) {
        float s = trauma * trauma;
        return s * 4f * noise(47.1f, (time + partial) * 2.2f);
    }

    public static float rollOffset(float partial) {
        float s = trauma * trauma;
        return s * 3f * noise(3.7f, (time + partial) * 1.6f);
    }

    private static float fovCurrent;

    public static float fovMultiplier(float partial) {
        fovCurrent += (fovTarget - fovCurrent) * 0.08f;
        return 1f + fovKick + fovCurrent;
    }

    public static float flashAlpha() {
        return flashAlpha;
    }

    public static int flashColor() {
        return flashColor;
    }

    public static void reset() {
        trauma = flashAlpha = fovKick = fovTarget = fovCurrent = tintAlpha = tintTarget = 0;
        tintHold = 0;
    }
}
