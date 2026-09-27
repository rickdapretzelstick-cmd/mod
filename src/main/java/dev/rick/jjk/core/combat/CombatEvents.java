package dev.rick.jjk.core.combat;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Hooks other systems use to react to combat (e.g. interrupting casts when stunned). */
public final class CombatEvents {
    public interface StatusApplied { void onApplied(LivingEntity entity, CombatStatus status, int ticks); }
    public interface HitResolved { void onResolved(HitResult result); }

    public static final List<StatusApplied> STATUS_APPLIED = new CopyOnWriteArrayList<>();
    public static final List<HitResolved> HIT_RESOLVED = new CopyOnWriteArrayList<>();

    private CombatEvents() {}
}
