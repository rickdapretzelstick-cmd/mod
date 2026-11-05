package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.story.CharacterStories;
import dev.rick.jjk.progression.story.CharacterStory;
import dev.rick.jjk.progression.story.PersonalTrials;
import dev.rick.jjk.progression.story.RelicItem;
import dev.rick.jjk.progression.story.UniqueRelics;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingEvaluator;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
import dev.rick.jjk.progression.training.TrainingSessions;
import dev.rick.jjk.progression.training.TrialPropEntity;
import dev.rick.jjk.progression.training.TrialRooms;
import dev.rick.jjk.yuta.RikaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Character training: every character's schedules obey the fairness rules for many seeds (telegraphs, same-key gaps,
 * staggering, phase windows, overlap by phase, hold/release and the aligned finale); the evaluator can't be beaten by
 * spamming, holding a key, repeating or replaying packets, and a late packet can't buy a better timing; a player who
 * hits every window passes every character's schedule; and a session runs its whole lifecycle on the server (intro,
 * challenge, failure, retry, success, cleanup), unlocks the base kit exactly once, offers practice afterwards with no
 * reward, and two participants never touch each other's sessions.
 */
public class TrainingTests {
    private static final String ENV = "jjk-test:story";
    private static final String[] KITS = {"yuji", "gojo", "yuta", "ryu", "hakari"};

    private static JJKConfig.Training cfg() {
        return new JJKConfig.Training();
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int i = 0; i < 6; i++) for (int k = 0; k < 6; k++) h.setBlock(i, 0, k, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        return p;
    }

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static void freeKit(GameTestHelper h, String kit) {
        KitOwnership.get(server(h)).release(kit);
        UniqueRelics.get(server(h)).reset(RelicItem.key(kit));
    }

    // --- Schedules ---

    @GameTest(environment = ENV)
    public void everyCharactersScheduleIsFair(GameTestHelper h) {
        JJKConfig.Training c = cfg();
        for (String kit : KITS) {
            for (long seed = 1; seed <= 40; seed++) {
                List<TrainingCheck> s = TrainingSchedule.generate(kit, seed * 7919L, c);
                List<String> problems = TrainingSchedule.problems(s, c);
                h.assertTrue(problems.isEmpty(), kit + " seed " + seed + ": " + (problems.isEmpty() ? "" : problems.get(0)));
                h.assertTrue(s.size() >= 25, kit + ": a full 45 seconds of checks (" + s.size() + ")");
                h.assertTrue(TrainingSchedule.maxConcurrent(s, 0, c.phase2AtMs) <= 2, kit + ": phase 1 is mostly one at a time");
                h.assertTrue(TrainingSchedule.maxConcurrent(s, c.phase3AtMs, c.durationMs) == 3, kit + ": phase 3 overlaps all three");
                h.assertTrue(TrainingSchedule.maxConcurrent(s, c.phase2AtMs, c.phase3AtMs) >= 2, kit + ": phase 2 overlaps two");
                // Same seed, same schedule (client and server agree).
                h.assertTrue(s.equals(TrainingSchedule.generate(kit, seed * 7919L, c)), kit + ": deterministic");
            }
        }
        List<TrainingCheck> yuta = TrainingSchedule.generate("yuta", 99, c);
        h.assertTrue(yuta.stream().anyMatch(k -> k.hold() && k.atMs() >= c.phase2AtMs && k.atMs() < c.phase3AtMs), "Yuta: holds in phase 2");
        h.assertTrue(yuta.stream().filter(k -> k.hold() && k.mod() == TrainingCheck.FINALE).count() == 3, "Yuta: three staggered releases to finish");
        h.assertTrue(yuta.stream().noneMatch(k -> k.hold() && k.atMs() < c.phase2AtMs), "Yuta: taps only in phase 1");
        List<TrainingCheck> hakari = TrainingSchedule.generate("hakari", 99, c);
        TrainingCheck last = hakari.get(hakari.size() - 1);
        h.assertTrue(hakari.stream().filter(k -> k.atMs() == last.atMs() && k.mod() == TrainingCheck.ALIGNED).count() == 3, "Hakari: the announced triple, aligned");
        List<TrainingCheck> yuji = TrainingSchedule.generate("yuji", 99, c);
        h.assertTrue(yuji.stream().anyMatch(k -> k.mod() == TrainingCheck.FAST) && yuji.stream().anyMatch(k -> k.mod() == TrainingCheck.SLOW), "Yuji: announced disturbances");
        h.assertTrue(yuji.stream().noneMatch(k -> k.mod() != TrainingCheck.PLAIN && k.atMs() < c.phase2AtMs), "Yuji: three steady speeds in phase 1");
        List<TrainingCheck> ryu = TrainingSchedule.generate("ryu", 99, c);
        h.assertTrue(ryu.stream().filter(k -> k.mod() == TrainingCheck.FINALE).count() == 3, "Ryu: three tight checks to finish");
        h.succeed();
    }

    /** Presses every check at its centre (and releases holds at theirs), in time order: the schedule must be playable. */
    private static int playPerfectly(List<TrainingCheck> s, double offsetFraction, int lagMs) {
        TrainingEvaluator e = new TrainingEvaluator(s, 5, 300, 120);
        record Ev(double t, int action, TrainingCheck c) {}
        List<Ev> evs = new ArrayList<>();
        for (TrainingCheck c : s) {
            evs.add(new Ev(c.atMs() + c.goodMs() * offsetFraction, TrainingEvaluator.PRESS, c));
            evs.add(new Ev(c.hold() ? c.releaseMs() + c.goodMs() * offsetFraction : c.atMs() + c.goodMs() * offsetFraction + 40, TrainingEvaluator.RELEASE, c));
        }
        evs.sort(Comparator.comparingDouble(Ev::t));
        int seq = 0;
        for (Ev ev : evs) {
            e.expire(ev.t() + lagMs);
            e.input(++seq, ev.action(), ev.c().gauge(), ev.c().id(), ev.t(), ev.t() + lagMs);
        }
        e.expire(1e9);
        return e.misses();
    }

    @GameTest(environment = ENV)
    public void everyWindowHitPassesEveryCharacter(GameTestHelper h) {
        JJKConfig.Training c = cfg();
        for (String kit : KITS) {
            for (long seed = 1; seed <= 15; seed++) {
                List<TrainingCheck> s = TrainingSchedule.generate(kit, seed * 31L, c);
                h.assertTrue(playPerfectly(s, 0, 60) == 0, kit + ": every check pressed on time passes (seed " + seed + ")");
                // GOOD (not PERFECT) everywhere, with a quarter-second round trip, still passes.
                h.assertTrue(playPerfectly(s, 0.4, 250) == 0, kit + ": all-GOOD timing with latency passes (seed " + seed + ")");
            }
        }
        h.succeed();
    }

    // --- The evaluator's rules ---

    private static List<TrainingCheck> simple() {
        // Two taps on gauge 0, a hold on gauge 1, two more taps on gauge 2 (for the five-miss rule).
        return List.of(
                new TrainingCheck(0, 0, false, 200, 1000, 1000, 300, 105, 1600, TrainingCheck.PLAIN),
                new TrainingCheck(1, 1, true, 1500, 2300, 3200, 300, 105, 2000, TrainingCheck.PLAIN),
                new TrainingCheck(2, 0, false, 2200, 3000, 3000, 300, 105, 1600, TrainingCheck.PLAIN),
                new TrainingCheck(3, 2, false, 3800, 4600, 4600, 300, 105, 1600, TrainingCheck.PLAIN),
                new TrainingCheck(4, 2, false, 5600, 6400, 6400, 300, 105, 1600, TrainingCheck.PLAIN),
                new TrainingCheck(5, 0, false, 7200, 8000, 8000, 300, 105, 1600, TrainingCheck.PLAIN),
                new TrainingCheck(6, 1, false, 8800, 9600, 9600, 300, 105, 1600, TrainingCheck.PLAIN));
    }

    @GameTest(environment = ENV)
    public void spamHeldKeysAndRepeatedPacketsNeverPass(GameTestHelper h) {
        // A press while the needle only travels (nothing cued) does nothing.
        TrainingEvaluator e = new TrainingEvaluator(simple(), 5, 300, 120);
        h.assertTrue(e.input(1, TrainingEvaluator.PRESS, 0, -1, 100, 100) == null, "travel press ignored");
        e.input(2, TrainingEvaluator.RELEASE, 0, -1, 120, 120);
        h.assertTrue(e.misses() == 0, "and costs nothing");
        // Spam once the cue is up: the first early press is the check's miss; the rest find nothing to score.
        TrainingEvaluator.Outcome o = e.input(3, TrainingEvaluator.PRESS, 0, 0, 600, 600);
        h.assertTrue(o != null && o.result() == TrainingCheck.MISS, "early press is a miss");
        e.input(4, TrainingEvaluator.RELEASE, 0, 0, 620, 620);
        for (int i = 0; i < 6; i++) {
            e.input(5 + i * 2, TrainingEvaluator.PRESS, 0, 0, 900 + i * 20, 900 + i * 20);
            e.input(6 + i * 2, TrainingEvaluator.RELEASE, 0, 0, 905 + i * 20, 905 + i * 20);
        }
        h.assertTrue(e.misses() == 1 && e.result(0) == TrainingCheck.MISS, "one check, at most one mistake (no success by spam)");

        // A held key never presses again: held from before check 2's cue, it can't satisfy it.
        TrainingEvaluator held = new TrainingEvaluator(simple(), 5, 300, 120);
        held.input(1, TrainingEvaluator.PRESS, 0, 0, 1000, 1000);
        h.assertTrue(held.result(0) == TrainingCheck.PERFECT, "a press on the centre is PERFECT");
        h.assertTrue(held.input(2, TrainingEvaluator.PRESS, 0, 2, 3000, 3000) == null, "a key still down can't press check 2");
        held.expire(4000);
        h.assertTrue(held.result(2) == TrainingCheck.MISS, "so check 2 passes as a miss");

        // Repeated and stale packets.
        TrainingEvaluator dup = new TrainingEvaluator(simple(), 5, 300, 120);
        h.assertTrue(dup.input(5, TrainingEvaluator.PRESS, 0, 0, 1000, 1010) != null, "first copy scored");
        h.assertTrue(dup.input(5, TrainingEvaluator.PRESS, 0, 0, 1000, 1020) == null, "repeat (same seq) ignored");
        h.assertTrue(dup.input(4, TrainingEvaluator.RELEASE, 0, 0, 1050, 1060) == null, "older seq ignored");
        h.assertTrue(dup.input(6, TrainingEvaluator.PRESS, 2, 1, 4600, 4600) == null, "a check id that isn't this key's current check is ignored");
        h.assertTrue(dup.misses() == 0, "none of it cost a mistake");

        // A late packet can't claim the perfect moment: its stamp is clamped to the latency allowance.
        TrainingEvaluator late = new TrainingEvaluator(simple(), 5, 300, 120);
        o = late.input(1, TrainingEvaluator.PRESS, 0, 0, 1000, 1600);
        h.assertTrue(o != null && o.result() == TrainingCheck.MISS, "stamped on time but arriving 600 ms late: a miss");
        // Nor can a packet stamped in the future.
        TrainingEvaluator early = new TrainingEvaluator(simple(), 5, 300, 120);
        o = early.input(1, TrainingEvaluator.PRESS, 0, 0, 1000, 500);
        h.assertTrue(o != null && o.result() == TrainingCheck.MISS, "a stamp ahead of the receiver's clock is pulled back (early)");
        h.succeed();
    }

    @GameTest(environment = ENV)
    public void holdReleaseScoresOnceAndFiveMissesFailOnce(GameTestHelper h) {
        TrainingEvaluator e = new TrainingEvaluator(simple(), 5, 300, 120);
        TrainingEvaluator.Outcome o = e.input(1, TrainingEvaluator.PRESS, 1, 1, 2300, 2300);
        h.assertTrue(o != null && o.result() == TrainingCheck.HOLDING, "the hold's press lands");
        o = e.input(2, TrainingEvaluator.RELEASE, 1, 1, 3200, 3200);
        h.assertTrue(o != null && o.result() == TrainingCheck.PERFECT, "released on time: one success");
        // Released early: one miss for the pair.
        TrainingEvaluator early = new TrainingEvaluator(simple(), 5, 300, 120);
        early.input(1, TrainingEvaluator.PRESS, 1, 1, 2300, 2300);
        o = early.input(2, TrainingEvaluator.RELEASE, 1, 1, 2700, 2700);
        h.assertTrue(o != null && o.result() == TrainingCheck.MISS && early.misses() == 1, "early release: one miss");
        // Never released: one miss once its release window is over.
        TrainingEvaluator never = new TrainingEvaluator(simple(), 5, 300, 120);
        never.input(1, TrainingEvaluator.PRESS, 1, 1, 2300, 2300);
        never.expire(3200 + 151 + 300);
        h.assertTrue(never.result(1) == TrainingCheck.MISS, "held too long: one miss");
        // Ignore everything: five misses fail, exactly once; nothing scores afterwards.
        TrainingEvaluator idle = new TrainingEvaluator(simple(), 5, 300, 120);
        idle.expire(20_000);
        h.assertTrue(idle.failed() && idle.misses() == 5, "five misses fail (" + idle.misses() + ")");
        h.assertTrue(idle.input(1, TrainingEvaluator.PRESS, 1, 6, 9600, 9600) == null, "after failing nothing more is scored");
        h.succeed();
    }

    // --- Sessions on the server ---

    private static int seq;

    private static void send(ServerPlayer p, int action, int gauge, int check, double ms) {
        TrainingSessions.Session s = TrainingSessions.of(p);
        if (s == null) return;
        TrainingSessions.inputForTest(p, new TrainingNet.Input(s.id, s.attempt(), ++seq, action, gauge, check, (int) Math.round(ms)));
    }

    /** Plays the running attempt with the given offset from every centre, stepping the session's clock 50 ms at a time. */
    private static void play(ServerPlayer p, double offsetFraction) {
        TrainingSessions.Session s = TrainingSessions.of(p);
        if (s == null) return;
        send(p, TrainingNet.READY, -1, -1, 0);
        TrainingSessions.advanceForTest(p, 2050);
        record Ev(double t, int action, TrainingCheck c) {}
        List<Ev> evs = new ArrayList<>();
        for (TrainingCheck c : s.checks()) {
            double at = c.atMs() + c.goodMs() * offsetFraction;
            evs.add(new Ev(at, TrainingNet.PRESS, c));
            evs.add(new Ev(c.hold() ? c.releaseMs() + c.goodMs() * offsetFraction : at + 40, TrainingNet.RELEASE, c));
        }
        evs.sort(Comparator.comparingDouble(Ev::t));
        int i = 0;
        while (s.state() == TrainingNet.ACTIVE && s.clock() < 47_000) {
            TrainingSessions.advanceForTest(p, 25);
            while (i < evs.size() && evs.get(i).t() <= s.clock()) {
                Ev ev = evs.get(i++);
                send(p, ev.action(), ev.c().gauge(), ev.c().id(), ev.t());
            }
        }
    }

    @GameTest(maxTicks = 200, environment = ENV)
    public void aPassUnlocksTheBaseKitOnceAndPracticeGivesNothing(GameTestHelper h) {
        TrainingSessions.setManualClockForTest(true);
        freeKit(h, "hakari");
        ServerPlayer p = survivor(h, 2, 2);
        PersonalTrials.startNow(p, CharacterStories.HAKARI, false);
        CursedRealms.finishPullForTest(p);
        TrainingSessions.Session s = TrainingSessions.of(p);
        h.assertTrue(s != null && !s.practice && CursedRealms.inRealm(p), "a real session, in its room");
        h.assertTrue(ClashCommon.clashing(p), "held still while it runs");
        play(p, 0);
        h.assertTrue(s.state() == TrainingNet.SUCCESS, "every window hit: SUCCESS (" + s.state() + ", misses " + s.evaluator().misses() + ")");
        h.assertTrue(KitOwnership.get(server(h)).isOwner("hakari", p.getUUID()), "the base kit is claimed");
        h.assertTrue(!TechniqueProgression.awakened(p, "hakari"), "and only the base kit");
        TrainingSessions.leaveNow(p);
        h.assertTrue(!TrainingSessions.inTraining(p) && !CursedRealms.inRealm(p), "home again");
        // Again, as its owner: practice, which can grant nothing.
        PersonalTrials.startNow(p, CharacterStories.HAKARI, false);
        CursedRealms.finishPullForTest(p);
        TrainingSessions.Session again = TrainingSessions.of(p);
        h.assertTrue(again != null && again.practice, "the owner practises");
        h.assertTrue(PersonalTrials.completeNow(p), "practice passed");
        h.assertTrue(KitOwnership.get(server(h)).isOwner("hakari", p.getUUID()), "still the owner, nothing more");
        TrainingSessions.leaveNow(p);
        freeKit(h, "hakari");
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 200, environment = ENV)
    public void failureRetryAndCancelNeverReward(GameTestHelper h) {
        TrainingSessions.setManualClockForTest(true);
        freeKit(h, "ryu");
        ServerPlayer p = survivor(h, 2, 2);
        PersonalTrials.startNow(p, CharacterStories.RYU, false);
        CursedRealms.finishPullForTest(p);
        TrainingSessions.Session s = TrainingSessions.of(p);
        h.assertTrue(s != null, "a session");
        send(p, TrainingNet.READY, -1, -1, 0);
        TrainingSessions.advanceForTest(p, 2050);
        h.assertTrue(s.state() == TrainingNet.ACTIVE, "the challenge starts after the intro");
        // Do nothing: the misses pile up to five and the attempt fails, once.
        while (s.state() == TrainingNet.ACTIVE && s.clock() < 46_000) TrainingSessions.advanceForTest(p, 100);
        h.assertTrue(s.state() == TrainingNet.FAILURE && s.evaluator().misses() == 5, "five misses fail (" + s.evaluator().misses() + ")");
        h.assertTrue(!KitOwnership.get(server(h)).isClaimed("ryu"), "no reward");
        // Retry: a fresh attempt with a fresh schedule, nothing carried over.
        int attempt = s.attempt();
        send(p, TrainingNet.RETRY, -1, -1, 0);
        h.assertTrue(s.attempt() == attempt + 1 && s.state() == TrainingNet.INTRO && s.evaluator().misses() == 0, "retry resets");
        // Stale input from the old attempt is ignored.
        TrainingSessions.inputForTest(p, new TrainingNet.Input(s.id, attempt, ++seq, TrainingNet.LEAVE, -1, -1, 0));
        h.assertTrue(TrainingSessions.inTraining(p), "a stale LEAVE did nothing");
        // A clear cancel mid-attempt: home, nothing gained, the relic untouched.
        send(p, TrainingNet.READY, -1, -1, 0);
        TrainingSessions.advanceForTest(p, 2050);
        TrainingSessions.inputForTest(p, new TrainingNet.Input(s.id, s.attempt(), ++seq, TrainingNet.CANCEL, -1, -1, 0));
        h.assertTrue(!TrainingSessions.inTraining(p) && !CursedRealms.inRealm(p), "cancel returns them");
        h.assertTrue(!KitOwnership.get(server(h)).isClaimed("ryu"), "and grants nothing");
        h.succeedWhen(() -> {
            h.assertTrue(!ClashCommon.clashing(p), "control comes back");
            freeKit(h, "ryu");
            p.discard();
        });
    }

    @GameTest(maxTicks = 200, environment = ENV)
    public void aSilentClientIsNeverScoredAndTwoParticipantsStayApart(GameTestHelper h) {
        TrainingSessions.setManualClockForTest(true);
        ServerPlayer a = survivor(h, 1, 1);
        ServerPlayer b = survivor(h, 4, 4);
        PersonalTrials.startNow(a, CharacterStories.YUJI, true);
        PersonalTrials.startNow(b, CharacterStories.GOJO, true);
        CursedRealms.finishPullForTest(a);
        CursedRealms.finishPullForTest(b);
        TrainingSessions.Session sa = TrainingSessions.of(a), sb = TrainingSessions.of(b);
        h.assertTrue(sa != null && sb != null && sa.id != sb.id, "two sessions");
        h.assertTrue(a.position().distanceTo(b.position()) > 200, "in rooms of their own");
        // B's inputs carrying A's session id do nothing to A's session.
        send(a, TrainingNet.READY, -1, -1, 0);
        TrainingSessions.advanceForTest(a, 2050);
        TrainingCheck first = sa.checks().get(0);
        TrainingSessions.advanceForTest(a, first.atMs());
        TrainingSessions.inputForTest(b, new TrainingNet.Input(sa.id, sa.attempt(), ++seq, TrainingNet.PRESS, first.gauge(), first.id(), first.atMs()));
        h.assertTrue(sa.evaluator().result(first.id()) < 0, "another player's packet never scores this session");
        // B never acknowledges its schedule: the attempt is void (nothing counted), with Retry/Leave.
        TrainingSessions.advanceForTest(b, 2050);
        TrainingSessions.advanceForTest(b, JJKConfig.get().training.syncTimeoutMs + 100);
        h.assertTrue(sb.state() == TrainingNet.SYNC_LOST, "an unsynchronised client gets the retry/leave path");
        TrainingSessions.leaveNow(a);
        TrainingSessions.leaveNow(b);
        h.assertTrue(!CursedRealms.inRealm(a) && !CursedRealms.inRealm(b), "both home");
        a.discard();
        b.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void everyPresetStartsItsOwnSceneAndCleansUp(GameTestHelper h) {
        TrainingSessions.setManualClockForTest(true);
        ServerPlayer p = survivor(h, 2, 2);
        for (CharacterStory story : CharacterStories.all()) {
            PersonalTrials.startNow(p, story, true);
            CursedRealms.finishPullForTest(p);
            TrainingSessions.Session s = TrainingSessions.of(p);
            h.assertTrue(s != null && s.story == story && CursedRealms.inRealm(p), story.kit() + ": its training starts");
            // Run the intro and a few seconds of each scene, then the failure and the victory payoffs.
            send(p, TrainingNet.READY, -1, -1, 0);
            for (int i = 0; i < 60; i++) TrainingSessions.advanceForTest(p, 100);
            TrainingSessions.failNow(p);
            for (int i = 0; i < 20; i++) TrainingSessions.advanceForTest(p, 50);
            send(p, TrainingNet.RETRY, -1, -1, 0);
            h.assertTrue(PersonalTrials.completeNow(p), story.kit() + ": passes");
            for (int i = 0; i < 70; i++) TrainingSessions.advanceForTest(p, 50);
            TrainingSessions.leaveNow(p);
            h.assertTrue(!TrainingSessions.inTraining(p) && !CursedRealms.inRealm(p), story.kit() + ": back home");
            h.assertTrue(!p.isPassenger(), story.kit() + ": not left sitting on anything");
            h.assertTrue(!KitOwnership.get(server(h)).isClaimed(story.kit()) || KitOwnership.get(server(h)).owner(story.kit()).uuid() != p.getUUID(),
                    story.kit() + ": a test session claims nothing");
        }
        p.discard();
        h.succeed();
    }

    // --- The rooms ---

    @GameTest(environment = ENV)
    public void everyRoomPlanLoadsFitsItsArenaAndLeavesOutTheRelief(GameTestHelper h) {
        for (String kit : KITS) {
            TrialRooms.Plan plan = TrialRooms.plan(kit);
            h.assertTrue(plan != null, kit + ": its room plan loads");
            int[] b = plan.bounds();
            // Everything the room places or draws lies in the box the realm clears when the arena closes.
            h.assertTrue(b[0] >= -CursedRealms.RADIUS && b[3] <= CursedRealms.RADIUS + 1 && b[2] >= -CursedRealms.RADIUS && b[5] <= CursedRealms.RADIUS + 1
                    && b[1] >= -20 && b[4] <= CursedRealms.HEIGHT, kit + ": the room fits its arena " + java.util.Arrays.toString(b));
            var blocks = plan.blocks();
            Vec3 at = plan.marker("player");
            BlockPos feet = BlockPos.containing(at.x, at.y + 0.01, at.z);
            // Ground under the spot (a full block, or the firing platform's slab under Ryu), and headroom above it.
            BlockPos ground = at.y - Math.floor(at.y) >= 0.49 ? feet : feet.below();
            if (!kit.equals("yuji")) h.assertTrue(blocks.containsKey(ground), kit + ": the participant stands on the room's floor");
            for (int dy = 1; dy <= 2; dy++) h.assertTrue(!blocks.containsKey(ground.above(dy)), kit + ": headroom at the participant's spot");
            h.assertTrue(!plan.props().isEmpty(), kit + ": the room has its props");
            // Closed: a ceiling over the participant and a wall on every side (no void anywhere in view).
            boolean roofed = false;
            for (int y = feet.getY() + 2; y < b[4]; y++) roofed |= blocks.containsKey(new BlockPos(feet.getX(), y, feet.getZ()));
            h.assertTrue(roofed, kit + ": a ceiling over the participant");
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                boolean wall = false;
                for (int k = 1; k < 45 && !wall; k++) wall = blocks.containsKey(new BlockPos(feet.getX() + d[0] * k, feet.getY() + 2, feet.getZ() + d[1] * k));
                h.assertTrue(wall, kit + ": walled in toward " + d[0] + "," + d[1]);
            }
            for (TrialRooms.Prop pr : plan.props()) {
                h.assertTrue(TrainingTests.class.getClassLoader().getResource("assets/jjk/models/bb/" + pr.model() + ".bbmodel") != null,
                        kit + ": prop model " + pr.model() + " is shipped");
            }
        }
        // The pack's crude Rika sculpture is never imported: the shrine shows the mod's own Rika.
        String yuta = read("assets/jjk/models/bb/trial_yuta.bbmodel");
        h.assertTrue(!yuta.contains("relief") && !yuta.contains("guardian"), "no Rika guardian relief in the shrine");
        // A clear shot down the gallery: nothing the plan places between the firing platform and the target.
        TrialRooms.Plan ryu = TrialRooms.plan("ryu");
        Vec3 from = ryu.marker("player").add(0, 1.62, 0), to = ryu.marker("target");
        var rb = ryu.blocks();
        for (int i = 1; i < 60; i++) {
            BlockPos q = BlockPos.containing(from.lerp(to, i / 60.0));
            h.assertTrue(!rb.containsKey(q) || q.getZ() >= (int) Math.floor(to.z) - 1, "ryu: the line to the target is clear at " + q);
        }
        h.succeed();
    }

    private static String read(String path) {
        try (var in = TrainingTests.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    @GameTest(maxTicks = 200, environment = ENV)
    public void everyRoomIsBuiltDressedOnceAndClearedAfter(GameTestHelper h) {
        TrainingSessions.setManualClockForTest(true);
        ServerPlayer p = survivor(h, 2, 2);
        for (CharacterStory story : CharacterStories.all()) {
            String kit = story.kit();
            TrialRooms.Plan plan = TrialRooms.plan(kit);
            for (int visit = 0; visit < 2; visit++) {
                PersonalTrials.startNow(p, story, true);
                CursedRealms.finishPullForTest(p);
                var arena = CursedRealms.arenaHere(p);
                h.assertTrue(arena != null, kit + ": in its room");
                ServerLevel realm = (ServerLevel) p.level();
                BlockPos o = arena.origin;
                // Its blocks are standing.
                var blocks = plan.blocks();
                int standing = 0;
                for (var e : blocks.entrySet()) if (realm.getBlockState(o.offset(e.getKey())).getBlock() == e.getValue().getBlock()) standing++;
                h.assertTrue(standing >= blocks.size() * 0.95, kit + ": its blocks are placed (" + standing + "/" + blocks.size() + ")");
                // It is where the room puts it, with room to stand.
                Vec3 spot = new Vec3(o.getX(), o.getY(), o.getZ()).add(plan.marker("player"));
                h.assertTrue(p.position().distanceTo(spot) < 1.2, kit + ": at its spot (" + p.position() + " vs " + spot + ")");
                h.assertTrue(realm.noCollision(p, p.getBoundingBox()), kit + ": not inside anything");
                // The room can't be broken down: not by a technique, a player breaking it, or an explosion.
                BlockPos wall = null;
                for (var e : blocks.entrySet()) {
                    if (e.getKey().getY() == 2 && (wall == null || e.getKey().getZ() > wall.getZ())) wall = e.getKey();
                }
                BlockPos w = o.offset(wall);
                var before = realm.getBlockState(w);
                h.assertTrue(dev.rick.jjk.progression.investigation.ProtectedPlaces.isProtected(realm, w), kit + ": its walls are protected");
                h.assertTrue(!dev.rick.jjk.util.Destruction.destroy(realm, w, 100f, p), kit + ": a technique can't break its wall");
                boolean broke = p.gameMode.destroyBlock(w);
                h.assertTrue(!broke && realm.getBlockState(w) == before, kit + ": a player can't break its wall (mode " + p.gameMode.getGameModeForPlayer()
                        + ", broke " + broke + ", now " + realm.getBlockState(w) + ")");
                realm.explode(null, w.getX() + 0.5, w.getY() + 0.5, w.getZ() - 1.5, 4f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                h.assertTrue(realm.getBlockState(w) == before, kit + ": its wall still stands");
                // A retry rebuilds nothing twice: the same props, one Rika at most.
                send(p, TrainingNet.READY, -1, -1, 0);
                for (int i = 0; i < 30; i++) TrainingSessions.advanceForTest(p, 100);
                TrainingSessions.failNow(p);
                for (int i = 0; i < 10; i++) TrainingSessions.advanceForTest(p, 50);
                send(p, TrainingNet.RETRY, -1, -1, 0);
                for (int i = 0; i < 10; i++) TrainingSessions.advanceForTest(p, 100);
                AABB box = new AABB(o).inflate(CursedRealms.RADIUS, CursedRealms.HEIGHT, CursedRealms.RADIUS);
                var props = realm.getEntitiesOfClass(TrialPropEntity.class, box, e -> true);
                h.assertTrue(props.size() == plan.props().size(), kit + ": its props once each (" + props.size() + ")");
                for (TrialRooms.Prop pr : plan.props()) {
                    h.assertTrue(props.stream().filter(e -> e.model().equals(pr.model())).count() == 1, kit + ": prop " + pr.model());
                }
                var rikas = realm.getEntitiesOfClass(RikaEntity.class, box, e -> true);
                if (kit.equals("yuta")) {
                    h.assertTrue(rikas.size() == 1, "yuta: exactly one Rika (" + rikas.size() + ")");
                    RikaEntity r = rikas.get(0);
                    Vec3 dais = new Vec3(o.getX(), o.getY(), o.getZ()).add(plan.marker("rika"));
                    h.assertTrue(r.position().distanceTo(dais) < 2.5 && r.has(RikaEntity.FULL), "yuta: Rika on her dais at the shrine's rear");
                    h.assertTrue(r.owner() == p, "yuta: she is the participant's, for the trial only");
                } else {
                    h.assertTrue(rikas.isEmpty(), kit + ": no Rika");
                }
                TrainingSessions.leaveNow(p);
                h.assertTrue(!CursedRealms.inRealm(p), kit + ": back home");
                h.assertTrue(realm.getEntitiesOfClass(TrialPropEntity.class, box, e -> true).isEmpty(), kit + ": no props left behind");
                h.assertTrue(realm.getEntitiesOfClass(RikaEntity.class, box, e -> true).isEmpty(), kit + ": no Rika left behind");
                int left = 0;
                for (var e : blocks.keySet()) if (!realm.getBlockState(o.offset(e)).isAir()) left++;
                h.assertTrue(left == 0, kit + ": the room is cleared (" + left + " blocks left)");
            }
        }
        p.discard();
        h.succeed();
    }
}
