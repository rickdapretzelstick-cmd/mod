package dev.rick.jjk.client.anim;

import dev.rick.jjk.client.anim.rig.Bone;

/**
 * What the playing clips want from each bone for one rendered frame, as a value and how strongly it replaces the
 * bone's own (vanilla) pose: final = lerp(vanilla, value, weight), per channel. Rotations are radians, positions
 * pixels (right, up, forward), scales factors.
 */
public final class PoseFrame {
    public final float[][][] value = new float[3][Bone.COUNT][3];
    public final float[][] weight = new float[3][Bone.COUNT];
    /** 0..1: how much the head keeps looking where the player looks (only where no clip poses the head). */
    public float look;
    /**
     * A model rig's bones (Rika's jaw, tail, fingers...), by name: [channel] -> {x, y, z, weight}. Rotations radians,
     * offsets pixels, in the model's own (Blockbench) axes. A bone missing here is at rest.
     */
    public final java.util.Map<String, float[][]> named = new java.util.HashMap<>();

    /** A model bone's channel blended over rest (rotation/offset 0, scale 1), or null when no clip moves it. */
    @org.jetbrains.annotations.Nullable
    public float[] named(String bone, int channel) {
        float[][] b = named.get(bone);
        if (b == null || b[channel] == null) return null;
        float[] v = b[channel];
        float base = channel == Clip.SCALE ? 1 : 0;
        return new float[]{base + (v[0] - base) * v[3], base + (v[1] - base) * v[3], base + (v[2] - base) * v[3]};
    }

    void addNamed(int channel, String bone, float[] v, float a) {
        if (a <= 0) return;
        float[][] b = named.computeIfAbsent(bone, k -> new float[3][]);
        float[] dst = b[channel];
        if (dst == null) {
            b[channel] = new float[]{v[0], v[1], v[2], a};
            return;
        }
        float w = dst[3], w2 = 1 - (1 - w) * (1 - a);
        for (int k = 0; k < 3; k++) dst[k] = (dst[k] * w * (1 - a) + v[k] * a) / w2;
        dst[3] = w2;
    }

    /** Sets a bone's rotation outright (degrees), for code-built poses and tests. */
    public PoseFrame rot(Bone b, float xDeg, float yDeg, float zDeg) {
        float k = (float) (Math.PI / 180);
        add(Clip.ROT, b, new float[]{xDeg * k, yDeg * k, zDeg * k}, 1);
        return this;
    }

    /** Sets a bone's offset outright (pixels: right, up, forward). */
    public PoseFrame pos(Bone b, float right, float up, float forward) {
        add(Clip.POS, b, new float[]{right, up, forward}, 1);
        return this;
    }

    public boolean any() {
        if (!named.isEmpty()) return true;
        for (float[] ch : weight) for (float w : ch) if (w > 0) return true;
        return false;
    }

    /** Layers {@code v} over whatever is already here with weight {@code a}. */
    void add(int channel, Bone b, float[] v, float a) {
        if (a <= 0) return;
        int i = b.ordinal();
        float w = weight[channel][i];
        float[] dst = value[channel][i];
        if (w <= 0) {
            dst[0] = v[0];
            dst[1] = v[1];
            dst[2] = v[2];
            weight[channel][i] = a;
            return;
        }
        // lerp(lerp(base, V, W), v, a) == lerp(base, V', W') with W' = 1 - (1-W)(1-a).
        float w2 = 1 - (1 - w) * (1 - a);
        for (int k = 0; k < 3; k++) dst[k] = (dst[k] * w * (1 - a) + v[k] * a) / w2;
        weight[channel][i] = w2;
    }
}
