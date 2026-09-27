package dev.rick.jjk.core.ability;

/** Input slots a character maps abilities onto. The client binds one key per slot. */
public enum AbilitySlot {
    DASH, GUARD, SKILL_1, SKILL_2, SKILL_3, SKILL_4, SKILL_5, ULTIMATE, HEAVY;

    public static AbilitySlot byIndex(int i) {
        AbilitySlot[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
