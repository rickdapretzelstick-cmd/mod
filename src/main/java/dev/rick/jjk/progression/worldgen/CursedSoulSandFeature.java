package dev.rick.jjk.progression.worldgen;

import com.mojang.serialization.Codec;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.ProgressionBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursed Soul Sand forms where the valley's fossils lie: directly under a bone block standing on soul sand or soul soil.
 * Runs once per Soul Sand Valley chunk in the last decoration step (after the Nether fossils are placed), so only
 * naturally generated bone blocks count. A few such spots per chunk turn, never whole areas; a chunk with any fossil
 * resting on the ground gets at least one, so a fossil is always worth looking under.
 */
public class CursedSoulSandFeature extends Feature<NoneFeatureConfiguration> {
    public CursedSoulSandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int minY = level.getMinY() + 1, maxY = Math.min(level.getMaxY(), level.getMinY() + 256);
        return decorate(level, context.random(), origin.getX() & ~15, origin.getZ() & ~15, minY, maxY) > 0;
    }

    /**
     * Turns soul sand/soil under bone blocks in the 16x16 columns from ({@code x0}, {@code z0}) between the given heights.
     * Returns how many blocks it turned.
     */
    public static int decorate(WorldGenLevel level, RandomSource random, int x0, int z0, int minY, int maxY) {
        var cfg = JJKConfig.get().progression;
        List<BlockPos> spots = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                BlockState below = level.getBlockState(p.set(x0 + dx, minY, z0 + dz));
                for (int y = minY + 1; y < maxY; y++) {
                    BlockState here = level.getBlockState(p.set(x0 + dx, y, z0 + dz));
                    if (here.is(Blocks.BONE_BLOCK) && (below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL))) {
                        spots.add(new BlockPos(x0 + dx, y - 1, z0 + dz));
                    }
                    below = here;
                }
            }
        }
        if (spots.isEmpty()) return 0;
        int cap = Math.max(1, cfg.cursedSoulSandPerChunk), placed = 0;
        // Shuffled so the cap doesn't always favour one corner of the chunk.
        for (int i = spots.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos t = spots.get(i);
            spots.set(i, spots.get(j));
            spots.set(j, t);
        }
        for (BlockPos s : spots) {
            if (placed >= cap) break;
            if (random.nextFloat() < cfg.cursedSoulSandChance) {
                level.setBlock(s, ProgressionBlocks.CURSED_SOUL_SAND.defaultBlockState(), 2);
                placed++;
            }
        }
        if (placed == 0) {
            level.setBlock(spots.get(0), ProgressionBlocks.CURSED_SOUL_SAND.defaultBlockState(), 2);
            placed = 1;
        }
        return placed;
    }
}
