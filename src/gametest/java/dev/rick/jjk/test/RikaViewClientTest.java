package dev.rick.jjk.test;

import dev.rick.jjk.client.render.RikaRenderer;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.yuta.RikaEntity;
import dev.rick.jjk.yuta.YutaCharacter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rika in True Love, through the owner's own third-person camera: standing, turning on the spot and charging True Love
 * Beam. She must never sit solid between the camera and Yuta: either she's off to his side, or (planted behind him for
 * the beam) faded to a faint outline on his screen. Opt-in: screenshots in build/run/clientGameTest/screenshots as rv*.png.
 */
public class RikaViewClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 8");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                CharacterService.assign(p, Characters.get(YutaCharacter.ID));
                Casters.get(p).setNoCost(true);
                Casters.get(p).setAwakening(Casters.get(p).maxAwakening());
            });
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                if (!Casters.get(p).input(AbilitySlot.ULTIMATE, true, 0, 0, null)) throw new AssertionError("True Love: " + Casters.get(p).lastRefusal);
                Casters.get(p).input(AbilitySlot.ULTIMATE, false, 0, 0, null);
            });
            ctx.waitTicks(90);
            int[] blockedSolid = {0};
            int[] watched = {0};
            Runnable sample = () -> {
                boolean bad = ctx.computeOnClient(mc -> blocksSolid(mc));
                watched[0]++;
                if (bad) blockedSolid[0]++;
            };
            for (int i = 0; i < 20; i++) {
                ctx.waitTick();
                sample.run();
            }
            ctx.takeScreenshot("rv1_true_love_standing");
            // Quick turns on the spot: she swings round with him, never through his view.
            for (int turn = 0; turn < 4; turn++) {
                int yaw = 90 * (turn + 1);
                server.runCommand("execute as @a at @s run tp @s ~ ~ ~ " + yaw + " 8");
                for (int i = 0; i < 12; i++) {
                    ctx.waitTick();
                    sample.run();
                }
                if (turn == 1) ctx.takeScreenshot("rv2_after_a_half_turn");
            }
            // True Love Beam: she plants behind him for it, so on his screen she fades out of the way.
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 8");
            ctx.waitTicks(10);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                Casters.get(p).input(AbilitySlot.SKILL_5, true, 0, 0, null);
                Casters.get(p).input(AbilitySlot.SKILL_5, false, 0, 0, null);
            });
            ctx.waitTicks(6);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                if (!Casters.get(p).input(AbilitySlot.SKILL_3, true, 0, 0, null)) throw new AssertionError("True Love Beam: " + Casters.get(p).lastRefusal);
                Casters.get(p).input(AbilitySlot.SKILL_3, false, 0, 0, null);
            });
            float maxClear = 0;
            for (int i = 0; i < 70; i++) {
                ctx.waitTick();
                sample.run();
                maxClear = Math.max(maxClear, ctx.computeOnClient(mc -> RikaRenderer.ownViewClear));
                if (i == 30) ctx.takeScreenshot("rv3_beam_charge");
            }
            ctx.takeScreenshot("rv4_beam");
            System.out.println("[rika view] samples " + watched[0] + ", solid in the way " + blockedSolid[0] + ", max fade " + maxClear);
            if (blockedSolid[0] > 2) throw new AssertionError("Rika sat solid in her owner's view for " + blockedSolid[0] + " ticks");
        }
    }

    /** Rika's box is between the camera and the player (or holds the camera) and she isn't faded out of the way. */
    private static boolean blocksSolid(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        var cam = mc.gameRenderer.mainCamera().position();
        var eye = mc.player.getEyePosition();
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof RikaEntity r) || r.owner() != mc.player) continue;
            var box = r.getBoundingBox().inflate(0.2);
            boolean between = box.contains(cam) || box.clip(cam, eye).isPresent();
            if (between && RikaRenderer.ownViewClear < 0.5f) return true;
        }
        return false;
    }
}
