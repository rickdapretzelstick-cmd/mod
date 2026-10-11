package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.ProgressionBlocks;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.NewsBoardBlock;
import dev.rick.jjk.progression.investigation.NewsHouse;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * The village news house: built on open flat ground near the bell in the village's style, door toward the bell, the
 * board inside on the back wall facing the door, a lectern (a librarian's work site, so villagers use the place); the
 * board shows as many pinned notices as the village has news.
 */
public class NewsHouseTests {
    @GameTest(maxTicks = 40, padding = 24, environment = "jjk-test:news_a")
    public void theNewsHouseGoesUpByTheBellWithItsBoardInside(GameTestHelper h) {
        JJKConfig.get().progression.enabled = true;
        JJKConfig.get().mastery.enabled = true;
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos bell = h.absolutePos(new BlockPos(11, 1, 3));
        h.setBlock(11, 1, 3, Blocks.BELL);
        BlockPos board = NewsHouse.placeNear(level, bell);
        h.assertTrue(board != null, "there was room for the house");
        h.assertTrue(level.getBlockState(board).is(ProgressionBlocks.NEWS_BOARD), "the board is in it");
        Direction faces = level.getBlockState(board).getValue(HorizontalDirectionalBlock.FACING);
        // Walking out of the board's face reaches the door, then the open air toward the bell.
        BlockPos p = board;
        boolean door = false;
        for (int i = 0; i < 8 && !door; i++) {
            p = p.relative(faces);
            door = level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
        }
        h.assertTrue(door, "the board faces the door");
        h.assertTrue(p.relative(faces).distSqr(bell) < board.distSqr(bell), "and the door faces the bell");
        boolean roof = false;
        for (int y = 3; y <= 8; y++) if (!level.getBlockState(board.above(y)).isAir()) roof = true;
        h.assertTrue(roof, "under a roof");
        boolean lectern = false;
        for (BlockPos q : BlockPos.betweenClosed(board.offset(-4, -1, -4), board.offset(4, 2, 4))) {
            if (level.getBlockState(q).is(Blocks.LECTERN)) lectern = true;
        }
        h.assertTrue(lectern, "a lectern for whoever keeps the record (a librarian's work site)");
        // The board's paper follows the news.
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, bell);
        v.setBoardForTest(board);
        Investigations.refreshBoard(level, v, st, level.getGameTime());
        int before = level.getBlockState(board).getValue(NewsBoardBlock.NOTICES);
        Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(bell.offset(120, 0, 40), 1, 0), level.getGameTime(),
                RandomSource.create(8));
        Investigations.refreshBoard(level, v, st, level.getGameTime());
        int after = level.getBlockState(board).getValue(NewsBoardBlock.NOTICES);
        h.assertTrue(after == Math.min(4, before + 1), "a new report pins up another notice: " + before + " -> " + after);
        h.succeed();
    }

    @GameTest(maxTicks = 20, padding = 24, environment = "jjk-test:news_a")
    public void noRoomMeansNoHouse(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) {
            h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            if ((x + z) % 3 == 0) h.setBlock(x, 1, z, Blocks.COBBLESTONE_WALL);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int x = 6; x <= 16; x += 5) for (int z = 6; z <= 16; z += 5) {
                h.assertTrue(!NewsHouse.fits(level, h.absolutePos(new BlockPos(x, 0, z)), d), "never built over the village's own things");
            }
        }
        h.succeed();
    }
}
