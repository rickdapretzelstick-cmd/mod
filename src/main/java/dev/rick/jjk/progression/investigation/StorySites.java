package dev.rick.jjk.progression.investigation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static dev.rick.jjk.progression.investigation.SiteKit.BACK;
import static dev.rick.jjk.progression.investigation.SiteKit.FRONT;
import static dev.rick.jjk.progression.investigation.SiteKit.LEFT;
import static dev.rick.jjk.progression.investigation.SiteKit.RIGHT;

/**
 * The places the character storylines happen ({@link Sites} kinds): Gojo's watchtower, Yuji's abandoned theater, Hakari's
 * storehouse, Ryu's crater and Yuta's chapel. Each is built once its ground is loaded and someone is near, like every
 * other site, drawn in its own frame ({@link SiteKit}) so it faces the way its site does, and marks what its trigger
 * needs: the {@code door} (a threshold), the {@code object} (the thing used: the watchtower's lamp, the theater's
 * projector, the storehouse's bell, the crater's black shard, the chapel's altar) and the {@code breach} beside it.
 * All of them stand on flat dry ground found by {@link Sites#findPasture}.
 */
final class StorySites {
    private StorySites() {}

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    /** The direction of the frame a cell on a ring round the middle looks in toward it. */
    private static int inward(int a, int b) {
        return Math.abs(a) >= Math.abs(b) ? (a > 0 ? BACK : FRONT) : (b > 0 ? LEFT : RIGHT);
    }

    // --- Gojo: the old watchtower ---

    /**
     * A round stone watchtower on a plinth: arrow slits climbing it, a ladder up the inside through two floors, a
     * corbelled lookout at the top behind a broken parapet, a little roof on posts, and under it the old lamp that still
     * hangs there. The door faces the way in.
     */
    static void buildTower(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int H = 15;
        k.clearGrowth(-8, -8, 8, 8, 24);
        k.clear(-5, -5, 0, 5, 5, H + 9);
        for (int a = -5; a <= 5; a++)
            for (int b = -5; b <= 5; b++) {
                double d = Math.hypot(a, b);
                if (d <= 4.6) k.foundation(a, b, a, b, -1, () -> k.wornBricks(), 7);
                if (d > 3.5 && d <= 4.6) k.set(a, b, 0, k.wornBricks());
                else if (d > 4.6 && d <= 5.4) k.set(a, b, 0, k.stairs(Blocks.STONE_BRICK_STAIRS, inward(a, b), false));
            }
        // The shaft, with slits that turn as they climb.
        int[][] slits = {{3, 0}, {0, 3}, {-3, 0}, {0, -3}};
        for (int y = 0; y < H; y++)
            for (int a = -4; a <= 4; a++)
                for (int b = -4; b <= 4; b++) {
                    double d = Math.hypot(a, b);
                    if (d > 2.5 && d <= 3.5) k.set(a, b, y, k.chance(0.06f) ? s(Blocks.ANDESITE) : k.wornBricks());
                }
        for (int y = 3; y < H - 1; y += 4) {
            int[] sl = slits[(y / 4) % 4];
            if (sl[0] == 3 && sl[1] == 0) sl = slits[1];
            k.set(sl[0], sl[1], y, s(Blocks.AIR));
            k.set(sl[0], sl[1], y + 1, s(Blocks.AIR));
            k.set(-sl[0], -sl[1], y + 1, s(Blocks.AIR));
        }
        // The doorway, cut through the plinth.
        k.set(4, 0, 0, s(Blocks.AIR));
        k.set(5, 0, 0, s(Blocks.AIR));
        k.door(3, 0, 0, Blocks.SPRUCE_DOOR, FRONT, true, false);
        k.set(3, 0, 2, k.stairs(Blocks.STONE_BRICK_STAIRS, FRONT, true));
        // Floors inside, the ladder through them, the watchman's things.
        for (int y : new int[] {5, 10}) for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) if (Math.hypot(a, b) <= 2.5) k.set(a, b, y, s(Blocks.SPRUCE_PLANKS));
        for (int y = 0; y <= H; y++) k.set(-2, 0, y, k.facing(Blocks.LADDER, FRONT));
        k.set(1, 2, 0, s(Blocks.BARREL));
        k.set(2, 1, 0, s(Blocks.BARREL));
        k.set(1, -2, 0, s(Blocks.COMPOSTER));
        k.set(1, 1, 6, s(Blocks.SPRUCE_FENCE));
        k.set(1, 1, 7, s(Blocks.SPRUCE_PRESSURE_PLATE));
        k.set(2, 1, 6, k.stairs(Blocks.SPRUCE_STAIRS, FRONT, false));
        k.bed(0, -2, 11, Blocks.BED.gray(), RIGHT);
        k.set(1, 2, 11, s(Blocks.BOOKSHELF));
        k.cobwebs(new int[][] {{2, -1, 4}, {-1, 2, 4}, {1, 2, 9}, {2, -1, 14}, {-1, -2, 14}, {0, 2, 13}}, 0.7f);
        // The lookout: corbels, the platform, a parapet fallen in on one side.
        for (int a = -5; a <= 5; a++)
            for (int b = -5; b <= 5; b++) {
                double d = Math.hypot(a, b);
                if (d > 3.5 && d <= 4.5) k.set(a, b, H - 1, k.stairs(Blocks.STONE_BRICK_STAIRS, inward(a, b), true));
                if (d <= 4.5) k.set(a, b, H, d > 3.5 ? k.wornBricks() : s(Blocks.SPRUCE_PLANKS));
                if (d > 3.5 && d <= 4.5) {
                    boolean fallen = a < 0 && b < 0 && k.chance(0.65f);
                    if (fallen) continue;
                    k.set(a, b, H + 1, s(Blocks.STONE_BRICK_WALL));
                    if ((a + b) % 2 == 0 && !k.chance(0.15f)) k.set(a, b, H + 2, s(Blocks.STONE_BRICK_WALL));
                }
            }
        k.set(-2, 0, H, k.facing(Blocks.LADDER, FRONT));
        // The roof over the lamp, on four posts.
        for (int a : new int[] {-2, 2}) for (int b : new int[] {-2, 2}) k.column(a, b, H + 1, H + 3, s(Blocks.DARK_OAK_FENCE));
        k.pyramidRoof(0, 0, 3, H + 4, Blocks.DARK_OAK_STAIRS, s(Blocks.DARK_OAK_PLANKS), s(Blocks.DARK_OAK_FENCE));
        k.set(0, 0, H + 8, s(Blocks.DARK_OAK_FENCE));
        k.set(0, 0, H + 3, SiteKit.hanging(Blocks.LANTERN));
        // Stones fallen from the top lie round the foot; a track comes to the door.
        for (int i = 0; i < 14; i++) {
            double ang = k.r.nextDouble() * Math.PI * 2, rr = 5.5 + k.r.nextDouble() * 3;
            k.onGround((int) Math.round(Math.cos(ang) * rr), (int) Math.round(Math.sin(ang) * rr), k.chance(0.5f) ? k.wornCobble() : k.wornBricks());
        }
        k.path(6, 0, 15, 0, s(Blocks.COARSE_DIRT), 0.3f);
        k.settle();
        in.marks.put("door", k.at(3, 0, 0));
        in.marks.put("object", k.at(0, 0, H + 3));
        in.marks.put("breach", k.at(1, 0, H + 1));
    }

    // --- Yuji: the abandoned theater ---

    /**
     * A brick picture house: a tall false front with a cornice, a marquee of dead bulbs and a blade sign over the doors,
     * posters gone to rags; inside, the lobby with its ticket booth and counter, the projection booth up over it, raked
     * rows of red seats, and the stage with its torn curtains round the screen. The projector (a jukebox) still sits in
     * the booth, looking through its little window at the screen.
     */
    static void buildTheater(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int A0 = -9, A1 = 7, B = 5, H = 7;
        k.clearGrowth(A0 - 2, -B - 3, A1 + 6, B + 3, 16);
        k.clear(A0, -B, 0, A1 + 3, B, 12);
        k.foundation(A0, -B, A1, B, -1, () -> s(Blocks.STONE_BRICKS), 6);
        for (int a = A0 + 1; a < A1; a++) for (int b = -B + 1; b < B; b++) k.set(a, b, -1, (a + b) % 4 == 0 ? s(Blocks.SPRUCE_PLANKS) : s(Blocks.DARK_OAK_PLANKS));
        // Walls: a stone course, brick above, stone pilasters along the sides, iron-barred windows high up.
        k.walls(A0, -B, A1, B, 0, 0, s(Blocks.STONE_BRICKS), () -> k.wornBricks());
        k.walls(A0, -B, A1, B, 1, H - 1, s(Blocks.STONE_BRICKS), () -> k.chance(0.04f) ? s(Blocks.MUD_BRICKS) : s(Blocks.BRICKS));
        for (int a = A0; a <= A1; a += 4)
            for (int b : new int[] {-B, B}) {
                k.column(a, b, 1, H - 1, s(Blocks.STONE_BRICKS));
                k.set(a, b + Integer.signum(b), 0, k.stairs(Blocks.STONE_BRICK_STAIRS, b > 0 ? LEFT : RIGHT, false));
            }
        for (int a : new int[] {-7, -3, 1}) for (int b : new int[] {-B, B}) for (int y = 3; y <= 4; y++) k.set(a, b, y, s(Blocks.IRON_BARS));
        // A flat roof behind a parapet, some of it gone.
        for (int a = A0; a <= A1; a++)
            for (int b = -B; b <= B; b++) {
                boolean edge = a == A0 || a == A1 || Math.abs(b) == B;
                if (edge) {
                    k.set(a, b, H, s(Blocks.BRICKS));
                    k.set(a, b, H + 1, SiteKit.slab(Blocks.STONE_BRICK_SLAB, false));
                } else if (!(a > -6 && a < -2 && b > 0 && k.chance(0.6f))) {
                    k.set(a, b, H, SiteKit.slab(Blocks.DARK_OAK_SLAB, true));
                }
            }
        // The false front: taller than the hall, a cornice, a raised middle.
        for (int b = -B; b <= B; b++) {
            int top = Math.abs(b) <= 1 ? H + 4 : Math.abs(b) <= 3 ? H + 3 : H + 2;
            for (int y = H; y <= top; y++) k.set(A1, b, y, Math.abs(b) == B || y == top ? s(Blocks.STONE_BRICKS) : s(Blocks.BRICKS));
            k.set(A1 + 1, b, H + 1, k.stairs(Blocks.STONE_BRICK_STAIRS, BACK, true));
            k.set(A1 + 1, b, top + 1, SiteKit.slab(Blocks.STONE_BRICK_SLAB, false));
        }
        // The entrance: a double door (one open) under a glass transom, posters either side.
        for (int b = -1; b <= 1; b++) for (int y = 0; y <= 2; y++) k.set(A1, b, y, s(Blocks.AIR));
        k.door(A1, -1, 0, Blocks.DARK_OAK_DOOR, FRONT, true, false);
        k.door(A1, 1, 0, Blocks.DARK_OAK_DOOR, FRONT, false, true);
        k.set(A1, 0, 0, s(Blocks.STONE_BRICKS));
        k.set(A1, 0, 1, s(Blocks.STONE_BRICKS));
        for (int b = -1; b <= 1; b++) k.set(A1, b, 2, s(Blocks.GLASS_PANE));
        for (int b : new int[] {-3, 3}) {
            k.set(A1 + 1, b, 1, k.facing(k.chance(0.5f) ? Blocks.WALL_BANNER.red() : Blocks.WALL_BANNER.white(), FRONT));
            if (k.chance(0.5f)) k.set(A1 + 1, b + (b > 0 ? 1 : -1), 1, k.facing(Blocks.WALL_BANNER.black(), FRONT));
        }
        // The marquee: a canopy out over the doors, its edge a row of dead bulbs; the blade sign above it.
        for (int b = -4; b <= 4; b++) {
            k.set(A1 + 1, b, 3, SiteKit.slab(Blocks.SMOOTH_STONE_SLAB, true));
            k.set(A1 + 2, b, 3, SiteKit.slab(Blocks.SMOOTH_STONE_SLAB, true));
            k.set(A1 + 2, b, 4, Math.floorMod(b, 2) == 0 ? s(Blocks.REDSTONE_LAMP) : s(Blocks.CONCRETE.black()));
            if (Math.abs(b) == 4) k.set(A1 + 2, b, 4, s(Blocks.REDSTONE_LAMP));
        }
        for (int y = 5; y <= H + 3; y++) {
            k.set(A1 + 1, 0, y, (y % 2 == 0) ? s(Blocks.REDSTONE_LAMP) : s(Blocks.CONCRETE.black()));
            k.set(A1 + 2, 0, y, s(Blocks.CONCRETE.black()));
        }
        k.set(A1 + 2, 0, H + 4, s(Blocks.REDSTONE_LAMP));
        for (int b : new int[] {-4, 4}) k.set(A1 + 1, b, 4, s(Blocks.IRON_CHAIN));
        // The lobby: a red carpet worn through, the ticket booth, the counter (and the popcorn machine on it).
        for (int a = 3; a <= A1 - 1; a++) for (int b = -2; b <= 2; b++) if (!k.chance(0.25f)) k.set(a, b, 0, s(Blocks.CARPET.red()));
        k.walls(4, -4, 6, -3, 0, 2, s(Blocks.DARK_OAK_PLANKS), () -> s(Blocks.DARK_OAK_PLANKS));
        k.set(6, -3, 1, s(Blocks.GLASS_PANE));
        k.set(5, -3, 0, s(Blocks.AIR));
        k.set(5, -3, 1, s(Blocks.AIR));
        for (int a = 4; a <= 6; a++) for (int b = -4; b <= -3; b++) k.set(a, b, 3, SiteKit.slab(Blocks.DARK_OAK_SLAB, false));
        for (int b = 2; b <= 4; b++) {
            k.set(4, b, 0, k.facing(Blocks.BARREL, FRONT));
            k.set(4, b, 1, SiteKit.slab(Blocks.DARK_OAK_SLAB, false));
        }
        k.set(5, 4, 0, s(Blocks.DYED_TERRACOTTA.red()));
        k.set(5, 4, 1, s(Blocks.STAINED_GLASS.yellow()));
        // The projection booth over the lobby, its ladder, the projector at its window.
        for (int a = 3; a <= A1 - 1; a++) for (int b = -2; b <= 2; b++) k.set(a, b, 4, s(Blocks.DARK_OAK_PLANKS));
        for (int b = -2; b <= 2; b++) for (int y = 5; y <= 6; y++) k.set(2, b, y, s(Blocks.BRICKS));
        for (int a = 2; a <= A1 - 1; a++) for (int y = 4; y <= 6; y++) {
            k.set(a, -3, y, s(Blocks.BRICKS));
            k.set(a, 3, y, s(Blocks.BRICKS));
        }
        for (int b = -3; b <= 3; b++) k.set(2, b, 4, s(Blocks.BRICKS));
        k.set(2, 0, 5, s(Blocks.GLASS_PANE));
        k.set(2, 1, 5, s(Blocks.GLASS_PANE));
        for (int y = 0; y <= 4; y++) k.set(A1 - 1, 2, y, k.facing(Blocks.LADDER, BACK));
        k.set(3, 0, 5, s(Blocks.JUKEBOX));
        k.set(4, 1, 5, s(Blocks.BARREL));
        k.set(5, -2, 5, k.facing(Blocks.SPRUCE_SHELF, RIGHT));
        k.cobwebs(new int[][] {{3, -2, 6}, {5, 2, 6}, {6, -2, 6}}, 0.8f);
        // The auditorium: rows of red seats stepping up toward the booth, an aisle down the middle.
        for (int a = -1; a <= 1; a++) for (int b = -B + 1; b <= B - 1; b++) k.set(a, b, 0, s(Blocks.DARK_OAK_PLANKS));
        int[][] rows = {{-4, 0}, {-2, 0}, {0, 1}};
        for (int[] row : rows)
            for (int b = -B + 1; b <= B - 1; b++) {
                if (b == 0) continue;
                if (k.chance(0.12f)) continue;
                k.set(row[0], b, row[1], k.chance(0.1f) ? SiteKit.slab(Blocks.MANGROVE_SLAB, false) : k.stairs(Blocks.MANGROVE_STAIRS, FRONT, false));
            }
        for (int b = -B + 1; b <= B - 1; b++) k.set(-1, b, 0, s(Blocks.DARK_OAK_PLANKS));
        k.set(1, 0, 0, k.stairs(Blocks.DARK_OAK_STAIRS, BACK, false));
        // The stage, the screen, the curtains (torn), a valance over it all.
        for (int a = A0 + 1; a <= -6; a++) for (int b = -B + 1; b <= B - 1; b++) k.set(a, b, 0, s(Blocks.DARK_OAK_PLANKS));
        for (int b = -B + 1; b <= B - 1; b++) k.set(-5, b, 0, k.stairs(Blocks.DARK_OAK_STAIRS, BACK, false));
        for (int b = -3; b <= 3; b++) for (int y = 2; y <= 5; y++) k.set(A0 + 1, b, y, k.chance(0.06f) ? s(Blocks.WOOL.lightGray()) : s(Blocks.WOOL.white()));
        for (int b : new int[] {-4, 4})
            for (int y = 1; y <= 6; y++) {
                if (!k.chance(0.12f)) k.set(A0 + 1, b, y, s(Blocks.WOOL.red()));
                if (y >= 3 && !k.chance(0.35f)) k.set(A0 + 2, b, y, s(Blocks.WOOL.red()));
            }
        for (int b = -3; b <= 3; b++) if (!k.chance(0.2f)) k.set(A0 + 1, b, 6, s(Blocks.WOOL.red()));
        k.set(A0 + 3, 3, 1, s(Blocks.WOOL.red()));
        k.set(A0 + 3, -2, 1, s(Blocks.CARPET.red()));
        // A dead chandelier, bricks down from the roof's hole, webs everywhere.
        k.set(-2, 0, H - 1, s(Blocks.IRON_CHAIN));
        k.set(-2, 0, H - 2, s(Blocks.REDSTONE_LAMP));
        k.set(-3, 3, 1, s(Blocks.BRICKS));
        k.set(-4, 2, 1, SiteKit.slab(Blocks.BRICK_SLAB, false));
        k.cobwebs(new int[][] {{A0 + 1, -B + 1, 6}, {A0 + 1, B - 1, 6}, {-6, -4, 6}, {-2, 4, 6}, {1, -4, 6}, {-4, -4, 5}, {0, 4, 4}, {A0 + 2, 0, 1}}, 0.7f);
        k.door(-6, -B, 0, Blocks.DARK_OAK_DOOR, LEFT, false, false);
        k.path(A1 + 3, 0, A1 + 12, 0, s(Blocks.GRAVEL), 0.25f);
        k.settle();
        in.marks.put("door", k.at(A1, -1, 0));
        in.marks.put("object", k.at(3, 0, 5));
        in.marks.put("breach", k.at(4, 0, 5));
    }

    // --- Hakari: the storehouse with something under it ---

    /**
     * An old kura storehouse: dark tiled lower walls, white plaster above, a heavy tile roof with its ridge, small
     * shuttered windows, the big doors thrown open. Inside, rice bales and crates, a loft, a felt table and chairs where
     * people played, coins in the cracks; and in the middle the trapdoor in the floor, the bell hung over it from the
     * beam, and under it the old player that makes the music people hear at night.
     */
    static void buildStorehouse(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int A = 5, B = 4, H = 6;
        k.clearGrowth(-A - 3, -B - 3, A + 6, B + 4, 14);
        k.clear(-A - 1, -B - 1, 0, A + 3, B + 1, 12);
        k.foundation(-A, -B, A, B, -1, () -> s(Blocks.STONE_BRICKS), 6);
        for (int a = -A + 1; a < A; a++) for (int b = -B + 1; b < B; b++) k.set(a, b, -1, k.chance(0.1f) ? s(Blocks.DARK_OAK_PLANKS) : s(Blocks.SPRUCE_PLANKS));
        // Dark tile below, white plaster above, a dark band under the eaves.
        k.walls(-A, -B, A, B, 0, 1, s(Blocks.POLISHED_DEEPSLATE), () -> k.chance(0.15f) ? s(Blocks.CRACKED_DEEPSLATE_TILES) : s(Blocks.DEEPSLATE_TILES));
        k.walls(-A, -B, A, B, 2, H - 1, s(Blocks.CALCITE), () -> k.chance(0.08f) ? s(Blocks.DIORITE) : s(Blocks.CALCITE));
        k.walls(-A, -B, A, B, H, H, s(Blocks.DARK_OAK_PLANKS), () -> s(Blocks.DARK_OAK_PLANKS));
        // The roof: heavy tiles to a ridge capped at both ends.
        int ridge = k.gableRoof(-A, -B, A, B, H + 1, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, () -> s(Blocks.CALCITE), true, 0.03f);
        for (int a = -A - 1; a <= A + 1; a++) k.set(a, 0, ridge, s(Blocks.DEEPSLATE_TILES));
        k.set(-A - 1, 0, ridge + 1, s(Blocks.CHISELED_DEEPSLATE));
        k.set(A + 1, 0, ridge + 1, s(Blocks.CHISELED_DEEPSLATE));
        // Little windows with iron shutters swung open.
        for (int a : new int[] {-2, 2})
            for (int b : new int[] {-B, B}) {
                k.set(a, b, 3, s(Blocks.IRON_BARS));
                int ob = b > 0 ? 1 : -1;
                k.set(a - 1, b + ob, 3, k.trapdoor(Blocks.IRON_TRAPDOOR, b > 0 ? RIGHT : LEFT, true, false));
                k.set(a + 1, b + ob, 3, k.trapdoor(Blocks.IRON_TRAPDOOR, b > 0 ? RIGHT : LEFT, true, false));
            }
        k.set(-A, 0, H + 2, s(Blocks.IRON_BARS));
        // The doors: a heavy frame, the two leaves thrown back against the front, a tile hood over them.
        for (int b = -1; b <= 1; b++) for (int y = 0; y <= 2; y++) k.set(A, b, y, s(Blocks.AIR));
        for (int b : new int[] {-2, 2}) k.column(A, b, 0, 3, s(Blocks.DARK_OAK_LOG));
        for (int b = -2; b <= 2; b++) k.set(A, b, 3, k.log(Blocks.DARK_OAK_LOG, RIGHT));
        for (int b : new int[] {-3, 3}) for (int y = 0; y <= 2; y++) k.set(A + 1, b, y, s(Blocks.DARK_OAK_PLANKS));
        for (int b = -3; b <= 3; b++) k.set(A + 1, b, 4, k.stairs(Blocks.DEEPSLATE_TILE_STAIRS, BACK, false));
        // A loft over the back half, its ladder, rice bales up there and below.
        for (int a = -A + 1; a <= -1; a++) for (int b = -B + 1; b < B; b++) k.set(a, b, 3, s(Blocks.SPRUCE_PLANKS));
        for (int y = 0; y <= 3; y++) k.set(-1, B - 1, y, k.facing(Blocks.LADDER, LEFT));
        for (int a = -A + 1; a <= -A + 2; a++) for (int b = -B + 1; b <= -1; b++) {
            k.set(a, b, 0, s(Blocks.HAY_BLOCK));
            if (k.chance(0.7f)) k.set(a, b, 1, s(Blocks.HAY_BLOCK));
            if (k.chance(0.5f)) k.set(a, b, 4, s(Blocks.HAY_BLOCK));
        }
        k.set(-A + 1, 2, 0, k.facing(Blocks.BARREL, FRONT));
        k.set(-A + 1, 2, 1, k.facing(Blocks.BARREL, FRONT));
        k.set(-A + 2, 2, 0, s(Blocks.DECORATED_POT));
        k.set(-A + 1, 3, 4, s(Blocks.DECORATED_POT));
        k.set(A - 1, -B + 1, 0, k.facing(Blocks.BARREL, FRONT));
        k.set(A - 1, B - 1, 0, k.facing(Blocks.BARREL, FRONT));
        k.set(A - 2, B - 1, 0, s(Blocks.HAY_BLOCK));
        // Where they played: a felt table, its chairs, coins in the cracks.
        for (int b = -3; b <= -2; b++) {
            k.set(2, b, 0, s(Blocks.DARK_OAK_FENCE));
            k.set(2, b, 1, s(Blocks.CARPET.green()));
        }
        k.set(3, -3, 0, k.stairs(Blocks.SPRUCE_STAIRS, FRONT, false));
        k.set(1, -2, 0, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
        k.set(2, -1, 0, k.stairs(Blocks.SPRUCE_STAIRS, RIGHT, false));
        for (int[] c : new int[][] {{1, 2}, {3, -1}, {-2, -2}, {2, 3}}) k.set(c[0], c[1], 0, s(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE));
        // The trapdoor in the middle of the floor, the shaft under it, and what's at the bottom.
        k.set(0, 0, -1, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, FRONT, false, true));
        for (int y = -2; y >= -5; y--) {
            for (int[] w : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (!k.solid(w[0], w[1], y)) k.set(w[0], w[1], y, s(Blocks.STONE_BRICKS));
            k.set(0, 0, y, y > -5 ? k.facing(Blocks.LADDER, FRONT) : s(Blocks.JUKEBOX));
        }
        k.set(0, 0, -6, s(Blocks.STONE_BRICKS));
        // The bell hangs over it from the beam.
        for (int b = -B + 1; b < B; b++) k.set(0, b, 4, k.log(Blocks.DARK_OAK_LOG, RIGHT));
        k.set(0, 0, 3, s(Blocks.BELL).setValue(BlockStateProperties.HORIZONTAL_FACING, k.dir(FRONT)).setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
        k.set(2, 1, 3, SiteKit.hanging(Blocks.LANTERN));
        k.cobwebs(new int[][] {{A - 1, -B + 1, H - 1}, {A - 1, B - 1, H - 1}, {-A + 1, -B + 1, 7}, {0, 2, H}, {1, -2, H}}, 0.7f);
        // Outside: two stone lanterns by the door, a handcart, bales under the eaves, a gravel yard.
        CountrySites.stoneLantern(k, A + 3, -3);
        CountrySites.stoneLantern(k, A + 3, 3);
        k.onGround(A + 2, -5, s(Blocks.HAY_BLOCK));
        k.onGround(A + 1, -5, s(Blocks.HAY_BLOCK));
        int cy = k.groundY(-1, B + 2);
        k.set(-2, B + 2, cy, SiteKit.slab(Blocks.SPRUCE_SLAB, true));
        k.set(-1, B + 2, cy, SiteKit.slab(Blocks.SPRUCE_SLAB, true));
        k.set(0, B + 2, cy, k.stairs(Blocks.SPRUCE_STAIRS, FRONT, true));
        k.set(-1, B + 3, cy, k.trapdoor(Blocks.DARK_OAK_TRAPDOOR, RIGHT, true, false));
        k.set(-1, B + 1, cy, k.trapdoor(Blocks.DARK_OAK_TRAPDOOR, LEFT, true, false));
        for (int a = A + 1; a <= A + 6; a++) for (int b = -2; b <= 2; b++) if (!k.chance(0.2f)) k.ground(a, b, s(Blocks.GRAVEL));
        k.settle();
        in.marks.put("door", k.at(A, 0, 0));
        in.marks.put("object", k.at(0, 0, 3));
        in.marks.put("breach", k.at(0, 0, 1));
    }

    // --- Ryu: the crater ---

    /**
     * A blast crater: a bowl glassed black at the bottom and torn to rubble up its sides, an earth rim thrown up round
     * it, scorch marks raying out over the grass, trees knocked flat pointing away from it, the ground still hot in
     * places; and at its centre the black shard everything struck, leaning out of the glass.
     */
    static void buildCrater(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int R = 9, D = 5;
        k.clearGrowth(-R - 4, -R - 4, R + 4, R + 4, 20);
        double[] wob = new double[8];
        for (int i = 0; i < wob.length; i++) wob[i] = k.r.nextDouble() * 0.16 - 0.08;
        int rays = 9;
        double[] ray = new double[rays];
        for (int i = 0; i < rays; i++) ray[i] = (i + k.r.nextDouble() * 0.6) * Math.PI * 2 / rays;
        for (int a = -R - 10; a <= R + 10; a++)
            for (int b = -R - 10; b <= R + 10; b++) {
                double ang = Math.atan2(b, a);
                int sector = (int) Math.floorMod((int) Math.floor((ang + Math.PI) / (Math.PI * 2) * wob.length), wob.length);
                double d = Math.hypot(a, b) * (1 + wob[sector]);
                if (d <= R) {
                    int depth = (int) Math.round(D * (1 - (d / R) * (d / R)));
                    for (int y = 8; y >= -depth; y--) if (!k.get(a, b, y).isAir()) k.set(a, b, y, s(Blocks.AIR));
                    BlockState floor = d < 2.5 ? (k.chance(0.25f) ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN))
                            : d < 5 ? s(k.pick(Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.TINTED_GLASS, d < 3.5 ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE))
                            : d < 7.5 ? s(k.pick(Blocks.TUFF, Blocks.BLACKSTONE, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL, Blocks.BASALT))
                            : s(k.pick(Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.TUFF, Blocks.COBBLESTONE));
                    k.set(a, b, -depth - 1, floor);
                    if (!k.solid(a, b, -depth - 2)) k.set(a, b, -depth - 2, s(Blocks.STONE));
                } else if (d <= R + 2.6) {
                    // The rim: earth thrown up over the grass.
                    int h = (int) Math.round(1.8 * (1 - (d - R) / 2.6) + k.r.nextDouble() * 0.7);
                    int y0 = k.groundY(a, b);
                    for (int y = y0; y < y0 + h; y++) k.set(a, b, y, s(k.pick(Blocks.COARSE_DIRT, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.DIRT, Blocks.COBBLESTONE)));
                    if (h <= 0) k.ground(a, b, s(Blocks.COARSE_DIRT));
                    if (h > 0 && k.chance(0.3f)) k.set(a, b, y0 + h - 1, s(Blocks.ROOTED_DIRT));
                } else {
                    // Scorch marks raying out.
                    double best = 9;
                    for (double r0 : ray) best = Math.min(best, Math.abs(Math.atan2(Math.sin(ang - r0), Math.cos(ang - r0))));
                    double reach = (d - R - 2.6) / 8.0;
                    if (best < 0.14 * (1.2 - reach) && k.chance((float) (1 - reach))) k.ground(a, b, s(k.pick(Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.TUFF)));
                    else if (k.chance((float) (0.12 * (1 - reach)))) k.onGround(a, b, s(Blocks.DEAD_BUSH));
                }
            }
        // Trees knocked flat, their stumps left standing.
        for (int i = 0; i < 7; i++) {
            double ang = (i + k.r.nextDouble() * 0.7) * Math.PI * 2 / 7;
            double ca = Math.cos(ang), cb = Math.sin(ang);
            int sa = (int) Math.round(ca * (R + 4)), sb = (int) Math.round(cb * (R + 4));
            int axis = Math.abs(ca) > Math.abs(cb) ? FRONT : RIGHT;
            int y = k.groundY(sa, sb);
            k.set(sa, sb, y, s(Blocks.DARK_OAK_LOG));
            if (k.chance(0.5f)) k.set(sa, sb, y + 1, s(Blocks.STRIPPED_DARK_OAK_LOG));
            int len = k.rand(3, 5);
            for (int j = 2; j < 2 + len; j++) {
                int ta = sa + (int) Math.round(ca * j), tb = sb + (int) Math.round(cb * j);
                k.onGround(ta, tb, k.log(Blocks.DARK_OAK_LOG, axis));
            }
        }
        // The shard: a leaning spire of black glass and stone, splinters round its foot.
        int floorY = -D;
        int la = k.pick(-1, 1), lb = k.pick(-1, 0, 1);
        int[][] spire = {{0, 0, 0}, {0, 0, 1}, {0, 0, 2}, {0, 0, 3}, {la, lb, 4}, {la, lb, 5}, {1, 0, 0}, {0, 1, 0}, {-1, 0, 1}};
        for (int[] c : spire) k.set(c[0], c[1], floorY + c[2], c[2] == 1 || c[2] == 4 ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN));
        k.set(la, lb, floorY + 6, s(Blocks.STAINED_GLASS.black()));
        for (int[] c : new int[][] {{2, -1}, {-2, 1}, {1, 2}, {-1, -2}, {3, 1}}) {
            int depth = (int) Math.round(D * (1 - (Math.hypot(c[0], c[1]) / R) * (Math.hypot(c[0], c[1]) / R)));
            k.set(c[0], c[1], -depth, k.chance(0.5f) ? s(Blocks.OBSIDIAN) : s(Blocks.STAINED_GLASS.black()));
        }
        k.settle();
        in.marks.put("object", k.at(0, 0, floorY + 3));
        int bd = (int) Math.round(D * (1 - (2.0 / R) * (2.0 / R)));
        in.marks.put("breach", k.at(-2, -1, -bd));
    }

    // --- Yuta: the chapel ---

    /**
     * A small white chapel: a bell tower at the front with its spire and cross, buttresses down the sides between tall
     * stained-glass windows (some of them broken), a steep dark roof, a rose window over the altar. Inside, pews either
     * side of a white runner, the chancel up its step, candles on the altar and lilies in pots; outside, the graveyard
     * behind its railing and a dead tree.
     */
    static void buildChapel(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int A0 = -6, A1 = 5, B = 4, H = 6;
        k.clearGrowth(A0 - 3, -B - 4, A1 + 10, B + 10, 20);
        k.clear(A0 - 1, -B - 2, 0, A1 + 4, B + 2, 16);
        k.foundation(A0, -B - 1, A1 + 4, B + 1, -1, () -> s(Blocks.STONE_BRICKS), 6);
        for (int a = A0 + 1; a < A1; a++) for (int b = -B + 1; b < B; b++) k.set(a, b, -1, (a + b) % 2 == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.STONE_BRICKS));
        // The nave: white walls on a stone course, stone quoins.
        k.walls(A0, -B, A1, B, 0, 0, s(Blocks.STONE_BRICKS), () -> k.wornBricks());
        k.walls(A0, -B, A1, B, 1, H, s(Blocks.STONE_BRICKS), () -> k.chance(0.07f) ? s(Blocks.MOSSY_STONE_BRICKS) : s(Blocks.CALCITE));
        // Buttresses between the windows.
        for (int a : new int[] {A0, -2, 2, A1})
            for (int b : new int[] {-B - 1, B + 1}) {
                int in2 = b > 0 ? LEFT : RIGHT;
                k.column(a, b, 0, 2, k.wornBricks());
                k.set(a, b, 3, k.stairs(Blocks.STONE_BRICK_STAIRS, in2, false));
                k.set(a, b + Integer.signum(b), 0, k.stairs(Blocks.STONE_BRICK_STAIRS, in2, false));
            }
        // Tall windows of coloured glass, some panes gone.
        Block[] glass = {Blocks.STAINED_GLASS_PANE.white(), Blocks.STAINED_GLASS_PANE.lightBlue(), Blocks.STAINED_GLASS_PANE.lightBlue(), Blocks.STAINED_GLASS_PANE.yellow()};
        for (int a : new int[] {-4, 0, 3})
            for (int b : new int[] {-B, B})
                for (int y = 1; y <= 4; y++) k.set(a, b, y, k.chance(0.22f) ? s(Blocks.AIR) : s(glass[y - 1]));
        // The rose window over the altar.
        k.set(A0, 0, 4, s(Blocks.STAINED_GLASS_PANE.yellow()));
        for (int[] c : new int[][] {{1, 4}, {-1, 4}, {0, 3}, {0, 5}}) k.set(A0, c[0], c[1], s(Blocks.STAINED_GLASS_PANE.red()));
        for (int[] c : new int[][] {{1, 5}, {-1, 5}, {1, 3}, {-1, 3}}) k.set(A0, c[0], c[1], s(Blocks.STAINED_GLASS_PANE.blue()));
        // The roof: steep, dark, its gables white.
        k.gableRoof(A0, -B, A1, B, H + 1, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, () -> s(Blocks.CALCITE), true, 0.03f);
        // The tower at the front: the way in through its foot, the bell up in its open top, a spire and a cross.
        int T = A1 + 2, TH = 11;
        k.walls(A1 + 1, -1, A1 + 3, 1, 0, TH, s(Blocks.STONE_BRICKS), () -> k.chance(0.06f) ? s(Blocks.MOSSY_STONE_BRICKS) : s(Blocks.CALCITE));
        for (int a = A1 + 1; a <= A1 + 3; a++) for (int b = -1; b <= 1; b++) k.set(a, b, TH, s(Blocks.STONE_BRICKS));
        k.set(T, 0, -1, s(Blocks.STONE_BRICKS));
        for (int y = 0; y <= 1; y++) {
            k.set(A1, 0, y, s(Blocks.AIR));
            k.set(A1 + 1, 0, y, s(Blocks.AIR));
            k.set(T, 0, y, s(Blocks.AIR));
        }
        k.door(A1 + 3, 0, 0, Blocks.SPRUCE_DOOR, FRONT, true, false);
        k.set(A1 + 3, 0, 2, k.stairs(Blocks.STONE_BRICK_STAIRS, FRONT, true));
        k.set(T, 0, 3, s(Blocks.AIR));
        for (int[] c : new int[][] {{A1 + 3, 0}, {A1 + 1, 0}, {T, 1}, {T, -1}}) for (int y = 8; y <= 9; y++) k.set(c[0], c[1], y, s(Blocks.AIR));
        for (int y = 2; y <= 9; y++) k.set(T, 0, y, s(Blocks.AIR));
        k.set(T, 0, 10, s(Blocks.DARK_OAK_PLANKS));
        k.set(T, 0, 9, s(Blocks.BELL).setValue(BlockStateProperties.HORIZONTAL_FACING, k.dir(FRONT)).setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
        int top = k.pyramidRoof(T, 0, 2, TH + 1, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), s(Blocks.DEEPSLATE_TILES));
        k.column(T, 0, top + 1, top + 3, s(Blocks.DARK_OAK_FENCE));
        k.set(T, -1, top + 2, s(Blocks.DARK_OAK_FENCE));
        k.set(T, 1, top + 2, s(Blocks.DARK_OAK_FENCE));
        // Inside: the white runner, the pews, the chancel up its step, the altar and its candles, lilies.
        for (int a = A0 + 4; a < A1; a++) if (!k.chance(0.15f)) k.set(a, 0, 0, k.chance(0.15f) ? s(Blocks.CARPET.lightGray()) : s(Blocks.CARPET.white()));
        for (int a = -2; a <= 3; a += 2)
            for (int b = -B + 1; b <= B - 1; b++) {
                if (b == 0 || k.chance(0.08f)) continue;
                k.set(a, b, 0, k.stairs(Blocks.SPRUCE_STAIRS, FRONT, false));
            }
        for (int a = A0 + 1; a <= A0 + 2; a++) for (int b = -B + 1; b < B; b++) k.set(a, b, 0, s(Blocks.POLISHED_ANDESITE));
        for (int b = -B + 1; b < B; b++) k.set(A0 + 3, b, 0, k.stairs(Blocks.POLISHED_ANDESITE_STAIRS, BACK, false));
        k.set(A0 + 1, 0, 1, s(Blocks.CHISELED_QUARTZ_BLOCK));
        k.set(A0 + 1, 0, 2, SiteKit.candles(3));
        for (int b : new int[] {-2, 2}) {
            k.set(A0 + 1, b, 1, s(Blocks.POLISHED_BLACKSTONE_WALL));
            k.set(A0 + 1, b, 2, SiteKit.candles(1));
            k.set(A0 + 2, b / 2, 1, s(Blocks.POTTED_LILY_OF_THE_VALLEY));
        }
        k.set(A0 + 2, 2, 1, k.facing(Blocks.LECTERN, FRONT));
        k.cobwebs(new int[][] {{A0 + 1, -B + 1, H}, {A0 + 1, B - 1, H}, {A1 - 1, -B + 1, H}, {A1 - 1, B - 1, H}, {0, 2, H + 2}, {-3, -2, H + 1}}, 0.7f);
        // The graveyard at the side, behind an iron railing; a dead tree over it.
        int G0 = -5, G1 = 3, GB0 = B + 3, GB1 = B + 9;
        for (int a = G0; a <= G1; a++)
            for (int b = GB0; b <= GB1; b++) {
                boolean edge = a == G0 || a == G1 || b == GB0 || b == GB1;
                if (edge && !(b == GB0 && a == 0) && !k.chance(0.08f)) k.onGround(a, b, s(Blocks.IRON_BARS));
            }
        for (int a = G0 + 1; a <= G1 - 2; a += 3)
            for (int b = GB0 + 2; b <= GB1 - 1; b += 2) {
                int y = k.groundY(a, b);
                switch (k.r.nextInt(4)) {
                    case 0 -> k.set(a, b, y, s(Blocks.STONE_BRICK_WALL));
                    case 1 -> k.set(a, b, y, s(Blocks.MOSSY_COBBLESTONE_WALL));
                    case 2 -> {
                        k.set(a, b, y, s(Blocks.POLISHED_ANDESITE));
                        k.set(a, b, y + 1, SiteKit.slab(Blocks.ANDESITE_SLAB, false));
                    }
                    default -> {
                        k.set(a, b, y, s(Blocks.SPRUCE_FENCE));
                        k.set(a, b, y + 1, s(Blocks.SPRUCE_FENCE));
                    }
                }
                k.ground(a + 1, b, s(k.pick(Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT)));
                k.ground(a + 2, b, s(k.pick(Blocks.COARSE_DIRT, Blocks.PODZOL)));
            }
        int ty = k.groundY(G0 - 2, GB1);
        for (int y = 0; y < 5; y++) k.set(G0 - 2, GB1, ty + y, s(Blocks.DARK_OAK_LOG));
        k.set(G0 - 1, GB1, ty + 3, k.log(Blocks.DARK_OAK_LOG, FRONT));
        k.set(G0 - 2, GB1 - 1, ty + 4, k.log(Blocks.DARK_OAK_LOG, RIGHT));
        k.set(G0 - 3, GB1, ty + 5, s(Blocks.DARK_OAK_FENCE));
        k.path(A1 + 4, 0, A1 + 13, 0, s(Blocks.GRAVEL), 0.2f);
        k.settle();
        in.marks.put("door", k.at(A1 + 3, 0, 0));
        in.marks.put("object", k.at(A0 + 1, 0, 1));
        in.marks.put("breach", k.at(A0 + 3, 0, 1));
    }
}
