package dev.rick.jjk.client.training;

import dev.rick.jjk.config.JJKConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A strength contest's cinematic shot: side-on to the two locked together, framing both, a slow orbit of about 25
 * degrees across the contest with a gentle push-in, and the field of view easing from 65 to 58. It never goes through
 * a wall: the shot is pulled in along its own line until the view is clear. With Reduced Camera Motion on it is one still
 * shot (no orbit, no push-in, no FOV change). The HUD stays fixed either way.
 */
public final class ContestCamera {
    @Nullable private static CameraType restore;
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

    /** 0 at the start of the contest to 1 at its end. */
    private static double progress(TrainingClient.Attempt a) {
        double total = a.start.introMs() + a.start.durationMs();
        return Mth.clamp((a.clock + a.start.introMs()) / Math.max(1, total), 0, 1);
    }

    private static boolean reduced() {
        return JJKConfig.get().client.reducedCameraMotion;
    }

    /** The field of view to use (degrees), or NaN when not in use. */
    public static float fov() {
        TrainingClient.Attempt a = TrainingClient.attempt();
        if (a == null || !active()) return Float.NaN;
        if (reduced()) return FOV_STILL;
        double k = progress(a);
        k = k * k * (3 - 2 * k);
        return (float) Mth.lerp(k, FOV_START, FOV_END);
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
        Vec3 line = foe.subtract(me).multiply(1, 0, 1);
        if (line.lengthSqr() < 1e-4) line = new Vec3(0, 0, 1);
        Vec3 n = line.normalize();
        Vec3 mid = me.lerp(foe, 0.5);
        double span = Math.max(2, me.distanceTo(foe));
        if (side == 0) side = clearFrom(mc, mid, perpendicular(n, 1), span) >= clearFrom(mc, mid, perpendicular(n, -1), span) ? 1 : -1;
        double k = reduced() ? 0 : progress(a);
        double eased = k * k * (3 - 2 * k);
        // Side-on, a little behind the contestant, turning ~25 degrees across the contest.
        double base = Math.toRadians(-20 * side);
        double orbit = base + Math.toRadians(ORBIT_DEGREES) * eased * side;
        Vec3 out = rotate(perpendicular(n, side), orbit);
        double dist = (span * 1.1 + 3.2) * (1 - 0.18 * eased);
        Vec3 want = mid.add(out.scale(dist)).add(0, 1.1 + span * 0.15, 0);
        Vec3 pos = clip(mc, mid, want);
        Vec3 dir = mid.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        // Into the shot from the player's own view over the first moments.
        double in = reduced() ? 1 : Mth.clamp((System.nanoTime() - since) / 3e8, 0, 1);
        in = in * in * (3 - 2 * in);
        Vec3 p = vanillaPos.lerp(pos, in);
        return new double[] {p.x, p.y, p.z, vanillaYaw + Mth.wrapDegrees(yaw - vanillaYaw) * in, Mth.lerp(in, vanillaPitch, pitch)};
    }

    private static Vec3 perpendicular(Vec3 n, int side) {
        return new Vec3(-n.z * side, 0, n.x * side);
    }

    private static Vec3 rotate(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
    }

    /** How far the view is clear from {@code from} towards {@code dir}. */
    private static double clearFrom(Minecraft mc, Vec3 from, Vec3 dir, double span) {
        Vec3 to = from.add(dir.scale(span * 1.1 + 3.2)).add(0, 1.1, 0);
        return clip(mc, from, to).distanceTo(from);
    }

    /** {@code want}, or pulled in along the line from {@code from} so it stays out of walls (half a block off them). */
    static Vec3 clip(Minecraft mc, Vec3 from, Vec3 want) {
        var hit = mc.level.clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return want;
        Vec3 d = want.subtract(from);
        double len = d.length();
        double ok = Math.max(1.2, hit.getLocation().distanceTo(from) - 0.5);
        Vec3 pos = from.add(d.scale(Math.min(1, ok / Math.max(1e-6, len))));
        BlockPos b = BlockPos.containing(pos);
        if (!mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty()) pos = pos.add(0, 1, 0);
        return pos;
    }
}
