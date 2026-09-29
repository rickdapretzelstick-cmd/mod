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
    /**
     * A keyframe. {@code ease} shapes the move into it: {@link #SMOOTH} eases both ends, {@link #STRIKE} starts slow and
     * whips into the key (a punch landing), {@link #SETTLE} leaves fast and slows into it (recoil, follow-through),
     * {@link #LINEAR} is constant speed (spins), {@link #OVERSHOOT} flies a little past and snaps back.
     */
    public record Key(float time, float x, float y, float z, int ease) {
        public Key(float time, float x, float y, float z) {
            this(time, x, y, z, SMOOTH);
        }
    }

    public static final int SMOOTH = 0, STRIKE = 1, SETTLE = 2, LINEAR = 3, OVERSHOOT = 4;

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
                f = ease(f, b.ease());
                return new float[]{a.x() + (b.x() - a.x()) * f, a.y() + (b.y() - a.y()) * f, a.z() + (b.z() - a.z()) * f};
            }
        }
        Key last = keys.getLast();
        return new float[]{last.x(), last.y(), last.z()};
    }

    private static float ease(float f, int type) {
        return switch (type) {
            case STRIKE -> f * f * f;
            case SETTLE -> 1 - (1 - f) * (1 - f) * (1 - f);
            case LINEAR -> f;
            case OVERSHOOT -> {
                float c = 1.9f, g = f - 1;
                yield 1 + (c + 1) * g * g * g + c * g * g;
            }
            default -> f * f * (3 - 2 * f);
        };
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
            return key(part, time, x, y, z, SMOOTH);
        }

        public Builder key(Part part, float time, float x, float y, float z, int ease) {
            tracks.computeIfAbsent(part, p -> new ArrayList<>()).add(new Key(time, x, y, z, ease));
            return this;
        }

        /** Whips into this key (a blow landing). */
        public Builder strike(Part part, float time, float x, float y, float z) {
            return key(part, time, x, y, z, STRIKE);
        }

        /** Slows into this key (recoil, follow-through, settling back). */
        public Builder settle(Part part, float time, float x, float y, float z) {
            return key(part, time, x, y, z, SETTLE);
        }

        /** Constant speed into this key (a spin). */
        public Builder spin(Part part, float time, float x, float y, float z) {
            return key(part, time, x, y, z, LINEAR);
        }

        /** Flies a little past this key and snaps back (a stance snapping into place). */
        public Builder snap(Part part, float time, float x, float y, float z) {
            return key(part, time, x, y, z, OVERSHOOT);
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

    /**
     * The torso turns about the neck, so leaning it far forward or back pulls the hips away from the legs. For a pose
     * with no root motion of its own, this moves the torso's lean onto the whole body (which turns about the hips) and
     * keeps only its twist and sway on the torso.
     */
    public AnimDef leanFromHips() {
        List<Key> body = tracks.get(Part.BODY);
        if (body == null || tracks.containsKey(Part.ROOT)) return this;
        boolean leans = false;
        for (Key k : body) leans |= Math.abs(k.x()) > 8;
        if (!leans) return this;
        AnimDef d = new AnimDef(name, duration, hold, blendIn, blendOut);
        d.tracks.putAll(tracks);
        List<Key> twist = new ArrayList<>(), lean = new ArrayList<>();
        for (Key k : body) {
            twist.add(new Key(k.time(), 0, k.y(), k.z(), k.ease()));
            lean.add(new Key(k.time(), k.x(), 0, 0, k.ease()));
        }
        d.tracks.put(Part.BODY, twist);
        d.tracks.put(Part.ROOT, lean);
        return d;
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
            // A root offset mirrors sideways; every angle mirrors its turn and its roll.
            boolean offset = e.getKey() == Part.ROOT_POS;
            for (Key k : e.getValue()) {
                keys.add(offset ? new Key(k.time(), -k.x(), k.y(), k.z(), k.ease()) : new Key(k.time(), k.x(), -k.y(), -k.z(), k.ease()));
            }
            m.tracks.put(target, keys);
        }
        return m;
    }
}
