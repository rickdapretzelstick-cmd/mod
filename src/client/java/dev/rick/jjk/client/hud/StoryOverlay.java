package dev.rick.jjk.client.hud;

import dev.rick.jjk.core.net.StoryPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The character storylines on screen ({@link StoryPayload}): title cards ("SATISFIED?", "JACKPOT"), the Infused
 * Blindfold's vision (the world all but black, the vignette breathing violet), the Six Eyes reveal when it comes off (a
 * white-out, then a blue iris opening out to the edges of the screen with rings of light), the Human Earthworm film
 * (letterbox, grain, a flicker, a line of dialogue) and a small line for a trial's progress. Everything is driven by game
 * time from one message, so it costs nothing while idle.
 */
public final class StoryOverlay {
    private static final Random RNG = new Random();

    private static long cardStart = -1;
    private static int cardTicks;
    private static String cardTitle = "", cardSub = "";
    private static int cardColor = 0xFFFFFFFF;

    private static boolean blind;
    private static long blindStart = -1;

    private static long eyesStart = -1;
    private static int eyesTicks;

    private static long filmStart = -1;
    private static int filmTicks;
    private static String filmLine = "";

    private static String meter = "";
    private static int meterColor = 0xFFE0D0FF;

    private StoryOverlay() {}

    public static void apply(StoryPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (p.kind()) {
            case StoryPayload.CARD -> {
                cardStart = now;
                cardTicks = Math.max(10, p.ticks());
                cardTitle = p.title();
                cardSub = p.subtitle();
                cardColor = p.color() | 0xFF000000;
            }
            case StoryPayload.BLIND -> {
                boolean on = p.ticks() > 0;
                if (on && !blind) blindStart = now;
                blind = on;
            }
            case StoryPayload.SIX_EYES -> {
                blind = false;
                eyesStart = now;
                eyesTicks = Math.max(40, p.ticks());
            }
            case StoryPayload.FILM -> {
                filmStart = now;
                filmTicks = Math.max(10, p.ticks());
                filmLine = p.subtitle();
            }
            case StoryPayload.METER -> {
                meter = p.title();
                meterColor = p.color() | 0xFF000000;
            }
            default -> {}
        }
    }

    /** Whether the blindfold's vision is on (tests). */
    public static boolean blind() {
        return blind;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            blind = false;
            cardStart = eyesStart = filmStart = -1;
            meter = "";
            return;
        }
        float now = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        if (filmStart >= 0) film(g, font, w, h, now - filmStart);
        if (blind) blindfold(g, w, h, now - blindStart);
        if (eyesStart >= 0) sixEyes(g, font, w, h, now - eyesStart);
        if (cardStart >= 0) card(g, font, w, h, now - cardStart);
        if (!meter.isEmpty()) g.centeredText(font, meter, w / 2, 8, meterColor);
    }

    // --- Title card: slams in large, holds, fades ---

    private static void card(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        if (t < 0 || t > cardTicks) {
            cardStart = -1;
            return;
        }
        float in = Mth.clamp(t / 4f, 0, 1), out = Mth.clamp((cardTicks - t) / 10f, 0, 1);
        float a = Math.min(in, out);
        int alpha = Mth.clamp(Math.round(a * 255), 0, 255);
        if (alpha < 8) return;
        // A dark band behind it, like a cut-in.
        int band = Math.round(28 + 10 * in);
        g.fill(0, h / 2 - band, w, h / 2 + band, Math.round(alpha * 0.55f) << 24);
        g.fill(0, h / 2 - band, w, h / 2 - band + 1, (alpha << 24) | (cardColor & 0xFFFFFF));
        g.fill(0, h / 2 + band - 1, w, h / 2 + band, (alpha << 24) | (cardColor & 0xFFFFFF));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h / 2f - 12);
        float scale = 3.2f + 1.4f * (1 - in) + 0.05f * Mth.sin(t * 0.4f);
        pose.scale(scale, scale);
        g.centeredText(font, cardTitle, 0, -4, (alpha << 24) | (cardColor & 0xFFFFFF));
        pose.popMatrix();
        if (!cardSub.isEmpty()) g.centeredText(font, cardSub, w / 2, h / 2 + 12, (alpha << 24) | 0xE8E8F0);
    }

    // --- The Infused Blindfold: darkness with a violet breath at the edges ---

    private static void blindfold(GuiGraphicsExtractor g, int w, int h, float t) {
        float fadeIn = Mth.clamp(t / 30f, 0, 1);
        // Severely restricted: the world is all but gone (cursed energy, glowing through it, is what's left to see).
        g.fill(0, 0, w, h, Math.round(238 * fadeIn) << 24);
        float breath = 0.5f + 0.5f * Mth.sin(t * 0.08f);
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float f = (float) i / steps;
            int alpha = Mth.clamp(Math.round(fadeIn * (60 + 50 * breath) * (1 - f) * (1 - f) * 0.4f), 0, 255);
            int c = (alpha << 24) | 0x3A1466;
            int x = Math.round(w * 0.5f * 0.6f * (1 - f)), y = Math.round(h * 0.5f * 0.6f * (1 - f));
            g.fill(0, 0, w, y, c);
            g.fill(0, h - y, w, h, c);
            g.fill(0, y, x, h - y, c);
            g.fill(w - x, y, w, h - y, c);
        }
    }

    // --- Six Eyes: white-out, then the iris opens and the world floods back sharper ---

    private static void sixEyes(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        if (t < 0 || t > eyesTicks) {
            eyesStart = -1;
            return;
        }
        float p = t / eyesTicks;
        int cx = w / 2, cy = h / 2;
        if (p < 0.12f) {
            // The blindfold falls: white floods in.
            int a = Mth.clamp(Math.round(255 * p / 0.12f), 0, 255);
            g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
            return;
        }
        // The iris: concentric rings of blue opening out from the centre, the white thinning behind them.
        float open = Mth.clamp((p - 0.12f) / 0.5f, 0, 1);
        float fade = 1 - Mth.clamp((p - 0.62f) / 0.38f, 0, 1);
        int white = Mth.clamp(Math.round(255 * (1 - open) * fade), 0, 255);
        if (white > 0) g.fill(0, 0, w, h, (white << 24) | 0xFFFFFF);
        float maxR = (float) Math.sqrt(w * w + h * h) / 2f;
        for (int ring = 0; ring < 6; ring++) {
            float r = maxR * open * (0.25f + ring * 0.16f);
            int a = Mth.clamp(Math.round(fade * (170 - ring * 22)), 0, 255);
            int col = ring % 2 == 0 ? 0x6EC8FF : 0xE8F8FF;
            ring(g, cx, cy, r, 1.5f + ring * 0.4f, (a << 24) | col);
        }
        // Light streaking outward: everything seen at once.
        for (int i = 0; i < 48; i++) {
            double ang = i * Math.PI * 2 / 48 + t * 0.01;
            float r0 = maxR * open * 0.2f, r1 = maxR * (0.3f + open * 0.9f) * (0.6f + 0.4f * ((i * 37) % 11) / 10f);
            int a = Mth.clamp(Math.round(fade * 110), 0, 255);
            for (float r = r0; r < r1; r += 4) {
                int x = cx + Math.round((float) Math.cos(ang) * r), y = cy + Math.round((float) Math.sin(ang) * r);
                g.fill(x, y, x + 1, y + 1, (a << 24) | 0xBEEBFF);
            }
        }
        if (p > 0.3f && p < 0.85f) {
            int a = Mth.clamp(Math.round(255 * Math.min((p - 0.3f) / 0.1f, (0.85f - p) / 0.1f)), 0, 255);
            if (a > 8) g.centeredText(font, "I can see everything.", cx, cy + Math.round(maxR * 0.35f), (a << 24) | 0xE8F8FF);
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, float r, float thick, int color) {
        int n = Math.max(24, Math.round(r * 1.2f));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = cx + Math.round((float) Math.cos(a) * r), y = cy + Math.round((float) Math.sin(a) * r);
            int s = Math.max(1, Math.round(thick));
            g.fill(x, y, x + s, y + s, color);
        }
    }

    // --- Human Earthworm: an old film ---

    private static void film(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        if (t < 0 || t > filmTicks) {
            filmStart = -1;
            return;
        }
        int bar = h / 8;
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // Sepia wash, a flicker, grain, a scratch running down the frame.
        int flicker = RNG.nextInt(10) == 0 ? 70 : 30 + RNG.nextInt(14);
        g.fill(0, bar, w, h - bar, (flicker << 24) | 0x5A3A18);
        for (int i = 0; i < 90; i++) {
            int x = RNG.nextInt(w), y = bar + RNG.nextInt(Math.max(1, h - 2 * bar));
            g.fill(x, y, x + 1, y + 1, RNG.nextBoolean() ? 0x50FFFFFF : 0x50000000);
        }
        int sx = (int) (w * (0.3f + 0.4f * Mth.sin(t * 0.05f)));
        g.fill(sx, bar, sx + 1, h - bar, 0x40FFFFFF);
        if (!filmLine.isEmpty()) g.centeredText(font, filmLine, w / 2, h - bar + bar / 2 - 4, 0xFFF0E6C8);
    }
}
