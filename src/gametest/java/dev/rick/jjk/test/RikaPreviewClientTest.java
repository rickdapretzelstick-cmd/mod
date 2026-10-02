package dev.rick.jjk.test;

import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.yuta.RikaEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rika's model seen from the front, the side and behind, at rest and in an axis-check pose. Opt-in (list it under
 * {@code fabric-client-gametest} to run); screenshots {@code rika_<view>_<pose>}.
 */
public class RikaPreviewClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..60]");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
            ctx.waitTicks(20);
            int[] rika = new int[1];
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
                var dummy = new dev.rick.jjk.entity.TrainingDummy(dev.rick.jjk.registry.ModEntities.TRAINING_DUMMY, p.level());
                dummy.setPos(p.getX(), p.getY(), p.getZ() + 7);
                dummy.setNoAi(true);
                p.level().addFreshEntity(dummy);
                RikaEntity r = RikaEntity.summon(p.level(), dummy);
                r.set(RikaEntity.FULL, true);
                rika[0] = r.getId();
            });
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            // build/rikapreview.txt: "clip:ms,ms,..." lines scrub her clips at exact times (front three-quarter view);
            // a line "base" first shows her partly manifested instead.
            java.nio.file.Path list = java.nio.file.Path.of("../../rikapreview.txt");
            if (java.nio.file.Files.exists(list)) {
                java.util.List<String> lines;
                try {
                    lines = java.nio.file.Files.readAllLines(list);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                server.runOnServer(s -> {
                    for (var e : s.overworld().getAllEntities()) {
                        if (e instanceof dev.rick.jjk.entity.TrainingDummy d) {
                            d.setYRot(160);
                            d.setYHeadRot(160);
                            d.setYBodyRot(160);
                        }
                        if (e instanceof RikaEntity r && lines.contains("base")) r.set(RikaEntity.FULL, false);
                    }
                });
                ctx.waitTicks(30);
                for (String l : lines) {
                    if (l.isBlank() || l.equals("base")) continue;
                    String[] kv = l.trim().split(":");
                    String name = kv[0];
                    for (String t : kv[1].split(",")) {
                        int shot = Integer.parseInt(t);
                        ctx.runOnClient(mc -> {
                            dev.rick.jjk.client.anim.AnimDebug.target = rika[0];
                            dev.rick.jjk.client.anim.AnimDebug.hud = false;
                            dev.rick.jjk.client.anim.AnimDebug.paused = true;
                            ClientAnimations.forget(rika[0]);
                            dev.rick.jjk.client.anim.AnimDebug.play(name);
                            var top = ClientAnimations.player(rika[0]).top();
                            if (top != null) top.time = shot;
                        });
                        ctx.waitTicks(1);
                        // A third field names a bone: log where it is, in her own frame (right, up, forward, in blocks).
                        if (kv.length > 2) {
                            String bone = kv[2];
                            ctx.runOnClient(mc -> {
                                var e = (RikaEntity) mc.level.getEntity(rika[0]);
                                var at = dev.rick.jjk.client.render.RikaRenderer.bone(e, bone, new org.joml.Vector3f(), 0f);
                                if (at == null) return;
                                var rel = at.subtract(e.position());
                                double yaw = Math.toRadians(e.getYRot());
                                var fwd = new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                                var right = new net.minecraft.world.phys.Vec3(-fwd.z, 0, fwd.x);
                                System.out.printf("[rikabone] %s %d %s right=%.2f up=%.2f forward=%.2f%n", name, shot, bone,
                                        rel.dot(right), rel.y, rel.dot(fwd));
                            });
                        }
                        ctx.takeScreenshot(String.format("rika_%s_%04d", name, shot));
                    }
                }
                return;
            }
            float[] yaws = {180, 90, 0, -45};
            String[] views = {"front", "side", "back", "threequarter"};
            for (int pose = 0; pose < 2; pose++) {
                if (pose == 1) ctx.runOnClient(mc -> ClientAnimations.play(rika[0], "rika_calibrate", 1f, mc.level.getGameTime()));
                for (int v = 0; v < yaws.length; v++) {
                    float yaw = yaws[v];
                    server.runOnServer(s -> {
                        for (var e : s.overworld().getAllEntities()) {
                            if (e instanceof dev.rick.jjk.entity.TrainingDummy d) {
                                d.setYRot(yaw);
                                d.setYHeadRot(yaw);
                                d.setYBodyRot(yaw);
                            }
                        }
                    });
                    ctx.waitTicks(25);
                    ctx.takeScreenshot("rika_" + views[v] + "_" + (pose == 0 ? "rest" : "calibrate"));
                }
            }
        }
    }
}
