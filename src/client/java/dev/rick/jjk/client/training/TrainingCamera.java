package dev.rick.jjk.client.training;

import dev.rick.jjk.progression.training.TrainingNet;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The training's camera: one fixed, readable shot per scene (sent by the server with the attempt), reached by a slow
 * two-second push-in during the intro and then held still for the whole challenge and its payoff. No cuts, no shake.
 * The participant's own view (and first/third person) comes back the moment the session ends.
 */
public final class TrainingCamera {
    @Nullable private static CameraType restore;
    private static long since;

    private TrainingCamera() {}

    static void begin() {
        if (since == 0) since = System.nanoTime();
    }

    public static boolean active() {
        return TrainingClient.attempt() != null && since != 0;
    }

    public static void tick(Minecraft mc) {
        boolean on = active() && mc.player != null && mc.player.isAlive();
        if (on && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            if (restore == null) restore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (!on) reset();
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (restore != null && mc.options != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(restore);
        restore = null;
        since = 0;
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        TrainingClient.Attempt a = TrainingClient.attempt();
        if (a == null || since == 0) return null;
        TrainingClient.advance();
        Vec3 cam = a.start.camera(), look = a.start.look();
        // The intro: a slow push in from a little further back and higher, eased; then the shot holds.
        double introMs = Math.max(1, a.start.introMs());
        double k = a.state == TrainingNet.INTRO ? Mth.clamp((a.clock + introMs) / introMs, 0, 1) : 1;
        k = k * k * (3 - 2 * k);
        Vec3 back = cam.subtract(look).normalize().scale(2.6).add(0, 0.9, 0);
        Vec3 pos = cam.add(back.scale(1 - k));
        Vec3 dir = look.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        // Blended in from the player's own view over the first moments (the realm's fade covers the rest).
        double in = Mth.clamp((System.nanoTime() - since) / 4e8, 0, 1);
        in = in * in * (3 - 2 * in);
        Vec3 p = vanillaPos.lerp(pos, in);
        return new double[] {p.x, p.y, p.z, vanillaYaw + Mth.wrapDegrees(yaw - vanillaYaw) * in, Mth.lerp(in, vanillaPitch, pitch)};
    }
}
