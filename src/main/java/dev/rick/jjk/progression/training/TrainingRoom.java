package dev.rick.jjk.progression.training;

import dev.rick.jjk.progression.investigation.CursedRealms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one training room every character's training is held in: a square of dark stone floating in the realm, four
 * pillars at its corners, the participant at its centre facing south. Each character's scene dresses it
 * ({@link TrainingScene#build}); the realm clears it all when the session ends.
 */
public final class TrainingRoom implements CursedRealms.Layout {
    public static final String ID = "training_room";
    public static final int HALF = 7;

    @Override
    public void build(ServerLevel l, BlockPos o, RandomSource r) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), edge = Blocks.DEEPSLATE_TILES.defaultBlockState();
        for (int x = -HALF - 5; x <= HALF + 5; x++) {
            for (int z = -HALF - 1; z <= HALF + 7; z++) {
                boolean rim = Math.abs(x) == HALF + 5 || z == -HALF - 1 || z == HALF + 7;
                set(l, o.offset(x, -1, z), rim ? edge : floor);
                set(l, o.offset(x, -2, z), Blocks.DEEPSLATE.defaultBlockState());
            }
        }
        for (int sx : new int[] {-HALF - 5, HALF + 5}) {
            for (int sz : new int[] {-HALF - 1, HALF + 7}) {
                for (int y = 0; y < 5; y++) set(l, o.offset(sx, y, sz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                set(l, o.offset(sx, 5, sz), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
    }

    static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    public Vec3 arrival() {
        return Vec3.ZERO;
    }

    @Override
    public List<Vec3> curseSpots() {
        return List.of();
    }

    @Override
    public float arrivalYaw() {
        return 0f;
    }

    @Override
    public int radius() {
        return 12;
    }

    @Override
    public String entryLine() {
        return "A quiet room, made for you alone.";
    }
}
