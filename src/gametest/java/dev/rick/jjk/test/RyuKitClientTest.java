package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.hud.RyuHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/**
 * Opt-in: Ryu played through his keys: the Overheat bar, Granite Blast tapped and held, Appetizer's ray, Restyle's
 * comb from 100%, then Every Last Drop from 85% (its beam, then Decadence). Screenshots land in
 * build/run/clientGameTest/screenshots as ryu_*.png.
 */
public class RyuKitClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            TestInput in = ctx.getInput();
            in.resizeWindow(1280, 720);
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runCommand("jjk character ryu @a");
            ctx.waitTicks(20);
            if (!ctx.computeOnClient(mc -> "ryu".equals(ClientState.character))) throw new AssertionError("not Ryu");
            KeyMapping s1 = key(ctx, "key.jjk.skill_1"), s4 = key(ctx, "key.jjk.skill_4"), s5 = key(ctx, "key.jjk.skill_5"),
                    ult = key(ctx, "key.jjk.ultimate");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^12");
            ctx.waitTicks(20);

            // Granite Blast, tapped.
            ShowcaseCamera.set(70, 7f, 2f, 5f);
            in.pressKey(s1);
            ctx.waitTicks(3);
            ctx.takeScreenshot("ryu_0_granite_tap");
            ctx.waitTicks(20);
            // Held to charge: it pierces.
            in.holdKey(s1);
            ctx.waitTicks(30);
            ctx.takeScreenshot("ryu_1_granite_charging");
            in.releaseKey(s1);
            ctx.waitTicks(2);
            ctx.takeScreenshot("ryu_2_granite_held");
            ctx.waitTicks(20);
            float heat = ctx.computeOnClient(mc -> RyuHud.heat);
            if (heat < 50) throw new AssertionError("Overheat shown on the HUD (" + heat + ")");
            // Appetizer: two blasts, then the ray from the ground.
            in.pressKey(s4);
            ctx.waitTicks(33);
            ShowcaseCamera.set(90, 12f, 3f, 7f);
            ctx.waitTicks(1);
            ctx.takeScreenshot("ryu_3_appetizer_ray");
            ctx.waitTicks(30);
            // Overheated: the bar flashes, the comb cools him all the way.
            server.runCommand("execute as @a run jjk awakening 0");
            server.runOnServer(s -> dev.rick.jjk.ryu.RyuCombat.setHeat(s.getPlayerList().getPlayers().getFirst(), 100));
            ctx.waitTicks(10);
            ShowcaseCamera.off();
            ctx.waitTicks(2);
            ctx.takeScreenshot("ryu_4_overheated_hud");
            ShowcaseCamera.set(160, 4f, 1.6f, 0f);
            in.pressKey(s5);
            ctx.waitTicks(25);
            ctx.takeScreenshot("ryu_5_restyle_comb");
            ctx.waitTicks(50);
            // Every Last Drop from 85%: the charge, the beam, and Decadence.
            server.runOnServer(s -> dev.rick.jjk.ryu.RyuCombat.setHeat(s.getPlayerList().getPlayers().getFirst(), 85));
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            ShowcaseCamera.set(60, 8f, 2.4f, 6f);
            in.pressKey(ult);
            ctx.waitTicks(30);
            ctx.takeScreenshot("ryu_6_eld_charge");
            ctx.waitTicks(24);
            ShowcaseCamera.set(90, 14f, 2.5f, 12f);
            ctx.waitTicks(2);
            ctx.takeScreenshot("ryu_7_eld_side");
            ShowcaseCamera.set(15, 6f, 2.2f, 14f);
            ctx.waitTicks(3);
            ctx.takeScreenshot("ryu_8_eld_behind");
            ctx.waitTicks(50);
            ShowcaseCamera.off();
            ctx.waitTicks(10);
            ctx.takeScreenshot("ryu_9_decadence");
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("Every Last Drop from 85% awakens him");
        }
    }

    private static KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
