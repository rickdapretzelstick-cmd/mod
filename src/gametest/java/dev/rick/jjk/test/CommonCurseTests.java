package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.progression.KitOwnership;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.TechniqueProgression;
import dev.rick.jjk.progression.curse.CommonCurseEntity;
import dev.rick.jjk.progression.curse.FlyHeadEntity;
import dev.rick.jjk.progression.curse.SchoolCrawlerEntity;
import dev.rick.jjk.progression.curse.SchoolMawEntity;
import dev.rick.jjk.progression.grade.CurseGrade;
import dev.rick.jjk.progression.grade.CursedDamage;
import dev.rick.jjk.progression.mastery.CurseRewards;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.mastery.MasteryTree;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The common curses and what they pay: grades scale them; ordinary weapons don't hurt them and cursed tools do; each
 * fights in its own way (the fly head dives and bites, the crawler swipes up close, the maw's tongue reels you in to its
 * bite); and an exorcism pays its grade's Mastery once, split by contribution (no double pay for tool + technique),
 * less each time the same grade is farmed. One environment each (rewards and the config are shared).
 */
public class CommonCurseTests {
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
        JJKConfig.get().mastery.enabled = true;
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static ServerPlayer survivor(GameTestHelper h, double x, double z, boolean glasses) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        if (glasses) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ProgressionItems.CURSED_GLASSES));
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

    @GameTest(environment = "jjk-test:curse_a")
    public void gradesScaleTheSameKind(GameTestHelper h) {
        floor(h);
        SchoolMawEntity maw = curse(h, ModEntities.SCHOOL_MAW, 5, 5);
        maw.setGrade(CurseGrade.GRADE_3);
        float g3 = maw.getMaxHealth();
        maw.setGrade(CurseGrade.GRADE_4);
        float g4 = maw.getMaxHealth();
        h.assertTrue(Math.abs(g3 - 26 * CurseGrade.GRADE_3.health) < 0.01 && Math.abs(g4 - 26) < 0.01, "health follows the grade: " + g3 + " / " + g4);
        h.assertTrue(maw.getHealth() == maw.getMaxHealth(), "full health at its grade");
        h.assertTrue(maw.curseGrade() == CurseGrade.GRADE_4 && maw.requiresCursePerception(), "graded, and hidden");
        h.succeed();
    }

    @GameTest(maxTicks = 40, environment = "jjk-test:curse_b")
    public void ordinaryWeaponsCantHurtACurseButACursedToolCan(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 2, 2, true);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 3, 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        var mundane = h.getLevel().damageSources().playerAttack(p);
        h.assertTrue(CursedDamage.classify(mundane) == CursedDamage.Kind.MUNDANE, "a diamond sword is mundane");
        c.hurtServer(h.getLevel(), mundane, 10f);
        h.assertTrue(c.getHealth() == c.getMaxHealth(), "unharmed by it");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.SLAUGHTER_DEMON));
        var cursed = h.getLevel().damageSources().playerAttack(p);
        h.assertTrue(CursedDamage.classify(cursed) == CursedDamage.Kind.CURSED_TOOL, "a cursed tool");
        c.setInvulnerableTime(0);
        c.hurtServer(h.getLevel(), cursed, 5f);
        h.assertTrue(c.getHealth() < c.getMaxHealth(), "hurt by it");
        h.succeed();
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:curse_c")
    public void flyHeadsCircleThenDiveAndBite(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 6, 6, true);
        FlyHeadEntity f = curse(h, ModEntities.FLY_HEAD, 3, 3);
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            h.assertTrue(f.getTarget() == p, "it takes the perceiving player");
            h.assertTrue(landed(p, "fly_head_bite") >= 1, "and dives in to bite");
        });
    }

    @GameTest(maxTicks = 120, environment = "jjk-test:curse_d")
    public void curseIgnoresSomeoneWhoCantPerceiveIt(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 6, 6, false);
        FlyHeadEntity f = curse(h, ModEntities.FLY_HEAD, 4, 4);
        h.runAfterDelay(100, () -> {
            h.assertTrue(f.getTarget() == null && landed(p, "fly_head_bite") == 0, "an unseen curse doesn't start on someone who can't see it");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:curse_e")
    public void crawlerSwipesUpClose(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 6, 6, true);
        SchoolCrawlerEntity c = curse(h, ModEntities.SCHOOL_CRAWLER, 7.5, 6);
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            Vec3 at = h.absoluteVec(new Vec3(6, 1, 6));
            p.teleportTo(at.x, at.y, at.z);
            h.assertTrue(landed(p, "crawler_swipe") + landed(p, "crawler_pounce") >= 1, "it rakes or pounces on them");
        });
    }

    @GameTest(maxTicks = 300, environment = "jjk-test:curse_f")
    public void mawsTongueReelsThemInToItsBite(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 8, 3, true);
        SchoolMawEntity m = curse(h, ModEntities.SCHOOL_MAW, 2, 3);
        m.setYRot(-90f);
        h.succeedWhen(() -> {
            p.setHealth(p.getMaxHealth());
            h.assertTrue(landed(p, "maw_tongue") >= 1, "the tongue catches them at range");
            h.assertTrue(landed(p, "maw_bite") >= 1, "and the bite follows");
        });
    }

    @GameTest(environment = "jjk-test:curse_g")
    public void oneExorcismPaysOnceSplitByContribution(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 2, 2, true);
        KitOwnership own = KitOwnership.get(h.getLevel().getServer());
        if (own.owner("yuji") != null) own.release("yuji");
        TechniqueProgression.tryClaimKit(p, "yuji", "test");
        CharacterService.assign(p, Characters.get("yuji"));
        int[] paid = new int[1];
        CurseRewards.listener = pay -> {
            if (pay.player().equals(p.getUUID())) paid[0] += pay.amount();
        };
        try {
            SchoolMawEntity m = curse(h, ModEntities.SCHOOL_MAW, 4, 4);
            m.setGrade(CurseGrade.GRADE_3);
            // Equal damage from a tool and from their own technique.
            CurseRewards.bookForTest(m, p, "tool/slaughter_demon", 20);
            CurseRewards.bookForTest(m, p, MasteryTree.techniqueId("yuji"), 20);
            int before = Mastery.data(p).points("tool/slaughter_demon") + Mastery.data(p).points(MasteryTree.techniqueId("yuji"));
            m.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 10_000f);
            int after = Mastery.data(p).points("tool/slaughter_demon") + Mastery.data(p).points(MasteryTree.techniqueId("yuji"));
            // Split by contribution: the tool's half is paid; the technique's half isn't (technique trees are retired).
            h.assertTrue(Math.abs(paid[0] - CurseGrade.GRADE_3.mastery / 2) <= 1 && after - before == paid[0], "only the tool's half: " + paid[0]);
            h.assertTrue(Mastery.data(p).points(MasteryTree.techniqueId("yuji")) == 0, "nothing into the retired technique tree");
            // Fatigue: the same grade again pays less.
            paid[0] = 0;
            SchoolMawEntity m2 = curse(h, ModEntities.SCHOOL_MAW, 6, 6);
            m2.setGrade(CurseGrade.GRADE_3);
            CurseRewards.bookForTest(m2, p, "tool/slaughter_demon", 20);
            m2.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 10_000f);
            h.assertTrue(paid[0] > 0 && paid[0] < CurseGrade.GRADE_3.mastery, "the second pays less: " + paid[0]);
            h.assertTrue(Mastery.data(p).exorcised().getOrDefault("GRADE_3", 0) == 2, "both recorded");
        } finally {
            CurseRewards.listener = null;
        }
        h.succeed();
    }

    @GameTest(environment = "jjk-test:curse_h")
    public void aBorrowedTechniquePaysNothing(GameTestHelper h) {
        floor(h);
        ServerPlayer p = survivor(h, 2, 2, true);
        int[] paid = new int[1];
        CurseRewards.listener = pay -> {
            if (pay.player().equals(p.getUUID())) paid[0] += pay.amount();
        };
        try {
            FlyHeadEntity f = curse(h, ModEntities.FLY_HEAD, 4, 4);
            // Gojo's technique, but they don't own Gojo: only the tool's share is paid.
            CurseRewards.bookForTest(f, p, MasteryTree.techniqueId("gojo"), 3);
            CurseRewards.bookForTest(f, p, "tool/cursed_cleaver", 3);
            f.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 10_000f);
            h.assertTrue(paid[0] == CurseGrade.GRADE_4.mastery / 2, "only the tool's half: " + paid[0]);
            h.assertTrue(Mastery.data(p).points(MasteryTree.techniqueId("gojo")) == 0, "nothing in a tree that isn't theirs");
        } finally {
            CurseRewards.listener = null;
        }
        h.succeed();
    }
}
