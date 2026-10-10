package dev.rick.jjk.client.rifle;

import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The Cursed Rifle's screen for whoever has it drawn (from the Cursed Item slot):
 * <ul>
 *   <li><b>The scope</b> ({@link RifleScope}): a precision optic first. The glass is clear at the centre, darkening
 *   toward a machined tube with etched range ticks and four small crimson sigils; outside it is black. The reticle is
 *   drawn in real screen pixels on the exact centre of the screen, which is the view direction every shot is fired
 *   along: thin crosshair lines with heavier outer posts, range dots below, a lit crimson dot at the centre. It never
 *   moves. The array's state shows only round the outside: arcs filling as the lenses charge, locking when ready, a
 *   flash as it fires.</li>
 *   <li>Unscoped, the vanilla crosshair is the aim (no spread to show).</li>
 *   <li>The reserve bar, and the beam's state while the array is out: deploying, the charge filling, READY (let go to
 *   fire), firing, cooling.</li>
 *   <li>The lodge's mounted scope: the same optic, and a faint mark of how long the aim has been held on what's out there.</li>
 * </ul>
 */
public final class RifleHud {
    private RifleHud() {}

    static final net.minecraft.resources.Identifier OPTIC = dev.rick.jjk.JJK.id("textures/gui/rifle_scope.png");

    /** Whether the vanilla crosshair should stand down (the scope's own reticle is up). */
    public static boolean hidesCrosshair() {
        return RifleClient.scopeOn() || RifleScope.overlay() > 0.35f;
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (RifleClient.scopeOn()) {
            optic(g, mc, w, h, 1f, null, time);
            float k = RifleClient.scopeProgress();
            if (k > 0 && !RifleClient.scopeRevealed()) {
                int bw = 60, filled = Math.round(bw * k);
                g.fill(w / 2 - bw / 2, h / 2 + 30, w / 2 + bw / 2, h / 2 + 32, 0x60000000);
                g.fill(w / 2 - bw / 2, h / 2 + 30, w / 2 - bw / 2 + filled, h / 2 + 32, 0xA0B04040);
            }
            return;
        }
        if (!dev.rick.jjk.client.gear.CursedGear.rifleDrawn(mc.player)) return;
        RifleClient.State s = RifleClient.orIdle(mc.player.getId());
        Font font = mc.font;
        float a = RifleScope.overlay();
        if (a > 0.001f) optic(g, mc, w, h, a, s, time);
        // The reserve.
        int bw = 82, x = w / 2 + 96, y = h - 22;
        float e = s.capacity <= 0 ? 0 : Mth.clamp(s.energy / s.capacity, 0, 1);
        g.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xA0000000);
        g.fill(x, y, x + Math.round(bw * e), y + 3, 0xFF8C7BFF);
        g.text(font, "RESERVE", x, y - 10, 0xFFB8B0E0, false);
        String beam = switch (s.phase) {
            case DEPLOY -> "ARRAY OPENING";
            case CHARGE -> "CHARGING " + Math.round(100 * s.progress(time)) + "%";
            case READY -> "READY - RELEASE TO FIRE";
            case FIRE -> s.output >= 0.99f ? "MAXIMUM OUTPUT" : "FIRING";
            case COOLDOWN -> "VENTING";
            case RETRACT -> "ARRAY CLOSING";
            default -> null;
        };
        if (beam != null) {
            int tw = font.width(beam);
            int col = s.phase == RifleServer.Phase.READY ? (((int) time / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFF9FDCFF) : 0xFFB8D8FF;
            g.text(font, beam, w / 2 - tw / 2, h / 2 + 22, col, true);
            if (s.phase == RifleServer.Phase.CHARGE || s.phase == RifleServer.Phase.DEPLOY) {
                int cw = 70;
                float k = s.phase == RifleServer.Phase.DEPLOY ? 0 : s.progress(time);
                g.fill(w / 2 - cw / 2, h / 2 + 34, w / 2 + cw / 2, h / 2 + 36, 0x80000000);
                g.fill(w / 2 - cw / 2, h / 2 + 34, w / 2 - cw / 2 + Math.round(cw * k), h / 2 + 36, 0xFF9FDCFF);
            }
        }
    }

    /**
     * The optic at opacity {@code alpha}: the overlay texture centred (black either side of it), then the reticle and,
     * for the rifle's own scope, the array's state round the outside.
     */
    private static void optic(GuiGraphicsExtractor g, Minecraft mc, int w, int h, float alpha, RifleClient.State s, float time) {
        int al = Math.round(255 * alpha) << 24;
        // As large as the screen is tall: a centred square, with black filling out the sides.
        int size = h, x0 = (w - size) / 2;
        int black = al | 0x040306;
        g.fill(0, 0, x0, h, black);
        g.fill(x0 + size, 0, w, h, black);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, OPTIC, x0, 0, 0, 0, size, size, 512, 512, 512, 512, al | 0xFFFFFF);

        // The reticle, in real pixels on the screen's exact centre (between pixels on an even screen: the dot spans it).
        float scale = mc.getWindow().getGuiScale();
        int W = mc.getWindow().getWidth(), H = mc.getWindow().getHeight();
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1 / scale, 1 / scale);
        float cx = W / 2f, cy = H / 2f;
        int lens = Math.round(H * 0.5f * 0.86f);
        int line = al | 0x0C0C10, post = al | 0x08080C, glow = Math.round(70 * alpha) << 24 | 0xFF2850;
        int t1 = Math.max(1, Math.round(scale * 0.5f)), t3 = Math.max(2, Math.round(scale * 1.4f));
        int gap = Math.round(lens * 0.05f), inner = Math.round(lens * 0.55f), outer = Math.round(lens * 0.98f);
        hline(g, cx, cy, gap, inner, t1, line);
        vline(g, cx, cy, gap, inner, t1, line);
        hline(g, cx, cy, inner, outer, t3, post);
        vline(g, cx, cy, inner, outer, t3, post);
        // Range marks below the centre (holdover), shorter as they go down.
        for (int i = 1; i <= 4; i++) {
            float y = cy + lens * 0.11f * i;
            int half = Math.round(lens * (0.035f - 0.004f * i));
            rect(g, cx - half, y - t1 / 2f, cx + half, y + t1 / 2f, line);
        }
        // The centre: a soft glow, then the lit dot.
        int d = Math.max(2, Math.round(scale));
        rect(g, cx - d * 2, cy - d * 2, cx + d * 2, cy + d * 2, glow);
        rect(g, cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f, al | 0xFF3050);

        if (s != null) arrayState(g, s, cx, cy, lens, alpha, time, scale);
        // A few motes of cursed energy drifting round the outer rim only.
        for (int i = 0; i < 10; i++) {
            double ang = time * 0.02 * (i % 2 == 0 ? 1 : -1) + i * 0.628;
            float rr = lens * (1.04f + 0.03f * (float) Math.sin(time * 0.07 + i));
            float mx = cx + (float) Math.cos(ang) * rr, my = cy + (float) Math.sin(ang) * rr;
            int ma = Math.round(alpha * (60 + 50 * (float) Math.sin(time * 0.11 + i * 1.7)));
            int m = Math.max(1, Math.round(scale * 0.8f));
            rect(g, mx - m, my - m, mx + m, my + m, Math.max(0, ma) << 24 | 0x9A3DFF);
        }
        pose.popMatrix();
    }

    /** The array's charge, ready lock and firing flash, round the outside of the glass only. */
    private static void arrayState(GuiGraphicsExtractor g, RifleClient.State s, float cx, float cy, int lens, float alpha, float time, float scale) {
        float k = switch (s.phase) {
            case CHARGE -> s.progress(time);
            case READY, FIRE -> 1f;
            default -> 0f;
        };
        if (k <= 0) return;
        boolean ready = s.phase == RifleServer.Phase.READY || s.phase == RifleServer.Phase.FIRE;
        float pulse = ready ? 0.75f + 0.25f * (float) Math.sin(time * 0.6f) : 1f;
        int col = ready ? 0xFFB070 : 0xFF6A3A;
        int ca = Math.round(220 * alpha * pulse) << 24;
        int dot = Math.max(1, Math.round(scale * 0.9f));
        // Four arcs, one per lens, each filling clockwise from its sigil.
        float r = lens * 0.93f;
        for (int q = 0; q < 4; q++) {
            double from = q * Math.PI / 2 - Math.PI / 2 + 0.18, span = (Math.PI / 2 - 0.36) * k;
            int steps = Math.max(6, (int) (span * r / (dot * 2.2)));
            for (int i = 0; i <= steps; i++) {
                double ang = from + span * i / steps;
                float x = cx + (float) Math.cos(ang) * r, y = cy + (float) Math.sin(ang) * r;
                rect(g, x - dot, y - dot, x + dot, y + dot, ca | col);
            }
        }
        if (s.phase == RifleServer.Phase.FIRE) {
            float f = Math.max(0, 1 - (time - s.since) / 6f);
            if (f > 0) {
                int fa = Math.round(150 * f * alpha) << 24;
                float rr = lens * (0.93f + 0.06f * (1 - f));
                for (int i = 0; i < 96; i++) {
                    double ang = i * Math.PI * 2 / 96;
                    float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
                    rect(g, x - dot, y - dot, x + dot, y + dot, fa | 0xFFFFFF);
                }
            }
        }
    }

    private static void hline(GuiGraphicsExtractor g, float cx, float cy, int from, int to, int t, int col) {
        rect(g, cx - to, cy - t / 2f, cx - from, cy + t / 2f, col);
        rect(g, cx + from, cy - t / 2f, cx + to, cy + t / 2f, col);
    }

    private static void vline(GuiGraphicsExtractor g, float cx, float cy, int from, int to, int t, int col) {
        rect(g, cx - t / 2f, cy - to, cx + t / 2f, cy - from, col);
        rect(g, cx - t / 2f, cy + from, cx + t / 2f, cy + to, col);
    }

    /** A filled rectangle in (scaled) pixels, rounded to whole pixels around its true centre. */
    private static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int col) {
        int a = Math.round(x0), b = Math.round(y0);
        g.fill(a, b, Math.max(a + 1, Math.round(x1)), Math.max(b + 1, Math.round(y1)), col);
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int colour) {
        int n = Math.max(24, r);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
