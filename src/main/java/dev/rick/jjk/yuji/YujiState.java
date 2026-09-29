package dev.rick.jjk.yuji;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Vessel's per-life memory, keyed to the living entity (lost on death, never tied to an account):
 * <ul>
 *   <li>the front dash lock after Cursed Strikes lands;</li>
 *   <li>the Black Flash chain: how many have landed in a row on whose back, and until when the next one continues it;</li>
 *   <li>Manji Kick's raised stance (when it went up, so a hit inside the window is countered).</li>
 * </ul>
 */
public final class YujiState {
    private static final Map<LivingEntity, YujiState> STATES = new WeakHashMap<>();

    public long frontDashLockedUntil;
    /** Black Flashes chained so far (0 = no chain running). */
    public int chain;
    public long chainUntil;
    @Nullable public LivingEntity chainTarget;
    /** Game time Manji Kick's stance went up, or -1 while it is down. */
    public long manjiRaised = -1;

    public static YujiState of(LivingEntity e) {
        return STATES.computeIfAbsent(e, k -> new YujiState());
    }

    public static void clear(LivingEntity e) {
        STATES.remove(e);
    }

    /** A chain is running on this target right now. */
    public boolean chaining(LivingEntity target, long now) {
        return chain > 0 && now <= chainUntil && chainTarget == target;
    }

    public void breakChain() {
        chain = 0;
        chainTarget = null;
    }
}
