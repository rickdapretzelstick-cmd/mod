package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.progression.contest.StrengthContests;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.finish.Finishers;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Perfect dodges, counters and finishers against curses: a curse's blow passing through a dash is a perfect dodge (one
 * per short while) that makes the next melee hit a counter (bonus damage, a stagger); a melee hit on an exposed curse
 * low on health finishes it with the character's own blow, and nothing else does.
 */
public class FinisherTests {
    private static ServerPlayer sorcerer(GameTestHelper h, String character) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        CharacterService.assign(p, Characters.get(character));
        StrengthContests.setRandomGrabsForTest(false);
        Finishers.forgetForTest(p);
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

    private static HitResult jab(ServerPlayer p, CommonCurseEntity c) {
        return HitResolver.resolve(Hit.builder(p, "test_jab").damage(2).tag(AttackTag.MELEE).build(), c);
    }

    @GameTest(environment = "jjk-test:finish_a")
    public void aPerfectDodgeOpensACounter(GameTestHelper h) {
        ServerPlayer p = sorcerer(h, "yuji");
        CommonCurseEntity hound = curse(h, ModEntities.GRAVE_HOUND, p, 1.6);
        h.assertTrue(!Finishers.counterReady(p), "no counter to begin with");
        // Its bite, through someone mid-dash.
        Statuses.apply(p, CombatStatus.EVADING, 6);
        HitResult bite = HitResolver.resolve(Hit.builder(hound, "test_bite").damage(4).tag(AttackTag.MELEE).build(), p);
        h.assertTrue(bite.outcome() == HitResult.Outcome.WHIFF, "the bite passes through the dash: " + bite.outcome());
        h.assertTrue(Finishers.counterReady(p), "a perfect dodge: the counter is ready");
        Statuses.remove(p, CombatStatus.EVADING);
        float hp = hound.getHealth();
        HitResult r = jab(p, hound);
        h.assertTrue(r.outcome() == HitResult.Outcome.HIT, "the jab lands: " + r.outcome());
        h.assertTrue(hound.getHealth() < hp - r.damageDealt() - 1, "the counter hits harder (" + hp + " -> " + hound.getHealth() + ", the jab alone " + r.damageDealt() + ")");
        h.assertTrue(hound.isStaggered() && hound.isExposed(), "and staggers it, wide open");
        h.assertTrue(!Finishers.counterReady(p), "one counter per dodge");
        // Another dodge at once doesn't open another counter.
        Statuses.apply(p, CombatStatus.EVADING, 6);
        HitResolver.resolve(Hit.builder(hound, "test_bite").damage(4).tag(AttackTag.MELEE).build(), p);
        h.assertTrue(!Finishers.counterReady(p), "perfect dodges are kept apart");
        hound.discard();
        p.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:finish_b")
    public void anExposedCurseLowOnHealthIsFinished(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = sorcerer(h, "yuji");
        CommonCurseEntity healthy = curse(h, ModEntities.GRAVE_HOUND, p, -1.6);
        healthy.staggerBy(level, p);
        jab(p, healthy);
        h.assertTrue(!Finishers.finishing(p), "exposed but healthy: no finisher");
        healthy.discard();
        CommonCurseEntity standing = curse(h, ModEntities.SCHOOL_CRAWLER, p, -1.6);
        standing.setHealth(standing.getMaxHealth() * 0.15f);
        jab(p, standing);
        h.assertTrue(!Finishers.finishing(p), "low but not open: no finisher");
        standing.discard();
        CommonCurseEntity maw = curse(h, ModEntities.SCHOOL_MAW, p, 1.8);
        maw.staggerBy(level, p);
        maw.setHealth(maw.getMaxHealth() * 0.2f);
        h.assertTrue(Finishers.finishable(maw), "open and low: it can be finished");
        HitResult r = jab(p, maw);
        h.assertTrue(r.outcome() == HitResult.Outcome.HIT && Finishers.finishing(p), "the hit starts a finisher");
        h.assertTrue("yuji".equals(Finishers.characterOfFinishForTest(p)), "Yuji's own (Black Flash)");
        h.assertTrue(maw.isAlive() && Finishers.beingFinished(maw), "it's held until the blow lands");
        h.runAfterDelay(Finishers.FINISH_TICKS + 4, () -> {
            h.assertTrue(!maw.isAlive(), "the blow lands: it is finished");
            h.assertTrue(!Finishers.finishing(p), "and the finisher is over");
            p.discard();
            h.succeed();
        });
    }
}
