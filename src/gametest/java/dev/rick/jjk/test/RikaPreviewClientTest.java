package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.yuta.RikaEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rika's model seen from the front, the side and behind, at rest and in an axis-check pose. Opt-in (list it under
 * {@code fabric-client-gametest} to run); screenshots {@code rika_<view>_<pose>}.
 */
public class RikaPreviewClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
            ctx.waitTicks(20);
            int[] rika = new int[1];
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var dummy = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, p.level());
                dummy.setPos(p.getX(), p.getY(), p.getZ() + 7);
                dummy.setNoAi(true);
                p.level().addFreshEntity(dummy);
                RikaEntity r = RikaEntity.summon(p.level(), dummy);
                r.set(RikaEntity.FULL, true);
                rika[0] = r.getId();
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            float[] yaws = {180, 90, 0, -45};
            String[] views = {"front", "side", "back", "threequarter"};
            for (int pose = 0; pose < 2; pose++) {
                if (pose == 1) ctx.runOnClient(mc -> ClientAnimations.play(rika[0], "rika_calibrate", 1f, mc.level.getGameTime()));
                for (int v = 0; v < yaws.length; v++) {
                    float yaw = yaws[v];
                    server.runOnServer(s -> {
                        for (var e : s.overworld().getAllEntities()) {
                            if (e instanceof dev.rick.jjk.entity.TrainingDummy d) {
                                d.setYRot(yaw);
                                d.setYHeadRot(yaw);
                                d.setYBodyRot(yaw);
                            }
                        }
                    });
                    ctx.waitTicks(25);
                    ctx.takeScreenshot("rika_" + views[v] + "_" + (pose == 0 ? "rest" : "calibrate"));
                }
            }
        }
    }
}
