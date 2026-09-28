package dev.rick.jjk.client.hud;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.cinematic.CinematicPanels;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.net.RhythmInputPayload;
import dev.rick.jjk.hakari.RhythmAbility;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Hakari's Rhythm on the client: while the dance runs, the Special key is a beat button. A bar of notes slides toward a
 * receptor under the crosshair; each press is stamped with this client's sub-tick clock and sent to the server, which
 * judges it with the domain clash's timing windows. The judgements pop back here.
 */
public final class RhythmClient {
    private static final Identifier ARROW = dev.rick.jjk.JJK.id("textures/gui/clash_receptor.png");
    private static final String[] JUDGE = {"PERFECT", "GREAT", "GOOD", "MISS"};
    private static final int[] JUDGE_COLOR = {0xFFF0C040, 0xFF5CFFA8, 0xFFFFFFFF, 0xFFFF6060};
    private static final int[] results = new int[16];
    private static int lastJudge = -1;
    private static long lastJudgeAt;

    private RhythmClient() {}

    /** Ticks since Rhythm started for the local player, or -1 when it isn't running. */
    public static float clock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientState.activeCast.equals(RhythmAbility.ID)) return -1;
        ClientState.Cast c = ClientState.CASTS.get(mc.player.getId());
        if (c == null) return -1;
        return mc.level.getGameTime() - c.startTick() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static boolean active() {
        return clock() >= 0;
    }

    /** The Special key went down during the dance. */
    public static void press() {
        float t = clock();
        if (t >= 0) ClientPlayNetworking.send(new RhythmInputPayload(t));
    }

    /** A judgement came back from the server (0 perfect .. 3 miss). */
    public static void judged(int j) {
        lastJudge = j;
        lastJudgeAt = System.currentTimeMillis();
    }

    public static void render(GuiGraphicsExtractor g) {
        float t = clock();
        Minecraft mc = Minecraft.getInstance();
        long ms = System.currentTimeMillis();
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        if (t >= 0) {
            JJKConfig.Hakari cfg = JJKConfig.get().hakari;
            int cy = h / 2 + 38, rx = w / 2;
            // Track and receptor.
            g.fill(rx - 110, cy - 1, rx + 110, cy + 1, 0x60FFFFFF);
            float beatPulse = 0;
            for (int i = 0; i < cfg.rhythmBeats; i++) beatPulse = Math.max(beatPulse, 1 - Math.abs(t - RhythmAbility.beatTime(i)) / 3f);
            int rs = Math.round(18 + 6 * Math.max(0, beatPulse));
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ARROW, rx - rs / 2, cy - rs / 2, 0, 0, rs, rs, 64, 64, 64, 64, 0xFFF0C040);
            // Notes sliding in from the right, 3.2 px per tick.
            for (int i = 0; i < cfg.rhythmBeats; i++) {
                float dt = RhythmAbility.beatTime(i) - t;
                if (dt < -4 || dt > 60) continue;
                int nx = Math.round(rx + dt * 3.2f);
                int col = dt < -1 ? 0x60FF6060 : 0xFF5CFFA8;
                g.fill(nx - 5, cy - 5, nx + 5, cy + 5, 0xFF000000);
                g.fill(nx - 4, cy - 4, nx + 4, cy + 4, col);
                g.fill(nx - 1, cy - 8, nx + 1, cy - 4, col);
            }
            CinematicPanels.labelCentered(g, font, "RHYTHM  —  PRESS [" + dev.rick.jjk.client.input.InputHandler.keyLabel(dev.rick.jjk.core.ability.AbilitySlot.SKILL_5)
                    + "] ON THE BEAT", w / 2f, cy + 14, 0.75f, 0xFFF0C040, 1f);
        }
        long age = ms - lastJudgeAt;
        if (lastJudge >= 0 && age < 500) {
            float a = age < 350 ? 1f : 1f - (age - 350) / 150f;
            float pop = age < 90 ? 1.5f - age / 90f * 0.4f : 1.1f;
            CinematicPanels.labelCentered(g, font, JUDGE[Mth.clamp(lastJudge, 0, 3)], w / 2f, h / 2f + 16, pop, JUDGE_COLOR[Mth.clamp(lastJudge, 0, 3)], a);
        }
    }
}
