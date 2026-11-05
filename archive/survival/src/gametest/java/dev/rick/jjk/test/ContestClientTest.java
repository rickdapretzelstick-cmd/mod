package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * A strength contest as its contestant sees it: the cinematic side-on shot (orbiting, pushing in, FOV 65 to 58), the
 * tug-of-war meter and the one key shown (nothing pressed here, so it overpowers them in the end: there is no clock,
 * it goes on until one side wins), the result, the view given back; then the same with Reduced Camera Motion (one still
 * shot at a fixed FOV), and a contest against a wall (the shot never goes through it). Screenshots contest_*.png.
 */
public class ContestClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            grab(server, false);
            ctx.waitTicks(8);
            ctx.takeScreenshot("contest_intro");
            float early = ctx.computeOnClient(mc -> dev.rick.jjk.client.training.ContestCamera.fov());
            ctx.waitTicks(40);
            ctx.takeScreenshot("contest_active");
            ctx.waitTicks(50);
            ctx.takeScreenshot("contest_late");
            float late = ctx.computeOnClient(mc -> dev.rick.jjk.client.training.ContestCamera.fov());
            if (!(early > late && early <= 65f && late >= 58f)) throw new AssertionError("the FOV eases in from 65 towards 58: " + early + " -> " + late);
            boolean third = ctx.computeOnClient(mc -> mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
            if (!third) throw new AssertionError("the contest uses its own shot");
            String key = ctx.computeOnClient(mc -> dev.rick.jjk.client.training.TrainingClient.moveLabel(dev.rick.jjk.client.training.TrainingClient.contestShown()));
            if (!List.of("W", "A", "S", "D").contains(key)) throw new AssertionError("one movement key is shown: " + key);
            // Nothing pressed: it wins in the end (no clock runs out first).
            for (int i = 0; i < 80 && ctx.computeOnClient(mc -> {
                var a = dev.rick.jjk.client.training.TrainingClient.attempt();
                return a != null && a.state != dev.rick.jjk.progression.training.TrainingNet.FAILURE;
            }); i++) ctx.waitTicks(5);
            ctx.waitTicks(4);
            ctx.takeScreenshot("contest_result");
            for (int i = 0; i < 40 && ctx.computeOnClient(mc -> dev.rick.jjk.client.training.TrainingClient.active()); i++) ctx.waitTicks(5);
            ctx.waitTicks(10);
            boolean back = ctx.computeOnClient(mc -> mc.options.getCameraType() == CameraType.FIRST_PERSON && !dev.rick.jjk.client.training.TrainingClient.active());
            if (!back) throw new AssertionError("the player's own view comes back afterwards");
            ctx.takeScreenshot("contest_after");

            // Reduced Camera Motion: one still shot, fixed FOV; and with a wall beside them the shot stays out of it.
            ctx.runOnClient(mc -> JJKConfig.get().client.reducedCameraMotion = true);
            grab(server, true);
            ctx.waitTicks(30);
            float still = ctx.computeOnClient(mc -> dev.rick.jjk.client.training.ContestCamera.fov());
            if (still != 62f) throw new AssertionError("reduced motion holds one FOV: " + still);
            boolean clear = ctx.computeOnClient(mc -> {
                var cam = mc.gameRenderer.mainCamera().position();
                BlockPos b = BlockPos.containing(cam);
                return mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty();
            });
            if (!clear) throw new AssertionError("the shot never sits inside a wall");
            ctx.takeScreenshot("contest_reduced_wall");
            for (int i = 0; i < 80 && ctx.computeOnClient(mc -> dev.rick.jjk.client.training.TrainingClient.active()); i++) ctx.waitTicks(5);
            ctx.runOnClient(mc -> JJKConfig.get().client.reducedCameraMotion = false);
        }
    }

    /** A grave hound grabs the player (past the telegraph), optionally with walls round them. */
    private static void grab(TestServerContext server, boolean walls) {
        server.runOnServer(srv -> {
            ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
            ServerLevel level = (ServerLevel) p.level();
            StrengthContests.setRandomGrabsForTest(false);
            p.setHealth(p.getMaxHealth());
            // Curses are only seen through cursed eyes.
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_GLASSES));
            p.teleportTo(p.getX(), p.getY(), p.getZ());
            p.setYRot(0);
            p.setYHeadRot(0);
            if (walls) {
                BlockPos at = p.blockPosition();
                for (int dz = -4; dz <= 6; dz++) for (int dy = 0; dy < 6; dy++) {
                    level.setBlockAndUpdate(at.offset(3, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
                    level.setBlockAndUpdate(at.offset(-3, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            CommonCurseEntity c = ModEntities.GRAVE_HOUND.create(level, EntitySpawnReason.EVENT);
            c.setPos(p.getX(), p.getY(), p.getZ() + 2.2);
            c.setYRot(180);
            level.addFreshEntity(c);
            c.setTarget(p);
            StrengthContests.forgetForTest(p, c);
            if (!StrengthContests.begin(srv, c, p)) throw new AssertionError("grabbed: " + StrengthContests.unavailable(p));
        });
    }
}
