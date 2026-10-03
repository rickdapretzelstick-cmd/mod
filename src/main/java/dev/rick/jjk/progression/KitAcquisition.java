package dev.rick.jjk.progression;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * One way of earning a kit in Survival (eating a Cursed Finger, a ritual, a duel...). Every path claims through
 * {@link TechniqueProgression#acquire}, so the one-owner-per-world rule is the same for all of them; what differs is
 * what happens to the player when the kit is free, already theirs, or already someone else's. Not every path has to
 * be lethal: that is the Cursed Finger's own consequence.
 */
public interface KitAcquisition {
    /** Names the path in the ownership record and logs, e.g. "cursed_finger". */
    String id();

    /** The kit (character id) this path grants. */
    String kit();

    /** The player just became the kit's one owner in this world. */
    void onClaimed(ServerPlayer player);

    /** The kit's owner went through the path again. Kept apart so later progression can hang off it. */
    void onAlreadyOwned(ServerPlayer player);

    /** Someone else owns the kit. {@code owner} is who, for the path's own logic; players aren't told. */
    void onTaken(ServerPlayer player, @Nullable KitOwnership.Owner owner);

    /** The claim couldn't be recorded (world data unwritable): nothing was granted. */
    default void onFailed(ServerPlayer player) {}

    /** Done in Creative: nothing is claimed. */
    default void onSandbox(ServerPlayer player) {}
}
