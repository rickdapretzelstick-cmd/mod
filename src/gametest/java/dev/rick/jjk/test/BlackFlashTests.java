package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.fx.BlackFlash;
import dev.rick.jjk.hakari.HakariCombat;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Black Flash presentation decides from the resolved blow, never from hope: nothing hit or a blow stopped (blocked,
 * negated, refused) only fizzles; a blow that connects is the full Black Flash with its hit-stop (the target's launch held
 * a moment and then given back); one that kills is the lethal escalation, once per body, and an execute that is refused
 * shows no kill. Nothing here touches the blocks around it.
 */
public class BlackFlashTests {
    private static ServerPlayer fighter(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(x, 0, z, Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        p.setYRot(-90);
        p.setYHeadRot(-90);
        return p;
    }

    private static CommonCurseEntity hound(GameTestHelper h, ServerPlayer p) {
        ServerLevel level = h.getLevel();
        CommonCurseEntity c = ModEntities.GRAVE_HOUND.create(level, EntitySpawnReason.EVENT);
        c.setPos(p.getX() + 1.4, p.getY(), p.getZ());
        c.setNoAi(true);
        level.addFreshEntity(c);
        return c;
    }

    private static HitResult blow(ServerPlayer p, CommonCurseEntity c, float damage, AttackTag... tags) {
        Hit.Builder b = Hit.builder(p, "test_black_flash").damage(damage).tag(AttackTag.MELEE).origin(p.getEyePosition()).noComboScaling()
                .knockback(Knockback.directional(HakariCombat.flat(p), 2.0, 0.5));
        if (tags.length > 0) b.tag(tags);
        return HitResolver.resolve(b.build(), c);
    }

    @GameTest(maxTicks = 60, environment = "jjk-test:black_flash_a")
    public void itShowsWhatTheBlowActuallyDid(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = fighter(h);
        CommonCurseEntity c = hound(h, p);
        Vec3 at = c.getBoundingBox().getCenter();
        // Nothing there: a fizzle.
        h.assertTrue(BlackFlash.land(level, p, null, null, at, p.getLookAngle(), 1f) == BlackFlash.Shown.FIZZLE, "nothing hit: it fizzles");
        // Stopped on a guard: a fizzle, never the impact.
        Combat.state(c).startGuard(level.getGameTime() - 40);
        HitResult blocked = blow(p, c, 2);
        h.assertTrue(blocked.outcome() == HitResult.Outcome.BLOCKED, "the guard took it: " + blocked.outcome());
        h.assertTrue(BlackFlash.land(level, p, c, blocked, at, p.getLookAngle(), 1f) == BlackFlash.Shown.FIZZLE, "a blocked flash only sparks");
        Combat.state(c).stopGuard();
        Combat.state(c).resetGuard();
        // Connects: the full Black Flash and its hit-stop (the launch held, then given back).
        HitResult r = blow(p, c, 2, AttackTag.UNBLOCKABLE);
        h.assertTrue(r.connected() && c.isAlive(), "it lands and they live: " + r.outcome());
        Vec3 launch = c.getDeltaMovement();
        h.assertTrue(launch.lengthSqr() > 0.5, "the blow launched it: " + launch);
        h.assertTrue(BlackFlash.land(level, p, c, r, at, p.getLookAngle(), 1f) == BlackFlash.Shown.IMPACT, "a connecting flash is the full impact");
        h.assertTrue(BlackFlash.held(c) && c.getDeltaMovement().lengthSqr() < 1e-6, "held in the contact (hit-stop)");
        h.assertTrue(!BlackFlash.lethalShown(c), "not lethal");
        int stop = JJKConfig.get().general.blackFlashHitStop;
        h.runAfterDelay(stop + 2, () -> {
            h.assertTrue(!BlackFlash.held(c), "the hit-stop lets go");
            h.assertTrue(c.getDeltaMovement().lengthSqr() > 0.1 || !c.onGround(), "and the launch is given back: " + c.getDeltaMovement());
            // The ground it happened on is untouched.
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.assertBlockPresent(Blocks.STONE, x, 0, z);
            c.discard();
            p.discard();
            h.succeed();
        });
    }

    @GameTest(environment = "jjk-test:black_flash_b")
    public void theLethalOneIsDecidedByTheKillAndShownOnce(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = fighter(h);
        CommonCurseEntity c = hound(h, p);
        Vec3 at = c.getBoundingBox().getCenter();
        // A refused execute (a curse not there yet can't be targeted): no kill, so no lethal look.
        c.setDormant(true);
        HitResult refused = HakariCombat.execute(p, c, "test_black_flash", "hit_heavy");
        h.assertTrue(c.isAlive(), "it survived (" + refused.outcome() + ")");
        h.assertTrue(BlackFlash.land(level, p, c, refused, at, p.getLookAngle(), 1.8f) != BlackFlash.Shown.LETHAL, "no false execution");
        h.assertTrue(!BlackFlash.lethalShown(c), "nothing recorded");
        c.setDormant(false);
        // A blow that leaves them standing is not lethal, however big.
        HitResult big = blow(p, c, 1, AttackTag.UNBLOCKABLE);
        h.assertTrue(c.isAlive() && BlackFlash.land(level, p, c, big, at, p.getLookAngle(), 2.2f) == BlackFlash.Shown.IMPACT, "alive: the impact, not lethal");
        // The kill: lethal, decided by the resolved death.
        c.setHealth(1f);
        HitResult kill = blow(p, c, 50, AttackTag.UNBLOCKABLE);
        h.assertTrue(!c.isAlive() || c.isDeadOrDying(), "killed: " + kill.outcome());
        h.assertTrue(BlackFlash.land(level, p, c, kill, at, p.getLookAngle(), 1f) == BlackFlash.Shown.LETHAL, "the lethal escalation");
        h.assertTrue(BlackFlash.lethalShown(c), "recorded for this body");
        h.assertTrue(BlackFlash.land(level, p, c, kill, at, p.getLookAngle(), 1f) != BlackFlash.Shown.LETHAL, "never twice for the same death");
        c.discard();
        p.discard();
        h.succeed();
    }
}
