package dev.rick.jjk.client.cinematic;

import dev.rick.jjk.JJK;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Anime-style cut-in panels for domain cinematics: full-width bands slanted across the screen, each holding one
 * sorcerer's close-up against an ink-washed backdrop in their domain's colour, with speed lines, white slanted borders
 * and heavy outlined lettering.
 */
public final class CinematicPanels {
    private static final Identifier INK = JJK.id("textures/gui/cinematic_ink.png");
    /** Slope of every band edge (screen y per x): edges rise to the right. */
    public static final float SLOPE = -0.07f;

    private CinematicPanels() {}

    /** One band: its straight top and bottom at the screen's horizontal centre (the edges slope through these). */
    public record Band(float top, float bottom) {
        public float topAt(float x, int w) {
            return top + SLOPE * (x - w / 2f);
        }

        public float bottomAt(float x, int w) {
            return bottom + SLOPE * (x - w / 2f);
        }
    }

    /**
     * Draws a band: domain-coloured backdrop with drifting ink and speed lines, then the sorcerer's close-up centred
     * on {@code faceX}. {@code slideX} shifts the whole band sideways (for sliding in).
     */
    public static void band(GuiGraphicsExtractor g, Band b, int w, int color, int entityId, float faceX, float slideX, float time) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(slideX, 0);
        skew(pose, w);
        int top = Math.round(b.top), bot = Math.round(b.bottom);
        int dark = mix(color, 0xFF000000, 0.78f), mid = mix(color, 0xFF000000, 0.45f);
        g.fillGradient(-40, top, w + 40, bot, dark, mid);
        // Ink wash drifting slowly across, in the domain's colour.
        int inkColor = (mix(color, 0xFFFFFFFF, 0.55f) & 0xFFFFFF) | 0xB0000000;
        int ih = bot - top, iw = ih * 2;
        float drift = (time * 1.5f) % iw;
        for (float x = -iw - drift; x < w + iw; x += iw) {
            g.blit(RenderPipelines.GUI_TEXTURED, INK, Math.round(x), top, 0, 0, iw, ih, 256, 128, 256, 128, inkColor);
        }
        // Speed lines streaking across.
        java.util.Random r = new java.util.Random(entityId * 7919L);
        for (int i = 0; i < 26; i++) {
            int y = top + 2 + r.nextInt(Math.max(1, ih - 4));
            float len = w * (0.15f + r.nextFloat() * 0.35f);
            float speed = 18 + r.nextFloat() * 30;
            float x = (r.nextFloat() * (w + len) + time * speed) % (w + len) - len;
            int a = 40 + r.nextInt(90);
            g.fill(Math.round(x), y, Math.round(x + len), y + 1, (a << 24) | 0xFFFFFF);
        }
        pose.popMatrix();
        // The close-up, clipped to the part of the band that is inside it everywhere across the portrait's width.
        int half = Math.round((b.bottom - b.top) * 0.75f);
        int px0 = Math.round(faceX + slideX) - half, px1 = Math.round(faceX + slideX) + half;
        int clipTop = (int) Math.ceil(Math.max(b.topAt(px0 - slideX, w), b.topAt(px1 - slideX, w)));
        int clipBot = (int) Math.floor(Math.min(b.bottomAt(px0 - slideX, w), b.bottomAt(px1 - slideX, w)));
        closeUp(g, entityId, px0, clipTop, px1, clipBot);
    }

    /**
     * Motion blur for a band sliding sideways: its edges smear out along the direction of travel ({@code dir}, -1 left or
     * 1 right) in proportion to {@code speed} (pixels per tick), with a white cutting edge on the leading side and
     * speed lines streaking ahead of it.
     */
    public static void motion(GuiGraphicsExtractor g, Band b, int w, int color, float slideX, int dir, float speed, float time) {
        float smear = Math.min(speed * 0.6f, w * 0.7f);
        if (smear < 2) return;
        float k = Math.min(1, smear / (w * 0.25f));
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(slideX, 0);
        skew(pose, w);
        int top = Math.round(b.top), bot = Math.round(b.bottom);
        int mid = mix(color, 0xFF000000, 0.45f);
        float lead = dir > 0 ? w + 40 : -40, trail = dir > 0 ? -40 : w + 40;
        int segs = 10;
        for (int i = 0; i < segs; i++) {
            float f0 = (float) i / segs, f1 = (i + 1f) / segs;
            float a = 0.75f * k * (1 - f0) * (1 - f0);
            // Ahead of the leading edge, and behind the trailing one.
            g.fill(Math.round(lead + dir * smear * f0), top, Math.round(lead + dir * smear * f1) + dir, bot, withAlpha(mid, a));
            g.fill(Math.round(trail - dir * smear * f0), top, Math.round(trail - dir * smear * f1) - dir, bot, withAlpha(mid, a * 0.6f));
        }
        // The cutting edge.
        for (int i = 0; i < 4; i++) {
            float x = lead + dir * i * 3;
            g.fill(Math.round(x), top, Math.round(x + dir * 3), bot, withAlpha(0xFFFFFFFF, k * (1 - i / 4f)));
        }
        // Speed lines tearing ahead.
        java.util.Random r = new java.util.Random(Float.floatToIntBits(b.top) * 31L + dir);
        int ih = bot - top;
        for (int i = 0; i < 18; i++) {
            int y = top + 2 + r.nextInt(Math.max(1, ih - 4));
            float len = smear * (0.5f + r.nextFloat() * 0.9f);
            float x0 = lead + dir * smear * r.nextFloat() * 0.3f;
            g.fill(Math.round(Math.min(x0, x0 + dir * len)), y, Math.round(Math.max(x0, x0 + dir * len)), y + 1,
                    withAlpha(0xFFFFFFFF, k * (0.25f + 0.5f * r.nextFloat())));
        }
        pose.popMatrix();
    }

    public static int withAlpha(int c, float a) {
        return (c & 0xFFFFFF) | (Math.round(Mth.clamp(a, 0, 1) * 255) << 24);
    }

    /** White slanted border along a band edge (drawn over the bands so their seams are clean). */
    public static void border(GuiGraphicsExtractor g, float y, int w, int thickness) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        skew(pose, w);
        g.fill(-40, Math.round(y - thickness / 2f) - 1, w + 40, Math.round(y + thickness / 2f) + 1, 0xFF000000);
        g.fill(-40, Math.round(y - thickness / 2f), w + 40, Math.round(y + thickness / 2f), 0xFFF4F4F4);
        pose.popMatrix();
    }

    /** A sorcerer's face and shoulders filling the box, looking straight out. */
    public static void closeUp(GuiGraphicsExtractor g, int entityId, int x0, int y0, int x1, int y1) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (!(e instanceof LivingEntity le) || y1 - y0 < 8) return;
        int h = y1 - y0;
        float bbh = le.getBbHeight();
        // Head (a quarter of a humanoid) at ~60% of the band; its centre a little above the band's middle.
        int size = Math.round(h * 0.56f / (bbh * 0.28f));
        float headCenter = y0 + h * 0.52f;
        float centerY = headCenter + bbh * size * 0.5f - bbh * size * 0.13f;
        float cx = (x0 + x1) / 2f;
        g.enableScissor(x0, y0, x1, y1);
        Portraits.model(g, le, x0, y0, x1, Math.round(2 * centerY - y0), size, cx, headCenter);
        g.disableScissor();
    }

    /** {@link #label} centred on x. */
    public static void labelCentered(GuiGraphicsExtractor g, Font font, String text, float x, float y, float scale, int color, float alpha) {
        int tw = font.width(Component.literal(text).withStyle(ChatFormatting.BOLD));
        label(g, font, text, x - tw * scale / 2f, y, scale, color, alpha, false);
    }

    /** Heavy white lettering with a thick black outline. */
    public static void label(GuiGraphicsExtractor g, Font font, String text, float x, float y, float scale, int color, float alpha, boolean alignRight) {
        if (alpha <= 0.02f) return;
        Component c = Component.literal(text).withStyle(ChatFormatting.BOLD);
        int tw = font.width(c);
        int a = Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        int ox = alignRight ? -tw : 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.text(font, c, ox + dx, dy, a, false);
            }
        }
        g.text(font, c, ox, 0, (color & 0xFFFFFF) | a, false);
        pose.popMatrix();
    }

    private static final Identifier HELIX = JJK.id("textures/gui/cinematic_helix.png");
    /** The Jujutsu Shenanigans cut-in is steeper than the house style. */
    public static final float HELIX_SLOPE = -0.11f;

    /**
     * The Idle Death Gamble cut-in, after Jujutsu Shenanigans: a teal band patterned with dark double helices, slanted
     * steeply across the screen, opening out of a thin slash ({@code open} 0 → 1) with the caster's close-up in it and
     * white edges. {@code center} and {@code half} are the band's middle and half height at the screen's centre.
     */
    public static void helixBand(GuiGraphicsExtractor g, int w, float center, float half, float open, int entityId, float faceX, float time) {
        float hh = half * open;
        Matrix3x2fStack pose = g.pose();
        if (hh >= 1) {
            pose.pushMatrix();
            skew(pose, w, HELIX_SLOPE);
            int top = Math.round(center - hh), bot = Math.round(center + hh);
            g.fillGradient(-60, top, w + 60, bot, 0xFF36AEB0, 0xFF22858A);
            int ih = Math.max(1, bot - top), iw = ih * 2;
            float drift = (time * 0.6f) % iw;
            for (float x = -iw - drift; x < w + iw; x += iw) {
                g.blit(RenderPipelines.GUI_TEXTURED, HELIX, Math.round(x), top, 0, 0, iw, ih, 256, 128, 256, 128, 0xFFFFFFFF);
            }
            pose.popMatrix();
            // The caster, clipped to where the band is inside at both sides of the close-up.
            int halfW = Math.round(half * 1.5f);
            int px0 = Math.round(faceX) - halfW, px1 = Math.round(faceX) + halfW;
            float cTop = Math.max(helixEdge(center - hh, px0, w), helixEdge(center - hh, px1, w));
            float cBot = Math.min(helixEdge(center + hh, px0, w), helixEdge(center + hh, px1, w));
            if (cBot - cTop > 8) closeUp(g, entityId, px0, (int) Math.ceil(cTop), px1, (int) Math.floor(cBot));
        }
        // The two white edges (at the start, the slash itself).
        pose.pushMatrix();
        skew(pose, w, HELIX_SLOPE);
        for (float y : new float[] {center - hh - 1, center + hh + 1}) {
            g.fill(-60, Math.round(y) - 2, w + 60, Math.round(y) + 2, 0xFF101418);
            g.fill(-60, Math.round(y) - 1, w + 60, Math.round(y) + 1, 0xFFFFFFFF);
        }
        pose.popMatrix();
    }

    /** Where a helix band edge (at height {@code y} in the middle of the screen) is at column {@code x}. */
    public static float helixEdge(float y, float x, int w) {
        return y + HELIX_SLOPE * (x - w / 2f);
    }

    private static void skew(Matrix3x2fStack pose, int w, float slope) {
        pose.mul(new Matrix3x2f(1, slope, 0, 1, 0, -slope * w / 2f));
    }

    private static void skew(Matrix3x2fStack pose, int w) {
        // y' = y + SLOPE * (x - w/2): every horizontal edge becomes a slanted one pivoting on the screen's centre.
        pose.mul(new Matrix3x2f(1, SLOPE, 0, 1, 0, -SLOPE * w / 2f));
    }

    public static int mix(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }
}
