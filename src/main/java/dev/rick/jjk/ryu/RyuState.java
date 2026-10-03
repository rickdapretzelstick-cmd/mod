package dev.rick.jjk.ryu;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * True Cannon's per-life memory, keyed to the living entity (lost on death): the Overheat meter (0-100) his discharges
 * build up, when he last front-dashed (Granite Blast's dash variant), and what Decadence held back (his hunger).
 */
public final class RyuState {
    private static final Map<LivingEntity, RyuState> STATES = new WeakHashMap<>();

    /** Overheat, 0-100. */
    public float heat;
    /** The last heat sent to his client (it is synced when it changes). */
    float sentHeat = -1;
    boolean sentAwakened;
    /** Game time of his last front dash. */
    public long frontDashAt = -1000;
    /** Decadence: the food level he had before his hunger took over (-1: not taken). */
    int savedFood = -1;

    public static RyuState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new RyuState());
    }

    public static void clear(LivingEntity e) {
        STATES.remove(e);
    }

    public boolean overheated() {
        return heat >= 100f - 1e-3f;
    }
}
