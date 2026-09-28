package dev.rick.jjk.core.domain.clash;

/** How well one input landed. GHOST is a press with no note to hit (breaks the streak, costs a little ground). */
public enum ClashJudgement {
    PERFECT, GREAT, GOOD, MISS, GHOST;

    public boolean hit() {
        return this == PERFECT || this == GREAT || this == GOOD;
    }

    public static ClashJudgement byId(int id) {
        ClashJudgement[] v = values();
        return id >= 0 && id < v.length ? v[id] : MISS;
    }
}
