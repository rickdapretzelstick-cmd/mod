package dev.rick.jjk.core.combat;

import net.minecraft.world.entity.LivingEntity;

/** Server-side status application that notifies listeners (cast interruption etc.). */
public final class Statuses {
    private Statuses() {}

    public static void apply(LivingEntity entity, CombatStatus status, int ticks) {
        if (ticks <= 0 || entity.level().isClientSide()) return;
        Combat.state(entity).apply(status, ticks);
        for (CombatEvents.StatusApplied l : CombatEvents.STATUS_APPLIED) l.onApplied(entity, status, ticks);
    }

    public static void remove(LivingEntity entity, CombatStatus status) {
        CombatState s = Combat.stateOrNull(entity);
        if (s != null) s.remove(status);
    }
}
