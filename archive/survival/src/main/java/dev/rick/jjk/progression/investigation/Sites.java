package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Where incidents can happen. Each kind of place is a {@link Finder} (searching the terrain around a village, loaded or
 * not: unloaded ground is read from the world generator) and a {@link Builder} that leaves its physical traces once the
 * place is loaded and someone is near: flowers left at a cliff edge, bones in a pasture, a boarded mine entrance cut into
 * a hillside. Nothing glows and nothing is labelled: the report and the residue are how a player finds it.
 */
public final class Sites {
    /** A found place: the spot that matters and the direction that matters there (a cliff's drop, a tunnel's run). */
    public record Site(BlockPos pos, int dirX, int dirZ) {}

    @FunctionalInterface
    public interface Finder {
        @Nullable
        Site find(ServerLevel level, BlockPos village, RandomSource random, int min, int max);
    }

    @FunctionalInterface
    public interface Builder {
        void build(ServerLevel level, Incident incident);
    }

    private record Kind(Finder finder, Builder builder, String landmark) {}

    /** How a village describes where a kind of place is ({@code {landmark}} in a report): the lodge has its own words. */
    @FunctionalInterface
    public interface Describer {
        String describe(ServerLevel level, BlockPos village, BlockPos site);
    }

    private static final Map<String, Describer> DESCRIBE = Map.of("lodge", LodgeSite::describe);

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final Map<String, Kind> KINDS = Map.of(
            "cliff", new Kind(Sites::findCliff, CountrySites::buildCliff, "the cliffs"),
            "pasture", new Kind(Sites::findPasture, CountrySites::buildPasture, "the grazing land"),
            "hillside", new Kind(Sites::findHillside, CountrySites::buildMine, "the hillside"),
            "lodge", new Kind(LodgeSite::find, LodgeSite::build, "out in the woods"),
            "house", new Kind(Sites::findPasture, CountrySites::buildHouse, "the old house"),
            // The character storylines' places (StorySites).
            "tower", new Kind(Sites::findPasture, StorySites::buildTower, "the old watchtower"),
            "theater", new Kind(Sites::findPasture, StorySites::buildTheater, "the old theater"),
            "storehouse", new Kind(Sites::findPasture, StorySites::buildStorehouse, "the abandoned storehouse"),
            "crater", new Kind(Sites::findPasture, StorySites::buildCrater, "the hills"),
            "chapel", new Kind(Sites::findPasture, StorySites::buildChapel, "the old chapel"));

    private Sites() {}

    public static boolean exists(String kind) {
        return KINDS.containsKey(kind);
    }

    @Nullable
    public static Site find(String kind, ServerLevel level, BlockPos village, RandomSource random, int min, int max) {
        Kind k = KINDS.get(kind);
        return k == null ? null : k.finder.find(level, village, random, min, max);
    }

    public static void build(String kind, ServerLevel level, Incident incident) {
        Kind k = KINDS.get(kind);
        if (k != null) k.builder.build(level, incident);
    }

    /** The village's words for where the place is (a ridge crossed, the kind of woods...), or the kind's plain landmark. */
    public static String describe(String kind, ServerLevel level, BlockPos village, BlockPos site) {
        Describer d = DESCRIBE.get(kind);
        return d != null ? d.describe(level, village, site) : landmark(kind);
    }

    public static String landmark(String kind) {
        Kind k = KINDS.get(kind);
        return k == null ? "the outskirts" : k.landmark;
    }

    // --- Terrain ---

    /** The surface height at a column: the real one where it is loaded, the generator's otherwise. */
    static int height(ServerLevel level, int x, int z) {
        if (level.hasChunk(x >> 4, z >> 4)) return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
    }

    private static boolean dryLand(ServerLevel level, int x, int y, int z) {
        if (y <= level.getSeaLevel() + 1) return false;
        Holder<Biome> b = level.getBiome(new BlockPos(x, y, z));
        return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_RIVER) && !b.is(BiomeTags.IS_BEACH);
    }

    private static int[] ring(BlockPos village, RandomSource r, int min, int max) {
        double a = r.nextDouble() * Mth.TWO_PI;
        double d = min + r.nextDouble() * Math.max(1, max - min);
        return new int[] {village.getX() + (int) Math.round(Math.cos(a) * d), village.getZ() + (int) Math.round(Math.sin(a) * d)};
    }

    // --- Cliff: a drop of at least ten blocks within three ---

    @Nullable
    static Site findCliff(ServerLevel level, BlockPos village, RandomSource r, int min, int max) {
        for (int attempt = 0; attempt < 60; attempt++) {
            int[] p = ring(village, r, min, max);
            int h = height(level, p[0], p[1]);
            if (!dryLand(level, p[0], h, p[1])) continue;
            for (int[] d : DIRS) {
                int below = height(level, p[0] + d[0] * 3, p[1] + d[1] * 3);
                if (h - below < 10) continue;
                // Walk to the lip: the last column still at the top.
                int ex = p[0], ez = p[1];
                for (int s = 1; s <= 3; s++) {
                    if (height(level, p[0] + d[0] * s, p[1] + d[1] * s) < h - 1) break;
                    ex = p[0] + d[0] * s;
                    ez = p[1] + d[1] * s;
                }
                return new Site(new BlockPos(ex, height(level, ex, ez), ez), d[0], d[1]);
            }
        }
        return null;
    }

    // --- Pasture: flat dry grassland ---

    @Nullable
    static Site findPasture(ServerLevel level, BlockPos village, RandomSource r, int min, int max) {
        for (int attempt = 0; attempt < 60; attempt++) {
            int[] p = ring(village, r, min, max);
            int h = height(level, p[0], p[1]);
            if (!dryLand(level, p[0], h, p[1])) continue;
            Holder<Biome> b = level.getBiome(new BlockPos(p[0], h, p[1]));
            if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_BADLANDS)) continue;
            boolean flat = true;
            for (int[] d : DIRS) if (Math.abs(height(level, p[0] + d[0] * 4, p[1] + d[1] * 4) - h) > 1) flat = false;
            if (!flat) continue;
            // It faces the village: the way in is the way people come from.
            int dx = village.getX() - p[0], dz = village.getZ() - p[1];
            return Math.abs(dx) >= Math.abs(dz) ? new Site(new BlockPos(p[0], h, p[1]), dx >= 0 ? 1 : -1, 0)
                    : new Site(new BlockPos(p[0], h, p[1]), 0, dz >= 0 ? 1 : -1);
        }
        return null;
    }

    // --- Hillside: ground rising into a slope (a mine entrance faces downhill) ---

    @Nullable
    static Site findHillside(ServerLevel level, BlockPos village, RandomSource r, int min, int max) {
        for (int attempt = 0; attempt < 60; attempt++) {
            int[] p = ring(village, r, min, max);
            int h = height(level, p[0], p[1]);
            if (!dryLand(level, p[0], h, p[1])) continue;
            for (int[] d : DIRS) {
                if (d[0] != 0 && d[1] != 0) continue;
                int up = height(level, p[0] + d[0] * 6, p[1] + d[1] * 6) - h;
                int behind = height(level, p[0] - d[0] * 3, p[1] - d[1] * 3) - h;
                if (up >= 5 && up <= 14 && Math.abs(behind) <= 2) return new Site(new BlockPos(p[0], h, p[1]), d[0], d[1]);
            }
        }
        return null;
    }

    /** The deepest point of the mine tunnel (where its trigger waits). */
    static BlockPos mineEnd(Incident in) {
        return in.site.offset(in.dirX * 8, -4, in.dirZ * 8);
    }

    // --- Helpers ---

    /** Tests: where a site's builder stands it (the surface at that column). */
    public static BlockPos surfaceForTest(ServerLevel level, BlockPos p) {
        return surface(level, p);
    }

    static BlockPos surface(ServerLevel level, BlockPos p) {
        return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
    }
}
