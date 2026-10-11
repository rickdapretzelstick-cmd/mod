package dev.rick.jjk.client.training;

import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The training's screen, fixed in screen space (it never moves with the camera): a row of three circular gauges in the
 * lower middle (left, centre, right: the same keys for every character), each with its key inside, its name under it,
 * its needle and, when a check is cued, its arcs (a wide GOOD arc with a narrow PERFECT core; a hold shows a HOLD arc
 * and a separate RELEASE arc). Above the row: the phase, the time left and the five mistake marks. Every state reads
 * by a symbol and a word as well as its colour.
 */
public final class TrainingHud {
    private static final int GOOD = 0xFFFFFFFF, PERFECT = 0xFFFFD84A, MISS = 0xFFFF5A5A;

    private TrainingHud() {}

    public static void render(GuiGraphicsExtractor g) {
        TrainingClient.Attempt a = TrainingClient.attempt();
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.player == null) return;
        TrainingClient.advance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        double t = a.clock;
        // About 120 px per circle on a 1080p screen at its usual GUI scale, scaling with the GUI scale.
        int r = 15, gap = 30;
        int cy = h - 40 - r;
        int[] cx = {w / 2 - 2 * r - gap, w / 2, w / 2 + 2 * r + gap};
        boolean live = a.state == TrainingNet.ACTIVE;
        boolean shown = a.state == TrainingNet.INTRO || live || a.state == TrainingNet.FAILURE && a.stateSeconds() < 2.2;

        if (a.state == TrainingNet.INTRO) title(g, font, w, h, a.start.title(), "Press each key as its needle crosses the arc", 0xFFFFFFFF);
        if (a.state == TrainingNet.SUCCESS) title(g, font, w, h, a.start.practice() ? "PRACTICE COMPLETE" : "TRAINING COMPLETE", "Steady.", PERFECT);
        if (a.state == TrainingNet.FAILURE) title(g, font, w, h, "✘ FAILED", a.start.maxMisses() + " mistakes", MISS);
        if (!shown) return;

        // Phase, time left, mistakes.
        int top = cy - r - 30;
        int phase = TrainingSchedule.phase(cfgPhase(a), Math.max(0, t));
        double left = Mth.clamp((a.start.durationMs() - Math.max(0, t)) / 1000.0, 0, a.start.durationMs() / 1000.0);
        String head = a.state == TrainingNet.INTRO ? "GET READY" : "PHASE " + phase + " / 3";
        g.centeredText(font, head, w / 2, top - 11, 0xFFE0E6F0);
        int bw = 150, bx = w / 2 - bw / 2;
        g.fill(bx - 1, top - 1, bx + bw + 1, top + 4, 0xC0000000);
        g.fill(bx, top, bx + (int) Math.round(bw * left / (a.start.durationMs() / 1000.0)), top + 3, 0xFFB8C8E0);
        g.text(font, String.format(java.util.Locale.ROOT, "%.1fs", left), bx + bw + 6, top - 3, 0xFFFFFFFF, true);
        int max = a.start.maxMisses();
        int pipsX = bx - 8 - max * 9;
        for (int i = 0; i < max; i++) {
            boolean used = i < a.misses;
            g.text(font, used ? "✘" : "○", pipsX + i * 9, top - 3, used ? MISS : 0xFF9098A8, true);
        }

        for (int gauge = 0; gauge < 3; gauge++) gauge(g, font, a, gauge, cx[gauge], cy, r, t, live);
    }

    private static dev.rick.jjk.config.JJKConfig.Training cfgPhase(TrainingClient.Attempt a) {
        dev.rick.jjk.config.JJKConfig.Training c = new dev.rick.jjk.config.JJKConfig.Training();
        c.phase2AtMs = a.start.phase2AtMs();
        c.phase3AtMs = a.start.phase3AtMs();
        return c;
    }

    private static void title(GuiGraphicsExtractor g, Font font, int w, int h, String title, String sub, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h * 0.16f);
        pose.scale(2.2f);
        g.centeredText(font, title, 0, -4, color);
        pose.popMatrix();
        g.centeredText(font, sub, w / 2, (int) (h * 0.16f) + 14, 0xFFD8DEE8);
    }

    private static void gauge(GuiGraphicsExtractor g, Font font, TrainingClient.Attempt a, int gauge, int cx, int cy, int r, double t, boolean live) {
        Matrix3x2fStack pose = g.pose();
        int accent = gauge < a.start.colors().size() ? a.start.colors().get(gauge) : 0xFFFFFFFF;
        List<TrainingCheck> checks = a.checks();
        TrainingCheck c = live ? current(a, gauge, t) : null;
        // Backing and ring.
        for (int i = 0; i < 72; i++) segment(g, pose, cx, cy, i * 5f, r + 1, 9, 3, 0xA8000000);
        for (int i = 0; i < 72; i++) segment(g, pose, cx, cy, i * 5f, r, 2, 3, c != null ? (accent & 0xFFFFFF) | 0xA0000000 : 0x90707888);
        String tag = null;
        int tagColor = 0xFFFFFFFF;
        if (c != null) {
            int res = a.eval.result(c.id());
            boolean holding = res == TrainingCheck.HOLDING;
            if (!holding) arc(g, pose, cx, cy, r, checks, c, c.atMs(), GOOD, PERFECT);
            if (c.hold()) {
                arc(g, pose, cx, cy, r, checks, c, c.releaseMs(), accent, PERFECT);
                tag = holding ? "RELEASE" : "HOLD";
                tagColor = holding ? accent : 0xFFFFFFFF;
            }
            if (c.mod() == TrainingCheck.FAST) {
                tag = tag == null ? "FASTER ▲" : tag;
                tagColor = 0xFFFF9A6A;
            } else if (c.mod() == TrainingCheck.SLOW) {
                tag = tag == null ? "SLOWER ▼" : tag;
                tagColor = 0xFF8AB4FF;
            } else if (c.mod() == TrainingCheck.ALIGNED) {
                tag = tag == null ? "TOGETHER" : tag;
                tagColor = PERFECT;
            } else if (c.mod() == TrainingCheck.FINALE && tag == null) {
                tag = "FINAL";
            }
        }
        // The needle: always moving; between checks it only travels (pressing then does nothing).
        double angle = TrainingSchedule.angle(checks, gauge, t);
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(angle));
        g.fill(-1, -(r + 4), 1, -5, c != null ? 0xFFFFFFFF : 0xFFB0B8C8);
        pose.popMatrix();
        // The key inside (filled while held).
        boolean down = a.down[gauge];
        g.fill(cx - 7, cy - 6, cx + 7, cy + 6, down ? (accent & 0xFFFFFF) | 0xE0000000 : 0xC0101018);
        g.centeredText(font, TrainingClient.keyLabel(gauge), cx, cy - 4, down ? 0xFF101010 : 0xFFFFFFFF);
        // Its name under it.
        String name = gauge < a.start.labels().size() ? a.start.labels().get(gauge) : "";
        g.centeredText(font, name, cx, cy + r + 5, accent);
        // A cue or a verdict above it.
        double since = t - a.lastAt[gauge];
        int verdict = a.lastVerdict[gauge];
        if (verdict >= 0 && verdict != TrainingCheck.HOLDING && since >= 0 && since < 550) {
            String text = verdict == TrainingCheck.PERFECT ? "★ PERFECT" : verdict == TrainingCheck.GOOD ? "✔ GOOD" : "✘ MISS";
            int col = verdict == TrainingCheck.PERFECT ? PERFECT : verdict == TrainingCheck.GOOD ? GOOD : MISS;
            float pop = since < 90 ? 1.35f - (float) since / 90f * 0.3f : 1.05f;
            pose.pushMatrix();
            pose.translate(cx, cy - r - 12);
            pose.scale(pop);
            g.centeredText(font, text, 0, -4, col);
            pose.popMatrix();
        } else if (tag != null) {
            g.centeredText(font, tag, cx, cy - r - 13, tagColor);
        }
    }

    /** The check a gauge is showing: undecided (or held), cued, and not long over. */
    private static TrainingCheck current(TrainingClient.Attempt a, int gauge, double t) {
        for (TrainingCheck c : a.checks()) {
            if (c.gauge() != gauge) continue;
            int res = a.eval.result(c.id());
            if (res >= 0 && res != TrainingCheck.HOLDING) continue;
            if (c.cueMs() > t) return null;
            if (t > c.endMs() + 250) continue;
            return c;
        }
        return null;
    }

    /** A window's arc: GOOD wide, PERFECT narrow at its centre, where the needle will be at {@code centreMs}. */
    private static void arc(GuiGraphicsExtractor g, Matrix3x2fStack pose, int cx, int cy, int r, List<TrainingCheck> checks, TrainingCheck c, int centreMs,
                            int good, int perfect) {
        double mid = TrainingSchedule.angle(checks, c.gauge(), centreMs);
        double gw = TrainingSchedule.arcDegrees(c, c.goodMs()), pw = TrainingSchedule.arcDegrees(c, c.perfectMs());
        for (double d = -gw / 2; d <= gw / 2; d += 2) segment(g, pose, cx, cy, (float) (mid + d), r + 1, 5, 3, good);
        for (double d = -pw / 2; d <= pw / 2; d += 1.5) segment(g, pose, cx, cy, (float) (mid + d), r + 2, 7, 3, perfect);
    }

    private static void segment(GuiGraphicsExtractor g, Matrix3x2fStack pose, int cx, int cy, float angle, int radius, int thick, int width, int color) {
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(angle));
        g.fill(-width / 2, -radius - thick / 2, width - width / 2, -radius + (thick + 1) / 2, color);
        pose.popMatrix();
    }
}
