package dev.rick.jjk.client.clash;

import dev.rick.jjk.client.input.InputHandler;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.net.BeamCounterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The beam clash's private screen, drawn only for its two contestants:
 * <ul>
 *   <li>the skill check, a dial of its own: a ring with a GOOD arc somewhere on it and a thinner GREAT arc at the arc's
 *   leading edge, and a needle sweeping round; press the space bar as it crosses;</li>
 *   <li>the tug of war along the top: the two beams' colours meeting where the collision is in the world;</li>
 *   <li>judgement pops, and the opponent's last result, small.</li>
 * </ul>
 * And, for whoever an incoming beam could be answered by, the counter prompt with their Ultimate key.
 */
public final class BeamClashHud {
    private static final int TLB = 0xFFF76BFF, ELD = 0xFF5AB4FF;
    private static final String[] TEXT = {"GREAT!", "GOOD", "MISS"};
    private static final int[] COLOR = {0xFFFFE27A, 0xFFFFFFFF, 0xFFFF5A5A};

    private BeamClashHud() {}

    /** A beam's colour, from its kind ("tlb", "eld") or its display name. */
    public static int colorOf(String kind) {
        return "eld".equals(kind) || kind.contains("DROP") ? ELD : TLB;
    }

    /** A beam's display name, from its kind or the name itself. */
    public static String nameOf(String kind) {
        return switch (kind) {
            case "eld" -> "EVERY LAST DROP";
            case "tlb" -> "TRUE LOVE BEAM";
            default -> kind.toUpperCase(java.util.Locale.ROOT);
        };
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        long now = mc.level.getGameTime();
        BeamCounterPayload p = BeamClashClient.prompt(now);
        if (p != null) prompt(g, font, w, h, now, p);
        BeamClashClient.View v = BeamClashClient.mine();
        if (v == null || v.phase == BeamClashClient.COUNTER) return;
        long ms = System.currentTimeMillis();
        int me = v.local, them = 1 - me;
        String myKind = me == 0 ? v.aKind : v.bKind, theirKind = me == 0 ? v.bKind : v.aKind;
        meter(g, font, v, w, me, colorOf(myKind), colorOf(theirKind), myKind, theirKind);
        if (v.phase == BeamClashClient.INTRO) {
            float age = v.phaseAge;
            float pop = Math.max(1f, 2.6f - age * 0.12f);
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(w / 2f, h * 0.3f);
            pose.scale(pop);
            g.centeredText(font, "BEAM CLASH", 0, -4, 0xFFFFFFFF);
            pose.popMatrix();
            g.centeredText(font, "Press [SPACE] as the needle crosses the zone", w / 2, (int) (h * 0.3f) + 16, 0xFFE0E8F0);
        }
        if (v.phase == BeamClashClient.DUEL || v.phase == BeamClashClient.RESOLVE && v.phaseAge < 8) {
            // Over the crosshair (they can't move or attack while it runs), clear of the cast bar and the hotbar.
            int dy = Math.round(h * 0.42f);
            dial(g, font, w / 2, dy, ms, colorOf(myKind));
            // The opponent's last result, small at the side of their colour.
            if (v.judged[them] >= 0 && ms - v.judgedAt[them] < 600) {
                int j = v.judged[them];
                g.centeredText(font, TEXT[j], w / 2 - 70, dy - 4, (COLOR[j] & 0xFFFFFF) | 0xA0000000);
            }
        }
        if (v.phase == BeamClashClient.RESOLVE && v.phaseAge >= 6) {
            boolean won = v.outcome == (me == 0 ? BeamClashClient.A_WINS : BeamClashClient.B_WINS);
            boolean tie = v.outcome == BeamClashClient.TIE;
            String text = tie ? "STALEMATE" : won ? "BREAKTHROUGH!" : "OVERPOWERED";
            int col = tie ? 0xFFFFFFFF : won ? 0xFFFFE27A : 0xFFFF5A5A;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(w / 2f, h * 0.3f);
            pose.scale(2.2f);
            g.centeredText(font, text, 0, -4, col);
            pose.popMatrix();
        }
    }

    private static void prompt(GuiGraphicsExtractor g, Font font, int w, int h, long now, BeamCounterPayload p) {
        long total = Math.max(1, BeamClashClient.promptUntil() - BeamClashClient.promptAt());
        float left = Mth.clamp((BeamClashClient.promptUntil() - now) / (float) total, 0, 1);
        boolean blink = (now / 3) % 2 == 0;
        int y = (int) (h * 0.62f);
        int col = colorOf(p.answer());
        g.fill(w / 2 - 110, y - 6, w / 2 + 110, y + 34, 0x90000000);
        g.fill(w / 2 - 110, y + 30, w / 2 - 110 + Math.round(220 * left), y + 34, col);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, y + 6);
        pose.scale(1.6f);
        g.centeredText(font, "PRESS [" + InputHandler.keyLabel(AbilitySlot.ULTIMATE) + "]", 0, -4, blink ? 0xFFFFFFFF : col);
        pose.popMatrix();
        g.centeredText(font, "Counter " + nameOf(p.attack()) + " with " + nameOf(p.answer()), w / 2, y + 18, 0xFFE0E8F0);
    }

    /** The tug of war: your colour from the right, theirs from the left, meeting where the beams meet. */
    private static void meter(GuiGraphicsExtractor g, Font font, BeamClashClient.View v, int w, int me, int mine, int theirs, String myKind,
                              String theirKind) {
        int mw = Math.min(w - 40, 320), mh = 9, y = 18;
        int x0 = w / 2 - mw / 2, x1 = x0 + mw;
        float lead = v.lead(me);
        int split = Math.round(w / 2f - lead * mw / 2f);
        split = Mth.clamp(split, x0, x1);
        g.fill(x0 - 2, y - 2, x1 + 2, y + mh + 2, 0xFF05050A);
        g.fillGradient(x0, y, split, y + mh, theirs, dim(theirs));
        g.fillGradient(split, y, x1, y + mh, mine, dim(mine));
        // The collision itself: a hot white front that flares with each push.
        int flare = 2 + Math.round(Math.abs(v.push) * 4 + v.intensity);
        g.fill(split - flare, y - 3, split + flare, y + mh + 3, 0xC0FFFFFF);
        g.fill(split - 1, y - 5, split + 1, y + mh + 5, 0xFFFFFFFF);
        g.text(font, nameOf(theirKind), x0, y - 11, theirs, true);
        String you = nameOf(myKind) + " (YOU)";
        g.text(font, you, x1 - font.width(you), y - 11, mine, true);
        int myPower = me == 0 ? v.powerA : v.powerB, theirPower = me == 0 ? v.powerB : v.powerA;
        g.text(font, String.valueOf(theirPower), x0, y + mh + 4, 0xFFB0B8C8, true);
        String mp = String.valueOf(myPower);
        g.text(font, mp, x1 - font.width(mp), y + mh + 4, 0xFFFFFFFF, true);
    }

    /** The skill-check dial: the ring, its GOOD and GREAT arcs, the needle, and the verdict. */
    private static void dial(GuiGraphicsExtractor g, Font font, int cx, int cy, long ms, int accent) {
        BeamClashClient.Check c = BeamClashClient.check();
        int r = 30;
        Matrix3x2fStack pose = g.pose();
        // Backing disc, then the ring.
        // A dark ring (so the white GOOD arc and the gold GREAT arc read against even a white-hot clash behind it).
        for (int i = 0; i < 90; i++) segment(g, pose, cx, cy, i * 4f, r + 1, 11, 4, 0xB0000000);
        for (int i = 0; i < 90; i++) segment(g, pose, cx, cy, i * 4f, r, 2, 4, c == null ? 0x60707080 : 0xC0606878);
        if (c == null) return;
        // GOOD: a white arc; GREAT: a gold arc at its leading edge.
        for (float a = c.zoneStart; a <= c.zoneStart + c.goodWidth; a += 2f) segment(g, pose, cx, cy, a, r + 1, 5, 3, 0xFFFFFFFF);
        for (float a = c.zoneStart; a <= c.zoneStart + c.greatWidth; a += 1.5f) segment(g, pose, cx, cy, a, r + 2, 7, 3, 0xFFFFD84A);
        float angle = Math.min(380, c.angle(ms));
        int needle = c.predicted < 0 ? 0xFFFF3030 : COLOR[c.predicted];
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(angle));
        g.fill(-1, -(r + 5), 1, -4, needle);
        g.fill(-2, -(r + 5), 2, -(r - 2), needle);
        pose.popMatrix();
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, accent);
        if (c.predicted >= 0) {
            long age = ms - c.doneAt;
            float pop = age < 90 ? 1.5f - age / 90f * 0.4f : 1.1f;
            pose.pushMatrix();
            pose.translate(cx, cy - r - 18);
            pose.scale(pop);
            g.centeredText(font, TEXT[c.predicted], 0, -4, COLOR[c.predicted]);
            pose.popMatrix();
        }
    }

    /** A short block of the ring at {@code angle} degrees clockwise from the top. */
    private static void segment(GuiGraphicsExtractor g, Matrix3x2fStack pose, int cx, int cy, float angle, int radius, int thick, int width, int color) {
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(angle));
        g.fill(-width / 2, -radius - thick / 2, width - width / 2, -radius + (thick + 1) / 2, color);
        pose.popMatrix();
    }

    private static int dim(int c) {
        int r = (c >> 16 & 255) * 55 / 100, gr = (c >> 8 & 255) * 55 / 100, b = (c & 255) * 55 / 100;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }
}
