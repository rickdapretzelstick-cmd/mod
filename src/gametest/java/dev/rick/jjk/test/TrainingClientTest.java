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
                // The room itself: from high over its entrance toward its feature wall, then the participant's own view.
                net.minecraft.world.phys.Vec3[] views = server.computeOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                    var a = dev.rick.jjk.progression.investigation.CursedRealms.arenaHere(p);
                    var plan = dev.rick.jjk.progression.training.TrialRooms.plan(kit);
                    if (a == null || plan == null) return null;
                    var o = new net.minecraft.world.phys.Vec3(a.origin.getX(), a.origin.getY(), a.origin.getZ());
                    int[] b = plan.bounds();
                    double cx = (b[0] + b[3]) / 2.0, cz = (b[2] + b[5]) / 2.0;
                    var eye = o.add(plan.marker("player")).add(0, 1.62, 0);
                    return new net.minecraft.world.phys.Vec3[] {o.add(cx + (b[3] - b[0]) * 0.45, b[4] + 7, b[2] - 5), o.add(cx, 1.5, cz + 2),
                            o.add(cx - (b[3] - b[0]) * 0.45, b[4] + 4, b[5] + 4), o.add(cx, 1.5, cz - 2), eye, eye.add(0, 0.4, 10)};
                });
                if (views != null) {
                    for (int v = 0; v < 3; v++) {
                        final int k = v;
                        ctx.runOnClient(mc -> dev.rick.jjk.client.training.TrainingCamera.viewForTest(views[k * 2], views[k * 2 + 1]));
                        ctx.waitTicks(6);
                        ctx.takeScreenshot("training_" + kit + "_room" + (v == 0 ? "" : v == 1 ? "_back" : "_eyes"));
                    }
                    ctx.runOnClient(mc -> dev.rick.jjk.client.training.TrainingCamera.viewForTest(null, null));
                }
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
