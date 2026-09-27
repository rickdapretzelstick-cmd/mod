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

    /** Positive widens the view (impacts), negative narrows it (charging). */
    public static void fovPunch(float amount) {
        if (!JJKConfig.get().client.fovEffects) return;
        fovKick += amount;
    }

    /** Sustained FOV change while something is held (e.g. charging Purple); reset to 0 when done. */
    public static void fovHold(float amount) {
        fovTarget = JJKConfig.get().client.fovEffects ? amount : 0;
    }

    public static void tick() {
        time++;
        trauma = Math.max(0, trauma - traumaDecay);
        flashAlpha = Math.max(0, flashAlpha - flashDecay);
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
        trauma = flashAlpha = fovKick = fovTarget = fovCurrent = 0;
    }
}
