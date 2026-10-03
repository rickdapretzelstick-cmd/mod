package dev.rick.jjk.client.clash;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The beam clash's camera: from the moment the two beams meet, the contestants' cameras swing out to a distant side-on
 * shot of the line between them, framing both fighters, both beams and the collision point, and hold it for the whole
 * clash: the intro, the sustained struggle (the skill-check dial sits over it) and the winner's breakthrough or the
 * overpower. Nothing else moves the view meanwhile (this is applied last, over the domain clash camera and cinematics).
 * When the clash ends, is cancelled, or the player dies or leaves, the view blends straight back to their own.
 */
public final class BeamClashCamera {
    private static float blend;
    @Nullable private static CameraType restore;
    @Nullable private static Vec3 smoothPos, smoothLook;

    private BeamClashCamera() {}

    private static boolean wanted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) return false;
        BeamClashClient.View v = BeamClashClient.mine();
        return v != null && (v.phase == BeamClashClient.INTRO || v.phase == BeamClashClient.DUEL || v.phase == BeamClashClient.RESOLVE);
    }

    /** Whether the wide clash shot has the view right now (tests, the HUD). */
    public static boolean active() {
        return blend > 0;
    }

    public static void tick(Minecraft mc) {
        // Once found, a shot is kept for the clash even if a wall briefly blocks the search (no cutting in and out).
        boolean on = wanted() && (smoothPos != null || target(mc) != null);
        blend = Mth.clamp(blend + (on ? 0.14f : -0.2f), 0, 1);
        if (on && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            // Held every tick: another camera (a domain cinematic, F5) switching the view mid-clash can't take it over.
            if (restore == null) restore = mc.options.getCameraType();
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
        if (t == null && smoothPos == null) return null;
        if (t != null) {
            // Eased, so the shot drifts with the clash rather than jumping.
            smoothPos = smoothPos == null ? t[0] : smoothPos.lerp(t[0], 0.12);
            smoothLook = smoothLook == null ? t[1] : smoothLook.lerp(t[1], 0.12);
        }
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
        // Far enough out that both sources (and the fighters at them) fit the frame wherever the collision has been
        // pushed to; never closer than a wide shot, pulled in only as far as walls force it.
        Vec3 mid = v.aOrigin.add(v.bOrigin).scale(0.5);
        Vec3 aim = mid.lerp(look, 0.35);
        for (double dist = Mth.clamp(span * 1.05 + 8, 16, 44); dist >= 8; dist -= 1) {
            Vec3 pos = aim.add(side.scale(dist)).add(0, 2 + dist * 0.18, 0);
            if (clear(mc, pos)) return new Vec3[] {pos, aim};
        }
        // Boxed in: the widest clear spot straight up from the collision.
        for (double up = 12; up >= 4; up -= 1) {
            Vec3 pos = aim.add(side.scale(4)).add(0, up, 0);
            if (clear(mc, pos)) return new Vec3[] {pos, aim};
        }
        return null;
    }

    private static boolean clear(Minecraft mc, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        return mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()
                && mc.level.getBlockState(p.above()).getCollisionShape(mc.level, p.above()).isEmpty();
    }
}
