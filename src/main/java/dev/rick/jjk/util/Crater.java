package dev.rick.jjk.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A spherical crater blown out of the terrain over a few ticks: carved from the centre outward one ring at a time,
 * within {@link Destruction}'s shared per-tick block budget and the crater's own block cap, so even a big one never
 * stalls the server. Every block goes through {@link Destruction} (hardness cap, mobGriefing, restoration).
 */
public final class Crater {
    private static final List<Crater> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    @Nullable private final Entity breaker;
    private final Vec3 center;
    private final double radius;
    private final float maxHardness;
    private final int limit;
    private final String source;
    private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    private int ring;
    private int carved;

    private Crater(ServerLevel level, @Nullable Entity breaker, Vec3 center, double radius, float maxHardness, int limit, String source) {
        this.level = level;
        this.breaker = breaker;
        this.center = center;
        this.radius = radius;
        this.maxHardness = maxHardness;
        this.limit = limit;
        this.source = source;
    }

    /** Starts blowing out a crater (nothing happens where block destruction is off). */
    public static void start(ServerLevel level, @Nullable Entity breaker, Vec3 center, double radius, float maxHardness, int limit, String source) {
        if (!Destruction.allowed(level) || radius <= 0 || limit <= 0) return;
        Crater c = new Crater(level, breaker, center, radius, maxHardness, limit, source);
        if (!c.carve()) ACTIVE.add(c);
    }

    public static void tick(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        for (Crater c : List.copyOf(ACTIVE)) {
            if (c.level == level && c.carve()) ACTIVE.remove(c);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    /** Whether any crater is still being carved (tests). */
    public static boolean busy() {
        return !ACTIVE.isEmpty();
    }

    /** As far as this tick's budget goes; true when finished (or capped). */
    private boolean carve() {
        int scans = 0;
        while (carved < limit) {
            if (pending.isEmpty()) {
                if (ring > radius) return true;
                // At most two rings looked over a tick (the outer ones are large), the rest next tick.
                if (scans++ >= 2) return false;
                collectRing(ring++);
                continue;
            }
            if (Destruction.budgetExhausted(level)) return false;
            BlockPos p = pending.poll();
            if (level.isLoaded(p) && !level.getBlockState(p).isAir() && Destruction.destroy(level, p, maxHardness, breaker, source)) carved++;
        }
        return true;
    }

    /** Every solid block whose distance from the centre falls in [k, k+1) (and within the radius). */
    private void collectRing(int k) {
        int r = k + 1;
        BlockPos c = BlockPos.containing(center);
        double lo = k * (double) k, hi = Math.min(k + 1.0, radius) * Math.min(k + 1.0, radius);
        if (hi <= lo) return;
        for (int y = r; y >= -r; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    double dx = x + 0.5 + c.getX() - center.x, dy = y + 0.5 + c.getY() - center.y, dz = z + 0.5 + c.getZ() - center.z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < lo || d2 >= hi) continue;
                    BlockPos p = new BlockPos(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (level.isLoaded(p) && !level.getBlockState(p).isAir()) pending.add(p);
                }
            }
        }
    }
}
