package dev.rick.jjk.test;

import com.mojang.authlib.GameProfile;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.gear.CursedGear;
import dev.rick.jjk.client.rifle.RifleClient;
import dev.rick.jjk.client.rifle.RifleStance;
import dev.rick.jjk.core.net.RifleStatePayload;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Tuning aid (not in the suite): the Cursed Rifle's combat body on a client-only stand-in, posed through every state
 * the server can put it in (ready, aiming up and down, a shot's kick, walking, strafing, sprinting, in the air, landing,
 * crouched, each stage of the array, the bash, the flare), each from several sides. Screenshots pose_*.png; each
 * pose's hand-to-rifle reach error is logged as RIFLEPOSE.
 */
public class RiflePoseClientTest implements FabricClientGameTest {
    /** What the stand-in is doing (set by the test, applied every client tick). */
    static final class Scene {
        float yaw = 180, pitch = 0, fwd, side, vy;
        boolean ground = true, sprint, crouch;
        float walk;
    }

    static final Scene SCENE = new Scene();
    static RemotePlayer dummy;
    static Vec3 at;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 -60 -1.7 0 12");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> {
                at = new Vec3(0.5, mc.player.getY(), 0.5);
                dummy = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Stand_In"));
                dummy.setId(900_000);
                dummy.snapTo(at.x, at.y, at.z, 180, 0);
                mc.level.addEntity(dummy);
                CursedGear.TEST_DRAWN.put(dummy.getId(), new ItemStack(ProgressionItems.CURSED_RIFLE));
            });
            ClientTickEvents.END_CLIENT_TICK.register(RiflePoseClientTest::apply);
            ctx.waitTicks(20);

            int[] sides = {180, 120, 90, 30, 0, -90};
            shoot(ctx, "ready", sides);
            phase(ctx, RifleServer.Phase.AIM, 0);
            ctx.waitTicks(8);
            shoot(ctx, "ads", sides);
            SCENE.pitch = -35;
            shoot(ctx, "ads_up", new int[] {180, 90});
            SCENE.pitch = 35;
            shoot(ctx, "ads_down", new int[] {180, 90});
            SCENE.pitch = 0;
            ctx.runOnClient(mc -> RifleClient.shot(dummy.getId()));
            ctx.waitTicks(1);
            shoot(ctx, "ads_kick", new int[] {90});
            phase(ctx, RifleServer.Phase.IDLE, 0);
            ctx.waitTicks(10);

            SCENE.fwd = 0.2f;
            SCENE.walk = 0.8f;
            shoot(ctx, "walk", new int[] {180, 90});
            SCENE.fwd = 0;
            SCENE.side = 0.2f;
            shoot(ctx, "strafe", new int[] {180, 90});
            SCENE.side = 0;
            SCENE.fwd = 0.35f;
            SCENE.sprint = true;
            SCENE.walk = 1f;
            ctx.waitTicks(8);
            shoot(ctx, "sprint", new int[] {180, 90, 30});
            SCENE.sprint = false;
            SCENE.fwd = 0;
            SCENE.walk = 0;
            SCENE.ground = false;
            SCENE.vy = 0.3f;
            ctx.waitTicks(6);
            shoot(ctx, "jump", new int[] {90});
            SCENE.vy = -0.6f;
            ctx.waitTicks(4);
            shoot(ctx, "fall", new int[] {90});
            SCENE.ground = true;
            SCENE.vy = 0;
            ctx.waitTicks(1);
            shoot(ctx, "land", new int[] {90});
            ctx.waitTicks(10);
            SCENE.crouch = true;
            ctx.waitTicks(4);
            shoot(ctx, "crouch", new int[] {180, 90});
            SCENE.crouch = false;
            ctx.waitTicks(4);

            phase(ctx, RifleServer.Phase.DEPLOY, 28);
            ctx.waitTicks(14);
            shoot(ctx, "deploy", new int[] {180, 90});
            phase(ctx, RifleServer.Phase.CHARGE, 30);
            ctx.waitTicks(20);
            shoot(ctx, "charge", new int[] {180, 90, 30});
            phase(ctx, RifleServer.Phase.READY, 0);
            ctx.waitTicks(6);
            shoot(ctx, "ready_beam", new int[] {90});
            phase(ctx, RifleServer.Phase.FIRE, 40);
            ctx.waitTicks(8);
            shoot(ctx, "fire", new int[] {180, 90, 120});
            phase(ctx, RifleServer.Phase.COOLDOWN, 14);
            ctx.waitTicks(6);
            shoot(ctx, "cooldown", new int[] {90});
            phase(ctx, RifleServer.Phase.RETRACT, 28);
            ctx.waitTicks(10);
            shoot(ctx, "retract", new int[] {90});
            phase(ctx, RifleServer.Phase.IDLE, 0);
            ctx.waitTicks(20);

            SCENE.yaw = 90;
            ctx.waitTicks(3);
            move(ctx, "rf_bash", 1);
            snap(ctx, "bash_wind");
            ctx.waitTicks(2);
            snap(ctx, "bash_strike");
            ctx.waitTicks(12);
            move(ctx, "rf_flare", 3);
            snap(ctx, "flare");
            ctx.waitTicks(12);
            move(ctx, "rf_volley", 4);
            shoot(ctx, "volley", new int[] {90});
            ctx.runOnClient(mc -> {
                mc.level.removeEntity(dummy.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                CursedGear.TEST_DRAWN.clear();
            });
        }
    }

    private static void apply(Minecraft mc) {
        RemotePlayer d = dummy;
        if (d == null || d.isRemoved()) return;
        Scene s = SCENE;
        double yaw = Math.toRadians(s.yaw);
        // Travel in the stand-in's own frame: forward and to its right.
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        d.setPos(at.x, at.y + (s.ground ? 0 : 0.8), at.z);
        d.xo = at.x - (fx * s.fwd + rx * s.side);
        d.zo = at.z - (fz * s.fwd + rz * s.side);
        d.yo = d.getY() - s.vy;
        d.setYRot(s.yaw);
        d.yRotO = s.yaw;
        d.setYBodyRot(s.yaw);
        d.yBodyRotO = s.yaw;
        d.setYHeadRot(s.yaw);
        d.yHeadRotO = s.yaw;
        d.setXRot(s.pitch);
        d.xRotO = s.pitch;
        d.setOnGround(s.ground);
        d.setSprinting(s.sprint);
        d.setPose(s.crouch ? Pose.CROUCHING : Pose.STANDING);
        d.walkAnimation.update(s.walk, 0.4f, 1f);
    }

    private static void phase(ClientGameTestContext ctx, RifleServer.Phase ph, int duration) {
        ctx.runOnClient(mc -> RifleClient.apply(new RifleStatePayload(dummy.getId(), ph.ordinal(), duration, 1f, 80, 100, true, 1f, 16)));
    }

    private static void move(ClientGameTestContext ctx, String clip, int ticks) {
        ctx.runOnClient(mc -> ClientAnimations.play(dummy.getId(), clip, 1f, mc.level.getGameTime()));
        ctx.waitTicks(ticks);
    }

    private static void snap(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot("pose_" + name + "_" + (int) SCENE.yaw);
    }

    private static void shoot(ClientGameTestContext ctx, String name, int[] sides) {
        for (int side : sides) {
            SCENE.yaw = side;
            ctx.waitTicks(3);
            ctx.takeScreenshot("pose_" + name + "_" + (side < 0 ? "m" + -side : String.valueOf(side)));
            float[] e = ctx.computeOnClient(mc -> RifleStance.errors(dummy.getId()));
            System.out.println("RIFLEPOSE " + name + " " + side + " grip=" + (e == null ? "none" : e[0]) + " fore=" + (e == null ? "none" : e[1]));
        }
    }
}
