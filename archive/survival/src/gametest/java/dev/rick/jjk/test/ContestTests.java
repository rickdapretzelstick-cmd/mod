package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.progression.contest.ContestPreset;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.investigation.Clearance;
import dev.rick.jjk.progression.investigation.CursedRealms;
import dev.rick.jjk.progression.investigation.Incident;
import dev.rick.jjk.progression.investigation.IncidentTemplate;
import dev.rick.jjk.progression.investigation.InvestigationState;
import dev.rick.jjk.progression.investigation.Investigations;
import dev.rick.jjk.progression.investigation.Sites;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingNet;
import dev.rick.jjk.progression.training.TrainingSchedule;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Strength contests: who may grab (explicit, never a fly head), the telegraph and its server check (step out of reach and
 * it grabs nothing), the atomic reservation and the shared protection timer, a won contest (the curse staggered and
 * open), a lost one (the hit), no interference while locked, an attempt's guardian limited to two; the keys (one shown at
 * a time from W/A/S/D, never twice running: the right one pushes, a wrong one slips and locks, too fast or stale is
 * ignored) and every preset's balance simulated (quick hands win, idle or mashing loses, and every contest ends).
 */
public class ContestTests {
    private static ServerPlayer survivor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        StrengthContests.setRandomGrabsForTest(false);
        return p;
    }

    private static <T extends CommonCurseEntity> T curse(GameTestHelper h, EntityType<T> type, ServerPlayer target, double dx) {
        ServerLevel level = h.getLevel();
        T c = type.create(level, EntitySpawnReason.EVENT);
        c.setPos(target.getX() + dx, target.getY(), target.getZ());
        level.addFreshEntity(c);
        c.setTarget(target);
        StrengthContests.forgetForTest(target, c);
        return c;
    }

    private static int seq = 10;

    /** Past the intro on the manual clock (its READY sent): the contest's keys are live. */
    private static StrengthContests.Session start(ServerPlayer p) {
        StrengthContests.Session s = StrengthContests.of(p);
        StrengthContests.input(p, new TrainingNet.Input(s.id, 1, ++seq, TrainingNet.READY, -1, -1, 0));
        StrengthContests.advanceForTest(p, JJKConfig.get().training.contestIntroMs + 1);
        return s;
    }

    /** One key pressed now, stamped with the contest's clock: the one shown, or a wrong one. */
    private static void key(ServerPlayer p, boolean right) {
        StrengthContests.Session s = StrengthContests.of(p);
        int dir = right ? s.shown() : (s.shown() + 1) % StrengthContests.KEYS;
        StrengthContests.input(p, new TrainingNet.Input(s.id, 1, ++seq, TrainingNet.PRESS, dir, s.nextIndex(), (int) Math.round(s.clock())));
    }

    /** Runs a started contest to its end: the right key {@code perSecond} times a second (0: nothing pressed at all). */
    private static void play(ServerPlayer p, double perSecond) {
        StrengthContests.Session s = start(p);
        double step = perSecond > 0 ? 1000 / perSecond : 100;
        for (int i = 0; i < 3000 && s.state() == TrainingNet.ACTIVE; i++) {
            StrengthContests.advanceForTest(p, step);
            if (perSecond > 0 && s.state() == TrainingNet.ACTIVE) key(p, true);
        }
    }

    @GameTest(environment = "jjk-test:contest_a")
    public void everyPresetIsBalancedAndEveryContestEnds(GameTestHelper h) {
        JJKConfig.Training cfg = JJKConfig.get().training;
        ContestPreset g = ContestPreset.GUARDIAN;
        h.assertTrue(g.pressure() == 11 && g.push() == 5 && g.slip() == 10, "the guardian preset as specified");
        for (ContestPreset p : List.of(ContestPreset.GRADE_4, ContestPreset.GRADE_3, ContestPreset.GRADE_2, ContestPreset.GRADE_1, ContestPreset.GUARDIAN, ContestPreset.SPECIAL)) {
            var quick = StrengthContests.simulate(p, 4.5, n -> false, cfg, 60_000);
            h.assertTrue(quick.won() && quick.atMs() < 12_000, p.name() + ": quick hands (4.5 right keys a second) break free: " + quick);
            var idle = StrengthContests.simulate(p, 0, n -> false, cfg, 60_000);
            h.assertTrue(!idle.won() && idle.meter() == 0 && idle.atMs() < 15_000, p.name() + ": doing nothing is overpowered: " + idle);
            // Mashing every key: three wrong for every right one, each wrong one locking the keys.
            var mash = StrengthContests.simulate(p, 14, n -> n % 4 != 0, cfg, 60_000);
            h.assertTrue(!mash.won() && mash.meter() == 0, p.name() + ": mashing loses: " + mash);
            // However fast or slow, it always ends (the grip tightens): someone wins.
            for (double rate = 0; rate <= 7; rate += 0.5) {
                var r = StrengthContests.simulate(p, rate, n -> n % 9 == 0, cfg, 120_000);
                h.assertTrue(r.won() ? r.meter() >= ContestPreset.MAX : r.meter() == 0, p.name() + " at " + rate + "/s: it ends (" + r + ")");
            }
        }
        for (ContestPreset p : List.of(ContestPreset.GUARDIAN, ContestPreset.SPECIAL)) {
            h.assertTrue(!StrengthContests.simulate(p, 3, n -> false, cfg, 120_000).won(), p.name() + ": three a second never beats it");
        }
        for (ContestPreset p : List.of(ContestPreset.GRADE_4, ContestPreset.GRADE_3)) {
            h.assertTrue(StrengthContests.simulate(p, 2, n -> false, cfg, 60_000).won(), p.name() + ": two a second is enough for a weak curse");
        }
        h.succeed();
    }

    @GameTest(environment = "jjk-test:contest_h")
    public void oneKeyAtATimeRightPushesWrongSlipsAndLocks(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.5);
        JJKConfig.Training cfg = JJKConfig.get().training;
        StrengthContests.setManualClockForTest(true);
        try {
            h.assertTrue(StrengthContests.begin(server, hound, p), "grabbed (" + StrengthContests.unavailable(p) + ")");
            StrengthContests.Session s = start(p);
            h.assertTrue(s.state() == TrainingNet.ACTIVE && s.nextIndex() == 0, "live, the first key shown");
            int[] order = StrengthContests.order(1234L);
            h.assertTrue(java.util.Arrays.equals(order, StrengthContests.order(1234L)), "the same seed gives the same order (both sides build it)");
            for (int i = 1; i < order.length; i++) h.assertTrue(order[i] != order[i - 1] && order[i] >= 0 && order[i] < 4, "never the same key twice running");
            double m = s.meter();
            key(p, true);
            h.assertTrue(s.nextIndex() == 1 && Math.abs(s.meter() - (m + s.preset.push())) < 1e-6, "the right key pushes and shows the next: " + s.meter());
            key(p, true);
            h.assertTrue(s.nextIndex() == 1, "a second key at once is too fast: ignored");
            StrengthContests.advanceForTest(p, 100);
            m = s.meter();
            key(p, false);
            h.assertTrue(s.nextIndex() == 1 && Math.abs(s.meter() - (m - s.preset.slip())) < 1e-6, "the wrong key slips it back, the same key still shown: " + s.meter());
            StrengthContests.advanceForTest(p, cfg.contestLockoutMs / 2.0);
            key(p, true);
            h.assertTrue(s.nextIndex() == 1, "while the keys are locked, even the right one is ignored");
            StrengthContests.advanceForTest(p, cfg.contestLockoutMs);
            key(p, true);
            h.assertTrue(s.nextIndex() == 2, "the lock over, the right key counts again");
            StrengthContests.advanceForTest(p, 100);
            StrengthContests.input(p, new TrainingNet.Input(s.id, 1, ++seq, TrainingNet.PRESS, order[0], 0, (int) Math.round(s.clock())));
            h.assertTrue(s.nextIndex() == 2, "a key for a place already passed is ignored");
            // No time limit: it runs on until one side wins.
            StrengthContests.advanceForTest(p, 2_000);
            h.assertTrue(s.state() == TrainingNet.ACTIVE, "no clock runs out on it");
            play(p, 6);
            h.assertTrue(Boolean.TRUE.equals(s.won()), "quick right keys break free");
            for (int i = 0; i < 40 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        hound.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:contest_b")
    public void onlyListedCursesGrabAndAFlyHeadNever(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.5);
        CommonCurseEntity fly = curse(h, ModEntities.FLY_HEAD, p, -1.5);
        h.assertTrue(StrengthContests.eligible(hound), "a grave hound may grab");
        JJKConfig.get().training.contestKinds.add("fly_head");
        try {
            h.assertTrue(!StrengthContests.eligible(fly), "a fly head never grabs, even if listed");
        } finally {
            JJKConfig.get().training.contestKinds.remove("fly_head");
        }
        h.assertTrue(!StrengthContests.telegraph(fly, p), "so it can't start one");
        hound.discard();
        fly.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = "jjk-test:contest_c")
    public void aTelegraphedGrabCanBeEscaped(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.5);
        h.assertTrue(StrengthContests.telegraph(hound, p), "the grab is telegraphed");
        h.assertTrue(!StrengthContests.active(p), "nothing is decided yet");
        // Out of reach before it closes.
        p.teleportTo(p.getX() - 7, p.getY(), p.getZ());
        h.runAfterDelay(JJKConfig.get().training.contestTelegraphTicks + 3, () -> {
            h.assertTrue(!StrengthContests.active(p), "out of reach when it closed: no contest");
            h.assertTrue(!StrengthContests.telegraphing(hound), "the grab is over");
            h.assertTrue(StrengthContests.cannotGrab(hound) != null, "and it waits before trying again");
            hound.discard();
            p.discard();
            h.succeed();
        });
    }

    @GameTest(environment = "jjk-test:contest_d")
    public void winningStaggersItAndProtectionIsShared(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        ServerPlayer other = h.makeMockServerPlayerInLevel();
        other.setGameMode(GameType.SURVIVAL);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.5);
        CommonCurseEntity second = curse(h, ModEntities.SCHOOL_MAW, p, -1.5);
        StrengthContests.setManualClockForTest(true);
        try {
            h.assertTrue(StrengthContests.begin(server, hound, p), "grabbed (" + StrengthContests.unavailable(p) + ")");
            // Atomic: neither can be taken twice.
            h.assertTrue(!StrengthContests.begin(server, second, p), "the player is already locked");
            h.assertTrue(!StrengthContests.begin(server, hound, other), "and so is the curse");
            // No interference: nobody else can hurt either of them while locked.
            float hp = hound.getHealth();
            hound.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(other), 6);
            h.assertTrue(hound.getHealth() == hp, "a bystander can't hit the locked curse");
            float php = p.getHealth();
            p.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(second), 6);
            h.assertTrue(p.getHealth() == php, "another curse can't hit the locked player");
            play(p, 5);
            StrengthContests.Session s = StrengthContests.of(p);
            h.assertTrue(s != null && Boolean.TRUE.equals(s.won()), "quick right keys break free (meter " + (s == null ? -1 : s.meter()) + ")");
            h.assertTrue(hound.isStaggered() && hound.isExposed(), "it reels, wide open");
            for (int i = 0; i < 40 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
            h.assertTrue(!StrengthContests.active(p), "the contest is over");
            h.assertTrue(StrengthContests.protectedForTest(p), "the player is protected afterwards");
            h.assertTrue(!StrengthContests.telegraph(second, p), "from every curse: one timer shared by all");
            h.assertTrue(StrengthContests.cannotGrab(hound) != null, "and the curse that grabbed waits too");
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        hound.discard();
        second.discard();
        other.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:contest_g")
    public void aGrabIsAGrappleWithABeatOnEveryKey(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 2.4);
        StrengthContests.setManualClockForTest(true);
        try {
            h.assertTrue(StrengthContests.begin(server, hound, p), "grabbed (" + StrengthContests.unavailable(p) + ")");
            StrengthContests.Session s = StrengthContests.of(p);
            h.assertTrue("grave_hound".equals(s.kind), "the grapple is the hound's own: " + s.kind);
            double d = Math.sqrt(Math.pow(hound.getX() - p.getX(), 2) + Math.pow(hound.getZ() - p.getZ(), 2));
            h.assertTrue(Math.abs(d - 1.45) < 0.1, "staged at its reach, face to face: " + d);
            h.assertTrue(dev.rick.jjk.core.anim.Anim.isHolding(p), "the player is in the grip (a held pose)");
            play(p, 5);
            h.assertTrue(Boolean.TRUE.equals(s.won()), "quick right keys break free");
            h.assertTrue(s.beats() >= 3, "a beat for every key on the way: " + s.beats());
            h.assertTrue(!dev.rick.jjk.core.anim.Anim.isHolding(p), "broken free, the grip is let go (the throw)");
            for (int i = 0; i < 40 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
            // Overpowered by another: thrown off it, no grip left held either.
            StrengthContests.forgetForTest(p, null);
            CommonCurseEntity maw = curse(h, ModEntities.SCHOOL_MAW, p, -2.2);
            h.assertTrue(StrengthContests.begin(server, maw, p), "grabbed again (" + StrengthContests.unavailable(p) + ")");
            StrengthContests.Session t = StrengthContests.of(p);
            // Losing ground (nothing pressed for a while): it bears in on them, a little at a time.
            start(p);
            for (int i = 0; i < 24 && t.state() == TrainingNet.ACTIVE; i++) StrengthContests.advanceForTest(p, 50);
            double close = Math.sqrt(Math.pow(maw.getX() - p.getX(), 2) + Math.pow(maw.getZ() - p.getZ(), 2));
            h.assertTrue(t.meter() < ContestPreset.START && close < 1.6 - 0.015, "it bears in as they lose ground: " + close + " (meter " + t.meter() + ")");
            // Wrong keys, one after another as the locks allow: each a beat, until it overpowers them.
            for (int i = 0; i < 400 && t.state() == TrainingNet.ACTIVE; i++) {
                StrengthContests.advanceForTest(p, JJKConfig.get().training.contestLockoutMs + 10);
                if (t.state() == TrainingNet.ACTIVE) key(p, false);
            }
            h.assertTrue(Boolean.FALSE.equals(t.won()) && t.beats() >= 1, "wrong keys are overpowered, with a beat each: " + t.beats());
            h.assertTrue(!dev.rick.jjk.core.anim.Anim.isHolding(p), "and thrown off it");
            for (int i = 0; i < 40 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
            maw.discard();
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        hound.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:contest_e")
    public void losingCostsTheHitAndEndingEarlyCostsNothing(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.5);
        StrengthContests.setManualClockForTest(true);
        try {
            float before = p.getHealth();
            h.assertTrue(StrengthContests.begin(server, hound, p), "grabbed (" + StrengthContests.unavailable(p) + ")");
            play(p, 0);
            StrengthContests.Session s = StrengthContests.of(p);
            h.assertTrue(s != null && Boolean.FALSE.equals(s.won()), "nothing pressed: overpowered");
            h.assertTrue(p.getHealth() < before, "and it hurts (" + before + " -> " + p.getHealth() + ")");
            for (int i = 0; i < 40 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
            // The curse dying mid-contest ends it with no result.
            StrengthContests.forgetForTest(p, hound);
            CommonCurseEntity maw = curse(h, ModEntities.SCHOOL_MAW, p, -1.5);
            h.assertTrue(StrengthContests.begin(server, maw, p), "grabbed again");
            float hp = p.getHealth();
            maw.discard();
            StrengthContests.stepForTest(server);
            h.assertTrue(!StrengthContests.active(p) && p.getHealth() == hp, "the curse gone: over, no hit");
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        hound.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:contest_f")
    public void aGuardianGrabsAtMostTwicePerAttempt(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = survivor(h);
        Clearance.set(p, 1);
        ServerLevel level = h.getLevel();
        InvestigationState st = InvestigationState.get(server);
        InvestigationState.Village v = Investigations.village(level, h.absolutePos(new BlockPos(-60, 1, -60)));
        Incident in = Investigations.create(level, v, st, IncidentTemplate.get("guardian_chapel"), new Sites.Site(h.absolutePos(new BlockPos(700, 1, 40)), 1, 0),
                level.getGameTime(), RandomSource.create(77));
        Investigations.challengeForTest(in, p);
        CursedRealms.enter(p, in, st);
        h.runAfterDelay(30, () -> {
            CommonCurseEntity g = (CommonCurseEntity) Investigations.curseEntity(server, in.objectiveTarget());
            h.assertTrue(g != null && StrengthContests.isGuardian(g), "the guardian is its contract's");
            h.assertTrue(StrengthContests.presetFor(g) == ContestPreset.GUARDIAN, "it uses the guardian preset");
            h.assertTrue(StrengthContests.eligible(g), "a guardian may grab");
            StrengthContests.setManualClockForTest(true);
            try {
                for (int round = 0; round < 2; round++) {
                    StrengthContests.forgetForTest(p, g);
                    h.assertTrue(StrengthContests.cannotGrab(g) == null, "grab " + (round + 1) + " allowed");
                    h.assertTrue(StrengthContests.begin(server, g, p), "grab " + (round + 1));
                    play(p, 6);
                    for (int i = 0; i < 40 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
                }
                StrengthContests.forgetForTest(p, g);
                h.assertTrue(StrengthContests.cannotGrab(g) != null, "no third grab in the same attempt");
            } finally {
                StrengthContests.setManualClockForTest(false);
            }
            var arena = CursedRealms.arenaOf(st, in.id);
            if (arena != null) CursedRealms.close(server, st, arena, false);
            p.discard();
            h.succeed();
        });
    }
}
