package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.clash.BeamClashClient;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.BeamClashSession;
import dev.rick.jjk.entity.TrainingDummy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Opt-in: the beam clash, played for real. Yuta (the player) fires True Love Beam at Ryu (a bot); Ryu answers with
 * Every Last Drop inside the window; the two meet, the clash camera frames it, the player's skill-check dial and the
 * tug of war run, and it resolves. Then Ryu fires Every Last Drop at the player: the counter prompt shows, the player
 * answers with their Ultimate, and that clash runs too. Screenshots land in build/run/clientGameTest/screenshots as
 * bclash_*.png.
 */
public class BeamClashClientTest implements FabricClientGameTest {
    private ClientGameTestContext ctx;
    private TestServerContext server;
    private int rivalId = -1;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            server = sp.getServer();
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
            double[] home = server.computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                return new double[] {p.getX(), p.getY(), p.getZ()};
            });
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runCommand("jjk character yuta @a");
            ctx.waitTicks(20);
            if (!ctx.computeOnClient(mc -> "yuta".equals(ClientState.character))) throw new AssertionError("not Yuta");
            KeyMapping s3 = key("key.jjk.skill_3"), s5 = key("key.jjk.skill_5"), ult = key("key.jjk.ultimate");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            // True Love: Rika fully manifested.
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(5);
            in.pressKey(ult);
            ctx.waitTicks(90);
            if (!ctx.computeOnClient(mc -> ClientState.awakened())) throw new AssertionError("True Love awakens him");
            spawnRyu(16);
            ctx.waitTicks(10);

            // --- 1: True Love Beam, answered by Every Last Drop ---
            in.pressKey(s5);
            ctx.waitTicks(6);
            ShowcaseCamera.set(70, 14f, 3f, 10f);
            in.pressKey(s3);
            boolean answered = false;
            for (int i = 0; i < 90 && !answered; i++) {
                answered = server.computeOnServer(s -> {
                    TrainingDummy r = rival(s);
                    if (r == null || !BeamClashManager.windowOpen(r)) return false;
                    return Casters.get(r).input(AbilitySlot.ULTIMATE, true, 0, 0, null);
                });
                ctx.waitTicks(1);
            }
            if (!answered) {
                String why = server.computeOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                    var c = Casters.get(p);
                    return "player casting " + (c.cast() == null ? "-" : c.cast().ability.id + "@" + c.cast().age()) + ", refusal " + c.lastRefusal
                            + ", rival " + (rival(s) != null) + ", miss " + BeamClashManager.lastMiss;
                });
                throw new AssertionError("Ryu never got the counter window: " + why);
            }
            ctx.takeScreenshot("bclash_0_answered");
            waitPhase(BeamClashClient.INTRO, 80);
            // The intro: the clash camera's own side-on shot.
            ShowcaseCamera.off();
            ctx.waitTicks(10);
            ctx.takeScreenshot("bclash_1_intro_camera");
            waitPhase(BeamClashClient.DUEL, 60);
            ctx.waitTicks(12);
            ctx.takeScreenshot("bclash_2_duel_dial");
            // Press on some checks (whatever lands) so the tug of war moves.
            for (int i = 0; i < 4; i++) {
                ctx.waitTicks(9);
                in.pressKey(o -> o.keyJump);
            }
            ctx.takeScreenshot("bclash_3_duel_pressed");
            ShowcaseCamera.set(90, 16f, 2.5f, 11f);
            ctx.waitTicks(10);
            ctx.takeScreenshot("bclash_4_world_side");
            ShowcaseCamera.off();
            waitPhase(BeamClashClient.RESOLVE, 120);
            ctx.waitTicks(16);
            ShowcaseCamera.set(80, 16f, 3f, 11f);
            ctx.waitTicks(2);
            ctx.takeScreenshot("bclash_5_resolve");
            ctx.waitTicks(30);
            ctx.takeScreenshot("bclash_6_after");
            ShowcaseCamera.off();
            ctx.waitTicks(100);

            // --- 2: Every Last Drop at the player, answered with True Love Beam ---
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            // The first clash tore up the floor: back to the start, the arena rebuilt round it.
            server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 0 0", home[0], home[1], home[2]));
            ctx.waitTicks(2);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            ctx.waitTicks(10);
            server.runCommand("execute as @a run jjk awakening 100");
            ctx.waitTicks(10);
            spawnRyu(16);
            ctx.waitTicks(10);
            server.runOnServer(s -> {
                TrainingDummy r = rival(s);
                if (r != null) Casters.get(r).input(AbilitySlot.ULTIMATE, true, 0, 0, null);
            });
            ShowcaseCamera.set(60, 12f, 3f, 10f);
            boolean prompt = false;
            for (int i = 0; i < 70 && !prompt; i++) {
                ctx.waitTicks(1);
                prompt = ctx.computeOnClient(mc -> mc.level != null && BeamClashClient.prompt(mc.level.getGameTime()) != null);
            }
            if (!prompt) {
                String why = server.computeOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                    TrainingDummy r = rival(s);
                    return "rival casting " + (r == null || Casters.get(r).cast() == null ? "-" : Casters.get(r).cast().ability.id + "@" + Casters.get(r).cast().age())
                            + " refusal " + (r == null ? "?" : Casters.get(r).lastRefusal) + ", miss " + BeamClashManager.lastMiss;
                });
                throw new AssertionError("no counter prompt for the player: " + why);
            }
            ctx.waitTicks(2);
            ctx.takeScreenshot("bclash_7_counter_prompt");
            in.pressKey(ult);
            waitPhase(BeamClashClient.DUEL, 80);
            ctx.waitTicks(20);
            ctx.takeScreenshot("bclash_8_second_clash");
            ShowcaseCamera.set(100, 15f, 3f, 9f);
            ctx.waitTicks(3);
            ctx.takeScreenshot("bclash_9_second_side");
            ShowcaseCamera.off();
            waitPhase(-1, 200);
            ctx.waitTicks(10);
            boolean clean = server.computeOnServer(s -> BeamClashManager.sessions().isEmpty());
            if (!clean) throw new AssertionError("a session outlived its clash");
        }
    }

    private void spawnRyu(double ahead) {
        server.runOnServer(s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
            var level = p.level();
            var look = p.getLookAngle().multiply(1, 0, 1).normalize();
            var r = new TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, level);
            r.setPos(p.getX() + look.x * ahead, p.getY(), p.getZ() + look.z * ahead);
            r.setYRot(p.getYRot() + 180);
            r.setYHeadRot(p.getYRot() + 180);
            r.setYBodyRot(p.getYRot() + 180);
            r.setXRot(0);
            level.addFreshEntity(r);
            dev.rick.jjk.core.character.CharacterService.assign(r, dev.rick.jjk.core.character.Characters.get(dev.rick.jjk.ryu.RyuCharacter.ID));
            dev.rick.jjk.core.domain.clash.ClashManager.setBotSkill(r, 0.6f);
            var rc = Casters.get(r);
            rc.setNoCost(true);
            rc.setAwakening(rc.maxAwakening());
            rivalId = r.getId();
        });
    }

    private TrainingDummy rival(net.minecraft.server.MinecraftServer s) {
        return s.overworld().getEntity(rivalId) instanceof TrainingDummy d ? d : null;
    }

    /** Waits until the local player's clash is in {@code phase} (-1: until there is none). */
    private void waitPhase(int phase, int max) {
        for (int i = 0; i < max; i++) {
            boolean there = ctx.computeOnClient(mc -> {
                BeamClashClient.View v = BeamClashClient.mine();
                return phase < 0 ? v == null : v != null && v.phase >= phase;
            });
            if (there) return;
            ctx.waitTicks(1);
        }
        boolean server = this.server.computeOnServer(s -> BeamClashManager.sessions().stream().anyMatch(x -> x.phase() != BeamClashSession.Phase.ENDED));
        throw new AssertionError("clash never reached phase " + phase + " (server session live: " + server + ")");
    }

    private KeyMapping key(String name) {
        return ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }
}
