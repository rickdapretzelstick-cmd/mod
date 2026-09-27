package dev.rick.jjk.core.combat;

/** Implemented on LivingEntity by mixin; gives every living entity a lazily created {@link CombatState}. */
public interface CombatHolder {
    CombatState jjk$combat();

    /** Returns the state only if one was already created (avoids allocating for idle entities). */
    CombatState jjk$combatOrNull();
}
