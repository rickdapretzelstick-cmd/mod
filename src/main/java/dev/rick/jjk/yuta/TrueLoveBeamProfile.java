package dev.rick.jjk.yuta;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of True Love Beam over its life, shared by the server's hit test and the client's drawing so what you see is
 * exactly what hits. It is a torrent, not a laser: a SQUARE prism of cursed energy {@code 2 * half} blocks across (5x5 at
 * the default half-width of 2.5), upright (its sides are vertical and horizontal whichever way it is aimed, so from the
 * side it is five blocks tall and from above five wide), swelling to full size a couple of blocks out of Rika's mouth and
 * ending in a short blunt nose. Over time it shoots out to its full length in {@code grow} ticks, holds at full thickness
 * for the {@code hold} ticks of the blast, and only then collapses over {@code collapse} ticks.
 */
public final class TrueLoveBeamProfile {
    /** Rika's open mouth in her beam pose, from her feet (measured on her rig at the "fire" frame of rika_beam). */
    public static final double MOUTH_UP = 3.45, MOUTH_FORWARD = 1.0;
    /** Where she plants herself: this far behind Yuta, so the beam clears his head. */
    public static final double BEHIND = 1.4;
    /** The swell from the mouth, in blocks, and the nose's length. */
    static final double SWELL = 2.5, NOSE = 1.5;

    private TrueLoveBeamProfile() {}

    /** How far the front has reached, {@code t} ticks after firing. */
    public static double front(double length, int grow, float t) {
        return length * Mth.clamp((t + 1f) / Math.max(1, grow), 0f, 1f);
    }

    /**
     * Overall thickness, 0-1, {@code t} ticks into a beam that holds full for {@code hold} ticks then collapses over
     * {@code collapse} (so it is drawn for {@code hold + collapse}).
     */
    public static float scale(float t, int hold, int collapse) {
        if (t < 0 || t >= hold + collapse) return 0f;
        float in = Mth.clamp(0.6f + 0.4f * t / 2f, 0f, 1f);
        float out = t < hold ? 1f : Mth.clamp((hold + collapse - t) / (float) Math.max(1, collapse), 0f, 1f);
        return in * out;
    }

    /** Whether the beam does damage at {@code t}: only while it holds (the collapse is only light). */
    public static boolean live(float t, int hold) {
        return t >= 0 && t < hold;
    }

    /** The half-width {@code s} blocks from the mouth, when the front is at {@code front}. */
    public static double half(double s, double front, double half) {
        if (s < 0 || s > front) return 0;
        double swell = 0.45 + 0.55 * smooth(Math.min(1, s / SWELL));
        double left = front - s;
        double nose = left >= NOSE ? 1 : 0.55 + 0.45 * Math.sqrt(Math.max(0, left / NOSE));
        return half * swell * nose;
    }

    /** The beam's square frame: {@code [side, up]}, both unit and square to {@code dir}; up is as vertical as it can be. */
    public static Vec3[] frame(Vec3 dir) {
        Vec3 n = dir.normalize();
        Vec3 side = Math.abs(n.y) > 0.97 ? new Vec3(1, 0, 0).cross(n) : n.cross(new Vec3(0, 1, 0));
        side = side.normalize();
        Vec3 up = side.cross(n).normalize();
        return new Vec3[] {side, up};
    }

    /**
     * Whether a body of half-width {@code body} at {@code p} is inside the beam right now: within its length, and within
     * the square (each of its side and up offsets inside the half-width).
     */
    public static boolean contains(Vec3 origin, Vec3 dir, double front, double half, float scale, Vec3 p, double body) {
        if (scale <= 0) return false;
        Vec3 rel = p.subtract(origin);
        double s = rel.dot(dir);
        if (s < -body || s > front + body) return false;
        double h = half(Mth.clamp(s, 0, front), front, half) * scale;
        Vec3[] f = frame(dir);
        return Math.abs(rel.dot(f[0])) <= h + body && Math.abs(rel.dot(f[1])) <= h + body;
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }
}
