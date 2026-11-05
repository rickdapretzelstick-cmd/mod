package dev.rick.jjk.client.finish;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.Shots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * A bind on the player's own screen (progression.finish.Binds): a tight two-shot side-on to the two blows grinding
 * against each other, pushing in as it goes and kicked by every tap; on the HUD the bind's meter (the player's side
 * against the curse's) and the call to mash the attack button; then a beat of the result. Taps go to the server from
 * the attack button (client.input.InputHandler) while it lasts. With Reduced Camera Motion the view stays the player's.
 */
public final class BindClient {
    private static long since, lastTap, endedAt;
    private static double lengthMs;
    private static int curseId = -1, result = -1;
    private static float meter = 0.5f;
    @Nullable private static Vec3 lastFoe;

    private BindClient() {}

    /** The server started a bind with curse {@code id}, lasting at most {@code ms}. */
    public static void start(int id, double ms) {
        curseId = id;
        lengthMs = Math.max(300, ms);
        since = System.nanoTime();
        meter = 0.5f;
        result = -1;
        lastFoe = null;
    }

    public static void meter(float m) {
        meter = Mth.clamp(m, 0f, 1f);
    }

    public static void end(boolean won) {
        if (since == 0) return;
        since = 0;
        result = won ? 1 : 0;
        endedAt = System.nanoTime();
    }

    /** Whether a bind is on (taps go to it; the attack button never swings). */
    public static boolean active() {
        return since != 0;
    }

    public static float meterForTest() {
        return meter;
    }

    public static void tapped() {
        lastTap = System.nanoTime();
    }

    private static double elapsed() {
        return (System.nanoTime() - since) / 1e6;
    }

    public static void tick(Minecraft mc) {
        // Never stuck on: the server's end is the rule, this only covers a lost packet.
        if (since != 0 && (elapsed() > lengthMs + 1500 || mc.player == null || !mc.player.isAlive())) since = 0;
    }

    public static boolean camera() {
        return active() && !CameraDirector.reducedMotion();
    }

    private static Vec3 me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Vec3.ZERO : mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0, 1.2, 0);
    }

    private static Vec3 foe() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(curseId);
        if (e != null) lastFoe = e.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0, Math.min(e.getBbHeight() * 0.6, 1.6), 0);
        return lastFoe != null ? lastFoe : me().add(0, 0, 2);
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        if (!camera()) return null;
        double k = Shots.ease(Mth.clamp(elapsed() / lengthMs, 0, 1));
        Vec3 a = me(), b = foe();
        Frame f = Shots.twoShot(a, b, 1, 0.8 - 0.45 * k, 0.15);
        // Each tap kicks the frame a touch toward the curse; the look leans toward whoever has the better of it.
        double kick = Mth.clamp(1 - (System.nanoTime() - lastTap) / 9e7, 0, 1);
        Vec3 along = b.subtract(a);
        along = along.lengthSqr() < 1e-4 ? Vec3.ZERO : along.normalize();
        Vec3 look = a.lerp(b, 0.5).add(along.scale((meter - 0.5) * 0.6));
        Vec3 pos = f.pos().add(along.scale(0.08 * kick)).add(0, 0.03 * kick * Math.sin(System.nanoTime() / 2e7), 0);
        return new Frame(pos, look).array();
    }

    // --- HUD ---

    private static final int YOU = 0xFFFFC36A, IT = 0xFFE0506A;

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        if (!active()) {
            // A beat of the result after it breaks.
            double after = (System.nanoTime() - endedAt) / 1e6;
            if (result < 0 || after > 900) return;
            int alpha = Mth.clamp((int) Math.round(255 * Math.min(1, (900 - after) / 300)), 0, 255);
            if (alpha < 8) return;
            title(g, font, w, h, result == 1 ? "BIND WON" : "OVERPOWERED", (alpha << 24) | ((result == 1 ? YOU : IT) & 0xFFFFFF));
            return;
        }
        double t = elapsed();
        // The meter: the curse's side on the left, the player's on the right, a marker where the blows meet.
        int bw = 200, bx = w / 2 - bw / 2, by = Math.round(h * 0.64f), bh = 9;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xD0000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF3A1820);
        int fill = Math.round(bw * meter);
        g.fill(bx, by, bx + fill, by + bh, meter >= 0.5f ? YOU : IT);
        g.fill(bx + bw / 2, by - 3, bx + bw / 2 + 1, by + bh + 3, 0x90FFFFFF);
        int mx = bx + fill;
        g.fill(mx - 1, by - 5, mx + 2, by + bh + 5, 0xFFFFFFFF);
        g.text(font, "IT", bx - 8 - font.width("IT"), by, IT, true);
        g.text(font, "YOU", bx + bw + 8, by, YOU, true);
        // The time left, as a thin line under it.
        float left = (float) Mth.clamp(1 - t / lengthMs, 0, 1);
        g.fill(bx, by + bh + 4, bx + Math.round(bw * left), by + bh + 5, 0xB0FFFFFF);
        // Mash: a pulse that jumps on every tap.
        double sinceTap = (System.nanoTime() - lastTap) / 1e6;
        float pop = sinceTap < 120 ? 1.3f - (float) (sinceTap / 120) * 0.3f : 1f + 0.04f * Mth.sin((float) (t / 70));
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, by - 26);
        pose.scale(2.2f * pop);
        g.centeredText(font, "MASH ATTACK!", 0, -4, 0xFFFFFFFF);
        pose.popMatrix();
        title(g, font, w, h, "BIND", 0xFFFFD27A);
    }

    private static void title(GuiGraphicsExtractor g, Font font, int w, int h, String text, int color) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h * 0.16f);
        pose.scale(2.4f);
        g.centeredText(font, text, 0, -4, color);
        pose.popMatrix();
    }
}
