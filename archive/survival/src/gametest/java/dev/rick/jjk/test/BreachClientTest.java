package dev.rick.jjk.test;

import dev.rick.jjk.progression.investigation.CursedBreaches;
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

/**
 * A Cursed Breach in the world (by day and at night, near and far), then used: the pull's dark, and the realm beyond.
 * Opt-in: screenshots breach*.png.
 */
public class BreachClientTest implements FabricClientGameTest {
    private static String tp(double x, double y, double z, float yaw, float pitch) {
        return String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 ~ 0");
            ctx.waitTicks(30);
            double[] at = new double[3];
            String[] id = new String[1];
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                BlockPos site = p.blockPosition().offset(0, 0, 6);
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, site.offset(60, 0, 60));
                Incident in = Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(site, 1, 0), level.getGameTime(),
                        RandomSource.create(3));
                Investigations.buildForTest(level, in);
                BlockPos a = CursedBreaches.anchor(in);
                CursedBreaches.ensure(level, in.id, a);
                at[0] = a.getX() + 0.5;
                at[1] = a.getY();
                at[2] = a.getZ() + 0.5;
                id[0] = in.id;
            });
            ctx.waitTicks(60);
            server.runCommand(tp(at[0], at[1] + 0.4, at[2] - 4.5, 0f, 6f));
            ctx.waitTicks(30);
            ctx.takeScreenshot("breach1_day_near");
            server.runCommand(tp(at[0] + 7, at[1] + 1.5, at[2] - 7, 45f, 8f));
            ctx.waitTicks(20);
            ctx.takeScreenshot("breach2_day_far");
            server.runCommand("time set midnight");
            server.runCommand(tp(at[0] - 3, at[1] + 0.6, at[2] - 3.2, -40f, 4f));
            ctx.waitTicks(40);
            ctx.takeScreenshot("breach3_night");
            // Use it: the pull, then the pasture realm.
            server.runCommand("gamemode survival @a");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                var b = CursedBreaches.find((ServerLevel) p.level(), id[0], BlockPos.containing(at[0], at[1], at[2]));
                if (b == null) throw new AssertionError("the breach is there");
                CursedBreaches.useForTest(p, b);
            });
            ctx.waitTicks(16);
            ctx.takeScreenshot("breach4_pull");
            ctx.waitTicks(70);
            ctx.takeScreenshot("breach5_realm");
        }
    }
}
