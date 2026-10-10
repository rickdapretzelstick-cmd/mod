package dev.rick.jjk.client.rifle;

import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The Cursed Rifle's screen for whoever has it drawn (from the Cursed Item slot):
 * <ul>
 *   <li>through the scope, a dark scope ring and a reticle that drifts exactly as the server's aim does (where it sits
 *   is where the round goes), settling the longer the aim is held;</li>
 *   <li>a hip shot's wide circle when aiming hasn't settled into the scope;</li>
 *   <li>the reserve bar, and the beam's state while the array is out: deploying, the charge filling, READY (let go to
 *   fire), firing, cooling;</li>
 *   <li>the lodge's mounted scope: the same ring, and a faint mark of how long the aim has been held on what's out there.</li>
 * </ul>
 */
public final class RifleHud {
    private RifleHud() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (RifleClient.scopeOn()) {
            scopeRing(g, w, h, 0, 0, 0xFF000000);
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
        float time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (s.phase == RifleServer.Phase.AIM) {
            float[] drift = RifleClient.drift(mc);
            // Degrees to pixels at the zoomed field of view.
            float fov = mc.options.fov().get() * RifleClient.zoom();
            float px = h / fov;
            int dx = Math.round(drift[0] * px), dy = Math.round(drift[1] * px);
            if (RifleClient.scoped(mc)) {
                scopeRing(g, w, h, 0, 0, 0xF0000000);
                int cx = w / 2 + dx, cy = h / 2 + dy;
                int c = 0xE0E0E8FF;
                g.fill(cx - 14, cy, cx - 3, cy + 1, c);
                g.fill(cx + 3, cy, cx + 14, cy + 1, c);
                g.fill(cx, cy - 14, cx + 1, cy - 3, c);
                g.fill(cx, cy + 3, cx + 1, cy + 14, c);
                g.fill(cx, cy, cx + 1, cy + 1, 0xFFFF6060);
            } else {
                // Not settled into the scope yet: a hip shot goes anywhere in here.
                int r = Math.round(dev.rick.jjk.config.JJKConfig.get().rifle.hipSwayDegrees * px);
                ring(g, w / 2, h / 2, r, 0x80FFFFFF);
            }
        }
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

    /** The scope's view: black outside a circle (drawn as bands), offset by {@code ox, oy}. */
    private static void scopeRing(GuiGraphicsExtractor g, int w, int h, int ox, int oy, int colour) {
        int r = (int) (Math.min(w, h) * 0.46f);
        int cx = w / 2 + ox, cy = h / 2 + oy;
        g.fill(0, 0, w, Math.max(0, cy - r), colour);
        g.fill(0, Math.min(h, cy + r), w, h, colour);
        for (int y = cy - r; y < cy + r; y += 2) {
            int dy = y - cy;
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(0, y, Math.max(0, cx - half), y + 2, colour);
            g.fill(Math.min(w, cx + half), y, w, y + 2, colour);
        }
        ring(g, cx, cy, r, 0xFF101014);
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
