package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.block.CauldronInfusions;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.guardian.EssenceCapture;
import dev.rick.jjk.progression.guardian.GuardianRemnantEntity;
import dev.rick.jjk.progression.investigation.Clearance;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.Vessels;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Grade 1 essence step: a defeated guardian leaves a remnant that waits (without a seal too); a seal starts a short
 * containment on the shared skill check, one mistake allowed; a failure spends nothing and the remnant stays for another
 * try; success spends exactly one seal and gives exactly one Sealed Grade 1 Essence (to a full inventory: an owner-bound
 * claim); the essence binds into an ordinary vessel at a cauldron (everything checked first); only an essence-bearing
 * vessel is taken by the infusion; and the containment's schedules always obey the fairness rules.
 */
public class EssenceTests {
    private static int spot;

    private static ServerPlayer survivor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        Clearance.set(p, 1);
        return p;
    }

    private static Incident guardianContract(GameTestHelper h, ServerPlayer challenger) {
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(level.getServer());
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-60, 1, -60)));
        BlockPos at = h.absolutePos(new BlockPos(300 + (spot++) * 90, 1, 40));
        Incident in = Investigations.create(level, v, st, IncidentTemplate.get("guardian_cliff"), new Sites.Site(at, 1, 0), level.getGameTime(),
                RandomSource.create(spot + 11));
        Investigations.challengeForTest(in, challenger);
        return in;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static GuardianRemnantEntity remnant(MinecraftServer server, Incident in) {
        ServerLevel realm = CursedRealms.level(server);
        InvestigationState.Arena a = CursedRealms.arenaOf(InvestigationState.get(server), in.id);
        if (a == null) return null;
        List<GuardianRemnantEntity> r = realm.getEntitiesOfClass(GuardianRemnantEntity.class, new AABB(a.origin).inflate(30), e -> in.id.equals(e.contract()));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Runs a containment through on the manual clock: every check answered dead on (or none, to fail). */
    private static void runContainment(ServerPlayer p, boolean answer) {
        EssenceCapture.Session s = EssenceCapture.of(p);
        int id = s.id, attempt = s.attempt();
        int seq = 0;
        EssenceCapture.inputForTest(p, new TrainingNet.Input(id, attempt, ++seq, TrainingNet.READY, -1, -1, 0));
        EssenceCapture.advanceForTest(p, JJKConfig.get().training.captureIntroMs + 1);
        for (TrainingCheck c : s.checks()) {
            if (EssenceCapture.of(p) != s || s.state() != TrainingNet.ACTIVE) break;
            double to = c.atMs() - s.clock();
            if (to > 0) EssenceCapture.advanceForTest(p, to);
            if (answer && s.state() == TrainingNet.ACTIVE) {
                EssenceCapture.inputForTest(p, new TrainingNet.Input(id, attempt, ++seq, TrainingNet.PRESS, c.gauge(), c.id(), c.atMs()));
                EssenceCapture.inputForTest(p, new TrainingNet.Input(id, attempt, ++seq, TrainingNet.RELEASE, c.gauge(), c.id(), c.atMs() + 30));
            }
        }
        // To the end, then through the result.
        for (int i = 0; i < 120 && EssenceCapture.of(p) == s; i++) EssenceCapture.advanceForTest(p, 100);
    }

    @GameTest(maxTicks = 400, environment = "jjk-test:essence_a")
    public void aRemnantWaitsAndOneSealCapturesItOnce(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        InvestigationState st = InvestigationState.get(server);
        Incident in = guardianContract(h, p);
        CursedRealms.enter(p, in, st);
        h.runAfterDelay(30, () -> {
            CommonCurseEntity g = (CommonCurseEntity) Investigations.curseEntity(server, Investigations.objectiveTargetForTest(in));
            h.assertTrue(g != null, "the guardian is there");
            g.kill((ServerLevel) g.level());
            h.assertTrue(in.state() == Incident.State.COMPLETE && in.capture() == Incident.CAPTURE_PENDING, "down: the combat objective is done, its essence waits");
            h.runAfterDelay(25, () -> {
                GuardianRemnantEntity r = remnant(server, in);
                h.assertTrue(r != null, "a remnant lies where it fell");
                h.assertTrue(CursedRealms.arenaOf(st, in.id) != null, "the arena isn't torn down under a waiting remnant");
                h.assertTrue(r.hurtServer((ServerLevel) r.level(), r.damageSources().generic(), 100) == false, "it can't be hurt");
                p.teleportTo(r.getX() + 1.5, r.getY(), r.getZ());
                // No seal: nothing starts, nothing is lost.
                h.assertTrue(!EssenceCapture.begin(p, r), "no seal, no containment");
                h.assertTrue(in.capture() == Incident.CAPTURE_PENDING, "and the capture is kept");
                p.getInventory().add(new ItemStack(ProgressionItems.EXORCISM_SEAL, 2));
                EssenceCapture.setManualClockForTest(true);
                try {
                    // A failed containment: no seal spent, the remnant still there, another try at once.
                    h.assertTrue(EssenceCapture.begin(p, r), "a seal starts the containment");
                    List<TrainingCheck> checks = EssenceCapture.of(p).checks();
                    h.assertTrue(checks.size() >= 6, "several checks (" + checks.size() + ")");
                    runContainment(p, false);
                    h.assertTrue(!EssenceCapture.active(p), "the failed attempt is over");
                    h.assertTrue(count(p, ProgressionItems.EXORCISM_SEAL) == 2, "a failure spends no seal");
                    h.assertTrue(in.capture() == Incident.CAPTURE_PENDING && remnant(server, in) != null, "the remnant waits for another try");
                    // Success: one seal, one essence, once.
                    h.assertTrue(EssenceCapture.begin(p, r), "an immediate retry");
                    runContainment(p, true);
                    h.assertTrue(count(p, ProgressionItems.EXORCISM_SEAL) == 1, "exactly one seal spent (" + count(p, ProgressionItems.EXORCISM_SEAL) + ")");
                    h.assertTrue(count(p, ProgressionItems.SEALED_GRADE1_ESSENCE) == 1, "exactly one Sealed Grade 1 Essence");
                    h.assertTrue(in.capture() == Incident.CAPTURE_DONE, "the essence is sealed");
                    h.assertTrue(Investigations.captureRefusal(in, p) != null, "and can't be sealed again");
                    Incident again = Investigations.reloadForTest(server, in);
                    h.assertTrue(again.capture() == Incident.CAPTURE_DONE, "through a save");
                } finally {
                    EssenceCapture.setManualClockForTest(false);
                }
                var arena = CursedRealms.arenaOf(st, in.id);
                if (arena != null) CursedRealms.close(server, st, arena, true);
                p.discard();
                h.succeed();
            });
        });
    }

    @GameTest(environment = "jjk-test:essence_b")
    public void onlyTheChallengerAndAFullInventoryMakesAClaim(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        ServerPlayer other = h.makeMockServerPlayerInLevel();
        Clearance.set(other, 1);
        Incident in = guardianContract(h, p);
        h.assertTrue(Investigations.captureRefusal(in, p) != null, "nothing to contain before the guardian falls");
        Investigations.guardianFellForTest(server, in);
        h.assertTrue(Investigations.captureRefusal(in, other) != null, "the remnant answers only to its challenger");
        h.assertTrue(Investigations.captureRefusal(in, p) == null, "its challenger may contain it");
        // A remnant left behind survives a save.
        h.assertTrue(Investigations.reloadForTest(server, in).capture() == Incident.CAPTURE_PENDING, "a pending capture persists");
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        Incident live = InvestigationState.get(server).incident(in.id);
        Investigations.markCaptured(server, live, Vessels.sealed(live.id, Investigations.guardianName(live)), p);
        h.assertTrue(live.claimOwed() && count(p, ProgressionItems.SEALED_GRADE1_ESSENCE) == 0, "a full inventory: the essence is held as a claim");
        Investigations.deliverClaimsForTest(server);
        h.assertTrue(live.claimOwed(), "still no room: still held");
        Investigations.markCaptured(server, live, Vessels.sealed(live.id, "x"), p);
        p.getInventory().setItem(5, ItemStack.EMPTY);
        other.getInventory().clearContent();
        Investigations.deliverClaimsForTest(server);
        h.assertTrue(!live.claimOwed() && count(p, ProgressionItems.SEALED_GRADE1_ESSENCE) == 1, "room made: delivered, once");
        h.assertTrue(count(other, ProgressionItems.SEALED_GRADE1_ESSENCE) == 0, "and to nobody else");
        Investigations.deliverClaimsForTest(server);
        h.assertTrue(count(p, ProgressionItems.SEALED_GRADE1_ESSENCE) == 1, "never twice");
        p.discard();
        other.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:essence_c")
    public void anEssenceBindsIntoAnOrdinaryVesselAtACauldron(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivor(h);
        KitOwnership.get(level.getServer()).release("yuta");
        BlockPos pos = h.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
        ItemStack essence = Vessels.sealed("c1", "a test guardian");
        // No vessel in the other hand: refused, nothing spent.
        p.setItemInHand(InteractionHand.MAIN_HAND, essence);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        h.assertTrue(Vessels.useOnCauldron(p, level, pos) == InteractionResult.FAIL && essence.getCount() == 1, "no vessel: refused, the essence kept");
        // An ordinary ring: bound.
        ItemStack ring = new ItemStack(ProgressionItems.CURSED_RING);
        h.assertTrue(Vessels.ordinary(ring) && !Vessels.essenceBearing(ring), "an ordinary vessel to begin with");
        h.assertTrue(CauldronInfusions.indexOf(ring) < 0, "the infusion won't take an ordinary vessel");
        p.setItemInHand(InteractionHand.OFF_HAND, ring);
        h.assertTrue(Vessels.useOnCauldron(p, level, pos) == InteractionResult.SUCCESS, "bound at the cauldron");
        h.assertTrue(essence.isEmpty(), "the essence is spent");
        ItemStack bound = p.getOffhandItem();
        h.assertTrue(Vessels.essenceBearing(bound) && !Vessels.ordinary(bound), "the ring now bears an essence (in its data)");
        h.assertTrue(bound.get(net.minecraft.core.component.DataComponents.LORE).lines().get(0).getString().startsWith("Essence-bearing"), "and says so");
        h.assertTrue(CauldronInfusions.indexOf(bound) >= 0, "the infusion takes it now");
        // An essence-bearing vessel can't take a second essence.
        ItemStack second = Vessels.sealed("c2", "another");
        p.setItemInHand(InteractionHand.MAIN_HAND, second);
        h.assertTrue(Vessels.useOnCauldron(p, level, pos) == InteractionResult.FAIL && second.getCount() == 1, "one essence per vessel, nothing spent");
        // A character already claimed by someone else: refused before anything is spent.
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        KitOwnership.get(level.getServer()).tryClaim(owner.getUUID(), owner.getName().getString(), "yuta", "test");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ProgressionItems.CURSED_RING));
        h.assertTrue(Vessels.useOnCauldron(p, level, pos) == InteractionResult.FAIL && second.getCount() == 1, "the character is taken: refused, the essence kept");
        KitOwnership.get(level.getServer()).release("yuta");
        owner.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:essence_d")
    public void containmentSchedulesAreFair(GameTestHelper h) {
        JJKConfig.Training cfg = JJKConfig.get().training;
        JJKConfig.Training rules = EssenceCapture.rules();
        h.assertTrue(cfg.captureDurationMs >= 6000 && cfg.captureDurationMs <= 8000, "six to eight seconds");
        h.assertTrue(cfg.captureMaxMisses == 2, "one mistake allowed, the second fails");
        for (long seed = 1; seed <= 300; seed++) {
            List<TrainingCheck> c = TrainingSchedule.containment(seed * 0x9E3779B97F4A7C15L, cfg);
            List<String> problems = TrainingSchedule.problems(c, rules);
            h.assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
            boolean[] used = new boolean[3];
            boolean overlap = false;
            for (TrainingCheck a : c) {
                used[a.gauge()] = true;
                for (TrainingCheck b : c) if (b.gauge() != a.gauge() && b.cueMs() < a.endMs() && b.cueMs() > a.cueMs()) overlap = true;
            }
            h.assertTrue(used[0] && used[1] && used[2], "seed " + seed + ": all three circles");
            h.assertTrue(overlap, "seed " + seed + ": the circles overlap");
            h.assertTrue(c.size() >= 6, "seed " + seed + ": enough checks (" + c.size() + ")");
        }
        h.succeed();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:essence_e")
    public void aChallengerComesBackForTheRemnant(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        InvestigationState st = InvestigationState.get(server);
        Incident in = guardianContract(h, p);
        Investigations.guardianFellForTest(server, in);
        Investigations.completeForTest(server, in);
        h.assertTrue(in.state() == Incident.State.COMPLETE && Investigations.capturePending(in), "won, its remnant left behind");
        // Back through the breach: the arena again, only the remnant in it (no guardian, nothing hostile).
        h.assertTrue(CursedRealms.enter(p, in, st), "the challenger can go back for it");
        h.assertTrue(in.state() == Incident.State.COMPLETE, "the contract stays complete");
        h.runAfterDelay(25, () -> {
            GuardianRemnantEntity r = remnant(server, in);
            h.assertTrue(r != null, "its remnant is raised again where it fell");
            h.assertTrue(Investigations.curseEntity(server, Investigations.objectiveTargetForTest(in)) == null || !Investigations.curseEntity(server, Investigations.objectiveTargetForTest(in)).isAlive(),
                    "the guardian doesn't come back");
            InvestigationState.Arena a = CursedRealms.arenaOf(st, in.id);
            BlockPos exit = BlockPos.containing(CursedRealms.arrivalOf(a)).offset(2, 0, 0);
            var leave = dev.rick.jjk.progression.investigation.CursedBreaches.find(CursedRealms.level(server), CursedRealms.LEAVE + a.slot, exit);
            h.assertTrue(leave != null, "and a way out beside the arrival");
            p.teleportTo(leave.getX(), leave.getY(), leave.getZ());
            dev.rick.jjk.progression.investigation.CursedBreaches.useForTest(p, leave);
            h.assertTrue(!CursedRealms.inRealm(p), "the way out takes them home");
            h.assertTrue(Investigations.capturePending(in), "the remnant still waits");
            h.runAfterDelay(110, () -> {
                h.assertTrue(CursedRealms.arenaOf(st, in.id) == null, "an empty remnant arena goes (no realm left loaded for it)");
                h.assertTrue(Investigations.capturePending(in), "and the capture is still kept");
                p.discard();
                h.succeed();
            });
        });
    }
}
