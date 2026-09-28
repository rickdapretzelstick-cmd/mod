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
    private final Map<AbilitySlot, Ability> awakenedAbilities = new EnumMap<>(AbilitySlot.class);

    protected JJKCharacter(String id) {
        this.id = id;
    }

    protected void bind(AbilitySlot slot, Ability ability) {
        abilities.put(slot, ability);
    }

    /** Binds an ability in the awakened moveset. Slots not bound here are empty while awakened. */
    protected void bindAwakened(AbilitySlot slot, Ability ability) {
        awakenedAbilities.put(slot, ability);
    }

    @Nullable
    public Ability ability(AbilitySlot slot) {
        return abilities.get(slot);
    }

    @Nullable
    public Ability ability(AbilitySlot slot, boolean awakened) {
        return awakened ? awakenedAbilities.get(slot) : abilities.get(slot);
    }

    /** Every ability in either moveset (for cleanup of toggles etc.). */
    public java.util.Collection<Ability> abilitiesAllModes() {
        java.util.Set<Ability> all = new java.util.LinkedHashSet<>(abilities.values());
        all.addAll(awakenedAbilities.values());
        return all;
    }

    public Map<AbilitySlot, Ability> abilities() {
        return abilities;
    }

    /** Called when the caster enters or leaves its awakened state. */
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {}

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
