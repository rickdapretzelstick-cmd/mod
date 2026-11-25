package dev.rick.jjk.test;

import dev.rick.jjk.battle.BattleMove;
import dev.rick.jjk.battle.BattleState;
import dev.rick.jjk.battle.Fighter;
import dev.rick.jjk.battle.Plan;
import dev.rick.jjk.battle.game.BattleManager;
import dev.rick.jjk.battle.game.BattleSession;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * A control sequence at full control: Yuji up close, Gojo focusing, Divergent Fist → Manji Kick → Crushing Blow picked
 * through the menu, then the Black Flash finisher. Screenshots every 3 ticks through it. Opt-in; ctl_*.png.
 */
public class ControlSequenceClientTest implements FabricClientGameTest {
    private static final int K_1 = 49;

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
            ctx.waitTicks(5);
            server.runOnServer(srv -> {
                BattleSession b = BattleManager.sessions().getFirst();
                b.holdAiForTest = true;
                BattleState s = b.stateForTest();
                s.range = BattleMove.Range.CLOSE;
                s.f[0].control = Fighter.MAX_CONTROL;
                s.f[0].ce = 100;
                s.f[1].ce = 0; // no reversal
                b.forcePickForTest(1, Plan.of("focus"));
                b.restageForTest();
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("ctl_00_menu");
            TestInput in = ctx.getInput();
            in.pressKey(K_1); // FIGHT
            in.pressKey(K_1); // Divergent Fist -> COMBO
            ctx.waitTicks(2);
            ctx.takeScreenshot("ctl_01_combo");
            in.pressKey(K_1); // Manji Kick
            ctx.waitTicks(2);
            ctx.takeScreenshot("ctl_02_combo");
            in.pressKey(K_1); // Crushing Blow (three moves: locks in)
            for (int i = 0; i < 40; i++) {
                ctx.waitTicks(3);
                ctx.takeScreenshot(String.format("ctl_%02d", 10 + i));
            }
        }
    }
}
