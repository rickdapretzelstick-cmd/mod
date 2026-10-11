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
 *
 * <p>A strength contest draws its own: the tug-of-war meter across the top and, low in the middle, the one key to press
 * now (W, A, S or D: the player's movement keys), popping as each right key lands, shaking red and greyed while a wrong
 * one locks the keys, with the run of right keys beside it.
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
        if (TrainingClient.contest()) {
            contest(g, font, a, w, h);
            return;
        }
        double t = a.clock;
        // About 120 px per circle on a 1080p screen at its usual GUI scale, scaling with the GUI scale.
        int r = 15, gap = 30;
        int cy = h - 34 - r;
        int[] cx = {w / 2 - 2 * r - gap, w / 2, w / 2 + 2 * r + gap};
        boolean live = a.state == TrainingNet.ACTIVE;
        boolean shown = a.state == TrainingNet.INTRO || live || a.state == TrainingNet.FAILURE && a.stateSeconds() < 2.2;

        if (a.state == TrainingNet.INTRO) title(g, font, w, h, a.start.title(), "Press each key as its needle crosses the arc", 0xFFFFFFFF);
        boolean seal = TrainingClient.containment();
        if (a.state == TrainingNet.SUCCESS) {
            if (seal) title(g, font, w, h, "ESSENCE SEALED", "A Sealed Grade 1 Essence is yours.", PERFECT);
            else title(g, font, w, h, a.start.practice() ? "PRACTICE COMPLETE" : "TRAINING COMPLETE", "Steady.", PERFECT);
        }
        if (a.state == TrainingNet.FAILURE) title(g, font, w, h, seal ? "✘ IT SLIPPED LOOSE" : "✘ FAILED",
                seal ? "Your seal is unspent. Use it on the remnant again." : a.start.maxMisses() + " mistakes", MISS);
        if (!shown) return;

        // Phase, time left, mistakes: one line under the row.
        int top = cy + r + 18;
        int phase = TrainingSchedule.phase(cfgPhase(a), Math.max(0, t));
        double left = Mth.clamp((a.start.durationMs() - Math.max(0, t)) / 1000.0, 0, a.start.durationMs() / 1000.0);
        String head = a.state == TrainingNet.INTRO ? "GET READY" : "PHASE " + phase + " / 3";
        g.text(font, head, w / 2 - 75 - font.width(head) - 10 - a.start.maxMisses() * 9 - 6, top - 3, 0xFFE0E6F0, true);
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

    /** A strength contest's screen: its meter, the one key to press now, and how it ended. */
    private static void contest(GuiGraphicsExtractor g, Font font, TrainingClient.Attempt a, int w, int h) {
        if (a.state == TrainingNet.INTRO) title(g, font, w, h, a.start.title(), "Press each key as it appears. Keep going until it lets go.", 0xFFFFFFFF);
        if (a.state == TrainingNet.SUCCESS) title(g, font, w, h, "BROKE FREE", "It's wide open: strike now.", PERFECT);
        if (a.state == TrainingNet.FAILURE) title(g, font, w, h, "✘ OVERPOWERED", "It throws you off.", MISS);
        tug(g, font, w, 12);
        if (a.state != TrainingNet.INTRO && a.state != TrainingNet.ACTIVE) return;
        int dir = TrainingClient.contestShown();
        if (dir < 0) return;
        long now = System.nanoTime();
        double sinceRight = (now - TrainingClient.contestRightAt) / 1e6, sinceWrong = (now - TrainingClient.contestWrongAt) / 1e6;
        boolean live = a.state == TrainingNet.ACTIVE, locked = live && a.clock < TrainingClient.contestLockedUntil;
        boolean wrong = TrainingClient.contestWrongAt != 0 && sinceWrong < 320;
        // The key: a pop as each new one appears, a shake while a wrong one has the keys locked.
        float pop = TrainingClient.contestRightAt != 0 && sinceRight < 140 ? 1.35f - (float) (sinceRight / 140) * 0.35f : 1f;
        float shake = wrong ? (float) Math.sin(sinceWrong / 18.0) * 4f * (float) (1 - sinceWrong / 320) : 0f;
        int cx = w / 2, cy = h - 62, half = 17;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx + shake, cy);
        pose.scale(pop);
        int edge = !live ? 0xFF606878 : wrong ? MISS : TrainingClient.contestStreak > 0 && TrainingClient.contestStreak % 8 == 0 ? PERFECT : 0xFFFFFFFF;
        g.fill(-half - 2, -half - 2, half + 2, half + 2, 0xE0000000);
        g.fill(-half - 1, -half - 1, half + 1, -half + 1, edge);
        g.fill(-half - 1, half - 1, half + 1, half + 1, edge);
        g.fill(-half - 1, -half - 1, -half + 1, half + 1, edge);
        g.fill(half - 1, -half - 1, half + 1, half + 1, edge);
        g.fill(-half + 1, -half + 1, half - 1, half - 1, locked ? 0xD0402028 : live ? 0xD0181C28 : 0xD0101218);
        pose.scale(2.4f);
        g.centeredText(font, TrainingClient.moveLabel(dir), 0, -4, locked ? 0xFF8A8F9A : live ? 0xFFFFFFFF : 0xFF9098A8);
        pose.popMatrix();
        String under = !live ? "GET READY" : locked ? "✘ WRONG KEY" : "PRESS";
        g.centeredText(font, under, cx, cy + half + 8, locked ? MISS : 0xFFD8DEE8);
        if (TrainingClient.contestStreak >= 3) g.text(font, "×" + TrainingClient.contestStreak, cx + half + 10, cy - 4,
                TrainingClient.contestStreak % 8 == 0 ? PERFECT : 0xFFFFC36A, true);
    }

    /** A strength contest's tug-of-war meter: 0 (overpowered) on the left, 100 (free) on the right. */
    private static void tug(GuiGraphicsExtractor g, Font font, int w, int y) {
        int bw = 180, bx = w / 2 - bw / 2, bh = 7;
        float m = Mth.clamp(TrainingClient.meter, 0, 100);
        g.fill(bx - 2, y - 2, bx + bw + 2, y + bh + 2, 0xD0000000);
        g.fill(bx, y, bx + bw, y + bh, 0xFF3A1820);
        int fill = Math.round(bw * m / 100f);
        g.fill(bx, y, bx + fill, y + bh, m >= 50 ? 0xFFFFC36A : 0xFFE0506A);
        // The middle, where it started.
        g.fill(bx + bw / 2, y - 3, bx + bw / 2 + 1, y + bh + 3, 0x90FFFFFF);
        g.text(font, TrainingClient.foe.isEmpty() ? "CURSE" : TrainingClient.foe.toUpperCase(java.util.Locale.ROOT), bx - 6 - font.width(TrainingClient.foe.isEmpty() ? "CURSE" : TrainingClient.foe.toUpperCase(java.util.Locale.ROOT)),
                y - 1, 0xFFE0506A, true);
        g.text(font, "YOU", bx + bw + 6, y - 1, 0xFFFFC36A, true);
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
