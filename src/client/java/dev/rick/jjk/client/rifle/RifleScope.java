package dev.rick.jjk.client.rifle;

import dev.rick.jjk.client.gear.CursedGear;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.minecraft.client.Minecraft;

/**
 * The local player's rifle scope, smoothed per rendered frame (never per tick, so the zoom never steps).
 *
 * <p>Raising it runs one curve, {@link #value}, from 0 to 1 in {@link #IN_SECONDS}: the first part is the rifle
 * coming up to the eye (first person) while the field of view tightens; the overlay fades in over the last part.
 * Lowering runs it back in {@link #OUT_SECONDS}. It is up only while the server says the rifle is aiming (Aimed Shot
 * held) and the rifle is drawn: firing, sprinting, holstering, Combat Mode off, death, a new world or a disconnect all
 * bring it down, and nothing it changes (FOV, sensitivity, overlay, crosshair) outlives it.
 *
 * <p>The aim never moves with any of this: the scope's centre is the screen's centre, which is the view direction the
 * server fires along. The view itself is never shaken while the scope is up.
 */
public final class RifleScope {
    public static final float IN_SECONDS = 0.20f, OUT_SECONDS = 0.13f;
    /** Field of view through the scope, as a share of the normal one. */
    public static final float ZOOM = 0.32f;

    private static float value;
    private static long lastNanos;

    private RifleScope() {}

    /** Whether the scope should be up right now. */
    public static boolean wanted(Minecraft mc) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        if (!CursedGear.rifleDrawn(mc.player) || mc.player.isSprinting()) return false;
        RifleClient.State s = RifleClient.state(mc.player.getId());
        return s != null && s.phase == RifleServer.Phase.AIM;
    }

    /** Advances the curve to this frame (called from wherever it is read first in a frame: idempotent within one). */
    public static float value() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        boolean up = wanted(mc);
        value = up ? Math.min(1, value + dt / IN_SECONDS) : Math.max(0, value - dt / OUT_SECONDS);
        if (mc.player == null) value = 0;
        return value;
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    /** The field-of-view multiplier this frame. */
    public static float zoom() {
        float t = ease(Math.min(1, value() / 0.8f));
        return 1 + (ZOOM - 1) * t;
    }

    /** How much of the overlay shows (it fades in over the last part of the rise). */
    public static float overlay() {
        return ease(Math.max(0, (value - 0.45f) / 0.55f));
    }

    /** Mouse sensitivity while scoped: scaled with the zoom so the view moves as far on screen per inch, a touch quicker. */
    public static float sensitivity() {
        float z = zoom();
        if (z >= 0.999f) return 1;
        return Math.min(1f, z * 1.15f);
    }

    /** No shaking the view while the scope is (even partly) up: what's centred is what's hit. */
    public static boolean steady() {
        return value > 0;
    }

    public static void reset() {
        value = 0;
        lastNanos = 0;
    }
}
