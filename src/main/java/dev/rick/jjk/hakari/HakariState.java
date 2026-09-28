package dev.rick.jjk.hakari;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hakari's per-life memory. Keyed to the living entity itself, so it is naturally lost on death (a respawned player is a
 * new entity) and is never tied to the player's account.
 * <ul>
 *   <li>After an odd-numbered jackpot: a probability change, better odds on the next domain's Riichi.</li>
 *   <li>After an even-numbered jackpot: the next domain starts with visual moves already counted.</li>
 * </ul>
 */
public final class HakariState {
    private static final Map<LivingEntity, HakariState> STATES = new WeakHashMap<>();

    /** Carried into the next domain. */
    public float oddsBonus;
    public int headStart;
    /** The numbers of the last jackpot (0 = none this life). */
    public int lastJackpot;
    /** Game time before which Jackpot can't save him from a lethal blow again. */
    public long lethalReadyAt;

    private HakariState() {}

    public static HakariState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new HakariState());
    }

    public static void clear(LivingEntity e) {
        STATES.remove(e);
    }

    /** A short line for the HUD describing the bonus waiting for the next gamble, or "". */
    public String bonusText() {
        if (oddsBonus > 0) return "PROBABILITY CHANGE +" + Math.round(oddsBonus * 100) + "%";
        if (headStart > 0) return "RESERVE: +" + headStart + " VISUAL";
        return "";
    }
}
