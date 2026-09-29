package dev.rick.jjk.client;

import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.net.CasterSyncPayload;
import dev.rick.jjk.core.net.DomainPayload;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Everything the client knows about its own character and about nearby casts/domains. */
public final class ClientState {
    public static String character = "";
    public static float energy, maxEnergy;
    public static int[] cooldowns = new int[AbilitySlot.values().length];
    public static int[] maxCooldowns = new int[AbilitySlot.values().length];
    public static int[] charges = new int[AbilitySlot.values().length];
    public static int flags;
    public static String activeCast = "";
    public static int castTicks;
    public static float awakening, awakeningMax = 100;
    /** Ability id bound to each slot in the current mode ("" when empty). */
    public static String[] slotAbilities = new String[AbilitySlot.values().length];
    /** Client tick when the awakened state last changed (drives HUD transitions). */
    public static long awakenedChangedTick;

    // Combo counter (as attacker).
    public static int comboCount;
    public static float comboDamage;
    public static long comboTime;
    public static int lastHitOutcome;

    /** Cast visuals for any entity: entityId → cast. */
    public static final Map<Integer, Cast> CASTS = new HashMap<>();
    /** Known domains by id. */
    public static final Map<Integer, Domain> DOMAINS = new HashMap<>();

    public record Cast(String ability, int phase, int duration, long startTick, long phaseTick) {}

    public static final class Domain {
        public int id, ownerId, phase, age, duration, clashWith;
        public String definition;
        public Vec3 center;
        public float radius;
        public long lastUpdateTick;
        public long phaseStartTick;
        /** Formation schedule: ticks to build, and the shell thickness (structure radius = radius + thickness). */
        public int formationTicks = 1;
        public int thickness;
        /** Clash feedback: last perfect-input pulse (game time, strength) and a destabilised-until time after a miss. */
        public long pulseTick = Long.MIN_VALUE;
        public float pulseStrength;
        public long unstableUntil = Long.MIN_VALUE;
        /**
         * Clash split: how far toward domain {@code splitWith}'s center this domain's side reaches (splitWith -1: not split),
         * and the value actually drawn (eased toward the server's so the boundary glides).
         */
        public float split, splitShown;
        public int splitWith = -1;
        /** Territory conquered in a clash: spheres as x,y,z,radius. */
        public float[] annex = new float[0];
        /** The phase before the current one (-1 none): a domain that just sealed went FORMING → ACTIVE. */
        public int prevPhase = -1;
    }

    /** Inside a sealed Idle Death Gamble (its endless white room). */
    public static boolean inWhiteRoom(Vec3 pos) {
        for (Domain d : DOMAINS.values()) {
            if (d.phase != dev.rick.jjk.core.net.DomainPayload.ACTIVE || d.center == null) continue;
            if (!dev.rick.jjk.hakari.IdleDeathGamble.ID.equals(d.definition)) continue;
            if (pos.distanceTo(d.center) < d.radius + d.thickness + 0.5) return true;
        }
        return false;
    }

    /** The live domain owned by this entity, if any. */
    @org.jetbrains.annotations.Nullable
    public static Domain domainOwnedBy(int entityId) {
        for (Domain d : DOMAINS.values()) if (d.ownerId == entityId && d.phase != dev.rick.jjk.core.net.DomainPayload.REMOVED) return d;
        return null;
    }

    /** Idle Death Gamble state per domain id (the gamble HUD and the giant reels over the domain). */
    public static final Map<Integer, Gamble> GAMBLES = new HashMap<>();

    public static final class Gamble {
        public dev.rick.jjk.core.net.GamblePayload p;
        /** Game time the current state began (for animating the reels and the Riichi). */
        public long stateStart;
        /** Game time the last visual move landed (the reels jolt). */
        public long visualTick = -100000L;
    }

    /** The gamble in a domain Hakari owns (null if none). */
    @org.jetbrains.annotations.Nullable
    public static Gamble gambleOf(int ownerId) {
        for (Gamble g : GAMBLES.values()) if (g.p.ownerId() == ownerId) return g;
        return null;
    }

    public static void applyGamble(dev.rick.jjk.core.net.GamblePayload p, long now) {
        if (p.state() == dev.rick.jjk.core.net.GamblePayload.REMOVED) {
            GAMBLES.remove(p.domainId());
            return;
        }
        Gamble g = GAMBLES.computeIfAbsent(p.domainId(), k -> new Gamble());
        if (g.p != null && g.p.progress() < p.progress()) g.visualTick = now;
        g.stateStart = now - p.stateAge();
        g.p = p;
    }

    /** Domain counter window (game tick it closes), its length, and the domain being opened. */
    public static long counterUntilTick;
    public static int counterWindow = 1;
    public static String counterDomain = "";

    private ClientState() {}

    public static boolean hasCharacter() {
        return !character.isEmpty();
    }

    public static void apply(CasterSyncPayload p) {
        boolean wasAwakened = awakened();
        character = p.character();
        energy = p.energy();
        maxEnergy = p.maxEnergy();
        cooldowns = p.cooldowns();
        maxCooldowns = p.maxCooldowns();
        charges = p.charges();
        flags = p.flags();
        activeCast = p.activeCast();
        castTicks = p.castTicks();
        awakening = p.awakening();
        awakeningMax = Math.max(1, p.awakeningMax());
        String[] ids = p.abilities().split(",", -1);
        slotAbilities = new String[AbilitySlot.values().length];
        for (int i = 0; i < slotAbilities.length; i++) slotAbilities[i] = i < ids.length ? ids[i] : "";
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (wasAwakened != awakened() && mc.level != null) awakenedChangedTick = mc.level.getGameTime();
    }

    public static void tick() {
        for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        if (!activeCast.isEmpty()) castTicks++;
    }

    public static int cooldown(AbilitySlot slot) {
        return slot.ordinal() < cooldowns.length ? cooldowns[slot.ordinal()] : 0;
    }

    public static int maxCooldown(AbilitySlot slot) {
        return slot.ordinal() < maxCooldowns.length ? Math.max(1, maxCooldowns[slot.ordinal()]) : 1;
    }

    public static int charges(AbilitySlot slot) {
        return slot.ordinal() < charges.length ? charges[slot.ordinal()] : 0;
    }

    public static boolean awakened() {
        return (flags & CasterSyncPayload.FLAG_AWAKENED) != 0;
    }

    public static String abilityIn(AbilitySlot slot) {
        String s = slot.ordinal() < slotAbilities.length ? slotAbilities[slot.ordinal()] : null;
        return s == null ? "" : s;
    }

    public static boolean flag(int f) {
        return (flags & f) != 0;
    }

    public static void applyDomain(DomainPayload p, long now) {
        if (p.phase() == DomainPayload.REMOVED) {
            DOMAINS.remove(p.id());
            return;
        }
        Domain d = DOMAINS.computeIfAbsent(p.id(), k -> {
            Domain nd = new Domain();
            nd.phaseStartTick = now;
            return nd;
        });
        if (d.phase != p.phase()) {
            d.prevPhase = d.phase;
            d.phaseStartTick = now;
        }
        d.id = p.id();
        d.ownerId = p.ownerId();
        d.definition = p.definition();
        d.center = p.center();
        d.radius = p.radius();
        d.phase = p.phase();
        d.age = p.age();
        d.duration = p.duration();
        d.clashWith = p.clashWith();
        d.formationTicks = Math.max(1, p.formationTicks());
        d.thickness = p.thickness();
        // A new split (or a domain just consumed) starts where the server says; after that the boundary glides.
        if (p.splitWith() != d.splitWith || p.split() == DomainPayload.CONSUMED) d.splitShown = p.split();
        d.split = p.split();
        d.splitWith = p.splitWith();
        d.annex = p.annex();
        // Late joiners see the formation where it actually is, not from the start.
        if (p.phase() == DomainPayload.FORMING) d.phaseStartTick = now - p.age();
        d.lastUpdateTick = now;
    }

    public static void reset() {
        character = "";
        energy = maxEnergy = 0;
        activeCast = "";
        CASTS.clear();
        DOMAINS.clear();
        GAMBLES.clear();
        // A counter window belongs to the world it was offered in.
        counterUntilTick = 0;
        counterDomain = "";
        comboCount = 0;
    }
}
