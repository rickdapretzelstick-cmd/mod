package dev.rick.jjk.util;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.world.WorldRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Controlled terrain destruction. Every technique that breaks blocks goes through here so the rules are uniform:
 * config switch + mobGriefing gamerule, hardness cap, never unbreakable or domain blocks, only loaded chunks, a global
 * per-tick budget so a Hollow Purple can't stall the server, and (by default) temporary: {@link WorldRestoration} puts
 * every destroyed block back exactly a few minutes later.
 */
public final class Destruction {
    private static long budgetTick = -1;
    private static int budgetUsed;

    private Destruction() {}

    public static boolean allowed(ServerLevel level) {
        return JJKConfig.get().general.allowBlockDestruction && level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    /** Whether this block may be destroyed by a technique at all. */
    public static boolean destructible(ServerLevel level, BlockPos pos, BlockState state, float maxHardness) {
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() == state.getFluidState().createLegacyBlock().getBlock()) return false;
        // Containers and other block entities are only touched when their data is kept and restored.
        if (state.hasBlockEntity() && !(WorldRestoration.enabled() && JJKConfig.get().restoration.destroyBlockEntities)) return false;
        if (dev.rick.jjk.registry.ModBlocks.isDomainBlock(state)) return false;
        if (state.is(BlockTags.WITHER_IMMUNE) || state.is(Blocks.BEDROCK)) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= Math.min(maxHardness, JJKConfig.get().general.maxDestructibleHardness);
    }

    /** Tries to destroy one block; returns true if it was removed. */
    public static boolean destroy(ServerLevel level, BlockPos pos, float maxHardness, @Nullable Entity breaker) {
        return destroy(level, pos, maxHardness, breaker, sourceOf(breaker));
    }

    /**
     * Tries to destroy one block as damage from {@code source} (a technique id); returns true if it was removed. With
     * world restoration on, the block is recorded and comes back later exactly as it was.
     */
    public static boolean destroy(ServerLevel level, BlockPos pos, float maxHardness, @Nullable Entity breaker, String source) {
        if (!level.isLoaded(pos) || !takeBudget(level)) return false;
        BlockState state = level.getBlockState(pos);
        if (!destructible(level, pos, state, maxHardness)) {
            refund();
            return false;
        }
        if (WorldRestoration.enabled()) return WorldRestoration.destroy(level, pos, state, breaker, source);
        return level.destroyBlock(pos, JJKConfig.get().general.destroyedBlocksDropItems, breaker, 512);
    }

    private static String sourceOf(@Nullable Entity breaker) {
        return breaker == null ? "unknown" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(breaker.getType()).toString();
    }

    /** Destroys blocks inside a sphere (nearest first); returns the number destroyed. */
    public static int sphere(ServerLevel level, Vec3 center, double radius, float maxHardness, int limit, @Nullable Entity breaker,
                             @Nullable Predicate<BlockState> filter) {
        return sphere(level, center, radius, maxHardness, limit, breaker, filter, sourceOf(breaker));
    }

    public static int sphere(ServerLevel level, Vec3 center, double radius, float maxHardness, int limit, @Nullable Entity breaker,
                             @Nullable Predicate<BlockState> filter, String source) {
        if (!allowed(level) || limit <= 0) return 0;
        int count = 0;
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        double r2 = radius * radius;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = r; y >= -r && count < limit; y--) {
            for (int x = -r; x <= r && count < limit; x++) {
                for (int z = -r; z <= r && count < limit; z++) {
                    double dx = x + 0.5 + c.getX() - center.x, dy = y + 0.5 + c.getY() - center.y, dz = z + 0.5 + c.getZ() - center.z;
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (!level.isLoaded(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || filter != null && !filter.test(s)) continue;
                    if (destroy(level, p.immutable(), maxHardness, breaker, source)) count++;
                    if (budgetExhausted(level)) return count;
                }
            }
        }
        return count;
    }

    private static boolean takeBudget(ServerLevel level) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        if (budgetUsed >= JJKConfig.get().general.maxBlocksPerTick) return false;
        budgetUsed++;
        return true;
    }

    private static void refund() {
        budgetUsed = Math.max(0, budgetUsed - 1);
    }

    public static boolean budgetExhausted(ServerLevel level) {
        return level.getGameTime() == budgetTick && budgetUsed >= JJKConfig.get().general.maxBlocksPerTick;
    }
}
