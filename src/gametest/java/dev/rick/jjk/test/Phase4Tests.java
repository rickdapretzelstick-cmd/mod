package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.finish.Binds;
import dev.rick.jjk.progression.finish.Finishers;
import dev.rick.jjk.progression.finish.GuardianPhases;
import dev.rick.jjk.progression.finish.ParryChains;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4 of the fights: binds (a heavy into a curse's own wind-up locks the two; mashing wins it), parry chains (a
 * string of glinting blows; parry them all and its posture breaks open for a finisher), rescue (hitting a curse that is
 * reaching for someone else breaks off its grab) and a guardian's phase beats (held, untouchable, at two thirds and a
 * third of its health, the arena changing).
 */
public class Phase4Tests {
    private static ServerPlayer sorcerer(GameTestHelper h, double x, double z) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int bx = 0; bx < 10; bx++) for (int bz = 0; bz < 10; bz++) h.setBlock(bx, 0, bz, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        CharacterService.assign(p, Characters.get("yuji"));
        StrengthContests.setRandomGrabsForTest(false);
        ParryChains.setRandomChainsForTest(false);
        Finishers.forgetForTest(p);
        Binds.forgetForTest(p);
        return p;
    }

    private static <T extends CommonCurseEntity> T curse(GameTestHelper h, EntityType<T> type, ServerPlayer target, double dx) {
        ServerLevel level = h.getLevel();
        T c = type.create(level, EntitySpawnReason.EVENT);
        c.setPos(target.getX() + dx, target.getY(), target.getZ());
        level.addFreshEntity(c);
        c.setTarget(target);
        return c;
    }

    private static HitResult heavy(ServerPlayer p, CommonCurseEntity c) {
        return HitResolver.resolve(Hit.builder(p, "test_heavy").damage(1).tag(AttackTag.MELEE, AttackTag.HEAVY).build(), c);
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:phase4_a")
    public void aHeavyIntoItsWindupIsABindAndMashingWinsIt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = sorcerer(h, 2.5, 2.5);
        CommonCurseEntity maw = curse(h, ModEntities.SCHOOL_MAW, p, 1.8);
        // Not winding up: a heavy is only a heavy.
        heavy(p, maw);
        h.assertTrue(!Binds.binding(p), "a heavy on a curse that isn't winding up: no bind");
        // A light hit into its wind-up isn't a bind either.
        maw.markWoundUpForTest();
        HitResolver.resolve(Hit.builder(p, "test_jab").damage(1).tag(AttackTag.MELEE).build(), maw);
        h.assertTrue(!Binds.binding(p), "a light hit into its wind-up: no bind");
        maw.markWoundUpForTest();
        HitResult r = heavy(p, maw);
        h.assertTrue(r.outcome() == HitResult.Outcome.HIT && Binds.binding(p) && Binds.bound(maw), "the two heavies meet: a bind (" + r.outcome() + ")");
        h.assertTrue(Math.abs(Binds.meter(p) - Binds.START) < 1e-4, "it starts even");
        h.assertTrue(StrengthContests.unavailable(p) != null, "nobody grabs someone in a bind");
        int[] tapped = {0};
        h.onEachTick(() -> {
            if (Binds.binding(p) && tapped[0] < 30) {
                Binds.tapForTest(p);
                tapped[0]++;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!Binds.binding(p), "the bind breaks");
            h.assertTrue(maw.isStaggered() && maw.isExposed(), "mashing won it: the curse reels, wide open");
            h.assertTrue(tapped[0] < 20, "it broke as soon as it was driven home (" + tapped[0] + " taps)");
            // Right after one, another heavy into its wind-up doesn't bind again.
            maw.markWoundUpForTest();
            heavy(p, maw);
            h.assertTrue(!Binds.binding(p), "binds are kept apart");
            maw.discard();
            p.discard();
        });
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:phase4_b")
    public void aBindLeftAloneIsLostAndThrowsThePlayer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = sorcerer(h, 2.5, 2.5);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.6);
        hound.markWoundUpForTest();
        heavy(p, hound);
        h.assertTrue(Binds.binding(p), "a bind");
        float hp = p.getHealth();
        h.runAfterDelay(Binds.BIND_TICKS + 4, () -> {
            h.assertTrue(!Binds.binding(p), "it runs out");
            h.assertTrue(p.getHealth() < hp, "nobody pressed: the curse wins and it hurts (" + hp + " -> " + p.getHealth() + ")");
            h.assertTrue(Combat.state(p).has(CombatStatus.GUARD_BROKEN), "the player reels");
            h.assertTrue(!hound.isStaggered(), "the curse is still standing");
            hound.discard();
            p.discard();
            h.succeed();
        });
    }

    /** Guards every blow of the string under way, starting the guard just as each lands (a parry each time). */
    private static void parryEach(GameTestHelper h, ServerPlayer p, CommonCurseEntity c, int skip) {
        h.onEachTick(() -> {
            if (!c.chaining()) return;
            // Under half its health (set once it has its grade: grading sets its health).
            if (c.actionTicksForTest() == 2) c.setHealth(c.getMaxHealth() * 0.45f);
            ClashCommon.face(p, c.position().add(0, 1, 0));
            for (int i = 0; i < c.chainBlows(); i++) {
                if (c.actionTicksForTest() == c.chainBlowAt(i) - 1) {
                    Combat.state(p).stopGuard();
                    if (i != skip) Combat.state(p).startGuard(h.getLevel().getGameTime());
                }
            }
        });
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:phase4_c")
    public void parryingAWholeChainBreaksItsPostureForAFinisher(GameTestHelper h) {
        ServerPlayer p = sorcerer(h, 2.5, 2.5);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.6);
        h.assertTrue(ParryChains.start(hound, p, 3), "a string of three begins");
        h.assertTrue(hound.chaining() && hound.isWindingUp(), "it is stringing blows (and reads as a wind-up)");
        parryEach(h, p, hound, -1);
        h.succeedWhen(() -> {
            h.assertTrue(!hound.chaining(), "the string is over");
            h.assertTrue(hound.chainParried() == 3, "every blow parried: " + hound.chainParried());
            h.assertTrue(hound.isStaggered() && hound.isExposed(), "its posture breaks: it reels, wide open");
            h.assertTrue(Finishers.opened(hound) && Finishers.finishable(hound), "and under half its health it can be finished now");
            hound.discard();
            p.discard();
        });
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:phase4_d")
    public void missingOneParryLeavesTheChainUnbroken(GameTestHelper h) {
        ServerPlayer p = sorcerer(h, 2.5, 2.5);
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.6);
        h.assertTrue(ParryChains.start(hound, p, 3), "a string of three begins");
        parryEach(h, p, hound, 1);
        long[] started = {h.getLevel().getGameTime()};
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getGameTime() - started[0] > hound.chainBlowAt(2) + 12, "the string has run out");
            h.assertTrue(!hound.chaining() && hound.chainParried() == 2, "two of three parried: " + hound.chainParried());
            h.assertTrue(!Finishers.opened(hound) && !Finishers.finishable(hound), "its posture holds: no opening");
            hound.discard();
            p.discard();
        });
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:phase4_e")
    public void hittingACurseReachingForSomeoneElseBreaksItsGrab(GameTestHelper h) {
        ServerPlayer victim = sorcerer(h, 2.5, 2.5);
        ServerPlayer rescuer = h.makeMockServerPlayerInLevel();
        rescuer.setGameMode(GameType.SURVIVAL);
        rescuer.setPos(h.absoluteVec(new Vec3(2.5, 1, 4.5)));
        rescuer.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        CharacterService.assign(rescuer, Characters.get("yuji"));
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, victim, 1.6);
        h.assertTrue(StrengthContests.telegraph(hound, victim), "it reaches for the first player");
        // Its own target hitting it doesn't count as a rescue.
        HitResolver.resolve(Hit.builder(victim, "test_jab").damage(1).tag(AttackTag.MELEE).build(), hound);
        h.assertTrue(StrengthContests.telegraphing(hound), "still reaching (the one it reaches for can't rescue themselves)");
        HitResult r = HitResolver.resolve(Hit.builder(rescuer, "test_jab").damage(1).tag(AttackTag.MELEE).build(), hound);
        h.assertTrue(r.outcome() == HitResult.Outcome.HIT, "the rescuer's hit lands: " + r.outcome());
        h.assertTrue(!StrengthContests.telegraphing(hound), "the grab is broken off");
        h.assertTrue(hound.isStaggered(), "and it reels");
        h.runAfterDelay(JJKConfig.get().training.contestTelegraphTicks + 4, () -> {
            h.assertTrue(!StrengthContests.active(victim), "no contest ever starts");
            hound.discard();
            victim.discard();
            rescuer.discard();
            h.succeed();
        });
    }

    @GameTest(maxTicks = 260, padding = 30, environment = "jjk-test:phase4_f")
    public void aGuardianStopsForItsActsAndTheArenaChanges(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = sorcerer(h, 2.5, 2.5);
        CommonCurseEntity maw = curse(h, ModEntities.SCHOOL_MAW, p, 3);
        GuardianPhases.trackForTest(maw);
        // A floor like the guardian arena's, for the arena's changes.
        BlockPos origin = h.absolutePos(new BlockPos(30, 1, 5));
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            level.setBlock(origin.offset(x, -1, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
            level.setBlock(origin.offset(x, -2, z), Blocks.DEEPSLATE.defaultBlockState(), 3);
        }
        GuardianPhases.checkForTest(level, maw);
        h.assertTrue(GuardianPhases.passed(maw) == 0, "at full health: no beat");
        maw.setHealth(maw.getMaxHealth() * 0.6f);
        GuardianPhases.checkForTest(level, maw);
        h.assertTrue(GuardianPhases.passed(maw) == 1 && GuardianPhases.inBeat(maw) && maw.inBeat(), "past two thirds: the first beat");
        float hp = maw.getHealth();
        HitResolver.resolve(Hit.builder(p, "test_jab").damage(4).tag(AttackTag.MELEE).build(), maw);
        h.assertTrue(maw.getHealth() >= hp, "it can't be hurt during its beat");
        GuardianPhases.checkForTest(level, maw);
        h.assertTrue(GuardianPhases.passed(maw) == 1, "one beat at a time");
        GuardianPhases.beatInArenaForTest(level, maw, 1, origin);
        h.runAfterDelay(GuardianPhases.BEAT_TICKS + 6, () -> {
            h.assertTrue(!maw.inBeat() && !GuardianPhases.inBeat(maw), "the beat is over, the fight goes on");
            for (int y = 0; y < GuardianPhases.BEAT_TICKS / 5 - 1 && y <= 5; y++) {
                h.assertTrue(level.getBlockState(origin.offset(6, y, 6)).is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                        || level.getBlockState(origin.offset(6, y, 6)).is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
                        || level.getBlockState(origin.offset(6, y, 6)).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "a pillar rose (layer " + y + ")");
            }
            maw.setHealth(maw.getMaxHealth() * 0.25f);
            GuardianPhases.checkForTest(level, maw);
            h.assertTrue(GuardianPhases.passed(maw) == 2 && maw.inBeat(), "under a third: the last beat");
            GuardianPhases.beatInArenaForTest(level, maw, 2, origin);
            h.runAfterDelay(20, () -> {
                int sunk = 0;
                for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d >= 7.5 && d <= 10.5 && level.getBlockState(origin.offset(x, -1, z)).isAir()) sunk++;
                }
                h.assertTrue(sunk > 20, "the floor's outer ring broke and sank (" + sunk + ")");
                maw.setHealth(maw.getMaxHealth() * 0.1f);
                GuardianPhases.checkForTest(level, maw);
                h.assertTrue(GuardianPhases.passed(maw) == 2, "two acts, no more");
                maw.discard();
                p.discard();
                h.succeed();
            });
        });
    }
}
