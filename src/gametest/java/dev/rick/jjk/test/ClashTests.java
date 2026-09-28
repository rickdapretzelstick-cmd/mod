package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.domain.clash.ClashJudgement;
import dev.rick.jjk.core.domain.clash.ClashManager;
import dev.rick.jjk.core.domain.clash.ClashParticipant;
import dev.rick.jjk.core.domain.clash.ClashSession;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.UnlimitedVoid;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Domain clashes are rhythm duels decided by timing, not by domain strength or who expanded first. */
public class ClashTests {
    private static JJKConfig testConfig;

    private static void applyConfig() {
        if (testConfig != null && JJKConfig.get() == testConfig) return;
        JJKConfig cfg = new JJKConfig();
        cfg.general.autoAssignGojo = false;
        cfg.domain.radius = 6;
        cfg.domain.duration = 200;
        cfg.domain.startup = 10;
        cfg.domain.physicalStructure = false;
        cfg.clash.countdownTicks = 10;
        cfg.clash.notes = 12;
        JJKConfig.set(cfg);
        testConfig = cfg;
    }

    private static TrainingDummy gojo(GameTestHelper h, double x, double z, float skill) {
        applyConfig();
        TrainingDummy g = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        g.setMode(TrainingDummy.Mode.STAND);
        CharacterService.assign(g, Characters.get(GojoCharacter.ID));
        ClashManager.setBotSkill(g, skill);
        return g;
    }

    private static void floor(GameTestHelper h) {
        applyConfig();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /** Two domains expanded next to each other: returns them (first expanded first). */
    private static DomainInstance[] clash(GameTestHelper h, LivingEntity a, LivingEntity b) {
        DomainInstance da = DomainManager.expand(a, UnlimitedVoid.INSTANCE);
        DomainInstance db = DomainManager.expand(b, UnlimitedVoid.INSTANCE);
        h.assertTrue(da != null && db != null, "both expanded");
        h.assertTrue(da.clash() != null && da.clash() == db.clash(), "overlapping domains start a clash session");
        return new DomainInstance[] {da, db};
    }

    /** Presses every note for {@code who} exactly on time (offset in ms added), as a skilled player would. */
    private static void playNotes(ClashSession s, LivingEntity who, double offsetMs, boolean[] pressed) {
        if (s.phase() != ClashSession.Phase.PLAYING) return;
        double clock = s.clock();
        for (int i = 0; i < s.chart().size(); i++) {
            double t = s.chart().times()[i] + offsetMs / 50.0;
            if (!pressed[i] && t <= clock) {
                pressed[i] = true;
                s.input(who, s.chart().lanes()[i], s.startTick() + t);
            }
        }
    }

    @GameTest(maxTicks = 400, environment = "jjk-test:clash_a")
    public void theBetterPlayerWinsNotTheFirstToExpand(GameTestHelper h) {
        floor(h);
        // The first to expand is the weaker player: activation order must not decide it.
        TrainingDummy first = gojo(h, 2, 4, 0.1f);
        TrainingDummy second = gojo(h, 6, 4, 0.95f);
        DomainInstance[] d = clash(h, first, second);
        h.startSequence()
                .thenIdle(1)
                .thenExecute(() -> h.assertTrue(Combat.has(first, CombatStatus.CLASHING) && Combat.has(second, CombatStatus.CLASHING),
                        "both duellists are locked in the clash"))
                .thenWaitUntil(() -> h.assertTrue(!d[0].isLive() || !d[1].isLive(), "the clash was decided"))
                .thenExecute(() -> {
                    h.assertTrue(d[1].isLive() && !d[0].isLive(), "the better player's domain survives");
                    h.assertValueEqual(d[0].endReason(), DomainInstance.EndReason.CLASH_LOST, "the loser lost the clash");
                    h.assertTrue(d[1].phase() == DomainInstance.Phase.ACTIVE, "the winner's domain takes over");
                    h.assertTrue(!Combat.has(second, CombatStatus.CLASHING), "released after the clash");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 400, environment = "jjk-test:clash_a")
    public void timingIsJudgedAndDrivesTheMeter(GameTestHelper h) {
        floor(h);
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        h.assertValueEqual(ClashSession.rate(0), ClashJudgement.PERFECT, "dead on");
        h.assertValueEqual(ClashSession.rate(cfg.perfectWindowMs + 1), ClashJudgement.GREAT, "just outside perfect");
        h.assertValueEqual(ClashSession.rate(-(cfg.greatWindowMs + 1)), ClashJudgement.GOOD, "a little early");
        h.assertValueEqual(ClashSession.rate(cfg.goodWindowMs + 30), ClashJudgement.MISS, "far off");
        // Two human-driven sorcerers: one plays perfectly, the other never presses.
        TrainingDummy player = gojo(h, 2, 4, -1f);
        TrainingDummy idle = gojo(h, 6, 4, -1f);
        DomainInstance[] d = clash(h, player, idle);
        ClashSession s = d[0].clash();
        boolean[] pressed = new boolean[s.chart().size()];
        float[] meterAfterFirst = {Float.NaN};
        h.onEachTick(() -> {
            playNotes(s, player, 0, pressed);
            if (Float.isNaN(meterAfterFirst[0]) && s.participant(player).perfects() == 1) meterAfterFirst[0] = s.meter();
        });
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(s.phase() == ClashSession.Phase.ENDED, "clash over"))
                .thenExecute(() -> {
                    ClashParticipant p = s.participant(player), q = s.participant(idle);
                    h.assertValueEqual(p.index, 0, "participants are in expansion order");
                    h.assertTrue(p.perfects() > 0 && p.misses() == 0, "on-time presses are PERFECT (" + p.perfects() + " perfect, " + p.misses() + " miss)");
                    h.assertTrue(q.misses() > 0 && q.score() == 0, "silence is all misses");
                    h.assertTrue(meterAfterFirst[0] > 0, "a perfect pushes the meter toward its player");
                    h.assertTrue(p.maxStreak() >= 3, "streak tracked");
                    h.assertTrue(p.accuracy() > 0.99f, "accuracy tracked (" + p.accuracy() + ")");
                    h.assertValueEqual(s.winner(), 0, "the player who played wins");
                    h.assertValueEqual(s.outcome(), ClashSession.Outcome.KNOCKOUT, "overwhelmed before the chart ended");
                    h.assertTrue(!d[1].isLive(), "the silent side's domain collapsed");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 600, environment = "jjk-test:clash_a")
    public void aDeadEvenClashGoesToSuddenDeath(GameTestHelper h) {
        floor(h);
        TrainingDummy a = gojo(h, 2, 4, -1f);
        TrainingDummy b = gojo(h, 6, 4, -1f);
        DomainInstance[] d = clash(h, a, b);
        ClashSession s = d[0].clash();
        boolean[][] pa = {new boolean[s.chart().size()]}, pb = {new boolean[s.chart().size()]};
        int[] seenRound = {0};
        h.onEachTick(() -> {
            if (s.round() != seenRound[0]) {
                seenRound[0] = s.round();
                pa[0] = new boolean[s.chart().size()];
                pb[0] = new boolean[s.chart().size()];
            }
            // Main sequence: identical play. Sudden death: B cracks under pressure.
            playNotes(s, a, 0, pa[0]);
            if (s.round() == 0) playNotes(s, b, 0, pb[0]);
        });
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(s.phase() == ClashSession.Phase.ENDED, "clash over"))
                .thenExecute(() -> {
                    h.assertTrue(s.round() >= 1, "a tie went to sudden death (round " + s.round() + ")");
                    h.assertValueEqual(s.winner(), 0, "sudden death decided it");
                    h.assertTrue(s.outcome() == ClashSession.Outcome.SUDDEN_DEATH || s.outcome() == ClashSession.Outcome.KNOCKOUT,
                            "decided by play, not a coin flip: " + s.outcome());
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 400, environment = "jjk-test:clash_a")
    public void strayPressesAndStaleTimesAreHandled(GameTestHelper h) {
        floor(h);
        TrainingDummy a = gojo(h, 2, 4, -1f);
        TrainingDummy b = gojo(h, 6, 4, -1f);
        DomainInstance[] d = clash(h, a, b);
        ClashSession s = d[0].clash();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(s.phase() == ClashSession.Phase.PLAYING, "playing"))
                .thenExecute(() -> {
                    // A press in a lane with nothing nearby: a ghost tap breaks the streak and costs a little ground.
                    int lane = s.chart().lanes()[s.chart().size() - 1];
                    float before = s.meter();
                    int ghostsBefore = s.participant(a).misses();
                    s.input(a, (lane + 1) % 4, h.getLevel().getGameTime());
                    h.assertTrue(s.meter() < before, "ghost tap costs ground (" + before + " -> " + s.meter() + ")");
                    h.assertTrue(s.participant(a).streak() == 0 && s.participant(a).misses() == ghostsBefore, "breaks the streak, not a counted miss");
                    // A time claimed far in the future is not trusted.
                    // A time claimed far in the future is not trusted: it is judged at arrival (nothing due yet, so a ghost tap).
                    ClashParticipant pb = s.participant(b);
                    int perfectsBefore = pb.perfects();
                    s.input(b, s.chart().lanes()[0], s.startTick() + s.chart().times()[0]);
                    h.assertTrue(pb.perfects() == perfectsBefore, "a claimed perfect time in the future is not accepted");
                })
                .thenWaitUntil(() -> h.assertTrue(s.phase() == ClashSession.Phase.ENDED, "clash still resolves"))
                .thenSucceed();
    }
}
