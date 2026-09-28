package dev.rick.jjk.test;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A film camera for the presentation recording: instead of sitting right behind the player (where their body hides the
 * target), it orbits to one side and slightly ahead, looking at the point between the player and the action.
 */
public final class ShowcaseCamera {
    public static volatile boolean enabled;
    /** Angle around the player from straight behind (degrees, positive = to their right). */
    public static volatile float angle = 55;
    public static volatile float distance = 6.5f;
    public static volatile float height = 2.2f;
    /** How far ahead of the player the shot is centred. */
    public static volatile float lookAhead = 3f;

    /** Holds the current shot still (for moves that cross the arena, like teleports). */
    public static volatile boolean frozen;

    @Nullable private static Vec3 smoothPos;
    @Nullable private static double[] last;

    private ShowcaseCamera() {}

    public static void set(float angle, float distance, float height, float lookAhead) {
        ShowcaseCamera.angle = angle;
        ShowcaseCamera.distance = distance;
        ShowcaseCamera.height = height;
        ShowcaseCamera.lookAhead = lookAhead;
        enabled = true;
        frozen = false;
    }

    public static void off() {
        enabled = false;
        frozen = false;
        smoothPos = null;
        last = null;
    }

    /** {x, y, z, yaw, pitch} for this frame, or null to leave the camera alone. */
    @Nullable
    public static double[] apply(float partial) {
        if (!enabled) return null;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || dev.rick.jjk.client.clash.ClashClient.playing() || dev.rick.jjk.client.cinematic.DomainCinematic.fullscreen()) return null;
        if (frozen && last != null) return last;
        Vec3 base = p.getPosition(partial);
        float yaw = (float) Math.toRadians(p.getYRot(partial));
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 focus = base.add(fwd.scale(lookAhead)).add(0, 1.1, 0);
        double a = Math.toRadians(angle);
        Vec3 dir = fwd.scale(-Math.cos(a)).add(right.scale(-Math.sin(a)));
        Vec3 pos = focus.add(dir.scale(distance)).add(0, height, 0);
        smoothPos = smoothPos == null || smoothPos.distanceTo(pos) > 12 ? pos : smoothPos.lerp(pos, 0.25);
        Vec3 d = focus.subtract(smoothPos);
        float camYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float camPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        last = new double[] {smoothPos.x, smoothPos.y, smoothPos.z, camYaw, camPitch};
        return last;
    }
}
