package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.melee.MeleeSystem;
import dev.rick.jjk.progression.contest.ContestCounters;
import dev.rick.jjk.progression.contest.ContestPreset;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The two newer ways into a strength contest, through the same entry as a grab: a curse's direct blow caught on the guard
 * (a defensive contest, every time the rules allow it), and the counter (an attack press from the guard opens a short
 * window; a grapple-able blow inside it is caught with no damage, exactly once, ahead of the guard, and the contest starts
 * with a head start). Never from a projectile, an unblockable, a fly head or a protected player; a missed counter has a
 * short recovery in which a block stays a block; every contest leaves at least the shared grace behind it.
 */
public class ContestTriggerTests {
    private static ServerPlayer guarding(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        // Facing +X, where the curses stand.
        p.setYRot(-90);
        p.setYHeadRot(-90);
        p.setXRot(0);
        StrengthContests.setRandomGrabsForTest(false);
        ContestCounters.reset(p);
        raise(h, p);
        return p;
    }

    /** The guard up, long enough ago that a blow is a block (not a parry). */
    private static void raise(GameTestHelper h, ServerPlayer p) {
        Combat.state(p).resetGuard();
        Combat.state(p).startGuard(h.getLevel().getGameTime() - 40);
    }

    private static <T extends CommonCurseEntity> T curse(GameTestHelper h, EntityType<T> type, ServerPlayer target, double dx) {
        ServerLevel level = h.getLevel();
        T c = type.create(level, EntitySpawnReason.EVENT);
        c.setPos(target.getX() + dx, target.getY(), target.getZ());
        level.addFreshEntity(c);
        c.setTarget(target);
        // Still: only the blows a test lands.
        c.setNoAi(true);
        // In a fight with this player (so its blows reach them, perceived or not).
        dev.rick.jjk.progression.CurseAggro.markHostile(c, target);
        StrengthContests.forgetForTest(target, c);
        return c;
    }

    /** The curse's own direct blow (as its melee lands), with any extra tags. */
    private static HitResult blow(CommonCurseEntity c, ServerPlayer p, AttackTag... extra) {
        Hit.Builder b = Hit.builder(c, "test_blow").damage(4).tag(AttackTag.MELEE).origin(c.position().add(0, c.getBbHeight() * 0.6, 0));
        if (extra.length > 0) b.tag(extra);
        return HitResolver.resolve(b.noComboScaling().build(), p);
    }

    private static int seq = 10;

    /** Past the intro (its READY sent), then broken free: the contest ends as a win would. */
    private static void end(ServerPlayer p) {
        StrengthContests.Session s = StrengthContests.of(p);
        if (s == null) return;
        StrengthContests.input(p, new dev.rick.jjk.progression.training.TrainingNet.Input(s.id, 1, ++seq,
                dev.rick.jjk.progression.training.TrainingNet.READY, -1, -1, 0));
        StrengthContests.advanceForTest(p, JJKConfig.get().training.contestIntroMs + 1);
        StrengthContests.finishForTest(p, true);
        for (int i = 0; i < 60 && StrengthContests.active(p); i++) StrengthContests.advanceForTest(p, 50);
    }

    @GameTest(environment = "jjk-test:contest_i")
    public void aBlockedBlowBecomesADefensiveContestReliably(GameTestHelper h) {
        ServerPlayer p = guarding(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.4);
        CommonCurseEntity fly = curse(h, ModEntities.FLY_HEAD, p, 1.4);
        StrengthContests.setManualClockForTest(true);
        try {
            // Not from a projectile, a technique, an area: the block just stands.
            for (AttackTag t : new AttackTag[] {AttackTag.PROJECTILE, AttackTag.TECHNIQUE, AttackTag.AREA, AttackTag.EXPLOSION}) {
                raise(h, p);
                HitResult r = blow(hound, p, t);
                ContestCounters.flushForTest();
                h.assertTrue(!StrengthContests.active(p), t + ": a blocked " + t + " never starts a contest (" + r.outcome() + ")");
            }
            // An unblockable isn't a block at all.
            raise(h, p);
            HitResult u = blow(hound, p, AttackTag.UNBLOCKABLE);
            ContestCounters.flushForTest();
            h.assertTrue(u.outcome() != HitResult.Outcome.BLOCKED && !StrengthContests.active(p), "an unblockable lands and starts nothing");
            p.setHealth(p.getMaxHealth());
            // A fly head's blow, blocked: never.
            raise(h, p);
            HitResult f = blow(fly, p);
            ContestCounters.flushForTest();
            h.assertTrue(f.outcome() == HitResult.Outcome.BLOCKED && !StrengthContests.active(p), "a fly head never contests (" + f.outcome() + ")");
            // The hound's blow, blocked: a contest, decided by the rules (not chance), at the end of the tick.
            raise(h, p);
            float hp = p.getHealth();
            HitResult r = blow(hound, p);
            h.assertTrue(r.outcome() == HitResult.Outcome.BLOCKED && p.getHealth() == hp, "the block itself stands: " + r.outcome());
            h.assertTrue(!StrengthContests.active(p), "not inside the blow's own resolution");
            ContestCounters.flushForTest();
            StrengthContests.Session s = StrengthContests.of(p);
            h.assertTrue(s != null && s.trigger == StrengthContests.Trigger.DEFENSIVE, "a defensive contest began (" + StrengthContests.unavailable(p) + ")");
            h.assertTrue(s.meter() == ContestPreset.START, "from the usual start");
            h.assertTrue(s.preset == StrengthContests.presetFor(hound), "the same preset by grade as a grab");
            h.assertTrue(!Combat.isGuarding(p), "the guard gives way to the struggle");
            end(p);
            h.assertTrue(!StrengthContests.active(p), "over");
            h.assertTrue(StrengthContests.protectedTicksForTest(p) >= JJKConfig.get().training.contestGraceSeconds * 20L,
                    "at least the shared grace afterwards: " + StrengthContests.protectedTicksForTest(p));
            // Protected: the next block is just a block.
            StrengthContests.forgetForTest(p, null);
            StrengthContests.forgetForTest(p, hound);
            h.assertTrue(StrengthContests.protectedTicksForTest(p) == 0, "(cleared for the next check)");
            // Twice in a row the same: reliable.
            raise(h, p);
            blow(hound, p);
            ContestCounters.flushForTest();
            h.assertTrue(StrengthContests.active(p), "every eligible block, not by chance");
            end(p);
            raise(h, p);
            blow(hound, p);
            ContestCounters.flushForTest();
            h.assertTrue(!StrengthContests.active(p), "within the grace a block stays a block");
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        hound.discard();
        fly.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(environment = "jjk-test:contest_j")
    public void aCounterCatchesOneBlowWithAHeadStart(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = guarding(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.4);
        JJKConfig.Training cfg = JJKConfig.get().training;
        StrengthContests.setManualClockForTest(true);
        try {
            h.assertTrue(!ContestCounters.open(p), "no window until pressed");
            // The attack press from the guard (the melee input's path): a window, never a swing.
            MeleeSystem.light(p, Casters.get(p), 0, -1);
            h.assertTrue(ContestCounters.open(p), "the press opened the counter window");
            h.assertTrue(Combat.isGuarding(p), "the guard stays up");
            h.assertTrue(ContestCounters.cooldown(p) == cfg.counterCooldownTicks, "the cooldown runs from activation: " + ContestCounters.cooldown(p));
            h.assertTrue(!ContestCounters.attempt(p), "a second press inside it opens nothing");
            // A blow that can't be grappled (a projectile) passes on to the guard; the window stays open for the real one.
            HitResult proj = blow(hound, p, AttackTag.PROJECTILE);
            h.assertTrue(proj.outcome() == HitResult.Outcome.BLOCKED && ContestCounters.open(p) && !StrengthContests.active(p),
                    "a projectile is just blocked: " + proj.outcome());
            raise(h, p);
            float hp = p.getHealth();
            HitResult r = blow(hound, p);
            h.assertTrue(r.outcome() == HitResult.Outcome.NEGATED && p.getHealth() == hp, "caught: no damage (" + r.outcome() + ")");
            StrengthContests.Session s = StrengthContests.of(p);
            h.assertTrue(s != null && s.trigger == StrengthContests.Trigger.COUNTER, "a counter contest");
            h.assertTrue(s.meter() > ContestPreset.START && s.meter() < ContestPreset.MAX, "with a modest head start: " + s.meter());
            h.assertTrue(!ContestCounters.open(p), "the window is spent: exactly once");
            ContestCounters.flushForTest();
            h.assertTrue(StrengthContests.of(p) == s, "and the guard's own trigger didn't start a second one");
            // Another blow while locked changes nothing.
            blow(hound, p);
            ContestCounters.flushForTest();
            h.assertTrue(StrengthContests.of(p) == s && p.getHealth() == hp, "no second contest, and no damage while locked");
            end(p);
            StrengthContests.Session done = StrengthContests.of(p);
            h.assertTrue(done == null, "over");
            h.assertTrue(StrengthContests.protectedTicksForTest(p) >= cfg.contestGraceSeconds * 20L, "the shared grace after a counter too");
            h.assertTrue(hound.isStaggered(), "a win staggers it (no automatic Black Flash)");
            // Disabled counters: the press opens nothing.
            ContestCounters.reset(p);
            raise(h, p);
            cfg.counters = false;
            try {
                h.assertTrue(!ContestCounters.attempt(p), "counters off: no window");
            } finally {
                cfg.counters = true;
            }
            // Not guarding: an attack press is not a counter.
            ContestCounters.reset(p);
            Combat.state(p).stopGuard();
            h.assertTrue(!ContestCounters.attempt(p), "no guard, no counter");
            // A creative player is never contested: the window catches nothing and the guard blocks.
            p.setGameMode(GameType.CREATIVE);
            raise(h, p);
            StrengthContests.forgetForTest(p, hound);
            h.assertTrue(ContestCounters.attempt(p), "the window opens");
            HitResult c = blow(hound, p);
            h.assertTrue(!StrengthContests.active(p) && c.outcome() != HitResult.Outcome.NEGATED, "a contest that can't start leaves the normal guard: " + c.outcome());
            p.setGameMode(GameType.SURVIVAL);
        } finally {
            StrengthContests.setManualClockForTest(false);
        }
        h.assertTrue(server != null, "server");
        hound.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, environment = "jjk-test:contest_k")
    public void aMissedCounterRecoversAndALateBlowIsJustBlocked(GameTestHelper h) {
        ServerPlayer p = guarding(h);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.4);
        JJKConfig.Training cfg = JJKConfig.get().training;
        h.assertTrue(ContestCounters.attempt(p), "the window opens");
        int window = cfg.counterWindowTicks;
        h.runAfterDelay(window - 2, () -> h.assertTrue(ContestCounters.open(p), "still open just inside the window"));
        h.runAfterDelay(window + 1, () -> {
            h.assertTrue(!ContestCounters.open(p), "closed after " + window + " ticks");
            h.assertTrue(ContestCounters.recovering(p), "a miss: the short recovery");
            h.assertTrue(!ContestCounters.attempt(p), "and still on cooldown");
            // Too late for a counter, and in the recovery a block stays a block.
            raise(h, p);
            float hp = p.getHealth();
            HitResult r = blow(hound, p);
            ContestCounters.flushForTest();
            h.assertTrue(r.outcome() == HitResult.Outcome.BLOCKED && p.getHealth() == hp && !StrengthContests.active(p),
                    "late: just blocked, no contest (" + r.outcome() + ")");
        });
        h.runAfterDelay(window + cfg.counterMissRecoveryTicks + 2, () -> h.assertTrue(!ContestCounters.recovering(p), "the recovery is short"));
        h.runAfterDelay(cfg.counterCooldownTicks + 1, () -> {
            raise(h, p);
            h.assertTrue(ContestCounters.attempt(p), "ready again once the cooldown from activation has run");
            ContestCounters.reset(p);
            hound.discard();
            p.discard();
            h.succeed();
        });
    }
}
