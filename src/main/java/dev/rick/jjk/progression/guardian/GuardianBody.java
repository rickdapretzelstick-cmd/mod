package dev.rick.jjk.progression.guardian;

/**
 * Implemented by a Grade 1 guardian entity (see {@link Guardians}). The contract sets its visual variant when it rises
 * and marks it as the contract's empowered guardian (strength contests use the guardian preset for it).
 */
public interface GuardianBody {
    /** 0 .. variants-1: the cosmetic variant the contract chose (gameplay is the body's own). */
    void setGuardianVariant(int variant);

    int guardianVariant();

    /**
     * Its own part in a phase beat (progression.finish.GuardianPhases), as it falls past two thirds (1) and a third
     * (2) of its health: a roar, a transformation clip, its look changing. The beat itself (the hold, the camera, the
     * title card, the arena changing) is shared.
     */
    default void phaseBeat(net.minecraft.server.level.ServerLevel level, int phase) {
    }

    /** The title card's words for its act (null for the shared ones). */
    @org.jetbrains.annotations.Nullable
    default String phaseTitle(int phase) {
        return null;
    }
}
