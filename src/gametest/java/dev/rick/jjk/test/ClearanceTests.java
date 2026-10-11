package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.guardian.Guardians;
import dev.rick.jjk.progression.investigation.Clearance;
import dev.rick.jjk.progression.investigation.CursedAnchorEntity;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.mastery.Mastery;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * News clearance: the climb from Grade 4 to Grade 1 (two distinct contracts per step), each contract credited once,
 * failures change nothing, nobody is carried past their own clearance, credit needs real participation, old records
 * migrate, boards follow the reader's clearance, every grade has contracts with varied objectives, a Grade 1 contract is
 * a solo challenge whose guardian (body and look) is fixed at generation, and the designated, anchor and guardian
 * objectives complete exactly when they should.
 */
public class ClearanceTests {
    private static final String ENV = "jjk-test:clearance";
    private static int spot;

    private static ServerPlayer survivor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        Clearance.reset(p);
        return p;
    }

    private static Incident contract(GameTestHelper h, String template) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-60, 1, -60)));
        // Spread out (sites too close together count as crowded for real generation; here they just need to be apart).
        BlockPos at = h.absolutePos(new BlockPos(200 + (spot++) * 80, 1, 0));
        return Investigations.create(level, v, st, IncidentTemplate.get(template), new Sites.Site(at, 1, 0), level.getGameTime(), RandomSource.create(spot));
    }

    private static void finish(GameTestHelper h, Incident in, ServerPlayer... who) {
        for (ServerPlayer p : who) Investigations.damageForTest(in, p, 50);
        Investigations.completeForTest(h.getLevel().getServer(), in);
    }

    @GameTest(environment = "jjk-test:clearance_a")
    public void theClimbFromGrade4ToGrade1(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        h.assertTrue(Clearance.access(p) == 4, "everyone starts at Grade 4");
        Incident g3 = contract(h, "cliff_fall");
        h.assertTrue(Clearance.refusal(p, g3) != null, "a Grade 3 contract is locked at first: " + Clearance.refusal(p, g3));
        Incident a = contract(h, "livestock");
        finish(h, a, p);
        h.assertTrue(Clearance.access(p) == 4 && Clearance.completed(p, 4) == 1, "one Grade 4 contract isn't enough");
        // Replaying the same contract never counts twice.
        finish(h, a, p);
        h.assertTrue(Clearance.completed(p, 4) == 1 && Clearance.access(p) == 4, "the same contract counts once");
        finish(h, contract(h, "hound_kennel"), p);
        h.assertTrue(Clearance.access(p) == 3, "two distinct Grade 4 contracts open Grade 3");
        h.assertTrue(Clearance.refusal(p, g3) == null, "now Grade 3 is open");
        // A failed attempt (never completed) changes nothing.
        Incident failed = contract(h, "jilted_bride");
        Investigations.damageForTest(failed, p, 80);
        h.assertTrue(Clearance.access(p) == 3 && Clearance.completed(p, 3) == 0, "a failed attempt keeps clearance as it was");
        finish(h, g3, p);
        finish(h, contract(h, "millpond_toad"), p);
        h.assertTrue(Clearance.access(p) == 2, "two Grade 3 contracts open Grade 2");
        finish(h, contract(h, "drowned_chapel"), p);
        finish(h, contract(h, "kennel_master"), p);
        h.assertTrue(Clearance.access(p) == 1, "two Grade 2 contracts open Grade 1");
        Incident g1 = contract(h, "guardian_cliff");
        h.assertTrue(Clearance.refusal(p, g1) == null, "Grade 1 guardians are open now");
        // Saved and reloaded, it stays.
        InvestigationState.get(h.getLevel().getServer()).markDirtyForTest();
        InvestigationState.get(h.getLevel().getServer()).flush();
        InvestigationState.unload();
        h.assertTrue(Clearance.access(p) == 1, "clearance survives a restart");
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:clearance_b")
    public void nobodyIsCarriedAndCreditNeedsRealPart(GameTestHelper h) {
        ServerPlayer low = survivor(h), high = survivor(h), idle = survivor(h);
        Clearance.set(high, 1);
        Incident g2 = contract(h, "kennel_master");
        h.assertTrue(Clearance.refusal(low, g2) != null && Clearance.refusal(high, g2) == null, "only the cleared player may take it");
        // Even if the low player somehow fought in it, a contract above their clearance never credits them.
        finish(h, g2, high, low);
        h.assertTrue(Clearance.completed(low, 2) == 0 && Clearance.access(low) == 4, "no carrying past a locked tier");
        // Being there without fighting is no credit; a real share of the fight is.
        Incident g4 = contract(h, "livestock");
        Investigations.participateForTest(g4, idle);
        Investigations.damageForTest(g4, low, 40);
        Investigations.completeForTest(h.getLevel().getServer(), g4);
        h.assertTrue(Clearance.completed(idle, 4) == 0, "standing around earns nothing");
        h.assertTrue(Clearance.completed(low, 4) == 1, "fighting earns the contract");
        low.discard();
        high.discard();
        idle.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:clearance_c")
    public void reliableOldRecordsMigrate(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        var d = Mastery.data(p);
        for (int i = 0; i < 3; i++) d = d.recordIncident("GRADE_4");
        d = d.recordIncident("GRADE_3");
        Mastery.set(p, d);
        Clearance.forgetForTest(p);
        h.assertTrue(Clearance.access(p) == 3, "three old Grade 4 completions open Grade 3 (" + Clearance.access(p) + ")");
        h.assertTrue(Clearance.completed(p, 3) == 1, "and the old Grade 3 completion counts toward Grade 2");
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:clearance_d")
    public void boardsFollowTheReadersClearance(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        InvestigationState.Village v = Investigations.village(h.getLevel(), h.absolutePos(new BlockPos(-90, 1, -90)));
        boolean sawOwn = false, sawAbove = false;
        for (long seed = 0; seed < 200; seed++) {
            int t = Investigations.targetGradeForTest(p, v, seed * 0x9E3779B97F4A7C15L + 12345);
            h.assertTrue(t == 4 || t == 3, "a Grade 4 reader sees Grade 4, and now and then a locked Grade 3 (" + t + ")");
            sawOwn |= t == 4;
            sawAbove |= t == 3;
        }
        h.assertTrue(sawOwn && sawAbove, "both happen");
        Clearance.set(p, 2);
        for (long seed = 0; seed < 200; seed++) h.assertTrue(Investigations.targetGradeForTest(p, v, seed * 0x9E3779B97F4A7C15L + 777) != 1, "no guardian before Grade 1 clearance");
        Clearance.set(p, 1);
        boolean guardian = false;
        for (long seed = 0; seed < 200; seed++) guardian |= Investigations.targetGradeForTest(p, v, seed * 0x9E3779B97F4A7C15L + 99) == 1;
        h.assertTrue(guardian, "guardian contracts reach a Grade 1 reader");
        // Every grade has several varied, non-storyline contracts.
        for (CurseGrade g : new CurseGrade[] {CurseGrade.GRADE_4, CurseGrade.GRADE_3, CurseGrade.GRADE_2, CurseGrade.GRADE_1}) {
            long n = IncidentTemplate.all().stream().filter(t -> t.grade() == g && t.weight() > 0).count();
            h.assertTrue(n >= 3, g + ": at least three contracts (" + n + ")");
        }
        for (IncidentTemplate.Objective o : IncidentTemplate.Objective.values()) {
            h.assertTrue(IncidentTemplate.all().stream().anyMatch(t -> t.weight() > 0 && t.objective() == o), "some contract asks " + o);
        }
        h.assertTrue(IncidentTemplate.all().stream().filter(t -> t.grade() == CurseGrade.GRADE_1).allMatch(t -> t.objective() == IncidentTemplate.Objective.GUARDIAN),
                "every Grade 1 contract is a guardian");
        h.assertTrue(Guardians.BODIES.size() == 4, "all four guardian bodies are in the pool");
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:clearance_e")
    public void aGuardianContractIsSoloAndKeepsItsGuardian(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h);
        Clearance.set(a, 1);
        Clearance.set(b, 1);
        Incident g1 = contract(h, "guardian_hillside");
        String body = g1.guardian();
        int variant = g1.variant();
        h.assertTrue(Guardians.body(body) != null && variant >= 0 && variant < Guardians.body(body).variants(), "a body and a look, chosen at generation");
        Investigations.challengeForTest(g1, a);
        h.assertTrue(Clearance.refusal(a, g1) == null, "its challenger may go in");
        h.assertTrue(Clearance.refusal(b, g1) != null, "nobody else (solo challenge)");
        Incident again = Investigations.reloadForTest(h.getLevel().getServer(), g1);
        h.assertTrue(again.guardian().equals(body) && again.variant() == variant, "the same guardian for every retry, through a save");
        h.assertTrue(Investigations.brief(g1, a).contains("Exorcism Seal") && Investigations.brief(g1, a).contains("Solo"), "the report says solo, and to bring a seal");
        a.discard();
        b.discard();
        h.succeed();
    }

    private static List<Entity> curses(MinecraftServer server, Incident in) {
        return in.curses().stream().map(id -> Investigations.curseEntity(server, id)).filter(e -> e != null).toList();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:clearance_f")
    public void objectivesCompleteWhenTheyShould(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        Clearance.set(p, 1);
        InvestigationState st = InvestigationState.get(server);
        Incident des = contract(h, "crawler_culvert");
        Incident anc = contract(h, "shrine_anchor");
        Incident g1 = contract(h, "guardian_cliff");
        Investigations.challengeForTest(g1, p);
        CursedRealms.enter(p, des, st);
        CursedRealms.enter(p, anc, st);
        CursedRealms.enter(p, g1, st);
        // A second for the arenas' curses to be in the world.
        h.runAfterDelay(30, () -> {
            // Designated: the marked curse is the objective; the rest scatter when it falls.
            UUID target = Investigations.objectiveTargetForTest(des);
            h.assertTrue(target != null && curses(server, des).size() >= 2, "a designated curse among others (" + curses(server, des).size() + ")");
            for (Entity e : curses(server, des)) {
                if (!e.getUUID().equals(target)) {
                    ((CommonCurseEntity) e).kill((ServerLevel) e.level());
                    break;
                }
            }
            h.assertTrue(des.state() == Incident.State.ACTIVE, "killing an escort isn't the objective");
            Entity t = Investigations.curseEntity(server, target);
            ((CommonCurseEntity) t).kill((ServerLevel) t.level());
            h.assertTrue(des.state() == Incident.State.COMPLETE, "the designated curse falling completes it");
            h.assertTrue(curses(server, des).isEmpty(), "and the rest scatter");
            // Anchor: its guards falling isn't enough; the anchor is.
            Entity anchor = Investigations.curseEntity(server, Investigations.objectiveTargetForTest(anc));
            h.assertTrue(anchor instanceof CursedAnchorEntity, "a cursed anchor stands in it");
            for (Entity e : curses(server, anc)) ((CommonCurseEntity) e).kill((ServerLevel) e.level());
            h.assertTrue(anc.state() == Incident.State.ACTIVE, "guards gone, anchor still up: not done");
            ((CursedAnchorEntity) anchor).kill((ServerLevel) anchor.level());
            h.assertTrue(anc.state() == Incident.State.COMPLETE, "destroying the anchor completes it");
            // Guardian: one Grade 1 guardian (its body, or the body's stand-in until it exists); its defeat is the objective.
            List<Entity> gs = curses(server, g1);
            h.assertTrue(gs.size() == 1 && ((CommonCurseEntity) gs.get(0)).curseGrade() == CurseGrade.GRADE_1, "one Grade 1 guardian, alone (" + gs.size() + ")");
            ((CommonCurseEntity) gs.get(0)).kill((ServerLevel) gs.get(0).level());
            h.assertTrue(g1.state() == Incident.State.COMPLETE, "its defeat completes the combat objective");
            for (Incident in : List.of(des, anc, g1)) {
                var arena = CursedRealms.arenaOf(st, in.id);
                if (arena != null) CursedRealms.close(server, st, arena, true);
            }
            p.discard();
            h.succeed();
        });
    }
}
