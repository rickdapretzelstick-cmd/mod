package dev.rick.jjk.test;

import dev.rick.jjk.battle.game.BattleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * A turn-based battle against the AI, driven through the menu with key presses: the menu, the camera while choosing,
 * and each turn's scenes. Opt-in; screenshots battle_*.png.
 */
public class BattleClientTest implements FabricClientGameTest {
    // GLFW key codes.
    private static final int K_1 = 49, K_2 = 50, K_3 = 51, K_W = 87, K_ENTER = 257, K_EQUAL = 61;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            ctx.waitTicks(30);
            server.runCommand("execute as @a at @s run jjk battle ai yuji gojo");
            ctx.waitTicks(40);
            ctx.takeScreenshot("battle_00_menu");
            TestInput in = ctx.getInput();
            // Turn 1 (mid range): Cursed Strikes, boosted once.
            in.pressKey(K_3);
            in.pressKey(K_EQUAL);
            ctx.waitTicks(4);
            ctx.takeScreenshot("battle_01_picked");
            in.pressKey(K_ENTER);
            shootTurn(ctx, "battle_t1");
            // Turn 2: the combo if close, else close in.
            in.pressKey(K_1);
            in.pressKey(K_2);
            in.pressKey(K_W);
            ctx.waitTicks(4);
            ctx.takeScreenshot("battle_02_menu");
            in.pressKey(K_ENTER);
            shootTurn(ctx, "battle_t2");
            for (int t = 3; t <= 6; t++) {
                boolean over = ctx.computeOnClient(mc -> dev.rick.jjk.client.battle.BattleClient.state() == null);
                if (over) break;
                in.pressKey(K_1);
                in.pressKey(K_2);
                in.pressKey(K_3);
                in.pressKey(K_ENTER);
                shootTurn(ctx, "battle_t" + t);
            }
            server.runOnServer(srv -> BattleManager.sessions().forEach(s -> {}));
        }
    }

    /** Screenshots through one turn's playback. */
    private static void shootTurn(ClientGameTestContext ctx, String name) {
        for (int i = 0; i < 6; i++) {
            ctx.waitTicks(8);
            ctx.takeScreenshot(name + "_" + i);
        }
        ctx.waitTicks(60);
    }
}
