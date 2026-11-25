package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
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
 * The animation fixes, by eye: a clip ending with both arms raised and twisted blends home along the shortest path (no
 * rolling), and Ryu's Granite Blast fired while looking 40 degrees off his body's facing turns the body to the shot.
 * Opt-in; screenshots fix_*.png.
 */
public class AnimFixClientTest implements FabricClientGameTest {
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
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
            server.runOnServer(srv -> CharacterService.assign(srv.getPlayerList().getPlayers().getFirst(), Characters.get("ryu")));
            ctx.waitTicks(30);
            ctx.runOnClient(mc -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            });

            // 1. The blend out of a held clip ending with both arms up and the left one twisted, from the side, every tick
            //    once it is released (160 ms: about 3 ticks).
            ShowcaseCamera.set(100, 3.6f, 1.4f, 0);
            ShowcaseCamera.enabled = true;
            ctx.runOnClient(mc -> ClientAnimations.play(mc.player.getId(), "ryu_eld_fire", 1f, mc.level.getGameTime()));
            ctx.waitTicks(16);
            ctx.takeScreenshot("fix_blend_0_held");
            ctx.runOnClient(mc -> ClientAnimations.play(mc.player.getId(), "", 1f, mc.level.getGameTime()));
            for (int i = 1; i <= 5; i++) {
                ctx.waitTicks(1);
                ctx.takeScreenshot("fix_blend_" + i);
            }
            ShowcaseCamera.off();
            ctx.waitTicks(20);

            // 2. Granite Blast while looking 40 degrees right of where the body faces.
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.runOnClient(mc -> {
                mc.player.setYRot(40);
                mc.player.setYHeadRot(40);
            });
            ctx.waitTicks(2);
            ctx.takeScreenshot("fix_ryu_0_lagging_body");
            TestInput in = ctx.getInput();
            KeyMapping s1 = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.skill_1")).findFirst()
                    .orElseThrow(() -> new AssertionError("missing key key.jjk.skill_1")));
            float before = ctx.computeOnClient(mc -> mc.player.yBodyRot);
            in.pressKey(s1);
            ctx.waitTicks(3);
            ctx.takeScreenshot("fix_ryu_1_aim");
            float during = ctx.computeOnClient(mc -> mc.player.yBodyRot);
            ctx.waitTicks(4);
            ctx.takeScreenshot("fix_ryu_2_fire");
            ctx.waitTicks(30);
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(during - 40)) > 3)
                throw new AssertionError("body should face the aim while casting: body " + during + " (was " + before + "), head 40");
        }
    }
}
