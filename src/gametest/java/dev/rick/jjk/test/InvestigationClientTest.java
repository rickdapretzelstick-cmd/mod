package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.investigation.NewsBoardScreen;
import dev.rick.jjk.client.mastery.MasteryScreen;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.Arrays;

/**
 * Investigations through a real client: the three common curses drawn from their models (through Cursed Glasses), the
 * Mastery screen's technique and tool tabs, a village news board and a note read in full, and a cursed realm entered
 * (in a real world it is its own dimension). Opt-in: screenshots in build/run/clientGameTest/screenshots as inv*.png.
 */
public class InvestigationClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("item replace entity @a armor.head with jjk:cursed_glasses");
            ctx.waitTicks(40);
            // The common curses, side by side in front of the camera (Creative: they leave the player alone).
            server.runCommand("tp @a 0 ~ 0 0 10");
            ctx.waitTicks(10);
            server.runCommand("execute at @a run summon jjk:fly_head ~-2 ~1.6 ~4 {NoAI:1b,Rotation:[180f,0f]}");
            server.runCommand("execute at @a run summon jjk:school_crawler ~0 ~ ~5 {NoAI:1b,Rotation:[180f,0f]}");
            server.runCommand("execute at @a run summon jjk:school_maw ~2.5 ~ ~4.5 {NoAI:1b,Rotation:[180f,0f]}");
            ctx.waitTicks(30);
            ctx.takeScreenshot("inv1_common_curses");
            server.runCommand("kill @e[type=jjk:fly_head]");
            server.runCommand("kill @e[type=jjk:school_crawler]");
            server.runCommand("kill @e[type=jjk:school_maw]");

            // Mastery: Gojo's tree (owned) and the Slaughter Demon's (in hand), some of each developed.
            server.runCommand("gamemode survival @a");
            server.runCommand("jjk kit grant gojo @p");
            server.runCommand("jjk mastery give @p technique/gojo 700");
            for (String n : new String[] {"red_focus", "red_limitless", "quick_hands", "face_grater", "blue_reach", "thin_infinity"}) {
                server.runCommand("jjk mastery buy @p technique/gojo " + n);
            }
            server.runCommand("item replace entity @a weapon.mainhand with jjk:slaughter_demon");
            server.runCommand("jjk mastery give @p tool/slaughter_demon 40");
            server.runCommand("jjk mastery buy @p tool/slaughter_demon keen_edge");
            server.runCommand("jjk mastery buy @p tool/slaughter_demon flurry");
            ctx.waitTicks(20);
            TestInput in = ctx.getInput();
            KeyMapping mastery = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.mastery"))
                    .findFirst().orElseThrow());
            in.pressKey(mastery);
            ctx.waitTicks(10);
            String tabs = ctx.computeOnClient(mc -> mc.gui.screen() instanceof MasteryScreen s ? String.join(",", s.tabIds()) : "none");
            if (!tabs.equals("technique/gojo,tool/slaughter_demon")) throw new AssertionError("tabs: " + tabs);
            ctx.runOnClient(mc -> ((MasteryScreen) mc.gui.screen()).select("red_limitless"));
            ctx.waitTicks(4);
            ctx.takeScreenshot("inv2_mastery_technique");
            ctx.runOnClient(mc -> {
                MasteryScreen s = (MasteryScreen) mc.gui.screen();
                s.selectTab(1);
                s.select("flurry");
            });
            ctx.waitTicks(4);
            ctx.takeScreenshot("inv3_mastery_tool");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);

            // A village news board with a report on it.
            server.runCommand("execute at @a run setblock ~ ~ ~3 jjk:news_board[facing=north]");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                BlockPos board = p.blockPosition().offset(0, 0, 3);
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, board.offset(4, 0, 0));
                Investigations.create(level, v, st, IncidentTemplate.get("livestock"), new Sites.Site(board.offset(150, 0, -120), 1, 0),
                        level.getGameTime(), RandomSource.create(4));
                Investigations.create(level, v, st, IncidentTemplate.get("old_mine"), new Sites.Site(board.offset(-90, 0, 200), 1, 0),
                        level.getGameTime(), RandomSource.create(9));
                Investigations.readBoard(p, level, board);
            });
            ctx.waitTicks(10);
            boolean board = ctx.computeOnClient(mc -> mc.gui.screen() instanceof NewsBoardScreen s && !s.board().notes().isEmpty());
            if (!board) throw new AssertionError("the news board opened with its notices");
            ctx.takeScreenshot("inv4_news_board");
            ctx.runOnClient(mc -> ((NewsBoardScreen) mc.gui.screen()).read(0));
            ctx.waitTicks(4);
            ctx.takeScreenshot("inv5_news_note");
            ctx.runOnClient(mc -> ((NewsBoardScreen) mc.gui.screen()).investigate(0));
            ctx.waitTicks(4);
            ctx.takeScreenshot("inv5b_news_note_investigating");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);

            // A cursed realm: the cliff, broken off in a red void.
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(60, 0, 60));
                Incident inc = Investigations.create(level, v, st, IncidentTemplate.get("cliff_fall"), new Sites.Site(p.blockPosition(), 1, 0),
                        level.getGameTime(), RandomSource.create(2));
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                Investigations.begin(level, p, inc, inc.def(), st, level.getGameTime());
            });
            ctx.waitTicks(100);
            String dim = ctx.computeOnClient(mc -> mc.level.dimension().identifier().toString());
            ctx.takeScreenshot("inv6_cursed_realm");
            if (!dim.equals(CursedRealms.DIMENSION.identifier().toString())) throw new AssertionError("in the realm dimension: " + dim);
        }
    }
}
