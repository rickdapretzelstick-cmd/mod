package dev.rick.jjk.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * A test's ground beyond its structure: only the structure's own chunks are kept ticking by the framework, so an entity
 * standing on floor a test laid past them may sit in a chunk that is loaded but not ticking (its casts, AI and timers
 * frozen), depending on where the test happens to be placed. Forcing the chunks under the whole floor keeps everything
 * on it ticking wherever the test lands.
 */
final class TestArea {
    private TestArea() {}

    /** Keeps every chunk under the test-relative rectangle [x0, x1) x [z0, z1) ticking. */
    static void keepTicking(GameTestHelper h, int x0, int z0, int x1, int z1) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(x0, 0, z0)), b = h.absolutePos(new BlockPos(x1 - 1, 0, z1 - 1));
        int cx0 = Math.min(a.getX(), b.getX()) >> 4, cx1 = Math.max(a.getX(), b.getX()) >> 4;
        int cz0 = Math.min(a.getZ(), b.getZ()) >> 4, cz1 = Math.max(a.getZ(), b.getZ()) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) level.setChunkForced(cx, cz, true);
    }
}
