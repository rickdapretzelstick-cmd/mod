package dev.rick.jjk.progression.investigation;

import dev.rick.jjk.progression.ProgressionBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * The haunted hunting lodge: where it can stand (dry, fairly level ground in or by a forest, preferably spruce), how
 * the village describes the way there (a direction, a ridge crossed on the way, the kind of woods), and the lodge itself
 * with its surroundings, built once its ground is loaded:
 * <ul>
 *   <li>a weathered, modest spruce-and-dark-oak cabin, broken windows, a gun rack (sealed) on the back wall, hunting
 *   gear left where it lay, and a rifle scope mounted on the front windowsill, looking out at the woods;</li>
 *   <li>the clues around it: two abandoned hunting stands, impact marks scored into tree trunks, a line of tracks that
 *   stops dead, and (from the investigation's tick) distant gunshots with nobody there.</li>
 * </ul>
 * Every place that matters is recorded on the incident ({@link Incident#mark}): {@code scope}, {@code rack},
 * {@code anomaly} (what the scope will show), {@code stand_a}, {@code stand_b}, {@code marks}, {@code tracks}.
 */
final class LodgeSite {
    private LodgeSite() {}

    // --- Where ---

    @Nullable
    static Sites.Site find(ServerLevel level, BlockPos village, RandomSource r, int min, int max) {
        for (int attempt = 0; attempt < 80; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = min + r.nextDouble() * Math.max(1, max - min);
            int x = village.getX() + (int) Math.round(Math.cos(a) * d), z = village.getZ() + (int) Math.round(Math.sin(a) * d);
            int h = Sites.height(level, x, z);
            if (h <= level.getSeaLevel() + 1) continue;
            Holder<Biome> b = level.getBiome(new BlockPos(x, h, z));
            if (!(b.is(BiomeTags.IS_TAIGA) || b.is(BiomeTags.IS_FOREST)) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER)) continue;
            // A lodge needs level-ish ground for its 9x7 floor: every corner within two blocks of the middle.
            boolean ok = true;
            for (int[] c : new int[][] {{-5, -4}, {5, -4}, {-5, 4}, {5, 4}, {0, 6}, {0, -6}}) {
                if (Math.abs(Sites.height(level, x + c[0], z + c[1]) - h) > 2) ok = false;
            }
            if (!ok) continue;
            // It faces back toward the village (the front window looks out at the deeper woods behind it).
            int dx = Integer.signum(x - village.getX()), dz = Integer.signum(z - village.getZ());
            if (Math.abs(x - village.getX()) > Math.abs(z - village.getZ())) dz = 0;
            else dx = 0;
            return new Sites.Site(new BlockPos(x, h, z), dx == 0 && dz == 0 ? 1 : dx, dz);
        }
        return null;
    }

    /**
     * How the village says where it is: "beyond the northern ridge, near the spruce forest" (a ridge only if the ground
     * really rises between them; the woods by the site's own biome).
     */
    static String describe(ServerLevel level, BlockPos village, BlockPos site) {
        String dir = ReportWriter.direction(village, site);
        int top = Integer.MIN_VALUE;
        for (int i = 1; i < 12; i++) {
            int x = village.getX() + (site.getX() - village.getX()) * i / 12, z = village.getZ() + (site.getZ() - village.getZ()) * i / 12;
            top = Math.max(top, Sites.height(level, x, z));
        }
        boolean ridge = top >= Math.max(village.getY(), site.getY()) + 6;
        Holder<Biome> b = level.getBiome(site);
        String woods = b.is(BiomeTags.IS_TAIGA) ? "near the spruce forest" : "in the woods";
        String adj = switch (dir) {
            case "north" -> "northern";
            case "south" -> "southern";
            case "east" -> "eastern";
            case "west" -> "western";
            default -> dir;
        };
        return (ridge ? "beyond the " + adj + " ridge" : "out past the " + adj + " fields") + ", " + woods;
    }

    // --- The lodge and what's round it ---

    static void build(ServerLevel level, Incident in) {
        int fx = in.dirX, fz = in.dirZ;
        Direction front = Direction.getApproximateNearest(fx, 0, fz);
        // The lodge faces away from the village: its front window looks into the woods.
        Direction out = front;
        Direction side = out.getClockWise();
        BlockPos base = in.site;
        int floorY = base.getY();
        RandomSource r = RandomSource.create(in.id.hashCode());
        // Ground: level it out under the floor and clear the inside.
        for (int a = -5; a <= 5; a++) {
            for (int b = -4; b <= 4; b++) {
                BlockPos col = at(base, out, side, b, a);
                for (int y = -3; y < 0; y++) {
                    BlockPos p = col.atY(floorY + y);
                    if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(BlockTags.LEAVES)) level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
                }
                for (int y = 0; y < 7; y++) level.setBlock(col.atY(floorY + y), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Floor, walls (log corners, plank walls), a pitched roof.
        for (int a = -4; a <= 4; a++) {
            for (int b = -3; b <= 3; b++) {
                BlockPos col = at(base, out, side, b, a);
                level.setBlock(col.atY(floorY - 1), (a + b) % 3 == 0 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                boolean edgeA = Math.abs(a) == 4, edgeB = Math.abs(b) == 3;
                if (!edgeA && !edgeB) continue;
                for (int y = 0; y < 4; y++) {
                    BlockState s = edgeA && edgeB ? Blocks.DARK_OAK_LOG.defaultBlockState()
                            : (y == 0 || y == 3) ? Blocks.SPRUCE_LOG.defaultBlockState() : (r.nextInt(6) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                    level.setBlock(col.atY(floorY + y), s, 2);
                }
            }
        }
        for (int a = -5; a <= 5; a++) {
            for (int b = -4; b <= 4; b++) {
                int rise = 3 - Math.abs(b);
                if (rise < 0) continue;
                BlockPos p = at(base, out, side, b, a).atY(floorY + 4 + Math.max(0, rise - 1));
                BlockState roof = Math.abs(b) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                        : Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, b > 0 ? side.getOpposite() : side);
                if (r.nextInt(14) == 0) continue; // a few missing shingles
                level.setBlock(p, roof, 2);
                for (int y = floorY + 4; y < p.getY(); y++) if (Math.abs(a) == 4 || Math.abs(a) == 5) level.setBlock(p.atY(y), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
            }
        }
        // The door, in the back wall (the side facing the village), left open.
        BlockPos door = at(base, out, side, 0, -4);
        level.setBlock(door.atY(floorY), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(door.atY(floorY + 1), Blocks.AIR.defaultBlockState(), 2);
        // Windows: the front one with the scope on its sill; the side ones broken (some panes gone).
        BlockPos sill = at(base, out, side, 0, 4).atY(floorY + 1);
        level.setBlock(sill.atY(floorY + 2), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(sill, Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
        BlockPos scope = sill.relative(out.getOpposite()).atY(floorY + 1);
        level.setBlock(scope.below(), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
        level.setBlock(scope, ProgressionBlocks.MOUNTED_SCOPE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, out), 3);
        for (int b : new int[] {-3, 3}) {
            for (int a : new int[] {-2, 2}) {
                BlockPos w = at(base, out, side, b, a).atY(floorY + 2);
                level.setBlock(w, r.nextInt(3) == 0 ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // The gun rack on the inside of the back wall, beside the door; sealed until the curse is gone.
        BlockPos rack = at(base, out, side, 2, -3).atY(floorY + 1);
        level.setBlock(rack, ProgressionBlocks.GUN_RACK.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, out), 3);
        // What the hunter left: barrels, a fletching table, a cold campfire, cobwebs in the corners, a hanging lantern.
        level.setBlock(at(base, out, side, -2, -3).atY(floorY), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, -2, -2).atY(floorY), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, 2, 2).atY(floorY), Blocks.FLETCHING_TABLE.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, -2, 2).atY(floorY), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false), 2);
        level.setBlock(at(base, out, side, 2, 0).atY(floorY), Blocks.TARGET.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, -2, 0).atY(floorY), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, 2, -3).atY(floorY + 3), Blocks.COBWEB.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, -2, 3).atY(floorY + 3), Blocks.COBWEB.defaultBlockState(), 2);
        level.setBlock(at(base, out, side, 0, 0).atY(floorY + 3), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), 2);
        in.marks.put("scope", scope.immutable());
        in.marks.put("rack", rack.immutable());
        // What the scope shows, out in the trees ahead of the window.
        BlockPos anomaly = Sites.surface(level, base.relative(out, 26).relative(side, 3));
        in.marks.put("anomaly", anomaly.above().immutable());
        // Two abandoned hunting stands out among the trees, either side of the way in.
        BlockPos standA = Sites.surface(level, base.relative(out.getOpposite(), 14).relative(side, 10));
        BlockPos standB = Sites.surface(level, base.relative(out, 12).relative(side, -12));
        stand(level, standA, side);
        stand(level, standB, side.getOpposite());
        in.marks.put("stand_a", standA.immutable());
        in.marks.put("stand_b", standB.immutable());
        // Impact marks: trunks near the lodge scored and blackened at chest height.
        BlockPos marked = null;
        int scored = 0;
        for (int i = 0; i < 160 && scored < 4; i++) {
            BlockPos p = base.offset(r.nextInt(25) - 12, 1, r.nextInt(25) - 12);
            BlockPos ground = Sites.surface(level, p);
            for (int y = 0; y < 8; y++) {
                BlockPos t = ground.below(y).above(1);
                if (level.getBlockState(t).is(BlockTags.LOGS) && !level.getBlockState(t).is(Blocks.DARK_OAK_LOG)) {
                    level.setBlock(t, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 2);
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos face = t.relative(d);
                        if (level.getBlockState(face).isAir()) {
                            level.setBlock(face, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                                    .setValue(HorizontalDirectionalBlock.FACING, d), 2);
                            break;
                        }
                    }
                    if (marked == null) marked = t.immutable();
                    scored++;
                    break;
                }
            }
        }
        in.marks.put("marks", marked != null ? marked : base.relative(side, 7).immutable());
        // Tracks from the lodge into the woods that stop dead, nine blocks out.
        BlockPos last = base;
        for (int i = 6; i <= 15; i++) {
            BlockPos p = Sites.surface(level, base.relative(out, i).relative(side, -3 + (i % 3 == 0 ? 1 : 0)));
            BlockState below = level.getBlockState(p.below());
            if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.PODZOL) || below.is(Blocks.DIRT) || below.is(Blocks.SNOW_BLOCK)) {
                level.setBlock(p.below(), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            }
            last = p;
        }
        in.marks.put("tracks", last.immutable());
    }

    /** A small wooden hunting stand: four posts, a platform four up, a ladder, a rail. */
    static void stand(ServerLevel level, BlockPos ground, Direction ladderSide) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int y = 0; y < 4; y++) level.setBlock(ground.offset(dx * 2, y, dz * 2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 2);
            }
        }
        for (int dx = 0; dx <= 2; dx++) for (int dz = 0; dz <= 2; dz++) level.setBlock(ground.offset(dx, 4, dz), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
        for (int dx = 0; dx <= 2; dx++) {
            level.setBlock(ground.offset(dx, 5, 0), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            level.setBlock(ground.offset(dx, 5, 2), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        }
        // The ladder up the north post (it faces away from the post it hangs on).
        BlockPos l = ground.offset(0, 0, -1);
        for (int y = 0; y < 5; y++) {
            BlockPos p = l.above(y);
            if (level.getBlockState(p).canBeReplaced()) level.setBlock(p, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 2);
        }
    }

    /** The column {@code b} blocks to the side and {@code a} blocks along the lodge's facing from its centre. */
    private static BlockPos at(BlockPos base, Direction out, Direction side, int b, int a) {
        return base.relative(out, a).relative(side, b);
    }
}
