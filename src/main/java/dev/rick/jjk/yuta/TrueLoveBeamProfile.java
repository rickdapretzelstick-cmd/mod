package dev.rick.jjk.yuta;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of True Love Beam over its life, shared by the server's hit test and the client's drawing so what you see is
 * exactly what hits. Along its length it swells from Rika's mouth to its full radius over the first few blocks and
 * ends in a rounded nose (JJS: a huge pink bullet with a white-hot core). Over time it shoots out to its full length in
 * {@code grow} ticks, holds, and collapses to nothing over its last {@code collapse} ticks.
 */
public final class TrueLoveBeamProfile {
    /** Rika's open mouth in her beam pose, from her feet (measured on her rig at the "fire" frame of rika_beam). */
    public static final double MOUTH_UP = 3.45, MOUTH_FORWARD = 1.0;
    /** Where she plants herself: this far behind Yuta, so the beam clears his head. */
    public static final double BEHIND = 1.4;
    /** The swell from the mouth, in blocks, and the nose's length. */
    static final double SWELL = 5, NOSE = 3;

    private TrueLoveBeamProfile() {}

    /** How far the front has reached, {@code t} ticks after firing. */
    public static double front(double length, int grow, float t) {
        return length * Mth.clamp((t + 1f) / Math.max(1, grow), 0f, 1f);
    }

    /** Overall thickness, 0-1, {@code t} ticks into a beam of {@code life} ticks. */
    public static float scale(float t, int life, int collapse) {
        if (t < 0 || t >= life) return 0f;
        float in = Mth.clamp(0.55f + 0.45f * t / 2f, 0f, 1f);
        float out = Mth.clamp((life - t) / (float) Math.max(1, collapse), 0f, 1f);
        return in * out;
    }

    /** The radius {@code s} blocks from the mouth, when the front is at {@code front}. */
    public static double radius(double s, double front, double radius) {
        if (s < 0 || s > front) return 0;
        double swell = 0.35 + 0.65 * smooth(Math.min(1, s / SWELL));
        double left = front - s;
        double nose = left >= NOSE ? 1 : Math.sqrt(Math.max(0, left / NOSE));
        return radius * swell * nose;
    }

    /** Whether a body of half-width {@code body} at {@code p} is inside the beam right now. */
    public static boolean contains(Vec3 origin, Vec3 dir, double front, double radius, float scale, Vec3 p, double body) {
        if (scale <= 0) return false;
        Vec3 rel = p.subtract(origin);
        double s = rel.dot(dir);
        if (s < -body || s > front + body) return false;
        double r = radius(Mth.clamp(s, 0, front), front, radius) * scale;
        double off = rel.subtract(dir.scale(s)).length();
        return off <= r + body;
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }
}
