package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
            "cliff", new Kind(Sites::findCliff, Sites::buildCliff, "the cliffs"),
            "pasture", new Kind(Sites::findPasture, Sites::buildPasture, "the grazing land"),
            "hillside", new Kind(Sites::findHillside, Sites::buildMine, "the hillside"),
            "lodge", new Kind(LodgeSite::find, LodgeSite::build, "out in the woods"),
            "house", new Kind(Sites::findPasture, Sites::buildHouse, "the old house"),
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

    static void buildCliff(ServerLevel level, Incident in) {
        // Somebody left flowers and a candle where it happened. Nothing else.
        BlockPos back = in.site.offset(-in.dirX, 0, -in.dirZ);
        place(level, surface(level, back), Blocks.POPPY.defaultBlockState());
        place(level, surface(level, back.offset(in.dirZ, 0, -in.dirX)), Blocks.CANDLE.defaultBlockState());
        place(level, surface(level, back.offset(-in.dirZ, 0, in.dirX)), Blocks.OXEYE_DAISY.defaultBlockState());
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
            if (flat) return new Site(new BlockPos(p[0], h, p[1]), 1, 0);
        }
        return null;
    }

    static void buildPasture(ServerLevel level, Incident in) {
        // What's left: bones in the grass, a fence post knocked flat, churned earth.
        RandomSource r = RandomSource.create(in.id.hashCode());
        for (int i = 0; i < 4; i++) {
            BlockPos at = surface(level, in.site.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3));
            BlockState below = level.getBlockState(at.below());
            if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)) level.setBlock(at.below(), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            if (i < 2) place(level, at, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, i == 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z));
        }
        place(level, surface(level, in.site.offset(3, 0, -2)), Blocks.OAK_FENCE.defaultBlockState());
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

    /** The mine: a timbered mouth at the foot of the slope and a tunnel sloping down into the hill. */
    static void buildMine(ServerLevel level, Incident in) {
        int dx = in.dirX, dz = in.dirZ, sx = dz, sz = -dx;
        BlockPos mouth = in.site;
        for (int s = 0; s < 9; s++) {
            int drop = s / 2;
            BlockPos c = mouth.offset(dx * s, -drop, dz * s);
            for (int w = -1; w <= 1; w++) {
                for (int y = 0; y < 3; y++) level.setBlock(c.offset(sx * w, y, sz * w), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(c.offset(sx * w, -1, sz * w), (s + w) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
            }
            if (s % 3 == 0) {
                // Timber frame.
                for (int y = 0; y < 3; y++) {
                    level.setBlock(c.offset(sx * 2, y, sz * 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
                    level.setBlock(c.offset(-sx * 2, y, -sz * 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
                }
                for (int w = -2; w <= 2; w++) level.setBlock(c.offset(sx * w, 3, sz * w), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            } else {
                for (int w = -2; w <= 2; w += 4) for (int y = 0; y < 3; y++) {
                    BlockPos side = c.offset(sx * w, y, sz * w);
                    if (level.getBlockState(side).isAir()) level.setBlock(side, Blocks.STONE.defaultBlockState(), 2);
                }
                for (int w = -1; w <= 1; w++) {
                    BlockPos roof = c.offset(sx * w, 3, sz * w);
                    if (level.getBlockState(roof).isAir()) level.setBlock(roof, Blocks.STONE.defaultBlockState(), 2);
                }
            }
            if (s == 4 || s == 7) level.setBlock(c.offset(sx, 2, sz), Blocks.COBWEB.defaultBlockState(), 2);
            if (s >= 2 && s <= 7) level.setBlock(c, Blocks.RAIL.defaultBlockState(), 2);
        }
        // The far end is dark rock: the tunnel "goes on" only for whoever is meant to follow it.
        BlockPos end = mouth.offset(dx * 9, -4, dz * 9);
        for (int w = -1; w <= 1; w++) for (int y = 0; y < 3; y++) level.setBlock(end.offset(sx * w, y, sz * w), Blocks.DEEPSLATE.defaultBlockState(), 2);
        level.setBlock(mouth.offset(sx, 0, sz).offset(-dx, 0, -dz), Blocks.LANTERN.defaultBlockState(), 2);
    }

    // --- House: an abandoned cottage on flat ground, its bed still made ---

    /**
     * The empty house: a small cottage of old planks and mossy stone, a sagging roof with holes in it, its door hanging
     * open, cobwebs in the corners, and the bed nobody has slept in since. Marks its {@code door} (the threshold) and its
     * {@code bed} (where the incident is set off).
     */
    static void buildHouse(ServerLevel level, Incident in) {
        RandomSource r = RandomSource.create(in.id.hashCode());
        BlockPos base = surface(level, in.site);
        int hx = 3, hz = 3, h = 4;
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                boolean wall = Math.abs(x) == hx || Math.abs(z) == hz;
                level.setBlock(base.offset(x, -1, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
                for (int y = 0; y < h; y++) {
                    BlockState st = !wall ? Blocks.AIR.defaultBlockState()
                            : (Math.abs(x) == hx && Math.abs(z) == hz) ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                            : y == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : y == 1 && (x == 0 || z == 0) ? Blocks.AIR.defaultBlockState()
                            : r.nextInt(9) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
                    level.setBlock(base.offset(x, y, z), st, 2);
                }
                // The roof, with holes.
                if (r.nextInt(6) != 0) level.setBlock(base.offset(x, h, z), Blocks.DARK_OAK_SLAB.defaultBlockState(), 2);
            }
        }
        // The door, on the side facing out, hanging open; glass gone from the windows.
        BlockPos door = base.offset(in.dirX * hx, 0, in.dirZ * hx);
        level.setBlock(door, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(door.above(), Blocks.AIR.defaultBlockState(), 2);
        net.minecraft.core.Direction out = net.minecraft.core.Direction.getApproximateNearest(in.dirX, 0, in.dirZ);
        level.setBlock(door, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, out)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true), 2);
        level.setBlock(door.above(), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, out)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
        // The bed against the far wall, made; a table, a cold lantern, webs.
        BlockPos foot = base.offset(-in.dirX * (hx - 2), 0, -in.dirZ * (hx - 2));
        BlockPos head = foot.offset(-in.dirX, 0, -in.dirZ);
        net.minecraft.core.Direction facing = out.getOpposite();
        level.setBlock(head, Blocks.BED.white().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
        level.setBlock(foot, Blocks.BED.white().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
        BlockPos side = base.offset(in.dirZ * 2, 0, -in.dirX * 2);
        level.setBlock(side, Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(side.above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
        level.setBlock(base.offset(-in.dirZ * 2, 0, in.dirX * 2), Blocks.LANTERN.defaultBlockState(), 2);
        for (int i = 0; i < 4; i++) level.setBlock(base.offset(r.nextBoolean() ? 2 : -2, h - 1, r.nextBoolean() ? 2 : -2), Blocks.COBWEB.defaultBlockState(), 2);
        in.marks.put("door", door.immutable());
        in.marks.put("bed", foot.immutable());
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

    private static void place(ServerLevel level, BlockPos at, BlockState state) {
        if (level.getBlockState(at).canBeReplaced() && state.canSurvive(level, at)) level.setBlock(at, state, 2);
    }
}
