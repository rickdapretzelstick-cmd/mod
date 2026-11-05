package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.curse.FlyHeadEntity;
import dev.rick.jjk.progression.curse.SchoolCrawlerEntity;
import dev.rick.jjk.progression.curse.SchoolMawEntity;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What makes the common curses worth fighting: wind-ups that read, misses that open them up (and pay more damage),
 * hits that freeze them for a beat, enough damage staggering them out of a move (but not an armoured one), turns taken
 * when several go at one target, healing only once nobody is near, and each kind's new moves (the Maw's slam, the
 * Crawler's feint, the Fly Head's spit and being swatted out of a dive).
 */
public class CurseFightTests {
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
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z, boolean glasses) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        if (glasses) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
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

    /** One cursed-tool hit from {@code p}. */
    private static float hit(GameTestHelper h, ServerPlayer p, CommonCurseEntity c, float amount) {
        DamageSource src = h.getLevel().damageSources().playerAttack(p);
        c.setInvulnerableTime(0);
        float before = c.getHealth();
        c.hurtServer(h.getLevel(), src, amount);
        return before - c.getHealth();
    }

    private static void moveTo(GameTestHelper h, ServerPlayer p, double x, double z) {
        Vec3 to = h.absoluteVec(new Vec3(x, 1, z));
        p.teleportTo(to.x, to.y, to.z);
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_i")
    public void aDodgedPounceGlowsFirstThenLeavesItExposed(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 3, 1.5, true);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 3, 7.5);
        c.forceWindupForTest(p, false);
        h.assertTrue(c.isWindingUp(), "the crouch before the spring reads as a wind-up");
        boolean[] dodged = new boolean[1];
        h.onEachTick(() -> {
            if (!dodged[0] && c.actionForTest() == 2) {
                // Sidestep as it springs.
                moveTo(h, p, 7.5, 1.5);
                dodged[0] = true;
            }
        });
        float[] normal = new float[1];
        h.succeedWhen(() -> {
            h.assertTrue(dodged[0] && c.isExposed(), "it skids past and lies exposed");
            h.assertTrue(landed(p, "crawler_pounce") == 0, "the pounce never touched them");
            float dealt = hit(h, p, c, 2f);
            h.assertTrue(Math.abs(dealt - 2f * CommonCurseEntity.EXPOSED_TAKEN) < 0.05f, "an exposed curse takes more: " + dealt);
            normal[0] = dealt;
        });
    }

    @GameTest(maxTicks = 60, environment = "jjk-test:curse_j")
    public void enoughDamageStaggersItOutOfAWindup(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 3, 1.5, true);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 3, 7);
        c.forceWindupForTest(p, false);
        float poise = c.poise();
        hit(h, p, c, poise * 0.5f);
        h.assertTrue(!c.isStaggered() && c.isWindingUp(), "half its poise: it keeps going");
        h.assertTrue(c.isFrozen(), "but the hit holds it for a beat (hitstop)");
        h.runAfterDelay(10, () -> {
            h.assertTrue(!c.isFrozen(), "only a beat");
            hit(h, p, c, poise * 0.6f);
            h.assertTrue(c.isStaggered() && c.action() == CommonCurseEntity.STAGGERED, "poise broken: staggered out of the pounce");
            h.assertTrue(c.isExposed(), "and open while it reels");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 160, environment = "jjk-test:curse_k")
    public void theMawsSlamCatchesWhatsOnTheFloorAndIsArmoured(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 2.5, true);
        SchoolMawEntity m = curse(h, ModEntities.SCHOOL_MAW, 4, 4.5);
        m.setGrade(dev.rick.jjk.progression.grade.CurseGrade.GRADE_3);
        m.setTarget(p);
        m.forceSlamForTest();
        h.assertTrue(m.isWindingUp(), "it rears up first");
        // Break its poise mid-slam: it doesn't flinch.
        hit(h, p, m, m.poise() + 6);
        h.assertTrue(!m.isStaggered(), "no flinch mid-slam");
        boolean[] slammed = new boolean[1];
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            if (landed(p, "maw_slam") >= 1) slammed[0] = true;
            h.assertTrue(slammed[0], "the slam catches them on the floor");
            h.assertTrue(m.isStaggered(), "and once the move is done, it reels");
        });
    }

    @GameTest(maxTicks = 260, environment = "jjk-test:curse_l")
    public void onlyTwoCommitToAnAttackOnOneTargetAtOnce(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 4, true);
        List<SchoolCrawlerEntity> pack = new ArrayList<>();
        pack.add(curse(h, ModEntities.SCHOOL_CRAWLER, 0.5, 0.5));
        pack.add(curse(h, ModEntities.SCHOOL_CRAWLER, 7.5, 0.5));
        pack.add(curse(h, ModEntities.SCHOOL_CRAWLER, 0.5, 7.5));
        pack.add(curse(h, ModEntities.SCHOOL_CRAWLER, 7.5, 7.5));
        int[] most = new int[1];
        h.onEachTick(() -> {
            p.setHealth(p.getMaxHealth());
            int n = 0;
            for (SchoolCrawlerEntity c : pack) if (c.committing()) n++;
            most[0] = Math.max(most[0], n);
        });
        h.runAfterDelay(240, () -> {
            h.assertTrue(most[0] >= 1, "they do attack");
            h.assertTrue(most[0] <= CommonCurseEntity.MAX_ATTACKERS, "never more than two at once: " + most[0]);
            h.succeed();
        });
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:curse_m")
    public void aFlyHeadHitMidDiveIsSwattedDown(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 1.5, true);
        FlyHeadEntity f = curse(h, ModEntities.FLY_HEAD, 4, 7);
        f.setPos(f.getX(), f.getY() + 2, f.getZ());
        f.forceDiveForTest(p);
        h.assertTrue(f.diving(), "diving");
        hit(h, p, f, 1f);
        h.assertTrue(f.grounded() && f.isExposed(), "knocked out of the air, open");
        h.assertTrue(!f.isNoGravity(), "it falls");
        h.succeedWhen(() -> h.assertTrue(f.onGround(), "and lands on the floor"));
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_n")
    public void aFlyHeadSpitsAGobThatTravels(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 6, 1.5, true);
        FlyHeadEntity f = curse(h, ModEntities.FLY_HEAD, 6, 7.5);
        f.forceSpitForTest(p);
        h.assertTrue(f.isWindingUp(), "it puffs up first");
        boolean[] flew = new boolean[1];
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            if (f.spitsInFlight() > 0) flew[0] = true;
            h.assertTrue(flew[0], "the gob is in flight a while (dodgeable)");
            h.assertTrue(landed(p, "fly_head_spit") >= 1, "and hits someone who stands in its way");
        });
    }

    @GameTest(maxTicks = 80, environment = "jjk-test:curse_o")
    public void aCrawlerFeintDartsAsideThenPounces(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 4, 1.5, true);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 4, 7.5);
        c.forceWindupForTest(p, true);
        Vec3 start = c.position();
        boolean[] feinted = new boolean[1];
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            if (c.actionForTest() == 6) feinted[0] = true;
            h.assertTrue(feinted[0], "the first crouch was a bluff");
            h.assertTrue(c.actionForTest() == 1 || c.actionForTest() == 2, "then it really pounces");
            Vec3 d = c.position().subtract(start);
            h.assertTrue(Math.abs(d.x) > 0.2, "from somewhere to the side: " + d);
        });
    }

    @GameTest(maxTicks = 340, environment = "jjk-test:curse_p")
    public void itOnlyMendsOnceTheFightIsWellOver(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 2, 2, false);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 6, 6);
        c.setHome(BlockPos.containing(h.absoluteVec(new Vec3(6, 1, 6))), 8);
        c.setGrade(dev.rick.jjk.progression.grade.CurseGrade.GRADE_4);
        c.setHealth(c.getMaxHealth() * 0.5f);
        float half = c.getHealth();
        h.runAfterDelay(CommonCurseEntity.CALM_TICKS - 20, () -> h.assertTrue(c.getHealth() == half, "no mending straight after a fight"));
        h.runAfterDelay(CommonCurseEntity.CALM_TICKS + 100, () -> {
            h.assertTrue(c.getHealth() > half, "it mends once things have been calm a while");
            h.succeed();
        });
    }
}
