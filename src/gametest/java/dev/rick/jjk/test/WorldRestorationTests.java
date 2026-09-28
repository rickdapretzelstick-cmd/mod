package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.domain.structure.DomainStructure;
import dev.rick.jjk.core.domain.structure.DomainStructures;
import dev.rick.jjk.core.domain.structure.StructureSpec;
import dev.rick.jjk.core.world.WorldRestoration;
import dev.rick.jjk.registry.ModBlocks;
import dev.rick.jjk.util.Destruction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporary battle damage: per-block timing, exact states, attached blocks and entities, block entities without
 * duplication, knock-on effects, repeated damage, conflicts, restarts and domains.
 */
public class WorldRestorationTests {
    static final int DELAY = 40;
    private static JJKConfig testConfig;

    private static void applyConfig() {
        if (testConfig != null && JJKConfig.get() == testConfig) return;
        JJKConfig cfg = new JJKConfig();
        cfg.general.autoAssignGojo = false;
        cfg.restoration.delayTicks = DELAY;
        JJKConfig.set(cfg);
        testConfig = cfg;
    }

    private static void floor(GameTestHelper h) {
        applyConfig();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static boolean destroy(GameTestHelper h, int x, int y, int z) {
        return Destruction.destroy(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)), 60f, null, "jjk:test");
    }

    private static List<ItemEntity> items(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(12), e -> true);
    }

    /** Records every block in the test area so the whole scene can be compared after restoration. */
    private static Map<BlockPos, BlockState> snapshot(GameTestHelper h) {
        Map<BlockPos, BlockState> m = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 7, 6, 7)) m.put(p.immutable(), h.getBlockState(p));
        return m;
    }

    private static void assertSame(GameTestHelper h, Map<BlockPos, BlockState> before, String what) {
        for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
            BlockState now = h.getBlockState(e.getKey());
            h.assertTrue(now == e.getValue(), what + ": " + e.getKey() + " is " + now + ", was " + e.getValue());
        }
    }

    // --- Timing ---

    @GameTest(maxTicks = 160, environment = "jjk-test:restore_a")
    public void everyBlockHasItsOwnTimer(GameTestHelper h) {
        floor(h);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.WATERLOGGED, true);
        h.setBlock(2, 1, 2, stairs);
        h.setBlock(5, 1, 5, Blocks.MOSSY_COBBLESTONE);
        h.startSequence()
                .thenExecute(() -> h.assertTrue(destroy(h, 2, 1, 2), "A destroyed"))
                .thenIdle(15)
                .thenExecute(() -> h.assertTrue(destroy(h, 5, 1, 5), "B destroyed 15 ticks later"))
                .thenIdle(DELAY - 15 + 1)
                .thenExecute(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(2, 1, 2)) == stairs, "A back exactly (facing, half, waterlogged): "
                            + h.getBlockState(new BlockPos(2, 1, 2)));
                    var rec = WorldRestoration.recordAt(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5)));
                    h.assertTrue(!h.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.MOSSY_COBBLESTONE), "B still waiting for its own timer (" + h.getBlockState(new BlockPos(5, 1, 5))
                            + ", record " + (rec == null ? "none" : rec.restoreAt() + " now " + h.getLevel().getGameTime()) + ")");
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.MOSSY_COBBLESTONE), "B back on its own timer");
                    h.assertTrue(items(h).isEmpty(), "no drops");
                })
                .thenSucceed();
    }

    // --- Attachments ---

    @GameTest(maxTicks = 200, padding = 4, environment = "jjk-test:restore_a")
    public void attachedBlocksAndEntitiesComeBackWithTheirSupports(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        // A stone wall along z = 5 with a torch, a lever, a painting and an item frame hanging on it.
        for (int x = 1; x <= 5; x++) for (int y = 1; y <= 3; y++) h.setBlock(x, y, 5, Blocks.STONE_BRICKS);
        h.setBlock(3, 2, 4, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        h.setBlock(1, 2, 4, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, true));
        BlockPos framePos = h.absolutePos(new BlockPos(5, 2, 4));
        ItemFrame frame = new ItemFrame(level, framePos, Direction.NORTH);
        frame.setItem(new ItemStack(Items.DIAMOND));
        level.addFreshEntity(frame);
        BlockPos paintingPos = h.absolutePos(new BlockPos(4, 2, 4));
        var kebab = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT)
                .getOrThrow(net.minecraft.world.entity.decoration.painting.PaintingVariants.KEBAB);
        level.addFreshEntity(new Painting(level, paintingPos, Direction.NORTH, kebab));
        // Ground cover and things standing on the floor.
        h.setBlock(1, 0, 1, Blocks.GRASS_BLOCK);
        h.setBlock(1, 1, 1, Blocks.POPPY);
        h.setBlock(2, 0, 1, Blocks.GRASS_BLOCK);
        h.setBlock(2, 1, 1, Blocks.SHORT_GRASS);
        h.setBlock(3, 1, 1, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        h.setBlock(4, 1, 1, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.FACING, Direction.SOUTH));
        h.setBlock(4, 2, 1, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.FACING, Direction.SOUTH));
        h.setBlock(5, 1, 1, Blocks.STONE_PRESSURE_PLATE);
        h.setBlock(6, 1, 1, Blocks.REDSTONE_WIRE);
        Map<BlockPos, BlockState> before = new HashMap<>();
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(level.getEntitiesOfClass(Painting.class, new AABB(paintingPos).inflate(1)).size() == 1, "setup: painting hung");
                    before.putAll(snapshot(h));
                    // Knock out the supports only.
                    for (int x = 1; x <= 5; x++) destroy(h, x, 2, 5);
                    for (int x = 1; x <= 6; x++) destroy(h, x, 0, 1);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(3, 2, 4)).isAir(), "torch fell with its wall");
                    h.assertTrue(h.getBlockState(new BlockPos(1, 2, 4)).isAir(), "lever fell with its wall");
                    h.assertTrue(h.getBlockState(new BlockPos(1, 1, 1)).isAir(), "flower fell with its ground");
                    h.assertTrue(h.getBlockState(new BlockPos(4, 1, 1)).isAir() && h.getBlockState(new BlockPos(4, 2, 1)).isAir(), "both door halves fell");
                    h.assertTrue(h.getBlockState(new BlockPos(3, 1, 1)).isAir(), "rail fell");
                    h.assertTrue(level.getEntitiesOfClass(Painting.class, new AABB(paintingPos).inflate(1)).isEmpty(), "painting taken down");
                    h.assertTrue(level.getEntitiesOfClass(ItemFrame.class, new AABB(framePos).inflate(1)).isEmpty(), "frame taken down");
                    h.assertTrue(items(h).isEmpty(), "nothing dropped (it all comes back instead): " + items(h));
                })
                .thenIdle(DELAY)
                .thenExecute(() -> {
                    assertSame(h, before, "restored scene");
                    h.assertTrue(level.getEntitiesOfClass(Painting.class, new AABB(paintingPos).inflate(1)).size() == 1, "painting back");
                    List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(framePos).inflate(1));
                    h.assertTrue(frames.size() == 1 && frames.getFirst().getItem().is(Items.DIAMOND), "frame back with its diamond");
                    h.assertTrue(items(h).isEmpty(), "still nothing duplicated as drops");
                })
                .thenSucceed();
    }

    // --- Block entities ---

    @GameTest(maxTicks = 160, environment = "jjk-test:restore_a")
    public void containersComeBackWithTheirContentsAndNeverDuplicate(GameTestHelper h) {
        floor(h);
        BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
        // Double chest facing north: the left half (x=2) pairs with the right half to its east (x=3).
        h.setBlock(2, 1, 3, left);
        h.setBlock(3, 1, 3, right);
        h.getBlockEntity(new BlockPos(2, 1, 3), ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 5));
        h.getBlockEntity(new BlockPos(3, 1, 3), ChestBlockEntity.class).setItem(4, new ItemStack(Items.EMERALD, 7));
        h.setBlock(5, 1, 5, Blocks.BARREL);
        ((net.minecraft.world.level.block.entity.BarrelBlockEntity) h.getBlockEntity(new BlockPos(5, 1, 5),
                net.minecraft.world.level.block.entity.BarrelBlockEntity.class)).setItem(2, new ItemStack(Items.GOLD_INGOT, 3));
        h.startSequence()
                .thenExecute(() -> {
                    h.assertTrue(destroy(h, 2, 1, 3), "left half destroyed");
                    h.assertTrue(destroy(h, 5, 1, 5), "barrel destroyed");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(items(h).isEmpty(), "no items spilled: " + items(h));
                    BlockState other = h.getBlockState(new BlockPos(3, 1, 3));
                    h.assertTrue(other.is(Blocks.CHEST) && other.getValue(ChestBlock.TYPE) == ChestType.SINGLE, "the other half became a single chest: " + other);
                    // A player takes the emeralds out of the surviving half before the restore.
                    h.getBlockEntity(new BlockPos(3, 1, 3), ChestBlockEntity.class).setItem(4, ItemStack.EMPTY);
                })
                .thenIdle(DELAY)
                .thenExecute(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(2, 1, 3)) == left && h.getBlockState(new BlockPos(3, 1, 3)) == right, "double chest rejoined");
                    ChestBlockEntity l = h.getBlockEntity(new BlockPos(2, 1, 3), ChestBlockEntity.class);
                    h.assertTrue(l.getItem(0).is(Items.DIAMOND) && l.getItem(0).getCount() == 5, "destroyed half has its diamonds back");
                    ChestBlockEntity r = h.getBlockEntity(new BlockPos(3, 1, 3), ChestBlockEntity.class);
                    h.assertTrue(r.getItem(4).isEmpty(), "the surviving half keeps what the player took (no duplication)");
                    var barrel = h.getBlockEntity(new BlockPos(5, 1, 5), net.minecraft.world.level.block.entity.BarrelBlockEntity.class);
                    h.assertTrue(barrel.getItem(2).is(Items.GOLD_INGOT) && barrel.getItem(2).getCount() == 3, "barrel contents back");
                    h.assertTrue(items(h).isEmpty(), "no items anywhere");
                })
                .thenSucceed();
    }

    // --- Knock-on effects ---

    @GameTest(maxTicks = 160, environment = "jjk-test:restore_a")
    public void fallingBlocksSetLooseComeBackInPlace(GameTestHelper h) {
        floor(h);
        h.setBlock(4, 1, 4, Blocks.STONE);
        for (int y = 2; y <= 5; y++) h.setBlock(4, y, 4, y % 2 == 0 ? Blocks.SAND : Blocks.GRAVEL);
        Map<BlockPos, BlockState> before = new HashMap<>();
        h.startSequence()
                .thenExecute(() -> {
                    before.putAll(snapshot(h));
                    h.assertTrue(destroy(h, 4, 1, 4), "support destroyed");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    for (int y = 1; y <= 5; y++) h.assertTrue(h.getBlockState(new BlockPos(4, y, 4)).isAir(), "column gone at y=" + y);
                    h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                            new AABB(h.absolutePos(BlockPos.ZERO)).inflate(10)).isEmpty(), "no stray falling blocks");
                    h.assertTrue(items(h).isEmpty(), "no drops");
                })
                .thenIdle(DELAY)
                .thenExecute(() -> assertSame(h, before, "column restored"))
                .thenSucceed();
    }

    // --- Repeated damage and conflicts ---

    @GameTest(maxTicks = 160, environment = "jjk-test:restore_a")
    public void repeatedDamageRestoresTheOriginalAndPlayerChangesArePreserved(GameTestHelper h) {
        floor(h);
        h.setBlock(2, 1, 2, Blocks.DEEPSLATE_BRICKS);
        h.setBlock(5, 1, 2, Blocks.COBBLESTONE);
        h.startSequence()
                .thenExecute(() -> {
                    destroy(h, 2, 1, 2);
                    destroy(h, 5, 1, 2);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    // Someone fills the first crater with dirt, and a technique blasts it again.
                    h.setBlock(2, 1, 2, Blocks.DIRT);
                    destroy(h, 2, 1, 2);
                    // Someone builds in the second crater and leaves it.
                    h.setBlock(5, 1, 2, Blocks.OAK_PLANKS);
                })
                .thenIdle(DELAY)
                .thenExecute(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.DEEPSLATE_BRICKS), "the original comes back, not the dirt in between");
                    h.assertTrue(h.getBlockState(new BlockPos(5, 1, 2)).is(Blocks.OAK_PLANKS), "the player's block is preserved");
                    h.assertTrue(items(h).stream().anyMatch(i -> i.getItem().is(Items.COBBLESTONE)), "the original is handed back as an item instead");
                })
                .thenSucceed();
    }

    // --- Domains ---

    @GameTest(maxTicks = 200, padding = 8, environment = "jjk-test:restore_a")
    public void domainBlocksWaitForTheirDomainThenDamageRestores(GameTestHelper h) {
        floor(h);
        h.setBlock(4, 1, 4, Blocks.EMERALD_BLOCK);
        DomainStructure[] dom = new DomainStructure[1];
        h.startSequence()
                .thenExecute(() -> h.assertTrue(destroy(h, 4, 1, 4), "damaged"))
                .thenExecute(() -> {
                    StructureSpec spec = new StructureSpec(3.5, 1, ModBlocks.DOMAIN_BARRIER.defaultBlockState(), ModBlocks.DOMAIN_FLOOR.defaultBlockState(), true);
                    dom[0] = DomainStructures.create(h.getLevel(), h.absolutePos(new BlockPos(4, 2, 4)), spec);
                    h.assertTrue(dom[0] != null, "domain structure over the crater");
                })
                .thenIdle(DELAY + 5)
                .thenExecute(() -> {
                    h.assertTrue(ModBlocks.isDomainBlock(h.getBlockState(new BlockPos(4, 1, 4))), "the domain still holds the spot: restore waits");
                    h.assertTrue(WorldRestoration.recordAt(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4))) != null, "damage still pending");
                    DomainStructures.beginRestore(dom[0]);
                })
                .thenIdle(30)
                .thenExecute(() -> h.assertTrue(h.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.EMERALD_BLOCK), "domain gone, then the damage restored"))
                .thenSucceed();
    }

    // --- Restarts ---

    @GameTest(maxTicks = 120, environment = "jjk-test:restore_b")
    public void pendingRestorationSurvivesARestart(GameTestHelper h) {
        floor(h);
        h.setBlock(3, 1, 3, Blocks.LAPIS_BLOCK);
        h.startSequence()
                .thenExecute(() -> {
                    destroy(h, 3, 1, 3);
                    WorldRestoration.saveForTesting(h.getLevel());
                    h.assertTrue(WorldRestoration.hasFileForTesting(h.getLevel()), "written to disk");
                    // Simulate a restart: everything in memory is gone, then the file is read back.
                    WorldRestoration.forgetInMemoryForTesting(h.getLevel());
                    h.assertTrue(WorldRestoration.recordAt(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3))) == null, "memory cleared");
                    h.assertTrue(WorldRestoration.load(h.getLevel()) > 0, "reloaded from disk");
                })
                .thenIdle(DELAY + 2)
                .thenExecute(() -> h.assertTrue(h.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.LAPIS_BLOCK), "restored after the restart"))
                .thenSucceed();
    }
}
