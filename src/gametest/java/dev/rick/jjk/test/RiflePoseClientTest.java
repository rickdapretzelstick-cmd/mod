package dev.rick.jjk.test;

import dev.rick.jjk.client.rifle.RifleItemRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Tuning aid (not in the suite): the held rifle in third person from the side, at a few angles. */
public class RiflePoseClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("item replace entity @a weapon.mainhand with jjk:cursed_rifle");
            server.runCommand("tp @a 0 ~ 0 180 10");
            server.runCommand("execute at @a run summon armor_stand ~ ~ ~-3 {ShowArms:1b,NoGravity:1b,Rotation:[90f,0f]}");
            ctx.waitTicks(5);
            server.runCommand("item replace entity @e[type=armor_stand] weapon.mainhand with jjk:cursed_rifle");
            ctx.waitTicks(40);
            int[][] rots = {{0, 0, 0}, {0, 90, 0}, {0, -90, 0}, {90, 0, 0}, {-90, 0, 0}, {0, 90, 90}, {0, -90, -90}, {0, 180, 0}};
            for (int[] r : rots) {
                ctx.runOnClient(mc -> RifleItemRenderer.THIRD_PERSON = new float[] {r[0], r[1], r[2]});
                ctx.waitTicks(3);
                ctx.takeScreenshot("pose_" + r[0] + "_" + r[1] + "_" + r[2]);
            }
        }
    }
}
