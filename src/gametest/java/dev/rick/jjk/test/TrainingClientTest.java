package dev.rick.jjk.test;

import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.CharacterStory;
import dev.rick.jjk.progression.story.PersonalTrials;
import dev.rick.jjk.progression.training.TrainingSessions;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tuning aid (opt-in): every character's training, seen as the participant sees it: the intro, the challenge with its
 * three gauges, the failure (and the Retry/Leave screen), the victory payoff. Screenshots training_<kit>_*.png. The
 * kits come from JJK_TRAINING (comma separated) or all five.
 */
public class TrainingClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getenv("JJK_TRAINING");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set midnight");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode survival @a");
            ctx.waitTicks(40);
            for (CharacterStory story : CharacterStories.all()) {
                if (only != null && !only.contains(story.kit())) continue;
                String kit = story.kit();
                server.runOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                    PersonalTrials.startNow(p, story, true);
                });
                // The pull, then the intro's push-in.
                ctx.waitTicks(50);
                ctx.takeScreenshot("training_" + kit + "_intro");
                ctx.waitTicks(60);
                ctx.takeScreenshot("training_" + kit + "_active");
                ctx.waitTicks(50);
                ctx.takeScreenshot("training_" + kit + "_active2");
                // The montage's later shots (its phases come later than this test waits): cut to each and look.
                for (int shot = 2; shot <= 3; shot++) {
                    final int s = shot;
                    ctx.runOnClient(mc -> dev.rick.jjk.client.training.TrainingCamera.cutForTest(s));
                    ctx.waitTicks(4);
                    ctx.takeScreenshot("training_" + kit + "_phase" + s);
                    boolean clear = ctx.computeOnClient(mc -> {
                        var cam = mc.gameRenderer.mainCamera().position();
                        var b = net.minecraft.core.BlockPos.containing(cam);
                        return mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty();
                    });
                    if (!clear) throw new AssertionError(kit + " phase " + s + ": the shot sits in a block");
                }
                ctx.runOnClient(mc -> dev.rick.jjk.client.training.TrainingCamera.cutForTest(1));
                server.runOnServer(srv -> TrainingSessions.failNow(srv.getPlayerList().getPlayers().get(0)));
                ctx.waitTicks(14);
                ctx.takeScreenshot("training_" + kit + "_failure");
                ctx.waitTicks(40);
                ctx.takeScreenshot("training_" + kit + "_retry_screen");
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                server.runOnServer(srv -> TrainingSessions.completeNow(srv.getPlayerList().getPlayers().get(0)));
                ctx.waitTicks(30);
                ctx.takeScreenshot("training_" + kit + "_victory");
                ctx.waitTicks(60);
                server.runOnServer(srv -> TrainingSessions.leaveNow(srv.getPlayerList().getPlayers().get(0)));
                ctx.waitTicks(30);
                ctx.takeScreenshot("training_" + kit + "_home");
            }
        }
    }
}
