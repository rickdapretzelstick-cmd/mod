package dev.rick.jjk.client.hud;

import dev.rick.jjk.core.net.EncounterPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A realm fight, as the player in it sees it: a quiet count of the curses left at the top of the screen; once the last
 * ones are revealed, each is outlined through walls ({@link #revealed}) and the nearest is pointed at with its distance;
 * if the fight stalls, the Emergency Return line and, while channelling it, its progress. Only the fight's own players
 * are sent any of this.
 */
public final class EncounterHud {
    private static int remaining = -1, stage;
    private static List<EncounterPayload.Mark> marks = List.of();
    private static float channel = -1f;
    private static long lastUpdate;
    private static final Set<Integer> REVEALED = new HashSet<>();

    private EncounterHud() {}

    public static void apply(EncounterPayload p) {
        remaining = p.remaining();
        stage = p.stage();
        marks = p.marks();
        channel = p.channel();
        REVEALED.clear();
        for (EncounterPayload.Mark m : marks) REVEALED.add(m.entity());
        Minecraft mc = Minecraft.getInstance();
        lastUpdate = mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Whether an entity is one of the fight's revealed curses (outlined for this player). */
    public static boolean revealed(Entity e) {
        return remaining >= 0 && !REVEALED.isEmpty() && REVEALED.contains(e.getId());
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || remaining < 0) return;
        // Nothing heard for two seconds (left the fight some way the server didn't say): clear.
        if (mc.level.getGameTime() - lastUpdate > 40) {
            remaining = -1;
            REVEALED.clear();
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int y = 22;
        String count = remaining == 1 ? "1 curse remains" : remaining + " curses remain";
        if (stage == EncounterPayload.NORMAL) {
            // Subtle while the fight is going on.
            g.centeredText(font, count, w / 2, y, 0x90B8A8C8);
            return;
        }
        g.centeredText(font, count, w / 2, y, 0xFFE0B0FF);
        y += 11;
        EncounterPayload.Mark near = null;
        double best = Double.MAX_VALUE;
        Vec3 me = mc.player.position();
        for (EncounterPayload.Mark m : marks) {
            double d = me.distanceToSqr(m.x(), m.y(), m.z());
            if (d < best) {
                best = d;
                near = m;
            }
        }
        if (near != null) {
            // Which way it is, relative to where the player faces: an arrow and the distance.
            double dx = near.x() - me.x, dz = near.z() - me.z;
            float to = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float rel = Mth.wrapDegrees(to - mc.player.getYRot());
            String arrow = Math.abs(rel) < 22.5f ? "↑" : Math.abs(rel) > 157.5f ? "↓" : rel > 0 ? (rel < 67.5f ? "↗" : rel < 112.5f ? "→" : "↘")
                    : (rel > -67.5f ? "↖" : rel > -112.5f ? "←" : "↙");
            double dy = near.y() - mc.player.getEyeY();
            String height = dy > 3 ? " (above)" : dy < -3 ? " (below)" : "";
            g.centeredText(font, arrow + " nearest: " + Math.round(Math.sqrt(best)) + " m" + height, w / 2, y, 0xFFD8C8F0);
            y += 11;
        }
        if (stage == EncounterPayload.EMERGENCY) {
            g.centeredText(font, "The fight has stalled: an Emergency Return is open (no rewards)", w / 2, y, 0xFFFFC040);
            y += 11;
            if (channel >= 0) {
                int bw = 120, x0 = w / 2 - bw / 2;
                g.fill(x0 - 1, y - 1, x0 + bw + 1, y + 5, 0xC0000000);
                g.fill(x0, y, x0 + Math.round(bw * Mth.clamp(channel, 0, 1)), y + 4, 0xFFF0E6C0);
                g.centeredText(font, "Returning... hold still", w / 2, y + 8, 0xFFF0E6C0);
            }
        }
    }
}
