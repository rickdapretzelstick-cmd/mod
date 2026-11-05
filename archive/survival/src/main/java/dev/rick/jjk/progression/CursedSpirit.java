package dev.rick.jjk.progression;

/**
 * Implemented by curse entities (the Finger Bearer and those after it). A curse that requires perception is invisible to
 * players who can't perceive curses and can't start a fight with them by proximity ({@link CurseAggro}); once it is
 * hostile toward someone, losing perception doesn't calm it. Entity types from elsewhere can opt in with the
 * {@code jjk:requires_curse_perception} entity type tag instead.
 */
public interface CursedSpirit {
    default boolean requiresCursePerception() {
        return true;
    }
}
