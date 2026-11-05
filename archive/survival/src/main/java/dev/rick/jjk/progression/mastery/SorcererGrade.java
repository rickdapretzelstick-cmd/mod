package dev.rick.jjk.progression.mastery;

/**
 * What level of sorcerer a player has proven themselves to be. Separate from Mastery on purpose: Mastery is how developed
 * your abilities are, a grade is what you have shown you can handle. Recorded on the player now (everyone starts
 * UNRANKED); promotion trials will read the exorcism and investigation record ({@link MasteryData}) to award them.
 */
public enum SorcererGrade {
    UNRANKED("Unranked"), GRADE_4("Grade 4"), GRADE_3("Grade 3"), GRADE_2("Grade 2"), SEMI_GRADE_1("Semi-Grade 1"),
    GRADE_1("Grade 1"), SPECIAL("Special Grade");

    public final String display;

    SorcererGrade(String display) {
        this.display = display;
    }

    public static SorcererGrade of(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return UNRANKED;
        }
    }
}
