package dev.rick.jjk.core.domain;

import net.minecraft.world.entity.LivingEntity;

/**
 * What a specific domain does. The framework ({@link DomainManager}, {@link DomainInstance}) handles
 * formation, boundaries, clashes, cancellation and expiry; definitions only describe behaviour.
 */
public interface DomainDefinition {
    String id();

    /**
     * The domain's colour in a clash (the meter, prompts and energy pulses). Clashes are decided by the players'
     * timing alone; nothing about the domain itself affects the outcome.
     */
    default int clashColor() {
        return 0xFF9FD8FF;
    }

    double radius(LivingEntity owner);

    int duration(LivingEntity owner);

    /** Name shown when the domain opens ("DOMAIN EXPANSION — ..."). */
    default String displayName() {
        return id();
    }

    /**
     * Ticks the domain takes to form: its structure builds on this schedule (feet → ground → walls → ceiling →
     * underground) and the barrier and sure-hit only switch on once it is sealed.
     */
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

    /** Whether the owner suffers technique burnout when this domain ends (Hakari's Jackpot ending skips it). */
    default boolean burnoutOnCollapse(DomainInstance domain, DomainInstance.EndReason reason) {
        return true;
    }
}
