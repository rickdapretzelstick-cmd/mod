package dev.rick.jjk.progression.contest;

import dev.rick.jjk.progression.grade.CurseGrade;

/**
 * How hard a strength contest pushes, by who grabbed you. The meter starts at 50 and runs 0-100: the curse's pressure
 * drains it every second, each tap check answered GOOD or PERFECT pushes it up, a MISS drops it. 100 breaks free at
 * once, 0 is overpowered at once; at the end of the contest you break free only if the meter is at or above
 * {@code threshold}. A loss costs {@code loseDamage}.
 *
 * <pre>
 * preset     pressure/s  GOOD  PERFECT  MISS  timeout threshold  lose damage
 * Grade 4        2.0     +16    +22     -8          40               4
 * Grade 3        3.0     +15    +21     -9          45               6
 * Grade 2        4.0     +13    +19    -10          50               8
 * Grade 1        4.5     +12    +18    -11          55              10
 * Guardian       5.0     +12    +18    -12          60              12
 * Special        6.0     +11    +17    -13          65              14
 * </pre>
 */
public record ContestPreset(String name, double pressure, double good, double perfect, double miss, double threshold, float loseDamage) {
    public static final ContestPreset GRADE_4 = new ContestPreset("Grade 4", 2.0, 16, 22, 8, 40, 4);
    public static final ContestPreset GRADE_3 = new ContestPreset("Grade 3", 3.0, 15, 21, 9, 45, 6);
    public static final ContestPreset GRADE_2 = new ContestPreset("Grade 2", 4.0, 13, 19, 10, 50, 8);
    public static final ContestPreset GRADE_1 = new ContestPreset("Grade 1", 4.5, 12, 18, 11, 55, 10);
    public static final ContestPreset GUARDIAN = new ContestPreset("Guardian", 5.0, 12, 18, 12, 60, 12);
    public static final ContestPreset SPECIAL = new ContestPreset("Special Grade", 6.0, 11, 17, 13, 65, 14);
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

    /** The meter after a verdict ({@code TrainingCheck.PERFECT / GOOD / MISS}). */
    public double judged(double meter, int result) {
        double d = switch (result) {
            case dev.rick.jjk.progression.training.TrainingCheck.PERFECT -> perfect;
            case dev.rick.jjk.progression.training.TrainingCheck.GOOD -> good;
            case dev.rick.jjk.progression.training.TrainingCheck.MISS -> -miss;
            default -> 0;
        };
        return Math.max(0, Math.min(MAX, meter + d));
    }

    /** The meter after {@code ms} of pressure. */
    public double pressed(double meter, double ms) {
        return Math.max(0, Math.min(MAX, meter - pressure * ms / 1000.0));
    }
}
