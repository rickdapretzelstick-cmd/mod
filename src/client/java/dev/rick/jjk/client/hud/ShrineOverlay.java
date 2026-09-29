package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.client.render.ShrineDomainRenderer;
import dev.rick.jjk.core.net.DomainPayload;
import dev.rick.jjk.yuji.MalevolentShrine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.Random;

/**
 * The screen side of being inside Malevolent Shrine, after the JJS GIF:
 * <ul>
 *   <li>While it forms, darkness closes in from every edge of the view (the world going black around you).</li>
 *   <li>As it seals: a white flash, the world stark and colourless for a moment while the shrine greys in, then a red
 *       flash as its colour floods in.</li>
 *   <li>Then the sure hit across the whole screen: huge white slashes with black cores tearing diagonally through the
 *       view, over a red vignette that never lets up.</li>
 * </ul>
 */
public final class ShrineOverlay {
    private ShrineOverlay() {}

    @Nullable
    private static ClientState.Domain around(Minecraft mc) {
        if (mc.player == null) return null;
        for (ClientState.Domain d : ClientState.DOMAINS.values()) {
            if (!MalevolentShrine.ID.equals(d.definition) || d.center == null) continue;
            if (d.phase != DomainPayload.ACTIVE && d.phase != DomainPayload.FORMING) continue;
            if (mc.player.getEyePosition().distanceTo(d.center) < d.radius + 0.5) return d;
        }
        return null;
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientState.Domain d = around(mc);
        if (d == null || dev.rick.jjk.client.clash.ClashClient.playing()) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        float time = now + partial;
        int w = g.guiWidth(), h = g.guiHeight();
        if (d.phase == DomainPayload.FORMING) {
            float p = Mth.clamp((now - d.phaseStartTick + partial) / Math.max(1, d.formationTicks), 0, 1);
            vignette(g, w, h, 0xFF000000, 0.25f + 0.7f * p, 0.5f + 0.45f * p);
            return;
        }
        float reveal = ShrineDomainRenderer.revealAge(d, now, partial);
        if (reveal >= 0 && reveal < ShrineDomainRenderer.REVEAL) {
            // The flash as it seals, the colourless moment, the red flash as the colour comes.
            float white = 1 - Mth.clamp(reveal / 4f, 0, 1);
            if (white > 0) g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, white));
            if (reveal < ShrineDomainRenderer.COLOUR_AT) {
                // Stark and colourless for a moment.
                g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFF8C8C8C, 0.22f));
            } else {
                float red = 1 - Mth.clamp((reveal - ShrineDomainRenderer.COLOUR_AT) / 6f, 0, 1);
                g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFB00010, 0.45f * red));
            }
            return;
        }
        boolean owner = d.ownerId == mc.player.getId();
        vignette(g, w, h, 0xFF500006, 0.22f + 0.06f * Mth.sin(time * 0.2f), 0.22f);
        slashes(g, w, h, time, owner ? 0.45f : 1f, d.id);
    }

    /** A soft ring of {@code color} closing in from the edges ({@code depth} is how far in it reaches, 0-1). */
    private static void vignette(GuiGraphicsExtractor g, int w, int h, int color, float strength, float depth) {
        int steps = 16;
        float dx = w * 0.5f * depth, dy = h * 0.5f * depth;
        for (int i = 0; i < steps; i++) {
            float f = (float) i / steps;
            float a = strength * (1 - f) * (1 - f) * 0.22f;
            int c = CinematicPanels.withAlpha(color, a);
            int x = Math.round(dx * (1 - f)), y = Math.round(dy * (1 - f));
            g.fill(0, 0, w, y, c);
            g.fill(0, h - y, w, h, c);
            g.fill(0, y, x, h - y, c);
            g.fill(w - x, y, w, h - y, c);
        }
    }

    /** Two or three blades across the screen every few ticks: a white glow, a bright edge and a black core. */
    private static void slashes(GuiGraphicsExtractor g, int w, int h, float time, float strength, int seed) {
        int batch = (int) (time / 5);
        float age = (time - batch * 5) / 5f;
        if (age > 0.6f) return;
        float fade = (1 - age / 0.6f) * strength;
        Random r = new Random(batch * 7919L + seed);
        Matrix3x2fStack pose = g.pose();
        int n = 1 + r.nextInt(3);
        float reach = (float) Math.hypot(w, h);
        for (int i = 0; i < n; i++) {
            float cx = w * (0.1f + r.nextFloat() * 0.8f), cy = h * (0.1f + r.nextFloat() * 0.8f);
            float ang = (r.nextFloat() - 0.5f) * 1.4f + (r.nextBoolean() ? 0.5f : -0.5f);
            float thick = 3 + r.nextFloat() * 6;
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.rotate(ang);
            int x0 = Math.round(-reach * 0.6f), x1 = Math.round(reach * 0.6f);
            for (int k = 3; k >= 1; k--) {
                float t = thick * k * 0.9f;
                g.fill(x0, Math.round(-t), x1, Math.round(t), CinematicPanels.withAlpha(0xFFFFD8D8, 0.12f * fade * (4 - k)));
            }
            g.fill(x0, Math.round(-thick * 0.45f), x1, Math.round(thick * 0.45f), CinematicPanels.withAlpha(0xFFFFFFFF, 0.9f * fade));
            g.fill(x0, -1, x1, 1, CinematicPanels.withAlpha(0xFF050203, 0.95f * fade));
            pose.popMatrix();
        }
    }
}
