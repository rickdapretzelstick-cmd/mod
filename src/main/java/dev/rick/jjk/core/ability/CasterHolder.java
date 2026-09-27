package dev.rick.jjk.core.ability;

/** Implemented on LivingEntity by mixin. */
public interface CasterHolder {
    AbilityCaster jjk$caster();

    AbilityCaster jjk$casterOrNull();
}
