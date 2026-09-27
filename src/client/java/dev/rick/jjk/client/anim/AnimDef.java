package dev.rick.jjk.client.anim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A keyframed pose animation. Rotations are in degrees and override the model's own pose, blended in and out so
 * transitions from walking/idle are smooth. Parts without keyframes are left to vanilla animation.
 */
public final class AnimDef {
    public record Key(float time, float x, float y, float z) {}

    public final String name;
    public final float duration;
    /** Hold the final pose until another animation replaces it (charging, guarding). */
    public final boolean hold;
    public final float blendIn;
    public final float blendOut;
    final Map<Part, List<Key>> tracks = new EnumMap<>(Part.class);

    private AnimDef(String name, float duration, boolean hold, float blendIn, float blendOut) {
        this.name = name;
        this.duration = duration;
        this.hold = hold;
        this.blendIn = blendIn;
        this.blendOut = blendOut;
    }

    public static Builder builder(String name, float duration) {
        return new Builder(name, duration);
    }

    /** Samples a part's rotation (degrees) at {@code time}, or null if the part isn't animated. */
    public float[] sample(Part part, float time) {
        List<Key> keys = tracks.get(part);
        if (keys == null || keys.isEmpty()) return null;
        if (time <= keys.getFirst().time()) return new float[]{keys.getFirst().x(), keys.getFirst().y(), keys.getFirst().z()};
        for (int i = 0; i < keys.size() - 1; i++) {
            Key a = keys.get(i), b = keys.get(i + 1);
            if (time <= b.time()) {
                float f = (time - a.time()) / Math.max(1e-4f, b.time() - a.time());
                f = ease(f);
                return new float[]{a.x() + (b.x() - a.x()) * f, a.y() + (b.y() - a.y()) * f, a.z() + (b.z() - a.z()) * f};
            }
        }
        Key last = keys.getLast();
        return new float[]{last.x(), last.y(), last.z()};
    }

    /** Smooth-step easing: snappy strikes that still read clearly. */
    private static float ease(float f) {
        return f * f * (3 - 2 * f);
    }

    public boolean animates(Part part) {
        return tracks.containsKey(part);
    }

    public static final class Builder {
        private final String name;
        private final float duration;
        private boolean hold;
        private float blendIn = 1.5f, blendOut = 3f;
        private final Map<Part, List<Key>> tracks = new EnumMap<>(Part.class);

        private Builder(String name, float duration) {
            this.name = name;
            this.duration = duration;
        }

        public Builder hold() {
            this.hold = true;
            return this;
        }

        public Builder blend(float in, float out) {
            this.blendIn = in;
            this.blendOut = out;
            return this;
        }

        public Builder key(Part part, float time, float x, float y, float z) {
            tracks.computeIfAbsent(part, p -> new ArrayList<>()).add(new Key(time, x, y, z));
            return this;
        }

        public AnimDef build() {
            AnimDef def = new AnimDef(name, duration, hold, blendIn, blendOut);
            for (var e : tracks.entrySet()) {
                List<Key> sorted = new ArrayList<>(e.getValue());
                sorted.sort((a, b) -> Float.compare(a.time(), b.time()));
                def.tracks.put(e.getKey(), sorted);
            }
            return def;
        }
    }

    /** A left/right mirrored copy (for alternating jabs). */
    public AnimDef mirrored(String newName) {
        AnimDef m = new AnimDef(newName, duration, hold, blendIn, blendOut);
        for (var e : tracks.entrySet()) {
            Part target = switch (e.getKey()) {
                case RIGHT_ARM -> Part.LEFT_ARM;
                case LEFT_ARM -> Part.RIGHT_ARM;
                case RIGHT_LEG -> Part.LEFT_LEG;
                case LEFT_LEG -> Part.RIGHT_LEG;
                default -> e.getKey();
            };
            List<Key> keys = new ArrayList<>();
            for (Key k : e.getValue()) keys.add(new Key(k.time(), k.x(), -k.y(), -k.z()));
            m.tracks.put(target, keys);
        }
        return m;
    }
}
