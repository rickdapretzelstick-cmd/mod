package dev.rick.jjk.core.defense;

import net.minecraft.world.entity.LivingEntity;

/**
 * A defensive technique or mechanic that can intercept attacks aimed at its owner.
 * Layers run from highest to lowest {@link #priority()}: outer defenses (Infinity stops things
 * before they arrive) run before inner ones (a physical guard).
 */
public interface DefenseLayer {
    String id();

    int priority();

    /** Whether this layer is currently up for the given entity. */
    boolean isActive(LivingEntity defender);

    DefenseResult intercept(LivingEntity defender, IncomingAttack attack);

    /** Called after the layer negated or blocked something (for costs and effects). */
    default void afterIntercept(LivingEntity defender, IncomingAttack attack, DefenseResult result) {}
}
