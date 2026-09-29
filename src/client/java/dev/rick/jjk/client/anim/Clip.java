package dev.rick.jjk.client.anim;

import dev.rick.jjk.client.anim.rig.Bone;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A compiled animation: per-bone tracks plus its playback rules. Times are milliseconds. Built from JSON by
 * {@link AnimLibrary}; see docs/ANIMATION.md for the format.
 */
public final class Clip {
    public static final int ROT = 0, POS = 1, SCALE = 2;

    /** A named moment (anticipation, impact, recovery...) shown by the debugger. */
    public record Marker(float time, String label) {}

    public final String name;
    public final String group;
    public final float duration;
    /** Frames per second of the reference the clip was matched against (frame numbers in the debugger use it). */
    public final float fps;
    public final boolean hold;
    public final boolean loop;
    public final float loopStart, loopEnd;
    public final float blendIn, blendOut;
    /** The shape of the blend in: a clip whose first key comes later eases out of the previous pose along it. */
    public final Easing blendEase;
    public final Priority priority;
    public final Layer layer;
    public final boolean interruptible;
    /** While interruptible, only between these times (ms); a negative end means until the clip is over. */
    public final float interruptFrom, interruptTo;
    public final float speed;
    /** A looping or held clip still lets go after this long (ms of clip time); 0 = only when stopped. */
    public float stopAfter;
    /** 0..1: how much the head keeps looking where the player looks when the clip doesn't pose it. */
    public final float look;
    /** Degrees of shake added to every animated bone (overload, struggling). */
    public final float tremble;
    /** Key times, for stepping key to key in the debugger. */
    public final float[] keyTimes;
    public final List<Marker> markers;
    /** [bone][channel], null where the clip leaves the bone alone. */
    final Track[][] tracks;

    Clip(String name, String group, float duration, float fps, boolean hold, boolean loop, float loopStart, float loopEnd,
         float blendIn, float blendOut, Easing blendEase, Priority priority, Layer layer, boolean interruptible, float interruptFrom, float interruptTo,
         float speed, float look, float tremble, float[] keyTimes, List<Marker> markers, Track[][] tracks) {
        this.name = name;
        this.group = group;
        this.duration = duration;
        this.fps = fps;
        this.hold = hold;
        this.loop = loop;
        this.loopStart = loopStart;
        this.loopEnd = loopEnd;
        this.blendIn = blendIn;
        this.blendOut = blendOut;
        this.blendEase = blendEase;
        this.priority = priority;
        this.layer = layer;
        this.interruptible = interruptible;
        this.interruptFrom = interruptFrom;
        this.interruptTo = interruptTo;
        this.speed = speed;
        this.look = look;
        this.tremble = tremble;
        this.keyTimes = keyTimes;
        this.markers = markers;
        this.tracks = tracks;
    }

    @Nullable
    public Track track(Bone b, int channel) {
        return tracks[b.ordinal()][channel];
    }

    public boolean animates(Bone b) {
        if (!layer.has(b)) return false;
        Track[] t = tracks[b.ordinal()];
        return t[0] != null || t[1] != null || t[2] != null;
    }

    /** Whether the clip keeps playing until something stops it. */
    public boolean endless() {
        return hold || loop;
    }

    /** Maps playback time to the time sampled (looping and clamping). */
    public float localTime(float t) {
        if (loop && loopEnd > loopStart && t > loopEnd) return loopStart + (t - loopStart) % (loopEnd - loopStart);
        return Math.max(0, Math.min(t, duration));
    }

    public boolean canBeInterruptedAt(float t) {
        if (!interruptible) return false;
        if (t < interruptFrom) return false;
        return interruptTo < 0 || t <= interruptTo;
    }

    /** The marker most recently passed at {@code t}, or null. */
    @Nullable
    public Marker markerAt(float t) {
        Marker m = null;
        for (Marker k : markers) if (k.time() <= t + 1e-3f) m = k;
        return m;
    }

    public int keyIndexAt(float t) {
        int k = 0;
        for (int i = 0; i < keyTimes.length; i++) if (keyTimes[i] <= t + 1e-3f) k = i;
        return k;
    }

    /** A left/right mirrored copy. */
    public Clip mirrored(String newName) {
        Track[][] m = new Track[Bone.COUNT][3];
        for (Bone b : Bone.ALL) {
            Track[] src = tracks[b.ordinal()];
            Track[] dst = m[b.mirror().ordinal()];
            if (src[ROT] != null) dst[ROT] = src[ROT].mirrored(false);
            if (src[POS] != null) dst[POS] = src[POS].mirrored(true);
            dst[SCALE] = src[SCALE];
        }
        Clip c = new Clip(newName, group, duration, fps, hold, loop, loopStart, loopEnd, blendIn, blendOut, blendEase, priority, layer,
                interruptible, interruptFrom, interruptTo, speed, look, tremble, keyTimes, markers, m);
        c.stopAfter = stopAfter;
        return c;
    }
}
