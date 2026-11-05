package dev.rick.jjk.client.finish;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.ShotSequence;
import dev.rick.jjk.client.camera.Shots;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A guardian's phase beat on its challenger's screen (progression.finish.GuardianPhases): cut low in front of it,
 * looking up as it rears (pushing in slowly under the act's title card), then hard to its head, close. With Reduced
 * Camera Motion there is no cut (the player's own view stays; the title card still shows).
 */
public final class EyecatchCamera {
    private static long since;
    private static double lengthMs;
    private static int curseId = -1, phase;
    @Nullable private static Vec3 lastFeet;
    private static double height = 2;
    @Nullable private static ShotSequence seq;

    private EyecatchCamera() {}

    /** The server began act {@code act}'s beat on guardian {@code id}, lasting {@code ms}. */
    public static void start(int id, double ms, int act) {
        curseId = id;
        phase = act;
        lengthMs = Math.max(400, ms);
        since = System.nanoTime();
        lastFeet = null;
        seq = new ShotSequence()
                .then(k -> Shots.lowHero(feet(), me(), 3.6 - 1.0 * k, height), lengthMs * 0.62)
                .then(k -> {
                    Vec3 head = feet().add(0, height * 0.88, 0);
                    Vec3 n = Shots.flatDir(feet(), me());
                    return Shots.place(head, head.add(n.scale(1.7 - 0.25 * k)).add(Shots.perpendicular(n, -1).scale(0.5)).add(0, -0.15, 0));
                }, lengthMs * 0.38);
    }

    public static boolean active() {
        return since != 0 && !CameraDirector.reducedMotion();
    }

    public static int phase() {
        return since != 0 ? phase : 0;
    }

    public static void tick(Minecraft mc) {
        if (since != 0 && ((System.nanoTime() - since) / 1e6 > lengthMs || mc.player == null || !mc.player.isAlive())) since = 0;
    }

    private static Vec3 me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Vec3.ZERO : mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    private static Vec3 feet() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(curseId);
        if (e != null) {
            lastFeet = e.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            height = Math.max(1.2, e.getBbHeight());
        }
        return lastFeet != null ? lastFeet : me().add(0, 0, 4);
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        if (!active() || seq == null) return null;
        Frame f = seq.at((System.nanoTime() - since) / 1e6);
        return f == null ? null : f.array();
    }
}
