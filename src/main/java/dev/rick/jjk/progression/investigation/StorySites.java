package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The places the character storylines happen ({@link Sites} kinds): Gojo's watchtower, Yuji's abandoned theater, Hakari's
 * storehouse, Ryu's crater and Yuta's chapel. Each is built once its ground is loaded and someone is near, like every
 * other site, and marks what its trigger needs: the {@code door} (a threshold) and the {@code object} (the thing used:
 * the watchtower's lamp, the theater's projector, the storehouse's bell, the crater's black shard, the chapel's altar).
 * All of them stand on flat dry ground found by {@link Sites#findPasture}; they face the way the site does.
 */
final class StorySites {
    private StorySites() {}

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, 2);
    }

    private static Direction out(Incident in) {
        return Direction.getApproximateNearest(in.dirX, 0, in.dirZ);
    }

    /** A box of {@code wall} from the floor up, hollow inside, with a floor of {@code floor}. */
    private static void shell(ServerLevel l, BlockPos base, int hx, int hz, int h, BlockState floor, BlockState wall, RandomSource r, float decay) {
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                set(l, base.offset(x, -1, z), floor);
                boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                for (int y = 0; y < h; y++) set(l, base.offset(x, y, z), edge && r.nextFloat() >= decay ? wall : Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** An open doorway two high in the wall facing {@code d}, {@code half} blocks out from the centre. */
    private static BlockPos doorway(ServerLevel l, BlockPos base, Direction d, int half, boolean withDoor) {
        BlockPos door = base.relative(d, half);
        set(l, door, Blocks.AIR.defaultBlockState());
        set(l, door.above(), Blocks.AIR.defaultBlockState());
        if (withDoor) {
            set(l, door, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d).setValue(DoorBlock.OPEN, true));
            set(l, door.above(), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d).setValue(DoorBlock.OPEN, true)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        return door.immutable();
    }

    // --- Gojo: the old watchtower ---

    /** A narrow stone watchtower, its ladder inside, a platform at the top where an old lamp still burns. */
    static void buildTower(ServerLevel l, Incident in) {
        RandomSource r = RandomSource.create(in.id.hashCode());
        BlockPos base = Sites.surface(l, in.site);
        Direction d = out(in);
        int h = 14;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(l, base.offset(x, -1, z), Blocks.COBBLESTONE.defaultBlockState());
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                for (int y = 0; y < h; y++) {
                    BlockState s = !edge ? Blocks.AIR.defaultBlockState()
                            : (Math.abs(x) == 2 && Math.abs(z) == 2) ? Blocks.STONE_BRICKS.defaultBlockState()
                            : r.nextInt(5) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : r.nextInt(7) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : (y % 5 == 3 && (x == 0 || z == 0)) ? Blocks.AIR.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    set(l, base.offset(x, y, z), s);
                }
                set(l, base.offset(x, h, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (edge) set(l, base.offset(x, h + 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState());
            }
        }
        // The ladder up the back wall, through a hatch in the platform.
        Direction back = d.getOpposite();
        BlockPos ladderCol = base.relative(back, 1);
        for (int y = 0; y <= h; y++) {
            set(l, ladderCol.above(y), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, d));
        }
        BlockPos door = doorway(l, base, d, 2, false);
        // The lamp at the top: still lit, though nobody climbs up to light it.
        BlockPos lamp = base.above(h + 1);
        set(l, lamp, Blocks.LANTERN.defaultBlockState());
        in.marks.put("door", door);
        in.marks.put("object", lamp.immutable());
    }

    // --- Yuji: the abandoned theater ---

    /**
     * A long dark hall: a boarded front with an empty marquee, rows of seats (stairs) facing a white screen, and the
     * projector booth at the back where the projector (a jukebox) still sits.
     */
    static void buildTheater(ServerLevel l, Incident in) {
        RandomSource r = RandomSource.create(in.id.hashCode());
        BlockPos base = Sites.surface(l, in.site);
        Direction d = out(in);
        Direction side = d.getClockWise();
        int half = 6, width = 4, h = 6;
        // Walls along the hall's length (d axis = depth).
        for (int a = -half; a <= half; a++) {
            for (int b = -width; b <= width; b++) {
                BlockPos c = base.relative(d, a).relative(side, b);
                set(l, c.below(), (a + b) % 3 == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                boolean edge = Math.abs(a) == half || Math.abs(b) == width;
                for (int y = 0; y < h; y++) {
                    BlockState s = edge ? (r.nextInt(10) == 0 ? Blocks.AIR.defaultBlockState()
                            : y == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    set(l, c.above(y), s);
                }
                if (r.nextInt(8) != 0) set(l, c.above(h), Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
        }
        // The screen on the far wall (inside, facing the seats).
        for (int b = -width + 1; b <= width - 1; b++) for (int y = 1; y < h - 1; y++) {
            set(l, base.relative(d, -half + 1).relative(side, b).above(y), Blocks.WOOL.white().defaultBlockState());
        }
        // Rows of seats facing the screen, an aisle down the middle.
        for (int row = 0; row < 4; row++) {
            int a = -half + 4 + row * 2;
            for (int b = -width + 1; b <= width - 1; b++) {
                if (b == 0) continue;
                set(l, base.relative(d, a).relative(side, b), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d));
            }
        }
        // The booth at the back: a raised floor and the projector on it.
        for (int b = -1; b <= 1; b++) set(l, base.relative(d, half - 1).relative(side, b), Blocks.SPRUCE_PLANKS.defaultBlockState());
        BlockPos projector = base.relative(d, half - 1).above();
        set(l, projector, Blocks.JUKEBOX.defaultBlockState());
        // A marquee over the entrance with no letters left on it; cobwebs; the door on the front.
        for (int b = -2; b <= 2; b++) set(l, base.relative(d, half + 1).relative(side, b).above(h - 2), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
        for (int i = 0; i < 6; i++) set(l, base.relative(d, r.nextInt(2 * half - 2) - half + 1).relative(side, r.nextBoolean() ? width - 1 : -width + 1).above(h - 1),
                Blocks.COBWEB.defaultBlockState());
        BlockPos door = doorway(l, base.relative(side, 1), d, half, false);
        doorway(l, base.relative(side, -1), d, half, false);
        in.marks.put("door", door);
        in.marks.put("object", projector.immutable());
    }

    // --- Hakari: the storehouse with something under it ---

    /** A barn of grey planks, a bell hung over the trapdoor in its floor; music comes from under it at night. */
    static void buildStorehouse(ServerLevel l, Incident in) {
        RandomSource r = RandomSource.create(in.id.hashCode());
        BlockPos base = Sites.surface(l, in.site);
        Direction d = out(in);
        shell(l, base, 4, 4, 5, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), r, 0.08f);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (r.nextInt(5) != 0) set(l, base.offset(x, 5, z), Blocks.SPRUCE_SLAB.defaultBlockState());
        BlockPos door = doorway(l, base, d, 4, true);
        // Hay, barrels, a cart's worth of crates, and coins (gold nuggets lost in the cracks are drawn as a pressure plate).
        for (int i = 0; i < 6; i++) {
            BlockPos p = base.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3);
            if (Math.abs(p.getX() - base.getX()) <= 1 && Math.abs(p.getZ() - base.getZ()) <= 1) continue;
            set(l, p, i % 2 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
        }
        set(l, base.offset(2, 0, -2), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        // The trapdoor in the middle of the floor, and the bell over it.
        set(l, base.below(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
        set(l, base.below(2), Blocks.AIR.defaultBlockState());
        BlockPos post = base.relative(d.getClockWise(), 1);
        set(l, post, Blocks.SPRUCE_FENCE.defaultBlockState());
        BlockPos bell = post.above();
        set(l, bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, d).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        in.marks.put("door", door);
        in.marks.put("object", bell.immutable());
    }

    // --- Ryu: the crater ---

    /** A blast crater: a glassy bowl, trees flattened outward, and at its centre the black shard everything struck. */
    static void buildCrater(ServerLevel l, Incident in) {
        RandomSource r = RandomSource.create(in.id.hashCode());
        BlockPos c = Sites.surface(l, in.site);
        int R = 7;
        for (int x = -R - 3; x <= R + 3; x++) {
            for (int z = -R - 3; z <= R + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                BlockPos col = c.offset(x, 0, z);
                if (d <= R) {
                    int depth = (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)) * 0.55);
                    for (int y = 6; y >= -depth; y--) set(l, col.above(y), Blocks.AIR.defaultBlockState());
                    BlockState bottom = d < 2.5 ? Blocks.OBSIDIAN.defaultBlockState()
                            : r.nextInt(4) == 0 ? Blocks.BASALT.defaultBlockState() : r.nextInt(3) == 0 ? Blocks.TINTED_GLASS.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState();
                    set(l, col.below(depth + 1), bottom);
                } else if (d <= R + 3 && r.nextInt(3) == 0) {
                    // Scorched earth thrown out over the rim.
                    BlockPos top = Sites.surface(l, col).below();
                    if (!l.getBlockState(top).isAir()) set(l, top, r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                }
            }
        }
        // Trunks laid flat, pointing away from the centre.
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int sx = (int) Math.round(Math.cos(a) * (R + 2)), sz = (int) Math.round(Math.sin(a) * (R + 2));
            Direction.Axis axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
            for (int k = 0; k < 3; k++) {
                BlockPos p = Sites.surface(l, c.offset(sx + (axis == Direction.Axis.X ? (int) Math.signum(Math.cos(a)) * k : 0), 0,
                        sz + (axis == Direction.Axis.Z ? (int) Math.signum(Math.sin(a)) * k : 0)));
                set(l, p, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, axis));
            }
        }
        // The shard.
        int bottomY = (int) Math.round(R * 0.55);
        BlockPos shard = c.below(bottomY);
        set(l, shard, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        set(l, shard.above(), Blocks.OBSIDIAN.defaultBlockState());
        in.marks.put("object", shard.above().immutable());
    }

    // --- Yuta: the chapel ---

    /** A small white chapel: pews, a strip of carpet, a ring of candles round the altar at the far end. */
    static void buildChapel(ServerLevel l, Incident in) {
        RandomSource r = RandomSource.create(in.id.hashCode());
        BlockPos base = Sites.surface(l, in.site);
        Direction d = out(in);
        Direction side = d.getClockWise();
        int half = 5, width = 3, h = 6;
        for (int a = -half; a <= half; a++) {
            for (int b = -width; b <= width; b++) {
                BlockPos c = base.relative(d, a).relative(side, b);
                set(l, c.below(), b == 0 ? Blocks.WOOL.white().defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                boolean edge = Math.abs(a) == half || Math.abs(b) == width;
                for (int y = 0; y < h; y++) {
                    BlockState s = !edge ? Blocks.AIR.defaultBlockState()
                            : (y == 2 && Math.abs(b) == width && a % 2 == 0) ? Blocks.STAINED_GLASS_PANE.white().defaultBlockState()
                            : r.nextInt(9) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
                    set(l, c.above(y), s);
                }
                set(l, c.above(h), Blocks.STONE_BRICK_SLAB.defaultBlockState());
            }
        }
        // Pews.
        for (int a = -half + 4; a <= half - 2; a += 2) for (int b = -width + 1; b <= width - 1; b++) {
            if (b == 0) continue;
            set(l, base.relative(d, a).relative(side, b), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d));
        }
        // The altar, candles round it, white flowers.
        BlockPos altar = base.relative(d, -half + 2);
        set(l, altar, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        set(l, altar.above(), Blocks.DYED_CANDLE.white().defaultBlockState());
        for (Direction s : new Direction[] {side, side.getOpposite()}) {
            set(l, altar.relative(s), Blocks.CANDLE.defaultBlockState());
            set(l, altar.relative(s, 2), Blocks.LILY_OF_THE_VALLEY.defaultBlockState().canSurvive(l, altar.relative(s, 2))
                    ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        // A bell tower stub over the door.
        BlockPos door = doorway(l, base, d, half, true);
        set(l, door.above(2), Blocks.CALCITE.defaultBlockState());
        set(l, door.above(h + 1), Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, d).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        in.marks.put("door", door);
        in.marks.put("object", altar.immutable());
    }
}
