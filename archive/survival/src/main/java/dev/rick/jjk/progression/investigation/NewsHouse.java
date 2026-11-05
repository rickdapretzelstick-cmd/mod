package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.JJK;
import dev.rick.jjk.progression.ProgressionBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The village's news house: a small building by the bell where the news board hangs, built in the village's own style
 * (plains, desert, savanna, taiga or snowy). Inside: the board on the back wall facing the door, a lectern where someone
 * keeps the record (a librarian's work site, so villagers come and use the place), a desk strewn with papers, a barrel
 * of old notices, a bench, a hanging lantern. It is part of the village, not a quest giver.
 */
public final class NewsHouse {
    /** A village style: what the house is made of. */
    public record Palette(String name, BlockState wall, BlockState frame, BlockState floor, BlockState foundation, BlockState roof,
                          BlockState roofTop, BlockState door, BlockState window, BlockState accent) {}

    public static final Palette PLAINS = new Palette("plains", Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState());
    public static final Palette DESERT = new Palette("desert", Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE_STAIRS.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(), Blocks.JUNGLE_DOOR.defaultBlockState(), Blocks.STAINED_GLASS_PANE.brown().defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState());
    public static final Palette SAVANNA = new Palette("savanna", Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.ACACIA_LOG.defaultBlockState(),
            Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.ACACIA_STAIRS.defaultBlockState(),
            Blocks.ACACIA_SLAB.defaultBlockState(), Blocks.ACACIA_DOOR.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(),
            Blocks.DYED_TERRACOTTA.orange().defaultBlockState());
    public static final Palette TAIGA = new Palette("taiga", Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.SPRUCE_STAIRS.defaultBlockState(),
            Blocks.SPRUCE_SLAB.defaultBlockState(), Blocks.SPRUCE_DOOR.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState());
    public static final Palette SNOWY = new Palette("snowy", Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.SPRUCE_STAIRS.defaultBlockState(),
            Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SPRUCE_DOOR.defaultBlockState(), Blocks.STAINED_GLASS_PANE.lightBlue().defaultBlockState(),
            Blocks.PACKED_ICE.defaultBlockState());
    public static final Palette[] ALL = {PLAINS, DESERT, SAVANNA, TAIGA, SNOWY};

    /** Half-width (x) and depth (z) of the footprint: 7 wide, 6 deep. */
    static final int HX = 3, DEPTH = 6, WALL = 4;

    private NewsHouse() {}

    /** The style for a village's biome. */
    public static Palette paletteFor(ServerLevel level, BlockPos at) {
        Holder<Biome> b = level.getBiome(at);
        if (b.is(BiomeTags.HAS_VILLAGE_DESERT)) return DESERT;
        if (b.is(BiomeTags.HAS_VILLAGE_SAVANNA)) return SAVANNA;
        if (b.is(BiomeTags.HAS_VILLAGE_SNOWY)) return SNOWY;
        if (b.is(BiomeTags.HAS_VILLAGE_TAIGA)) return TAIGA;
        return PLAINS;
    }

    /**
     * Looks for flat, open ground near the bell and builds the house there, door toward the bell. Returns the board's
     * position, or null if there was no room (the caller falls back to a board in the open).
     */
    @Nullable
    public static BlockPos placeNear(ServerLevel level, BlockPos bell) {
        for (int r = 7; r <= 15; r += 2) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int side = -4; side <= 4; side += 4) {
                    // The door's spot: r out from the bell; the house extends further away from it.
                    BlockPos door = bell.relative(d, r).relative(d.getClockWise(), side);
                    if (!level.isLoaded(door)) continue;
                    BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door).below();
                    // The door faces back toward the bell; the house extends away from it.
                    Direction out = d.getOpposite();
                    if (fits(level, ground, out)) return build(level, ground, out, paletteFor(level, ground), RandomSource.create(bell.asLong()));
                }
            }
        }
        return null;
    }

    /** Flat (within one block), dry, and only grass, flowers or air where the house would stand. */
    public static boolean fits(ServerLevel level, BlockPos ground, Direction out) {
        if (!level.isLoaded(ground)) return false;
        int y0 = ground.getY();
        for (int x = -HX - 1; x <= HX + 1; x++) {
            for (int z = -1; z <= DEPTH; z++) {
                BlockPos col = at(ground, out, x, 0, z);
                if (!level.isLoaded(col)) return false;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ()) - 1;
                // Level ground (a block lower is filled in under the floor), never higher than the doorstep.
                if (h > y0 || h < y0 - 1) return false;
                BlockPos topPos = new BlockPos(col.getX(), h, col.getZ());
                BlockState top = level.getBlockState(topPos);
                if (!top.getFluidState().isEmpty() || top.is(Blocks.DIRT_PATH) || top.is(Blocks.FARMLAND)) return false;
                // Natural ground only: a full block (not a fence, a wall, a path, a bed...).
                if (!top.isCollisionShapeFullBlock(level, topPos)) return false;
                for (int y = 1; y <= WALL + 3; y++) {
                    BlockState s = level.getBlockState(new BlockPos(col.getX(), y0 + y, col.getZ()));
                    if (!s.isAir() && !s.canBeReplaced()) return false;
                }
            }
        }
        return true;
    }

    /**
     * A world position from house coordinates: x across (right as you face in), z in from the door, y up from the floor.
     * {@code out} is the way the door faces (toward the bell); the house extends the other way.
     */
    static BlockPos at(BlockPos ground, Direction out, int x, int y, int z) {
        Direction in = out.getOpposite(), right = in.getClockWise();
        return ground.relative(in, z).relative(right, x).above(y);
    }

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_ALL);
    }

    /** Builds the house and returns its board's position. */
    public static BlockPos build(ServerLevel l, BlockPos ground, Direction out, Palette pal, RandomSource r) {
        Direction in = out.getOpposite(), right = in.getClockWise(), left = right.getOpposite();
        // Foundation and floor (filling down to solid ground), then clear the inside.
        for (int x = -HX; x <= HX; x++) {
            for (int z = 0; z < DEPTH; z++) {
                set(l, at(ground, out, x, 0, z), (x == -HX || x == HX || z == 0 || z == DEPTH - 1) ? pal.foundation() : pal.floor());
                for (int y = -1; y >= -4; y--) {
                    BlockPos p = at(ground, out, x, y, z);
                    if (!l.getBlockState(p).isAir() && l.getBlockState(p).getFluidState().isEmpty() && !l.getBlockState(p).canBeReplaced()) break;
                    set(l, p, pal.foundation());
                }
                for (int y = 1; y <= WALL + 3; y++) set(l, at(ground, out, x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        // Walls with log corners, windows on the sides, the doorway in front.
        for (int y = 1; y <= WALL; y++) {
            for (int x = -HX; x <= HX; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    boolean edgeX = x == -HX || x == HX, edgeZ = z == 0 || z == DEPTH - 1;
                    if (!edgeX && !edgeZ) continue;
                    BlockState s = edgeX && edgeZ ? pal.frame() : pal.wall();
                    if (y == 2 && edgeX && !edgeZ && (z == 2 || z == 3)) s = pal.window();
                    if (y == 2 && edgeZ && z == DEPTH - 1 && (x == -2 || x == 2)) s = pal.window();
                    set(l, at(ground, out, x, y, z), s);
                }
            }
        }
        // Panes connect themselves once their neighbours are in: refresh their shape.
        for (int y = 2; y <= 2; y++) for (int x = -HX; x <= HX; x++) for (int z = 0; z < DEPTH; z++) {
            BlockPos p = at(ground, out, x, y, z);
            BlockState s = l.getBlockState(p);
            if (s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) l.setBlock(p, Block.updateFromNeighbourShapes(s, l, p), Block.UPDATE_ALL);
        }
        BlockPos door = at(ground, out, 0, 1, 0);
        set(l, door, pal.door().setValue(DoorBlock.FACING, out).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(l, door.above(), pal.door().setValue(DoorBlock.FACING, out).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // A gabled roof: stairs climbing from both eaves (overhanging the walls by one), a ridge of slabs; the gable ends
        // walled up under it, front and back.
        for (int z = -1; z <= DEPTH; z++) {
            for (int step = 0; step <= HX; step++) {
                int y = WALL + step;
                set(l, at(ground, out, -HX - 1 + step, y, z), pal.roof().setValue(StairBlock.FACING, right).setValue(StairBlock.HALF, Half.BOTTOM));
                set(l, at(ground, out, HX + 1 - step, y, z), pal.roof().setValue(StairBlock.FACING, left).setValue(StairBlock.HALF, Half.BOTTOM));
            }
            set(l, at(ground, out, 0, WALL + HX, z), pal.roofTop());
            if (z == 0 || z == DEPTH - 1) {
                for (int x = -HX + 1; x <= HX - 1; x++) {
                    int top = WALL + HX - Math.abs(x) - (x == 0 ? 1 : 0);
                    for (int y = WALL + 1; y <= top; y++) set(l, at(ground, out, x, y, z), pal.wall());
                }
            }
        }
        // Ceiling beams across, for the lantern to hang from.
        for (int x = -HX + 1; x <= HX - 1; x++) set(l, at(ground, out, x, WALL + 1, DEPTH / 2), pal.frame()
                .trySetValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, right.getAxis()));
        set(l, at(ground, out, 0, WALL, DEPTH / 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // The board on the back wall, facing the door.
        BlockPos board = at(ground, out, 0, 1, DEPTH - 2);
        set(l, board, ProgressionBlocks.NEWS_BOARD.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, out));
        // The record keeper's lectern, turned to the room; a desk of papers; a barrel of old notices; a bench by the door.
        set(l, at(ground, out, 2, 1, DEPTH - 3), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, left));
        set(l, at(ground, out, -2, 1, DEPTH - 2), Blocks.BARREL.defaultBlockState());
        set(l, at(ground, out, -2, 2, DEPTH - 2), Blocks.BOOKSHELF.defaultBlockState());
        set(l, at(ground, out, -2, 1, 2), pal.roof().setValue(StairBlock.FACING, left));
        set(l, at(ground, out, -2, 1, 3), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, at(ground, out, -2, 2, 3), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
        set(l, at(ground, out, -1, 1, 3), Blocks.CARPET.white().defaultBlockState());
        set(l, at(ground, out, 2, 1, 1), pal.roof().setValue(StairBlock.FACING, right));
        set(l, at(ground, out, 1, 1, DEPTH - 2), Blocks.CARPET.white().defaultBlockState());
        set(l, at(ground, out, 2, 2, DEPTH - 1), Blocks.FLOWER_POT.defaultBlockState());
        // Outside: a step, a lantern on a post by the door, a little of the village's own material.
        set(l, at(ground, out, 2, 1, -1), pal.frame().trySetValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y));
        set(l, at(ground, out, 2, 2, -1), Blocks.LANTERN.defaultBlockState());
        set(l, at(ground, out, -2, 1, -1), pal.accent());
        if (pal == SNOWY) for (int x = -HX - 1; x <= HX + 1; x++) {
            BlockPos ridge = at(ground, out, x, WALL + HX + 2, DEPTH / 2);
            if (l.getBlockState(ridge).isAir() && !l.getBlockState(ridge.below()).isAir()) set(l, ridge, Blocks.SNOW.defaultBlockState());
        }
        JJK.LOGGER.info("[investigations] {} news house built at {} (board {})", pal.name(), ground.toShortString(), board.toShortString());
        return board.immutable();
    }
}
