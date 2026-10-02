package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;

import java.util.Arrays;

/**
 * Opt-in: True Love Beam filmed through its whole life from the angles it has to read from: Rika's charge with its path
 * traced, then the 5-second blast from behind Yuta, from the side, head-on from just past its reach, and from far away.
 * Screenshots land in build/run/clientGameTest/screenshots as beam_*.png.
 */
public class TrueLoveBeamViewClientTest implements FabricClientGameTest {
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
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 6");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runCommand("jjk character yuta @a");
            ctx.waitTicks(20);
            if (!ctx.computeOnClient(mc -> "yuta".equals(ClientState.character))) throw new AssertionError("not Yuta");
            TestInput in = ctx.getInput();
            KeyMapping s3 = key(ctx, "key.jjk.skill_3"), s5 = key(ctx, "key.jjk.skill_5"), ult = key(ctx, "key.jjk.ultimate");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.runOnClient(mc -> ShowcaseCamera.set(25, 9f, 3f, 8f));
            // True Love: Rika fully manifested.
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            in.pressKey(ult);
            ctx.waitTicks(90);
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("True Love awakens him");
            server.runCommand("execute as @a at @s rotated ~ 0 run summon jjk:training_dummy ^ ^ ^34");
            ctx.waitTicks(16);
            // Her moveset, then the beam.
            in.pressKey(s5);
            ctx.waitTicks(6);
            in.pressKey(s3);
            ctx.waitTicks(30);
            ctx.takeScreenshot("beam_0_charge_telegraph");
            ctx.waitTicks(36);
            // Firing (it holds for 5 seconds).
            shot(ctx, "beam_1_behind", 0, 8f, 3.2f, 8f);
            shot(ctx, "beam_2_side", 90, 16f, 1.5f, 12f);
            shot(ctx, "beam_3_head_on", 180, 47f, 2.4f, 0f);
            shot(ctx, "beam_4_far", 60, 70f, 22f, 20f);
            shot(ctx, "beam_5_impact", 120, 12f, 3f, 32f);
            // Still full thickness near its end (about 4.3 s in).
            shot(ctx, "beam_6_late", 30, 12f, 4f, 10f);
        }
    }

    private static void shot(ClientGameTestContext ctx, String name, float angle, float distance, float height, float lookAhead) {
        ctx.runOnClient(mc -> ShowcaseCamera.set(angle, distance, height, lookAhead));
        ctx.waitTicks(3);
        ctx.takeScreenshot(name);
        ctx.waitTicks(10);
    }

    private static KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
