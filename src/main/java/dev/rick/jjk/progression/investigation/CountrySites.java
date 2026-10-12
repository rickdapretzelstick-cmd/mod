package dev.rick.jjk.progression.investigation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static dev.rick.jjk.progression.investigation.SiteKit.BACK;
import static dev.rick.jjk.progression.investigation.SiteKit.FRONT;
import static dev.rick.jjk.progression.investigation.SiteKit.LEFT;
import static dev.rick.jjk.progression.investigation.SiteKit.RIGHT;

/**
 * The countryside's incident sites ({@link Sites} kinds), each drawn in its site's own frame ({@link SiteKit}) so it
 * stands whichever way it faces:
 * <ul>
 *   <li>the cliff: a wooden lookout built out over the drop on stilts, its far railing broken through, candles and
 *   flowers left at the gap, stone lanterns and a bench where the path comes out of the trees;</li>
 *   <li>the pasture: a paddock smashed open on one side, a field shelter with its hay, the trough, a scarecrow, and in
 *   the middle the churned ground, the bones and what's left of the flock, dragged out through the gap;</li>
 *   <li>the mine: a timber portal set into the hillside under an earth bank, boards torn off it, rails out to the end
 *   stop, a spoil heap, the old tool shed, and the tunnel sloping down into the dark;</li>
 *   <li>the house: an abandoned timber cottage on a stone footing, shuttered windows, a porch, a chimney, a sagging
 *   roof fallen in at one corner, its bed still made, and its garden and dry well gone to weeds.</li>
 * </ul>
 * Nothing is labelled: the report and the residue are how a player finds the place.
 */
final class CountrySites {
    private CountrySites() {}

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    // --- The cliff ---

    static void buildCliff(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        BlockState fence = s(Blocks.SPRUCE_FENCE), post = s(Blocks.STRIPPED_SPRUCE_LOG);
        k.clearGrowth(-7, -5, 3, 5, 8);
        k.clear(-2, -2, 0, 3, 2, 4);
        // The deck: two back on the land, two out over the drop, framed in logs.
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                boolean rim = Math.abs(b) == 2 || a == 2;
                k.set(a, b, -1, rim ? k.log(Blocks.STRIPPED_SPRUCE_LOG, Math.abs(b) == 2 ? FRONT : RIGHT)
                        : k.chance(0.18f) ? s(Blocks.DARK_OAK_PLANKS) : s(Blocks.SPRUCE_PLANKS));
            }
        // A board gone right by the gap.
        k.set(2, -1, -1, s(Blocks.AIR));
        // Stilts down to the foot of the cliff, braced under the end.
        for (int[] p : new int[][] {{2, -2}, {2, 2}, {0, -2}, {0, 2}}) k.pillarDown(p[0], p[1], -2, s(Blocks.SPRUCE_LOG), 40);
        for (int b = 0; b <= 1; b++) k.soft(2, b, -2, fence);
        k.soft(1, -2, -2, fence);
        k.soft(1, 2, -2, fence);
        // The railing, broken through at the far end; a board hangs off the edge below the gap.
        for (int b : new int[] {-2, 2}) {
            k.set(2, b, 0, post);
            k.set(-2, b, 0, post);
            for (int a = -1; a <= 1; a++) k.set(a, b, 0, fence);
        }
        k.set(2, 1, 0, fence);
        k.set(3, -1, -1, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, FRONT, true, false));
        // What people left at the gap.
        k.set(1, -1, 0, SiteKit.candles(3));
        k.set(1, 1, 0, s(Blocks.POTTED_WHITE_TULIP));
        k.set(1, 0, 0, s(Blocks.POTTED_POPPY));
        k.set(0, 1, 0, SiteKit.candles(1));
        k.set(-1, -1, 0, s(Blocks.POTTED_LILY_OF_THE_VALLEY));
        // Where the path comes out: two stone lanterns, a bench looking out over it all.
        stoneLantern(k, -3, -3);
        stoneLantern(k, -3, 3);
        int by = k.groundY(-5, 2);
        for (int b = 1; b <= 2; b++) k.soft(-5, b, by, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
        k.soft(-5, 0, by, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, LEFT, true, false));
        k.soft(-5, 3, by, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, RIGHT, true, false));
        k.path(-3, 0, -15, -1, s(Blocks.DIRT_PATH), 0.15f);
        for (int i = 0; i < 10; i++) k.onGround(k.rand(-9, -3), k.rand(-5, 5), s(k.pick(Blocks.OXEYE_DAISY, Blocks.POPPY, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.FERN)));
        k.settle();
        // The breach hangs right at the lip, where it happened (the site is the ground level there).
        in.marks.put("breach", in.site.immutable());
    }

    /** A little stone lantern standing on the land (its candle long out). */
    static void stoneLantern(SiteKit k, int a, int b) {
        int y = k.groundY(a, b);
        k.set(a, b, y, s(Blocks.POLISHED_ANDESITE));
        k.set(a, b, y + 1, s(Blocks.ANDESITE_WALL));
        k.set(a, b, y + 2, SiteKit.candles(2));
        k.set(a, b, y + 3, SiteKit.slab(Blocks.STONE_BRICK_SLAB, false));
    }

    // --- The pasture ---

    static void buildPasture(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int A = 7, B = 5;
        BlockState fence = s(Blocks.SPRUCE_FENCE), post = s(Blocks.STRIPPED_SPRUCE_LOG);
        k.clearGrowth(-A - 2, -B - 7, A + 2, B + 2, 10);
        // The paddock fence: log posts, open gate at the front, smashed through on the left.
        for (int a = -A; a <= A; a++)
            for (int b = -B; b <= B; b++) {
                if (Math.abs(a) != A && Math.abs(b) != B) continue;
                if (b == -B && a >= -3 && a <= 0) continue;
                int y = k.groundY(a, b);
                if (a == A && b == 0) {
                    k.set(a, b, y, k.facing(Blocks.SPRUCE_FENCE_GATE, FRONT).setValue(BlockStateProperties.OPEN, true));
                    continue;
                }
                if (Math.abs(a) == A && Math.abs(b) == B) {
                    k.set(a, b, y, post);
                    k.set(a, b, y + 1, fence);
                } else if (!k.chance(0.05f)) {
                    k.set(a, b, y, fence);
                }
            }
        // The rails it went through, thrown out into the grass.
        k.onGround(-2, -B - 2, k.log(Blocks.STRIPPED_SPRUCE_LOG, FRONT));
        k.onGround(-1, -B - 2, k.log(Blocks.STRIPPED_SPRUCE_LOG, FRONT));
        k.onGround(0, -B - 4, k.log(Blocks.STRIPPED_SPRUCE_LOG, RIGHT));
        k.onGround(-4, -B - 1, fence);
        // A lantern on the gate post.
        int gy = k.groundY(A, -1);
        k.set(A, -1, gy, post);
        k.set(A, -1, gy + 1, post);
        k.set(A, -1, gy + 2, s(Blocks.LANTERN));
        // The field shelter in the back corner: four posts, a back wall, a roof with a lip, hay under it.
        int sy = k.groundY(-5, 2);
        k.foundation(-6, 1, -4, 4, sy - 1, () -> s(Blocks.COARSE_DIRT), 4);
        for (int[] p : new int[][] {{-6, 1}, {-6, 4}, {-4, 1}, {-4, 4}}) k.column(p[0], p[1], sy, sy + 2, s(Blocks.SPRUCE_LOG));
        for (int b = 2; b <= 3; b++) k.column(-6, b, sy, sy + 2, s(Blocks.SPRUCE_PLANKS));
        for (int b = 0; b <= 5; b++) {
            k.set(-7, b, sy + 3, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
            for (int a = -6; a <= -3; a++) if (!k.chance(0.07f)) k.set(a, b, sy + 3, SiteKit.slab(Blocks.SPRUCE_SLAB, false));
        }
        k.set(-5, 2, sy, s(Blocks.HAY_BLOCK));
        k.set(-5, 3, sy, s(Blocks.HAY_BLOCK));
        k.set(-5, 3, sy + 1, s(Blocks.HAY_BLOCK));
        k.set(-4, 3, sy, s(Blocks.HAY_BLOCK));
        k.onGround(-2, 3, s(Blocks.HAY_BLOCK));
        // The trough along the right fence.
        for (int a = 2; a <= 3; a++) {
            int y = k.groundY(a, B - 1);
            k.set(a, B - 1, y, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        }
        // A scarecrow, knocked askew.
        int cy = k.groundY(3, -2);
        k.set(3, -2, cy, fence);
        k.set(3, -2, cy + 1, fence);
        k.set(3, -3, cy + 1, fence);
        k.set(3, -1, cy + 1, fence);
        k.set(3, -2, cy + 2, k.facing(Blocks.CARVED_PUMPKIN, k.pick(FRONT, LEFT, RIGHT)));
        // Where it happened: churned ground, the bones and wool of the flock, dragged off through the gap.
        for (int a = -4; a <= 4; a++)
            for (int b = -4; b <= 4; b++) {
                double d = Math.sqrt(a * a + b * b) + k.r.nextDouble() * 1.6;
                if (d < 3.8) k.ground(a, b, s(k.pick(Blocks.COARSE_DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.PODZOL)));
            }
        k.ground(1, -1, k.log(Blocks.BONE_BLOCK, FRONT));
        k.ground(-1, 1, k.log(Blocks.BONE_BLOCK, RIGHT));
        k.onGround(-1, -2, k.log(Blocks.BONE_BLOCK, RIGHT));
        k.onGround(0, 1, s(Blocks.WOOL.white()));
        k.onGround(1, 1, s(Blocks.WOOL.white()));
        k.onGround(2, 2, s(Blocks.WOOL.lightGray()));
        k.onGround(2, 0, s(Blocks.SKELETON_SKULL).setValue(BlockStateProperties.ROTATION_16, k.r.nextInt(16)));
        k.onGround(-3, 2, s(Blocks.SKELETON_SKULL).setValue(BlockStateProperties.ROTATION_16, k.r.nextInt(16)));
        k.path(0, -1, -1, -B - 7, s(Blocks.COARSE_DIRT), 0.1f);
        k.path(1, -1, 0, -B - 6, s(Blocks.ROOTED_DIRT), 0.4f);
        // The grass has gone long and dry since.
        for (int i = 0; i < 26; i++) k.onGround(k.rand(-A + 1, A - 1), k.rand(-B + 1, B - 1), s(k.pick(Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS, Blocks.SHORT_GRASS, Blocks.DEAD_BUSH)));
        k.settle();
        in.marks.put("breach", in.site.immutable());
    }

    // --- The mine ---

    /**
     * The mine: the tunnel (nine steps sloping down a block every two, three wide and three high, timbered every third
     * step, rails down it, dark rock at the end where the trigger waits), set into the hill under an earth bank, and the
     * workings outside its mouth. The tunnel's shape is fixed: {@link Sites#mineEnd} is where it ends.
     */
    static void buildMine(ServerLevel l, Incident in) {
        SiteKit k = new SiteKit(l, in.site, SiteKit.facing(in), in.id.hashCode());
        // The earth bank over the first steps, so the portal sits in the hillside rather than standing out of it.
        for (int a = 0; a <= 8; a++)
            for (int b = -5; b <= 5; b++) {
                int want = 5 - a / 2 - Math.max(0, Math.abs(b) - 2);
                int y0 = k.groundY(a, b);
                for (int y = y0; y < want; y++) {
                    if (Math.abs(b) <= 2 && y >= -a / 2 - 1 && y <= -a / 2 + 3) continue; // the tunnel and its frame
                    if (!k.get(a, b, y).isAir() && !k.get(a, b, y).canBeReplaced()) continue;
                    k.set(a, b, y, y == want - 1 ? s(Blocks.GRASS_BLOCK) : s(Blocks.DIRT));
                }
            }
        for (int st = 0; st < 9; st++) {
            int drop = st / 2;
            for (int w = -1; w <= 1; w++) {
                for (int y = 0; y < 3; y++) k.set(st, w, y - drop, s(Blocks.AIR));
                k.set(st, w, -1 - drop, (st + w) % 3 == 0 ? s(Blocks.GRAVEL) : s(Blocks.STONE));
            }
            if (st % 3 == 0) {
                // Timber frame.
                for (int y = 0; y < 3; y++) {
                    k.set(st, 2, y - drop, s(Blocks.STRIPPED_OAK_LOG));
                    k.set(st, -2, y - drop, s(Blocks.STRIPPED_OAK_LOG));
                }
                for (int w = -2; w <= 2; w++) k.set(st, w, 3 - drop, k.log(Blocks.DARK_OAK_LOG, RIGHT));
            } else {
                for (int w = -2; w <= 2; w += 4)
                    for (int y = 0; y < 3; y++) if (k.get(st, w, y - drop).isAir()) k.set(st, w, y - drop, k.chance(0.3f) ? s(Blocks.ANDESITE) : s(Blocks.STONE));
                for (int w = -1; w <= 1; w++) if (k.get(st, w, 3 - drop).isAir()) k.set(st, w, 3 - drop, k.chance(0.3f) ? s(Blocks.COBBLESTONE) : s(Blocks.STONE));
            }
            if (st == 4 || st == 7) k.set(st, -1, 2 - drop, s(Blocks.COBWEB));
            if (st <= 7) k.set(st, 0, -drop, s(Blocks.RAIL));
        }
        k.set(3, 0, 1, SiteKit.hanging(Blocks.LANTERN));
        // The far end is dark rock: the tunnel "goes on" only for whoever is meant to follow it.
        for (int w = -1; w <= 1; w++) for (int y = 0; y < 3; y++) k.set(9, w, y - 4, s(Blocks.DEEPSLATE));
        // The portal: heavier posts and a lintel out front, a little roof over it, boards torn off.
        for (int b : new int[] {-2, 2}) k.column(-1, b, 0, 3, s(Blocks.DARK_OAK_LOG));
        for (int b = -3; b <= 3; b++) {
            k.set(-1, b, 4, k.log(Blocks.DARK_OAK_LOG, RIGHT));
            k.set(-1, b, 5, SiteKit.slab(Blocks.SPRUCE_SLAB, false));
            k.set(-2, b, 4, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
        }
        k.foundation(-1, -3, -1, 3, -1, () -> s(Blocks.COBBLESTONE), 6);
        k.set(-1, -1, 2, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, BACK, true, false));
        k.set(-1, 1, 0, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, BACK, true, false));
        k.set(0, 0, 0, s(Blocks.RAIL));
        k.set(-1, 0, 3, SiteKit.hanging(Blocks.LANTERN));
        // Rails out of the mouth on a gravel bed, to a log end stop.
        for (int a = -1; a >= -6; a--) {
            k.foundation(a, 0, a, 0, -1, () -> s(Blocks.GRAVEL), 6);
            k.clear(a, 0, 0, a, 0, 2);
            k.set(a, 0, 0, s(Blocks.RAIL));
            if (k.chance(0.5f)) k.ground(a, k.pick(-1, 1), s(Blocks.GRAVEL));
        }
        k.foundation(-7, -1, -7, 1, -1, () -> s(Blocks.GRAVEL), 6);
        for (int b = -1; b <= 1; b++) k.set(-7, b, 0, k.log(Blocks.SPRUCE_LOG, RIGHT));
        // The spoil heap, to the right.
        for (int a = -8; a <= -2; a++)
            for (int b = 4; b <= 9; b++) {
                double d = Math.hypot((a + 5) / 1.4, (b - 6.5) / 1.2);
                int h = (int) Math.round(2.6 - d + k.r.nextDouble() * 0.6);
                int y0 = k.groundY(a, b);
                for (int y = y0; y < y0 + h; y++) k.set(a, b, y, s(k.pick(Blocks.GRAVEL, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.COARSE_DIRT)));
            }
        // The tool shed, to the left: plank walls on a log frame, a lean roof, its door off toward the rails.
        int y0 = k.groundY(-5, -6);
        k.clearGrowth(-8, -10, -2, -3, 8);
        k.foundation(-7, -8, -3, -5, y0 - 1, () -> s(Blocks.COBBLESTONE), 5);
        k.clear(-7, -8, y0, -3, -5, y0 + 4);
        k.walls(-7, -8, -3, -5, y0, y0 + 2, s(Blocks.SPRUCE_LOG), () -> k.chance(0.08f) ? s(Blocks.AIR) : s(Blocks.SPRUCE_PLANKS));
        k.gableRoof(-7, -8, -3, -5, y0 + 3, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, () -> s(Blocks.SPRUCE_PLANKS), true, 0.06f);
        k.door(-5, -5, y0, Blocks.SPRUCE_DOOR, RIGHT, true, false);
        k.set(-3, -7, y0 + 1, s(Blocks.GLASS_PANE));
        k.set(-6, -7, y0, k.facing(Blocks.BARREL, FRONT));
        k.set(-6, -6, y0, s(Blocks.CRAFTING_TABLE));
        k.set(-4, -7, y0, s(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        k.set(-5, -7, y0 + 2, SiteKit.hanging(Blocks.LANTERN));
        k.cobwebs(new int[][] {{-6, -7, y0 + 2}, {-4, -6, y0 + 2}}, 0.8f);
        // Crates by the portal; a coarse track worn out to the trees.
        k.onGround(-2, 3, s(Blocks.BARREL));
        k.onGround(-3, 3, s(Blocks.BARREL));
        k.onGround(-2, -3, s(Blocks.BARREL));
        int cy = k.groundY(-3, 3);
        k.set(-3, 3, cy, s(Blocks.BARREL));
        k.path(-8, 2, -16, 3, s(Blocks.COARSE_DIRT), 0.25f);
        k.settle();
    }

    // --- The house ---

    /** The house's walls run four either way along it from its middle, three to either side. */
    static final int HOUSE_HALF = 4, HOUSE_SIDE = 3;

    /**
     * The empty house: a timber cottage on a stone footing, its door hanging open, windows broken behind their
     * shutters, a porch, a chimney, its roof fallen in at the back corner, cobwebs in the rafters, and the bed nobody
     * has slept in since. Marks its {@code door} (the threshold) and its {@code bed} (where the incident was set off).
     */
    static void buildHouse(ServerLevel l, Incident in) {
        SiteKit k = SiteKit.of(l, in);
        int A = HOUSE_HALF, B = HOUSE_SIDE;
        k.clearGrowth(-A - 3, -B - 5, A + 4, B + 7, 12);
        k.clear(-A - 1, -B - 1, 0, A + 2, B + 1, 9);
        // Footing and floor.
        k.foundation(-A, -B, A + 2, B, -1, () -> k.wornCobble(), 6);
        for (int a = -A + 1; a <= A - 1; a++)
            for (int b = -B + 1; b <= B - 1; b++) k.set(a, b, -1, k.chance(0.12f) ? s(Blocks.DARK_OAK_PLANKS) : s(Blocks.SPRUCE_PLANKS));
        // Walls: a stone course, plank walls between dark log posts, a beam along the top.
        k.walls(-A, -B, A, B, 0, 0, s(Blocks.DARK_OAK_LOG), () -> k.wornCobble());
        k.walls(-A, -B, A, B, 1, 3, s(Blocks.DARK_OAK_LOG), () -> k.chance(0.06f) ? s(Blocks.AIR) : s(Blocks.SPRUCE_PLANKS));
        for (int y = 1; y <= 3; y++) {
            k.set(0, -B, y, s(Blocks.DARK_OAK_LOG));
            k.set(0, B, y, s(Blocks.DARK_OAK_LOG));
        }
        for (int a = -A + 1; a <= A - 1; a++) {
            k.set(a, -B, 3, k.log(Blocks.STRIPPED_DARK_OAK_LOG, FRONT));
            k.set(a, B, 3, k.log(Blocks.STRIPPED_DARK_OAK_LOG, FRONT));
        }
        // Tie beams across under the roof.
        for (int a : new int[] {-2, 2}) for (int b = -B + 1; b <= B - 1; b++) k.set(a, b, 3, k.log(Blocks.STRIPPED_DARK_OAK_LOG, RIGHT));
        // The roof, along the house, fallen in over the back corner.
        k.gableRoof(-A, -B, A, B, 4, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, () -> s(Blocks.SPRUCE_PLANKS), true, 0.04f);
        for (int a = -A; a <= -1; a++)
            for (int b = 0; b <= B + 1; b++)
                for (int y = 4; y <= 8; y++) if (k.chance(0.55f - 0.08f * (a + A))) k.set(a, b, y, s(Blocks.AIR));
        // What came down: boards and shingles on the floor, moss where the rain gets in, leaves blown in.
        k.set(-1, 2, 0, k.stairs(Blocks.DARK_OAK_STAIRS, LEFT, false));
        k.set(-2, 1, 0, SiteKit.slab(Blocks.DARK_OAK_SLAB, false));
        k.set(-2, 2, 0, s(Blocks.MOSS_CARPET));
        k.set(-1, 1, 0, s(Blocks.MOSS_CARPET));
        k.set(-3, 1, 0, s(Blocks.MOSS_CARPET));
        k.set(-2, 2, 1, SiteKit.leaves(Blocks.OAK_LEAVES));
        // The door, at the front, hanging open.
        k.door(A, 0, 0, Blocks.SPRUCE_DOOR, FRONT, true, false);
        // Windows, the glass mostly gone, shutters (some hanging, some lost).
        int[][] windows = {{-2, -B, LEFT}, {2, -B, LEFT}, {-2, B, RIGHT}, {2, B, RIGHT}, {A, -2, FRONT}, {A, 2, FRONT}, {-A, 1, BACK}};
        for (int[] w : windows) {
            for (int y = 1; y <= 2; y++) k.set(w[0], w[1], y, k.chance(0.45f) ? s(Blocks.GLASS_PANE) : s(Blocks.AIR));
            int out = w[2];
            int oa = out == FRONT ? 1 : out == BACK ? -1 : 0, ob = out == RIGHT ? 1 : out == LEFT ? -1 : 0;
            // The shutters either side of it, open against the wall.
            int sa = ob != 0 ? 1 : 0, sb = oa != 0 ? 1 : 0;
            for (int side : new int[] {-1, 1}) {
                if (k.chance(0.25f)) continue;
                for (int y = 1; y <= 2; y++) {
                    if (y == 2 && k.chance(0.2f)) continue;
                    k.set(w[0] + oa + sa * side, w[1] + ob + sb * side, y, k.trapdoor(Blocks.SPRUCE_TRAPDOOR, out, true, false));
                }
            }
        }
        // The chimney up the back wall, a cold stove inside it.
        k.foundation(-A - 1, 2, -A - 1, 2, -1, () -> s(Blocks.COBBLESTONE), 6);
        for (int y = 0; y <= 9; y++) k.set(-A - 1, 2, y, k.wornCobble());
        k.set(-A - 1, 2, 10, s(Blocks.COBBLESTONE_WALL));
        k.set(-A + 1, 2, 0, k.facing(Blocks.SMOKER, FRONT));
        // The porch: a deck out front, two posts, a roof over it, a chair still there.
        for (int a = A + 1; a <= A + 2; a++) for (int b = -B; b <= B; b++) k.set(a, b, -1, k.chance(0.1f) ? s(Blocks.AIR) : s(Blocks.SPRUCE_PLANKS));
        k.set(A + 1, 0, -1, s(Blocks.SPRUCE_PLANKS));
        k.set(A + 2, 0, -1, s(Blocks.SPRUCE_PLANKS));
        for (int b : new int[] {-B, B}) k.column(A + 2, b, 0, 2, s(Blocks.SPRUCE_FENCE));
        for (int b = -B - 1; b <= B + 1; b++) {
            k.set(A + 1, b, 3, SiteKit.slab(Blocks.SPRUCE_SLAB, false));
            k.set(A + 2, b, 3, SiteKit.slab(Blocks.SPRUCE_SLAB, false));
            k.set(A + 3, b, 3, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
        }
        k.set(A + 1, 2, 0, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
        k.set(A + 1, -2, 0, s(Blocks.POTTED_DEAD_BUSH));
        k.set(A + 2, 0, 2, SiteKit.hanging(Blocks.LANTERN));
        // Inside: the bed against the back wall, made; a nightstand, the table and its chairs, the kitchen corner.
        k.bed(-A + 2, -1, 0, Blocks.BED.white(), BACK);
        k.set(-A + 1, -2, 0, k.facing(Blocks.BARREL, FRONT));
        k.set(-A + 1, -2, 1, SiteKit.candles(1));
        for (int a = -A + 2; a <= -A + 3; a++) for (int b = 0; b <= 1; b++) k.set(a, b, 0, s(Blocks.CARPET.red()));
        k.set(1, -1, 0, s(Blocks.SPRUCE_FENCE));
        k.set(1, -1, 1, s(Blocks.SPRUCE_PRESSURE_PLATE));
        k.set(1, -2, 0, k.stairs(Blocks.SPRUCE_STAIRS, LEFT, false));
        k.set(2, -1, 0, k.stairs(Blocks.SPRUCE_STAIRS, FRONT, false));
        k.set(A - 1, -2, 0, k.facing(Blocks.BARREL, RIGHT));
        k.set(A - 2, -2, 0, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        k.set(A - 1, -2, 2, k.facing(Blocks.SPRUCE_SHELF, RIGHT));
        k.set(A - 2, -2, 2, k.facing(Blocks.SPRUCE_SHELF, RIGHT));
        k.set(A - 1, 2, 0, s(Blocks.BOOKSHELF));
        k.set(A - 1, 2, 1, s(Blocks.BOOKSHELF));
        k.set(A - 2, 2, 0, k.facing(Blocks.CHISELED_BOOKSHELF, LEFT));
        k.set(2, 0, 2, SiteKit.hanging(Blocks.LANTERN));
        k.cobwebs(new int[][] {{-A + 1, -B + 1, 2}, {A - 1, B - 1, 2}, {A - 1, -B + 1, 2}, {-2, -2, 2}, {2, 2, 2}, {-A + 1, B - 1, 2}, {0, 1, 4}, {1, -1, 5}}, 0.65f);
        // The garden at the side, gone to weeds, its fence half down.
        for (int a = -3; a <= 3; a++)
            for (int b = B + 3; b <= B + 7; b++) {
                boolean edge = Math.abs(a) == 3 || b == B + 3 || b == B + 7;
                if (edge) {
                    if (!k.chance(0.12f) && !(a == 0 && b == B + 3)) k.onGround(a, b, s(Blocks.SPRUCE_FENCE));
                } else if (a % 2 == 0) {
                    k.ground(a, b, s(Blocks.COARSE_DIRT));
                    if (k.chance(0.6f)) k.onGround(a, b, s(k.pick(Blocks.DEAD_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS)));
                } else if (k.chance(0.5f)) {
                    k.onGround(a, b, s(k.pick(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.SHORT_DRY_GRASS)));
                }
            }
        k.onGround(2, B + 5, s(Blocks.PUMPKIN));
        k.onGround(-2, B + 4, s(Blocks.COMPOSTER));
        // The dry well, out by the front corner.
        dryWell(k, 3, -B - 4);
        k.path(A + 3, 0, A + 12, 0, s(Blocks.DIRT_PATH), 0.3f);
        k.settle();
        in.marks.put("door", k.at(A, 0, 0));
        in.marks.put("bed", k.at(-A + 2, -1, 0));
        // Inside, in the middle of the room, between the door and the bed.
        in.marks.put("breach", k.at(0, 0, 0));
    }

    /** A dry well: a stone ring round a dark shaft, two posts and a little roof. */
    static void dryWell(SiteKit k, int a, int b) {
        int y = k.groundY(a, b);
        for (int da = -1; da <= 1; da++)
            for (int db = -1; db <= 1; db++) {
                if (da == 0 && db == 0) {
                    for (int d = 0; d >= -4; d--) k.set(a, b, y - 1 + d, s(Blocks.AIR));
                    continue;
                }
                k.set(a + da, b + db, y, k.wornCobble());
                for (int d = 1; d <= 4; d++) if (Math.abs(da) + Math.abs(db) == 1) k.set(a + da, b + db, y - d, k.wornCobble());
            }
        k.set(a, b, y - 5, s(Blocks.COBBLESTONE));
        k.set(a, b, y - 3, s(Blocks.COBWEB));
        for (int db : new int[] {-1, 1}) k.column(a, b + db, y + 1, y + 2, s(Blocks.SPRUCE_FENCE));
        for (int db = -1; db <= 1; db++) {
            k.set(a - 1, b + db, y + 3, k.stairs(Blocks.SPRUCE_STAIRS, FRONT, false));
            k.set(a, b + db, y + 3, SiteKit.slab(Blocks.SPRUCE_SLAB, false));
            k.set(a + 1, b + db, y + 3, k.stairs(Blocks.SPRUCE_STAIRS, BACK, false));
        }
    }
}
