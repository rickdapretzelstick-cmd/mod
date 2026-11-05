package dev.rick.jjk.test;

import dev.rick.jjk.progression.investigation.NewsHouse;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * The village news house in each of its five styles, outside and in (the board on the back wall, the lectern, the
 * desk). Opt-in: screenshots news*.png.
 */
public class NewsHouseClientTest implements FabricClientGameTest {
    private static String tp(double x, double y, double z, float yaw, float pitch) {
        return String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 ~ 0");
            ctx.waitTicks(30);
            int[] ground = new int[1];
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 20) - 1;
                ground[0] = y;
                for (int i = 0; i < NewsHouse.ALL.length; i++) {
                    // Doors facing north (toward the camera), houses 14 apart.
                    NewsHouse.build(level, new BlockPos(i * 14, y, 20), Direction.NORTH, NewsHouse.ALL[i], RandomSource.create(i));
                }
            });
            ctx.waitTicks(40);
            for (int i = 0; i < NewsHouse.ALL.length; i++) {
                String name = NewsHouse.ALL[i].name();
                server.runCommand(tp(i * 14 + 6.5, ground[0] + 4.5, 12.5, 35f, 15f));
                ctx.waitTicks(20);
                ctx.takeScreenshot("news_" + name + "_outside");
                // Inside, from just within the door, looking at the board on the back wall.
                server.runCommand(tp(i * 14 + 0.5, ground[0] + 1.2, 20.9, 0f, 12f));
                ctx.waitTicks(20);
                ctx.takeScreenshot("news_" + name + "_inside");
            }
        }
    }
}
