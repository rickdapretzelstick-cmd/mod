package dev.rick.jjk.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Being pulled into a cursed realm, on screen: the edges close in black, a dull red-violet pulse beats twice (with the
 * server's heartbeats), the view goes fully dark at the moment of the jump, then lifts slowly in the realm. Driven by
 * game time from one message at the start of the pull, so it costs nothing while idle.
 */
public final class RealmTransitionOverlay {
    private static long start = -1;
    private static int length;
    /** Ticks the dark takes to lift once there. */
    private static final int FADE_IN = 30;

    private RealmTransitionOverlay() {}

    public static void start(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        start = mc.level.getGameTime();
        length = Math.max(10, ticks);
    }

    /** Whether it is showing (for tests). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return start >= 0 && mc.level != null && mc.level.getGameTime() - start < length + FADE_IN;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (start < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            start = -1;
            return;
        }
        float t = (mc.level.getGameTime() - start) + delta.getGameTimeDeltaPartialTick(false);
        // A dimension change resets game time on some servers: never leave the screen stuck dark.
        if (t < 0 || t > length + FADE_IN) {
            start = -1;
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        float dark;
        if (t < length) {
            float p = t / length;
            dark = p * p;
            // The pulse: two beats, at a third and at two thirds.
            float beat = Math.max(pulse(p, 0.33f), pulse(p, 0.66f));
            vignette(g, w, h, 0x5A0A3C, 0.35f + 0.5f * p + 0.4f * beat);
        } else {
            dark = 1 - (t - length) / FADE_IN;
            vignette(g, w, h, 0x3A0620, 0.6f * dark);
        }
        int a = Mth.clamp(Math.round(dark * 245), 0, 255);
        if (a > 0) g.fill(0, 0, w, h, a << 24);
    }

    private static float pulse(float p, float at) {
        float d = Math.abs(p - at) / 0.06f;
        return d >= 1 ? 0 : 1 - d * d;
    }

    private static void vignette(GuiGraphicsExtractor g, int w, int h, int rgb, float strength) {
        int steps = 14;
        for (int i = 0; i < steps; i++) {
            float f = (float) i / steps;
            int alpha = Mth.clamp(Math.round(255 * strength * (1 - f) * (1 - f) * 0.25f), 0, 255);
            int c = (alpha << 24) | rgb;
            int x = Math.round(w * 0.5f * 0.55f * (1 - f)), y = Math.round(h * 0.5f * 0.55f * (1 - f));
            g.fill(0, 0, w, y, c);
            g.fill(0, h - y, w, h, c);
            g.fill(0, y, x, h - y, c);
            g.fill(w - x, y, w, h - y, c);
        }
    }
}
