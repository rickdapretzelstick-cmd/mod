package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.progression.CursedFingerAcquisition;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.PlayerProgression;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.registry.ModAttachments;
import dev.rick.jjk.yuji.YujiCharacter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Survival progression: Survival starts with no kit and the select screen can't hand one out, Creative picks are never
 * ownership and go when the player leaves Creative, each kit has one owner per world (kept across a reload, checked
 * against player data), and the Cursed Finger (the first eater becomes Yuji, a later eater dies, the owner eating
 * another is harmless). Kit ownership is world-wide, so these run as their own batch and each test uses its own kit
 * and releases it afterwards. Survival/Creative is set with a test hook (mock players are Creative).
 */
public class ProgressionGameTests {
    private static final String ENV = "jjk-test:progression";

    private static ServerPlayer survivor(GameTestHelper h) {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;
        cfg.progression.enabled = true;
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        TechniqueProgression.setSandboxForTesting(p, false);
        return p;
    }

    private static void done(ServerPlayer... players) {
        for (ServerPlayer p : players) {
            TechniqueProgression.setSandboxForTesting(p, null);
            p.discard();
        }
    }

    private static KitOwnership ownership(GameTestHelper h) {
        return KitOwnership.get(h.getLevel().getServer());
    }

    @GameTest(maxTicks = 40, environment = ENV)
    public void kitsHaveOneOwnerPerWorldAndSurviveAReload(GameTestHelper h) {
        ServerPlayer a = survivor(h);
        ServerPlayer b = survivor(h);
        ownership(h).release("gojo");
        h.assertValueEqual(TechniqueProgression.tryClaimKit(a, "gojo", "test"), KitOwnership.ClaimResult.CLAIMED, "first claim");
        h.assertValueEqual(TechniqueProgression.tryClaimKit(b, "gojo", "test"), KitOwnership.ClaimResult.TAKEN, "second player refused");
        h.assertValueEqual(TechniqueProgression.tryClaimKit(a, "gojo", "test"), KitOwnership.ClaimResult.ALREADY_OWNER, "owner again");
        h.assertTrue(Casters.get(a).character() == Characters.get("gojo"), "the Survival owner plays the kit at once");
        // A reload reads the registry back from disk.
        KitOwnership.unload();
        h.assertTrue(ownership(h).isOwner("gojo", a.getUUID()), "ownership kept across a reload");
        // Copied player data can't make a second owner: the world's record wins.
        b.setAttached(ModAttachments.PROGRESSION, PlayerProgression.EMPTY.withKit("gojo").encode());
        h.assertTrue(!TechniqueProgression.validate(b).owns("gojo"), "a forged kit is dropped on validation");
        // Administration: transfer and release.
        h.assertTrue(TechniqueProgression.transfer(h.getLevel().getServer(), "gojo", b), "transfer");
        h.assertTrue(ownership(h).isOwner("gojo", b.getUUID()) && !TechniqueProgression.validate(a).owns("gojo"), "transferred away from A");
        TechniqueProgression.release(h.getLevel().getServer(), "gojo");
        h.assertTrue(!ownership(h).isClaimed("gojo"), "released");
        done(a, b);
        h.succeed();
    }

    @GameTest(maxTicks = 40, environment = ENV)
    public void survivalStartsWithNothingAndCreativePicksAreTemporary(GameTestHelper h) {
        ServerPlayer p = survivor(h);
        ownership(h).release("ryu");
        TechniqueProgression.restore(p);
        h.assertTrue(Casters.get(p).character() == null, "a new Survival player has no kit");
        h.assertValueEqual(CharacterService.select(p, "ryu"), TechniqueProgression.NOT_AWAKENED, "the select screen can't hand one out");
        TechniqueProgression.setSandboxForTesting(p, true);
        h.runAfterDelay(2, () -> {
            h.assertTrue(CharacterService.select(p, "ryu") == null && Casters.get(p).character() == Characters.get("ryu"), "Creative picks freely");
            h.assertTrue(!ownership(h).isClaimed("ryu"), "a Creative pick claims nothing");
            TechniqueProgression.setSandboxForTesting(p, false);
            h.runAfterDelay(3, () -> {
                h.assertTrue(Casters.get(p).character() == null, "back in Survival the Creative pick is gone");
                done(p);
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void cursedFingerMakesTheFirstEaterYujiAndKillsTheNext(GameTestHelper h) {
        ServerPlayer a = survivor(h);
        ServerPlayer b = survivor(h);
        ownership(h).release(YujiCharacter.ID);
        h.assertValueEqual(TechniqueProgression.acquire(a, CursedFingerAcquisition.INSTANCE), KitOwnership.ClaimResult.CLAIMED, "A claims Yuji");
        h.assertValueEqual(TechniqueProgression.acquire(b, CursedFingerAcquisition.INSTANCE), KitOwnership.ClaimResult.TAKEN, "B finds Yuji taken");
        h.assertValueEqual(TechniqueProgression.acquire(a, CursedFingerAcquisition.INSTANCE), KitOwnership.ClaimResult.ALREADY_OWNER, "A again: their own case");
        h.runAfterDelay(3, () -> {
            h.assertTrue(a.isAlive() && Casters.get(a).character() == Characters.get(YujiCharacter.ID), "A survives as Yuji");
            h.assertTrue(TechniqueProgression.progression(a).counter(CursedFingerAcquisition.FINGERS_EATEN) == 2, "A's fingers are counted");
            h.assertTrue(!b.isAlive(), "B is consumed by the finger");
            h.assertTrue(ownership(h).isOwner(YujiCharacter.ID, a.getUUID()), "Yuji still belongs to A");
            ownership(h).release(YujiCharacter.ID);
            done(a, b);
            h.succeed();
        });
    }

    @GameTest(maxTicks = 20, environment = ENV)
    public void playerProgressionRoundTrips(GameTestHelper h) {
        PlayerProgression p = PlayerProgression.EMPTY.withKit("yuji").withKit("gojo").addCounter("cursed_fingers_eaten", 3);
        h.assertValueEqual(PlayerProgression.decode(p.encode()), p, "encode/decode");
        h.assertValueEqual(PlayerProgression.decode(""), PlayerProgression.EMPTY, "empty");
        h.assertValueEqual(PlayerProgression.decode(null), PlayerProgression.EMPTY, "missing");
        h.succeed();
    }
}
