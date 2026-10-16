package dev.rick.jjk.test;

import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/**
 * Vessel's Cursed Strikes through every beat (windup, dash, rush, last punch), from behind and from the front.
 * Opt-in; screenshots cs_*.png.
 */
public class CursedStrikesClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runOnServer(srv -> CharacterService.assign(srv.getPlayerList().getPlayers().getFirst(), Characters.get("yuji")));
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
            TestInput in = ctx.getInput();
            KeyMapping s1 = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.skill_1")).findFirst()
                    .orElseThrow(() -> new AssertionError("missing key key.jjk.skill_1")));
            for (CameraType cam : new CameraType[] {CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT}) {
                String v = cam == CameraType.THIRD_PERSON_BACK ? "back" : "front";
                ctx.runOnClient(mc -> mc.options.setCameraType(cam));
                server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 8");
                server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
                server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^5");
                ctx.waitTicks(16);
                in.pressKey(s1);
                ctx.waitTicks(3);
                ctx.takeScreenshot("cs_" + v + "_0_reach");
                ctx.waitTicks(4);
                ctx.takeScreenshot("cs_" + v + "_1_low");
                ctx.waitTicks(4);
                ctx.takeScreenshot("cs_" + v + "_2_dash");
                ctx.waitTicks(8);
                ctx.takeScreenshot("cs_" + v + "_3_rush");
                ctx.waitTicks(9);
                ctx.takeScreenshot("cs_" + v + "_4_rush");
                ctx.waitTicks(16);
                ctx.takeScreenshot("cs_" + v + "_5_windback");
                ctx.waitTicks(3);
                ctx.takeScreenshot("cs_" + v + "_6_final");
                ctx.waitTicks(2);
                ctx.takeScreenshot("cs_" + v + "_7_burst");
                ctx.waitTicks(30);
                server.runCommand("execute as @a run jjk restore now");
            }
        }
    }
}
