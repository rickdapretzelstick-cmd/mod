package dev.rick.jjk.test;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.ShotSequence;
import dev.rick.jjk.client.camera.Shots;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;

/**
 * The camera director's shot vocabulary on a player and a curse: two-shot, over-the-shoulder, low hero, top-down and
 * orbit, each in the open and boxed in by walls (the shot must never sit inside a block), plus a cut list played through.
 * Screenshots shot_*.png.
 */
public class CameraShotsClientTest implements FabricClientGameTest {
    private static Entity foe;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            ctx.waitTicks(40);
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_GLASSES));
                p.setYRot(0);
                var c = ModEntities.GRAVE_HOUND.create(level, EntitySpawnReason.EVENT);
                c.setPos(p.getX(), p.getY(), p.getZ() + 3);
                c.setNoAi(true);
                c.setYRot(180);
                level.addFreshEntity(c);
            });
            ctx.waitTicks(10);
            // Cinematic shots draw the player's own body.
            ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            List<String> names = List.of("two_shot", "over_shoulder", "low_hero", "top_down", "orbit");
            List<Function<Vec3[], Frame>> shots = List.of(
                    v -> Shots.twoShot(v[0], v[1], 1, 3, 1.4),
                    v -> Shots.overShoulder(v[0], v[1], 1, 2.2),
                    v -> Shots.lowHero(v[1].add(0, -0.7, 0), v[0], 2.6, 1.3),
                    v -> Shots.topDown(v[0].lerp(v[1], 0.5), 7, v[1].subtract(v[0])),
                    v -> Shots.orbit(v[0].lerp(v[1], 0.5), 5, 2, Math.toRadians(40)));
            for (int round = 0; round < 2; round++) {
                if (round == 1) {
                    // Boxed in: walls and a low ceiling round the pair.
                    server.runOnServer(srv -> {
                        ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                        BlockPos at = p.blockPosition();
                        ServerLevel level = (ServerLevel) p.level();
                        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 6; dz++) for (int dy = 0; dy <= 4; dy++) {
                            boolean shell = Math.abs(dx) == 3 || dz == -3 || dz == 6 || dy == 4;
                            if (shell) level.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
                        }
                        level.setBlockAndUpdate(at.offset(3, 2, 1), Blocks.GLOWSTONE.defaultBlockState());
                    });
                    ctx.waitTicks(10);
                }
                for (int i = 0; i < names.size(); i++) {
                    Function<Vec3[], Frame> shot = shots.get(i);
                    ctx.runOnClient(mc -> CameraDirector.register("test_shot", 10, last(() -> shot.apply(subjects(mc)))));
                    ctx.waitTicks(4);
                    boolean clear = ctx.computeOnClient(mc -> Shots.clear(mc.gameRenderer.mainCamera().position()));
                    String owner = ctx.computeOnClient(mc -> CameraDirector.owner());
                    if (!clear) throw new AssertionError(names.get(i) + (round == 1 ? " (boxed in)" : "") + ": the camera is inside a block");
                    if (!"test_shot".equals(owner)) throw new AssertionError("the test shot owns the view: " + owner);
                    ctx.takeScreenshot("shot_" + names.get(i) + (round == 1 ? "_boxed" : "_open"));
                }
            }
            // A cut list: two-shot, cut to over-the-shoulder, eased move into a low hero shot.
            ctx.runOnClient(mc -> {
                long t0 = System.nanoTime();
                ShotSequence seq = new ShotSequence()
                        .then(k -> Shots.twoShot(subjects(mc)[0], subjects(mc)[1], 1, 3, 1.4), 600)
                        .then(k -> Shots.overShoulder(subjects(mc)[0], subjects(mc)[1], -1, 2.0), 600)
                        .then(k -> Shots.lowHero(subjects(mc)[1].add(0, -0.7, 0), subjects(mc)[0], 2.6, 1.3), 900, ShotSequence.Join.MOVE, 500);
                CameraDirector.register("test_shot", 10, last(() -> seq.at((System.nanoTime() - t0) / 1e6)));
            });
            ctx.waitTicks(6);
            ctx.takeScreenshot("shot_sequence_1");
            ctx.waitTicks(14);
            ctx.takeScreenshot("shot_sequence_2");
            ctx.waitTicks(16);
            ctx.takeScreenshot("shot_sequence_3");
            ctx.runOnClient(mc -> CameraDirector.unregister("test_shot"));
            ctx.waitTicks(5);
            String after = ctx.computeOnClient(mc -> String.valueOf(CameraDirector.owner()));
            if (!"null".equals(after)) throw new AssertionError("no stage owns the view afterwards: " + after);
        }
    }

    private static Vec3[] subjects(Minecraft mc) {
        Entity f = null;
        for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == ModEntities.GRAVE_HOUND) f = e;
        Vec3 me = mc.player.position().add(0, 1.1, 0);
        Vec3 them = f == null ? me.add(0, 0, 3) : f.position().add(0, 0.7, 0);
        return new Vec3[] {me, them};
    }

    private static CameraDirector.Stage last(java.util.function.Supplier<Frame> frame) {
        return new CameraDirector.Stage() {
            @Override
            public double[] apply(Vec3 pos, float yaw, float pitch) {
                Frame f = frame.get();
                return f == null ? null : f.array();
            }

            @Override
            public boolean last() {
                return true;
            }
        };
    }
}
