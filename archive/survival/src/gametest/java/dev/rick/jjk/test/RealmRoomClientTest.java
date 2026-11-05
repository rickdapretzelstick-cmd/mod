package dev.rick.jjk.test;

import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.GuardianArena;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.RealmRooms;
import dev.rick.jjk.progression.investigation.Sites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tuning aid (opt-in): every cursed realm environment, entered through its own incident as a player would, looked at from
 * where you arrive, from high in a corner, and from across the encounter space. Screenshots realm_<room>_*.png. The rooms
 * come from JJK_REALMS (comma separated) or all ten.
 */
public class RealmRoomClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getenv("JJK_REALMS");
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("time set midnight");
            server.runCommand("gamerule advance_time false");
            ctx.waitTicks(40);
            Map<String, String> via = new LinkedHashMap<>();
            via.put("distance_realm", "gojo_chain_1");
            via.put("theater_realm", "yuji_chain_1");
            via.put("chapel_realm", "jilted_bride");
            via.put("crater_realm", "stalker_crater");
            via.put("fight_club_realm", "hakari_chain_1");
            via.put("forest_realm", "hunting_lodge");
            for (String g : new String[] {"guardian_claw_hunter", "guardian_pale_stalker", "guardian_cyclops_bruiser", "guardian_veiled_reacher"}) via.put(g, "guardian_cliff");
            via.put("mine_realm", "old_mine");
            via.put("house_realm", "empty_house");
            via.put("pasture_realm", "livestock");
            via.put("cliff_realm", "cliff_fall");
            // No incident of their own: opened as a trial's realm is (the hall's battle room and the fallback).
            via.put("finger_bearer_realm", "@finger_bearer_realm");
            via.put("hollow_realm", "@no_such_realm");
            for (var e : via.entrySet()) {
                String room = e.getKey();
                if (only != null && !only.contains(room)) continue;
                Vec3[] views = server.computeOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                    ServerLevel level = srv.overworld();
                    InvestigationState st = InvestigationState.get(srv);
                    InvestigationState.Village v = Investigations.village(level, p.blockPosition().offset(-40, 0, -40));
                    p.setGameMode(GameType.CREATIVE);
                    var plan = RealmRooms.plan(room);
                    InvestigationState.Arena arena;
                    if (e.getValue().startsWith("@")) {
                        arena = CursedRealms.enterTrial(p, dev.rick.jjk.progression.story.PersonalTrials.VIEW + room, e.getValue().substring(1), st);
                    } else {
                    Incident in = null;
                    for (int seed = 0; seed < 120; seed++) {
                        Incident c = Investigations.create(level, v, st, IncidentTemplate.get(e.getValue()), new Sites.Site(p.blockPosition().offset(6, 0, 6), 1, 0),
                                level.getGameTime(), RandomSource.create(seed));
                        if (!room.startsWith("guardian_") || c.guardian().equals(room)) {
                            in = c;
                            break;
                        }
                    }
                    if (in == null) return null;
                    CursedRealms.enter(p, in, st);
                    arena = CursedRealms.arenaOf(st, in.id);
                    }
                    Vec3 o = new Vec3(arena.origin.getX(), arena.origin.getY(), arena.origin.getZ());
                    int[] b = plan.bounds();
                    Vec3 arrival = o.add(plan.marker("arrival")).add(0, 1.62, 0);
                    Vec3 center = o.add(plan.marker("encounter_center"));
                    if (plan.marker("landing") != null) arrival = o.add(plan.marker("landing")).add(0, 1.62, 0);
                    // Then close up: from the middle at head height, looking out each way (props seen from every side).
                    Vec3 eye = center.add(0, 1.62, 0);
                    return new Vec3[] {arrival, center.add(0, 1.5, 0), o.add(b[3] - 3, b[4] - 3.5, b[5] - 3), center.add(0, 1, 0),
                            o.add(b[0] + 3, b[4] - 4, b[2] + 3), center.add(0, 1, 6), eye, eye.add(6, -0.8, 0), eye, eye.add(-6, -0.8, 0),
                            eye, eye.add(0, -0.8, 6), eye, eye.add(0, -0.8, -6)};
                });
                if (views == null) continue;
                ctx.waitTicks(60);
                String[] names = {"arrival", "corner", "far_corner", "close_e", "close_w", "close_s", "close_n"};
                for (int i = 0; i < names.length; i++) {
                    Vec3 from = views[i * 2], to = views[i * 2 + 1];
                    server.runOnServer(srv -> {
                        ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                        p.setGameMode(GameType.SPECTATOR);
                        Vec3 d = to.subtract(from);
                        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                        p.teleportTo((ServerLevel) p.level(), from.x, from.y - 1.62, from.z, Set.of(), yaw, pitch, false);
                    });
                    ctx.waitTicks(25);
                    ctx.takeScreenshot("realm_" + room + "_" + names[i]);
                }
                server.runOnServer(srv -> {
                    ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
                    p.setGameMode(GameType.CREATIVE);
                    InvestigationState st = InvestigationState.get(srv);
                    var a = CursedRealms.arenaHere(p);
                    if (a != null) CursedRealms.close(srv, st, a, false);
                });
                ctx.waitTicks(30);
            }
        }
    }
}
