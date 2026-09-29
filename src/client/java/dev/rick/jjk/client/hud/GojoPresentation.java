package dev.rick.jjk.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gojo's screen presentation after the Jujutsu Shenanigans GIFs:
 * <ul>
 *   <li>Six Eyes: comic speech panels beside him — "LET'S GET" then "...A LITTLE CRAZY." — while he pulls the
 *       blindfold off and punches his palm (seen by everyone nearby, pinned to where he stands on screen).</li>
 *   <li>The 0.2 Domain: a "DOMAIN EXPANSION" cut-in band, a cut to black, then the white burst of Infinite Void.</li>
 *   <li>Limitless: the caster's view shatters like glass (the frame itself breaks into shards and flies apart) and
 *       opens on where he reappears.</li>
 * </ul>
 */
public final class GojoPresentation {
    private GojoPresentation() {}

    // --- Six Eyes speech panels ---
    private record Speech(int entityId, long start, int duration) {}

    private static final List<Speech> SPEECH = new ArrayList<>();

    /** The Six Eyes lines over this Gojo, for {@code duration} ticks. */
    public static void speech(int entityId, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SPEECH.removeIf(s -> s.entityId == entityId);
        SPEECH.add(new Speech(entityId, mc.level.getGameTime(), duration));
    }

    // --- Hakari's Jackpot: the three slot cards (JJS GIF), his numbers on them, around him ---
    private record Cards(int entityId, long start, int number) {}

    private static final List<Cards> CARDS = new ArrayList<>();
    private static final int CARDS_LIFE = 60;

    public static void jackpotCards(int entityId, int number) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CARDS.removeIf(c -> c.entityId == entityId);
        CARDS.add(new Cards(entityId, mc.level.getGameTime(), number));
    }

    // --- 0.2 Domain cut-in ---
    private static long cutInStart = Long.MIN_VALUE;
    private static final int CUT_IN = 24;

    public static void domainCutIn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) cutInStart = mc.level.getGameTime();
    }

    // --- Limitless glass shatter ---
    private static final Identifier SHATTER_TEX = JJK.id("dynamic/limitless_shatter");
    @Nullable private static DynamicTexture shatterTexture;
    private static int shatterW, shatterH;
    private static long shatterStart = Long.MIN_VALUE;
    private static final int SHATTER = 12;
    private record Shard(float u, float v, float w, float h, float vx, float vy, float spin, float delay) {}
    private static final List<Shard> SHARDS = new ArrayList<>();

    /** Grabs the frame the caster sees and breaks it (the shards fly once the frame arrives from the GPU). */
    public static void shatter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long at = mc.level.getGameTime();
        try {
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, image -> mc.execute(() -> startShatter(image, at)));
        } catch (Throwable t) {
            // No frame to grab: shards of plain glass instead.
            startShatter(null, at);
        }
    }

    private static void startShatter(@Nullable NativeImage image, long at) {
        Minecraft mc = Minecraft.getInstance();
        if (shatterTexture != null) {
            mc.getTextureManager().release(SHATTER_TEX);
            shatterTexture = null;
        }
        if (image != null) {
            shatterW = image.getWidth();
            shatterH = image.getHeight();
            shatterTexture = new DynamicTexture(() -> "jjk limitless shatter", image);
            mc.getTextureManager().register(SHATTER_TEX, shatterTexture);
        }
        shatterStart = mc.level != null ? mc.level.getGameTime() : at;
        // Break the frame into uneven panes; the ones near the centre go first and fastest.
        SHARDS.clear();
        Random r = new Random(at);
        int cols = 7, rows = 5;
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                float u = (x + (r.nextFloat() - 0.5f) * 0.3f) / cols, v = (y + (r.nextFloat() - 0.5f) * 0.3f) / rows;
                float cx = u + 0.5f / cols - 0.5f, cy = v + 0.5f / rows - 0.5f;
                float d = (float) Math.sqrt(cx * cx + cy * cy) + 0.05f;
                SHARDS.add(new Shard(Mth.clamp(u, 0, 1), Mth.clamp(v, 0, 1), (1.1f + r.nextFloat() * 0.4f) / cols, (1.1f + r.nextFloat() * 0.4f) / rows,
                        cx / d * (0.9f + r.nextFloat()), cy / d * (0.9f + r.nextFloat()), (r.nextFloat() - 0.5f) * 80f, d * 3f));
            }
        }
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        float time = now + partial;
        int w = g.guiWidth(), h = g.guiHeight();
        speechPanels(g, mc, w, h, time, partial);
        jackpotCards(g, mc, w, h, time, partial);
        cutIn(g, mc.font, w, h, time);
        glass(g, w, h, time);
    }

    /** World position → GUI coordinates, or null behind the camera. */
    @Nullable
    private static float[] project(Minecraft mc, Vec3 p, int w, int h) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 d = p.subtract(cam.position());
        Vector3fc f = cam.forwardVector(), up = cam.upVector(), left = cam.leftVector();
        double z = d.x * f.x() + d.y * f.y() + d.z * f.z();
        if (z < 0.2) return null;
        double x = -(d.x * left.x() + d.y * left.y() + d.z * left.z());
        double y = d.x * up.x() + d.y * up.y() + d.z * up.z();
        double scale = (h / 2.0) / Math.tan(Math.toRadians(cam.getFov()) / 2);
        return new float[] {(float) (w / 2.0 + x / z * scale), (float) (h / 2.0 - y / z * scale), (float) (scale / z)};
    }

    private static void speechPanels(GuiGraphicsExtractor g, Minecraft mc, int w, int h, float time, float partial) {
        SPEECH.removeIf(s -> time > s.start + s.duration + 2);
        for (Speech s : SPEECH) {
            Entity e = mc.level.getEntity(s.entityId);
            if (e == null || e.distanceTo(mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player) > 48) continue;
            float t = (time - s.start) / s.duration;
            Vec3 head = e.getPosition(partial).add(0, e.getBbHeight() * 0.8, 0);
            // "LET'S GET" on his left from the start; "...A LITTLE CRAZY." on the other side from a third of the way in.
            Vec3 side = Vec3.directionFromRotation(0, e.getYRot() + 90);
            panel(g, mc, w, h, head.add(side.scale(-0.9)).add(0, 0.25, 0), "LET'S\nGET", t, 0.02f);
            panel(g, mc, w, h, head.add(side.scale(0.9)).add(0, -0.1, 0), "...A\nLITTLE\nCRAZY.", t, 0.3f);
        }
    }

    private static void jackpotCards(GuiGraphicsExtractor g, Minecraft mc, int w, int h, float time, float partial) {
        CARDS.removeIf(c -> time > c.start + CARDS_LIFE);
        for (Cards c : CARDS) {
            Entity e = mc.level.getEntity(c.entityId);
            if (e == null) continue;
            float t = (time - c.start) / CARDS_LIFE;
            float a = Math.min(Mth.clamp(t / 0.1f, 0, 1), Mth.clamp((1 - t) / 0.2f, 0, 1));
            String digits = String.valueOf(Math.max(0, c.number));
            Vec3 base = e.getPosition(partial).add(0, e.getBbHeight() * 0.9, 0);
            for (int i = 0; i < 3; i++) {
                // Fanned out in front of him and to the sides, rising a little as they settle.
                double ang = Math.toRadians(e.getYRot() + 90 + (i - 1) * 70);
                Vec3 at = base.add(Math.cos(ang) * 1.6, 0.3 + (1 - Math.min(1, t * 4)) * -0.6 + (i == 1 ? 0.4 : 0), Math.sin(ang) * 1.6);
                float[] p = project(mc, at, w, h);
                if (p == null) continue;
                float size = Mth.clamp(p[2] * 0.02f, 0.4f, 3f);
                String d = digits.length() == 3 ? String.valueOf(digits.charAt(i)) : "7";
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate(p[0], p[1]);
                pose.scale(size, size);
                int cw = 20, ch = 28;
                g.fill(-cw / 2 - 1, -ch / 2 - 1, cw / 2 + 1, ch / 2 + 1, CinematicPanels.withAlpha(0xFF2A1A10, a));
                g.fill(-cw / 2, -ch / 2, cw / 2, ch / 2, CinematicPanels.withAlpha(0xFFFFF4E0, a));
                g.fill(-cw / 2 + 2, -ch / 2 + 2, cw / 2 - 2, ch / 2 - 2, CinematicPanels.withAlpha(0xFF3FD08A, a * 0.35f));
                pose.pushMatrix();
                pose.scale(2f, 2f);
                g.text(mc.font, d, -mc.font.width(d) / 2, -4, CinematicPanels.withAlpha(0xFFE0182E, a), false);
                pose.popMatrix();
                pose.popMatrix();
            }
        }
    }

    private static void panel(GuiGraphicsExtractor g, Minecraft mc, int w, int h, Vec3 at, String text, float t, float from) {
        float in = Mth.clamp((t - from) / 0.08f, 0, 1), out = Mth.clamp((0.92f - t) / 0.1f, 0, 1);
        float a = Math.min(in, out);
        if (a <= 0) return;
        float[] p = project(mc, at, w, h);
        if (p == null) return;
        float size = Mth.clamp(p[2] * 0.012f, 0.35f, 2.2f);
        Font font = mc.font;
        String[] lines = text.split("\n");
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, font.width(l));
        int pw = tw + 8, ph = lines.length * 10 + 6;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(p[0], p[1] - (1 - in) * 6);
        pose.scale(size, size);
        g.fill(-pw / 2 - 1, -ph / 2 - 1, pw / 2 + 1, ph / 2 + 1, CinematicPanels.withAlpha(0xFF101010, a));
        g.fill(-pw / 2, -ph / 2, pw / 2, ph / 2, CinematicPanels.withAlpha(0xFFF4F4F2, a));
        for (int i = 0; i < lines.length; i++) {
            g.text(font, lines[i], -font.width(lines[i]) / 2, -ph / 2 + 4 + i * 10, CinematicPanels.withAlpha(0xFF101010, a), false);
        }
        pose.popMatrix();
    }

    private static void cutIn(GuiGraphicsExtractor g, Font font, int w, int h, float time) {
        float age = time - cutInStart;
        if (age < 0 || age > CUT_IN + 10) return;
        if (age < CUT_IN - 6) {
            // A slanted band across the screen: DOMAIN / EXPANSION.
            float in = Mth.clamp(age / 3f, 0, 1);
            int bandH = Math.round(h * 0.34f * in);
            g.fill(0, h / 2 - bandH / 2, w, h / 2 + bandH / 2, 0xE0101018);
            g.fill(0, h / 2 - bandH / 2, w, h / 2 - bandH / 2 + 2, 0xFFF0F0F0);
            g.fill(0, h / 2 + bandH / 2 - 2, w, h / 2 + bandH / 2, 0xFFF0F0F0);
            if (in >= 1) {
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate(w * 0.08f + age * 1.5f, h / 2f - bandH / 2f + 6);
                pose.scale(2f, 2f);
                g.text(font, "DOMAIN", 0, 0, 0xFFFFFFFF, true);
                pose.popMatrix();
                pose.pushMatrix();
                pose.translate(w * 0.62f - age * 1.5f, h / 2f + bandH / 2f - 22);
                pose.scale(2f, 2f);
                g.text(font, "EXPANSION", 0, 0, 0xFFFFFFFF, true);
                pose.popMatrix();
            }
        } else if (age < CUT_IN) {
            g.fill(0, 0, w, h, 0xFF000000); // the cut to black
        } else {
            // Infinite Void for two tenths of a second: a white burst.
            float a = 1 - (age - CUT_IN) / 10f;
            g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, a));
        }
    }

    private static void glass(GuiGraphicsExtractor g, int w, int h, float time) {
        float age = time - shatterStart;
        if (age < 0 || age > SHATTER) return;
        float k = age / SHATTER;
        Matrix3x2fStack pose = g.pose();
        for (Shard s : SHARDS) {
            float t = Math.max(0, k * 4 - s.delay);
            float x = s.u * w, y = s.v * h, sw = s.w * w, sh = s.h * h;
            pose.pushMatrix();
            pose.translate(x + sw / 2 + s.vx * t * w * 0.35f, y + sh / 2 + s.vy * t * h * 0.35f + t * t * h * 0.15f);
            pose.rotate((float) Math.toRadians(s.spin * t));
            float scale = 1 - 0.3f * Math.min(1, t);
            pose.scale(scale, scale);
            int a = (int) (255 * Mth.clamp(1.3f - k * 1.2f, 0, 1));
            if (shatterTexture != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, SHATTER_TEX, Math.round(-sw / 2), Math.round(-sh / 2), s.u * shatterW, s.v * shatterH,
                        Math.round(sw), Math.round(sh), Math.round(s.w * shatterW), Math.round(s.h * shatterH), shatterW, shatterH, (a << 24) | 0xFFFFFF);
            } else {
                g.fill(Math.round(-sw / 2), Math.round(-sh / 2), Math.round(sw / 2), Math.round(sh / 2), CinematicPanels.withAlpha(0xFFDDE6EE, a / 255f * 0.5f));
            }
            // The cracked edges catch the light.
            int edge = CinematicPanels.withAlpha(0xFFFFFFFF, a / 255f * 0.8f);
            g.fill(Math.round(-sw / 2), Math.round(-sh / 2), Math.round(sw / 2), Math.round(-sh / 2) + 1, edge);
            g.fill(Math.round(-sw / 2), Math.round(-sh / 2), Math.round(-sw / 2) + 1, Math.round(sh / 2), edge);
            pose.popMatrix();
        }
        if (k < 0.25f) g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, 0.5f * (1 - k / 0.25f)));
    }
}
