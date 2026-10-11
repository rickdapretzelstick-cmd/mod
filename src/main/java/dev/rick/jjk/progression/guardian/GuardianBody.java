package dev.rick.jjk.progression.guardian;

/**
 * Implemented by a Grade 1 guardian entity (see {@link Guardians}). The contract sets its visual variant when it rises
 * and marks it as the contract's empowered guardian (strength contests use the guardian preset for it).
 */
public interface GuardianBody {
    /** 0 .. variants-1: the cosmetic variant the contract chose (gameplay is the body's own). */
    void setGuardianVariant(int variant);

    int guardianVariant();
}
