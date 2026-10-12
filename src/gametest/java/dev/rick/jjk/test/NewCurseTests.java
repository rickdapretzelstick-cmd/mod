package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.BloatToadEntity;
import dev.rick.jjk.progression.curse.BroodMotherEntity;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.curse.FlyHeadEntity;
import dev.rick.jjk.progression.curse.GraveHoundEntity;
import dev.rick.jjk.progression.curse.SchoolCrawlerEntity;
import dev.rick.jjk.progression.curse.VeiledBrideEntity;
import dev.rick.jjk.progression.investigation.CurseKinds;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The four newer curses, each doing its one thing: the Grave Hound's leap (dodged, it sprawls exposed) and its howl
 * (it hurries every curse around, unless it is staggered out of it); the Veiled Bride's flicker (it reappears behind
 * you and grabs) and its wail (a cone that hurts, then it stands exposed); the Bloat Toad's belly flop (it lands on the
 * marked spot and ends up on its back, exposed); the Brood Mother's births (never more than three, then it is exposed;
 * they die with it) and its acid pools.
 */
public class NewCurseTests {
    private static final Map<UUID, List<HitResult>> HITS = new ConcurrentHashMap<>();

    static {
        CombatEvents.HIT_RESOLVED.add(r -> HITS.computeIfAbsent(r.target().getUUID(), k -> new CopyOnWriteArrayList<>()).add(r));
    }

    private static long landed(ServerPlayer p, String source) {
        return HITS.getOrDefault(p.getUUID(), List.of()).stream()
                .filter(r -> r.hit().source.equals(source) && r.outcome() != HitResult.Outcome.INVALID && r.outcome() != HitResult.Outcome.WHIFF).count();
    }

    private static void floor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);
        TestArea.keepTicking(h, -4, -4, 13, 13);
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
        HITS.remove(p.getUUID());
        return p;
    }

    private static <T extends CommonCurseEntity> T curse(GameTestHelper h, EntityType<T> type, double x, double z) {
        T c = type.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        Vec3 at = h.absoluteVec(new Vec3(x, 1, z));
        c.snapTo(at.x, at.y, at.z, 0f, 0f);
        h.getLevel().addFreshEntity(c);
        return c;
    }

    private static void moveTo(GameTestHelper h, ServerPlayer p, double x, double z) {
        Vec3 to = h.absoluteVec(new Vec3(x, 1, z));
        p.teleportTo(to.x, to.y, to.z);
    }

    @GameTest(environment = "jjk-test:curse_q")
    public void theNewKindsAreKnownToIncidents(GameTestHelper h) {
        for (String k : new String[] {"grave_hound", "veiled_bride", "bloat_toad", "brood_mother"}) {
            h.assertTrue(CurseKinds.exists(k), k + " can be named by an incident");
        }
        h.succeed();
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_r")
    public void aHoundsDodgedLeapLeavesItExposed(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 3, 1.5);
        GraveHoundEntity d = curse(h, ModEntities.GRAVE_HOUND, 3, 7.5);
        d.forceLeapForTest(p);
        h.assertTrue(d.isWindingUp(), "it crouches first");
        boolean[] dodged = new boolean[1];
        h.onEachTick(() -> {
            if (!dodged[0] && d.actionForTest() == 2) {
                moveTo(h, p, 7.5, 1.5);
                dodged[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(dodged[0] && d.isExposed(), "it lands sprawled and open");
            h.assertTrue(landed(p, "hound_leap") == 0, "and missed");
        });
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_s")
    public void aHowlHurriesTheCursesAround(GameTestHelper h) {
        floor(h);
        GraveHoundEntity d = curse(h, ModEntities.GRAVE_HOUND, 2, 2);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 6, 6);
        c.setRestForTest(500);
        d.forceHowlForTest();
        h.assertTrue(d.howling() && d.isWindingUp(), "it throws its head back (glowing)");
        h.succeedWhen(() -> h.assertTrue(c.restTicksForTest() == 0, "the curses around it attack again at once: " + c.restTicksForTest()
                + " (hound action " + d.actionForTest() + ", alive " + d.isAlive() + "/" + c.isAlive() + ", apart " + String.format("%.1f", d.distanceTo(c))
                + ", hound at " + d.position() + ", crawler at " + c.position() + ")"));
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_y")
    public void aHoundStaggeredOutOfItsHowlHurriesNobody(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 4);
        GraveHoundEntity d = curse(h, ModEntities.GRAVE_HOUND, 2, 2);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 6, 6);
        c.setRestForTest(2000);
        d.forceHowlForTest();
        d.setInvulnerableTime(0);
        d.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), d.poise() + 1);
        h.assertTrue(d.isStaggered() && !d.howling(), "staggered out of the howl");
        h.succeedWhen(() -> {
            h.assertTrue(!d.isStaggered(), "it reels, then recovers");
            h.assertTrue(c.restTicksForTest() > 1000, "and nobody was hurried: " + c.restTicksForTest());
        });
    }

    @GameTest(maxTicks = 100, environment = "jjk-test:curse_t")
    public void theBrideFlickersBehindYouAndGrabs(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 3);
        p.setYRot(0f);
        p.setYHeadRot(0f);
        VeiledBrideEntity b = curse(h, ModEntities.VEILED_BRIDE, 4, 8);
        b.forceFlickerForTest(p);
        h.assertTrue(b.isWindingUp(), "its veil lifts first (the tell)");
        boolean[] behind = new boolean[1];
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            p.setYRot(0f);
            // Facing +z (toward where it stood), "behind" is the -z side.
            if (b.getZ() < p.getZ() - 1) behind[0] = true;
            h.assertTrue(behind[0], "it reappears behind them");
            h.assertTrue(landed(p, "bride_grasp") >= 1, "and its arms close on them");
        });
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:curse_u")
    public void theBridesWailHitsTheConeThenItCatchesItsBreath(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 4.5);
        VeiledBrideEntity b = curse(h, ModEntities.VEILED_BRIDE, 4, 1);
        b.forceWailForTest(p);
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            h.assertTrue(landed(p, "bride_wail") >= 1, "the scream catches them in the cone");
            h.assertTrue(b.isExposed(), "and it stands there catching its breath, open");
        });
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_v")
    public void theToadFlopsOnTheMarkedSpotAndEndsUpOnItsBack(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 6.5);
        BloatToadEntity t = curse(h, ModEntities.BLOAT_TOAD, 4, 1.5);
        Vec3 spot = p.position();
        t.forceFlopForTest(spot);
        h.assertTrue(t.isWindingUp(), "it squats first, the ring marked");
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            h.assertTrue(landed(p, "toad_flop") >= 1, "it comes down on whoever stays in the ring");
            h.assertTrue(t.onBack() && t.isExposed(), "and lies on its back, open");
            h.assertTrue(t.position().distanceTo(spot) < 1.5, "right where it was marked: " + t.position().distanceTo(spot));
        });
    }

    @GameTest(maxTicks = 200, environment = "jjk-test:curse_w")
    public void theBroodMotherBirthsAFewAndTheyDieWithIt(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 7);
        BroodMotherEntity m = curse(h, ModEntities.BROOD_MOTHER, 4, 2);
        m.forceBirthForTest();
        h.assertTrue(m.isWindingUp(), "the sac swells first");
        h.runAfterDelay(BroodMotherEntity.BIRTH_TICKS + 3, () -> {
            int alive = m.broodAlive(h.getLevel());
            h.assertTrue(alive == 2, "two burst out: " + alive);
            h.assertTrue(m.isExposed(), "and it hangs empty, open");
            m.forceBirthForTest();
            h.runAfterDelay(BroodMotherEntity.BIRTH_TICKS + 3, () -> {
                h.assertTrue(m.broodAlive(h.getLevel()) == BroodMotherEntity.MAX_BROOD, "never more than three alive");
                m.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 10_000f);
                h.runAfterDelay(2, () -> {
                    AABB box = new AABB(h.absoluteVec(new Vec3(-8, -4, -8)), h.absoluteVec(new Vec3(16, 12, 16)));
                    long left = h.getLevel().getEntitiesOfClass(FlyHeadEntity.class, box, e -> e.isAlive()).size();
                    h.assertTrue(left == 0, "its brood died with it: " + left);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 160, environment = "jjk-test:curse_x")
    public void theBroodMothersAcidLeavesAPoolThatBurns(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 7);
        BroodMotherEntity m = curse(h, ModEntities.BROOD_MOTHER, 4, 1.5);
        m.forceSpitForTest(p.position());
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            h.assertTrue(m.poolsForTest() >= 1, "a pool where it landed");
            h.assertTrue(landed(p, "brood_acid") >= 2, "burning whoever stands in it, again and again");
        });
    }
}
