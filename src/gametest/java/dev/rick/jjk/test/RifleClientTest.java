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
    private static net.minecraft.client.KeyMapping key(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing key " + name)));
    }

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
            server.runCommand("tp @a 0 ~ 0 0 0");
            // The rifle fights from the Cursed Item slot (no technique: its moveset is simply yours), its array learned.
            dev.rick.jjk.config.JJKConfig.get().rifle.beamCooldown = 20;
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                dev.rick.jjk.core.character.CharacterService.assign(p, null);
                dev.rick.jjk.progression.tool.kit.CursedSlot.set(p, new net.minecraft.world.item.ItemStack(dev.rick.jjk.progression.ProgressionItems.CURSED_RIFLE));
                dev.rick.jjk.progression.tool.kit.CursedKits.update(p);
                String tree = dev.rick.jjk.progression.tool.CursedTools.CURSED_RIFLE.treeId();
                dev.rick.jjk.progression.mastery.Mastery.award(p, tree, 5000);
                for (String n : RifleTests.TO_BEAM) dev.rick.jjk.progression.mastery.Mastery.purchase(p, tree, n);
            });
            ctx.waitTicks(40);
            boolean drawn = ctx.computeOnClient(mc -> dev.rick.jjk.client.gear.CursedGear.rifleDrawn(mc.player));
            if (!drawn) throw new AssertionError("the equipped rifle is drawn");
            ctx.takeScreenshot("rifle1_first_person");
            net.fabricmc.fabric.api.client.gametest.v1.TestInput input = ctx.getInput();
            net.minecraft.client.KeyMapping snap = key(ctx, "key.jjk.skill_1"), aim = key(ctx, "key.jjk.skill_2"), ult = key(ctx, "key.jjk.ultimate");
            // Snap Shot: the rifle snaps up, fires on its tick.
            input.pressKey(snap);
            ctx.waitTicks(2);
            ctx.takeScreenshot("rifle1b_snap_raise");
            ctx.waitTicks(3);
            ctx.takeScreenshot("rifle1c_snap_fired");
            ctx.waitTicks(25);
            // Aimed Shot: up to the eye, then the scope.
            input.holdKey(aim);
            ctx.waitTicks(3);
            ctx.takeScreenshot("rifle1d_ads_rising");
            ctx.waitTicks(20);
            ctx.takeScreenshot("rifle7_scope");
            input.releaseKey(aim);
            ctx.waitTicks(25);
            // First person through the array: braced, charging, firing.
            input.holdKey(ult);
            ctx.waitTicks(20);
            String phase = ctx.computeOnClient(mc -> RifleClient.orIdle(mc.player.getId()).phase.name());
            if (!phase.equals("DEPLOY") && !phase.equals("CHARGE")) throw new AssertionError("G opens the array: " + phase);
            ctx.takeScreenshot("rifle1e_fp_deploy");
            ctx.waitTicks(30);
            ctx.takeScreenshot("rifle1f_fp_charge");
            ctx.waitTicks(15);
            input.releaseKey(ult);
            ctx.waitTicks(8);
            ctx.takeScreenshot("rifle1g_fp_beam");
            ctx.waitTicks(110);

            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(10);
            ctx.takeScreenshot("rifle2_third_person");
            server.runOnServer(srv -> RifleServer.setEnergy(srv.getPlayerList().getPlayers().getFirst(), 100));
            // The array from the front: deploying, then ready (held).
            input.holdKey(ult);
            ctx.waitTicks(16);
            ctx.takeScreenshot("rifle3_deploying");
            phase = ctx.computeOnClient(mc -> RifleClient.orIdle(mc.player.getId()).phase.name());
            if (!phase.equals("DEPLOY")) throw new AssertionError("the client follows the phase: " + phase);
            ctx.waitTicks(50);
            ctx.takeScreenshot("rifle4_ready");
            // Fired, seen from the side.
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            server.runCommand("tp @a 0 ~ 0 -60 0");
            ctx.waitTicks(4);
            input.releaseKey(ult);
            ctx.waitTicks(12);
            ctx.takeScreenshot("rifle5_beam");
            ctx.waitTicks(30);
            ctx.takeScreenshot("rifle6_beam_late");
            ctx.waitTicks(100);
            phase = ctx.computeOnClient(mc -> RifleClient.orIdle(mc.player.getId()).phase.name());
            if (!phase.equals("IDLE")) throw new AssertionError("back at rest after the beam: " + phase);
            float[] err = ctx.computeOnClient(mc -> dev.rick.jjk.client.rifle.RifleStance.errors(mc.player.getId()));
            if (err == null || err[0] > 1 || err[1] > 1) throw new AssertionError("both hands on the rifle: " + java.util.Arrays.toString(err));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            dev.rick.jjk.config.JJKConfig.get().rifle.beamCooldown = 240;

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
