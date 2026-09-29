package dev.rick.jjk.client.anim;

import dev.rick.jjk.client.anim.rig.Bone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The clips playing on one entity: at most one per layer (plus a hit reaction over everything), each with its own
 * clock, speed and blend weight.
 */
public final class AnimPlayer {
    /** One playing clip. Times are milliseconds of clip time. */
    public static final class Instance {
        public final Clip clip;
        public float time;
        public final float speed;
        final long seq;
        public boolean stopping;
        float fadeFrom, fadeMs, fadeTime;
        /** The clip that took over this one's layer: this stays under it, unfaded, until it shows fully. */
        @Nullable Instance replacedBy;

        Instance(Clip clip, float speed, long seq) {
            this.clip = clip;
            this.speed = speed;
            this.seq = seq;
        }

        /** 0..1: how much of the pose shows (blending in, then fading out once stopped). */
        public float weight() {
            if (stopping && replacedBy != null) return fadeFrom;
            if (stopping) return fadeMs <= 0 ? 0 : Math.max(0, fadeFrom * (1 - fadeTime / fadeMs));
            return clip.blendIn <= 0 ? 1 : clip.blendEase.apply(Math.min(1, time / clip.blendIn));
        }

        void stop(float fade) {
            if (stopping) return;
            fadeFrom = weight();
            fadeMs = fade;
            fadeTime = 0;
            stopping = true;
        }

        /** The time actually sampled. */
        public float sampleTime() {
            return clip.localTime(time);
        }
    }

    private static final Comparator<Instance> ORDER = Comparator.<Instance>comparingInt(i -> i.clip.priority.ordinal())
            .thenComparingInt(i -> i.clip.layer.ordinal()).thenComparingLong(i -> i.seq);

    final List<Instance> instances = new ArrayList<>();
    @Nullable Instance reaction;
    @Nullable String reactionName;
    float lastNow = Float.NaN;
    private long seq;

    /** Starts a clip, unless a higher-priority clip that can't be interrupted right now holds its bones. */
    public boolean play(Clip clip, float speed) {
        for (Instance i : instances) {
            if (i.stopping || !i.clip.layer.overlaps(clip.layer)) continue;
            if (i.clip.priority.ordinal() > clip.priority.ordinal() && !i.clip.canBeInterruptedAt(i.time)) return false;
        }
        Instance next = new Instance(clip, speed * clip.speed, seq++);
        for (Instance i : instances) {
            // The new clip takes over its layer; a full-body clip also takes over the other layers it outranks.
            boolean replaces = i.clip.layer == clip.layer
                    || clip.layer == Layer.BASE && i.clip.priority.ordinal() <= clip.priority.ordinal();
            if (!replaces) continue;
            if (clip.blendIn <= 0) {
                // A hard transition cuts off everything there, including whatever was still fading out.
                i.stop(0);
                i.replacedBy = null;
                i.fadeMs = 0;
            } else if (!i.stopping) {
                i.stop(clip.blendIn);
                i.replacedBy = next;
            }
        }
        instances.removeIf(i -> i.stopping && i.weight() <= 0);
        instances.add(next);
        return true;
    }

    /** Lets held and looping clips go (blending out). */
    public void release() {
        for (Instance i : instances) if (i.clip.endless()) i.stop(i.clip.blendOut);
    }

    /** Stops everything at once. */
    public void stopAll() {
        instances.clear();
        reaction = null;
        reactionName = null;
    }

    public void setReaction(@Nullable String name, boolean restart) {
        if (name != null && (restart || !name.equals(reactionName))) {
            Clip c = AnimLibrary.get(name);
            if (c != null) {
                if (reaction != null) reaction.stop(c.blendIn);
                reaction = new Instance(c, 1, seq++);
            }
        } else if (name == null && reaction != null) {
            reaction.stop(reaction.clip.blendOut);
        }
        reactionName = name;
    }

    /** Moves every clock on by {@code dt} ms (already scaled by the debugger). */
    public void advance(float dt) {
        for (Instance i : instances) tick(i, dt);
        if (reaction != null) {
            tick(reaction, dt);
            if (reaction.stopping && reaction.weight() <= 0) reaction = null;
        }
        instances.removeIf(i -> i.stopping && (i.replacedBy != null
                ? i.replacedBy.weight() >= 1 && !i.replacedBy.stopping || !instances.contains(i.replacedBy) || i.replacedBy.stopping && i.replacedBy.replacedBy == null
                : i.weight() <= 0));
    }

    private static void tick(Instance i, float dt) {
        if (i.stopping) i.fadeTime += dt;
        i.time += dt * i.speed;
        if (!i.stopping && !i.clip.endless() && i.time >= i.clip.duration) i.stop(i.clip.blendOut);
        if (!i.stopping && i.clip.stopAfter > 0 && i.time >= i.clip.stopAfter) i.stop(i.clip.blendOut);
    }

    /** Moves the clips' clocks by {@code dt} without fading anything (the debugger scrubbing a paused clip). */
    void scrub(float dt) {
        for (Instance i : instances) if (!i.stopping) i.time = Math.max(0, i.time + dt);
    }

    public boolean idle() {
        return instances.isEmpty() && reaction == null;
    }

    /** Every clip, bottom layer first, the reaction last. */
    public List<Instance> ordered() {
        List<Instance> out = new ArrayList<>(instances);
        out.sort(ORDER);
        if (reaction != null) out.add(reaction);
        return out;
    }

    /** The clip that shows the most (the debugger's subject). */
    @Nullable
    public Instance top() {
        Instance best = null;
        for (Instance i : ordered()) if (!i.stopping || best == null) best = i;
        return best;
    }

    private final float[] tmp = new float[3];

    public PoseFrame sample(PoseFrame f) {
        for (Instance inst : ordered()) {
            float w = inst.weight();
            if (w <= 0) continue;
            Clip c = inst.clip;
            float t = inst.sampleTime();
            for (Bone b : Bone.ALL) {
                if (!c.layer.has(b)) continue;
                for (int ch = 0; ch < 3; ch++) {
                    Track tr = c.tracks[b.ordinal()][ch];
                    if (tr == null) continue;
                    tr.sample(t, tmp);
                    if (ch == Clip.ROT && c.tremble > 0) {
                        float amp = c.tremble * (float) (Math.PI / 180);
                        float s = inst.time * 0.047f + b.ordinal() * 1.7f;
                        tmp[0] += (float) Math.sin(s * 1.3f) * amp;
                        tmp[1] += (float) Math.sin(s * 1.7f + 2) * amp * 0.6f;
                        tmp[2] += (float) Math.sin(s * 2.1f + 4) * amp * 0.6f;
                    }
                    f.add(ch, b, tmp, w);
                }
            }
            f.look += (c.look - f.look) * w;
        }
        return f;
    }
}
