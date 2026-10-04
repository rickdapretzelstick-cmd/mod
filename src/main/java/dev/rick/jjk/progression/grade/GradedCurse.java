package dev.rick.jjk.progression.grade;

import dev.rick.jjk.progression.CursedSpirit;

/**
 * A cursed spirit with a grade: what an exorcism of it is worth, how hard it hits, how bad the incident it causes sounds.
 * {@code curseKind} names the species (for the anti-grind rule and incidents), e.g. "fly_head".
 */
public interface GradedCurse extends CursedSpirit {
    CurseGrade curseGrade();

    String curseKind();

    /** The investigation this curse belongs to, if it was raised by one (it then completes the incident when exorcised). */
    default String incidentId() {
        return "";
    }
}
