package dev.rick.jjk.test;

import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.Arrays;

/**
 * Drives a real client through Gojo's kit with real key/mouse input and takes screenshots of each technique.
 * Screenshots land in build/run/clientGameTest/screenshots.
 */
public class GojoClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection();
            var server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a run jjk nocooldown true");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 10");
            ctx.waitTicks(40);
            boolean gojo = ctx.computeOnClient(mc -> dev.rick.jjk.client.ClientState.hasCharacter());
            if (!gojo) throw new AssertionError("player should have become Gojo on join");
            ctx.takeScreenshot("01_arena_hud");

            TestInput in = ctx.getInput();
            KeyMapping blue = key(ctx, "key.jjk.skill_1"), red = key(ctx, "key.jjk.skill_2"), purple = key(ctx, "key.jjk.skill_3");
            KeyMapping warp = key(ctx, "key.jjk.skill_4"), domain = key(ctx, "key.jjk.ultimate");

            // Melee: walk up to the east dummies and throw a 4-hit chain.
            in.holdKeyFor(o -> o.keyUp, 18);
            for (int i = 0; i < 4; i++) {
                in.pressKey(o -> o.keyAttack);
                ctx.waitTicks(i == 1 ? 3 : 7);
                if (i == 1) ctx.takeScreenshot("02_melee_hit");
            }
            ctx.waitTicks(3);
            ctx.takeScreenshot("03_melee_finisher");
            int combo = ctx.computeOnClient(mc -> dev.rick.jjk.client.ClientState.comboCount);
            if (combo < 2) throw new AssertionError("melee chain should connect (combo " + combo + ")");

            // Third person for the techniques so the poses are visible.
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("execute as @a at @s run tp @s ~-4 ~ ~ -90 5");
            ctx.waitTicks(10);

            in.holdKey(blue);
            ctx.waitTicks(12);
            ctx.takeScreenshot("04_blue_pull");
            ctx.waitTicks(15);
            in.releaseKey(blue);
            ctx.waitTicks(30);

            in.holdKey(red);
            ctx.waitTicks(22);
            ctx.takeScreenshot("05_red_charge");
            in.releaseKey(red);
            ctx.waitTicks(5);
            ctx.takeScreenshot("06_red_explosion");
            ctx.waitTicks(30);

            // Hollow Purple at the pillar to the north-east.
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -120 5");
            in.holdKey(purple);
            ctx.waitTicks(10);
            ctx.takeScreenshot("07_purple_blue");
            ctx.waitTicks(16);
            ctx.takeScreenshot("08_purple_red");
            ctx.waitTicks(14);
            ctx.takeScreenshot("09_purple_fusion");
            ctx.waitTicks(10);
            in.releaseKey(purple);
            ctx.waitTicks(4);
            ctx.takeScreenshot("10_purple_fired");
            ctx.waitTicks(40);

            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 5");
            in.pressKey(warp);
            ctx.waitTicks(2);
            ctx.takeScreenshot("11_teleport");
            ctx.waitTicks(20);

            in.pressKey(domain);
            ctx.waitTicks(15);
            ctx.takeScreenshot("12_domain_sign");
            ctx.waitTicks(30);
            ctx.takeScreenshot("13_unlimited_void");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(10);
            ctx.takeScreenshot("14_unlimited_void_first_person");
            boolean overloaded = server.computeOnServer(s -> {
                for (var level : s.getAllLevels()) {
                    for (var e : level.getAllEntities()) {
                        if (e instanceof LivingEntity le && !(e instanceof net.minecraft.world.entity.player.Player) && Combat.has(le, CombatStatus.OVERLOAD)) return true;
                    }
                }
                return false;
            });
            if (!overloaded) throw new AssertionError("dummies inside Unlimited Void should be overloaded");
            ctx.waitTicks(260);
            ctx.takeScreenshot("15_after_domain_burnout");
            boolean burnt = server.computeOnServer(s -> s.getPlayerList().getPlayers().stream().anyMatch(p -> Combat.has(p, CombatStatus.BURNOUT)
                    || Casters.get(p).character() == null));
            if (!burnt) throw new AssertionError("owner should be burnt out after the domain");
        }
    }

    private static KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
