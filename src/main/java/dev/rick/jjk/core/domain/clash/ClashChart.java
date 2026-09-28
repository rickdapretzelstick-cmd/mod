package dev.rick.jjk.core.domain.clash;

import java.util.Random;

/**
 * A sequence of timed prompts: note {@code i} is lane {@code lanes[i]} (0 ←, 1 ↓, 2 ↑, 3 →) and should be hit at
 * {@code times[i]} ticks after the clash clock starts. Every participant plays the same chart, so the only difference
 * between them is how well they play it.
 */
public record ClashChart(float[] times, byte[] lanes) {
    public int size() {
        return times.length;
    }

    public float end() {
        return times.length == 0 ? 0 : times[times.length - 1];
    }

    /**
     * Notes on beats and half beats: sparse at first, denser as the clash heats up, never two in the same lane
     * back-to-back on a half beat (so every pattern is playable).
     */
    public static ClashChart generate(long seed, float bpm, int count) {
        Random rnd = new Random(seed);
        float beat = 1200f / Math.max(40f, bpm); // ticks per beat
        float[] times = new float[count];
        byte[] lanes = new byte[count];
        float t = beat; // one beat of lead-in after the countdown
        int lastLane = -1;
        for (int i = 0; i < count; i++) {
            float progress = count <= 1 ? 1 : (float) i / (count - 1);
            // Early notes land on every beat; later ones increasingly on half beats.
            boolean half = i > 0 && rnd.nextFloat() < 0.15f + 0.5f * progress;
            if (i > 0) t += half ? beat / 2 : beat;
            int lane;
            do {
                lane = rnd.nextInt(4);
            } while (half && lane == lastLane);
            times[i] = t;
            lanes[i] = (byte) lane;
            lastLane = lane;
        }
        return new ClashChart(times, lanes);
    }
}
