package dev.rick.jjk.client.clash;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.cinematic.DomainCinematic;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * While a domain clash is being played, the camera frames the battle itself: side-on to the line between the two
 * domains, looking at the point where they collide, with you on the right (where your lanes are) and your opponent on
 * the left. It eases in when the rhythm section starts and hands the normal camera back when the clash ends.
 */
public final class ClashCamera {
    private static float blend;
    @Nullable private static CameraType restore;
    /** Smoothed framing, so meter swings move the shot gently. */
    @Nullable private static Vec3 smoothPos;
    @Nullable private static Vec3 smoothLook;

    private ClashCamera() {}

    public static void tick(Minecraft mc) {
        boolean on = ClashClient.playing() && !DomainCinematic.versusShowing() && target(mc) != null;
        blend = Mth.clamp(blend + (on ? 0.08f : -0.12f), 0, 1);
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
        blend = 0;
        restore = null;
        smoothPos = smoothLook = null;
    }

    /**
     * Where the camera should be this frame, blended with where vanilla put it: {x, y, z, yaw, pitch}, or null when the
     * clash camera is not in use.
     */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        if (blend <= 0) return null;
        Minecraft mc = Minecraft.getInstance();
        Vec3[] t = target(mc);
        if (t == null) return null;
        smoothPos = smoothPos == null ? t[0] : smoothPos.lerp(t[0], 0.08);
        smoothLook = smoothLook == null ? t[1] : smoothLook.lerp(t[1], 0.08);
        Vec3 dir = smoothLook.subtract(smoothPos);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        float k = blend * blend * (3 - 2 * blend);
        Vec3 pos = vanillaPos.lerp(smoothPos, k);
        return new double[] {pos.x, pos.y, pos.z, vanillaYaw + Mth.wrapDegrees(yaw - vanillaYaw) * k, Mth.lerp(k, vanillaPitch, pitch)};
    }

    /** {camera position, point to look at}, or null if the two domains aren't both known here. */
    @Nullable
    private static Vec3[] target(Minecraft mc) {
        ClashClient.View v = ClashClient.view();
        if (v == null || v.domains.length < 2 || mc.level == null) return null;
        ClientState.Domain a = ClientState.DOMAINS.get(v.domains[0]), b = ClientState.DOMAINS.get(v.domains[1]);
        if (a == null || b == null || a.center == null || b.center == null) return null;
        Vec3 ab = new Vec3(b.center.x - a.center.x, 0, b.center.z - a.center.z);
        double span = ab.length();
        if (span < 0.5) return null;
        Vec3 n = ab.scale(1 / span);
        Vec3 front = a.center.add(b.center.subtract(a.center).scale(0.5 + v.shownMeter * 0.35)).add(0, 1.4, 0);
        Vec3 mine = v.local == 1 ? b.center : a.center;
        Vec3 side = new Vec3(-n.z, 0, n.x);
        // Look along -side: screen-right is then (side.z, 0, -side.x); put your own domain on the right.
        Vec3 right = new Vec3(side.z, 0, -side.x);
        if (mine.subtract(front).dot(right) < 0) side = side.reverse();
        ClientState.Domain[] domains = {a, b};
        for (double dist = Math.min(18, span * 0.8 + 6); dist >= 4; dist -= 1) {
            Vec3 pos = front.add(side.scale(dist)).add(0, 2 + dist * 0.2, 0);
            if (clear(mc, pos, domains)) return new Vec3[] {pos, front};
        }
        return new Vec3[] {front.add(side.scale(4)).add(0, 2.5, 0), front};
    }

    /** Inside a domain, not inside any barrier wall or solid block. */
    private static boolean clear(Minecraft mc, Vec3 pos, ClientState.Domain[] domains) {
        boolean inside = false;
        for (ClientState.Domain d : domains) {
            double outer = d.radius + d.thickness, dist = pos.distanceTo(d.center);
            if (dist > d.radius - 1.2 && dist < outer + 1.2) return false;
            if (dist < d.radius - 1.2) inside = true;
        }
        BlockPos p = BlockPos.containing(pos);
        return inside && mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()
                && mc.level.getBlockState(p.above()).getCollisionShape(mc.level, p.above()).isEmpty();
    }
}
