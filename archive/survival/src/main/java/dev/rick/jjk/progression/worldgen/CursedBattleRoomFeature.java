package dev.rick.jjk.progression.worldgen;

import com.mojang.serialization.MapCodec;
import dev.rick.jjk.JJK;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.block.CursedSealBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Cursed battle rooms. In the chunk where a Woodland Mansion or an Igloo starts (after the structure has been placed),
 * this digs a sealed chamber well below it and joins it to the building's lowest floor by a hidden way down: a
 * trapdoor set flush into the floor under a carpet, and a ladder shaft. The chamber is built to feel wrong (deepslate and
 * blackstone, a ritual ring of crying obsidian round the seal, hanging soul lanterns, black candles, bones, sculk, webs)
 * with accents from the building above (dark oak under a mansion, packed ice under an igloo). The {@link CursedSealBlock}
 * at its heart is the encounter marker: it registers the room when it first ticks in the world.
 *
 * <p>The vanilla buildings are left as they are apart from that one floor block (an igloo's own basement and trapdoor
 * keep working; under an igloo with a basement the way down starts from the basement floor). Everything is written
 * inside the start chunk, so generation never reaches into chunks that aren't ready.
 */
public record CursedBattleRoomFeature() implements Feature {
    public static final MapCodec<CursedBattleRoomFeature> CODEC = MapCodec.unit(new CursedBattleRoomFeature());

    /** Interior half-width (the room is 13x13 inside, centred on the chunk) and height. */
    private static final int HALF = 6, HEIGHT = 7;

    @Override
    public MapCodec<CursedBattleRoomFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        ChunkAccess chunk = level.getChunk(origin);
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return false;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var cfg = JJKConfig.get().progression;
        boolean placed = false;
        for (Map.Entry<Structure, StructureStart> e : starts.entrySet()) {
            StructureStart start = e.getValue();
            if (start == null || !start.isValid()) continue;
            Identifier id = registry.getKey(e.getKey());
            if (id == null || !id.getNamespace().equals("minecraft")) continue;
            int site;
            if (id.getPath().equals("mansion") && cfg.mansionBattleRooms) site = 0;
            else if (id.getPath().equals("igloo") && cfg.iglooBattleRooms) site = 1;
            else continue;
            placed |= build(level, random, origin.getX() & ~15, origin.getZ() & ~15, start.getBoundingBox(), site);
        }
        return placed;
    }

    private boolean build(WorldGenLevel level, RandomSource random, int x0, int z0, BoundingBox box, int site) {
        int cx = x0 + 8, cz = z0 + 8;
        BlockPos entry = findEntry(level, box, cx, cz);
        if (entry == null) {
            JJK.LOGGER.debug("No floor inside the {} at chunk {},{} for a battle room entrance", site == 0 ? "mansion" : "igloo", x0 >> 4, z0 >> 4);
            return false;
        }
        int ceiling = Math.min(box.minY(), entry.getY()) - 4;
        int floor = ceiling - HEIGHT - 1;
        if (floor < level.getMinY() + 3) return false;
        Palette pal = site == 0 ? Palette.MANSION : Palette.IGLOO;
        shell(level, random, cx, cz, floor, ceiling, pal);
        furnish(level, random, cx, cz, floor, ceiling, pal);
        // The encounter marker: it registers the room with CursedEncounters the first time it ticks in the world.
        BlockPos seal = new BlockPos(cx, floor, cz);
        level.setBlock(seal, ProgressionBlocks.CURSED_SEAL.defaultBlockState().setValue(CursedSealBlock.SITE, site), 2);
        level.scheduleTick(seal, ProgressionBlocks.CURSED_SEAL, CursedSealBlock.CHECK_TICKS);
        shaft(level, entry, floor, ceiling, pal);
        return true;
    }

    /**
     * The building's lowest roofed floor inside this chunk, on a column the room can take a ladder down from; a spot
     * against a wall or in a corner is preferred (harder to notice). Null if the building has no such floor here.
     */
    @Nullable
    private static BlockPos findEntry(WorldGenLevel level, BoundingBox box, int cx, int cz) {
        BlockPos best = null;
        int bestScore = -1, bestY = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = cx - HALF + 1; x <= cx + HALF - 1; x++) {
            for (int z = cz - HALF + 1; z <= cz + HALF - 2; z++) {
                if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) continue;
                // Not down into the ritual ring or through a pillar.
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz < 16 || (Math.abs(dx) == 4 && Math.abs(dz) == 4)) continue;
                for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 24); y++) {
                    BlockState f = level.getBlockState(p.set(x, y, z));
                    if (!f.isCollisionShapeFullBlock(level, p) || natural(f)) continue;
                    if (!level.getBlockState(p.set(x, y + 1, z)).isAir() || !level.getBlockState(p.set(x, y + 2, z)).isAir()) continue;
                    if (!roofed(level, x, y + 3, z)) continue;
                    // The lowest floor wins; on it, the most enclosed spot.
                    int score = 0;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (!level.getBlockState(p.set(x + d.getStepX(), y + 1, z + d.getStepZ())).isAir()) score++;
                    }
                    if (y < bestY || (y == bestY && score > bestScore)) {
                        best = new BlockPos(x, y, z);
                        bestY = y;
                        bestScore = score;
                    }
                    break;
                }
            }
        }
        return best;
    }

    /** Ground rather than a building's floor (a cave pocket inside the bounding box isn't a way in). */
    private static boolean natural(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.TUFF) || s.is(Blocks.BEDROCK)
                || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.SAND) || s.is(Blocks.CLAY);
    }

    private static boolean roofed(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < 10; dy++) if (!level.getBlockState(p.set(x, y + dy, z)).isAir()) return true;
        return false;
    }

    private enum Palette {
        MANSION(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_TRAPDOOR, Blocks.CARPET.gray(), Blocks.STRIPPED_DARK_OAK_LOG),
        IGLOO(Blocks.PACKED_ICE, Blocks.SPRUCE_TRAPDOOR, Blocks.CARPET.white(), Blocks.BLUE_ICE);

        final Block pillar, trapdoor, carpet, accent;

        Palette(Block pillar, Block trapdoor, Block carpet, Block accent) {
            this.pillar = pillar;
            this.trapdoor = trapdoor;
            this.carpet = carpet;
            this.accent = accent;
        }
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, 2);
    }

    /** Solid shell (floor, walls, ceiling) round an empty interior, so the room stays sealed whatever it was dug into. */
    private static void shell(WorldGenLevel level, RandomSource random, int cx, int cz, int floor, int ceiling, Palette pal) {
        for (int x = cx - HALF - 1; x <= cx + HALF + 1; x++) {
            for (int z = cz - HALF - 1; z <= cz + HALF + 1; z++) {
                boolean edge = Math.abs(x - cx) == HALF + 1 || Math.abs(z - cz) == HALF + 1;
                for (int y = floor - 1; y <= ceiling; y++) {
                    BlockState s;
                    if (y == floor - 1) s = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                    else if (y == floor) s = floorBlock(random, x - cx, z - cz);
                    else if (y == ceiling) s = random.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                    else if (edge) s = wallBlock(random, y - floor);
                    else s = Blocks.AIR.defaultBlockState();
                    set(level, x, y, z, s);
                }
            }
        }
    }

    private static BlockState floorBlock(RandomSource random, int dx, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        // The ritual ring round the seal: blackstone, with crying obsidian at its four points.
        if (r >= 2.5 && r < 3.5) {
            boolean point = (dx == 0 || dz == 0) && Math.abs(dx + dz) == 3;
            return point ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        }
        if (r < 2.5) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        int n = random.nextInt(12);
        if (n == 0) return Blocks.SCULK.defaultBlockState();
        if (n == 1) return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    private static BlockState wallBlock(RandomSource random, int height) {
        if (height == 1) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        int n = random.nextInt(10);
        if (n == 0) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (n == 1) return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        if (height == 4 && n < 4) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    private static void furnish(WorldGenLevel level, RandomSource random, int cx, int cz, int floor, int ceiling, Palette pal) {
        // Four pillars round the ring, in the building's material, with a soul lantern hanging between each pair.
        int[][] corners = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] c : corners) {
            for (int y = floor + 1; y < ceiling; y++) {
                set(level, cx + c[0], y, cz + c[1], (y == floor + 1 || y == ceiling - 1) ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : pal.pillar.defaultBlockState());
            }
        }
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        int[][] lanterns = {{0, -4}, {0, 4}, {-4, 0}, {4, 0}};
        for (int[] l : lanterns) set(level, cx + l[0], ceiling - 1, cz + l[1], lantern);
        // Black candles burning along the walls; bones and skulls left by whatever lived here.
        for (int i = 0; i < 10; i++) {
            int side = random.nextInt(4);
            int along = random.nextInt(2 * HALF + 1) - HALF;
            int x = side < 2 ? cx + along : cx + (side == 2 ? -HALF : HALF);
            int z = side < 2 ? cz + (side == 0 ? -HALF : HALF) : cz + along;
            BlockState s;
            int n = random.nextInt(4);
            if (n == 0) s = Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16));
            else if (n == 1) s = Blocks.BONE_BLOCK.defaultBlockState();
            else s = Blocks.DYED_CANDLE.black().defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(4)).setValue(CandleBlock.LIT, true);
            set(level, x, floor + 1, z, s);
        }
        // Accents from the building above, set into the walls at eye height.
        for (int i = -HALF + 2; i <= HALF - 2; i += 4) {
            set(level, cx + i, floor + 3, cz - HALF - 1, pal.accent.defaultBlockState());
            set(level, cx + i, floor + 3, cz + HALF + 1, pal.accent.defaultBlockState());
            set(level, cx - HALF - 1, floor + 3, cz + i, pal.accent.defaultBlockState());
            set(level, cx + HALF + 1, floor + 3, cz + i, pal.accent.defaultBlockState());
        }
        // Webs in the upper corners.
        int[][] webs = {{-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}};
        for (int[] w : webs) {
            set(level, cx + w[0], ceiling - 1, cz + w[1], Blocks.COBWEB.defaultBlockState());
            if (random.nextBoolean()) set(level, cx + w[0], ceiling - 2, cz + w[1], Blocks.COBWEB.defaultBlockState());
        }
    }

    /**
     * The hidden way down: the floor block becomes a closed trapdoor flush with the floor, under a carpet; below it a
     * ladder runs to the chamber's floor, against a wall the shaft is lined with (and a deepslate pillar inside the room).
     */
    private static void shaft(WorldGenLevel level, BlockPos entry, int floor, int ceiling, Palette pal) {
        int x = entry.getX(), z = entry.getZ();
        set(level, x, entry.getY(), z, pal.trapdoor.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.NORTH));
        set(level, x, entry.getY() + 1, z, pal.carpet.defaultBlockState());
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = entry.getY() - 1; y > floor; y--) {
            boolean inRoom = y < ceiling;
            // Line the shaft above the room; inside the room the ladder runs down a pillar.
            if (!inRoom) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockState s = level.getBlockState(p.set(x + d.getStepX(), y, z + d.getStepZ()));
                    if (!s.isCollisionShapeFullBlock(level, p)) set(level, x + d.getStepX(), y, z + d.getStepZ(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                }
            } else {
                set(level, x, y, z + 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
            set(level, x, y, z, ladder);
        }
    }
}
