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
 *   (camera pulled out to third person so they can watch it), then "DOMAIN EXPANSION / name" as it seals. Malevolent
 *   Shrine has no title: the camera turns to face the caster as it seals, so they watch the shrine rise behind them.</li>
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
    /** Malevolent Shrine's reveal: the camera the caster had before it turned to face them. */
    @Nullable private static CameraType turnedFrom;

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
        // Malevolent Shrine's caster keeps the shot until its colour has flooded in.
        int duration = kind == DomainCinematicPayload.SOLO && shrine(p.domains())
                ? Math.max(p.duration(), p.titleAt() + dev.rick.jjk.client.render.ShrineDomainRenderer.REVEAL) : p.duration();
        show = new Show(kind, p.entities(), p.names(), p.domains(), p.colors(), mc.level.getGameTime(), p.titleAt(), duration, local,
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
        return active() && show.kind == DomainCinematicPayload.VERSUS;
    }

    public static void tick(Minecraft mc) {
        if (show != null && !active()) end(mc);
        // As Malevolent Shrine seals the camera turns to face the caster: the shrine rises behind them (JJS GIF).
        if (show != null && show.kind == DomainCinematicPayload.SOLO && show.local == 0 && turnedFrom == null && shrine(show.domains)
                && mc.level.getGameTime() - show.start >= show.titleAt && !ClashClient.playing()
                && !dev.rick.jjk.client.clash.BeamClashCamera.active()) {
            turnedFrom = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        // The versus card hands over to the clash as its countdown starts: the bands slide out, finishing as it begins.
        if (show != null && show.kind == DomainCinematicPayload.VERSUS && ClashClient.view() != null && ClashClient.view().clock() > -22 - MOVE) {
            int leave = (int) (mc.level.getGameTime() - show.start + MOVE);
            if (leave < show.duration) {
                show = new Show(show.kind, show.entities, show.names, show.domains, show.colors, show.start, show.titleAt, leave, show.local,
                        show.opponentSide);
            }
        }
    }

    private static void end(Minecraft mc) {
        show = null;
        CameraType now = mc.options.getCameraType();
        if (turnedFrom != null) {
            if (now == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(restoreCamera != null ? restoreCamera : turnedFrom);
        } else if (restoreCamera != null && now == CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(restoreCamera);
        }
        restoreCamera = null;
        turnedFrom = null;
    }

    public static void reset() {
        show = null;
        restoreCamera = null;
        turnedFrom = null;
    }

    private static boolean shrine(List<String> domains) {
        return !domains.isEmpty() && dev.rick.jjk.yuji.MalevolentShrine.INSTANCE.displayName().equals(domains.get(0));
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
        if (dev.rick.jjk.hakari.IdleDeathGamble.INSTANCE.displayName().equals(s.domains.get(0))) {
            soloGamble(g, font, s, t, w, h);
            return;
        }
        float out = Mth.clamp((s.duration - t) / 6f, 0, 1);
        int color = s.colors[0] | 0xFF000000;
        // Shot 1-2: a cut-in of the caster as their energy builds, then it clears so the domain can be watched forming.
        float slide = ease(Mth.clamp(t / 6f, 0, 1));
        float leave = ease(Mth.clamp((t - 26) / 6f, 0, 1));
        if (t < s.titleAt && leave < 1f) {
            CinematicPanels.Band band = new CinematicPanels.Band(h * 0.3f, h * 0.7f);
            float sx = -(1 - slide) * w + leave * w;
            boolean shrine = dev.rick.jjk.yuji.MalevolentShrine.INSTANCE.displayName().equals(s.domains.get(0));
            if (shrine) CinematicPanels.shrineBand(g, band, w, s.entities[0], w * 0.5f, sx, s.start + t);
            else CinematicPanels.band(g, band, w, color, s.entities[0], w * 0.5f, sx, s.start + t);
            if (slide > 0.95f && leave < 0.05f) {
                CinematicPanels.border(g, band.top(), w, 5);
                CinematicPanels.border(g, band.bottom(), w, 5);
            }
            float txt = Mth.clamp((t - 5) / 3f, 0, 1) * (1 - leave);
            CinematicPanels.label(g, font, "DOMAIN", w * 0.12f + sx, band.topAt(w * 0.12f, w) + 10, 2.2f, 0xFFFFFF, txt, false);
            CinematicPanels.label(g, font, "EXPANSION", w * 0.88f + sx, band.bottomAt(w * 0.88f, w) - 28, 2.2f, 0xFFFFFF, txt, true);
            CinematicPanels.label(g, font, s.names.get(0), w * 0.12f + sx, band.topAt(w * 0.12f, w) + 34, 1f, color, txt, false);
        }
        // Malevolent Shrine opens on a white flash (JJS GIF frame 0).
        if (dev.rick.jjk.yuji.MalevolentShrine.INSTANCE.displayName().equals(s.domains.get(0)) && t < 5) {
            g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, 0.85f * (1 - t / 5f)));
        }
        // Shot 5: the domain has sealed (Malevolent Shrine has no title card: the shrine itself appears).
        float tt = t - s.titleAt;
        if (tt >= 0 && !dev.rick.jjk.yuji.MalevolentShrine.INSTANCE.displayName().equals(s.domains.get(0))) title(g, font, "DOMAIN EXPANSION", s.domains.get(0).toUpperCase(java.util.Locale.ROOT), w / 2, h / 2 - 8, tt, out, color);
    }

    /**
     * Idle Death Gamble's opening, after Jujutsu Shenanigans: a white slash cuts across the screen and opens into a teal
     * band patterned with double helices, the caster's close-up in it and "DOMAIN" / "EXPANSION" on its edges; it closes
     * again so the flood of white can be watched spreading from their feet, and the screen goes white as the domain seals
     * (the rush of trains inside takes over from there, see {@code IdgOpening}). No title card.
     */
    private static void soloGamble(GuiGraphicsExtractor g, Font font, Show s, float t, int w, int h) {
        float center = h * 0.5f, half = h * 0.17f;
        float faceX = w * 0.44f;
        // The slash (0-4), opening (4-9), holding, then closing (24-29).
        float open = ease(Mth.clamp((t - 4) / 5f, 0, 1)) * (1 - easeIn(Mth.clamp((t - 24) / 5f, 0, 1)));
        float slash = Mth.clamp(t / 4f, 0, 1);
        if (t < 30) {
            if (t < 4) {
                // Two thin white lines racing in from opposite sides.
                float reach = w * (0.2f + 0.8f * ease(slash));
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.mul(new org.joml.Matrix3x2f(1, CinematicPanels.HELIX_SLOPE, 0, 1, 0, -CinematicPanels.HELIX_SLOPE * w / 2f));
                g.fill(-60, Math.round(center - 3), Math.round(-60 + reach + 60), Math.round(center - 1), 0xFFFFFFFF);
                g.fill(Math.round(w + 60 - reach - 60), Math.round(center + 1), w + 60, Math.round(center + 3), 0xFFFFFFFF);
                pose.popMatrix();
            } else {
                CinematicPanels.helixBand(g, w, center, half, open, s.entities[0], faceX, s.start + t);
            }
            float txt = Mth.clamp((t - 7) / 3f, 0, 1) * (1 - Mth.clamp((t - 21) / 4f, 0, 1));
            float topY = CinematicPanels.helixEdge(center - half * open, w * 0.33f, w);
            float botY = CinematicPanels.helixEdge(center + half * open, w * 0.7f, w);
            CinematicPanels.label(g, font, "DOMAIN", w * 0.24f, topY - 20, 1.8f, 0xFFFFFF, txt, false);
            CinematicPanels.label(g, font, "EXPANSION", w * 0.58f, botY - 22, 1.8f, 0xFFFFFF, txt, false);
        }
        // As it seals, everything floods white.
        float white = Mth.clamp((t - (s.titleAt - 12)) / 10f, 0, 1) * (1 - Mth.clamp((t - s.titleAt - 2) / 3f, 0, 1));
        if (white > 0) g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, white));
    }

    // --- VERSUS ---

    /** Ticks for each band to slide in (and, at the end, to slide out). */
    private static final float MOVE = 8f;

    private static void versus(GuiGraphicsExtractor g, Font font, Show s, float t, int w, int h) {
        float out = Mth.clamp((s.duration - t) / MOVE, 0, 1);
        int me = Math.max(0, s.local), them = s.entities.length > 1 ? 1 - me : 0;
        float time = s.start + t;
        // Two cut-in bands, the opponent's on top and yours underneath. Each slams in from its sorcerer's side of the
        // screen, holds in place, then carries on out through the other side.
        int themSide = s.opponentSide, meSide = -themSide;
        float in = Mth.clamp(t / MOVE, 0, 1), slide = ease(in), slideOut = easeIn(1 - out);
        CinematicPanels.Band top = new CinematicPanels.Band(h * 0.12f, h * 0.5f), bottom = new CinematicPanels.Band(h * 0.5f, h * 0.88f);
        int lc = contrast(s.colors, them, me), rc = s.colors[me] | 0xFF000000;
        float travel = w * 1.15f;
        float topSlide = themSide * ((1 - slide) - slideOut) * travel, bottomSlide = meSide * ((1 - slide) - slideOut) * travel;
        // Speed (screen widths per tick) for the motion blur: fast on arrival and departure, still while holding.
        float speed = in < 1 ? 3 * (1 - in) * (1 - in) / MOVE : out < 1 ? 3 * (1 - out) * (1 - out) / MOVE : 0;
        CinematicPanels.band(g, top, w, lc, s.entities[them], w * 0.6f, topSlide, time);
        CinematicPanels.band(g, bottom, w, rc, s.entities[me], w * 0.42f, bottomSlide, time);
        if (speed > 0) {
            // Entering, each band moves away from its own side; leaving, it keeps going the same way.
            CinematicPanels.motion(g, top, w, lc, topSlide, -themSide, speed * travel, time);
            CinematicPanels.motion(g, bottom, w, rc, bottomSlide, -meSide, speed * travel, time);
        }
        if (slide > 0.95f && out > 0.5f) {
            CinematicPanels.border(g, top.top(), w, 5);
            CinematicPanels.border(g, top.bottom(), w, 6);
            CinematicPanels.border(g, bottom.bottom(), w, 5);
        }
        // The bands landing against each other: a flash on the seam.
        float impact = 1 - Mth.clamp((t - MOVE) / 6f, 0, 1);
        if (in >= 1 && impact > 0) {
            int size = Math.round(h * (0.5f + 0.6f * (1 - impact)));
            float cy = h * 0.5f;
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, dev.rick.jjk.JJK.id("textures/gui/clash_burst.png"), w / 2 - size / 2,
                    Math.round(cy) - size / 2, 0, 0, size, size, 64, 64, 64, 64, CinematicPanels.withAlpha(0xFFFFFFFF, impact));
            Matrix3x2fStack pose = g.pose();
            java.util.Random r = new java.util.Random(s.start);
            for (int i = 0; i < 14; i++) {
                pose.pushMatrix();
                pose.translate(w / 2f, cy);
                pose.rotate(r.nextFloat() * Mth.TWO_PI);
                int len = Math.round(h * (0.3f + 0.4f * r.nextFloat()) * (1.2f - impact));
                g.fill(size / 6, -1, size / 6 + len, 1, CinematicPanels.withAlpha(0xFFFFFFFF, impact));
                pose.popMatrix();
            }
        }
        float txt = Mth.clamp((t - MOVE) / 3f, 0, 1) * out;
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
        float vs = Mth.clamp((t - MOVE - 1) / 3f, 0, 1) * out;
        if (vs > 0) {
            float vx = w * 0.64f;
            CinematicPanels.label(g, font, "VS", vx, top.bottomAt(vx, w) - 12 * Mth.lerp(vs, 5f, 3f) / 3f, Mth.lerp(vs, 5f, 3f), 0xFFFFE08A, vs, false);
        }
    }

    /**
     * Which side of the screen the opponent is on from where the camera looks (-1 left, 1 right), so each sorcerer's
     * band comes from their own side. When they are lined up with the camera, the opponent is on the left (matching the
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

    private static float easeIn(float x) {
        return x * x * x;
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
