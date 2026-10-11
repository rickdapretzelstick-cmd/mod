package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.EncounterWatch;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * A realm fight's safeguards through a real client: the quiet count of curses left; the last one revealed (outlined
 * through a wall, its distance over it, the pointer on the HUD); the stalled fight's pale Emergency Return beside the
 * player, and its channel. Opt-in: screenshots encounter*.png.
 */
public class EncounterClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        JJKConfig.get().general.autoAssignGojo = false;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("item replace entity @a armor.head with jjk:cursed_glasses");
            ctx.waitTicks(30);
            String[] id = new String[1];
            server.runOnServer(srv -> {
                JJKConfig.get().progression.enabled = true;
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel level = (ServerLevel) p.level();
                InvestigationState st = InvestigationState.get(srv);
                InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(-60, 0, 0));
                Incident in = Investigations.create(level, v, st, IncidentTemplate.get("old_mine"), new Sites.Site(p.blockPosition().offset(8, 0, 0), 1, 0),
                        level.getGameTime(), RandomSource.create(3));
                id[0] = in.id;
                CursedRealms.enter(p, in, st);
                p.getAbilities().invulnerable = true;
                p.onUpdateAbilities();
            });
            ctx.waitTicks(60);
            ctx.takeScreenshot("encounter1_count");

            // One curse left, held still, and a quiet spell: revealed.
            server.runOnServer(srv -> {
                ServerLevel realm = CursedRealms.level(srv);
                Incident in = InvestigationState.get(srv).incidents().get(id[0]);
                CommonCurseEntity keep = null;
                for (UUID u : in.curses()) {
                    if (realm.getEntity(u) instanceof CommonCurseEntity c) {
                        if (keep == null && !c.dormant()) keep = c;
                        else c.discard();
                    }
                }
                if (keep != null) keep.setNoAi(true);
            });
            // (The server can lag the client here: wait until the fight has seen the others go before the quiet spell.)
            for (int i = 0; i < 200 && !server.computeOnServer(srv -> EncounterWatch.remainingForTest(EncounterWatch.arenaForTest(srv, id[0])) == 1); i++) ctx.waitTick();
            server.runOnServer(srv -> EncounterWatch.ageForTest(EncounterWatch.arenaForTest(srv, id[0]), JJKConfig.get().encounters.revealAfter + 5));
            for (int i = 0; i < 200 && !server.computeOnServer(srv -> EncounterWatch.arenaForTest(srv, id[0]).watch().revealed()); i++) ctx.waitTick();
            ctx.waitTicks(20);
            // A wall between the player and it: the outline shows through.
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                ServerLevel realm = CursedRealms.level(srv);
                Incident in = InvestigationState.get(srv).incidents().get(id[0]);
                for (UUID u : in.curses()) {
                    if (!(realm.getEntity(u) instanceof CommonCurseEntity c)) continue;
                    var to = c.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90f;
                    p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, 0f);
                    var mid = p.position().add(c.position().subtract(p.position()).scale(0.5));
                    var side = new net.minecraft.world.phys.Vec3(-to.z, 0, to.x);
                    for (int s = -3; s <= 3; s++) {
                        for (int y = 0; y < 4; y++) {
                            BlockPos q = BlockPos.containing(mid.add(side.scale(s))).atY((int) Math.floor(p.getY()) + y);
                            realm.setBlockAndUpdate(q, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        }
                    }
                    break;
                }
            });
            ctx.waitTicks(20);
            boolean revealed = ctx.computeOnClient(mc -> {
                for (var e : mc.level.entitiesForRendering()) if (dev.rick.jjk.client.hud.EncounterHud.revealed(e)) return true;
                return false;
            });
            if (!revealed) throw new AssertionError("the last curse is revealed to the player");
            ctx.takeScreenshot("encounter2_revealed_through_wall");

            // The fight stalls: the Emergency Return opens beside the player.
            server.runOnServer(srv -> {
                var a = EncounterWatch.arenaForTest(srv, id[0]);
                var c = JJKConfig.get().encounters;
                EncounterWatch.ageForTest(a, c.emergencyAfter + c.recoverAfter);
            });
            for (int i = 0; i < 200 && !server.computeOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                return EncounterWatch.exitNear(p) != null;
            }); i++) ctx.waitTick();
            ctx.waitTicks(20);
            ctx.takeScreenshot("encounter3_emergency_return");
            server.runOnServer(srv -> {
                ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
                var exit = EncounterWatch.exitNear(p);
                if (exit != null) EncounterWatch.useExitForTest(p, exit);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("encounter4_channelling");
            for (int i = 0; i < 300 && ctx.computeOnClient(mc -> mc.level.dimension() == CursedRealms.DIMENSION); i++) ctx.waitTick();
            ctx.waitTicks(20);
            boolean home = ctx.computeOnClient(mc -> mc.level.dimension() != CursedRealms.DIMENSION);
            ctx.takeScreenshot("encounter5_home");
            if (!home) throw new AssertionError("the Emergency Return takes the player home");
        }
    }
}
