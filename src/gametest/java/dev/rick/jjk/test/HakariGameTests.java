package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.domain.clash.ClashManager;
import dev.rick.jjk.core.domain.structure.DomainStructure;
import dev.rick.jjk.entity.HakariDoorEntity;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.hakari.Gamble;
import dev.rick.jjk.hakari.HakariCharacter;
import dev.rick.jjk.hakari.HakariState;
import dev.rick.jjk.hakari.IdleDeathGamble;
import dev.rick.jjk.registry.ModBlocks;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hakari / Restless Gambler on the shared framework: his two kits, safe character switching, every base move, Door
 * Guard, the Idle Death Gamble state machine to a Jackpot (and to a miss), Jackpot's sustain, the Jackpot moves, Rhythm,
 * the domain counter into a clash, and the per-life jackpot bonus.
 */
public class HakariGameTests {
    private static JJKConfig testConfig;

    private static void applyConfig() {
        if (testConfig != null && JJKConfig.get() == testConfig) return;
        JJKConfig cfg = new JJKConfig();
        cfg.general.autoAssignGojo = false;
        // Shared settings match the other test classes (they share one config); only Hakari's own section differs.
        cfg.purple.range = 14;
        cfg.purple.maxBlocksDestroyed = 400;
        cfg.domain.radius = 6;
        cfg.domain.duration = 60;
        cfg.domain.startup = 10;
        cfg.clash.notes = 8;
        cfg.clash.countdownTicks = 10;
        cfg.red.range = 12;
        cfg.teleport.targetRange = 12;
        cfg.hakari.domainRadius = 6;
        cfg.hakari.domainStartup = 8;
        cfg.hakari.domainDuration = 500;
        cfg.hakari.domainFormationTicks = 20;
        cfg.hakari.visualMovesRequired = 2;
        cfg.hakari.riichiTicks = 40;
        cfg.hakari.missTicks = 10;
        // Outcomes are steered per test through each Hakari's own odds bonus; no random rainbow.
        cfg.hakari.rainbowChance = 0;
        JJKConfig.set(cfg);
        testConfig = cfg;
    }

    private static void floor(GameTestHelper h, int size) {
        applyConfig();
        for (int x = -2; x < size; x++) for (int z = -2; z < size; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        return d;
    }

    private static TrainingDummy hakari(GameTestHelper h, double x, double z, LivingEntity face) {
        TrainingDummy g = dummy(h, x, z);
        CharacterService.assign(g, Characters.get(HakariCharacter.ID));
        Casters.get(g).setNoCost(true);
        if (face != null) face(g, face.getBoundingBox().getCenter());
        return g;
    }

    private static void face(LivingEntity e, Vec3 target) {
        Vec3 d = target.subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setXRot(pitch);
    }

    private static void press(LivingEntity e, AbilitySlot slot) {
        Casters.get(e).input(slot, true, 0, 0, null);
    }

    private static void release(LivingEntity e, AbilitySlot slot) {
        Casters.get(e).input(slot, false, 0, 0, null);
    }

    // --- Kits and switching ---

    @GameTest(maxTicks = 5)
    public void hakariHasHisBaseAndJackpotKits(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = hakari(h, 1.5, 1.5, null);
        AbilityCaster c = Casters.get(g);
        String[] base = {"reserve_balls", "shutter_doors", "rough_energy", "fever_breaker", "door_guard", "idle_death_gamble"};
        String[] jackpot = {"lucky_volley", "lucky_rushdown", "overwhelming_luck", "energy_surge", "rhythm"};
        AbilitySlot[] slots = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4, AbilitySlot.SKILL_5, AbilitySlot.ULTIMATE};
        for (int i = 0; i < base.length; i++) h.assertValueEqual(c.ability(slots[i]).id, base[i], "base " + slots[i]);
        c.enterAwakening();
        for (int i = 0; i < jackpot.length; i++) h.assertValueEqual(c.ability(slots[i]).id, jackpot[i], "jackpot " + slots[i]);
        h.assertTrue(c.ability(AbilitySlot.ULTIMATE) == null, "no domain while in Jackpot");
        h.assertTrue(Characters.get(HakariCharacter.ID).title().equals("Restless Gambler") && Characters.get(GojoCharacter.ID).title().equals("Honored One"),
                "both characters carry their identity for the select screen");
        h.succeed();
    }

    @GameTest(maxTicks = 60)
    public void switchingCharactersIsOnlyAllowedFromNeutral(GameTestHelper h) {
        floor(h, 6);
        var player = h.makeMockServerPlayerInLevel();
        CharacterService.assign(player, Characters.get(GojoCharacter.ID));
        AbilityCaster c = Casters.get(player);
        c.setNoCost(true);
        h.assertTrue(CharacterService.select(player, HakariCharacter.ID) == null, "neutral: switching to Hakari works");
        h.assertValueEqual(c.character().id, HakariCharacter.ID, "now Hakari");
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_1).id, "reserve_balls", "Hakari's moveset is loaded");
        // Mid-technique: refused, and nothing changes.
        press(player, AbilitySlot.SKILL_3);
        h.assertTrue(c.isCasting(), "Rough Energy winding up");
        h.assertTrue(CharacterService.select(player, GojoCharacter.ID) != null, "can't switch mid-technique");
        h.assertValueEqual(c.character().id, HakariCharacter.ID, "still Hakari");
        // In Jackpot: refused too.
        c.interrupt("test");
        h.runAfterDelay(2, () -> {
            c.enterAwakening();
            h.assertTrue(CharacterService.select(player, GojoCharacter.ID) != null, "can't switch in Jackpot");
            c.endAwakening("test");
            player.setLastHurtByMob(null);
            h.assertTrue(CharacterService.select(player, GojoCharacter.ID) == null, "back to neutral: switching to Gojo works");
            h.assertValueEqual(c.ability(AbilitySlot.SKILL_1).id, "blue", "Gojo's moveset, none of Hakari's left");
            h.assertTrue(!Combat.has(player, CombatStatus.JACKPOT), "no Jackpot state carried over");
            player.discard();
            h.succeed();
        });
    }

    // --- Base moves ---

    @GameTest(maxTicks = 60)
    public void aReserveBallStunsFromRangeAndRagdollsUpClose(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy far = dummy(h, 7.5, 1.5);
        TrainingDummy g = hakari(h, 1.5, 1.5, far);
        TrainingDummy near = dummy(h, 3.5, 5.5);
        TrainingDummy g2 = hakari(h, 1.5, 5.5, near);
        float hpFar = far.getHealth(), hpNear = near.getHealth();
        boolean[] nearLaunched = {false}, farLaunched = {false}, farStunned = {false};
        press(g, AbilitySlot.SKILL_1);
        press(g2, AbilitySlot.SKILL_1);
        h.onEachTick(() -> {
            if (Combat.has(near, CombatStatus.LAUNCHED)) nearLaunched[0] = true;
            if (Combat.has(far, CombatStatus.LAUNCHED)) farLaunched[0] = true;
            if (Combat.has(far, CombatStatus.HITSTUN)) farStunned[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(far.getHealth() < hpFar && near.getHealth() < hpNear, "both balls landed");
            h.assertTrue(farStunned[0] && !farLaunched[0], "from range: a momentary stun, no ragdoll");
            h.assertTrue(nearLaunched[0], "within 15 studs: ragdolled away");
        });
    }

    @GameTest(maxTicks = 60)
    public void reserveBallsAndShutterDoorsCombine(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        float hp = target.getHealth();
        boolean[] sawDoors = {false};
        press(g, AbilitySlot.SKILL_1);
        press(g, AbilitySlot.SKILL_2);
        h.assertTrue(c.isCasting("reserve_balls"), "the doors press joined the ball instead of casting on its own");
        h.assertTrue(c.cooldown(AbilitySlot.SKILL_2) > 0, "Shutter Doors went on cooldown too");
        h.onEachTick(() -> {
            if (!h.getLevel().getEntitiesOfClass(HakariDoorEntity.class, new AABB(target.blockPosition()).inflate(3)).isEmpty()) sawDoors[0] = true;
        });
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        h.succeedWhen(() -> {
            h.assertTrue(sawDoors[0], "the doors manifested where the ball landed");
            h.assertTrue(hp - target.getHealth() >= cfg.ballDamage + cfg.comboDoorDamage + cfg.doorBounceDamage * 2,
                    "ball, doors and bounces all landed (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 60)
    public void shutterDoorsSlamShutOnTheTarget(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 8.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        boolean[] sawDoors = {false};
        press(g, AbilitySlot.SKILL_2);
        h.onEachTick(() -> {
            if (!h.getLevel().getEntitiesOfClass(HakariDoorEntity.class, new AABB(target.blockPosition()).inflate(4)).isEmpty()) sawDoors[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(sawDoors[0], "two shutter doors appeared around the target");
            h.assertTrue(target.getHealth() < hp - 4, "the doors crushed them");
            h.assertTrue(Combat.has(target, CombatStatus.HITSTUN), "pinned in hitstun");
        });
    }

    @GameTest(maxTicks = 200)
    public void missedShutterDoorsLingerAndBounceHakari(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy g = hakari(h, 1.5, 2.5, null);
        face(g, h.absoluteVec(new Vec3(6.5, 1, 2.5)));
        press(g, AbilitySlot.SKILL_2);
        HakariDoorEntity[] door = new HakariDoorEntity[1];
        double[] peak = {0};
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    var doors = h.getLevel().getEntitiesOfClass(HakariDoorEntity.class, new AABB(g.blockPosition()).inflate(12), dd -> dd.owner() == g);
                    h.assertTrue(doors.size() == 2, "the doors caught nobody and stayed (" + doors.size() + ")");
                    door[0] = doors.getFirst();
                    // Drop Hakari onto them.
                    Vec3 top = doors.getFirst().position().add(doors.get(1).position()).scale(0.5).add(0, 3.2, 0);
                    g.teleportTo(top.x, top.y, top.z);
                    g.setDeltaMovement(0, -0.2, 0);
                })
                .thenWaitUntil(() -> {
                    peak[0] = Math.max(peak[0], g.getDeltaMovement().y);
                    h.assertTrue(peak[0] > 1, "bounced high off the doors");
                })
                .thenWaitUntil(() -> h.assertTrue(door[0].isRemoved(), "and they shattered"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void shutterDoorsFinishALowTarget(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 6.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        target.setHealth(target.getMaxHealth() * 0.1f);
        press(g, AbilitySlot.SKILL_2);
        h.succeedWhen(() -> h.assertTrue(!target.isAlive(), "shut in completely: the finisher"));
    }

    @GameTest(maxTicks = 80)
    public void roughEnergyInTheAirIsAStompThatLaunches(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy target = dummy(h, 4.5, 2.5);
        TrainingDummy g = hakari(h, 2.5, 2.5, target);
        float hp = target.getHealth();
        boolean[] up = {false};
        h.startSequence()
                .thenExecute(() -> {
                    g.teleportTo(g.getX(), g.getY() + 4.5, g.getZ());
                    g.setDeltaMovement(0, 0, 0);
                })
                .thenIdle(1)
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenWaitUntil(() -> {
                    if (target.getDeltaMovement().y > 0.3) up[0] = true;
                    h.assertTrue(up[0] && target.getHealth() < hp, "the shockwave launched them upward");
                })
                .thenExecute(() -> h.assertTrue(hp - target.getHealth() >= JJKConfig.get().hakari.roughStompDamage * 1.5f,
                        "from higher than a jump: double damage (" + (hp - target.getHealth()) + ")"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void roughEnergyIsACommittedGuardBreakingStrike(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 3.8, 1.5);
        TrainingDummy g = hakari(h, 1.5, 1.5, target);
        face(target, g.getBoundingBox().getCenter());
        Combat.state(target).startGuard(h.getLevel().getGameTime() - 40);
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_3);
        h.runAfterDelay(JJKConfig.get().hakari.roughWindup - 3, () -> h.assertTrue(target.getHealth() == hp, "nothing lands during the wind-up"));
        h.succeedWhen(() -> h.assertTrue(Combat.has(target, CombatStatus.GUARD_BROKEN) || target.getHealth() < hp - 5, "the strike broke through the guard"));
    }

    @GameTest(maxTicks = 80)
    public void feverBreakerKicksRushesAndBreaks(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 3.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        float[] afterKick = {-1};
        press(g, AbilitySlot.SKILL_4);
        h.onEachTick(() -> {
            if (afterKick[0] < 0 && target.getHealth() < hp) afterKick[0] = target.getHealth();
        });
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        h.succeedWhen(() -> {
            h.assertTrue(afterKick[0] > 0, "the opening kick landed");
            h.assertTrue(target.getHealth() < afterKick[0] - cfg.feverFinishDamage * 0.5f, "then the second, heavier impact");
        });
    }

    @GameTest(maxTicks = 80)
    public void feverCrushClampsAndAxeKicks(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_4);
        press(g, AbilitySlot.SKILL_2);
        h.assertTrue(c.cooldown(AbilitySlot.SKILL_2) > 0 && c.isCasting("fever_breaker"), "Shutter Doors joined the wind-up");
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        h.succeedWhen(() -> {
            h.assertTrue(!c.isCasting(), "finished");
            h.assertTrue(hp - target.getHealth() >= cfg.crushDoorDamage + cfg.crushStompDamage * 0.9f, "doors and axe kick landed (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 80)
    public void feverCrushOnARagdolledTargetHitsTwiceAsHard(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        dev.rick.jjk.core.combat.Statuses.apply(target, CombatStatus.KNOCKDOWN, 40);
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_4);
        press(g, AbilitySlot.SKILL_2);
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        h.succeedWhen(() -> {
            h.assertTrue(!c.isCasting(), "finished");
            h.assertTrue(hp - target.getHealth() >= cfg.crushDoorDamage + cfg.crushRagdollStompDamage * 0.9f,
                    "the doors, then the heavy stomp on a ragdolled target (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 30)
    public void shutterDoorsSetYouToYourThirdHit(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 5.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        press(g, AbilitySlot.SKILL_2);
        h.assertValueEqual(Casters.get(g).melee.chainIndex(), 2, "the next light attack is the 3rd of the chain");
        h.succeed();
    }

    @GameTest(maxTicks = 220)
    public void bouncingOffTheDoorsIntoAHighAirStomp(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy g = hakari(h, 1.5, 2.5, null);
        face(g, h.absoluteVec(new Vec3(6.5, 1, 2.5)));
        press(g, AbilitySlot.SKILL_2);
        TrainingDummy[] victim = new TrainingDummy[1];
        float[] hp = new float[1];
        boolean[] stomped = {false};
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    var doors = h.getLevel().getEntitiesOfClass(HakariDoorEntity.class, new AABB(g.blockPosition()).inflate(12), dd -> dd.owner() == g);
                    h.assertTrue(doors.size() == 2, "the doors are lingering");
                    Vec3 mid = doors.getFirst().position().add(doors.get(1).position()).scale(0.5);
                    // Someone standing just past the doors, where Hakari will come down.
                    victim[0] = dummy(h, 0, 0);
                    victim[0].teleportTo(mid.x, mid.y, mid.z + 2);
                    hp[0] = victim[0].getHealth();
                    g.teleportTo(mid.x, mid.y + 3.2, mid.z);
                    g.setDeltaMovement(0, -0.2, 0);
                })
                .thenWaitUntil(() -> h.assertTrue(g.getDeltaMovement().y > 1, "bounced high off the doors"))
                .thenIdle(6)
                .thenExecute(() -> {
                    h.assertTrue(!g.onGround(), "still in the air");
                    press(g, AbilitySlot.SKILL_3);
                })
                .thenWaitUntil(() -> {
                    if (victim[0] != null && hp[0] - victim[0].getHealth() > 0) stomped[0] = true;
                    h.assertTrue(stomped[0], "the stomp landed");
                })
                .thenExecute(() -> h.assertTrue(hp[0] - victim[0].getHealth() >= JJKConfig.get().hakari.roughStompDamage * 1.5f,
                        "from that height it was the unblockable, double-damage stomp (" + (hp[0] - victim[0].getHealth()) + ")"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 200)
    public void aRagdolledEnemyBouncesOnLingeringDoors(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy g = hakari(h, 1.5, 2.5, null);
        face(g, h.absoluteVec(new Vec3(6.5, 1, 2.5)));
        press(g, AbilitySlot.SKILL_2);
        TrainingDummy[] victim = new TrainingDummy[1];
        float[] hp = new float[1];
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    var doors = h.getLevel().getEntitiesOfClass(HakariDoorEntity.class, new AABB(g.blockPosition()).inflate(12), dd -> dd.owner() == g);
                    h.assertTrue(doors.size() == 2, "the doors are lingering");
                    Vec3 mid = doors.getFirst().position().add(doors.get(1).position()).scale(0.5);
                    victim[0] = dummy(h, 0, 0);
                    victim[0].teleportTo(mid.x, mid.y + 3.4, mid.z);
                    victim[0].setDeltaMovement(0, -0.3, 0);
                    dev.rick.jjk.core.combat.Statuses.apply(victim[0], CombatStatus.LAUNCHED, 100);
                    hp[0] = victim[0].getHealth();
                })
                .thenWaitUntil(() -> h.assertTrue(victim[0] != null && hp[0] - victim[0].getHealth() >= JJKConfig.get().hakari.doorBounceDamage,
                        "a ragdolled enemy falling on them bounced and took damage"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 40)
    public void doorGuardPunchesBackThroughTheDoors(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy front = dummy(h, 4.5, 2.5);
        TrainingDummy g = hakari(h, 2.5, 2.5, front);
        press(g, AbilitySlot.SKILL_5);
        h.runAfterDelay(3, () -> {
            float hp = front.getHealth();
            HitResolver.resolve(Hit.builder(front, "t").damage(5).tag(AttackTag.MELEE).origin(front.getEyePosition()).build(), g);
            h.assertTrue(front.getHealth() <= hp - JJKConfig.get().hakari.doorGuardCounterDamage + 0.01f, "punched back through the doors");
            h.assertTrue(Combat.has(front, CombatStatus.GUARD_BROKEN) || Combat.has(front, CombatStatus.HITSTUN), "and repelled");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void doorGuardStopsFrontalAttacksButNotFromBehind(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy front = dummy(h, 4.5, 2.5);
        TrainingDummy g = hakari(h, 2.5, 2.5, front);
        TrainingDummy back = dummy(h, 0.5, 2.5);
        press(g, AbilitySlot.SKILL_5);
        h.runAfterDelay(10, () -> {
            HitResult r = HitResolver.resolve(Hit.builder(front, "t").damage(6).tag(AttackTag.TECHNIQUE).origin(front.getEyePosition()).build(), g);
            h.assertValueEqual(r.outcome(), HitResult.Outcome.NEGATED, "a technique from the front hits the door");
            float hp = g.getHealth();
            HitResult b = HitResolver.resolve(Hit.builder(back, "t").damage(4).tag(AttackTag.MELEE).origin(back.getEyePosition()).build(), g);
            h.assertTrue(b.connected() && g.getHealth() < hp, "an attack from behind gets around it");
            release(g, AbilitySlot.SKILL_5);
            h.succeed();
        });
    }

    // --- Idle Death Gamble ---

    /** Opens the domain through the real ability and makes two visual moves once it's up. */
    private static void openAndGamble(GameTestHelper h, TrainingDummy g, Runnable after) {
        AbilityCaster c = Casters.get(g);
        c.setAwakening(c.maxAwakening());
        h.startSequence()
                .thenExecute(() -> h.assertTrue(c.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "Idle Death Gamble opens on a full meter"))
                .thenWaitUntil(() -> h.assertTrue(IdleDeathGamble.gambleOf(g) != null, "domain sealed and the gamble running"))
                .thenExecute(() -> press(g, AbilitySlot.SKILL_1))
                .thenIdle(18)
                .thenExecute(() -> press(g, AbilitySlot.SKILL_2))
                .thenExecute(after);
    }

    @GameTest(maxTicks = 320, padding = 10)
    public void idleDeathGambleRunsToAJackpot(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 7.5, 4.5);
        TrainingDummy g = hakari(h, 4.5, 4.5, target);
        AbilityCaster c = Casters.get(g);
        HakariState.of(g).oddsBonus = 5f; // a certain jackpot for this Hakari
        DomainInstance[] d = new DomainInstance[1];
        boolean[] riichi = {false};
        h.onEachTick(() -> {
            Gamble gm = IdleDeathGamble.gambleOf(g);
            if (gm != null && gm.state() == Gamble.State.RIICHI) riichi[0] = true;
            if (d[0] == null) d[0] = DomainManager.ownedBy(g);
        });
        openAndGamble(h, g, () -> {});
        h.succeedWhen(() -> {
            h.assertTrue(d[0] != null && d[0].structure() != null, "built as a physical domain");
            h.assertTrue(riichi[0], "two visual moves led to a Riichi");
            h.assertTrue(c.isAwakened(), "JACKPOT");
            h.assertValueEqual(c.ability(AbilitySlot.SKILL_1).id, "lucky_volley", "Jackpot moveset");
            h.assertTrue(g.getHealth() == g.getMaxHealth(), "health restored");
            h.assertTrue(!Combat.has(g, CombatStatus.BURNOUT), "no burnout out of a jackpot");
            h.assertTrue(DomainManager.ownedBy(g) == null || !DomainManager.ownedBy(g).isLive(), "the domain closed on the jackpot");
            HakariState hs = HakariState.of(g);
            h.assertTrue(hs.lastJackpot > 0 && (hs.lastJackpot % 2 == 1 ? hs.oddsBonus > 0 : hs.fastRiichi), "the jackpot's parity set the next bonus");
        });
    }

    @GameTest(maxTicks = 200, padding = 10)
    public void idleDeathGambleBuildsItsOwnCasino(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy g = hakari(h, 4.5, 4.5, null);
        BlockPos c = h.absolutePos(new BlockPos(4, 1, 4));
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> d[0] = DomainManager.expand(g, IdleDeathGamble.INSTANCE))
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.BUILT && IdleDeathGamble.gambleOf(g) != null,
                        "built, sealed and gambling"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(c.below()).is(ModBlocks.IDG_FLOOR), "casino floor");
                    h.assertTrue(h.getLevel().getBlockState(c.above(6)).is(ModBlocks.IDG_BARRIER), "neon pachinko ceiling");
                    h.assertTrue(IdleDeathGamble.gambleOf(g) != null, "the gamble is running");
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].structure().state() == DomainStructure.State.DONE, "restored"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(c.above(6)).isAir() && !h.getLevel().getBlockState(c.below()).is(ModBlocks.IDG_FLOOR),
                            "the world is back");
                    h.assertTrue(IdleDeathGamble.gambleOf(g) == null, "gamble cleaned up");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 320, padding = 10)
    public void aMissedGambleKeepsGoingAndRefundsOnClose(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy g = hakari(h, 4.5, 4.5, null);
        AbilityCaster c = Casters.get(g);
        HakariState.of(g).oddsBonus = -5f; // this Hakari can't win
        boolean[] missed = {false};
        h.onEachTick(() -> {
            Gamble gm = IdleDeathGamble.gambleOf(g);
            if (gm != null && gm.state() == Gamble.State.MISS) missed[0] = true;
        });
        openAndGamble(h, g, () -> {});
        h.succeedWhen(() -> {
            h.assertTrue(missed[0], "the Riichi missed");
            h.assertTrue(!c.isAwakened(), "no Jackpot");
            Gamble gm = IdleDeathGamble.gambleOf(g);
            h.assertTrue(gm != null && gm.state() == Gamble.State.SPINNING, "back to spinning for another attempt");
            DomainManager.cancel(DomainManager.ownedBy(g), DomainInstance.EndReason.CANCELLED);
            h.assertTrue(c.awakening() >= c.maxAwakening() * JJKConfig.get().hakari.missRefund - 0.01f, "part of the meter comes back");
        });
    }

    @GameTest(maxTicks = 400, padding = 10)
    public void renewalRewindsToWhereTheBallLanded(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 7.5, 4.5);
        TrainingDummy g = hakari(h, 4.5, 4.5, target);
        Vec3[] spot = new Vec3[1];
        float[] hp = new float[1];
        h.startSequence()
                .thenExecute(() -> DomainManager.expand(g, IdleDeathGamble.INSTANCE))
                .thenWaitUntil(() -> h.assertTrue(IdleDeathGamble.gambleOf(g) != null, "the gamble is running"))
                .thenExecute(() -> press(g, AbilitySlot.SKILL_1))
                .thenWaitUntil(() -> h.assertTrue(HakariState.of(g).renewal != null, "the ball landed inside the domain: a moment to go back to"))
                .thenExecute(() -> {
                    spot[0] = target.position();
                    hp[0] = g.getHealth();
                    target.teleportTo(target.getX() - 1.5, target.getY(), target.getZ() + 1.5);
                    g.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 6);
                    h.assertTrue(g.getHealth() < hp[0], "Hakari took damage");
                    Combat.state(g).clearAll();
                    var r = HakariState.of(g).renewal;
                    String info = r == null ? "no snapshot" : r.spots().size() + " spots " + r.spots().stream().map(sp -> sp.entity().getName().getString() + "@" + sp.pos()).toList();
                    boolean used = Casters.get(g).input(AbilitySlot.SKILL_1, true, 0, 0, null);
                    h.assertTrue(target.position().distanceTo(spot[0]) < 0.6, "Renewal put everyone back where they stood (" + info + ", pressed " + used
                            + ", refusal " + Casters.get(g).lastRefusal + ", target " + target.position() + " vs " + spot[0] + ")");
                    h.assertTrue(g.getHealth() >= hp[0] - 0.01f, "and undid the damage he took");
                    h.assertTrue(HakariState.of(g).renewal == null, "used up");
                    DomainManager.collapseOwnedBy(g, DomainInstance.EndReason.CANCELLED);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 900, padding = 10)
    public void theFourthScenarioIsAPityJackpot(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 7.5, 4.5);
        TrainingDummy g = hakari(h, 4.5, 4.5, target);
        AbilityCaster c = Casters.get(g);
        HakariState.of(g).oddsBonus = -5f; // it can't win a normal scenario
        c.setAwakening(c.maxAwakening());
        boolean[] pity = {false};
        int[] attempts = {0};
        h.onEachTick(() -> {
            Gamble gm = IdleDeathGamble.gambleOf(g);
            if (gm == null) return;
            if (gm.pity()) pity[0] = true;
            if (gm.state() == Gamble.State.RIICHI) attempts[0] = Math.max(attempts[0], 1);
            // Keep making visual moves.
            if (gm.state() == Gamble.State.SPINNING && !c.isBusy() && g.tickCount % 3 == 0) press(g, AbilitySlot.SKILL_1);
        });
        h.startSequence()
                .thenExecute(() -> h.assertTrue(c.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "Idle Death Gamble opens"))
                .thenWaitUntil(() -> h.assertTrue(c.isAwakened(), "Jackpot came in the end"))
                .thenExecute(() -> {
                    h.assertTrue(pity[0], "it was the pity jackpot of the last scenario");
                    h.assertTrue(c.awakening() <= c.maxAwakening() * 0.5f + 0.01f, "with half the usual Jackpot time (" + c.awakening() + ")");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 20)
    public void jackpotIsImmortalWhileItsMeterLasts(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy attacker = dummy(h, 3.5, 1.5);
        TrainingDummy g = hakari(h, 1.5, 1.5, attacker);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.enterAwakening();
        float full = c.awakening();
        // A blow worth one max health drains about a third of the meter (it empties after 3.33 of them)...
        HitResolver.resolve(Hit.builder(attacker, "t").type(ModDamageTypes.TECHNIQUE).damage(g.getMaxHealth()).tag(AttackTag.UNBLOCKABLE).build(), g);
        h.assertTrue(g.isAlive(), "the Reverse Cursed Technique kept him up");
        float drained = full - c.awakening();
        h.assertTrue(drained > full * 0.2f && drained < full * 0.45f, "the blow drained the Jackpot meter (" + drained + " of " + full + ")");
        Combat.state(g).clearAll();
        // ...and once it's empty he can be put down.
        c.setAwakening(0.5f);
        HitResolver.resolve(Hit.builder(attacker, "t").type(ModDamageTypes.TECHNIQUE).damage(10000).tag(AttackTag.UNBLOCKABLE).build(), g);
        h.assertTrue(!g.isAlive(), "no meter left: not immortal any more");
        h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void survivingAJackpotRefundsMoreEachTimeInARow(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = hakari(h, 1.5, 1.5, null);
        AbilityCaster c = Casters.get(g);
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        HakariState hs = HakariState.of(g);
        hs.jackpotChain = 1;
        c.enterAwakening();
        c.endAwakening("test");
        h.assertTrue(Math.abs(c.awakening() - c.maxAwakening() * cfg.jackpotRefund) < 0.5f, "first Jackpot survived: 40% back (" + c.awakening() + ")");
        hs.jackpotChain = 2;
        c.enterAwakening();
        c.endAwakening("test");
        h.assertTrue(Math.abs(c.awakening() - c.maxAwakening() * (cfg.jackpotRefund + cfg.jackpotRefundChain)) < 0.5f, "second in a row: 65% back");
        h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void jackpotRegeneratesAndHasUnlimitedEnergy(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = hakari(h, 1.5, 1.5, null);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.enterAwakening();
        g.setHealth(g.getMaxHealth() * 0.5f);
        c.setEnergy(10);
        float hp = g.getHealth();
        h.runAfterDelay(20, () -> {
            h.assertTrue(g.getHealth() > hp + 1, "regenerating");
            h.assertTrue(c.energy() >= c.maxEnergy() - 1, "cursed energy stays full");
            h.assertTrue(c.awakening() < c.maxAwakening(), "the Jackpot timer is running down");
            h.succeed();
        });
    }

    // --- Jackpot moves ---

    @GameTest(maxTicks = 80)
    public void luckyVolleyIsAnOpenerAFlurryAndAFinalStrike(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        Casters.get(g).enterAwakening();
        float hp = target.getHealth();
        JJKConfig.Hakari cfg = JJKConfig.get().hakari;
        press(g, AbilitySlot.SKILL_1);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isCasting(), "finished");
            h.assertTrue(hp - target.getHealth() >= (cfg.volleyOpenerDamage + cfg.volleyFinalDamage) * 0.6f, "many hits landed");
            h.assertTrue(target.position().distanceTo(g.position()) > 3, "the final strike sent them flying");
        });
    }

    @GameTest(maxTicks = 80)
    public void luckyVolleyFinishesALowTarget(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        Casters.get(g).enterAwakening();
        target.setHealth(target.getMaxHealth() * 0.25f);
        press(g, AbilitySlot.SKILL_1);
        h.succeedWhen(() -> h.assertTrue(!target.isAlive(), "the flurry brought them low and the swipe finished them"));
    }

    @GameTest(maxTicks = 80, padding = 12)
    public void luckyRushdownRunsThemDownAndThrowsThem(GameTestHelper h) {
        floor(h, 22);
        // Inside the test area (it is walled at x = 8): the run, the drag and the throw all go toward that wall.
        TrainingDummy target = dummy(h, 4.5, 2.5);
        TrainingDummy g = hakari(h, 0.5, 2.5, target);
        Casters.get(g).enterAwakening();
        Vec3 start = target.position();
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_2);
        h.succeedWhen(() -> {
            h.assertTrue(target.getHealth() < hp - 5, "grabbed and thrown");
            h.assertTrue(target.position().distanceTo(start) > 2, "dragged and thrown away (target " + target.position() + " from " + start
                    + ", hakari " + g.position() + ", hp " + target.getHealth() + "/" + hp + ")");
        });
    }

    @GameTest(maxTicks = 90, padding = 10)
    public void overwhelmingLuckMarchesForwardAndEndsBig(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        Casters.get(g).enterAwakening();
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_3);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isCasting(), "finished");
            h.assertTrue(hp - target.getHealth() >= JJKConfig.get().hakari.overwhelmFinalDamage * 0.8f, "the punches and the final one landed");
        });
    }

    @GameTest(maxTicks = 60, padding = 10)
    public void energySurgeDashesVanishesAndKicksFromAbove(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 5.5, 2.5);
        TrainingDummy g = hakari(h, 1.5, 2.5, target);
        Casters.get(g).enterAwakening();
        float hp = target.getHealth();
        double[] highest = {0};
        press(g, AbilitySlot.SKILL_4);
        h.onEachTick(() -> highest[0] = Math.max(highest[0], g.getY() - target.getY()));
        h.succeedWhen(() -> {
            h.assertTrue(highest[0] > 1.5, "reappeared above the target (" + highest[0] + ")");
            h.assertTrue(target.getHealth() < hp - 5, "dash and kick both landed");
            h.assertTrue(!g.isInvisible(), "visible again");
        });
    }

    @GameTest(maxTicks = 80)
    public void rhythmStacksSpeedAndCutsCooldowns(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = hakari(h, 1.5, 1.5, null);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.enterAwakening();
        c.startCooldown(AbilitySlot.SKILL_1, 200);
        press(g, AbilitySlot.SKILL_5);
        int[] cdAtStart = {-1};
        h.onEachTick(() -> {
            if (cdAtStart[0] < 0) cdAtStart[0] = c.cooldown(AbilitySlot.SKILL_1);
        });
        h.succeedWhen(() -> {
            h.assertTrue(!c.isCasting(), "the dance finished");
            h.assertValueEqual(HakariState.of(g).rhythmStacks, 1, "a stack of speed");
            h.assertTrue(c.character().castSpeed(c) > 1f, "his moves play out faster");
            int elapsed = 200 - c.cooldown(AbilitySlot.SKILL_1);
            h.assertTrue(elapsed >= JJKConfig.get().hakari.rhythmCooldownCut + 10, "cooldowns finished sooner (" + elapsed + " gone)");
        });
    }

    // --- Counter, clash, per-life bonus ---

    @GameTest(maxTicks = 120, padding = 14)
    public void hakariCountersADomainAndTheyClash(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy gojo = dummy(h, 5.5, 4.5);
        CharacterService.assign(gojo, Characters.get(GojoCharacter.ID));
        Casters.get(gojo).setNoCost(true);
        ClashManager.setBotSkill(gojo, 0.5f);
        TrainingDummy g = hakari(h, 10.5, 4.5, gojo);
        ClashManager.setBotSkill(g, 0.5f);
        AbilityCaster hc = Casters.get(g);
        hc.setNoCost(false);
        hc.setAwakening(hc.maxAwakening());
        hc.setEnergy(hc.maxEnergy());
        Casters.get(gojo).enterAwakening();
        h.startSequence()
                .thenExecute(() -> h.assertTrue(Casters.get(gojo).input(AbilitySlot.ULTIMATE, true, 0, 0, null), "Gojo starts opening"))
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(hc.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "Hakari answers with his domain");
                    h.assertTrue(!hc.isAwakened(), "a counter goes straight to the domain (no Jackpot for free)");
                    h.assertTrue(DomainManager.ownedBy(g) != null, "Idle Death Gamble opened at once");
                    h.assertTrue(hc.awakening() == 0, "the meter was spent");
                })
                .thenWaitUntil(() -> {
                    DomainInstance hd = DomainManager.ownedBy(g), gd = DomainManager.ownedBy(gojo);
                    h.assertTrue(hd != null && ClashManager.of(hd) != null, "the two domains clash (hakari " + (hd == null ? "none" : hd.phase())
                            + ", gojo " + (gd == null ? "none, casting " + Casters.get(gojo).isCasting() : gd.phase() + " " + gd.center.distanceTo(hd == null ? gd.center : hd.center)) + ")");
                })
                .thenExecute(() -> {
                    DomainManager.collapseOwnedBy(gojo, DomainInstance.EndReason.CANCELLED);
                    DomainManager.collapseOwnedBy(g, DomainInstance.EndReason.CANCELLED);
                })
                .thenSucceed();
    }

    /**
     * Two domains in a clash share the space: each side of the split is built from its own domain's blocks, the two
     * shells open into one room, the winner's side then consumes the loser's step by step, the winner keeps the whole
     * space under its normal rules, and when it finally ends every block of both comes back exactly.
     */
    @GameTest(maxTicks = 700, padding = 24)
    public void aClashSplitsTheSpaceAndTheWinnerConsumesTheLoser(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy gojo = dummy(h, 4.5, 4.5);
        CharacterService.assign(gojo, Characters.get(GojoCharacter.ID));
        ClashManager.setBotSkill(gojo, 0.95f);
        TrainingDummy g = hakari(h, 9.5, 4.5, gojo);
        ClashManager.setBotSkill(g, 0.1f);
        // The world before either domain, to check the restore against.
        BlockPos lo = h.absolutePos(new BlockPos(-6, -8, -6)), hi = h.absolutePos(new BlockPos(20, 10, 16));
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(lo, hi)) before.put(p.immutable(), h.getLevel().getBlockState(p));
        DomainInstance[] d = new DomainInstance[2];
        DomainStructure[] st = new DomainStructure[2];
        java.util.List<Double> shares = new java.util.ArrayList<>();
        h.onEachTick(() -> {
            if (d[0] != null && d[0].front() != null && d[0].front().conquering()) shares.add(d[0].front().shareOfFirst());
        });
        h.startSequence()
                .thenExecute(() -> {
                    d[0] = DomainManager.expand(gojo, dev.rick.jjk.gojo.UnlimitedVoid.INSTANCE);
                    h.assertTrue(d[0] != null && d[0].structure() != null, "Gojo's domain is built");
                    st[0] = d[0].structure();
                })
                .thenWaitUntil(() -> h.assertTrue(st[0].state() == DomainStructure.State.BUILT, "Gojo's structure stands"))
                .thenExecute(() -> {
                    d[1] = DomainManager.expand(g, IdleDeathGamble.INSTANCE);
                    h.assertTrue(d[1] != null && d[1].structure() != null && d[1].clash() != null, "Hakari's domain clashes with it");
                    st[1] = d[1].structure();
                    h.assertTrue(d[0].front() != null && d[0].front() == d[1].front(), "the space is split between them");
                })
                .thenWaitUntil(() -> h.assertTrue(st[1].state() == DomainStructure.State.BUILT, "Hakari's structure stands"))
                .thenExecute(() -> {
                    BlockPos gc = st[0].center;
                    var level = h.getLevel();
                    h.assertTrue(level.getBlockState(gc.offset(1, -1, 0)).is(ModBlocks.DOMAIN_FLOOR), "Gojo's side of the floor is his");
                    var far = level.getBlockState(gc.offset(4, -1, 0));
                    h.assertTrue(far.is(ModBlocks.IDG_FLOOR), "Hakari's side of the floor is his, even over Gojo's blocks");
                    h.assertTrue(level.getBlockState(gc.offset(6, 0, 0)).isAir(), "Gojo's wall inside Hakari's room is opened up: one shared space");
                    h.assertTrue(level.getBlockState(st[1].center.above(6)).is(ModBlocks.IDG_BARRIER), "Hakari's far ceiling is his own");
                })
                .thenWaitUntil(() -> h.assertTrue(!d[1].isLive(), "the clash was decided and Hakari's space consumed"))
                .thenExecute(() -> {
                    h.assertValueEqual(d[1].endReason(), DomainInstance.EndReason.CLASH_LOST, "Hakari lost the clash");
                    h.assertTrue(d[0].phase() == DomainInstance.Phase.ACTIVE, "Gojo's domain runs by its normal rules");
                    h.assertTrue(shares.size() >= dev.rick.jjk.core.domain.ClashFront.CONQUEST_TICKS - 1, "the conquest took time (" + shares.size() + " ticks)");
                    for (double[] band : new double[][] {{0.55, 0.68}, {0.7, 0.8}, {0.85, 0.95}}) {
                        h.assertTrue(shares.stream().anyMatch(v -> v >= band[0] && v <= band[1]),
                                "the winner's share passed through " + band[0] + ".." + band[1] + " on the way (no instant swap)");
                    }
                    h.assertTrue(d[1].structure() == null && d[0].annexes().size() == 1, "Hakari's space became Gojo's territory");
                    h.assertTrue(d[0].contains(Vec3.atCenterOf(st[1].center)), "Gojo's domain now covers it");
                    h.assertTrue(st[1].state() == DomainStructure.State.BUILT, "the conquered blocks stay up with the winner");
                    var level = h.getLevel();
                    h.assertTrue(level.getBlockState(st[1].center.above(6)).is(ModBlocks.DOMAIN_BARRIER), "the far ceiling is Gojo's now");
                    for (BlockPos p : BlockPos.betweenClosed(st[1].center.offset(-7, -7, -7), st[1].center.offset(7, 7, 7))) {
                        var b = level.getBlockState(p);
                        h.assertFalse(b.is(ModBlocks.IDG_BARRIER) || b.is(ModBlocks.IDG_FLOOR), "nothing of Hakari's domain is left at " + p);
                    }
                    DomainManager.collapseOwnedBy(gojo, DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(st[0].state() == DomainStructure.State.DONE && st[1].state() == DomainStructure.State.DONE,
                        "both structures are given back when the winner's domain ends"))
                .thenExecute(() -> {
                    for (var e : before.entrySet()) {
                        h.assertTrue(h.getLevel().getBlockState(e.getKey()) == e.getValue(), "restored exactly at " + e.getKey()
                                + " (" + h.getLevel().getBlockState(e.getKey()) + " vs " + e.getValue() + ")");
                    }
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 20)
    public void theJackpotBonusIsLostOnDeath(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = hakari(h, 1.5, 1.5, null);
        HakariState.of(g).oddsBonus = 0.25f;
        g.kill(h.getLevel());
        h.runAfterDelay(2, () -> {
            h.assertTrue(HakariState.of(g).oddsBonus == 0, "death cleared the bonus");
            h.succeed();
        });
    }
}
