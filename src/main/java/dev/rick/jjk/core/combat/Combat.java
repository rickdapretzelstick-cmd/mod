package dev.rick.jjk.core.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Convenience accessors for combat state. */
public final class Combat {
    private Combat() {}

    public static CombatState state(LivingEntity entity) {
        return ((CombatHolder) entity).jjk$combat();
    }

    public static CombatState stateOrNull(Entity entity) {
        return entity instanceof CombatHolder h ? h.jjk$combatOrNull() : null;
    }

    public static boolean has(Entity entity, CombatStatus status) {
        CombatState s = stateOrNull(entity);
        return s != null && s.has(status);
    }

    public static boolean movementLocked(Entity entity) {
        CombatState s = stateOrNull(entity);
        return s != null && s.movementLocked();
    }

    public static boolean actionsLocked(Entity entity) {
        CombatState s = stateOrNull(entity);
        return s != null && s.actionsLocked();
    }

    public static boolean techniquesLocked(Entity entity) {
        CombatState s = stateOrNull(entity);
        return s != null && s.techniquesLocked();
    }

    public static boolean isAirborne(Entity entity) {
        return !entity.onGround() && !entity.isInWater() && !entity.isPassenger();
    }

    public static boolean isDowned(Entity entity) {
        CombatState s = stateOrNull(entity);
        return s != null && s.isDowned();
    }

    public static boolean isGuarding(Entity entity) {
        CombatState s = stateOrNull(entity);
        return s != null && s.isGuarding();
    }
}
