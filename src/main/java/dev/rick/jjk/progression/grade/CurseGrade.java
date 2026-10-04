package dev.rick.jjk.progression.grade;

/**
 * How dangerous a cursed spirit is. A grade is not a health number: it is the whole profile a curse is built to, and
 * everything that scales with danger reads it from here. A curse's own code decides how its grade shows (a Grade 4 swarm
 * bites and scatters, a Grade 3 bloat spits and slams, a Grade 1 boss has phases); the grade gives the scale.
 *
 * <ul>
 *   <li>{@code health}, {@code damage}: multipliers on the curse's own base numbers;</li>
 *   <li>{@code aggression}: how soon it attacks again (rest between moves is divided by it);</li>
 *   <li>{@code resistance}: damage it shrugs off from cursed energy that isn't a technique (0-1);</li>
 *   <li>{@code mastery}: the Mastery an exorcism is worth before contribution and anti-grind rules;</li>
 *   <li>{@code incidentBonus}: added once when the curse ends an investigation;</li>
 *   <li>{@code severity}: how bad an incident caused by it sounds (0 mild ... 4 catastrophic), for the report writer.</li>
 * </ul>
 */
public enum CurseGrade {
    GRADE_4("Grade 4", 1.0f, 1.0f, 1.0f, 0.0f, 6, 10, 0),
    GRADE_3("Grade 3", 2.2f, 1.5f, 1.25f, 0.05f, 14, 26, 1),
    GRADE_2("Grade 2", 4.5f, 2.2f, 1.5f, 0.1f, 32, 60, 2),
    GRADE_1("Grade 1", 8.0f, 3.0f, 1.8f, 0.15f, 70, 140, 3),
    SPECIAL("Special Grade", 14.0f, 4.2f, 2.2f, 0.25f, 160, 320, 4);

    public final String display;
    public final float health, damage, aggression, resistance;
    public final int mastery, incidentBonus, severity;

    CurseGrade(String display, float health, float damage, float aggression, float resistance, int mastery, int incidentBonus, int severity) {
        this.display = display;
        this.health = health;
        this.damage = damage;
        this.aggression = aggression;
        this.resistance = resistance;
        this.mastery = mastery;
        this.incidentBonus = incidentBonus;
        this.severity = severity;
    }

    /** Rank for comparisons: Grade 4 is 0, Special Grade 4. */
    public int rank() {
        return ordinal();
    }

    public static CurseGrade byRank(int rank) {
        CurseGrade[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, rank))];
    }
}
