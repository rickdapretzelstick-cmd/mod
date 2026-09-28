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

    // --- Identity (shown by the character select screen; every character registers its own) ---

    /** Short name, e.g. "Gojo". */
    public String displayName() {
        return id;
    }

    /** The character's JJS title, e.g. "Honored One". */
    public String title() {
        return "";
    }

    /** One or two sentences on how the character plays. */
    public String description() {
        return "";
    }

    // --- Hooks the shared systems call ---

    /**
     * How fast the awakened state's timer drains, as meter per second (the meter doubles as the timer). Gojo's
     * Awakening and Hakari's Jackpot both run on it.
     */
    public float awakeningDrainPerSecond() {
        return dev.rick.jjk.config.JJKConfig.get().awakening.drainPerSecond;
    }

    /**
     * Whether answering a domain with the counter first puts the sorcerer into their awakened state (Gojo's eyes open
     * and his domain answers). Characters whose domain is how they reach that state (Hakari's Jackpot) open it directly.
     */
    public boolean awakensOnCounter() {
        return true;
    }

    /** An ability was just used (after its costs were paid). */
    public void onAbilityUsed(AbilityCaster caster, dev.rick.jjk.core.ability.Ability ability, AbilitySlot slot) {}

    /**
     * About to die from {@code source}. Return true to cancel the death (the character's own state must then make the
     * entity survive, e.g. heal it).
     */
    public boolean preventDeath(AbilityCaster caster, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    /** The entity died (for clearing per-life state). */
    public void onDeath(AbilityCaster caster) {}

    /**
     * A reason this caster can't switch to another character right now (beyond the shared neutral-state rules), or
     * null.
     */
    public @org.jetbrains.annotations.Nullable String switchBlocked(AbilityCaster caster) {
        return null;
    }
}
