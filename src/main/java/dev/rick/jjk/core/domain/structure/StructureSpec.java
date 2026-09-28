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
 */
public record StructureSpec(double radius, int thickness, BlockState shell, BlockState floor, boolean clearInterior) {

    /** What this structure wants at an offset from its center, or null for "leave the world alone". */
    @Nullable
    public BlockState targetAt(int dx, int dy, int dz) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > radius + 0.5) return null;
        if (d > radius + 0.5 - thickness) return shell;
        if (dy == -1) return floor;
        if (dy > -1 && clearInterior) return Blocks.AIR.defaultBlockState();
        return null;
    }

    public boolean contains(BlockPos center, BlockPos p) {
        int dx = p.getX() - center.getX(), dy = p.getY() - center.getY(), dz = p.getZ() - center.getZ();
        return targetAt(dx, dy, dz) != null;
    }
}
