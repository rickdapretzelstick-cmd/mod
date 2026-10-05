package dev.rick.jjk.progression.tool.rifle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the rifle actually points: the shooter's look, drifted by the scope's sway. The same function runs on the
 * server (which decides the hit) and the client (which draws the reticle), from the same inputs, so what the scope shows
 * is where the round goes. The sway is a slow figure-eight that shrinks as the aim settles.
 */
public final class RifleAim {
    private RifleAim() {}

    /**
     * {yaw, pitch} offsets in degrees at game time {@code time} for shooter {@code id}, aiming for {@code aimTicks} with
     * {@code sway} degrees of drift that settles over {@code settle} ticks ({@code scoped}: through the scope; else a hip
     * shot, which never settles below its own sway).
     */
    public static float[] drift(long time, int id, int aimTicks, float sway, int settle, boolean scoped, float hipSway) {
        float settled = Mth.clamp(aimTicks / (float) Math.max(1, settle), 0f, 1f);
        float amp = scoped ? sway * (1f - 0.75f * settled) : hipSway;
        float t = time + id * 37f;
        return new float[] {amp * Mth.sin(t * 0.11f), amp * 0.7f * Mth.sin(t * 0.19f + 1.3f)};
    }

    /** The look direction turned by those offsets. */
    public static Vec3 apply(float yRot, float xRot, float[] drift) {
        return Vec3.directionFromRotation(xRot + drift[1], yRot + drift[0]);
    }
}
