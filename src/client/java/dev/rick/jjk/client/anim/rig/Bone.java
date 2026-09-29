package dev.rick.jjk.client.anim.rig;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The player skeleton. Every bone is authored in its parent's space, in degrees and pixels (16 to a block):
 *
 * <ul>
 *   <li>rotation x+ pitches the bone forward (a torso bows, a head nods down, an arm at x -90 points straight ahead),
 *   y+ turns it to the character's right, z+ swings a right limb out to the side (a left limb in) and tips the torso
 *   and head toward the character's left;</li>
 *   <li>position is (right, up, forward), an offset from the bone's rest place.</li>
 * </ul>
 *
 * <p>{@link #ROOT} sits between the feet and carries the whole body without moving the player's hitbox; {@link #HIPS}
 * turns the pelvis with both legs and the torso; {@link #CHEST} bends at the waist above it.
 */
public enum Bone {
    ROOT("root", null),
    HIPS("hips", ROOT),
    CHEST("chest", HIPS),
    NECK("neck", CHEST),
    HEAD("head", NECK),
    RIGHT_ARM("rightArm", CHEST),
    RIGHT_FOREARM("rightForearm", RIGHT_ARM),
    RIGHT_HAND("rightHand", RIGHT_FOREARM),
    LEFT_ARM("leftArm", CHEST),
    LEFT_FOREARM("leftForearm", LEFT_ARM),
    LEFT_HAND("leftHand", LEFT_FOREARM),
    RIGHT_THIGH("rightThigh", HIPS),
    RIGHT_SHIN("rightShin", RIGHT_THIGH),
    RIGHT_FOOT("rightFoot", RIGHT_SHIN),
    LEFT_THIGH("leftThigh", HIPS),
    LEFT_SHIN("leftShin", LEFT_THIGH),
    LEFT_FOOT("leftFoot", LEFT_SHIN);

    public static final Bone[] ALL = values();
    public static final int COUNT = ALL.length;
    private static final Map<String, Bone> BY_NAME = new HashMap<>();

    public final String id;
    @Nullable public final Bone parent;

    Bone(String id, @Nullable Bone parent) {
        this.id = id;
        this.parent = parent;
    }

    static {
        for (Bone b : ALL) {
            BY_NAME.put(b.id.toLowerCase(Locale.ROOT), b);
            BY_NAME.put(b.name().toLowerCase(Locale.ROOT), b);
        }
        // Alternative and semantic names, so authors (and converted data) can use the words they think in.
        alias(HIPS, "waist", "pelvis", "lowerTorso", "lower_torso");
        alias(CHEST, "torso", "body", "upperTorso", "upper_torso", "spine");
        alias(RIGHT_ARM, "rightUpperArm", "right_upper_arm", "mainArm");
        alias(RIGHT_FOREARM, "rightLowerArm", "right_lower_arm", "rightElbow", "mainForearm");
        alias(RIGHT_HAND, "rightFist", "rightWrist", "mainHand");
        alias(LEFT_ARM, "leftUpperArm", "left_upper_arm", "offArm");
        alias(LEFT_FOREARM, "leftLowerArm", "left_lower_arm", "leftElbow", "offForearm");
        alias(LEFT_HAND, "leftFist", "leftWrist", "offHand");
        alias(RIGHT_THIGH, "rightLeg", "rightUpperLeg", "right_upper_leg");
        alias(RIGHT_SHIN, "rightLowerLeg", "right_lower_leg", "rightKnee", "rightCalf");
        alias(RIGHT_FOOT, "rightAnkle");
        alias(LEFT_THIGH, "leftLeg", "leftUpperLeg", "left_upper_leg");
        alias(LEFT_SHIN, "leftLowerLeg", "left_lower_leg", "leftKnee", "leftCalf");
        alias(LEFT_FOOT, "leftAnkle");
    }

    private static void alias(Bone b, String... names) {
        for (String n : names) BY_NAME.put(n.toLowerCase(Locale.ROOT), b);
    }

    @Nullable
    public static Bone byName(String name) {
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    /** The same bone on the other side of the body (itself for the centre line). */
    public Bone mirror() {
        return switch (this) {
            case RIGHT_ARM -> LEFT_ARM;
            case RIGHT_FOREARM -> LEFT_FOREARM;
            case RIGHT_HAND -> LEFT_HAND;
            case LEFT_ARM -> RIGHT_ARM;
            case LEFT_FOREARM -> RIGHT_FOREARM;
            case LEFT_HAND -> RIGHT_HAND;
            case RIGHT_THIGH -> LEFT_THIGH;
            case RIGHT_SHIN -> LEFT_SHIN;
            case RIGHT_FOOT -> LEFT_FOOT;
            case LEFT_THIGH -> RIGHT_THIGH;
            case LEFT_SHIN -> RIGHT_SHIN;
            case LEFT_FOOT -> RIGHT_FOOT;
            default -> this;
        };
    }

    public boolean isLeft() {
        return id.startsWith("left");
    }

    public boolean isRight() {
        return id.startsWith("right");
    }
}
