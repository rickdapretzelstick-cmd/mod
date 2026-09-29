package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.hud.CharacterSelectScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/**
 * Yuji through a real client with real input: pick Vessel on the character select screen, use every base move (the
 * Black Flash timed by pressing Divergent Fist again), awaken the King of Curses, use Shrine's slashes, Cleave, Dismantle,
 * Open, Rush, World Cutting Slash's chant, and expand Malevolent Shrine. Screenshots land in
 * build/run/clientGameTest/screenshots.
 */
public class YujiClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 8");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            ctx.waitTicks(30);
            TestInput in = ctx.getInput();

            // --- Character select: K opens it, click Yuji's card. ---
            in.pressKey(key(ctx, "key.jjk.character_menu"));
            ctx.waitTicks(12);
            if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof CharacterSelectScreen)) throw new AssertionError("K opens the character select screen");
            ctx.takeScreenshot("y01_character_select");
            clickCard(ctx, in, "yuji");
            ctx.waitTicks(10);
            if (!ctx.computeOnClient(mc -> "yuji".equals(ClientState.character))) throw new AssertionError("clicking Yuji's card makes you Vessel");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            KeyMapping s1 = key(ctx, "key.jjk.skill_1"), s2 = key(ctx, "key.jjk.skill_2"), s3 = key(ctx, "key.jjk.skill_3");
            KeyMapping s4 = key(ctx, "key.jjk.skill_4"), s5 = key(ctx, "key.jjk.skill_5"), ult = key(ctx, "key.jjk.ultimate");

            // --- Base kit ---
            dummy(ctx, server, 5);
            ctx.takeScreenshot("y02_vessel_hud");
            in.pressKey(s1);
            ctx.waitTicks(12);
            ctx.takeScreenshot("y03_cursed_strikes_slide");
            ctx.waitTicks(14);
            ctx.takeScreenshot("y04_cursed_strikes_flurry");
            ctx.waitTicks(30);
            dummy(ctx, server, 2.4);
            in.pressKey(s2);
            ctx.waitTicks(10);
            ctx.takeScreenshot("y05_crushing_blow");
            ctx.waitTicks(30);
            dummy(ctx, server, 2.6);
            in.pressKey(s3);
            ctx.waitTicks(8);
            ctx.takeScreenshot("y06_divergent_windup");
            ctx.waitTicks(4);
            ctx.takeScreenshot("y07_divergent_impact");
            ctx.waitTicks(30);
            dummy(ctx, server, 2.6);
            in.pressKey(s3);
            ctx.waitTicks(6);
            in.pressKey(s3);
            ctx.waitTicks(4);
            ctx.takeScreenshot("y08_black_flash");
            ctx.waitTicks(30);
            in.pressKey(s4);
            ctx.waitTicks(4);
            ctx.takeScreenshot("y09_manji_stance");
            ctx.waitTicks(24);
            in.pressKey(s2);
            ctx.waitTicks(2);
            in.pressKey(s5);
            ctx.waitTicks(3);
            ctx.takeScreenshot("y10_combat_instincts_feint");
            ctx.waitTicks(20);

            // --- King of Curses ---
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            in.pressKey(ult);
            ctx.waitTicks(20);
            ctx.takeScreenshot("y11_king_of_curses");
            ctx.waitTicks(30);
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("Sukuna took over");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(4);
            ctx.takeScreenshot("y12_sukuna_face");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            dummy(ctx, server, 7);
            in.pressMouse(0);
            ctx.waitTicks(3);
            ctx.takeScreenshot("y13_shrine_m1");
            ctx.waitTicks(20);
            dummy(ctx, server, 6);
            in.pressKey(s1);
            ctx.waitTicks(11);
            ctx.takeScreenshot("y14_dismantle");
            ctx.waitTicks(30);
            dummy(ctx, server, 2.6);
            in.pressKey(s5);
            ctx.waitTicks(22);
            ctx.takeScreenshot("y15_cleave");
            ctx.waitTicks(30);
            dummy(ctx, server, 12);
            in.pressKey(s2);
            ctx.waitTicks(28);
            ctx.takeScreenshot("y16_open_draw");
            ctx.waitTicks(20);
            ctx.takeScreenshot("y17_open_pillar");
            ctx.waitTicks(40);
            dummy(ctx, server, 6);
            in.pressKey(s3);
            ctx.waitTicks(16);
            ctx.takeScreenshot("y18_rush");
            ctx.waitTicks(40);
            // World Cutting Slash: Dismantle, Rush in its wind-up, Open, Cleave.
            dummy(ctx, server, 9);
            in.pressKey(s1);
            ctx.waitTicks(2);
            in.pressKey(s3);
            ctx.waitTicks(10);
            ctx.takeScreenshot("y19_wcs_scale_of_the_dragon");
            in.pressKey(s2);
            ctx.waitTicks(10);
            in.pressKey(s5);
            ctx.waitTicks(8);
            ctx.takeScreenshot("y20_wcs_twin_meteors");
            ctx.waitTicks(10);
            ctx.takeScreenshot("y21_world_cutting_slash");
            ctx.waitTicks(40);
            server.runCommand("execute as @a run jjk restore now");
            // Malevolent Shrine.
            dummy(ctx, server, 5);
            in.pressKey(s4);
            ctx.waitTicks(12);
            ctx.takeScreenshot("y22_shrine_sign");
            ctx.waitTicks(80);
            ctx.takeScreenshot("y23_malevolent_shrine");
            ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180));
            ctx.waitTicks(10);
            ctx.takeScreenshot("y24_malevolent_shrine_temple");
            ctx.waitTicks(20);
            server.runCommand("execute as @a run jjk domain cancel");
            server.runCommand("execute as @a run jjk awakening end");
            ctx.waitTicks(60);
            server.runCommand("execute as @a run jjk restore now");
            ctx.waitTicks(10);
        }
    }

    private static void dummy(ClientGameTestContext ctx, TestServerContext server, double distance) {
        server.runCommand("execute as @a run jjk restore now");
        server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
        server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^" + distance);
        ctx.waitTicks(16);
    }

    private static void clickCard(ClientGameTestContext ctx, TestInput in, String id) {
        double[] at = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof CharacterSelectScreen s)) throw new AssertionError("select screen not open");
            int[] c = s.cardCenter(id);
            double scale = mc.getWindow().getGuiScale();
            return new double[] {c[0] * scale, c[1] * scale};
        });
        in.setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        in.pressMouse(0);
    }

    private static KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
