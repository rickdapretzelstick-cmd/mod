package dev.rick.jjk.progression.contest;

import dev.rick.jjk.progression.grade.CurseGrade;

/**
 * How hard a strength contest pushes, by who grabbed you. The meter starts at 50 and runs 0-100. Every key shown
 * (W, A, S or D: one at a time) pressed right pushes it up by {@code push}; the wrong key slips it back by {@code slip}
 * (and locks the keys for a moment, so mashing loses); the curse's {@code pressure} drains it every second. 100 breaks
 * free, 0 is overpowered: there is no time limit, but after a while its grip tightens (its pressure grows steadily), so
 * a stalemate always ends. A loss costs {@code loseDamage}.
 *
 * <pre>
 * preset     pressure/s  push  slip  lose damage   (right keys a second to break free before it tightens)
 * Grade 4        6        +7    -6       4          ~1.8
 * Grade 3        7.5      +6.5  -7       6          ~2.1
 * Grade 2        9        +6    -8       8          ~2.5
 * Grade 1       10        +5.5  -9      10          ~3.0
 * Guardian      11        +5    -10     12          ~3.5
 * Special       12        +4.5  -11     14          ~4.1
 * </pre>
 */
public record ContestPreset(String name, double pressure, double push, double slip, float loseDamage) {
    public static final ContestPreset GRADE_4 = new ContestPreset("Grade 4", 6, 7, 6, 4);
    public static final ContestPreset GRADE_3 = new ContestPreset("Grade 3", 7.5, 6.5, 7, 6);
    public static final ContestPreset GRADE_2 = new ContestPreset("Grade 2", 9, 6, 8, 8);
    public static final ContestPreset GRADE_1 = new ContestPreset("Grade 1", 10, 5.5, 9, 10);
    public static final ContestPreset GUARDIAN = new ContestPreset("Guardian", 11, 5, 10, 12);
    public static final ContestPreset SPECIAL = new ContestPreset("Special Grade", 12, 4.5, 11, 14);
    public static final double START = 50, MAX = 100;

    public static ContestPreset of(CurseGrade grade, boolean guardian) {
        if (guardian) return GUARDIAN;
        return switch (grade) {
            case GRADE_4 -> GRADE_4;
            case GRADE_3 -> GRADE_3;
            case GRADE_2 -> GRADE_2;
            case GRADE_1 -> GRADE_1;
            default -> SPECIAL;
        };
    }

    /** The meter after the right key. */
    public double pushed(double meter) {
        return Math.min(MAX, meter + push);
    }

    /** The meter after the wrong key. */
    public double slipped(double meter) {
        return Math.max(0, meter - slip);
    }

    /**
     * Its pressure {@code atMs} into the contest: steady, then, past {@code tightenAfterMs}, growing by
     * {@code tightenPerSecond} of itself every second (its grip tightening).
     */
    public double pressureAt(double atMs, int tightenAfterMs, double tightenPerSecond) {
        return pressure * (1 + Math.max(0, atMs - tightenAfterMs) / 1000.0 * tightenPerSecond);
    }

    /** The meter after {@code ms} of pressure, ending {@code atMs} into the contest. */
    public double pressed(double meter, double ms, double atMs, int tightenAfterMs, double tightenPerSecond) {
        return Math.max(0, meter - pressureAt(atMs, tightenAfterMs, tightenPerSecond) * ms / 1000.0);
    }
}
