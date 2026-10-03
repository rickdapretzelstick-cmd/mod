package dev.rick.jjk.core.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fall damage from height a move created doesn't count. When a move throws someone upward (a launch, a door, a juggle,
 * a leap of their own), the height they were launched from is remembered until they land; on landing, only how far they
 * end up below that point counts as a fall. Launched 20 blocks and back down where they started: no damage. Launched 20
 * and landing 10 below their start (falling 30 in all): a 10-block fall. Juggled again in the air, the first launch point
 * stays. Ordinary jumps and falls are untouched.
 */
public final class LaunchHeight {
    /** Entity → {launch height, game time of the launch}. */
    private static final Map<Entity, double[]> LAUNCHED = new WeakHashMap<>();

    private LaunchHeight() {}

    /** A move just gave {@code e} upward speed. */
    public static void launched(Entity e) {
        if (!(e instanceof LivingEntity) || e.level().isClientSide()) return;
        double[] l = LAUNCHED.get(e);
        // Already airborne from an earlier launch: that point stays (a juggle never adds a fall).
        if (l != null && !e.onGround()) {
            l[1] = e.level().getGameTime();
            return;
        }
        LAUNCHED.put(e, new double[] {e.getY(), e.level().getGameTime()});
    }

    /** The fall distance that counts on landing: only the drop below the launch point. */
    public static double adjust(LivingEntity e, double fallDistance) {
        double[] l = LAUNCHED.remove(e);
        if (l == null) return fallDistance;
        return Math.min(fallDistance, Math.max(0, l[0] - e.getY()));
    }

    /** Whether a launch is being remembered for {@code e} (tests). */
    public static boolean tracking(LivingEntity e) {
        return LAUNCHED.containsKey(e);
    }

    /** Each tick: a launched entity that has been back on the ground a moment is no longer launched. */
    public static void tick(LivingEntity e) {
        double[] l = LAUNCHED.get(e);
        if (l != null && (e.onGround() || e.isInWater()) && e.level().getGameTime() - l[1] > 10) LAUNCHED.remove(e);
    }
}
