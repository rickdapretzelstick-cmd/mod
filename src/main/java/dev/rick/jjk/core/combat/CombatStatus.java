package dev.rick.jjk.core.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A timed combat condition an entity can be in (hitstun, launched, pulled, knocked down, ...).
 *
 * Statuses are data, not code: each one declares what it restricts, and the systems that care
 * (movement, AI, abilities, gravity, melee) ask {@link CombatState} about those properties.
 * New characters can register their own statuses with {@link #register}.
 */
public final class CombatStatus {
    private static final List<CombatStatus> REGISTRY = new ArrayList<>();

    /** Took a hit: can't move, attack or cast. */
    public static final CombatStatus HITSTUN = register(builder("hitstun").lockMovement().lockActions().lockTechniques().interrupts());
    /** Knocked into the air: gravity is reduced so follow-ups can connect. */
    public static final CombatStatus LAUNCHED = register(builder("launched").gravity(0.42f));
    /** Being dragged by an attraction technique. */
    public static final CombatStatus PULLED = register(builder("pulled").lockMovement().lockActions().lockTechniques().gravity(0f).interrupts());
    /** Spiked downward; becomes KNOCKDOWN on landing. */
    public static final CombatStatus SPIKED = register(builder("spiked").lockMovement().lockActions().lockTechniques().gravity(1.6f));
    /** On the ground. Normal melee whiffs; ground attacks and techniques still connect. */
    public static final CombatStatus KNOCKDOWN = register(builder("knockdown").lockMovement().lockActions().lockTechniques().downed().interrupts());
    /** Getting back up: briefly immune to melee to prevent infinite loops. */
    public static final CombatStatus WAKEUP = register(builder("wakeup").meleeImmune());
    /** Unlimited Void's information overload. */
    public static final CombatStatus OVERLOAD = register(builder("overload").lockMovement().lockActions().lockTechniques().gravity(0.6f).interrupts());
    /** Guard shattered or parried. */
    public static final CombatStatus GUARD_BROKEN = register(builder("guard_broken").lockMovement().lockActions().lockTechniques().interrupts());
    /** Cursed technique burnt out (after a domain): no techniques. */
    public static final CombatStatus BURNOUT = register(builder("burnout").lockTechniques());
    /** Caster hanging in the air during air combos / after an aerial teleport. */
    public static final CombatStatus HOVER = register(builder("hover").gravity(0.12f));
    /** Evasive invulnerability (dash, teleport). */
    public static final CombatStatus EVADING = register(builder("evading").evasive());
    /** Infinity is up (informational; synced so everyone sees the distortion). */
    public static final CombatStatus INFINITY = register(builder("infinity"));
    /** Inside an enemy domain. */
    public static final CombatStatus IN_DOMAIN = register(builder("in_domain"));
    /** Mid-transformation (awakening): rooted, can't act, untouchable. */
    public static final CombatStatus AWAKENING = register(builder("awakening").lockMovement().lockActions().evasive());
    /** Locked in a domain clash: rooted, can't act, untouchable while the duel plays out. */
    public static final CombatStatus CLASHING = register(builder("clashing").lockMovement().lockActions().lockTechniques().evasive());
    /** Awakened state (informational, drives visuals for everyone). */
    public static final CombatStatus AWAKENED = register(builder("awakened"));
    /** Wearing the blindfold (informational, drives the blindfold visual). */
    public static final CombatStatus BLINDFOLD = register(builder("blindfold"));
    /** Slowed by pushing against Infinity. */
    public static final CombatStatus INFINITY_SLOWED = register(builder("infinity_slowed"));
    /** Held in someone's grip (Lucky Rushdown's drag, Lucky Volley's flurry): no gravity, can't act. */
    public static final CombatStatus GRABBED = register(builder("grabbed").lockMovement().lockActions().lockTechniques().gravity(0f).interrupts());
    /** Hakari's Riichi presentation is playing: he is untouchable and can't act until it resolves. */
    public static final CombatStatus GAMBLING = register(builder("gambling").lockMovement().lockActions().lockTechniques().evasive());
    /** Hakari's Jackpot (the visible state; the moveset itself is the awakened kit). */
    public static final CombatStatus JACKPOT = register(builder("jackpot"));
    /** Rhythm's reward: every Jackpot move hits harder while it lasts. */
    public static final CombatStatus LUCKY_STREAK = register(builder("lucky_streak"));

    public final String id;
    public final int index;
    public final boolean locksMovement;
    public final boolean locksActions;
    public final boolean locksTechniques;
    public final boolean interruptsCasting;
    public final boolean downed;
    public final boolean meleeImmune;
    public final boolean evasive;
    public final float gravityScale;

    private CombatStatus(Builder b, int index) {
        this.id = b.id;
        this.index = index;
        this.locksMovement = b.locksMovement;
        this.locksActions = b.locksActions;
        this.locksTechniques = b.locksTechniques;
        this.interruptsCasting = b.interrupts;
        this.downed = b.downed;
        this.meleeImmune = b.meleeImmune;
        this.evasive = b.evasive;
        this.gravityScale = b.gravity;
    }

    public static synchronized CombatStatus register(Builder b) {
        CombatStatus s = new CombatStatus(b, REGISTRY.size());
        REGISTRY.add(s);
        return s;
    }

    public static List<CombatStatus> all() {
        return Collections.unmodifiableList(REGISTRY);
    }

    public static CombatStatus byIndex(int i) {
        return i >= 0 && i < REGISTRY.size() ? REGISTRY.get(i) : null;
    }

    public static int count() {
        return REGISTRY.size();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    @Override
    public String toString() {
        return id;
    }

    public static final class Builder {
        private final String id;
        private boolean locksMovement, locksActions, locksTechniques, interrupts, downed, meleeImmune, evasive;
        private float gravity = 1f;

        private Builder(String id) { this.id = id; }
        public Builder lockMovement() { locksMovement = true; return this; }
        public Builder lockActions() { locksActions = true; return this; }
        public Builder lockTechniques() { locksTechniques = true; return this; }
        public Builder interrupts() { interrupts = true; return this; }
        public Builder downed() { downed = true; return this; }
        public Builder meleeImmune() { meleeImmune = true; return this; }
        public Builder evasive() { evasive = true; return this; }
        public Builder gravity(float g) { gravity = g; return this; }
    }
}
