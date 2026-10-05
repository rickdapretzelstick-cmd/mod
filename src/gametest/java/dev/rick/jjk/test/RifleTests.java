package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.progression.ProgressionItems;
import dev.rick.jjk.progression.mastery.Mastery;
import dev.rick.jjk.progression.tool.CursedTools;
import dev.rick.jjk.progression.tool.rifle.RifleBeam;
import dev.rick.jjk.progression.tool.rifle.RifleClaims;
import dev.rick.jjk.progression.tool.rifle.RifleRules;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Cursed Rifle on the server: a normal shot costs its reserve, hits what the aim is on, and can't be fired again
 * before its interval; an empty reserve refuses; the beam needs Unfolding Array, then runs deploy, charge, ready, fire,
 * cooldown, retract on the server's clock; letting go early, switching items, running dry and dying all end it cleanly;
 * the Mastery upgrades move the numbers they say, within their caps, and Maximum Output is the full-width beam. Claims:
 * a re-issued rifle retires the old one.
 */
public class RifleTests {
    private static final String ENV = "jjk-test:rifle";

    static void floor(GameTestHelper h) {
        JJKConfig.get().general.autoAssignGojo = false;
        JJKConfig.get().progression.enabled = true;
        JJKConfig.get().rifle.beamRange = 12;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /** A Survival player holding the rifle (Mastery governs them: the beam has to be learned). */
    static ServerPlayer shooter(GameTestHelper h, double x, double z, GameType mode) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        p.getAbilities().invulnerable = false;
        p.setPos(h.absoluteVec(new Vec3(x, 1, z)));
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ProgressionItems.CURSED_RIFLE));
        RifleServer.setEnergy(p, JJKConfig.get().rifle.capacity);
        return p;
    }

    static void face(LivingEntity e, Vec3 target) {
        Vec3 d = target.subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setXRot(pitch);
    }

    /** Use pressed, the way the game does it (the item starts being used if the rifle accepts). */
    static boolean press(ServerPlayer p) {
        p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
        return p.isUsingItem();
    }

    static void letGo(ServerPlayer p) {
        p.releaseUsingItem();
    }

    /** Buys the rifle's nodes up to (and including) {@code last} along the beam's path. */
    static void learn(ServerPlayer p, String... nodes) {
        String tree = CursedTools.CURSED_RIFLE.treeId();
        Mastery.award(p, tree, 5000);
        for (String n : nodes) Mastery.purchase(p, tree, n);
    }

    static final String[] TO_BEAM = {"steady_hands", "quick_bolt", "settle", "frugal_rounds", "heavy_rounds", "oiled_action", "unfolding_array"};
    static final String[] TO_MAX = {"steady_hands", "quick_bolt", "settle", "frugal_rounds", "heavy_rounds", "oiled_action", "unfolding_array",
            "lens_economy", "fast_focus", "sustained_fire", "harmonised_arms", "focused_lenses", "maximum_output"};

    static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        return d;
    }

    @GameTest(maxTicks = 100, environment = ENV)
    public void aShotCostsTheReserveHitsAndWaitsItsInterval(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        TrainingDummy d = dummy(h, 1.5, 6.5);
        face(p, d.getBoundingBox().getCenter());
        float hp = d.getHealth();
        float full = RifleServer.energy(p);
        h.assertTrue(press(p), "use raises the scope");
        h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.AIM, "aiming");
        h.runAfterDelay(JJKConfig.get().rifle.settleTicks + 6, () -> {
            face(p, d.getBoundingBox().getCenter());
            letGo(p);
            h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.IDLE, "fired");
            h.assertTrue(Math.abs(full - RifleServer.energy(p) - RifleRules.shotCost(p)) < 0.5f, "paid its cost: " + (full - RifleServer.energy(p)));
            h.assertTrue(d.getHealth() < hp, "the round hit what the scope was on (" + hp + " -> " + d.getHealth() + ")");
            h.assertTrue(p.getCooldowns().isOnCooldown(p.getMainHandItem()), "and the bolt has to cycle");
            float after = RifleServer.energy(p), hit = d.getHealth();
            press(p);
            letGo(p);
            h.assertTrue(Math.abs(RifleServer.energy(p) - after) < 0.5f && d.getHealth() == hit, "no second shot inside the interval");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 40, environment = ENV)
    public void anEmptyReserveRefusesAShot(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        TrainingDummy d = dummy(h, 1.5, 5.5);
        face(p, d.getBoundingBox().getCenter());
        RifleServer.setEnergy(p, 1f);
        float hp = d.getHealth();
        press(p);
        h.runAfterDelay(20, () -> {
            letGo(p);
            h.assertTrue(d.getHealth() == hp, "nothing fired");
            h.assertTrue(!p.getCooldowns().isOnCooldown(p.getMainHandItem()), "no interval started");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 40, environment = ENV)
    public void theBeamHasToBeLearned(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        p.setShiftKeyDown(true);
        press(p);
        h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.AIM, "without Unfolding Array, sneak-use is just the scope");
        letGo(p);
        learn(p, TO_BEAM);
        h.assertTrue(RifleRules.beamUnlocked(p) && !RifleRules.maximumOutput(p), "learned the array (not Maximum Output)");
        h.runAfterDelay(JJKConfig.get().rifle.shotInterval + 2, () -> {
            press(p);
            h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.DEPLOY, "now the arms deploy");
            h.assertTrue(RifleServer.beam(p) != null && Math.abs(RifleServer.beam(p).output() - JJKConfig.get().rifle.baseOutput) < 1e-3, "at the base output");
            letGo(p);
            h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.RETRACT && RifleServer.beam(p) == null, "let go early: cancelled, folding away");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 300, environment = ENV)
    public void theBeamRunsItsWholeSequenceOnTheServersClock(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        learn(p, TO_BEAM);
        p.setShiftKeyDown(true);
        face(p, h.absoluteVec(new Vec3(1.5, 1.5, 7.5)));
        JJKConfig.Rifle cfg = JJKConfig.get().rifle;
        h.assertTrue(press(p) && RifleServer.phase(p) == RifleServer.Phase.DEPLOY, "deploying");
        long start = h.getTick();
        float[] beforeFire = {-1};
        boolean[] released = new boolean[1], sawFire = new boolean[1], sawCooldown = new boolean[1], sawRetract = new boolean[1];
        long[] readyAt = {-1};
        h.onEachTick(() -> {
            RifleServer.Phase ph = RifleServer.phase(p);
            long t = h.getTick() - start;
            if (ph == RifleServer.Phase.READY && readyAt[0] < 0) readyAt[0] = t;
            if (ph == RifleServer.Phase.READY && !released[0] && t >= readyAt[0] + 5) {
                beforeFire[0] = RifleServer.energy(p);
                letGo(p);
                released[0] = true;
                h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.FIRE && RifleServer.beam(p) != null && RifleServer.beam(p).firing(), "fires on release");
                h.assertTrue(beforeFire[0] - RifleServer.energy(p) >= RifleRules.beamCost(p) - 0.5f, "paid the beam's cost");
            }
            if (ph == RifleServer.Phase.FIRE) sawFire[0] = true;
            if (ph == RifleServer.Phase.COOLDOWN) sawCooldown[0] = true;
            if (ph == RifleServer.Phase.RETRACT) sawRetract[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(readyAt[0] >= cfg.deployTicks + RifleRules.chargeTicks(p) - 1 && readyAt[0] <= cfg.deployTicks + RifleRules.chargeTicks(p) + 2,
                    "ready after deploy + charge (" + readyAt[0] + ")");
            h.assertTrue(sawFire[0] && sawCooldown[0] && sawRetract[0], "fired, cooled, retracted");
            h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.IDLE && RifleServer.beam(p) == null, "back at rest");
            h.assertTrue(!press(p) || RifleServer.phase(p) != RifleServer.Phase.DEPLOY, "the array is cooling: no second beam yet");
        });
    }

    @GameTest(maxTicks = 200, environment = ENV)
    public void switchingItemsMidBeamEndsIt(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        learn(p, TO_BEAM);
        p.setShiftKeyDown(true);
        face(p, h.absoluteVec(new Vec3(1.5, 1.5, 7.5)));
        RifleServer.forcePhaseForTest(p, RifleServer.Phase.READY);
        p.startUsingItem(InteractionHand.MAIN_HAND);
        letGo(p);
        RifleBeam b = RifleServer.beam(p);
        h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.FIRE && b != null && b.firing(), "firing");
        h.runAfterDelay(5, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            h.runAfterDelay(2, () -> {
                h.assertTrue(!b.firing(), "the beam stopped");
                h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.COOLDOWN || RifleServer.phase(p) == RifleServer.Phase.RETRACT, "folding away");
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 200, environment = ENV)
    public void runningDryWhileReadyCancels(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        learn(p, TO_BEAM);
        RifleServer.forcePhaseForTest(p, RifleServer.Phase.READY);
        p.startUsingItem(InteractionHand.MAIN_HAND);
        RifleServer.setEnergy(p, RifleRules.beamCost(p) + 0.1f);
        h.succeedWhen(() -> {
            h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.RETRACT || RifleServer.phase(p) == RifleServer.Phase.IDLE, "the reserve gave out: cancelled");
            h.assertTrue(RifleServer.beam(p) == null, "no beam left behind");
        });
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void dyingClearsTheRifle(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        learn(p, TO_BEAM);
        RifleServer.forcePhaseForTest(p, RifleServer.Phase.CHARGE);
        RifleBeam b = RifleServer.beam(p);
        p.hurtServer(h.getLevel(), p.damageSources().genericKill(), 10_000f);
        h.assertTrue(!p.isAlive(), "dead");
        h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.IDLE && RifleServer.beam(p) == null, "nothing of the beam runs on");
        h.assertTrue(b != null && !dev.rick.jjk.core.clash.BeamClashManager.threatening(b), "and nothing is threatened any more");
        h.succeed();
    }

    @GameTest(maxTicks = 20, environment = ENV)
    public void upgradesMoveTheirNumbersWithinTheirCaps(GameTestHelper h) {
        floor(h);
        JJKConfig.Rifle cfg = JJKConfig.get().rifle;
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        float sway = RifleRules.sway(p), cost = RifleRules.shotCost(p), dmg = RifleRules.shotDamage(p);
        int interval = RifleRules.shotInterval(p);
        h.assertTrue(Math.abs(sway - cfg.swayDegrees) < 1e-3 && interval == cfg.shotInterval, "untouched at first");
        learn(p, TO_MAX);
        h.assertTrue(RifleRules.sway(p) < sway && RifleRules.shotCost(p) < cost && RifleRules.shotDamage(p) > dmg && RifleRules.shotInterval(p) < interval,
                "every small upgrade did something");
        h.assertTrue(RifleRules.shotInterval(p) >= cfg.minShotInterval && RifleRules.shotCost(p) >= cfg.shotCost * cfg.minCostShare - 1e-3, "within the caps");
        h.assertTrue(RifleRules.maximumOutput(p) && RifleRules.output(p) == 1f, "Maximum Output: full output");
        h.assertTrue(Math.abs(RifleRules.half(1f) - JJKConfig.get().yuta.beamRadius) < 1e-6, "as wide as True Love Beam");
        h.assertTrue(RifleRules.half(cfg.baseOutput) < RifleRules.half(1f), "the first unlock is narrower");
        h.succeed();
    }

    @GameTest(maxTicks = 20, environment = ENV)
    public void aReissuedRifleRetiresTheOldOne(GameTestHelper h) {
        floor(h);
        ServerPlayer p = shooter(h, 1.5, 1.5, GameType.SURVIVAL);
        ItemStack first = RifleClaims.issue(p);
        p.getInventory().add(first);
        h.assertTrue(RifleClaims.carriesLive(p) && RifleClaims.inertReason(p, first) == null, "the issued rifle is live");
        ItemStack second = RifleClaims.issue(p);
        h.assertTrue(RifleClaims.inertReason(p, first) != null, "the old one goes cold");
        h.assertTrue(RifleClaims.inertReason(p, second) == null, "the new one works");
        h.assertTrue(RifleClaims.inertReason(p, new ItemStack(ProgressionItems.CURSED_RIFLE)) == null, "an unclaimed rifle (Creative, a give) always works");
        p.setItemInHand(InteractionHand.MAIN_HAND, first);
        h.assertTrue(!press(p), "and the cold one won't raise its scope");
        h.succeed();
    }
}
