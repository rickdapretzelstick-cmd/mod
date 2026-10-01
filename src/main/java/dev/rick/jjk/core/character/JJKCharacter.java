package dev.rick.jjk.core.character;

import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.combat.melee.MeleeMoveset;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A playable sorcerer: resources, a melee moveset and abilities bound to input slots.
 *
 * <p>A character has up to {@link #MODES} movesets ("modes"): the base kit (mode 0), the awakened kit (mode 1), and any
 * others it switches between itself (Cursed Partners' Rika movesets, modes 2 and 3). {@link #mode} says which one a caster
 * is using right now; each mode's slots keep their own cooldowns, except an ability bound in several modes, which shares
 * one.
 */
public abstract class JJKCharacter {
    public static final int MODES = 4;
    public static final int BASE = 0, AWAKENED = 1;
    public final String id;
    private final List<Map<AbilitySlot, Ability>> modes = new ArrayList<>();

    protected JJKCharacter(String id) {
        this.id = id;
        for (int i = 0; i < MODES; i++) modes.add(new EnumMap<>(AbilitySlot.class));
    }

    protected void bind(AbilitySlot slot, Ability ability) {
        bindMode(BASE, slot, ability);
    }

    /** Binds an ability in the awakened moveset. Slots not bound here are empty while awakened. */
    protected void bindAwakened(AbilitySlot slot, Ability ability) {
        bindMode(AWAKENED, slot, ability);
    }

    /** Binds an ability in any moveset (0 base, 1 awakened, 2 and 3 the character's own). */
    protected void bindMode(int mode, AbilitySlot slot, Ability ability) {
        modes.get(mode).put(slot, ability);
    }

    /** Which moveset this caster is using right now. */
    public int mode(AbilityCaster caster) {
        return caster.isAwakened() ? AWAKENED : BASE;
    }

    @Nullable
    public Ability ability(AbilitySlot slot) {
        return modes.get(BASE).get(slot);
    }

    @Nullable
    public Ability ability(AbilitySlot slot, boolean awakened) {
        return modes.get(awakened ? AWAKENED : BASE).get(slot);
    }

    @Nullable
    public Ability ability(AbilitySlot slot, int mode) {
        return mode >= 0 && mode < MODES ? modes.get(mode).get(slot) : null;
    }

    /** Every ability in every moveset (for cleanup of toggles etc.). */
    public java.util.Collection<Ability> abilitiesAllModes() {
        java.util.Set<Ability> all = new java.util.LinkedHashSet<>();
        for (Map<AbilitySlot, Ability> m : modes) all.addAll(m.values());
        return all;
    }

    public Map<AbilitySlot, Ability> abilities() {
        return modes.get(BASE);
    }

    /** Multiplier on the caster's movement from the character's own state (1 = unaffected). */
    public float movementMultiplier(AbilityCaster caster) {
        return 1f;
    }

    /** Called when the caster enters or leaves its awakened state. */
    public void onAwakeningChanged(AbilityCaster caster, boolean awakened) {}

    public abstract float maxEnergy();

    public abstract float regenPerSecond();

    public abstract MeleeMoveset melee();

    /** The melee this caster fights with right now (a character whose basic attacks change while awakened overrides it). */
    public MeleeMoveset melee(AbilityCaster caster) {
        return melee();
    }

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

    /**
     * A press of {@code slot} the character handles itself before the normal checks (busy, cooldown, cost): a combination
     * pressed during another move's wind-up, or a follow-up that works while the move is on cooldown. Return true if
     * the press was used.
     */
    public boolean interceptInput(AbilityCaster caster, AbilitySlot slot, dev.rick.jjk.core.ability.Ability ability,
                                  @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity targetHint) {
        return false;
    }

    /** How fast this caster's abilities play out (1 = normal). */
    public float castSpeed(AbilityCaster caster) {
        return 1f;
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
