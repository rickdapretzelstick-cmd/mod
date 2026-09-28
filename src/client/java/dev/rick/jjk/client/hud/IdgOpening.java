package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.client.render.GambleDomainRenderer;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.DomainPayload;
import dev.rick.jjk.hakari.IdleDeathGamble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The screen side of being caught in Idle Death Gamble, after Jujutsu Shenanigans:
 * <ul>
 *   <li>As it seals, the white it flooded everything with fades into a rush of black speed lines (the tunnel of trains
 *       streams past in the world, see {@link GambleDomainRenderer}).</li>
 *   <li>The rules of the game, imparted by the sure-hit, stay on screen for everyone inside: how to reach a Riichi, the
 *       pity jackpot and the odd/even bonuses in the top left, the scenarios in the top right, their colour cycling.</li>
 * </ul>
 */
public final class IdgOpening {
    private static final int[] PALETTE = {0xFFE23B2E, 0xFFE08A1E, 0xFF8BC34A, 0xFF1E9E7A, 0xFF1E88E5, 0xFF283593, 0xFF8E24AA, 0xFFD81B60};

    private IdgOpening() {}

    /** The Idle Death Gamble the local player is standing in, if any. */
    @Nullable
    private static ClientState.Domain inside(Minecraft mc) {
        if (mc.player == null) return null;
        for (ClientState.Domain d : ClientState.DOMAINS.values()) {
            if (!IdleDeathGamble.ID.equals(d.definition) || d.center == null) continue;
            if (d.phase != DomainPayload.ACTIVE) continue;
            if (mc.player.getEyePosition().distanceTo(d.center) < d.radius + 0.5) return d;
        }
        return null;
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientState.Domain d = inside(mc);
        if (d == null || dev.rick.jjk.client.clash.ClashClient.playing()) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        float time = now + partial;
        int w = g.guiWidth(), h = g.guiHeight();
        float rush = GambleDomainRenderer.rushAge(d, now, partial);
        boolean rushing = rush >= 0 && rush < GambleDomainRenderer.RUSH;
        // Overexposed: the light of the white room blooms in from the edges of the view.
        bloom(g, w, h, d.phase == DomainPayload.ACTIVE ? 1f : 0f);
        if (rushing) {
            float white = 1 - Mth.clamp(rush / 9f, 0, 1);
            speedLines(g, w, h, rush, now);
            if (white > 0) g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, white));
        }
        // The rules slide in from the sides as the rush begins, and stay.
        float in = rush < 0 ? 1 : Mth.clamp((rush - 8) / 8f, 0, 1);
        if (in > 0) rules(g, mc.font, w, time, in);
    }

    /** A soft white glow creeping in from every edge of the screen, and a faint white wash over the whole view. */
    private static void bloom(GuiGraphicsExtractor g, int w, int h, float strength) {
        if (strength <= 0) return;
        g.fill(0, 0, w, h, CinematicPanels.withAlpha(0xFFFFFFFF, 0.1f * strength));
        int steps = 14;
        float depthX = w * 0.22f, depthY = h * 0.26f;
        for (int i = 0; i < steps; i++) {
            float f = (float) i / steps;
            // Strongest at the very edge, fading to nothing inward.
            float a = 0.07f * (1 - f) * (1 - f) * strength;
            int x = Math.round(depthX * f), y = Math.round(depthY * f);
            int c = CinematicPanels.withAlpha(0xFFFFFFFF, a);
            g.fill(0, 0, w, Math.round(depthY * (1 - f)), c);
            g.fill(0, h - Math.round(depthY * (1 - f)), w, h, c);
            g.fill(0, 0, Math.round(depthX * (1 - f)), h, c);
            g.fill(w - Math.round(depthX * (1 - f)), 0, w, h, c);
        }
    }

    /** Black streaks converging on the centre of the screen, flickering as the trains stream past. */
    private static void speedLines(GuiGraphicsExtractor g, int w, int h, float rush, long now) {
        float fade = 1 - Mth.clamp((rush - 32) / 12f, 0, 1);
        if (fade <= 0) return;
        java.util.Random r = new java.util.Random(now / 2 * 7919L);
        Matrix3x2fStack pose = g.pose();
        float cx = w / 2f, cy = h / 2f, reach = (float) Math.hypot(w, h) / 2f;
        for (int i = 0; i < 70; i++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            float inner = reach * (0.35f + r.nextFloat() * 0.35f);
            float thick = 1f + r.nextFloat() * 3f;
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.rotate(a);
            int alpha = Math.round((90 + r.nextInt(150)) * fade);
            // Tapering toward the centre: a few stacked slivers.
            for (int k = 0; k < 3; k++) {
                float from = inner + k * (reach - inner) / 3f;
                float th = thick * (k + 1) / 3f;
                g.fill(Math.round(from), Math.round(-th), Math.round(reach + 10), Math.round(th), (alpha << 24) | 0x0A0A0A);
            }
            pose.popMatrix();
        }
    }

    private static void rules(GuiGraphicsExtractor g, Font font, int w, float time, float in) {
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        int color = PALETTE[(int) (time / 6) % PALETTE.length];
        float slide = 1 - (1 - in) * (1 - in);
        // Room either side of the gamble machine at the top centre.
        int room = Math.max(90, (w - 168) / 2 - 10);
        float big = 0.78f, small = 0.62f;
        List<String> left = List.of(
                "Use " + cfg.visualMovesRequired + " visual abilities to advance to a riichi scenario!",
                "Failing " + Math.max(1, cfg.maxAttempts - 1) + " scenarios will guarantee a jackpot on the next one!");
        String detail = "Getting an even number jackpot starts the next jackpot with faster spins, and getting an odd number starts it with advanced probability.";
        float x = 4 - (1 - slide) * (room + 20), y = 4;
        for (String line : left) y = paragraph(g, font, line, x, y, big, room, color, false);
        paragraph(g, font, detail, x, y + 2, small, room, color, false);
        // The scenarios, top right (clear of the ability icons down the right edge).
        float rx = w - 46 + (1 - slide) * (room + 20);
        float ry = 4;
        for (String line : new String[] {"Scenarios:", "Transit Card Riichi (☆☆★)", "Travel Emergency Riichi (☆★★)"}) {
            ry = paragraph(g, font, line, rx, ry, big, room, color, true);
        }
    }

    /** Wrapped lines of shadowed text; returns the y below them. */
    private static float paragraph(GuiGraphicsExtractor g, Font font, String text, float x, float y, float scale, int maxWidth, int color, boolean right) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.round(maxWidth / scale));
        Matrix3x2fStack pose = g.pose();
        for (FormattedCharSequence line : lines) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale);
            int tx = right ? -font.width(line) : 0;
            g.text(font, line, tx, 0, color, true);
            pose.popMatrix();
            y += (font.lineHeight + 1) * scale;
        }
        return y;
    }
}
