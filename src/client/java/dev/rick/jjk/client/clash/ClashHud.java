package dev.rick.jjk.client.clash;

import dev.rick.jjk.JJK;
import dev.rick.jjk.core.domain.clash.ClashJudgement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The domain clash screen: cinematic letterbox, the tug-of-war domain meter, both sides' lanes (opponent left, you
 * right), prompts scrolling up into the timing zone, judgement pops, streak and accuracy. Drawn only while the local
 * player is duelling; it vanishes the instant the clash ends.
 */
public final class ClashHud {
    private static final Identifier ARROW = JJK.id("textures/gui/clash_arrow.png");
    private static final Identifier RECEPTOR = JJK.id("textures/gui/clash_receptor.png");
    private static final Identifier BURST = JJK.id("textures/gui/clash_burst.png");
    /** Lane colours (←, ↓, ↑, →) and arrow rotations (the texture points left). */
    private static final int[] LANE_COLOR = {0xFFC24BF9, 0xFF3FE0FF, 0xFF3CFA6A, 0xFFFF4B5C};
    private static final float[] LANE_ROT = {0, -90, 90, 180};
    private static final String[] JUDGE_TEXT = {"PERFECT!!", "GREAT!", "GOOD", "MISS", "MISS"};
    private static final int[] JUDGE_COLOR = {0xFFFFF08A, 0xFF8AF0FF, 0xFF9CFF9C, 0xFFFF5A5A, 0xFFB05050};
    /** Pixels a prompt travels per tick. */
    private static final float SPEED = 5.5f;

    private ClashHud() {}

    public static void render(GuiGraphicsExtractor g) {
        ClashClient.View v = ClashClient.view();
        if (v == null || v.local < 0) return;
        // A counter's versus card has the screen first; the clash takes over for its 3-2-1.
        if (dev.rick.jjk.client.cinematic.DomainCinematic.versusShowing()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        double clock = v.clock();
        long ms = System.currentTimeMillis();
        v.shownMeter += (v.meter - v.shownMeter) * 0.25f;
        int me = v.local, them = v.other(me);

        // Cinematic letterbox, and the screen edges burning in each side's colour as they gain ground.
        int bar = Math.max(14, h / 11);
        g.fill(0, 0, w, bar, 0xE0000000);
        g.fill(0, h - bar, w, h, 0xE0000000);
        float mine = me == 0 ? v.shownMeter : -v.shownMeter;
        edgeGlow(g, w, h, bar, v.colors[me], Math.max(0, mine) * 0.6f + v.heat[me] * 0.08f, true);
        edgeGlow(g, w, h, bar, v.colors[them], Math.max(0, -mine) * 0.6f + v.heat[them] * 0.08f, false);

        meter(g, font, v, w, bar, me, them);

        // Lanes: yours on the right, theirs on the left (smaller), receptors at the top, prompts rising into them.
        int laneGap = 26;
        int recY = bar + 46;
        int myX = w / 2 + 40;
        int theirX = w / 2 - 40 - laneGap * 3;
        lanes(g, v, them, theirX, recY, laneGap, 18, clock, ms, h - bar, 0.55f);
        lanes(g, v, me, myX, recY, laneGap, 24, clock, ms, h - bar, 1f);

        // Judgement pop and streak beside your lanes.
        judgement(g, font, v, me, myX + laneGap * 3 / 2, recY + 34, ms, 1f);
        judgement(g, font, v, them, theirX + laneGap * 3 / 2, recY + 34, ms, 0.7f);
        int infoY = h - bar - 22;
        g.text(font, String.format("ACC %.1f%%", v.accuracy[me] * 100), myX - 6, infoY, 0xFFE8F4FF, true);
        if (v.streak[me] >= 3) g.text(font, "STREAK " + v.streak[me], myX - 6, infoY + 10, streakColor(v.streak[me]), true);

        // Progress through the chart along the bottom bar.
        float prog = Mth.clamp((float) (clock / Math.max(1, v.end())), 0, 1);
        g.fill(0, h - bar, Math.round(w * prog), h - bar + 2, 0xC0FFFFFF);
        String title = v.round == 0 ? "DOMAIN CLASH" : "SUDDEN DEATH";
        g.centeredText(font, title, w / 2, h - bar / 2 - 4, v.round == 0 ? 0xFFDDEEFF : 0xFFFF6A6A);

        countdown(g, font, v, w, h, clock);
    }

    private static void meter(GuiGraphicsExtractor g, Font font, ClashClient.View v, int w, int bar, int me, int them) {
        int mw = Math.min(w - 40, 380), mh = 10;
        int x0 = w / 2 - mw / 2, x1 = x0 + mw, y = bar + 14;
        // Your bar grows from the right as you win.
        float mine = me == 0 ? v.shownMeter : -v.shownMeter;
        int split = Math.round(w / 2f - mine * mw / 2f);
        g.fill(x0 - 2, y - 2, x1 + 2, y + mh + 2, 0xFF05050A);
        g.fillGradient(x0, y, split, y + mh, brighten(v.colors[them], 0.25f), dim(v.colors[them]));
        g.fillGradient(split, y, x1, y + mh, brighten(v.colors[me], 0.25f), dim(v.colors[me]));
        // The front where the two domains meet, flaring with each side's momentum.
        int glow = 14 + Math.round((v.heat[me] + v.heat[them]) * 4);
        g.blit(RenderPipelines.GUI_TEXTURED, BURST, split - glow / 2, y + mh / 2 - glow / 2, 0, 0, glow, glow, 64, 64, 64, 64, 0xFFFFFFFF);
        g.fill(split - 1, y - 4, split + 1, y + mh + 4, 0xFFFFFFFF);
        g.text(font, v.names.get(them), x0, y - 11, v.colors[them] | 0xFF000000, true);
        String myName = v.names.get(me) + " (YOU)";
        g.text(font, myName, x1 - font.width(myName), y - 11, v.colors[me] | 0xFF000000, true);
        g.centeredText(font, "VS", w / 2, y + mh + 4, 0xFFFFFFFF);
        g.text(font, String.valueOf(v.score[them]), x0, y + mh + 4, 0xFFB0B8C8, true);
        String ms = String.valueOf(v.score[me]);
        g.text(font, ms, x1 - font.width(ms), y + mh + 4, 0xFFFFFFFF, true);
    }

    private static void lanes(GuiGraphicsExtractor g, ClashClient.View v, int who, int x, int recY, int gap, int size, double clock, long ms,
                              int bottom, float alpha) {
        int a = Math.round(alpha * 255) << 24;
        for (int lane = 0; lane < 4; lane++) {
            int cx = x + lane * gap + gap / 2;
            long since = ms - v.laneFlashAt[who][lane];
            float press = since < 120 ? 1f - since / 120f : 0f;
            int col = press > 0 ? lerpColor(0xFFFFFFFF, LANE_COLOR[lane], 1 - press) : 0x90A0A8B8;
            drawArrow(g, RECEPTOR, cx, recY, Math.round(size * (1f + press * 0.18f)), lane, (col & 0xFFFFFF) | a);
        }
        // Prompts still to hit (or just judged: a hit flashes out at the receptor, a miss keeps falling faintly).
        for (int i = 0; i < v.times.length; i++) {
            double dt = v.times[i] - clock;
            int y = recY + (int) Math.round(dt * SPEED);
            if (y > bottom + size || y < recY - size * 2) continue;
            int res = v.result[who][i];
            int lane = v.lanes[i];
            int cx = x + lane * gap + gap / 2;
            if (res == 0) {
                drawArrow(g, ARROW, cx, y, size, lane, (LANE_COLOR[lane] & 0xFFFFFF) | a);
            } else if (res - 1 <= ClashJudgement.GOOD.ordinal() && dt > -3) {
                float f = (float) Mth.clamp(1 + dt / 3, 0, 1);
                int s = Math.round(size * (1.4f + (1 - f)));
                g.blit(RenderPipelines.GUI_TEXTURED, BURST, cx - s / 2, recY - s / 2, 0, 0, s, s, 64, 64, 64, 64,
                        (LANE_COLOR[lane] & 0xFFFFFF) | (Math.round(f * alpha * 255) << 24));
            } else if (res - 1 == ClashJudgement.MISS.ordinal()) {
                drawArrow(g, ARROW, cx, y, size, lane, 0x40602020);
            }
        }
    }

    private static void judgement(GuiGraphicsExtractor g, Font font, ClashClient.View v, int who, int cx, int y, long ms, float scale) {
        int j = v.lastJudge[who];
        if (j < 0) return;
        long age = ms - v.lastJudgeAt[who];
        if (age > 500) return;
        float pop = age < 90 ? 1.35f - age / 90f * 0.35f : 1f;
        int alpha = age < 350 ? 255 : Math.round(255 * (1 - (age - 350) / 150f));
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, y);
        pose.scale(pop * scale * (j == 0 ? 1.4f : 1.1f));
        String text = JUDGE_TEXT[Math.min(j, JUDGE_TEXT.length - 1)];
        g.centeredText(font, text, 0, -4, (JUDGE_COLOR[Math.min(j, JUDGE_COLOR.length - 1)] & 0xFFFFFF) | (Math.max(0, alpha) << 24));
        if (v.streak[who] >= 2 && j <= ClashJudgement.GOOD.ordinal()) {
            pose.scale(0.7f);
            g.centeredText(font, "x" + v.streak[who], 0, 10, (streakColor(v.streak[who]) & 0xFFFFFF) | (Math.max(0, alpha) << 24));
        }
        pose.popMatrix();
    }

    private static void countdown(GuiGraphicsExtractor g, Font font, ClashClient.View v, int w, int h, double clock) {
        if (clock >= 12) return;
        // A counter's versus card is still on screen: the clash intro waits for it.
        if (dev.rick.jjk.client.cinematic.DomainCinematic.versusShowing()) return;
        String text;
        int color = 0xFFFFFFFF;
        float scale;
        if (clock < -30) {
            text = v.round == 0 ? "DOMAIN CLASH" : "SUDDEN DEATH";
            color = v.round == 0 ? 0xFFBFE6FF : 0xFFFF6A6A;
            scale = 3f;
        } else if (clock < 0) {
            int n = (int) Math.ceil(-clock / 10.0);
            text = String.valueOf(n);
            float f = (float) (1 - ((-clock) % 10) / 10.0);
            scale = 4f - f * 1.2f;
        } else {
            text = "CLASH!";
            color = 0xFFFFE08A;
            scale = 3.5f - (float) clock * 0.1f;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h / 2f - 10);
        pose.scale(scale);
        g.centeredText(font, text, 0, -4, color);
        pose.popMatrix();
        if (clock < -30) g.centeredText(font, "Hit the arrows as they reach the top  [← ↓ ↑ →  or  A S W D]", w / 2, h / 2 + 22, 0xFFE0E8F0);
    }

    private static void drawArrow(GuiGraphicsExtractor g, Identifier tex, int cx, int cy, int size, int lane, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(LANE_ROT[lane]));
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -size / 2, -size / 2, 0, 0, size, size, 64, 64, 64, 64, color);
        pose.popMatrix();
    }

    private static void edgeGlow(GuiGraphicsExtractor g, int w, int h, int bar, int color, float strength, boolean right) {
        if (strength <= 0.01f) return;
        int a = Math.round(Mth.clamp(strength, 0, 1) * 150);
        int edge = w / 6;
        // A horizontal fade built from strips (fillGradient runs top to bottom).
        for (int i = 0; i < 12; i++) {
            int alpha = Math.round(a * (1 - i / 12f));
            int col = (color & 0xFFFFFF) | (alpha << 24);
            int x0 = right ? w - edge * (i + 1) / 12 : edge * i / 12;
            int x1 = right ? w - edge * i / 12 : edge * (i + 1) / 12;
            g.fill(x0, bar, x1, h - bar, col);
        }
    }

    private static int streakColor(int streak) {
        return streak >= 10 ? 0xFFFFD84A : streak >= 5 ? 0xFFFFA24A : 0xFFE0E8F0;
    }

    private static int brighten(int c, float f) {
        return lerpColor(c | 0xFF000000, 0xFFFFFFFF, f);
    }

    private static int dim(int c) {
        return lerpColor(c | 0xFF000000, 0xFF000000, 0.45f);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, aa = a >>> 24;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255, ba = b >>> 24;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8
                | Math.round(ab + (bb - ab) * t);
    }
}
