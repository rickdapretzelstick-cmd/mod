package dev.rick.jjk.client.anim;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** How a segment moves from one key into the next. The ease belongs to the key being moved into. */
public enum Easing {
    /** Constant speed (spins, slides). */
    LINEAR,
    /** Starts slow and whips into the key (a blow landing). */
    EASE_IN,
    /** Leaves fast and slows into the key (recoil, follow-through, settling). */
    EASE_OUT,
    /** Slow at both ends. The default. */
    EASE_IN_OUT,
    /** Holds the previous key, then jumps to this one on its frame (a hard cut, a smear frame). */
    STEP,
    /** Flies a little past the key and snaps back (a stance snapping into place). */
    OVERSHOOT,
    /** Overshoots and rings a few times before settling (a heavy impact wobble). */
    SPRING;

    public float apply(float f) {
        if (f <= 0) return 0;
        if (f >= 1) return 1;
        return switch (this) {
            case LINEAR -> f;
            case EASE_IN -> f * f * f;
            case EASE_OUT -> 1 - (1 - f) * (1 - f) * (1 - f);
            case EASE_IN_OUT -> f < 0.5f ? 4 * f * f * f : 1 - (float) Math.pow(-2 * f + 2, 3) / 2;
            case STEP -> 0;
            case OVERSHOOT -> {
                float c = 1.9f, g = f - 1;
                yield 1 + (c + 1) * g * g * g + c * g * g;
            }
            case SPRING -> 1 - (float) (Math.exp(-6 * f) * Math.cos(f * Math.PI * 4.5));
        };
    }

    @Nullable
    public static Easing byName(@Nullable String s) {
        if (s == null) return null;
        return switch (s.toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "LINEAR" -> LINEAR;
            case "EASE_IN", "IN", "STRIKE", "ACCEL" -> EASE_IN;
            case "EASE_OUT", "OUT", "SETTLE", "DECEL" -> EASE_OUT;
            case "EASE_IN_OUT", "IN_OUT", "SMOOTH" -> EASE_IN_OUT;
            case "STEP", "HOLD", "CONSTANT" -> STEP;
            case "OVERSHOOT", "BACK", "SNAP" -> OVERSHOOT;
            case "SPRING", "ELASTIC" -> SPRING;
            default -> null;
        };
    }
}
