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
 *
 * <p>Height can also come without an upward push: a move that teleports its user (or a victim) into the air ({@link
 * #displaced}), or anyone left airborne mid-move or mid-combo (casting, in hitstun, launched, spiked, hovering, pulled).
 * Those are covered too: the point they last stood on, or where they were moved from, is the launch point.
 */
public final class LaunchHeight {
    /** Entity → {launch height, game time of the launch}. */
    private static final Map<Entity, double[]> LAUNCHED = new WeakHashMap<>();
    /** Entity → the height it last stood on the ground at. */
    private static final Map<Entity, Double> GROUND = new WeakHashMap<>();
    /** Statuses that mean the entity is in the air because of a fight (a move or a hit put them there). */
    private static final CombatStatus[] AIRBORNE_BY_COMBAT = {CombatStatus.HITSTUN, CombatStatus.LAUNCHED, CombatStatus.SPIKED,
            CombatStatus.HOVER, CombatStatus.PULLED, CombatStatus.OVERLOAD, CombatStatus.GUARD_BROKEN};

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

    /**
     * A move moved {@code e} from height {@code fromY} (a teleport, a blink behind someone): if that put them higher, the
     * fall back down to {@code fromY} is not damage. Already airborne from a launch: that point stays.
     */
    public static void displaced(Entity e, double fromY) {
        if (!(e instanceof LivingEntity) || e.level().isClientSide()) return;
        double[] l = LAUNCHED.get(e);
        if (l != null) {
            l[1] = e.level().getGameTime();
            return;
        }
        LAUNCHED.put(e, new double[] {fromY, e.level().getGameTime()});
        e.resetFallDistance();
    }

    /** The fall distance that counts on landing: only the drop below the launch point. */
    public static double adjust(LivingEntity e, double fallDistance) {
        double[] l = LAUNCHED.remove(e);
        if (l == null) return fallDistance;
        return Math.min(fallDistance, Math.max(0, l[0] - e.getY()));
    }

    private static boolean inAMove(LivingEntity e) {
        dev.rick.jjk.core.ability.AbilityCaster c = dev.rick.jjk.core.ability.Casters.active(e);
        if (c != null && c.isCasting()) return true;
        for (CombatStatus s : AIRBORNE_BY_COMBAT) if (Combat.has(e, s)) return true;
        return false;
    }

    /** Whether a launch is being remembered for {@code e} (tests). */
    public static boolean tracking(LivingEntity e) {
        return LAUNCHED.containsKey(e);
    }

    /** Each tick: a launched entity that has been back on the ground a moment is no longer launched. */
    public static void tick(LivingEntity e) {
        if (e.onGround()) GROUND.put(e, e.getY());
        else if (!LAUNCHED.containsKey(e) && inAMove(e)) {
            // In the air mid-move or mid-combo without a recorded launch (a teleport upward, a hit that only kept them
            // up): where they last stood is the launch point.
            Double ground = GROUND.get(e);
            LAUNCHED.put(e, new double[] {ground != null ? ground : e.getY(), e.level().getGameTime()});
        }
        double[] l = LAUNCHED.get(e);
        if (l != null && (e.onGround() || e.isInWater()) && e.level().getGameTime() - l[1] > 10) LAUNCHED.remove(e);
    }
}
