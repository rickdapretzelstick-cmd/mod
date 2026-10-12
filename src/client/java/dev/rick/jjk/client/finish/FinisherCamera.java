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
 * A finisher's cinematic (progression.finish.Finishers), on the finisher's own screen: over their shoulder at the
 * reeling curse, a cut low on them as they wind up, then side-on as the blow lands, pulling out as it is sent flying.
 * Hard cuts, never through a wall. With Reduced Camera Motion there is no cinematic (the player's own view stays).
 */
public final class FinisherCamera {
    private static long since;
    private static double lengthMs;
    private static int curseId = -1;
    @Nullable private static Vec3 lastFoe;
    @Nullable private static ShotSequence seq;

    private FinisherCamera() {}

    /** The server started a finisher on curse {@code id}, lasting {@code ms}. */
    public static void start(int id, double ms) {
        curseId = id;
        lengthMs = Math.max(300, ms);
        since = System.nanoTime();
        lastFoe = null;
        seq = new ShotSequence()
                .then(k -> Shots.overShoulder(me().add(0, 0.3, 0), foe(), 1, 2.8 - 0.5 * k), lengthMs * 0.3)
                .then(k -> Shots.lowHero(feet(), foe(), 2.2 - 0.3 * k, 1.8), lengthMs * 0.3)
                .then(k -> Shots.twoShot(me(), foe(), -1, 1.4 + 1.0 * k, 0.8 + 0.4 * k), lengthMs * 0.4);
    }

    public static boolean active() {
        return since != 0 && !CameraDirector.reducedMotion();
    }

    public static void tick(Minecraft mc) {
        if (since != 0 && ((System.nanoTime() - since) / 1e6 > lengthMs || mc.player == null || !mc.player.isAlive())) since = 0;
    }

    private static Vec3 feet() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Vec3.ZERO : mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    private static Vec3 me() {
        return feet().add(0, 1.2, 0);
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
        if (!active() || seq == null) return null;
        Frame f = seq.at((System.nanoTime() - since) / 1e6);
        return f == null ? null : f.array();
    }
}
