package dev.rick.jjk.client.anim;

/** The pose override computed for one entity for one rendered frame (radians). */
public final class PoseFrame {
    public final float[][] rot = new float[Part.values().length][3];
    public final float[] weight = new float[Part.values().length];
    /** 0..1: how far the body is laid flat on its back (knockdown). */
    public float lieDown;

    public boolean any() {
        if (lieDown > 0) return true;
        for (float w : weight) if (w > 0) return true;
        return false;
    }

    /** Blends a pose (degrees) into this frame with the given weight, on top of what's there. */
    void blend(Part part, float xDeg, float yDeg, float zDeg, float w) {
        if (w <= 0) return;
        int i = part.ordinal();
        float rad = (float) (Math.PI / 180);
        if (weight[i] <= 0) {
            rot[i][0] = xDeg * rad;
            rot[i][1] = yDeg * rad;
            rot[i][2] = zDeg * rad;
            weight[i] = w;
            return;
        }
        // Later layers (the active animation) win in proportion to their weight.
        rot[i][0] += (xDeg * rad - rot[i][0]) * w;
        rot[i][1] += (yDeg * rad - rot[i][1]) * w;
        rot[i][2] += (zDeg * rad - rot[i][2]) * w;
        weight[i] = Math.max(weight[i], w);
    }
}
