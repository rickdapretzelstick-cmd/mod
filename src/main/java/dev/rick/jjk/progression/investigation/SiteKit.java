package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Building in a site's own frame, so every structure is drawn once and stands whichever way its site faces: {@code a}
 * runs toward the front (the way the site faces: doors open that way), {@code b} to the right of it, {@code y} up from
 * the ground (y=0 is the first block of air over the land at the site).
 *
 * <p>Everything placed goes through the level (so {@link SiteFootprint} records it and the site comes down cleanly),
 * without shape updates while it goes up; {@link #settle} then joins it all at once (fences to fences, panes to panes,
 * stairs into corners) and drops whatever can't stand where it was put. The helpers keep a structure honest with the
 * land: foundations and stilts run down to real ground, the space a building takes is cleared of whatever stood there,
 * and weathering is seeded by the incident so a site looks the same after a reload.
 */
final class SiteKit {
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    static final int FRONT = 0, RIGHT = 1, BACK = 2, LEFT = 3;

    final ServerLevel l;
    final BlockPos o;
    final Direction front, right;
    final RandomSource r;
    private final Set<BlockPos> placed = new LinkedHashSet<>();

    SiteKit(ServerLevel l, BlockPos origin, Direction front, long seed) {
        this.l = l;
        this.o = origin.immutable();
        this.front = front;
        this.right = front.getClockWise();
        this.r = RandomSource.create(seed);
    }

    /** The kit for an incident's site, standing on the land there. */
    static SiteKit of(ServerLevel l, Incident in) {
        return new SiteKit(l, Sites.surface(l, in.site), facing(in), in.id.hashCode());
    }

    /** The way a site faces (the nearest flat direction to its own). */
    static Direction facing(Incident in) {
        Direction d = Direction.getApproximateNearest(in.dirX, 0, in.dirZ);
        return d.getAxis() == Direction.Axis.Y ? Direction.EAST : d;
    }

    // --- Positions and facings ---

    BlockPos at(int a, int b, int y) {
        return o.relative(front, a).relative(right, b).above(y);
    }

    /** A direction in the site's frame: {@link #FRONT}, {@link #RIGHT}, {@link #BACK}, {@link #LEFT}. */
    Direction dir(int quarter) {
        return switch (Math.floorMod(quarter, 4)) {
            case 0 -> front;
            case 1 -> right;
            case 2 -> front.getOpposite();
            default -> right.getOpposite();
        };
    }

    // --- Placing ---

    void set(int a, int b, int y, BlockState s) {
        set(at(a, b, y), s);
    }

    void set(BlockPos p, BlockState s) {
        if (!l.isInWorldBounds(p)) return;
        l.setBlock(p, s, FLAGS);
        placed.add(p.immutable());
    }

    void set(int a, int b, int y, Block s) {
        set(a, b, y, s.defaultBlockState());
    }

    /** Places only into air or something replaceable (grass, flowers): never over a wall or the land. */
    void soft(int a, int b, int y, BlockState s) {
        if (get(a, b, y).canBeReplaced()) set(a, b, y, s);
    }

    BlockState get(int a, int b, int y) {
        return l.getBlockState(at(a, b, y));
    }

    boolean solid(int a, int b, int y) {
        BlockState s = get(a, b, y);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS);
    }

    void fill(int a0, int b0, int y0, int a1, int b1, int y1, BlockState s) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++)
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) set(a, b, y, s);
    }

    void fill(int a0, int b0, int y0, int a1, int b1, int y1, Block s) {
        fill(a0, b0, y0, a1, b1, y1, s.defaultBlockState());
    }

    void fill(int a0, int b0, int y0, int a1, int b1, int y1, Supplier<BlockState> s) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++)
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) set(a, b, y, s.get());
    }

    /** Clears the space a structure takes of whatever stood there (trees, tall grass, the rise of the land). */
    void clear(int a0, int b0, int y0, int a1, int b1, int y1) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++)
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) if (!get(a, b, y).isAir()) set(a, b, y, Blocks.AIR.defaultBlockState());
    }

    /**
     * Clears what grows over the land in an area (trees, bushes, tall grass, flowers) up to {@code h} over it, leaving
     * the land itself, water, and anything this site has already put there.
     */
    void clearGrowth(int a0, int b0, int a1, int b1, int h) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++)
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) {
                int g = groundY(a, b);
                for (int y = g; y <= g + h; y++) {
                    BlockPos p = at(a, b, y);
                    if (placed.contains(p)) continue;
                    BlockState s = l.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                    if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.is(Blocks.BAMBOO)
                            || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.SNOW) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                            || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BEE_NEST)) set(p, Blocks.AIR.defaultBlockState());
                }
            }
    }

    void column(int a, int b, int y0, int y1, BlockState s) {
        for (int y = y0; y <= y1; y++) set(a, b, y, s);
    }

    /**
     * Fills under a footprint from {@code top} down to real ground (at most {@code depth} blocks), so nothing floats:
     * the top layer always, then down through air, water and plants until the land.
     */
    void foundation(int a0, int b0, int a1, int b1, int top, Supplier<BlockState> s, int depth) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++)
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) {
                set(a, b, top, s.get());
                pillarDown(a, b, top - 1, s, depth - 1);
            }
    }

    void foundation(int a0, int b0, int a1, int b1, BlockState s, int depth) {
        foundation(a0, b0, a1, b1, -1, () -> s, depth);
    }

    /** A post from {@code top} down to the ground (at most {@code depth}): it stops at the first solid block. */
    void pillarDown(int a, int b, int top, Supplier<BlockState> s, int depth) {
        for (int y = top; y > top - depth; y--) {
            if (solid(a, b, y)) break;
            set(a, b, y, s.get());
        }
    }

    void pillarDown(int a, int b, int top, BlockState s, int depth) {
        pillarDown(a, b, top, () -> s, depth);
    }

    /** The height of the land in a column of the frame: the first air over it (leaves and plants aside). */
    int groundY(int a, int b) {
        BlockPos p = at(a, b, 0);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - o.getY();
        // Down past anything this site has already stood up there, and past logs: the land itself.
        while (y > -24 && !get(a, b, y - 1).isAir() && (placed.contains(at(a, b, y - 1)) || get(a, b, y - 1).is(BlockTags.LOGS)) && !isSoil(get(a, b, y - 1))) y--;
        return y;
    }

    private static boolean isSoil(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.DIRT_PATH);
    }

    /** Something standing on the land in a column (only where it can stand, and only into air or plants). */
    void onGround(int a, int b, BlockState s) {
        int y = groundY(a, b);
        if (get(a, b, y).canBeReplaced() && s.canSurvive(l, at(a, b, y))) set(a, b, y, s);
    }

    void onGround(int a, int b, Block s) {
        onGround(a, b, s.defaultBlockState());
    }

    /** Replaces the top of the land in a column (a path, churned earth...), where it is soil. */
    void ground(int a, int b, BlockState s) {
        int y = groundY(a, b) - 1;
        if (isSoil(get(a, b, y))) {
            set(a, b, y, s);
            // A plant on it that can't stand on the new top goes.
            if (!get(a, b, y + 1).isAir() && get(a, b, y + 1).canBeReplaced()) set(a, b, y + 1, Blocks.AIR.defaultBlockState());
        }
    }

    void ground(int a, int b, Block s) {
        ground(a, b, s.defaultBlockState());
    }

    boolean chance(float p) {
        return r.nextFloat() < p;
    }

    int rand(int lo, int hi) {
        return lo + r.nextInt(hi - lo + 1);
    }

    @SafeVarargs
    final <T> T pick(T... options) {
        return options[r.nextInt(options.length)];
    }

    // --- Materials ---

    static BlockState stairs(Block b, Direction facing, boolean upside) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, upside ? Half.TOP : Half.BOTTOM);
    }

    /** Stairs whose tall back faces a direction of the frame ({@link #FRONT}...). */
    BlockState stairs(Block b, int quarter, boolean upside) {
        return stairs(b, dir(quarter), upside);
    }

    static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState doubleSlab(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
    }

    /** A log lying along a direction of the frame. */
    BlockState log(Block b, int quarter) {
        return b.defaultBlockState().setValue(BlockStateProperties.AXIS, dir(quarter).getAxis());
    }

    /** A block turned to face a direction of the frame (by whichever facing it has). */
    BlockState facing(Block b, int quarter) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir(quarter));
        if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, dir(quarter));
        return s;
    }

    /** A trapdoor: shut and flat (a hatch, a board), or open and standing against the side away from {@code quarter}. */
    BlockState trapdoor(Block b, int quarter, boolean open, boolean top) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir(quarter)).setValue(BlockStateProperties.OPEN, open)
                .setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState hanging(Block lantern) {
        return lantern.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
    }

    static BlockState candles(int n) {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n))).setValue(CandleBlock.LIT, false);
    }

    static BlockState unlit(Block campfire) {
        return campfire.defaultBlockState().setValue(BlockStateProperties.LIT, false);
    }

    /** A worn stone: mostly {@code main}, now and then mossy or cracked. */
    BlockState worn(BlockState main, BlockState mossy, BlockState cracked) {
        float f = r.nextFloat();
        return f < 0.16f ? mossy : f < 0.27f ? cracked : main;
    }

    BlockState wornBricks() {
        return worn(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
    }

    BlockState wornCobble() {
        return chance(0.28f) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    BlockState wornDeepslate() {
        return worn(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
    }

    // --- Shapes ---

    /**
     * A gable roof over [a0..a1] x [b0..b1] starting at {@code y}: stairs rising from both long sides to a ridge (along
     * {@code a} when {@code ridgeAlongA}), with one block of overhang all round, the gables under it filled with
     * {@code gable}, upside-down stairs under the eaves. {@code holes} is the share of roof left out (a ruin's).
     * Returns the height of the ridge.
     */
    int gableRoof(int a0, int b0, int a1, int b1, int y, Block stair, Block slab, Supplier<BlockState> gable, boolean ridgeAlongA, float holes) {
        int lo = (ridgeAlongA ? Math.min(b0, b1) : Math.min(a0, a1)) - 1, hi = (ridgeAlongA ? Math.max(b0, b1) : Math.max(a0, a1)) + 1;
        int s0 = (ridgeAlongA ? Math.min(a0, a1) : Math.min(b0, b1)) - 1, s1 = (ridgeAlongA ? Math.max(a0, a1) : Math.max(b0, b1)) + 1;
        // The low side faces in toward the ridge: for the ridge along a, that's RIGHT from the left side.
        int inLo = ridgeAlongA ? RIGHT : FRONT, inHi = ridgeAlongA ? LEFT : BACK;
        int top = y;
        for (int k = 0; lo + k <= hi - k; k++) {
            int left = lo + k, rt = hi - k, yy = y + k;
            top = yy;
            for (int s = s0; s <= s1; s++) {
                boolean end = s == s0 || s == s1;
                if (left < rt) {
                    if (!chance(holes)) place(ridgeAlongA, s, left, yy, stairs(stair, inLo, false));
                    if (!chance(holes)) place(ridgeAlongA, s, rt, yy, stairs(stair, inHi, false));
                    if (k == 0) {
                        // Under the eaves.
                        place(ridgeAlongA, s, left, yy - 1, stairs(stair, inHi, true));
                        place(ridgeAlongA, s, rt, yy - 1, stairs(stair, inLo, true));
                    }
                    // The gables, just inside the overhang at either end.
                    if (!end && (s == s0 + 1 || s == s1 - 1)) for (int g = left + 1; g < rt; g++) place(ridgeAlongA, s, g, yy, gable.get());
                } else if (!chance(holes)) {
                    place(ridgeAlongA, s, left, yy, slab(slab, false));
                }
            }
        }
        return top;
    }

    private void place(boolean ridgeAlongA, int along, int across, int y, BlockState s) {
        if (ridgeAlongA) set(along, across, y, s);
        else set(across, along, y, s);
    }

    /** A pyramid (hip) roof over the square [-half..half] round (ca, cb), stairs on every side, filled in, to a point. */
    int pyramidRoof(int ca, int cb, int half, int y, Block stair, BlockState fill, BlockState peak) {
        for (int k = 0; k <= half; k++) {
            int h = half - k, yy = y + k;
            for (int a = -h; a <= h; a++)
                for (int b = -h; b <= h; b++) {
                    boolean ea = Math.abs(a) == h, eb = Math.abs(b) == h;
                    if (h == 0) set(ca, cb, yy, peak);
                    else if (ea && !eb) set(ca + a, cb + b, yy, stairs(stair, a > 0 ? BACK : FRONT, false));
                    else if (eb && !ea) set(ca + a, cb + b, yy, stairs(stair, b > 0 ? LEFT : RIGHT, false));
                    else if (ea) set(ca + a, cb + b, yy, stairs(stair, a > 0 ? BACK : FRONT, false));
                    else set(ca + a, cb + b, yy, fill);
                }
        }
        return y + half;
    }

    /** Hollow walls round [a0..a1] x [b0..b1], from y0 to y1; corners {@code post}, the rest from {@code wall}. */
    void walls(int a0, int b0, int a1, int b1, int y0, int y1, BlockState post, Supplier<BlockState> wall) {
        for (int a = a0; a <= a1; a++)
            for (int b = b0; b <= b1; b++) {
                boolean edgeA = a == a0 || a == a1, edgeB = b == b0 || b == b1;
                if (!edgeA && !edgeB) continue;
                for (int y = y0; y <= y1; y++) set(a, b, y, edgeA && edgeB ? post : wall.get());
            }
    }

    /** A door two high at (a, b, y), facing a direction of the frame, open or shut. */
    void door(int a, int b, int y, Block doorBlock, int quarter, boolean open, boolean rightHinge) {
        BlockState d = doorBlock.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir(quarter))
                .setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.DOOR_HINGE, rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        set(a, b, y, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        set(a, b, y + 1, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** A bed, its foot at (a, b, y), its head toward a direction of the frame. */
    void bed(int a, int b, int y, Block bed, int headTo) {
        BlockState s = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir(headTo));
        set(a, b, y, s.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        BlockPos head = at(a, b, y).relative(dir(headTo));
        set(head, s.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    /**
     * Leaves that never decay (overgrowth, a bush against a wall). Nothing a site puts down may grow or spread on its own
     * (vines, saplings, crops, dirt that grass creeps onto): what changes by itself can't be told from a player's work
     * when the site comes down, and would be left behind.
     */
    static BlockState leaves(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
    }

    /** Cobwebs in some of the given spots ({a, b, y}), only where there is air. */
    void cobwebs(int[][] spots, float p) {
        for (int[] s : spots) if (chance(p) && get(s[0], s[1], s[2]).isAir()) set(s[0], s[1], s[2], Blocks.COBWEB.defaultBlockState());
    }

    /** A worn path of {@code s} from (a0, b0) to (a1, b1), wandering a little. */
    void path(int a0, int b0, int a1, int b1, BlockState s, float gaps) {
        int n = Math.max(Math.abs(a1 - a0), Math.abs(b1 - b0));
        double wob = 0;
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            wob += (r.nextDouble() - 0.5) * 0.6;
            wob = Math.max(-1, Math.min(1, wob));
            int a = (int) Math.round(a0 + (a1 - a0) * t + (b1 != b0 && a1 == a0 ? wob : 0));
            int b = (int) Math.round(b0 + (b1 - b0) * t + (a1 != a0 ? wob : 0));
            if (!chance(gaps)) ground(a, b, s);
            if (chance(0.35f)) ground(a + (r.nextBoolean() ? 1 : 0), b + (r.nextBoolean() ? 1 : -1), s);
        }
    }

    /** Joins up everything placed (fences, panes, walls, stairs) and drops what can't stand where it was put. */
    void settle() {
        Set<BlockPos> all = new LinkedHashSet<>(placed);
        for (BlockPos p : placed) for (Direction d : Direction.values()) all.add(p.relative(d));
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos p : all) {
                BlockState s = l.getBlockState(p);
                if (s.isAir()) continue;
                BlockState t = Block.updateFromNeighbourShapes(s, l, p);
                if (t != s) l.setBlock(p, t, FLAGS);
            }
        }
    }
}
