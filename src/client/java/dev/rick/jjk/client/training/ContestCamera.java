package dev.rick.jjk.client.training;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.Shots;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A strength contest's cinematic shot: side-on to the two locked together, framing both, a slow orbit of about 25
 * degrees across the contest with a gentle push-in, and the field of view easing from 65 to 58. It never goes through
 * a wall ({@link Shots#clip}). With Reduced Camera Motion it is one still shot (no orbit, no push-in, no FOV change).
 * The HUD stays fixed either way; the third-person view it needs comes from {@link CameraDirector}.
 */
public final class ContestCamera {
    private static long since;
    /** Which side of the pair the shot is on (chosen once per contest, the clearer side). */
    private static int side;

    static final float FOV_START = 65f, FOV_END = 58f, FOV_STILL = 62f;
    static final double ORBIT_DEGREES = 25;

    private ContestCamera() {}

    static void begin() {
        since = System.nanoTime();
        side = 0;
    }

    public static boolean active() {
        return TrainingClient.contest() && since != 0;
    }

    public static void tick(Minecraft mc) {
        if (!TrainingClient.contest() || mc.player == null || !mc.player.isAlive()) since = 0;
    }

    public static void reset() {
        since = 0;
    }

    /** 0 at the start of the contest to 1 at its end. */
    private static double progress(TrainingClient.Attempt a) {
        double total = a.start.introMs() + a.start.durationMs();
        return Mth.clamp((a.clock + a.start.introMs()) / Math.max(1, total), 0, 1);
    }

    /** The field of view to use (degrees), or NaN when not in use. */
    public static float fov() {
        TrainingClient.Attempt a = TrainingClient.attempt();
        if (a == null || !active()) return Float.NaN;
        if (CameraDirector.reducedMotion()) return FOV_STILL;
        return (float) Mth.lerp(Shots.ease(progress(a)), FOV_START, FOV_END);
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        TrainingClient.Attempt a = TrainingClient.attempt();
        Minecraft mc = Minecraft.getInstance();
        if (a == null || !active() || mc.player == null || mc.level == null) return null;
        TrainingClient.advance();
        Vec3 me = mc.player.position().add(0, 1.2, 0);
        Vec3 foe = a.start.look();
        Vec3 n = Shots.flatDir(me, foe);
        Vec3 mid = me.lerp(foe, 0.5);
        double span = Math.max(2, me.distanceTo(foe));
        double reach = span * 1.1 + 3.2;
        if (side == 0) {
            side = Shots.clearance(mid, mid.add(Shots.perpendicular(n, 1).scale(reach)).add(0, 1.1, 0))
                    >= Shots.clearance(mid, mid.add(Shots.perpendicular(n, -1).scale(reach)).add(0, 1.1, 0)) ? 1 : -1;
        }
        boolean still = CameraDirector.reducedMotion();
        double eased = still ? 0 : Shots.ease(progress(a));
        // Side-on, a little behind the contestant, turning ~25 degrees across the contest.
        double orbit = Math.toRadians(-20 * side) + Math.toRadians(ORBIT_DEGREES) * eased * side;
        Vec3 out = Shots.rotate(Shots.perpendicular(n, side), orbit);
        double dist = reach * (1 - 0.18 * eased);
        Frame shot = Shots.place(mid, mid.add(out.scale(dist)).add(0, 1.1 + span * 0.15, 0));
        // Into the shot from the player's own view over the first moments.
        double in = still ? 1 : Shots.ease((System.nanoTime() - since) / 3e8);
        return shot.blendFrom(vanillaPos, vanillaYaw, vanillaPitch, in);
    }
}
