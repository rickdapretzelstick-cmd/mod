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
                        int local) {}

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
        show = new Show(kind, p.entities(), p.names(), p.domains(), p.colors(), mc.level.getGameTime(), p.titleAt(), p.duration(), local);
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
        return active() && show.kind == DomainCinematicPayload.VERSUS;
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

    private static void versus(GuiGraphicsExtractor g, Font font, Show s, float t, int w, int h) {
        float out = Mth.clamp((s.duration - t) / 6f, 0, 1);
        int me = Math.max(0, s.local), them = s.entities.length > 1 ? 1 - me : 0;
        float time = s.start + t;
        // Two cut-in bands: the opponent's slams in from the right on top, yours from the left underneath.
        float slide = ease(Mth.clamp(t / 6f, 0, 1)), slideOut = ease(1 - out);
        CinematicPanels.Band top = new CinematicPanels.Band(h * 0.12f, h * 0.5f), bottom = new CinematicPanels.Band(h * 0.5f, h * 0.88f);
        int lc = contrast(s.colors, them, me), rc = s.colors[me] | 0xFF000000;
        float topSlide = (1 - slide) * w - slideOut * w, bottomSlide = -(1 - slide) * w + slideOut * w;
        CinematicPanels.band(g, top, w, lc, s.entities[them], w * 0.6f, topSlide, time);
        CinematicPanels.band(g, bottom, w, rc, s.entities[me], w * 0.42f, bottomSlide, time);
        if (slide > 0.95f && out > 0.5f) {
            CinematicPanels.border(g, top.top(), w, 5);
            CinematicPanels.border(g, top.bottom(), w, 6);
            CinematicPanels.border(g, bottom.bottom(), w, 5);
        }
        float txt = Mth.clamp((t - 6) / 3f, 0, 1) * out;
        float mid = h * 0.5f;
        // Who is who, in each band's corner.
        CinematicPanels.label(g, font, s.domains.get(them).toUpperCase(java.util.Locale.ROOT), w * 0.05f + topSlide, top.topAt(w * 0.05f, w) + 12, 1.6f,
                0xFFFFFF, txt, false);
        CinematicPanels.label(g, font, s.names.get(them), w * 0.05f + topSlide, top.topAt(w * 0.05f, w) + 30, 1f, lc, txt, false);
        CinematicPanels.label(g, font, s.domains.get(me).toUpperCase(java.util.Locale.ROOT), w * 0.95f + bottomSlide, bottom.bottomAt(w * 0.95f, w) - 40, 1.6f,
                0xFFFFFF, txt, true);
        CinematicPanels.label(g, font, s.names.get(me) + " (YOU)", w * 0.95f + bottomSlide, bottom.bottomAt(w * 0.95f, w) - 22, 1f, rc, txt, true);
        // The big words, straddling the seams like the anime title cards.
        CinematicPanels.label(g, font, "DOMAIN", w * 0.26f, top.bottomAt(w * 0.26f, w) - 26, 2.4f, 0xFFFFFF, txt, false);
        CinematicPanels.label(g, font, "EXPANSION", w * 0.8f, bottom.bottomAt(w * 0.8f, w) - 12, 2.4f, 0xFFFFFF, txt, true);
        float vs = Mth.clamp((t - 7) / 3f, 0, 1) * out;
        if (vs > 0) {
            float vx = w * 0.64f;
            CinematicPanels.label(g, font, "VS", vx, top.bottomAt(vx, w) - 12 * Mth.lerp(vs, 5f, 3f) / 3f, Mth.lerp(vs, 5f, 3f), 0xFFFFE08A, vs, false);
        }
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
