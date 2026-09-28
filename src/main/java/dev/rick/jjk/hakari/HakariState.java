package dev.rick.jjk.hakari;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hakari's per-life memory. Keyed to the living entity itself, so it is naturally lost on death (a respawned player is a
 * new entity) and is never tied to the player's account.
 * <ul>
 *   <li>After an odd-numbered jackpot: a probability change, better odds on the next domain's Riichi.</li>
 *   <li>After an even-numbered jackpot: the next domain's Riichi scenarios play twice as fast.</li>
 *   <li>Consecutive jackpots: each one refunds more Awakening when it ends.</li>
 *   <li>Rhythm stacks: permanent (for this life) speed on his moves.</li>
 *   <li>Renewal: the moment the last Reserve Ball landed inside his domain, to rewind to.</li>
 * </ul>
 */
public final class HakariState {
    private static final Map<LivingEntity, HakariState> STATES = new WeakHashMap<>();

    /** Carried into the next domain. */
    public float oddsBonus;
    public boolean fastRiichi;
    /** The numbers of the last jackpot (0 = none this life). */
    public int lastJackpot;
    /** Jackpots in a row (broken by a domain without one, or death). */
    public int jackpotChain;
    /** Rhythm dances finished. */
    public int rhythmStacks;
    /** Health last tick (Jackpot turns damage taken into meter drained). */
    public float lastHealth;
    @Nullable public Renewal renewal;

    /** Where everyone stood when a Reserve Ball landed, and how much health Hakari had. */
    public record Renewal(long expires, float health, List<Spot> spots) {}

    public record Spot(LivingEntity entity, Vec3 pos, float yaw) {}

    private HakariState() {}

    public static HakariState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new HakariState());
    }

    public static void clear(LivingEntity e) {
        STATES.remove(e);
    }

    static Renewal snapshot(long expires, float health, List<LivingEntity> who) {
        List<Spot> spots = new ArrayList<>();
        for (LivingEntity e : who) spots.add(new Spot(e, e.position(), e.getYRot()));
        return new Renewal(expires, health, spots);
    }

    /** A short line for the HUD describing the bonus waiting for the next gamble, or "". */
    public String bonusText() {
        if (oddsBonus > 0) return "PROBABILITY CHANGE +" + Math.round(oddsBonus * 100) + "%";
        if (fastRiichi) return "FAST RIICHI";
        return "";
    }
}
