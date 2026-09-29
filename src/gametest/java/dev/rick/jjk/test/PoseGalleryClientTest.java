package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Every pose animation played on a training dummy seen three-quarters on, shot as a filmstrip (a few frames from
 * wind-up to recovery), for judging how the moves move. Not run by default: list it under
 * {@code fabric-client-gametest} in the test mod's fabric.mod.json. A {@code build/posegallery.txt} limits it to the
 * animations named there, one per line. Screenshots land in build/run/clientGameTest/screenshots as
 * {@code pose_<name>_<tick>}.
 */
public class PoseGalleryClientTest implements FabricClientGameTest {
    /** Marks where {@code HandPos} puts each hand (for checking that effects held in a fist follow it). */
    private static final boolean MARK_HANDS = java.nio.file.Files.exists(java.nio.file.Path.of("../../posegallery_hands"));

    @Override
    public void runTest(ClientGameTestContext ctx) {
        java.util.Set<String> only = new java.util.HashSet<>();
        try {
            java.nio.file.Path list = java.nio.file.Path.of("../../posegallery.txt");
            if (java.nio.file.Files.exists(list)) for (String l : java.nio.file.Files.readAllLines(list)) if (!l.isBlank()) only.add(l.trim());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 12");
            ctx.waitTicks(20);
            server.runCommand("execute as @a at @s run summon jjk:training_dummy ~ ~ ~2.9 {NoAI:1b,Rotation:[-60f,0f]}");
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            List<String> names = ctx.computeOnClient(mc -> AnimLibrary.names());
            for (String name : names) {
                if (!only.isEmpty() && !only.contains(name)) continue;
                float duration = ctx.computeOnClient(mc -> AnimLibrary.get(name).duration / 50f);
                long start = ctx.computeOnClient(mc -> {
                    Entity d = dummy(mc);
                    ClientAnimations.play(d.getId(), name, 1f, mc.level.getGameTime());
                    return mc.level.getGameTime();
                });
                int[] shots = shots(duration);
                for (int shot : shots) {
                    ctx.waitFor(mc -> mc.level.getGameTime() - start >= shot);
                    if (MARK_HANDS) ctx.runOnClient(mc -> {
                        // Where the effects think the fists are: a small red flash on the right, green on the left.
                        Entity d = dummy(mc);
                        long now = mc.level.getGameTime();
                        dev.rick.jjk.client.render.Flashes.flash(dev.rick.jjk.client.anim.HandPos.of((net.minecraft.world.entity.LivingEntity) d, 0, true, 0),
                                0.25f, 0.2f, new float[] {1, 0, 0}, 1f, 2, now);
                        dev.rick.jjk.client.render.Flashes.flash(dev.rick.jjk.client.anim.HandPos.of((net.minecraft.world.entity.LivingEntity) d, 0, false, 0),
                                0.25f, 0.2f, new float[] {0, 1, 0}, 1f, 2, now);
                    });
                    ctx.takeScreenshot(String.format("pose_%s_%02d", name, shot));
                }
                ctx.runOnClient(mc -> ClientAnimations.forget(dummy(mc).getId()));
                ctx.waitTicks(3);
            }
        }
    }

    /** Four frames across the move: early wind-up, around the hit, follow-through and the end. */
    private static int[] shots(float duration) {
        int d = Math.max(4, Math.round(duration));
        if (d <= 8) return new int[] {1, 2, 4, d};
        return new int[] {1, Math.max(2, d / 5), d / 3, d * 2 / 3};
    }

    private static Entity dummy(net.minecraft.client.Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == ModEntities.TRAINING_DUMMY) return e;
        throw new AssertionError("no dummy");
    }
}
