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
     * A slash sweeping across the screen: a long blade in the sorcerer's colour centred at (cx, cy), travelling along
     * {@code angle} (radians, screen space). The head is a bright white edge, the tail fades out behind it with ghost
     * copies for motion blur, speed lines stream backwards along it, and the sorcerer's close-up rides in the middle.
     */
    public static void slash(GuiGraphicsExtractor g, float cx, float cy, float angle, float length, float thickness, int color, float alpha,
                             float time, int entityId, boolean portrait) {
        if (alpha <= 0.01f) return;
        Matrix3x2fStack pose = g.pose();
        float half = length / 2, t2 = thickness / 2;
        int dark = mix(color, 0xFF000000, 0.7f), mid = mix(color, 0xFF000000, 0.35f);
        // Motion blur: faint copies trailing behind.
        for (int k = 3; k >= 1; k--) {
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.rotate(angle);
            pose.translate(-k * thickness * 0.45f, 0);
            g.fill(Math.round(-half), Math.round(-t2), Math.round(half), Math.round(t2), withAlpha(mid, alpha * 0.18f / k));
            pose.popMatrix();
        }
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate(angle);
        // Body: brightest toward the head, fading out along the tail.
        int segs = 12;
        for (int i = 0; i < segs; i++) {
            float x0 = -half + length * i / segs, x1 = -half + length * (i + 1) / segs;
            float f = (i + 1f) / segs;
            float a = alpha * (0.15f + 0.85f * f * f);
            g.fillGradient(Math.round(x0), Math.round(-t2), Math.round(x1) + 1, Math.round(t2), withAlpha(dark, a), withAlpha(mid, a));
        }
        // Ink wash streaming backwards along the blade.
        int ih = Math.round(thickness), iw = ih * 2;
        float drift = (time * 14f) % iw;
        for (float x = -half - drift; x < half; x += iw) {
            float f = Mth.clamp((x + half) / length, 0, 1);
            g.blit(RenderPipelines.GUI_TEXTURED, INK, Math.round(x), Math.round(-t2), 0, 0, iw, ih, 256, 128, 256, 128,
                    withAlpha(mix(color, 0xFFFFFFFF, 0.55f), alpha * 0.7f * f));
        }
        // Speed lines.
        java.util.Random r = new java.util.Random(entityId * 7919L);
        for (int i = 0; i < 22; i++) {
            float y = -t2 + 2 + r.nextFloat() * (thickness - 4);
            float len = length * (0.1f + r.nextFloat() * 0.25f);
            float x = half - ((r.nextFloat() * length + time * (40 + r.nextFloat() * 40)) % (length + len));
            g.fill(Math.round(x), Math.round(y), Math.round(x + len), Math.round(y) + 1, withAlpha(0xFFFFFFFF, alpha * (0.2f + 0.4f * r.nextFloat())));
        }
        // White edges, and the cutting edge at the head.
        g.fill(Math.round(-half), Math.round(-t2) - 2, Math.round(half), Math.round(-t2), withAlpha(0xFFF4F4F4, alpha));
        g.fill(Math.round(-half), Math.round(t2), Math.round(half), Math.round(t2) + 2, withAlpha(0xFFF4F4F4, alpha));
        for (int k = 0; k < 5; k++) {
            float taper = 1 - k / 5f;
            g.fill(Math.round(half + k * 4), Math.round(-t2 * taper), Math.round(half + k * 4 + 5), Math.round(t2 * taper), withAlpha(0xFFFFFFFF, alpha * taper));
        }
        pose.popMatrix();
        // The sorcerer rides in the middle of their slash.
        if (portrait && alpha > 0.5f) {
            float a = thickness * 0.42f, b = thickness * 0.3f;
            closeUp(g, entityId, Math.round(cx - a), Math.round(cy - b), Math.round(cx + a), Math.round(cy + b));
        }
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
