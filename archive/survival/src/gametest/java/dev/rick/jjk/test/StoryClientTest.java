package dev.rick.jjk.test;

import dev.rick.jjk.client.hud.StoryOverlay;
import dev.rick.jjk.client.investigation.NewsBoardScreen;
import dev.rick.jjk.core.net.StoryPayload;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.investigation.StoryChains;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * The character storylines through a real client: the new items in the hotbar, a village board with its storyline's
 * report pinned and read, the five new places built at their sites, each personal trial's realm (with the Infused
 * Blindfold's vision, the Six Eyes reveal and a title card), and the trip home. Opt-in: screenshots in
 * build/run/clientGameTest/screenshots as story*.png.
 */
public class StoryClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            ctx.waitTicks(40);

            // The items: Essences and objects, then the relics.
            String[] first = {"yuji_essence", "gojo_essence", "yuta_essence", "ryu_essence", "hakari_essence", "human_earthworm_vhs", "blindfold",
                    "cursed_ring", "comb"};
            for (int i = 0; i < first.length; i++) server.runCommand("item replace entity @a hotbar." + i + " with jjk:" + first[i]);
            ctx.waitTicks(10);
            ctx.takeScreenshot("story1_items");
            String[] second = {"scratch_off_ticket", "infused_human_earthworm_vhs", "infused_blindfold", "infused_cursed_ring", "infused_comb",
                    "infused_scratch_off_ticket", "air", "air", "air"};
            for (int i = 0; i < second.length; i++) server.runCommand("item replace entity @a hotbar." + i + " with " + (second[i].equals("air") ? "air" : "jjk:" + second[i]));
            ctx.waitTicks(10);
            ctx.takeScreenshot("story2_relics");
            for (int i = 0; i < 9; i++) server.runCommand("item replace entity @a hotbar." + i + " with air");

            // A Gojo village's board: the watchtower report pinned first, read in full.
            server.runCommand("execute at @a run setblock ~ ~ ~3 jjk:news_board[facing=north]");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                BlockPos board = p.blockPosition().offset(0, 0, 3);
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, board.offset(4, 0, 0));
                StoryChains.setStory(st, v, "gojo", 1);
                Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(board.offset(150, 0, -120), 1, 0),
                        level.getGameTime(), RandomSource.create(4));
                Investigations.create(level, v, st, IncidentTemplate.get("gojo_chain_1"), new Sites.Site(board.offset(0, 0, -210), 1, 0),
                        level.getGameTime(), RandomSource.create(7));
                Investigations.readBoard(p, level, board);
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("story3_board");
            ctx.runOnClient(mc -> ((NewsBoardScreen) mc.gui.screen()).read(0));
            ctx.waitTicks(4);
            ctx.takeScreenshot("story4_report");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);

            // The five new places, built where their events happen.
            String[][] sites = {{"gojo_chain_1", "tower"}, {"yuji_chain_1", "theater"}, {"hakari_chain_1", "storehouse"}, {"ryu_chain_1", "crater"},
                    {"yuta_chain_4", "chapel"}};
            for (int i = 0; i < sites.length; i++) {
                String template = sites[i][0];
                int off = 60 + i * 60;
                int[] view = server.computeOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                    ServerLevel level = (ServerLevel) p.level();
                    InvestigationState st = InvestigationState.get(srv);
                    BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            p.blockPosition().offset(off, 0, 40));
                    InvestigationState.Village v = Investigations.village(level, at.offset(-80, 0, 0));
                    Incident in = Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(at, 1, 0), level.getGameTime(),
                            RandomSource.create(3));
                    Investigations.buildForTest(level, in);
                    return new int[] {at.getX(), at.getY(), at.getZ()};
                });
                server.runCommand("tp @a " + (view[0] + 14) + " " + (view[1] + 7) + " " + (view[2] - 14) + " facing " + view[0] + " " + (view[1] + 3) + " " + view[2]);
                ctx.waitTicks(40);
                ctx.takeScreenshot("story5_site_" + sites[i][1]);
            }

            // The personal trials' realms (test runs in Creative: nothing is claimed).
            for (String kit : new String[] {"yuji", "ryu", "hakari", "yuta"}) {
                server.runCommand("execute as @p run jjk story trial " + kit);
                ctx.waitTicks(130);
                String dim = ctx.computeOnClient(mc -> mc.level.dimension().identifier().toString());
                if (!dim.equals(CursedRealms.DIMENSION.identifier().toString())) throw new AssertionError(kit + ": in the realm dimension: " + dim);
                ctx.takeScreenshot("story6_trial_" + kit);
                server.runCommand("execute as @p run jjk story complete");
                ctx.waitTicks(12);
                ctx.takeScreenshot("story7_complete_" + kit);
                ctx.waitTicks(90);
            }

            // Gojo: put the Infused Blindfold on, and the trial takes you; then the blindfold comes off.
            server.runCommand("item replace entity @a armor.head with jjk:infused_blindfold");
            ctx.waitTicks(30);
            if (!ctx.computeOnClient(mc -> StoryOverlay.blind())) throw new AssertionError("the blindfold's vision is on");
            ctx.takeScreenshot("story8_blindfold_world");
            ctx.waitTicks(200);
            ctx.takeScreenshot("story9_trial_gojo_blind");
            server.runOnServer(srv -> ServerPlayNetworking.send(srv.getPlayerList().getPlayers().getFirst(),
                    new StoryPayload(StoryPayload.SIX_EYES, "", "", 0, 120)));
            ctx.waitTicks(30);
            ctx.takeScreenshot("story10_six_eyes");
            ctx.waitTicks(30);
            ctx.takeScreenshot("story11_six_eyes_late");
            server.runCommand("execute as @p run jjk story complete");
            ctx.waitTicks(100);
            ctx.takeScreenshot("story12_home");
        }
    }
}
