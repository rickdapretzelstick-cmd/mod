package dev.rick.jjk.client.camera;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A cut list: shots one after another on a clock, each joined to the last by a hard {@link Join#CUT} or an eased
 * {@link Join#MOVE}. A shot is a function of its own progress (0..1), so it can push in, drift or orbit while it holds.
 * With Reduced Camera Motion every join is a cut and every shot holds its middle frame.
 */
public final class ShotSequence {
    public enum Join { CUT, MOVE }

    private record Beat(DoubleFunction<Frame> shot, double ms, Join join, double joinMs) {}

    private final List<Beat> beats = new ArrayList<>();

    /** Holds {@code shot} for {@code ms}, reached by {@code join} over {@code joinMs} (a cut ignores it). */
    public ShotSequence then(DoubleFunction<Frame> shot, double ms, Join join, double joinMs) {
        beats.add(new Beat(shot, Math.max(1, ms), join, joinMs));
        return this;
    }

    public ShotSequence then(DoubleFunction<Frame> shot, double ms) {
        return then(shot, ms, Join.CUT, 0);
    }

    public double length() {
        double t = 0;
        for (Beat b : beats) t += b.ms;
        return t;
    }

    /** Which beat plays at {@code t} ms (the last one holds past the end), or -1 if there are none. */
    public int beatAt(double t) {
        double at = 0;
        for (int i = 0; i < beats.size(); i++) {
            at += beats.get(i).ms;
            if (t < at) return i;
        }
        return beats.size() - 1;
    }

    /** The frame at {@code t} ms into the sequence, or null if it has no shots. */
    @Nullable
    public Frame at(double t) {
        if (beats.isEmpty()) return null;
        boolean still = CameraDirector.reducedMotion();
        double start = 0;
        for (int i = 0; i < beats.size(); i++) {
            Beat b = beats.get(i);
            if (t < start + b.ms || i == beats.size() - 1) {
                double local = Math.max(0, t - start);
                double k = still ? 0.5 : Math.min(1, local / b.ms);
                Frame here = b.shot.apply(k);
                if (!still && i > 0 && b.join == Join.MOVE && local < b.joinMs) {
                    Beat prev = beats.get(i - 1);
                    Frame from = prev.shot.apply(1);
                    return from.lerp(here, Shots.ease(local / Math.max(1, b.joinMs)));
                }
                return here;
            }
            start += b.ms;
        }
        return null;
    }
}
