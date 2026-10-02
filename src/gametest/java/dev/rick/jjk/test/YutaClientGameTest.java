package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.hud.CharacterSelectScreen;
import dev.rick.jjk.client.hud.YutaHud;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Arrays;

/**
 * Cursed Partners through a real client with real input: pick Yuta on the select screen; katana M1s, Severing Path,
 * Resolute Slash and its Black Flash, a held Outburst, Second Wind; summon Rika, switch to her moveset and use Rika Smash,
 * Rika Haymaker and Rika Launch; awaken True Love; Steel Arm M1s, Elbow Rush, the Copy Wheel and Cursed Speech, Energy
 * Ripple; awakened Rika's Downslam, Slam, True Love Beam and Throw; and Authentic Mutual Love with a katana taken up.
 * Screenshots land in build/run/clientGameTest/screenshots.
 */
public class YutaClientGameTest implements FabricClientGameTest {
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
            // Where he stands for every segment (his moves carry him about the arena).
            double[] home = ctx.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            HOME[0] = home[0];
            HOME[1] = home[1];
            HOME[2] = home[2];

            // --- Character select: Yuta's card ---
            in.pressKey(key(ctx, "key.jjk.character_menu"));
            ctx.waitTicks(12);
            if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof CharacterSelectScreen)) throw new AssertionError("K opens the character select screen");
            ctx.takeScreenshot("t01_character_select");
            clickCard(ctx, in, "yuta");
            ctx.waitTicks(10);
            if (!ctx.computeOnClient(mc -> "yuta".equals(ClientState.character))) throw new AssertionError("clicking Yuta's card makes you Cursed Partners");
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            // A side-on film camera, so the target isn't hidden behind him.
            ctx.runOnClient(mc -> ShowcaseCamera.set(60, 7f, 2.4f, 3.5f));
            KeyMapping s1 = key(ctx, "key.jjk.skill_1"), s2 = key(ctx, "key.jjk.skill_2"), s3 = key(ctx, "key.jjk.skill_3");
            KeyMapping s4 = key(ctx, "key.jjk.skill_4"), s5 = key(ctx, "key.jjk.skill_5"), ult = key(ctx, "key.jjk.ultimate");
            // The attack key itself (a raw click is spent grabbing the mouse in a headless window).
            KeyMapping m1 = ctx.computeOnClient(mc -> mc.options.keyAttack);

            // --- Swordsmanship: the katana comes out for the M1s ---
            dummy(ctx, server, 3.2);
            ctx.takeScreenshot("t02_cursed_partners_hud");
            float full = dummyHealth(ctx);
            for (int k = 0; k < 4; k++) {
                in.pressKey(m1);
                ctx.waitTicks(k == 1 ? 3 : 8);
                if (k == 1) ctx.takeScreenshot("t03_katana_m1");
            }
            ctx.waitTicks(4);
            float after = dummyHealth(ctx);
            int combo = ctx.computeOnClient(mc -> ClientState.comboCount);
            System.out.println("[yuta-test] dummy " + full + " -> " + after + ", combo " + combo);
            if (after >= full && combo == 0) throw new AssertionError("katana M1s hurt the dummy");

            // --- Severing Path: the slide, the lock and the three swings ---
            dummy(ctx, server, 4.5);
            in.pressKey(s1);
            ctx.waitTicks(10);
            ctx.takeScreenshot("t04_severing_path_slide");
            ctx.waitTicks(10);
            ctx.takeScreenshot("t05_severing_path_swings");
            ctx.waitTicks(30);

            // --- Resolute Slash, then again as he reappears: the Resolute Black Flash ---
            dummy(ctx, server, 6);
            in.pressKey(s2);
            ctx.waitTicks(6);
            ctx.takeScreenshot("t06_resolute_vanish");
            ctx.waitTicks(4);
            in.pressKey(s2);
            ctx.waitTicks(10);
            ctx.takeScreenshot("t07_resolute_black_flash");
            ctx.waitTicks(30);

            // --- Outburst, held to its last stage ---
            dummy(ctx, server, 3);
            in.holdKey(s3);
            ctx.waitTicks(36);
            ctx.takeScreenshot("t08_outburst_charged");
            ctx.waitTicks(10);
            in.releaseKey(s3);
            ctx.waitTicks(5);
            ctx.takeScreenshot("t09_outburst_burst");
            ctx.waitTicks(30);

            // --- Second Wind ---
            dummy(ctx, server, 5);
            in.pressKey(s4);
            ctx.waitTicks(10);
            ctx.takeScreenshot("t10_second_wind_grab");
            ctx.waitTicks(30);

            // --- Rika: summoned at his right, then her moveset ---
            dummy(ctx, server, 6);
            in.pressKey(s5);
            ctx.waitTicks(30);
            if (ctx.computeOnClient(mc -> YutaHud.rikaId < 0 || mc.level.getEntity(YutaHud.rikaId) == null)) throw new AssertionError("the special summons Rika");
            ctx.takeScreenshot("t11_rika_summoned");
            in.pressKey(s5);
            ctx.waitTicks(6);
            if (!ctx.computeOnClient(mc -> YutaHud.rikaMode)) throw new AssertionError("pressing the special again switches to Rika's moveset");
            ctx.takeScreenshot("t12_rika_moveset");
            in.pressKey(s1);
            ctx.waitTicks(12);
            ctx.takeScreenshot("t13_rika_smash");
            ctx.waitTicks(10);
            if (ctx.computeOnClient(mc -> YutaHud.rikaMode)) throw new AssertionError("using one of her moves puts her moveset away");
            ctx.waitTicks(20);
            dummy(ctx, server, 5);
            in.pressKey(s5);
            ctx.waitTicks(8);
            in.pressKey(s3);
            ctx.waitTicks(23);
            ctx.takeScreenshot("t14_rika_haymaker");
            ctx.waitTicks(30);
            in.pressKey(s5);
            ctx.waitTicks(8);
            in.pressKey(s2);
            ctx.waitTicks(6);
            ctx.takeScreenshot("t15_rika_launch");
            ctx.waitTicks(30);

            // --- True Love ---
            dummy(ctx, server, 6);
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            in.pressKey(ult);
            ctx.waitTicks(16);
            ctx.takeScreenshot("t16_true_love_ring");
            ctx.waitTicks(26);
            ctx.takeScreenshot("t17_true_love_casing");
            ctx.waitTicks(30);
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("True Love awakens him");
            ctx.takeScreenshot("t18_true_love_hud");

            // --- Steel Arm M1s, Elbow Rush ---
            dummy(ctx, server, 3.2);
            for (int k = 0; k < 3; k++) {
                in.pressKey(m1);
                ctx.waitTicks(k == 0 ? 6 : 9);
                if (k == 0) ctx.takeScreenshot("t19_steel_arm_jab");
            }
            ctx.waitTicks(20);
            dummy(ctx, server, 7);
            in.pressKey(s1);
            ctx.waitTicks(16);
            ctx.takeScreenshot("t20_elbow_rush_barrage");
            ctx.waitTicks(40);

            // --- The Copy Wheel, Copy: Cursed Speech ---
            dummy(ctx, server, 5);
            in.pressKey(ult);
            ctx.waitTicks(6);
            if (!ctx.computeOnClient(mc -> YutaHud.wheelOpen)) throw new AssertionError("the Awakening key opens the Copy Wheel");
            ctx.takeScreenshot("t21_copy_wheel");
            in.pressKey(s1);
            ctx.waitTicks(4);
            if (ctx.computeOnClient(mc -> YutaHud.wheelOpen)) throw new AssertionError("picking a technique closes the wheel");
            in.pressKey(s2);
            ctx.waitTicks(13);
            ctx.takeScreenshot("t22_cursed_speech");
            ctx.waitTicks(30);

            // --- Energy Ripple ---
            dummy(ctx, server, 4);
            in.pressKey(s3);
            ctx.waitTicks(16);
            ctx.takeScreenshot("t23_energy_ripple");
            ctx.waitTicks(30);

            // --- Awakened Rika ---
            dummy(ctx, server, 6);
            in.pressKey(s5);
            ctx.waitTicks(6);
            in.pressKey(s1);
            ctx.waitTicks(11);
            ctx.takeScreenshot("t24_rika_downslam");
            ctx.waitTicks(40);
            in.pressKey(s5);
            ctx.waitTicks(6);
            in.pressKey(s2);
            ctx.waitTicks(30);
            ctx.takeScreenshot("t25_rika_slam");
            ctx.waitTicks(60);
            dummy(ctx, server, 10);
            in.pressKey(s5);
            ctx.waitTicks(6);
            in.pressKey(s3);
            ctx.waitTicks(30);
            ctx.takeScreenshot("t26_true_love_beam_charge");
            ctx.waitTicks(34);
            ctx.takeScreenshot("t27_true_love_beam");
            // The blast holds for 5 seconds; Rika is busy until it's over.
            ctx.waitTicks(110);
            dummy(ctx, server, 8);
            in.pressKey(s5);
            ctx.waitTicks(6);
            in.pressKey(s4);
            ctx.waitTicks(10);
            ctx.takeScreenshot("t28_rika_throw_windup");
            ctx.waitTicks(14);
            ctx.takeScreenshot("t29_rika_throw_flight");
            ctx.waitTicks(40);

            // --- Authentic Mutual Love: the platform, the blades, one taken up ---
            dummy(ctx, server, 6);
            server.runCommand("execute as @a run jjk restore now");
            in.pressKey(s4);
            ctx.waitTicks(90);
            ctx.takeScreenshot("t30_authentic_mutual_love");
            ctx.waitTicks(30);
            boolean blades = ctx.computeOnClient(mc -> {
                for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == ModEntities.DOMAIN_BLADE) return true;
                return false;
            });
            if (!blades) throw new AssertionError("blades fall inside Authentic Mutual Love");
            ctx.takeScreenshot("t31_domain_blades");
            server.runCommand("execute as @a at @s run tp @s @e[type=jjk:domain_blade,sort=nearest,limit=1]");
            ctx.waitTicks(5);
            ctx.takeScreenshot("t32_blade_run");
            ctx.waitTicks(16);
            ctx.takeScreenshot("t33_blade_swing");
            ctx.waitTicks(20);
            server.runCommand("execute as @a run jjk domain cancel");
            server.runCommand("execute as @a run jjk awakening end");
            ctx.waitTicks(40);
        }
    }

    private static float dummyHealth(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == ModEntities.TRAINING_DUMMY && e instanceof LivingEntity l) return l.getHealth();
            return -1f;
        });
    }

    private static final double[] HOME = new double[3];

    private static void dummy(ClientGameTestContext ctx, TestServerContext server, double distance) {
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 8", HOME[0], HOME[1], HOME[2]));
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
