package dev.rick.jjk.test;

import dev.rick.jjk.client.rifle.RifleClient;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.LodgeScope;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * The Cursed Rifle and the hunting lodge through a real client: the rifle held (first and third person) at rest, its
 * arms deploying and ready, the beam fired (Creative: Maximum Output), the scope's view; then the lodge with its sealed
 * rack, the scope's view of the anomaly resolved, the rack open after the curse is gone, and the Hunter's Shade (its
 * placeholder model). Opt-in: screenshots in build/run/clientGameTest/screenshots as rifle*.png.
 */
public class RifleClientTest implements FabricClientGameTest {
    private static String tp(double x, double y, double z, float yaw, float pitch) {
        return String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("item replace entity @a armor.head with jjk:cursed_glasses");
            server.runCommand("item replace entity @a weapon.mainhand with jjk:cursed_rifle");
            server.runCommand("tp @a 0 ~ 0 0 0");
            ctx.waitTicks(40);
            ctx.takeScreenshot("rifle1_first_person");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(10);
            ctx.takeScreenshot("rifle2_third_person");

            // The array: deploying, then ready (held), seen from the front.
            // Through the real controls: sneak and hold use.
            net.fabricmc.fabric.api.client.gametest.v1.TestInput input = ctx.getInput();
            net.minecraft.client.KeyMapping use = ctx.computeOnClient(mc -> mc.options.keyUse), sneak = ctx.computeOnClient(mc -> mc.options.keyShift);
            input.holdKey(sneak);
            ctx.waitTicks(2);
            input.holdKey(use);
            ctx.waitTicks(16);
            ctx.takeScreenshot("rifle3_deploying");
            String phase = ctx.computeOnClient(mc -> RifleClient.orIdle(mc.player.getId()).phase.name());
            if (!phase.equals("DEPLOY")) throw new AssertionError("the client follows the phase: " + phase);
            ctx.waitTicks(50);
            ctx.takeScreenshot("rifle4_ready");
            // Fired, seen from the side.
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("tp @a 0 ~ 0 -60 0");
            ctx.waitTicks(4);
            input.releaseKey(use);
            input.releaseKey(sneak);
            ctx.waitTicks(12);
            ctx.takeScreenshot("rifle5_beam");
            ctx.waitTicks(30);
            ctx.takeScreenshot("rifle6_beam_late");
            ctx.waitTicks(100);
            phase = ctx.computeOnClient(mc -> RifleClient.orIdle(mc.player.getId()).phase.name());
            if (!phase.equals("IDLE")) throw new AssertionError("back at rest after the beam: " + phase);

            // The scope.
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            input.holdKey(use);
            ctx.waitTicks(20);
            ctx.takeScreenshot("rifle7_scope");
            input.releaseKey(use);
            ctx.waitTicks(10);

            // The lodge, its window facing east.
            String[] id = new String[1];
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                InvestigationState st = InvestigationState.get(srv);
                BlockPos site = p.blockPosition().offset(40, 0, 0);
                InvestigationState.Village v = Investigations.village(level, site.offset(-80, 0, 0));
                Incident in = Investigations.create(level, v, st, IncidentTemplate.get("hunting_lodge"), new Sites.Site(site, 1, 0), level.getGameTime(),
                        RandomSource.create(3));
                Investigations.buildForTest(level, in);
                id[0] = in.id;
            });
            server.runCommand(server.computeOnServer(srv -> {
                BlockPos site = InvestigationState.get(srv).incident(id[0]).site;
                return tp(site.getX() - 13.5, site.getY() + 4, site.getZ() + 9.5, -125, 12);
            }));
            ctx.waitTicks(40);
            ctx.takeScreenshot("rifle8_lodge");
            // Inside, at the rack (sealed), then the scope's view.
            String atRack = server.computeOnServer(srv -> {
                BlockPos rack = InvestigationState.get(srv).incident(id[0]).mark("rack");
                return tp(rack.getX() + 0.5 + 2.4, rack.getY() - 1, rack.getZ() + 0.5, 90, 18);
            });
            server.runCommand(atRack);
            ctx.waitTicks(20);
            ctx.takeScreenshot("rifle9_rack_sealed");
            server.runCommand(server.computeOnServer(srv -> {
                Incident in = InvestigationState.get(srv).incident(id[0]);
                BlockPos scope = in.mark("scope");
                Vec3 at = Vec3.atBottomCenterOf(scope.relative(Direction.WEST));
                // Aimed from the scope's lens, as the view through it is.
                Vec3 lens = Vec3.atCenterOf(scope).add(0.55, 0.22, 0);
                Vec3 to = Vec3.atCenterOf(in.mark("anomaly")).add(0, 1, 0).subtract(lens);
                return tp(at.x, at.y, at.z, (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90),
                        (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
            }));
            ctx.waitTicks(5);
            server.runOnServer(srv -> LodgeScope.useForTest(srv.getPlayerList().getPlayers().getFirst(),
                    InvestigationState.get(srv).incident(id[0]).mark("scope")));
            ctx.waitTicks(20);
            ctx.takeScreenshot("rifle10_scope_view");
            ctx.waitTicks(40);
            boolean revealed = ctx.computeOnClient(mc -> RifleClient.scopeRevealed());
            ctx.takeScreenshot("rifle11_scope_revealed");
            if (!revealed) throw new AssertionError("the anomaly resolved through the scope");
            server.runOnServer(srv -> LodgeScope.stopForTest(srv.getPlayerList().getPlayers().getFirst()));
            ctx.waitTicks(4);
            // The curse gone: the rack unsealed, the rifle on it.
            server.runCommand("execute as @p run jjk incident complete " + id[0]);
            server.runCommand(atRack);
            ctx.waitTicks(20);
            ctx.takeScreenshot("rifle12_rack_open");

            // The Hunter's Shade (a placeholder model), out in the open.
            server.runCommand(server.computeOnServer(srv -> {
                BlockPos site = InvestigationState.get(srv).incident(id[0]).site;
                return tp(site.getX() + 12.5, site.getY() + 1, site.getZ() + 0.5, 90, 15);
            }));
            ctx.waitTicks(5);
            server.runCommand("execute at @a run summon jjk:forest_stalker ~-4 ~ ~ {NoAI:1b,Rotation:[-90f,0f]}");
            ctx.waitTicks(20);
            ctx.takeScreenshot("rifle13_stalker");
        }
    }
}
