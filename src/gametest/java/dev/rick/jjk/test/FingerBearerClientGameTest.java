package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientProgression;
import dev.rick.jjk.progression.curse.FingerBearerEntity;
import dev.rick.jjk.progression.curse.FingerBearerEntity.Move;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Finger Bearer through a real client: unseen without Cursed Glasses, drawn from the supplied model with them, and
 * each attack's windup, release and effects as they look in game (aimed at a training dummy, so the camera stays put).
 * Opt-in (heavy): screenshots land in build/run/clientGameTest/screenshots as fb*.png.
 */
public class FingerBearerClientGameTest implements FabricClientGameTest {
    private static final int[] BEARER = new int[1];
    private static final int[] DUMMY = new int[1];

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("weather clear");
            // Creative: the curse never targets the camera, so every move is filmed from the same spot.
            server.runCommand("gamemode creative @a");
            ctx.waitTicks(40);
            server.runCommand("execute as @a at @s run jjk arena");
            server.runCommand("execute as @a at @s run kill @e[type=jjk:training_dummy,distance=..40]");
            ctx.waitTicks(20);
            // The player looks south at a stage 9 blocks off: the curse on the right, its target on the left.
            server.runOnServer(srv -> stage(srv));
            ctx.waitTicks(30);
            if (ctx.computeOnClient(mc -> ClientProgression.perceivesCurses())) throw new AssertionError("no glasses: no perception");
            ctx.takeScreenshot("fb01_hidden_without_glasses");
            server.runCommand("item replace entity @a armor.head with jjk:cursed_glasses");
            ctx.waitTicks(20);
            if (!ctx.computeOnClient(mc -> ClientProgression.perceivesCurses())) throw new AssertionError("the glasses grant perception");
            ctx.takeScreenshot("fb02_idle_with_glasses");
            ctx.waitTicks(40);
            ctx.takeScreenshot("fb03_idle_breath");

            force(server, Move.SHOT);
            ctx.waitTicks(9);
            ctx.takeScreenshot("fb04_shot_windup");
            ctx.waitTicks(4);
            ctx.takeScreenshot("fb05_shot_release");
            ctx.waitTicks(40);

            force(server, Move.BLAST);
            ctx.waitTicks(30);
            ctx.takeScreenshot("fb06_blast_gathering");
            ctx.waitTicks(11);
            ctx.takeScreenshot("fb07_blast_release");
            ctx.waitTicks(4);
            ctx.takeScreenshot("fb08_blast_flight");
            ctx.waitTicks(30);
            ctx.takeScreenshot("fb09_blast_winded");
            ctx.waitTicks(30);

            force(server, Move.BURST);
            ctx.waitTicks(10);
            ctx.takeScreenshot("fb10_burst_warning");
            ctx.waitTicks(9);
            ctx.takeScreenshot("fb11_burst_release");
            ctx.waitTicks(40);

            force(server, Move.RUSH);
            ctx.waitTicks(10);
            ctx.takeScreenshot("fb12_rush_crouch");
            ctx.waitTicks(8);
            ctx.takeScreenshot("fb13_rush_run");
            ctx.waitTicks(40);
            server.runOnServer(srv -> stage(srv));
            ctx.waitTicks(20);

            force(server, Move.SMASH);
            ctx.waitTicks(14);
            ctx.takeScreenshot("fb14_smash_raised");
            ctx.waitTicks(9);
            ctx.takeScreenshot("fb15_smash_impact");
            ctx.waitTicks(40);

            server.runOnServer(srv -> {
                if (bearer(srv) instanceof FingerBearerEntity fb) fb.hurtServer((ServerLevel) fb.level(), fb.damageSources().generic(), 10_000f);
            });
            ctx.waitTicks(6);
            ctx.takeScreenshot("fb16_death_roar");
            ctx.waitTicks(20);
            ctx.takeScreenshot("fb17_death_knees");
            ctx.waitTicks(12);
            ctx.takeScreenshot("fb18_death_fallen");
            ctx.waitTicks(20);
            ctx.takeScreenshot("fb19_finger");
        }
    }

    /** (Re)places the curse and a dummy target in front of the player. */
    private static void stage(MinecraftServer srv) {
        ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
        ServerLevel level = p.level();
        p.teleportTo(level, p.getX(), p.getY(), p.getZ(), java.util.Set.of(), 0f, 12f, true);
        Vec3 base = p.position().add(0, 0, 10);
        if (bearer(srv) instanceof FingerBearerEntity old) old.discard();
        if (level.getEntity(DUMMY[0]) instanceof LivingEntity old) old.discard();
        FingerBearerEntity fb = ModEntities.FINGER_BEARER.create(level, EntitySpawnReason.COMMAND);
        fb.snapTo(base.x - 3.5, base.y, base.z, 90f, 0f);
        fb.setHome(net.minecraft.core.BlockPos.containing(fb.position()).below(), 12);
        level.addFreshEntity(fb);
        fb.setRest(1_000_000);
        BEARER[0] = fb.getId();
        var dummy = ModEntities.TRAINING_DUMMY.create(level, EntitySpawnReason.COMMAND);
        dummy.snapTo(base.x + 4.5, base.y, base.z, -90f, 0f);
        level.addFreshEntity(dummy);
        DUMMY[0] = dummy.getId();
    }

    private static Object bearer(MinecraftServer srv) {
        return srv.getPlayerList().getPlayers().getFirst().level().getEntity(BEARER[0]);
    }

    private static void force(TestServerContext server, Move m) {
        server.runOnServer(srv -> {
            ServerLevel level = srv.getPlayerList().getPlayers().getFirst().level();
            if (level.getEntity(BEARER[0]) instanceof FingerBearerEntity fb && level.getEntity(DUMMY[0]) instanceof LivingEntity d) fb.forceMove(m, d);
        });
    }
}
