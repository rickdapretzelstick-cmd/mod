package dev.rick.jjk.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rick.jjk.client.clash.ClashClient;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.CombatStatus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Records the domain showcase as numbered screenshots plus {@code frames.txt} (the game tick each shows), with the game
 * slowed to one tick per second so every tick is captured; a real-speed video is then rebuilt from them. Not registered
 * by default: point the {@code fabric-client-gametest} entrypoint at this class to record.
 */
public class VideoClientTest implements FabricClientGameTest {
    private int frame;
    private final Set<Integer> pressed = new HashSet<>();
    /** Game tick (with partial) each frame shows, so the video can be rebuilt at real speed. */
    private final StringBuilder log = new StringBuilder();

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
            server.runCommand("execute as @a at @s run tp @s ~-4 ~ ~ -90 12");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            TestInput in = ctx.getInput();
            KeyMapping g = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.ultimate")).findFirst().orElseThrow());
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);

            // Slow the game so every tick can be photographed; the video is rebuilt at real speed afterwards.
            server.runCommand("tick rate 1");
            ctx.waitTicks(3);
            frames(ctx, server, in, 10);
            // Awakening.
            in.pressKey(g);
            frames(ctx, server, in, 50);
            // Domain expansion: the solo opening, the dome building from the feet, the title card.
            in.pressKey(g);
            frames(ctx, server, in, 120);
            frames(ctx, server, in, 30);

            // Reset for the clash (not filmed): let the domain go and the world restore.
            server.runCommand("tick rate 20");
            server.runCommand("execute as @a run jjk domain cancel");
            ctx.waitTicks(80);
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.combat.Statuses.remove(p, CombatStatus.BURNOUT);
                var c = Casters.get(p);
                c.endAwakening("video");
                c.setAwakening(c.maxAwakening());
            });
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ -90 8");
            ctx.waitTicks(10);
            server.runCommand("tick rate 1");
            ctx.waitTicks(3);
            // A rival opens their domain...
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var level = p.level();
                var rival = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
                var look = p.getLookAngle().multiply(1, 0, 1).normalize();
                rival.setPos(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
                rival.setYRot(p.getYRot() + 180);
                level.addFreshEntity(rival);
                dev.rick.jjk.core.character.CharacterService.assign(rival, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.gojo.GojoCharacter.ID));
                dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(rival, 0.5f);
                var rc = Casters.get(rival);
                rc.enterAwakening();
                rc.setAwakening(rc.maxAwakening());
                rc.setNoCost(true);
            });
            frames(ctx, server, in, 12);
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) {
                    if (e instanceof dev.rick.jjk.entity.TrainingDummy d && Casters.getOrNull(d) != null && Casters.get(d).isAwakened()) {
                        Casters.get(d).input(dev.rick.jjk.core.ability.AbilitySlot.ULTIMATE, true, 0, 0, null);
                    }
                }
            });
            frames(ctx, server, in, 8);
            // ...and Gojo answers with the Awakening key: instant Awakening, counter domain, versus cut-in, clash.
            in.pressKey(g);
            frames(ctx, server, in, 60);
            for (int i = 0; i < 900 && ctx.computeOnClient(mc -> ClashClient.view() != null); i++) frames(ctx, server, in, 1);
            frames(ctx, server, in, 70);
            server.runCommand("tick rate 20");
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("screenshots", "frames.txt"), log.toString());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Films {@code n} game ticks: a screenshot every client frame-step, pressing any clash prompts as they come due. */
    private void frames(ClientGameTestContext ctx, TestServerContext server, TestInput in, int n) {
        int[] keys = {InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_UP, InputConstants.KEY_RIGHT};
        long target = ctx.computeOnClient(mc -> mc.level.getGameTime()) + n;
        while (ctx.computeOnClient(mc -> mc.level.getGameTime()) < target) {
            ctx.waitTick();
            int[] due = ctx.computeOnClient(mc -> {
                var v = ClashClient.view();
                if (v == null || mc.level == null) return new int[0];
                double clock = mc.level.getGameTime() - v.startTick;
                java.util.List<Integer> out = new java.util.ArrayList<>();
                for (int k = 0; k < v.times.length; k++) if (Math.abs(v.times[k] - clock) <= 0.5) out.add(v.round * 1000 + k);
                return out.stream().mapToInt(Integer::intValue).toArray();
            });
            for (int id : due) {
                if (!pressed.add(id)) continue;
                int lane = ctx.computeOnClient(mc -> {
                    var v = ClashClient.view();
                    return v == null || id % 1000 >= v.lanes.length ? -1 : (int) v.lanes[id % 1000];
                });
                if (lane >= 0) in.pressKey(keys[lane]);
            }
            double t = ctx.computeOnClient(mc -> mc.level.getGameTime() + (double) mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            String name = String.format("v%05d", frame++);
            ctx.takeScreenshot(name);
            log.append(name).append(' ').append(t).append('\n');
        }
    }
}
