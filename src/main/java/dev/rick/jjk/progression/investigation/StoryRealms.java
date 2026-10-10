package dev.rick.jjk.progression.investigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The character storylines' realms ({@link CursedRealms.Layout}s in the shared realm dimension, built and cleared like
 * every other arena): Gojo's road that never arrives, Yuji's theater, Ryu's crater, Hakari's fight club and Yuta's
 * moonlit chapel garden. Used by the village storylines' events and, with their own rules on top, by the personal trials.
 * Everything stays inside the arena box ({@link CursedRealms#RADIUS}).
 */
public final class StoryRealms {
    private StoryRealms() {}

    static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** A disc of {@code top} over {@code depth} of {@code under}, ragged at the rim. */
    static void island(ServerLevel l, BlockPos o, RandomSource r, int radius, BlockState top, BlockState under, int depth) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 1.5;
                if (d > radius) continue;
                set(l, o.offset(x, 0, z), top);
                int dd = Math.max(1, depth - (int) (d / 4));
                for (int y = 1; y <= dd; y++) set(l, o.offset(x, -y, z), under);
            }
        }
    }

    /**
     * Gojo: the road to the watchtower, from the inside. A straight causeway over nothing, the same watchtower standing at
     * the far end and again and again beside the road, each smaller than it should be; lamp posts in a line that never
     * converges. Fragments of road hang in the void at wrong angles.
     */
    public static final class Distance implements CursedRealms.Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            // The causeway along x, and a wide square in the middle of it (room to fight).
            for (int x = -24; x <= 24; x++) {
                for (int z = -3; z <= 3; z++) {
                    BlockState s = Math.abs(z) == 3 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                            : (x % 4 == 0 && z == 0) ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                    set(l, o.offset(x, 0, z), s);
                    set(l, o.offset(x, -1, z), Blocks.DEEPSLATE.defaultBlockState());
                }
                if (x % 6 == 0) for (int side : new int[] {-3, 3}) {
                    for (int y = 1; y <= 3; y++) set(l, o.offset(x, y, side), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                    set(l, o.offset(x, 4, side), Blocks.SOUL_LANTERN.defaultBlockState());
                }
            }
            for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
                if (x * x + z * z > 90) continue;
                set(l, o.offset(x, 0, z), (x + z) % 5 == 0 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
                set(l, o.offset(x, -1, z), Blocks.DEEPSLATE.defaultBlockState());
            }
            // The watchtower at the end of the road, and its smaller copies beside it.
            tower(l, o.offset(22, 1, 0), 12);
            tower(l, o.offset(12, 1, -12), 7);
            tower(l, o.offset(-6, 1, 14), 5);
            tower(l, o.offset(-18, 1, -10), 3);
            // Pieces of road hanging at angles in the void.
            for (int i = 0; i < 7; i++) {
                int cx = r.nextInt(40) - 20, cz = (r.nextBoolean() ? 1 : -1) * (12 + r.nextInt(10)), cy = r.nextInt(18) - 4;
                for (int k = 0; k < 6; k++) set(l, o.offset(cx + k, cy + k / 2, cz + (k % 3) - 1), Blocks.DEEPSLATE_TILES.defaultBlockState());
            }
        }

        private static void tower(ServerLevel l, BlockPos b, int h) {
            for (int y = 0; y < h; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0 && y < h - 1) continue;
                set(l, b.offset(x, y, z), y % 4 == 3 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
            set(l, b.above(h), Blocks.LANTERN.defaultBlockState());
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(-20, 1, 0);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(4, 1, 0), new Vec3(2, 1, 5), new Vec3(2, 1, -5), new Vec3(8, 1, 3), new Vec3(8, 1, -3), new Vec3(-2, 1, 6));
        }

        @Override
        public int radius() {
            return 24;
        }

        @Override
        public String entryLine() {
            return "The tower is right there. It has always been right there. You can't reach it.";
        }
    }

    /**
     * Yuji: inside the theater. A long hall of red seats in rows facing a lit screen, the aisle carpeted red, the
     * projector's beam a line of light from the booth. Close, crowded, humanoid shapes rising out of the seats.
     */
    public static final class Theater implements CursedRealms.Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            int hx = 20, hz = 12, h = 10;
            for (int x = -hx - 1; x <= hx + 1; x++) {
                for (int z = -hz - 1; z <= hz + 1; z++) {
                    set(l, o.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
                    set(l, o.offset(x, 0, z), Math.abs(z) <= 1 ? Blocks.RED_CARPET.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    if (Math.abs(z) <= 1) set(l, o.offset(x, -1, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    set(l, o.offset(x, h, z), Blocks.BLACK_CONCRETE.defaultBlockState());
                    boolean wall = Math.abs(x) == hx + 1 || Math.abs(z) == hz + 1;
                    if (wall) for (int y = 0; y < h; y++) set(l, o.offset(x, y, z), y % 3 == 1 ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                }
            }
            // The carpet sits on planks (a carpet needs a floor under it).
            for (int x = -hx; x <= hx; x++) for (int z = -1; z <= 1; z++) {
                set(l, o.offset(x, 0, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                set(l, o.offset(x, 1, z), Blocks.RED_CARPET.defaultBlockState());
            }
            // The screen across the far (east) wall, lit from behind.
            for (int z = -hz + 2; z <= hz - 2; z++) for (int y = 2; y < h - 1; y++) {
                set(l, o.offset(hx, y, z), Blocks.WHITE_CONCRETE.defaultBlockState());
                set(l, o.offset(hx + 1, y, z), Blocks.SEA_LANTERN.defaultBlockState());
            }
            // Rows of seats facing the screen, with gaps between blocks of seats.
            for (int x = -hx + 4; x <= hx - 8; x += 3) {
                for (int z = -hz + 1; z <= hz - 1; z++) {
                    if (Math.abs(z) <= 1 || r.nextInt(9) == 0) continue;
                    set(l, o.offset(x, 1, z), Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
                }
            }
            // The booth at the back and the projector beam (end rods along the ceiling).
            for (int z = -2; z <= 2; z++) for (int y = 1; y <= 3; y++) set(l, o.offset(-hx, y, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            set(l, o.offset(-hx + 1, 4, 0), Blocks.JUKEBOX.defaultBlockState());
            for (int x = -hx + 3; x < hx; x += 4) set(l, o.offset(x, h - 2, 0), Blocks.END_ROD.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.EndRodBlock.FACING, Direction.EAST));
            for (int x = -hx + 2; x < hx; x += 8) for (int z : new int[] {-hz, hz}) set(l, o.offset(x, h - 1, z),
                    Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(-16, 1, 0);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(12, 1, 0), new Vec3(9, 1, 4), new Vec3(9, 1, -4), new Vec3(5, 1, 7), new Vec3(5, 1, -7), new Vec3(14, 1, 6));
        }

        @Override
        public int radius() {
            return 20;
        }

        @Override
        public String entryLine() {
            return "The film is still playing. Everyone in the audience turns around to look at you.";
        }
    }

    /** Ryu: a scorched crater, glassy and wide open, black spires leaning out from where everything struck. */
    public static final class Crater implements CursedRealms.Layout {
        static final int R = 22;

        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + z * z) + r.nextDouble() * 1.2;
                    if (d > R) continue;
                    int y = (int) Math.round((d / R) * (d / R) * 5);
                    BlockState top = d < 4 ? Blocks.OBSIDIAN.defaultBlockState()
                            : r.nextInt(9) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : r.nextInt(4) == 0 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                    set(l, o.offset(x, y, z), top);
                    for (int k = 1; k <= 3; k++) set(l, o.offset(x, y - k, z), Blocks.BLACKSTONE.defaultBlockState());
                }
            }
            for (int i = 0; i < 9; i++) {
                double a = Mth.TWO_PI * i / 9 + r.nextDouble() * 0.3;
                double rad = 12 + r.nextDouble() * 7;
                int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad);
                int base = (int) Math.round((rad / R) * (rad / R) * 5);
                int h = 3 + r.nextInt(6);
                for (int y = 1; y <= h; y++) {
                    int lean = y / 3;
                    set(l, o.offset(x + (int) Math.round(Math.cos(a) * lean), base + y, z + (int) Math.round(Math.sin(a) * lean)),
                            y == h ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
                }
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 6, 17);
        }

        @Override
        public List<Vec3> curseSpots() {
            List<Vec3> out = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double a = Mth.TWO_PI * i / 8;
                out.add(new Vec3(Math.cos(a) * 8, 2, Math.sin(a) * 8 - 3));
            }
            return out;
        }

        @Override
        public int radius() {
            return 21;
        }

        @Override
        public String entryLine() {
            return "Everything here has already been struck once. The ground is still warm.";
        }
    }

    /**
     * Hakari: the fight club under the storehouse. A square ring fenced in iron, bleachers on every side, the walls lined
     * with gold and casino lights, a ceiling hung with chains and lanterns, and the bell in the corner.
     */
    public static final class FightClub implements CursedRealms.Layout {
        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            int hall = 18, ring = 8, h = 11;
            BlockState[] lights = {Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(), Blocks.OCHRE_FROGLIGHT.defaultBlockState(),
                    Blocks.VERDANT_FROGLIGHT.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState()};
            for (int x = -hall - 1; x <= hall + 1; x++) {
                for (int z = -hall - 1; z <= hall + 1; z++) {
                    set(l, o.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    boolean inRing = Math.abs(x) <= ring && Math.abs(z) <= ring;
                    set(l, o.offset(x, 0, z), inRing ? ((x + z) % 2 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState())
                            : Blocks.SPRUCE_PLANKS.defaultBlockState());
                    set(l, o.offset(x, h, z), Blocks.STONE_BRICKS.defaultBlockState());
                    boolean wall = Math.abs(x) == hall + 1 || Math.abs(z) == hall + 1;
                    if (wall) for (int y = 1; y < h; y++) {
                        BlockState s = y == 4 && Math.floorMod(x + z, 3) == 0 ? lights[Math.floorMod(x * 7 + z * 3, lights.length)]
                                : y == 6 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                        set(l, o.offset(x, y, z), s);
                    }
                }
            }
            // The ring's fence, open at two corners.
            for (int i = -ring; i <= ring; i++) for (int[] e : new int[][] {{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
                if (Math.abs(e[0]) == ring && Math.abs(e[1]) == ring) {
                    for (int y = 1; y <= 3; y++) set(l, o.offset(e[0], y, e[1]), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    continue;
                }
                if (Math.abs(i) <= 1 && (e[0] == -ring || e[1] == ring)) continue;
                set(l, o.offset(e[0], 1, e[1]), Blocks.IRON_BARS.defaultBlockState());
                set(l, o.offset(e[0], 2, e[1]), Blocks.IRON_BARS.defaultBlockState());
            }
            // Bleachers stepping up toward the walls.
            for (int step = 0; step < 4; step++) {
                int d = ring + 3 + step * 2;
                for (int i = -d; i <= d; i++) for (int[] e : new int[][] {{i, -d}, {i, d}, {-d, i}, {d, i}}) {
                    if (Math.abs(e[0]) > hall || Math.abs(e[1]) > hall) continue;
                    for (int y = 1; y <= step + 1; y++) set(l, o.offset(e[0], y, e[1]), Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
            // Chains and lanterns over the ring, the bell in the corner.
            for (int x = -ring; x <= ring; x += 4) for (int z = -ring; z <= ring; z += 4) {
                for (int y = h - 3; y < h; y++) set(l, o.offset(x, y, z), Blocks.IRON_CHAIN.defaultBlockState());
                set(l, o.offset(x, h - 4, z), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            }
            set(l, o.offset(ring - 1, 1, ring - 1), Blocks.BELL.defaultBlockState());
            for (int i = 0; i < 12; i++) set(l, o.offset(r.nextInt(2 * ring - 2) - ring + 1, 1, r.nextInt(2 * ring - 2) - ring + 1),
                    Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(-5, 1, 0);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(5, 1, 0), new Vec3(4, 1, 4), new Vec3(4, 1, -4), new Vec3(0, 1, 5), new Vec3(0, 1, -5), new Vec3(6, 1, 3));
        }

        @Override
        public int radius() {
            return 17;
        }

        @Override
        public String entryLine() {
            return "The crowd roars. A bell rings. Place your bets.";
        }
    }

    /**
     * Yuta: a chapel garden at night, as someone remembers it. Grass under no sky, a ring of white flowers, a broken
     * chapel wall with its window still standing, and pale lights hanging in the dark.
     */
    public static final class Chapel implements CursedRealms.Layout {
        static final int R = 18;

        @Override
        public void build(ServerLevel l, BlockPos o, RandomSource r) {
            island(l, o, r, R, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), 4);
            // The ring of white flowers.
            for (int i = 0; i < 72; i++) {
                double a = Mth.TWO_PI * i / 72;
                BlockPos p = o.offset((int) Math.round(Math.cos(a) * 10), 1, (int) Math.round(Math.sin(a) * 10));
                set(l, p, i % 3 == 0 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState() : Blocks.OXEYE_DAISY.defaultBlockState());
            }
            for (int i = 0; i < 30; i++) {
                int x = r.nextInt(2 * R - 4) - R + 2, z = r.nextInt(2 * R - 4) - R + 2;
                if (x * x + z * z > (R - 2) * (R - 2)) continue;
                set(l, o.offset(x, 1, z), Blocks.PINK_PETALS.defaultBlockState());
            }
            // The chapel's back wall, standing alone, its round window.
            for (int x = -6; x <= 6; x++) for (int y = 1; y <= 9 - Math.abs(x) / 2; y++) {
                boolean window = x * x + (y - 6) * (y - 6) <= 5;
                set(l, o.offset(x, y, -14), window ? Blocks.WHITE_STAINED_GLASS.defaultBlockState()
                        : r.nextInt(7) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
            }
            set(l, o.offset(0, 1, -12), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            set(l, o.offset(0, 2, -12), Blocks.WHITE_CANDLE.defaultBlockState());
            // Pale lights hanging in the dark.
            for (int i = 0; i < 8; i++) {
                double a = Mth.TWO_PI * i / 8 + 0.2;
                set(l, o.offset((int) Math.round(Math.cos(a) * 14), 6 + r.nextInt(3), (int) Math.round(Math.sin(a) * 14)),
                        Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            }
        }

        @Override
        public Vec3 arrival() {
            return new Vec3(0, 1, 14);
        }

        @Override
        public List<Vec3> curseSpots() {
            return List.of(new Vec3(0, 1, -6), new Vec3(-7, 1, -2), new Vec3(7, 1, -2), new Vec3(-5, 1, 5), new Vec3(5, 1, 5), new Vec3(0, 1, 0));
        }

        @Override
        public int radius() {
            return 17;
        }

        @Override
        public String entryLine() {
            return "Someone is holding your hand. When you look, there's nobody there.";
        }
    }
}
