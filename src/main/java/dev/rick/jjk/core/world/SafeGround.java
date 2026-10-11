package dev.rick.jjk.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Getting someone out of ground that closed over them. Whatever puts terrain back (an incident's site coming down, the
 * world restoring combat damage, a domain giving its blocks back) must never leave a player inside it, or sealed in a
 * pocket of it with no way out: they are brought to a real place to stand on the surface instead.
 */
public final class SafeGround {
    /** A pocket with fewer open spaces than this, reachable from where someone stands, counts as sealed in. */
    static final int POCKET = 48;

    private SafeGround() {}

    /**
     * Whether whoever stands at {@code feet} is shut in: every way out of the open space around them is blocked within
     * a few blocks (a flood fill through the spaces they could move through, cut off at {@link #POCKET}).
     */
    public static boolean sealedIn(ServerLevel level, BlockPos feet) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        if (!passable(level, feet)) return true;
        open.add(feet.immutable());
        seen.add(feet.asLong());
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            for (Direction d : BlockSnapshots.DIRECTIONS) {
                BlockPos n = at.relative(d);
                // Open sky, or ground nobody knows yet: not shut in.
                if (n.getY() >= level.getMaxY() || !level.isLoaded(n)) return false;
                if (seen.contains(n.asLong()) || !passable(level, n)) continue;
                seen.add(n.asLong());
                if (seen.size() >= POCKET) return false;
                open.add(n);
            }
        }
        return true;
    }

    /** Whether {@code e} is inside something solid or sealed in a pocket of ground. */
    public static boolean entombed(Entity e) {
        if (!(e.level() instanceof ServerLevel level) || e.isSpectator()) return false;
        return !level.noCollision(e) || sealedIn(level, e.blockPosition());
    }

    private static boolean passable(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && !s.getFluidState().is(FluidTags.LAVA);
    }

    /**
     * A place to stand on the surface near {@code near}, searched in rings from {@code minRadius} out to
     * {@code maxRadius}: on top of the ground (the heightmap, leaves aside), on a solid floor that isn't fluid, with room
     * for a player and nothing dangerous. Null if there is none.
     */
    @Nullable
    public static Vec3 standNear(ServerLevel level, BlockPos near, int minRadius, int maxRadius) {
        for (int r = minRadius; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Vec3 at = surfaceAt(level, near.getX() + dx, near.getZ() + dz);
                    if (at != null) return at;
                }
            }
        }
        return null;
    }

    /** The top of the ground in one column, if a player can stand there. */
    @Nullable
    static Vec3 surfaceAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinY() || y >= level.getMaxY() - 2) return null;
        BlockPos feet = new BlockPos(x, y, z), floor = feet.below();
        BlockState f = level.getBlockState(floor);
        if (f.getCollisionShape(level, floor).isEmpty() || !f.getFluidState().isEmpty() || f.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || f.is(net.minecraft.tags.BlockTags.FIRE) || f.is(net.minecraft.world.level.block.Blocks.CACTUS)) return null;
        for (int up = 0; up < 2; up++) {
            BlockPos q = feet.above(up);
            BlockState s = level.getBlockState(q);
            if (!s.getCollisionShape(level, q).isEmpty() || !s.getFluidState().isEmpty() || s.is(net.minecraft.tags.BlockTags.FIRE)) return null;
        }
        return Vec3.atBottomCenterOf(feet);
    }

    /** Somewhere to stand on the surface over {@code e}'s own column (or close by), or null if there is nowhere. */
    @Nullable
    public static Vec3 surfaceOver(Entity e) {
        return e.level() instanceof ServerLevel level ? standNear(level, e.blockPosition(), 0, 8) : null;
    }

    /** Lifts {@code e} out to the surface over where it is. True if it was moved. */
    public static boolean toSurface(Entity e) {
        Vec3 at = surfaceOver(e);
        if (at == null || !e.level().noCollision(e, e.getDimensions(Pose.STANDING).makeBoundingBox(at))) return false;
        put(e, at);
        return true;
    }

    /** Puts {@code e} down at {@code at} in its own level, safely (no fall damage, a moment of slow falling). */
    public static void put(Entity e, Vec3 at) {
        e.teleportTo(at.x, at.y, at.z);
        e.setDeltaMovement(Vec3.ZERO);
        e.resetFallDistance();
        if (e instanceof ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        }
    }
}
