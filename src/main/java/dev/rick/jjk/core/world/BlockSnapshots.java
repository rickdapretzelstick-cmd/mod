package dev.rick.jjk.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for capturing and putting back exact block states with their block-entity data. */
public final class BlockSnapshots {
    /** Place exactly this state: no drops, no neighbour cascades, no onPlace logic, no container side effects. */
    public static final int EXACT = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    private BlockSnapshots() {}

    @Nullable
    public static CompoundTag blockEntity(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.hasBlockEntity()) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.saveWithFullMetadata(level.registryAccess());
    }

    /** Puts {@code state} (and its block-entity data) back exactly. */
    public static void place(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag beTag) {
        level.setBlock(pos, state, EXACT);
        if (beTag != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), beTag));
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
    }

    /**
     * Hands a block that will not be restored back to the world as the drops it would have given (and its container
     * contents), so nothing whose drops were held back is ever lost.
     */
    public static void dropInstead(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag beTag) {
        BlockEntity be = beTag == null ? null : BlockEntity.loadStatic(pos, state, beTag, level.registryAccess());
        if (be != null) be.setLevel(level);
        Block.dropResources(state, level, pos, be);
        if (be instanceof Container c) Containers.dropContents(level, pos, c);
    }

    /**
     * Blocks that hang off a neighbour (torches, flowers, rails, doors, buttons, carpets...) — restored after the solid
     * blocks around them.
     */
    public static boolean dependsOnSupport(BlockState state) {
        return !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Leftovers the damage itself produced: air, fire, and fluid that flowed in. These never block a restore. */
    public static boolean isDebris(BlockState state) {
        return state.isAir() || state.is(net.minecraft.tags.BlockTags.FIRE) || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
    }

    /** Anyone standing where a block came back is lifted to the nearest free space above. */
    public static void liftToFreeSpace(Entity e) {
        for (int up = 1; up <= 64; up++) {
            AABB moved = e.getBoundingBox().move(0, up, 0);
            if (e.level().noCollision(e, moved)) {
                e.teleportTo(e.getX(), e.getY() + up, e.getZ());
                e.resetFallDistance();
                return;
            }
        }
    }

    public static final Direction[] DIRECTIONS = Direction.values();
}
