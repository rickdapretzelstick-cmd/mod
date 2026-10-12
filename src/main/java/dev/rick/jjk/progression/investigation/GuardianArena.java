package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one arena every Grade 1 guardian is fought in: a round, flat floor about twenty-three blocks across (readable
 * footing, nothing to trip on or hide behind), ringed by a sheer wall too tall to climb, with no ledge anywhere to stand
 * on out of reach. Dim cracked stone, faint soul light set into the wall. The challenger arrives at one end facing the
 * guardian at the other. Blocks can't be placed inside it ({@link Investigations}): the fight is the fight.
 */
public final class GuardianArena implements CursedRealms.Layout {
    public static final String ID = "guardian_arena";
    static final int R = 11, WALL = 7;
    /** The floor's radius (what the guardian's phase beats change: progression.finish.GuardianPhases). */
    public static final int RADIUS = R;

    @Override
    public void build(ServerLevel l, BlockPos o, RandomSource r) {
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 1.5) continue;
                boolean wall = d > R + 0.3;
                BlockState floor = (x * 7 + z * 13) % 5 == 0 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                        : d < 3 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                set(l, o.offset(x, -1, z), wall ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : floor);
                set(l, o.offset(x, -2, z), Blocks.DEEPSLATE.defaultBlockState());
                if (wall) {
                    for (int y = 0; y < WALL; y++) {
                        boolean lamp = y == 3 && (x + z) % 4 == 0;
                        set(l, o.offset(x, y, z), lamp ? Blocks.SOUL_LANTERN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    }
                    // A lip leaning inward at the top: nothing to stand on, nothing to climb over.
                    set(l, o.offset(x, WALL, z), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        // A faint ring in the floor marking the middle.
        for (int a = 0; a < 24; a++) {
            double t = a * Math.PI * 2 / 24;
            set(l, o.offset((int) Math.round(Math.cos(t) * 5), -1, (int) Math.round(Math.sin(t) * 5)), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        }
    }

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    public Vec3 arrival() {
        return new Vec3(0, 0, -7);
    }

    @Override
    public float arrivalYaw() {
        return 0f;
    }

    @Override
    public List<Vec3> curseSpots() {
        return List.of(new Vec3(0, 0, 6));
    }

    @Override
    public int radius() {
        return R;
    }

    @Override
    public String entryLine() {
        return "It has been waiting for you.";
    }
}
