package dev.rick.jjk.client.clash;

import dev.rick.jjk.client.cinematic.DomainCinematic;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the clash readable while it is being played. The effects still play, but nothing is allowed to wash over the
 * whole screen: glows that would fill the view from where the camera is are dimmed toward what they look like from a
 * distance, and full-screen flashes are pushed out to the edges of the screen, away from the lanes.
 */
public final class ClashFocus {
    /** Largest share of the view (glow radius over distance) a glow may cover at full strength. */
    private static final double MAX_COVER = 0.3;
    /** What is left of a glow the camera is inside of. */
    private static final float INSIDE = 0.12f;

    private ClashFocus() {}

    /** The rhythm section is on screen (after any versus card). */
    public static boolean active() {
        return ClashClient.playing() && !DomainCinematic.versusShowing();
    }

    /** Alpha multiplier for a world glow of radius {@code size} at {@code pos}, seen from {@code cam}. */
    public static float world(Vec3 cam, Vec3 pos, float size) {
        if (!active() || size <= 0) return 1;
        double d = cam.distanceTo(pos);
        if (d <= size) return INSIDE;
        double cover = size / d;
        return cover <= MAX_COVER ? 1 : (float) Mth.clamp(MAX_COVER / cover, INSIDE, 1);
    }
}
