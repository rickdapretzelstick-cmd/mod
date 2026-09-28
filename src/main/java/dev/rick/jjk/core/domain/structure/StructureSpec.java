package dev.rick.jjk.core.domain.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The physical shape of a domain: a sealed sphere around the center with a floor at the owner's feet.
 * <ul>
 *   <li>Shell: every block within {@code thickness} of the sphere's surface, above and below ground, so the volume is
 *       completely enclosed and nothing can walk or dig underneath.</li>
 *   <li>Floor: a flat disc one block below the center, the ground everyone stands on inside.</li>
 *   <li>Interior above the floor: optionally cleared to air, turning the battlefield into the domain's space.</li>
 *   <li>Everything below the floor inside the shell is left untouched (sealed off by the shell).</li>
 * </ul>
 * An optional {@link FloorPattern} swaps some floor blocks for others (markings, hatches) without changing the shape.
 */
public record StructureSpec(double radius, int thickness, BlockState shell, BlockState floor, boolean clearInterior,
                            @Nullable FloorPattern pattern) {

    /** Picks the floor block at a horizontal offset from the center. */
    @FunctionalInterface
    public interface FloorPattern {
        BlockState at(int dx, int dz, BlockState floor);
    }

    public StructureSpec(double radius, int thickness, BlockState shell, BlockState floor, boolean clearInterior) {
        this(radius, thickness, shell, floor, clearInterior, null);
    }

    /** What this structure wants at an offset from its center, or null for "leave the world alone". */
    @Nullable
    public BlockState targetAt(int dx, int dy, int dz) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > radius + 0.5) return null;
        if (d > radius + 0.5 - thickness) return shell;
        if (dy == -1) return pattern != null ? pattern.at(dx, dz, floor) : floor;
        if (dy > -1 && clearInterior) return Blocks.AIR.defaultBlockState();
        return null;
    }

    /** When the block at this offset is placed, as a fraction of the formation time (see {@link DomainFormation}). */
    public float buildTime(int dx, int dy, int dz) {
        return DomainFormation.time(dx, dy, dz, radius, thickness);
    }

    public boolean contains(BlockPos center, BlockPos p) {
        int dx = p.getX() - center.getX(), dy = p.getY() - center.getY(), dz = p.getZ() - center.getZ();
        return targetAt(dx, dy, dz) != null;
    }
}
