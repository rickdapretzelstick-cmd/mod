package dev.rick.jjk.client.cinematic;

import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.client.fx.ScreenEffects;
import dev.rick.jjk.core.net.DomainCinematicPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The short presentation of a domain opening. It frames what is happening in the world (the domain physically
 * building itself from the caster's feet) rather than replacing it:
 * <ul>
 *   <li>SOLO (the caster, ~3-4 s): letterbox, the caster's portrait while their energy builds and the domain forms
 *   (camera pulled out to third person so they can watch it), then "DOMAIN EXPANSION / name" as it seals.</li>
 *   <li>OBSERVE (anyone nearby, ~1.5 s): a small banner; nobody else's camera is touched.</li>
 *   <li>VERSUS (a counter, ~3 s): both duellists' portraits, "DOMAIN EXPANSION VS DOMAIN EXPANSION", then it hands the
 *   screen to the domain clash.</li>
 * </ul>
 */
public final class DomainCinematic {
    private record Show(int kind, int[] entities, List<String> names, List<String> domains, int[] colors, long start, int titleAt, int duration,
                        int local, int opponentSide) {}

    @Nullable private static Show show;
    @Nullable private static CameraType restoreCamera;

    private DomainCinematic() {}

    public static void start(DomainCinematicPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int local = -1;
        for (int i = 0; i < p.entities().length; i++) if (p.entities()[i] == mc.player.getId()) local = i;
        int kind = p.kind();
        // Someone else's clash: watchers get the light version.
        if (kind == DomainCinematicPayload.VERSUS && local < 0) kind = DomainCinematicPayload.OBSERVE;
        // Never let a passing banner replace a duellist's own presentation.
        if (kind == DomainCinematicPayload.OBSERVE && show != null && show.kind != DomainCinematicPayload.OBSERVE && active()) return;
        show = new Show(kind, p.entities(), p.names(), p.domains(), p.colors(), mc.level.getGameTime(), p.titleAt(), p.duration(), local,
                opponentSide(mc, p.entities(), local));
        if (kind != DomainCinematicPayload.OBSERVE) {
            // The caster watches their domain build from just behind themselves; restored afterwards.
            if (restoreCamera == null && mc.options.getCameraType().isFirstPerson()) {
                restoreCamera = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            ScreenEffects.fovPunch(-0.06f);
        }
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return show != null && mc.level != null && mc.level.getGameTime() - show.start < show.duration;
    }

    /** A caster's or duellist's presentation is on screen (the regular HUD steps aside). */
    public static boolean fullscreen() {
        return active() && show.kind != DomainCinematicPayload.OBSERVE;
    }

    /** A versus card is on screen (the clash intro waits for it). */
    public static boolean versusShowing() {
        Minecraft mc = Minecraft.getInstance();
        return active() && show.kind == DomainCinematicPayload.VERSUS && mc.level.getGameTime() - show.start < SWEEP;
    }

    public static void tick(Minecraft mc) {
        if (show != null && !active()) end(mc);
        // The versus card hands over to the clash as its countdown starts.
        if (show != null && show.kind == DomainCinematicPayload.VERSUS && ClashClient.view() != null && ClashClient.view().clock() > -22) end(mc);
    }

    private static void end(Minecraft mc) {
        show = null;
        if (restoreCamera != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(restoreCamera);
            restoreCamera = null;
        }
    }

    public static void reset() {
        show = null;
        restoreCamera = null;
    }

    public static void render(GuiGraphicsExtractor g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Show s = show;
        if (s == null || mc.level == null || !active()) return;
        float t = mc.level.getGameTime() - s.start + partial;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        switch (s.kind) {
            case DomainCinematicPayload.SOLO -> solo(g, font, s, t, w, h);
            case DomainCinematicPayload.VERSUS -> versus(g, font, s, t, w, h);
            default -> observe(g, font, s, t, w, h);
        }
    }

    // --- SOLO ---

    private static void solo(GuiGraphicsExtractor g, Font font, Show s, float t, int w, int h) {
        float out = Mth.clamp((s.duration - t) / 6f, 0, 1);
        int color = s.colors[0] | 0xFF000000;
        // Shot 1-2: a cut-in of the caster as their energy builds, then it clears so the domain can be watched forming.
        float slide = ease(Mth.clamp(t / 6f, 0, 1));
        float leave = ease(Mth.clamp((t - 26) / 6f, 0, 1));
        if (t < s.titleAt && leave < 1f) {
            CinematicPanels.Band band = new CinematicPanels.Band(h * 0.3f, h * 0.7f);
            float sx = -(1 - slide) * w + leave * w;
            CinematicPanels.band(g, band, w, color, s.entities[0], w * 0.5f, sx, s.start + t);
            if (slide > 0.95f && leave < 0.05f) {
                CinematicPanels.border(g, band.top(), w, 5);
                CinematicPanels.border(g, band.bottom(), w, 5);
            }
            float txt = Mth.clamp((t - 5) / 3f, 0, 1) * (1 - leave);
            CinematicPanels.label(g, font, "DOMAIN", w * 0.12f + sx, band.topAt(w * 0.12f, w) + 10, 2.2f, 0xFFFFFF, txt, false);
            CinematicPanels.label(g, font, "EXPANSION", w * 0.88f + sx, band.bottomAt(w * 0.88f, w) - 28, 2.2f, 0xFFFFFF, txt, true);
            CinematicPanels.label(g, font, s.names.get(0), w * 0.12f + sx, band.topAt(w * 0.12f, w) + 34, 1f, color, txt, false);
        }
        // Shot 5: the domain has sealed.
        float tt = t - s.titleAt;
        if (tt >= 0) title(g, font, "DOMAIN EXPANSION", s.domains.get(0).toUpperCase(java.util.Locale.ROOT), w / 2, h / 2 - 8, tt, out, color);
    }

    // --- VERSUS ---

    /** Ticks for the two slashes to sweep all the way through. */
    public static final float SWEEP = 34f;

    private static void versus(GuiGraphicsExtractor g, Font font, Show s, float t, int w, int h) {
        int me = Math.max(0, s.local), them = s.entities.length > 1 ? 1 - me : 0;
        float time = s.start + t;
        float p = Mth.clamp(t / SWEEP, 0, 1);
        if (p >= 1) return;
        // Travel along each slash's path: fast in, slowing as they cross in the middle, fast out.
        float u = p * 2 - 1;
        float travel = Math.signum(u) * (float) Math.pow(Math.abs(u), 2.2);
        float alpha = Mth.clamp(p / 0.12f, 0, 1) * Mth.clamp((1 - p) / 0.12f, 0, 1);
        float angle = (float) Math.toRadians(24);
        float reach = w * 0.95f, length = w * 1.1f;
        float thickness = h * 0.34f * (0.85f + 0.25f * (1 - Math.abs(u)));
        int lc = contrast(s.colors, them, me), rc = s.colors[me] | 0xFF000000;
        int themSide = s.opponentSide, meSide = -themSide;
        // Each slash enters from its sorcerer's side, cuts down across the screen through the centre and exits the other side.
        float[] a = path(w, h, themSide, angle, travel * reach);
        float[] b = path(w, h, meSide, angle, travel * reach);
        CinematicPanels.slash(g, a[0], a[1], a[2], length, thickness, lc, alpha, time, s.entities[them], true);
        CinematicPanels.slash(g, b[0], b[1], b[2], length, thickness, rc, alpha, time, s.entities[me], true);
        // Where they cross: the impact.
        float impact = 1 - Math.abs(u) / 0.14f;
        if (impact > 0) {
            int size = Math.round(h * (0.5f + 0.7f * (1 - impact)));
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, dev.rick.jjk.JJK.id("textures/gui/clash_burst.png"), w / 2 - size / 2,
                    h / 2 - size / 2, 0, 0, size, size, 64, 64, 64, 64, CinematicPanels.withAlpha(0xFFFFFFFF, impact));
            Matrix3x2fStack pose = g.pose();
            java.util.Random r = new java.util.Random(s.start);
            for (int i = 0; i < 14; i++) {
                pose.pushMatrix();
                pose.translate(w / 2f, h / 2f);
                pose.rotate(r.nextFloat() * Mth.TWO_PI);
                int len = Math.round(h * (0.3f + 0.4f * r.nextFloat()) * (1.2f - impact));
                g.fill(size / 6, -1, size / 6 + len, 1, CinematicPanels.withAlpha(0xFFFFFFFF, impact));
                pose.popMatrix();
            }
        }
        // Lettering rides with each slash on its sorcerer's own side, so the two sets stay apart as the slashes cross.
        float txt = alpha;
        float ax = a[0] + themSide * thickness * 0.75f, ay = a[1] - thickness * 0.15f;
        float bx = b[0] + meSide * thickness * 0.75f, by = b[1] + thickness * 0.35f;
        boolean aLeft = themSide > 0, bLeft = meSide > 0;
        CinematicPanels.label(g, font, "DOMAIN", ax, ay - 34, 2.4f, 0xFFFFFF, txt, !aLeft);
        CinematicPanels.label(g, font, s.domains.get(them).toUpperCase(java.util.Locale.ROOT), ax, ay - 8, 1.4f, 0xFFFFFF, txt, !aLeft);
        CinematicPanels.label(g, font, s.names.get(them), ax, ay + 8, 1f, lc, txt, !aLeft);
        CinematicPanels.label(g, font, "EXPANSION", bx, by - 34, 2.4f, 0xFFFFFF, txt, !bLeft);
        CinematicPanels.label(g, font, s.domains.get(me).toUpperCase(java.util.Locale.ROOT), bx, by - 8, 1.4f, 0xFFFFFF, txt, !bLeft);
        CinematicPanels.label(g, font, s.names.get(me) + " (YOU)", bx, by + 8, 1f, rc, txt, !bLeft);
        float vs = 1 - Math.abs(u) / 0.35f;
        if (vs > 0) CinematicPanels.label(g, font, "VS", w / 2f - 22, h / 2f - 18, Mth.lerp(vs, 2.5f, 4f), 0xFFFFE08A, Math.min(1, vs * 2), false);
    }

    /** Centre and heading of a slash that enters from {@code side} (-1 left, 1 right) and has travelled {@code dist} past the centre. */
    private static float[] path(int w, int h, int side, float angle, float dist) {
        float dx = -side * Mth.cos(angle), dy = Mth.sin(angle);
        return new float[] {w / 2f + dx * dist, h / 2f + dy * dist, (float) Math.atan2(dy, dx)};
    }

    /**
     * Which side of the screen the opponent is on from where the camera looks (-1 left, 1 right), so each sorcerer's
     * slash comes from their own side. When they are lined up with the camera, the opponent is on the left (matching the
     * clash screen, where your lanes are on the right).
     */
    private static int opponentSide(Minecraft mc, int[] entities, int local) {
        if (local < 0 || entities.length < 2 || mc.level == null) return -1;
        var me = mc.level.getEntity(entities[local]);
        var them = mc.level.getEntity(entities[1 - local]);
        if (me == null || them == null) return -1;
        double yaw = Math.toRadians(mc.gameRenderer.mainCamera().yRot());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double d = (them.getX() - me.getX()) * rx + (them.getZ() - me.getZ()) * rz;
        return Math.abs(d) < 2 ? -1 : d > 0 ? 1 : -1;
    }

    private static float ease(float x) {
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    // --- OBSERVE ---

    private static void observe(GuiGraphicsExtractor g, Font font, Show s, float t, int w, int h) {
        float alpha = Math.min(1, t / 4f) * Mth.clamp((s.duration - t) / 8f, 0, 1);
        if (alpha <= 0.01f) return;
        int a = Math.round(alpha * 255) << 24;
        int bw = Math.min(w - 20, 240), x0 = w / 2 - bw / 2, y0 = 26;
        g.fill(x0, y0, x0 + bw, y0 + 30, (Math.round(alpha * 170) << 24));
        g.fill(x0, y0, x0 + 3, y0 + 30, (s.colors[0] & 0xFFFFFF) | a);
        if (alpha > 0.6f) Portraits.draw(g, s.entities[0], x0 + 6, y0 + 3, x0 + 26, y0 + 27, 1.7f, (s.colors[0] & 0xFFFFFF) | a);
        String who = s.entities.length > 1 ? s.names.get(0) + " vs " + s.names.get(1) : s.names.get(0);
        String what = s.entities.length > 1 ? "DOMAIN CLASH" : "DOMAIN EXPANSION: " + s.domains.get(0).toUpperCase(java.util.Locale.ROOT);
        g.text(font, what, x0 + 32, y0 + 5, (s.colors[0] & 0xFFFFFF) | a, true);
        g.text(font, who, x0 + 32, y0 + 17, 0xE0E8F0 | a, true);
    }

    // --- pieces ---

    private static void title(GuiGraphicsExtractor g, Font font, String small, String big, int cx, int cy, float tt, float out, int color) {
        float in = Mth.clamp(tt / 3f, 0, 1);
        int a = Math.round(in * out * 255) << 24;
        if (a == 0) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy - 16);
        pose.scale(1.6f);
        g.centeredText(font, small, 0, -4, (color & 0xFFFFFF) | a);
        pose.popMatrix();
        pose.pushMatrix();
        pose.translate(cx, cy + 6);
        pose.scale(Mth.lerp(in, 5.2f, 3.6f));
        g.centeredText(font, big, 0, -4, 0xFFFFFF | a);
        pose.popMatrix();
        g.fill(cx - 90, cy + 24, cx + 90, cy + 25, (color & 0xFFFFFF) | a);
    }

    /** The opponent's colour, made distinct when both sides share one (a mirror match). */
    private static int contrast(int[] colors, int them, int me) {
        int c = colors[them] | 0xFF000000;
        return (c & 0xFFFFFF) == (colors[me] & 0xFFFFFF) ? 0xFFFF5A7A : c;
    }
}
