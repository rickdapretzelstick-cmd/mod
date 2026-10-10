package dev.rick.jjk.progression.tool.kit;

/**
 * How a cursed tool is held and carried. The holster is where it rides while its moveset isn't in use (sheathed at the
 * hip, slung across the back); the grip is how it sits in the hands when drawn: one hand, or two with the off hand
 * brought onto the haft, the stock or the grip.
 */
public enum GripProfile {
    /** A short blade in one hand; sheathed at the left hip. */
    ONE_HAND(Holster.HIP, false),
    /** A knife held low; carried at the small of the back. */
    DAGGER(Holster.WAIST, false),
    /** A long haft in both hands, point forward; slung on the back. */
    SPEAR(Holster.BACK, true),
    /** A long blade in both hands; slung on the back. */
    TWO_HAND(Holster.BACK, true),
    /** Something heavy in both hands, high and ready to come down; slung on the back, head up. */
    HEAVY(Holster.BACK, true),
    /** A long gun shouldered with both hands; slung on the back, muzzle up. */
    RANGED(Holster.BACK, true);

    public enum Holster { HIP, WAIST, BACK }

    public final Holster holster;
    public final boolean twoHanded;

    GripProfile(Holster holster, boolean twoHanded) {
        this.holster = holster;
        this.twoHanded = twoHanded;
    }
}
