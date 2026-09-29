package dev.rick.jjk.core.ability;

/**
 * A cast that takes a key press even while its user can't otherwise act (e.g. Gojo's Awakening sequence, during which
 * the Special key turns it into the 0.2 Domain).
 */
public interface LockedInputCast {
    /** Return true if the press was used. */
    boolean pressWhileLocked(AbilitySlot slot);
}
