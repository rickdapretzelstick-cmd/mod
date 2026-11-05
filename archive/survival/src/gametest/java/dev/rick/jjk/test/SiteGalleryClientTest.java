package dev.rick.jjk.test;

import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * Every overworld incident site built on land shaped for it (a cliff for the cliff, a hill for the mine, woods for the
 * lodge), seen from a few angles: the way in, the far side, and from inside. Opt-in; JJK_SITES=house,chapel narrows it.
 * Screenshots site_<kind>_<n>.png.
 */
public class SiteGalleryClientTest implements FabricClientGameTest {
    /** A kind of site, the incident that builds it, its land, trees round it, and where the camera stands (a, b, y). */
    private record Kind(String name, String template, IntBinaryOperator land, String tree, int trees, int[][] views) {}

    private static final int[][] OUTSIDE = {{15, -12, 8, 0, 0, 2}, {-13, 14, 10, 0, 0, 2}, {3, 2, 0, -3, -1, 1}};

    private static final List<Kind> KINDS = List.of(
            new Kind("cliff", "cliff_fall", (a, b) -> a <= (Math.floorMod(b * 7, 3) == 0 ? 1 : 0) ? 13 : 0, "oak", 6,
                    new int[][] {{-9, -8, 5, 0, 0, 1}, {14, 10, -6, 0, 0, 2}, {-2, 0, 0, 3, 0, 0}}),
            new Kind("pasture", "livestock", (a, b) -> 0, "oak", 4, OUTSIDE),
            new Kind("hillside", "old_mine", (a, b) -> Math.max(0, Math.min(14, (int) ((a - 1) * 1.4) - Math.max(0, Math.abs(b) - 9))), "spruce", 6,
                    new int[][] {{-12, -9, 5, 0, 0, 2}, {-6, 8, 3, 2, 0, 1}, {2, 0, -1, 8, 0, -4}}),
            new Kind("lodge", "hunting_lodge", (a, b) -> 0, "spruce", 26, new int[][] {{-15, -11, 7, 0, 0, 2}, {14, 12, 9, 0, 0, 2}, {2, -2, 1, 4, 0, 1}}),
            new Kind("house", "empty_house", (a, b) -> 0, "oak", 3, OUTSIDE),
            new Kind("tower", "gojo_chain_1", (a, b) -> 0, "oak", 3, new int[][] {{18, -14, 6, 0, 0, 9}, {-15, 15, 16, 0, 0, 8}, {-3, 2, 16, 2, -1, 17}}),
            new Kind("theater", "yuji_chain_1", (a, b) -> 0, "oak", 2, new int[][] {{18, -13, 8, 0, 0, 3}, {-16, 15, 12, 0, 0, 2}, {4, 2, 2, -6, 0, 2}}),
            new Kind("storehouse", "hakari_chain_1", (a, b) -> 0, "oak", 3, OUTSIDE),
            new Kind("crater", "ryu_chain_1", (a, b) -> 0, "oak", 8, new int[][] {{16, -14, 10, 0, 0, -2}, {-6, 6, 4, 0, 0, -3}, {-14, 13, 12, 0, 0, -2}}),
            new Kind("chapel", "yuta_chain_4", (a, b) -> 0, "birch", 3, new int[][] {{18, -13, 8, 0, 0, 4}, {-16, 15, 12, 0, 0, 3}, {4, 0, 2, -5, 0, 1}}));

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getenv("JJK_SITES");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
            for (int i = 0; i < KINDS.size(); i++) {
                Kind k = KINDS.get(i);
                if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(k.name)) continue;
                int cx = 200 + i * 96, cz = 200;
                server.runCommand("tp @a " + cx + " -20 " + cz);
                ctx.waitTicks(60);
                int[] site = server.computeOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                    ServerLevel level = (ServerLevel) p.level();
                    int top = shape(level, cx, cz, k.land);
                    // The site: at the cliff's lip, the hill's foot, the middle of the flat for the rest.
                    BlockPos at = new BlockPos(cx, top + 1 + k.land.applyAsInt(0, 0), cz);
                    InvestigationState st = InvestigationState.get(srv);
                    InvestigationState.Village v = Investigations.village(level, at.offset(-90, 0, 0));
                    Incident in = Investigations.create(level, v, st, IncidentTemplate.get(k.template), new Sites.Site(at, 1, 0), level.getGameTime(),
                            RandomSource.create(11));
                    trees(srv, level, cx, cz, top, k);
                    Investigations.buildForTest(level, in);
                    return new int[] {at.getX(), at.getY(), at.getZ()};
                });
                ctx.waitTicks(20);
                for (int v = 0; v < k.views.length; v++) {
                    int[] c = k.views[v];
                    // The frame faces +x: a is x, b is z.
                    server.runCommand("tp @a " + (site[0] + c[0]) + " " + (site[1] + c[2]) + " " + (site[2] + c[1]) + " facing "
                            + (site[0] + c[3]) + " " + (site[1] + c[5]) + " " + (site[2] + c[4]));
                    ctx.waitTicks(v == 0 ? 60 : 25);
                    ctx.takeScreenshot("site_" + k.name + "_" + v);
                }
            }
        }
    }

    /** Lays land round (cx, cz) to the shape given (heights above the flat), and clears the air over it. The flat's top. */
    private static int shape(ServerLevel level, int cx, int cz, IntBinaryOperator land) {
        // Deep enough land for a crater or a mine to go down into (the flat world is only a few blocks thick).
        int top = -40;
        for (int a = -34; a <= 34; a++) {
            for (int b = -34; b <= 34; b++) {
                int h = land.applyAsInt(a, b);
                for (int y = -61; y <= top + 24; y++) {
                    BlockPos p = new BlockPos(cx + a, y, cz + b);
                    int surf = top + h;
                    var s = y > surf ? Blocks.AIR.defaultBlockState() : y == surf ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : y >= surf - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    level.setBlock(p, s, 2);
                }
            }
        }
        return top;
    }

    /** Trees scattered round the site, never in the middle of it. */
    private static void trees(net.minecraft.server.MinecraftServer srv, ServerLevel level, int cx, int cz, int top, Kind k) {
        RandomSource r = RandomSource.create(k.name.hashCode());
        for (int i = 0, placed = 0; i < 200 && placed < k.trees; i++) {
            int a = r.nextInt(57) - 28, b = r.nextInt(57) - 28;
            if (Math.abs(a) < 13 && Math.abs(b) < 13) continue;
            int y = top + 1 + k.land.applyAsInt(a, b);
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(),
                    "place feature minecraft:" + k.tree + " " + (cx + a) + " " + y + " " + (cz + b));
            placed++;
        }
        // A little undergrowth.
        for (int i = 0; i < 90; i++) {
            int a = r.nextInt(61) - 30, b = r.nextInt(61) - 30;
            BlockPos p = new BlockPos(cx + a, top + 1 + k.land.applyAsInt(a, b), cz + b);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK))
                level.setBlock(p, (i % 5 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState(), 2);
        }
    }
}
