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
    /** Combat stance: left click performs this mod's melee instead of vanilla attacks. */
    public static boolean stanceEnabled = true;

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
        /** Smoothly animated 0..1 barrier formation. */
        public float formation;
    }

    private ClientState() {}

    public static boolean hasCharacter() {
        return !character.isEmpty();
    }

    public static void apply(CasterSyncPayload p) {
        character = p.character();
        energy = p.energy();
        maxEnergy = p.maxEnergy();
        cooldowns = p.cooldowns();
        maxCooldowns = p.maxCooldowns();
        charges = p.charges();
        flags = p.flags();
        activeCast = p.activeCast();
        castTicks = p.castTicks();
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
        if (d.phase != p.phase()) d.phaseStartTick = now;
        d.id = p.id();
        d.ownerId = p.ownerId();
        d.definition = p.definition();
        d.center = p.center();
        d.radius = p.radius();
        d.phase = p.phase();
        d.age = p.age();
        d.duration = p.duration();
        d.clashWith = p.clashWith();
        d.lastUpdateTick = now;
    }

    public static void reset() {
        character = "";
        energy = maxEnergy = 0;
        activeCast = "";
        CASTS.clear();
        DOMAINS.clear();
        comboCount = 0;
    }
}
