package dev.rick.jjk.client.anim;

/** One channel (rotation, position or scale) of one bone: keyed values over time, each eased in from the one before. */
public final class Track {
    final float[] times;
    final float[][] values;
    final Easing[] eases;
    /** A limb rotation: its keys are canonical Euler angles and it is blended the short way round (never a spin). */
    final boolean angular;

    Track(float[] times, float[][] values, Easing[] eases) {
        this(times, values, eases, false);
    }

    Track(float[] times, float[][] values, Easing[] eases, boolean angular) {
        this.times = times;
        this.values = values;
        this.eases = eases;
        this.angular = angular;
    }

    private static final float PI = (float) Math.PI;

    /** {@code a} folded into (-pi, pi]. */
    public static float wrap(float a) {
        a = a % (2 * PI);
        if (a <= -PI) a += 2 * PI;
        if (a > PI) a -= 2 * PI;
        return a;
    }

    /**
     * The canonical form of a ZYX Euler rotation (radians): the solution with the middle angle in [-pi/2, pi/2], every
     * angle in (-pi, pi]. (x, y, z) and (x + pi, pi - y, z + pi) are the same rotation; the far one blends badly.
     */
    public static float[] canonical(float[] r) {
        float x = wrap(r[0]), y = wrap(r[1]), z = wrap(r[2]);
        if (Math.abs(y) > PI / 2) {
            x = wrap(x + PI);
            y = wrap(PI - y);
            z = wrap(z + PI);
        }
        return new float[] {x, y, z};
    }

    /** {@code a} toward {@code b} by {@code f}, the short way round the circle. */
    public static float lerpAngle(float a, float b, float f) {
        return a + wrap(b - a) * f;
    }

    /** Writes the value at {@code t} (ms) into {@code out}. */
    public void sample(float t, float[] out) {
        int n = times.length;
        if (t <= times[0] || n == 1) {
            copy(values[0], out);
            return;
        }
        if (t >= times[n - 1]) {
            copy(values[n - 1], out);
            return;
        }
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= t) lo = mid;
            else hi = mid;
        }
        float span = times[hi] - times[lo];
        float f = span <= 0 ? 1 : eases[hi].apply((t - times[lo]) / span);
        float[] a = values[lo], b = values[hi];
        if (angular) for (int i = 0; i < 3; i++) out[i] = lerpAngle(a[i], b[i], f);
        else for (int i = 0; i < 3; i++) out[i] = a[i] + (b[i] - a[i]) * f;
    }

    /** The index of the last key at or before {@code t}. */
    public int keyAt(float t) {
        int k = 0;
        for (int i = 0; i < times.length; i++) if (times[i] <= t + 1e-3f) k = i;
        return k;
    }

    private static void copy(float[] a, float[] out) {
        out[0] = a[0];
        out[1] = a[1];
        out[2] = a[2];
    }

    Track mirrored(boolean position) {
        float[][] v = new float[values.length][];
        for (int i = 0; i < values.length; i++) {
            float[] s = values[i];
            // A mirrored angle keeps its pitch and flips its turn and roll; an offset flips sideways; scale is unchanged.
            v[i] = position ? new float[]{-s[0], s[1], s[2]} : new float[]{s[0], -s[1], -s[2]};
        }
        return new Track(times, v, eases, angular);
    }
}
