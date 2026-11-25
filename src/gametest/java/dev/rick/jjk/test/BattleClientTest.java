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
    private static final int K_1 = 49, K_2 = 50, K_3 = 51, K_4 = 52;

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
            ctx.takeScreenshot("battle_00_root");
            TestInput in = ctx.getInput();
            // Turn 1: FIGHT -> Cursed Strikes (reaches from mid range; nothing links after it, so it locks in).
            in.pressKey(K_1);
            ctx.waitTicks(3);
            ctx.takeScreenshot("battle_01_fight");
            in.pressKey(K_3);
            shootTurn(ctx, "battle_t1");
            // Turn 2: DEFEND -> guard.
            in.pressKey(K_2);
            ctx.waitTicks(3);
            ctx.takeScreenshot("battle_02_defend");
            in.pressKey(K_1);
            shootTurn(ctx, "battle_t2");
            // Turn 3: CURSED page, then back, then FIGHT -> Divergent Fist (opens COMBO when close) or Cursed Strikes.
            in.pressKey(K_4);
            ctx.waitTicks(3);
            ctx.takeScreenshot("battle_03_cursed");
            in.pressKey(K_4); // BACK
            in.pressKey(K_1);
            in.pressKey(K_1);
            ctx.waitTicks(3);
            ctx.takeScreenshot("battle_03_combo");
            in.pressKey(K_1);
            in.pressKey(K_3);
            shootTurn(ctx, "battle_t3");
            for (int t = 4; t <= 6; t++) {
                boolean over = ctx.computeOnClient(mc -> dev.rick.jjk.client.battle.BattleClient.state() == null);
                if (over) break;
                in.pressKey(K_1);
                in.pressKey(K_3);
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
