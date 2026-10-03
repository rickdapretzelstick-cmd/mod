package dev.rick.jjk.client.clash;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The beam clash's one cinematic beat: as the two beams meet, the contestants' cameras swing out side-on to the line
 * between them, framing both sources and the collision, then hand the view straight back as the skill checks begin
 * (the dial sits over the player's own view, and nothing moves the camera while it is up).
 */
public final class BeamClashCamera {
    private static float blend;
    @Nullable private static CameraType restore;
    @Nullable private static Vec3 smoothPos, smoothLook;

    private BeamClashCamera() {}

    private static boolean wanted() {
        BeamClashClient.View v = BeamClashClient.mine();
        return v != null && v.phase == BeamClashClient.INTRO && v.phaseAge < 26;
    }

    public static void tick(Minecraft mc) {
        boolean on = wanted() && target(mc) != null;
        blend = Mth.clamp(blend + (on ? 0.14f : -0.2f), 0, 1);
        if (on && restore == null && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            restore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (!on && blend <= 0) {
            if (restore != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(restore);
            restore = null;
            smoothPos = smoothLook = null;
        }
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (restore != null && mc.options != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(restore);
        blend = 0;
        restore = null;
        smoothPos = smoothLook = null;
    }

    /** {x, y, z, yaw, pitch} blended with vanilla's camera, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        if (blend <= 0) return null;
        Vec3[] t = target(Minecraft.getInstance());
        if (t == null) return null;
        smoothPos = smoothPos == null ? t[0] : smoothPos.lerp(t[0], 0.12);
        smoothLook = smoothLook == null ? t[1] : smoothLook.lerp(t[1], 0.12);
        Vec3 dir = smoothLook.subtract(smoothPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        float k = blend * blend * (3 - 2 * blend);
        Vec3 pos = vanillaPos.lerp(smoothPos, k);
        return new double[] {pos.x, pos.y, pos.z, vanillaYaw + Mth.wrapDegrees(yaw - vanillaYaw) * k, Mth.lerp(k, vanillaPitch, pitch)};
    }

    /** {camera position, look-at}: off to the side of the clash, far enough back to see both sources. */
    @Nullable
    private static Vec3[] target(Minecraft mc) {
        BeamClashClient.View v = BeamClashClient.mine();
        if (v == null || mc.level == null) return null;
        Vec3 ab = v.bOrigin.subtract(v.aOrigin);
        Vec3 flat = new Vec3(ab.x, 0, ab.z);
        double span = flat.length();
        if (span < 1) return null;
        Vec3 n = flat.scale(1 / span);
        Vec3 look = v.point();
        Vec3 side = new Vec3(-n.z, 0, n.x);
        // Your own source on the right of the shot.
        Vec3 mine = v.local == 0 ? v.aOrigin : v.bOrigin;
        Vec3 right = new Vec3(side.z, 0, -side.x);
        if (mine.subtract(look).dot(right) < 0) side = side.reverse();
        for (double dist = Math.min(26, span * 0.6 + 6); dist >= 5; dist -= 1) {
            Vec3 pos = look.add(side.scale(dist)).add(0, 1.5 + dist * 0.15, 0);
            if (clear(mc, pos)) return new Vec3[] {pos, look};
        }
        return null;
    }

    private static boolean clear(Minecraft mc, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        return mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()
                && mc.level.getBlockState(p.above()).getCollisionShape(mc.level, p.above()).isEmpty();
    }
}
