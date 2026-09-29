package dev.rick.jjk.client.anim;

import dev.rick.jjk.client.anim.rig.Bone;

import java.util.EnumSet;
import java.util.Set;

/**
 * The bones a clip is allowed to drive. Clips on different layers play at the same time (a punch on the upper body
 * over a run on the lower body); a new clip replaces the clip already on its layer.
 */
public enum Layer {
    BASE(EnumSet.allOf(Bone.class)),
    LOWER_BODY(EnumSet.of(Bone.ROOT, Bone.HIPS, Bone.RIGHT_THIGH, Bone.RIGHT_SHIN, Bone.RIGHT_FOOT, Bone.LEFT_THIGH, Bone.LEFT_SHIN, Bone.LEFT_FOOT)),
    UPPER_BODY(EnumSet.of(Bone.CHEST, Bone.NECK, Bone.HEAD, Bone.RIGHT_ARM, Bone.RIGHT_FOREARM, Bone.RIGHT_HAND, Bone.LEFT_ARM, Bone.LEFT_FOREARM, Bone.LEFT_HAND)),
    HEAD(EnumSet.of(Bone.NECK, Bone.HEAD));

    public final Set<Bone> bones;
    private final boolean[] mask = new boolean[Bone.COUNT];

    Layer(Set<Bone> bones) {
        this.bones = bones;
        for (Bone b : bones) mask[b.ordinal()] = true;
    }

    public boolean has(Bone b) {
        return mask[b.ordinal()];
    }

    public boolean overlaps(Layer o) {
        for (Bone b : bones) if (o.has(b)) return true;
        return false;
    }
}
