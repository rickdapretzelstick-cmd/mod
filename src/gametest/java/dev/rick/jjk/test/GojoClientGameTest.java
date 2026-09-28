package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.domain.structure.DomainStructures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Drives a real client through Gojo's whole kit with real key/mouse input: the base moveset, building and spending the
 * Awakening, the awakened moveset, and Infinite Void's physical domain with exact world restoration. Screenshots land in
 * build/run/clientGameTest/screenshots.
 */
public class GojoClientGameTest implements FabricClientGameTest {
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
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 10");
            ctx.waitTicks(40);
            if (!ctx.computeOnClient(mc -> ClientState.hasCharacter())) throw new AssertionError("player should have become Gojo on join");
            ctx.takeScreenshot("01_arena_hud");

            TestInput in = ctx.getInput();
            KeyMapping z = key(ctx, "key.jjk.skill_1"), x = key(ctx, "key.jjk.skill_2"), c = key(ctx, "key.jjk.skill_3");
            KeyMapping v = key(ctx, "key.jjk.skill_4"), g = key(ctx, "key.jjk.ultimate");

            // --- Base kit ---
            in.holdKeyFor(o -> o.keyUp, 18);
            for (int i = 0; i < 4; i++) {
                in.pressKey(o -> o.keyAttack);
                ctx.waitTicks(i == 1 ? 3 : 7);
                if (i == 1) ctx.takeScreenshot("02_melee_hit");
            }
            ctx.waitTicks(3);
            ctx.takeScreenshot("03_melee_finisher");
            int combo = ctx.computeOnClient(mc -> ClientState.comboCount);
            if (combo < 2) throw new AssertionError("melee chain should connect (combo " + combo + ")");
            float meter = ctx.computeOnClient(mc -> ClientState.awakening);
            if (meter <= 0) throw new AssertionError("landing hits should build the Awakening meter");

            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("execute as @a at @s run tp @s ~-4 ~ ~ -90 5");
            ctx.waitTicks(10);
            in.holdKey(z);
            ctx.waitTicks(12);
            ctx.takeScreenshot("04_blue");
            ctx.waitTicks(10);
            in.releaseKey(z);
            ctx.waitTicks(30);
            in.holdKey(x);
            ctx.waitTicks(20);
            ctx.takeScreenshot("05_red_charge");
            in.releaseKey(x);
            ctx.waitTicks(5);
            ctx.takeScreenshot("06_red_explosion");
            ctx.waitTicks(30);

            // --- Awakening ---
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            ctx.takeScreenshot("07_awakening_ready");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(3);
            ctx.takeScreenshot("08_blindfold");
            in.pressKey(g);
            ctx.waitTicks(12);
            ctx.takeScreenshot("09_awakening_transition");
            ctx.waitTicks(12);
            ctx.takeScreenshot("10_awakened");
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("pressing G on a full meter should awaken");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(20);

            // --- Awakened kit ---
            in.pressKey(z);
            ctx.waitTicks(8);
            ctx.takeScreenshot("11_max_blue_cast");
            ctx.waitTicks(16);
            ctx.takeScreenshot("12_max_blue");
            ctx.waitTicks(40);
            in.holdKey(x);
            ctx.waitTicks(36);
            ctx.takeScreenshot("13_max_red_charge");
            in.releaseKey(x);
            ctx.waitTicks(5);
            ctx.takeScreenshot("14_max_red_explosion");
            ctx.waitTicks(30);
            server.runCommand("execute as @a run jjk awakening 100");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -120 5");
            in.holdKey(c);
            ctx.waitTicks(10);
            ctx.takeScreenshot("15_purple_blue");
            ctx.waitTicks(16);
            ctx.takeScreenshot("16_purple_red");
            ctx.waitTicks(14);
            ctx.takeScreenshot("17_purple_fusion");
            ctx.waitTicks(10);
            in.releaseKey(c);
            ctx.waitTicks(4);
            ctx.takeScreenshot("18_purple_fired");
            ctx.waitTicks(40);

            // --- Infinite Void: record the world first, then check it comes back exactly. ---
            server.runCommand("execute as @a run jjk awakening 100");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 5");
            ctx.waitTicks(10);
            Map<BlockPos, BlockState> before = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                Map<BlockPos, BlockState> m = new HashMap<>();
                BlockPos c0 = p.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(c0.offset(-21, -21, -21), c0.offset(21, 21, 21))) m.put(pos.immutable(), p.level().getBlockState(pos));
                return m;
            });
            in.pressKey(g);
            ctx.waitTicks(15);
            ctx.takeScreenshot("19_domain_sign");
            ctx.waitTicks(30);
            ctx.takeScreenshot("20_infinite_void");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -35");
            ctx.waitTicks(8);
            ctx.takeScreenshot("21_infinite_void_sky");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ 20");
            ctx.waitTicks(4);
            ctx.takeScreenshot("22_infinite_void_first_person");
            boolean overloaded = server.computeOnServer(s -> {
                for (var level : s.getAllLevels()) {
                    for (var e : level.getAllEntities()) {
                        if (e instanceof LivingEntity le && !(e instanceof net.minecraft.world.entity.player.Player) && Combat.has(le, CombatStatus.OVERLOAD)) return true;
                    }
                }
                return false;
            });
            if (!overloaded) throw new AssertionError("dummies inside Infinite Void should be overloaded");
            ctx.waitTicks(340);
            ctx.takeScreenshot("23_after_domain");
            String diff = server.computeOnServer(s -> {
                var level = s.getPlayerList().getPlayers().getFirst().level();
                if (!DomainStructures.all(level).isEmpty()) return "structure still active";
                int wrong = 0;
                String example = "";
                for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                    BlockState now = level.getBlockState(e.getKey());
                    // Leaves cut off from their logs (distance 7, not player-placed) decay on their own; that's vanilla, not the domain.
                    if (e.getValue().is(net.minecraft.tags.BlockTags.LEAVES) && e.getValue().hasProperty(net.minecraft.world.level.block.LeavesBlock.DISTANCE)
                            && e.getValue().getValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE) == 7
                            && !e.getValue().getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) continue;
                    if (!now.equals(e.getValue())) {
                        if (wrong++ == 0) example = e.getKey() + " was " + e.getValue() + " now " + now;
                    }
                }
                return wrong == 0 ? "" : wrong + " blocks differ, e.g. " + example;
            });
            if (!diff.isEmpty()) throw new AssertionError("world should be restored exactly after Infinite Void: " + diff);
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
