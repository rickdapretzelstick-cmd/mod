package dev.rick.jjk.test;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.prison.PrisonClient;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.prison.PrisonRealm;
import dev.rick.jjk.progression.prison.PrisonRealmItem;
import dev.rick.jjk.progression.prison.PrisonRealmState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.UUID;

/**
 * The Prison Realm through a real client: the cube opening and sealing on the player, the cell from inside, the outside
 * view (its key; watching only: movement keys do nothing while it is on; off again restores the player's own camera),
 * the escape and the release beside the realm (which claims nothing). Opt-in: screenshots in
 * build/run/clientGameTest/screenshots as pr*.png.
 */
public class PrisonRealmClientTest implements FabricClientGameTest {
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
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance infinite 4 true");
            server.runCommand("effect give @a minecraft:saturation infinite 1 true");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(20);
            server.runOnServer(srv -> {
                PrisonRealm.adminReset(srv);
                KitOwnership.get(srv).release("gojo");
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                UUID id = PrisonRealm.forge(srv);
                p.setItemInHand(InteractionHand.MAIN_HAND, PrisonRealmItem.create(id));
                p.setShiftKeyDown(true);
                if (!PrisonRealm.use(p, InteractionHand.MAIN_HAND)) throw new AssertionError("the cube opens on the player");
                p.setShiftKeyDown(false);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("pr1_opening");
            ctx.waitTicks(45);
            ctx.takeScreenshot("pr2_restraints");
            waitFor(ctx, server, s -> s.phase() == PrisonRealmState.Phase.SEALED, 80, "the seal closed");
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(5);
            if (!ctx.computeOnClient(mc -> PrisonClient.sealed())) throw new AssertionError("the client knows it is sealed");
            ctx.takeScreenshot("pr3_inside_the_cell");
            // The outside view.
            TestInput in = ctx.getInput();
            KeyMapping view = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.jjk.prison_view")).findFirst().orElseThrow());
            in.pressKey(view);
            ctx.waitTicks(6);
            if (!ctx.computeOnClient(mc -> PrisonClient.viewing())) {
                String why = ctx.computeOnClient(mc -> "key " + view.saveString() + " sealed " + PrisonClient.sealed() + " alive " + mc.player.isAlive()
                        + " screen " + mc.gui.screen() + " cam " + mc.options.getCameraType());
                throw new AssertionError("the outside view is on (" + why + ")");
            }
            double[] before = ctx.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            boolean near = ctx.computeOnClient(mc -> {
                Vec3 cam = mc.gameRenderer.mainCamera().position();
                return cam.distanceTo(mc.player.position()) > 20;
            });
            if (!near) throw new AssertionError("the camera is out by the realm, far from the cell");
            KeyMapping up = ctx.computeOnClient(mc -> mc.options.keyUp);
            in.holdKeyFor(up, 20);
            ctx.takeScreenshot("pr4_outside_view");
            // Trying to look up from beside it: the camera stays above the realm, the marker beam rising over it.
            ctx.runOnClient(mc -> mc.player.setXRot(-40f));
            ctx.waitTicks(4);
            ctx.takeScreenshot("pr4b_marker");
            ctx.runOnClient(mc -> mc.player.setXRot(20f));
            double[] after = ctx.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            double moved = Math.hypot(after[0] - before[0], after[2] - before[2]);
            if (moved > 0.2) throw new AssertionError("watching only: walking does nothing (moved " + moved + ")");
            in.pressKey(view);
            ctx.waitTicks(6);
            boolean restored = ctx.computeOnClient(mc -> !PrisonClient.viewing() && mc.options.getCameraType() == CameraType.FIRST_PERSON);
            if (!restored) throw new AssertionError("off again: the player's own first-person view is back");
            // Escape: every glowing seal broken in time, three stages, then the core.
            int ticks = 0;
            while (ticks++ < 20 * 40) {
                ctx.waitTick();
                boolean done = server.computeOnServer(srv -> {
                    PrisonRealmState s = PrisonRealm.state(srv);
                    if (s.phase() != PrisonRealmState.Phase.SEALED) return true;
                    ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                    if (s.stage() >= PrisonRealm.STAGES) {
                        PrisonRealm.useCore(p);
                        return false;
                    }
                    long now = p.level().getGameTime();
                    for (int i = 0; i < 4; i++) {
                        if ((s.broken() & (1 << i)) == 0 && PrisonRealm.lockOpen(now, i, s.stage())) PrisonRealm.useLock(p, PrisonRealm.lockPos(srv, i));
                    }
                    return false;
                });
                if (ticks == 60) ctx.takeScreenshot("pr5_seals");
                if (done) break;
            }
            waitFor(ctx, server, s -> s.phase() == PrisonRealmState.Phase.ITEM, 80, "released");
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(10);
            ctx.takeScreenshot("pr6_released");
            if (ctx.computeOnClient(mc -> PrisonClient.sealed() || PrisonClient.viewing())) throw new AssertionError("the client knows it is out");
            boolean gojo = server.computeOnServer(srv -> KitOwnership.get(srv).isOwner("gojo", srv.getPlayerList().getPlayers().getFirst().getUUID()));
            if (gojo) throw new AssertionError("an escape no longer claims Gojo (he is earned through his storyline)");
            server.runOnServer(srv -> {
                PrisonRealm.adminReset(srv);
                KitOwnership.get(srv).release("gojo");
            });
        }
    }

    private static void waitFor(ClientGameTestContext ctx, TestServerContext server, java.util.function.Predicate<PrisonRealmState> p, int max, String what) {
        for (int i = 0; i < max; i++) {
            if (server.computeOnServer(srv -> p.test(PrisonRealm.state(srv)))) return;
            ctx.waitTick();
        }
        throw new AssertionError("timed out waiting: " + what);
    }
}
