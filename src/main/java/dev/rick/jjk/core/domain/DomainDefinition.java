package dev.rick.jjk.core.domain;

import net.minecraft.world.entity.LivingEntity;

/**
 * What a specific domain does. The framework ({@link DomainManager}, {@link DomainInstance}) handles
 * formation, boundaries, clashes, cancellation and expiry; definitions only describe behaviour.
 */
public interface DomainDefinition {
    String id();

    /** Clash strength before the owner's cursed energy is factored in. */
    float refinement();

    double radius(LivingEntity owner);

    int duration(LivingEntity owner);

    /** Ticks the barrier takes to close (sure-hit starts afterwards). */
    default int formingTicks() {
        return 20;
    }

    default int collapseTicks() {
        return 20;
    }

    /** The physical structure this domain builds, or null for a purely gameplay domain. */
    default dev.rick.jjk.core.domain.structure.@org.jetbrains.annotations.Nullable StructureSpec structure(LivingEntity owner) {
        return null;
    }

    /** Whether people inside can leave. Closed barriers trap everyone but the owner. */
    boolean closedBarrier();

    /** Called every tick for each non-owner entity inside while the domain is active. */
    void applySureHit(DomainInstance domain, LivingEntity target, int ticksInside);

    /** Called for each victim when they leave or the domain ends. */
    default void onRelease(DomainInstance domain, LivingEntity target) {}

    default void onActivated(DomainInstance domain) {}

    default void onTick(DomainInstance domain) {}

    /** Called once when the domain ends, for any reason. */
    default void onCollapse(DomainInstance domain, DomainInstance.EndReason reason) {}
}
