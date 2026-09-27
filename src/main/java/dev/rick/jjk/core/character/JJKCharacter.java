package dev.rick.jjk.core.character;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** A playable sorcerer: resources, a melee moveset and abilities bound to input slots. */
public abstract class JJKCharacter {
    public final String id;
    private final Map<AbilitySlot, Ability> abilities = new EnumMap<>(AbilitySlot.class);

    protected JJKCharacter(String id) {
        this.id = id;
    }

    protected void bind(AbilitySlot slot, Ability ability) {
        abilities.put(slot, ability);
    }

    @Nullable
    public Ability ability(AbilitySlot slot) {
        return abilities.get(slot);
    }

    public Map<AbilitySlot, Ability> abilities() {
        return abilities;
    }

    public abstract float maxEnergy();

    public abstract float regenPerSecond();

    public abstract MeleeMoveset melee();

    /** Called when a caster becomes this character. */
    public void onAssigned(AbilityCaster caster) {}

    /** Called when a caster stops being this character. */
    public void onRemoved(AbilityCaster caster) {}

    /** Passive per-tick logic. */
    public void tick(AbilityCaster caster) {}
}
