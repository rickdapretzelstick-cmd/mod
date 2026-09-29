package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.AttackTag;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatState;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Hit;
import dev.rick.jjk.core.combat.HitResolver;
import dev.rick.jjk.core.combat.HitResult;
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.melee.MeleeSystem;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.net.MeleeInputPayload;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.entity.HollowPurpleEntity;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.BlueAbility;
import dev.rick.jjk.gojo.RedAbility;
import dev.rick.jjk.gojo.TeleportAbility;
import dev.rick.jjk.gojo.InfinityAbility;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server-side behaviour tests for Gojo's kit. Casters are training dummies playing Gojo (any LivingEntity can be a
 * caster), which the level ticks like a normal mob.
 */
public class GojoGameTests {
    private static JJKConfig testConfig;

    /** Test values (applied lazily: the mod loads config/jjk.json during init, after this class may have loaded). */
    private static void applyConfig() {
        if (testConfig != null && JJKConfig.get() == testConfig) return;
        JJKConfig cfg = new JJKConfig();
        cfg.general.autoAssignGojo = false;
        cfg.purple.range = 14;
        cfg.purple.maxBlocksDestroyed = 400;
        cfg.domain.radius = 6;
        cfg.domain.duration = 60;
        cfg.domain.startup = 10;
        cfg.clash.notes = 8;
        cfg.clash.countdownTicks = 10;
        cfg.red.range = 12;
        cfg.teleport.targetRange = 12;
        JJKConfig.set(cfg);
        testConfig = cfg;
    }

    // --- helpers ---

    private static void floor(GameTestHelper h, int size) {
        applyConfig();
        for (int x = -size; x < 8 + size; x++) for (int z = -size; z < 8 + size; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        return d;
    }

    private static TrainingDummy gojo(GameTestHelper h, double x, double z, LivingEntity faceTarget) {
        TrainingDummy g = dummy(h, x, z);
        CharacterService.assign(g, Characters.get(GojoCharacter.ID));
        Casters.get(g).setNoCost(true);
        if (faceTarget != null) face(g, faceTarget.getBoundingBox().getCenter());
        return g;
    }

    /** Infinity is out of Gojo's moveset for now; these tests raise it directly to exercise the implementation. */
    private static TrainingDummy withInfinity(TrainingDummy g) {
        AbilityCaster c = Casters.get(g);
        if (c.character() instanceof GojoCharacter gojo) gojo.infinity.turnOn(c, false);
        return g;
    }

    private static List<BlueEntity> ownedBlues(GameTestHelper h, LivingEntity owner) {
        return h.getLevel().getEntitiesOfClass(BlueEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40), b -> b.owner() == owner);
    }

    private static void face(LivingEntity e, Vec3 target) {
        Vec3 d = target.subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
    }

    private static void infinityOff(LivingEntity e) {
        AbilityCaster c = Casters.get(e);
        if (c.character() instanceof GojoCharacter gojo) gojo.infinity.toggleOff(c, "test");
        c.resetCooldowns();
    }

    private static void awaken(LivingEntity e) {
        Casters.get(e).enterAwakening();
    }

    private static void press(LivingEntity e, AbilitySlot slot) {
        Casters.get(e).input(slot, true, 0, 0, null);
    }

    private static void release(LivingEntity e, AbilitySlot slot) {
        Casters.get(e).input(slot, false, 0, 0, null);
    }

    private static void light(LivingEntity e, int flags) {
        MeleeSystem.handleInput(e, Casters.get(e), MeleeInputPayload.LIGHT, flags, -1);
    }

    // --- melee ---

    @GameTest(maxTicks = 80)
    public void lightChainEndsInKnockbackFinisher(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        infinityOff(target);
        double startX = target.getX();
        h.startSequence()
                .thenExecute(() -> light(g, 0))
                .thenIdle(7).thenExecute(() -> {
                    h.assertTrue(Combat.has(target, CombatStatus.HITSTUN), "light 1 applies hitstun");
                    light(g, 0);
                })
                .thenIdle(7).thenExecute(() -> light(g, 0))
                .thenIdle(7).thenExecute(() -> {
                    h.assertValueEqual(Combat.state(target).comboCount(h.getLevel().getGameTime(), 40), 3, "combo after 3 lights");
                    light(g, 0);
                })
                .thenIdle(10).thenExecute(() -> {
                    h.assertTrue(Combat.state(target).comboCount(h.getLevel().getGameTime(), 40) >= 4, "finisher counted in combo");
                    h.assertTrue(Math.abs(target.getX() - startX) > 1.5, "finisher knocks the target away (moved " + (target.getX() - startX) + ")");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 80)
    public void uppercutLaunchesWhenJumpHeld(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        infinityOff(target);
        double[] peak = {0};
        h.startSequence()
                .thenExecute(() -> light(g, 0)).thenIdle(7)
                .thenExecute(() -> light(g, 0)).thenIdle(7)
                .thenExecute(() -> light(g, 0)).thenIdle(7)
                .thenExecute(() -> light(g, MeleeInputPayload.FLAG_JUMP))
                .thenExecuteFor(14, () -> peak[0] = Math.max(peak[0], target.getY() - h.absoluteVec(Vec3.ZERO).y))
                .thenExecute(() -> h.assertTrue(peak[0] > 2.5, "uppercut launches (peak " + peak[0] + ")"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void guardBlocksLightsAndHeavyBreaksIt(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        face(target, g.getEyePosition());
        infinityOff(target);
        CombatState ts = Combat.state(target);
        ts.startGuard(h.getLevel().getGameTime() - 100);
        float hp = target.getHealth();
        Hit light = Hit.builder(g, "t").damage(3).tag(AttackTag.MELEE).origin(g.getEyePosition()).hitstun(10).knockback(Knockback.NONE).build();
        HitResult r = HitResolver.resolve(light, target);
        h.assertValueEqual(r.outcome(), HitResult.Outcome.BLOCKED, "guarded light");
        h.assertTrue(target.getHealth() == hp, "blocked melee deals no damage");
        h.assertTrue(!ts.has(CombatStatus.HITSTUN), "blocked hit causes no hitstun");
        Hit heavy = Hit.builder(g, "t").damage(8).tag(AttackTag.MELEE, AttackTag.HEAVY, AttackTag.GUARD_BREAK).origin(g.getEyePosition()).hitstun(20).build();
        HitResult r2 = HitResolver.resolve(heavy, target);
        h.assertValueEqual(r2.outcome(), HitResult.Outcome.GUARD_BROKEN, "heavy breaks guard");
        h.assertTrue(ts.has(CombatStatus.GUARD_BROKEN) && !ts.isGuarding(), "defender is guard-broken");
        h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void perfectGuardParriesAttacker(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        face(target, g.getEyePosition());
        Combat.state(target).startGuard(h.getLevel().getGameTime());
        HitResult r = HitResolver.resolve(Hit.builder(g, "t").damage(3).tag(AttackTag.MELEE).origin(g.getEyePosition()).build(), target);
        h.assertValueEqual(r.outcome(), HitResult.Outcome.PARRIED, "parry");
        h.assertTrue(Combat.has(g, CombatStatus.GUARD_BROKEN), "attacker punished");
        h.succeed();
    }

    @GameTest(maxTicks = 120)
    public void downslamKnocksDownAndOnlyGroundAttacksConnect(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        infinityOff(target);
        h.startSequence()
                .thenExecute(() -> {
                    // Put both in the air as if mid air-combo.
                    target.setPos(target.getX(), target.getY() + 3, target.getZ());
                    g.setPos(g.getX(), g.getY() + 3, g.getZ());
                    Combat.state(target).apply(CombatStatus.LAUNCHED, 40);
                    Combat.state(g).apply(CombatStatus.HOVER, 40);
                    face(g, target.getBoundingBox().getCenter());
                    Casters.get(g).melee.reset();
                })
                .thenExecute(() -> airLight(h, g, target, 0))
                .thenIdle(7).thenExecute(() -> airLight(h, g, target, 1))
                .thenIdle(7).thenExecute(() -> airLight(h, g, target, 2))
                .thenIdle(7).thenExecute(() -> airLight(h, g, target, 3))
                .thenIdle(6).thenExecute(() -> h.assertTrue(Combat.has(target, CombatStatus.SPIKED) || Combat.has(target, CombatStatus.KNOCKDOWN),
                        "downslam spikes (target y " + (target.getY() - h.absoluteVec(Vec3.ZERO).y) + ", gojo onGround " + g.onGround() + ")"))
                .thenWaitUntil(() -> h.assertTrue(Combat.has(target, CombatStatus.KNOCKDOWN), "spiked target is knocked down on landing"))
                .thenExecute(() -> {
                    HitResult whiff = HitResolver.resolve(Hit.builder(g, "t").damage(3).tag(AttackTag.MELEE).build(), target);
                    h.assertValueEqual(whiff.outcome(), HitResult.Outcome.WHIFF, "normal melee whiffs on a downed target");
                    HitResult otg = HitResolver.resolve(Hit.builder(g, "t").damage(3).tag(AttackTag.MELEE, AttackTag.OTG)
                            .knockback(Knockback.set(new Vec3(0, 0.5, 0))).build(), target);
                    h.assertValueEqual(otg.outcome(), HitResult.Outcome.HIT, "ground attack connects");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 40)
    public void longCombosLoseHitstun(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        infinityOff(target);
        Hit hit = Hit.builder(g, "t").damage(0.1f).tag(AttackTag.MELEE).hitstun(20).knockback(Knockback.NONE).build();
        for (int i = 0; i < 11; i++) {
            Combat.state(target).remove(CombatStatus.HITSTUN);
            HitResolver.resolve(hit, target);
        }
        h.assertTrue(Combat.state(target).get(CombatStatus.HITSTUN) < 20, "hitstun decays deep into a combo");
        h.succeed();
    }

    private static void airLight(GameTestHelper h, LivingEntity g, LivingEntity target, int expectedChain) {
        h.assertTrue(!g.onGround(), "attacker still airborne before air hit " + (expectedChain + 1) + " (y " + (g.getY() - h.absoluteVec(Vec3.ZERO).y) + ")");
        h.assertValueEqual(Casters.get(g).melee.chainIndex(), expectedChain, "chain index");
        face(g, target.getBoundingBox().getCenter());
        light(g, MeleeInputPayload.FLAG_AIRBORNE);
        h.assertTrue(Casters.get(g).melee.current() != null, "air attack " + (expectedChain + 1) + " started");
    }

    // --- Infinity ---

    @GameTest(maxTicks = 40)
    public void baseKitIsTheJjsKit(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy g = gojo(h, 4, 4, null);
        AbilityCaster c = Casters.get(g);
        h.assertTrue(!c.toggled(InfinityAbility.ID), "Infinity isn't raised by default");
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_1).id, BlueAbility.ID, "1 Lapse Blue");
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_2).id, RedAbility.ID, "2 Reversal Red");
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_3).id, dev.rick.jjk.gojo.RapidPunchesAbility.ID, "3 Rapid Punches");
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_4).id, dev.rick.jjk.gojo.TwofoldKickAbility.ID, "4 Twofold Kick");
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_5).id, TeleportAbility.ID, "R Limitless");
        h.assertValueEqual(c.ability(AbilitySlot.ULTIMATE).id, "awaken", "G Awakening");
        h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void infinityStopsMeleeAndCostsEnergy(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy attacker = dummy(h, 3, 4);
        TrainingDummy g = withInfinity(gojo(h, 5, 4, attacker));
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        float before = c.energy();
        float hp = g.getHealth();
        HitResult r = HitResolver.resolve(Hit.builder(attacker, "t").damage(6).tag(AttackTag.MELEE).origin(attacker.getEyePosition()).hitstun(10).build(), g);
        h.assertValueEqual(r.outcome(), HitResult.Outcome.NEGATED, "Infinity negates melee");
        h.assertTrue(g.getHealth() == hp && !Combat.has(g, CombatStatus.HITSTUN), "no damage, no hitstun");
        h.assertTrue(c.energy() < before, "stopping it cost cursed energy");
        // Vanilla melee is stopped too.
        attacker.doHurtTarget(h.getLevel(), g);
        h.assertTrue(g.getHealth() == hp, "vanilla mob attack stopped");
        // Sure-hit and bypass tags pass.
        HitResult sure = HitResolver.resolve(Hit.builder(attacker, "t").type(ModDamageTypes.SURE_HIT).damage(1).tag(AttackTag.SURE_HIT).build(), g);
        h.assertValueEqual(sure.outcome(), HitResult.Outcome.HIT, "sure-hit bypasses Infinity");
        h.succeed();
    }

    @GameTest(maxTicks = 60)
    public void infinityHoldsArrowsInTheAir(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy g = withInfinity(gojo(h, 4, 6, null));
        float hp = g.getHealth();
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityTypes.ARROW, h.getLevel());
        Vec3 from = h.absoluteVec(new Vec3(4, 2.2, 0.5));
        arrow.setPos(from.x, from.y, from.z);
        arrow.shoot(0, 0, 1, 2.5f, 0);
        h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(15, () -> {
            h.assertTrue(g.getHealth() == hp, "arrow never hit Gojo");
            double d = arrow.position().distanceTo(g.getBoundingBox().getCenter());
            h.assertTrue(!arrow.isRemoved() && d < 3.5 && arrow.getDeltaMovement().length() < 0.2,
                    "arrow hangs at Infinity's edge (distance " + d + ", speed " + arrow.getDeltaMovement().length() + ")");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void infinityCollapsesWhenEnergyRunsOut(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy attacker = dummy(h, 3, 4);
        TrainingDummy g = withInfinity(gojo(h, 5, 4, attacker));
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setEnergy(5);
        HitResult r = HitResolver.resolve(Hit.builder(attacker, "t").damage(4).tag(AttackTag.MELEE).build(), g);
        h.assertValueEqual(r.outcome(), HitResult.Outcome.HIT, "attack lands once Infinity can't be paid for");
        h.assertTrue(!c.toggled(InfinityAbility.ID), "Infinity collapsed");
        h.succeed();
    }

    // --- Blue / Red ---

    @GameTest(maxTicks = 100, padding = 16, skyAccess = true, environment = "jjk-test:pull_a")
    public void lapseBluePullsSuspendsThenKicks(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 7, 4);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1, 4, target);
        float hp = target.getHealth();
        boolean[] armored = new boolean[1];
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_1))
                .thenExecute(() -> release(g, AbilitySlot.SKILL_1))
                .thenIdle(JJKConfig.get().gojo.blueWindup + 4)
                .thenExecute(() -> h.assertTrue(Combat.has(target, CombatStatus.PULLED) || Combat.has(target, CombatStatus.GRABBED), "pulled in"))
                .thenWaitUntil(() -> {
                    if (Combat.has(g, CombatStatus.MELEE_ARMOR)) armored[0] = true;
                    h.assertTrue(target.distanceTo(g) < 3, "suspended right in front of Gojo (" + target.distanceTo(g) + ")");
                })
                .thenWaitUntil(() -> {
                    if (Combat.has(g, CombatStatus.MELEE_ARMOR)) armored[0] = true;
                    h.assertTrue(Combat.has(target, CombatStatus.LAUNCHED), "then kicked away");
                })
                .thenExecute(() -> {
                    float want = JJKConfig.get().gojo.bluePullDamage + JJKConfig.get().gojo.blueKickDamage;
                    h.assertTrue(hp - target.getHealth() >= want * 0.8f, "pull + kick damage (" + (hp - target.getHealth()) + ")");
                    h.assertTrue(armored[0], "Gojo had melee i-frames while it was suspended");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 40, padding = 16, skyAccess = true)
    public void lapseBlueAtNobodyWhiffs(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy g = gojo(h, 1, 4, null);
        face(g, h.absoluteVec(new Vec3(8, 1.6, 4)));
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_1))
                .thenIdle(JJKConfig.get().gojo.blueWindup + 2)
                .thenExecute(() -> h.assertTrue(Casters.get(g).isCasting(BlueAbility.ID), "stuck in the whiff's endlag"))
                .thenWaitUntil(() -> h.assertTrue(!Casters.get(g).isBusy(), "then free again"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 80, padding = 16, skyAccess = true)
    public void redPointBlankLaunchesAway(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 5.5, 4);
        TrainingDummy g = gojo(h, 3.5, 4, target);
        infinityOff(target);
        float hp = target.getHealth();
        Vec3 start = target.position();
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_2))
                .thenExecute(() -> release(g, AbilitySlot.SKILL_2))
                .thenIdle(JJKConfig.get().red.minCharge + 4).thenExecute(() -> {
                    h.assertTrue(target.getHealth() < hp, "Red damages");
                    h.assertTrue(target.position().distanceTo(start) > 2 || target.getDeltaMovement().length() > 0.5, "Red blasts the target away");
                    h.assertTrue(Combat.has(target, CombatStatus.LAUNCHED), "target launched");
                    h.assertTrue(Casters.get(g).cooldown(AbilitySlot.SKILL_2) > 0 || Casters.get(g).noCost(), "cooldown started");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60, padding = 16, skyAccess = true)
    public void limitlessDuringRedPhasesBehindForPointBlank(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 6.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        Casters.get(g).setNoCost(false);
        Casters.get(g).setEnergy(Casters.get(g).maxEnergy());
        Vec3 toTarget = target.position().subtract(g.position());
        float hp = target.getHealth();
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_2))
                .thenIdle(3)
                .thenExecute(() -> {
                    press(g, AbilitySlot.SKILL_5);
                    Vec3 rel = g.position().subtract(target.position());
                    h.assertTrue(rel.dot(toTarget) > 0, "Gojo phased behind the target (" + rel + ")");
                    h.assertTrue(Casters.get(g).cooldown(AbilitySlot.SKILL_5) > 0, "Limitless went on cooldown too");
                })
                .thenIdle(JJKConfig.get().red.minCharge)
                .thenExecute(() -> h.assertTrue(target.getHealth() <= hp - JJKConfig.get().red.damage * 0.6f, "point-blank Red (" + (hp - target.getHealth()) + ")"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 60, padding = 16, skyAccess = true)
    public void redOnSomeoneMidActionFreezesThemForAka(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 6.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        float hp = target.getHealth();
        h.startSequence()
                .thenExecute(() -> {
                    press(g, AbilitySlot.SKILL_2);
                    dev.rick.jjk.core.combat.Statuses.apply(target, CombatStatus.EVADING, 6); // mid-dash
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    press(g, AbilitySlot.SKILL_5);
                    h.assertTrue(Combat.has(target, CombatStatus.GRABBED), "frozen in place");
                })
                .thenWaitUntil(() -> h.assertTrue(hp - target.getHealth() >= JJKConfig.get().gojo.redInterruptDamage * 0.6f, "the enhanced Red lands"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 100, padding = 16, skyAccess = true)
    public void rapidPunchesLocksBarragesAndLaunches(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 3.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        float hp = target.getHealth();
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        boolean[] bulletArmor = new boolean[1];
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenIdle(cfg.punchesWindup + 4)
                .thenExecute(() -> h.assertTrue(Combat.has(target, CombatStatus.GRABBED), "locked in place by the kick"))
                .thenWaitUntil(() -> {
                    if (Combat.has(g, CombatStatus.BULLET_ARMOR)) bulletArmor[0] = true;
                    h.assertTrue(Combat.has(target, CombatStatus.LAUNCHED), "the final blow ragdolls them out");
                })
                .thenExecute(() -> {
                    float want = cfg.punchesGrabDamage + cfg.punchesBarrage * cfg.punchesBarrageDamage + cfg.punchesHeavy * cfg.punchesHeavyDamage + cfg.punchesFinalDamage;
                    h.assertTrue(hp - target.getHealth() >= want * 0.6f, "the whole sequence landed (" + (hp - target.getHealth()) + " of " + want + ")");
                    h.assertTrue(bulletArmor[0], "bullet i-frames during the barrage");
                    h.assertTrue(dev.rick.jjk.gojo.GojoState.of(g).faceGraterTarget(h.getLevel().getGameTime()) == target, "Face Grater is ready");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60, padding = 16, skyAccess = true)
    public void rapidPunchesCantCatchARagdoll(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 3.5, 4.5);
        infinityOff(target);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        dev.rick.jjk.core.combat.Statuses.apply(target, CombatStatus.KNOCKDOWN, 40);
        float hp = target.getHealth();
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenIdle(JJKConfig.get().gojo.punchesWindup + 3)
                .thenExecute(() -> {
                    h.assertTrue(!Combat.has(target, CombatStatus.GRABBED), "no grab on a ragdolled target");
                    h.assertTrue(target.getHealth() == hp, "and no damage");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 80, padding = 16, skyAccess = true)
    public void twofoldKickKicksTwiceAndBouncesHigher(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 3.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        float hp = target.getHealth();
        double ground = target.getY();
        JJKConfig.Gojo cfg = JJKConfig.get().gojo;
        float[] afterFirst = new float[1];
        double[] peak = new double[1];
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_4))
                .thenIdle(cfg.twofoldWindup + 2)
                .thenExecute(() -> {
                    afterFirst[0] = hp - target.getHealth();
                    h.assertTrue(afterFirst[0] > 0, "first kick lands");
                    h.assertTrue(Combat.has(g, CombatStatus.MELEE_ARMOR), "melee i-frames on hit");
                })
                .thenIdle(cfg.twofoldAnchorTicks + 2)
                .thenExecute(() -> h.assertTrue(hp - target.getHealth() > afterFirst[0], "second kick lands"))
                .thenWaitUntil(() -> {
                    peak[0] = Math.max(peak[0], target.getY() - ground);
                    h.assertTrue(peak[0] > 2.5, "bounced high (" + peak[0] + ")");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60, padding = 16, skyAccess = true)
    public void twofoldKickFinisherIsPointBlankRed(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 3.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        target.setHealth(target.getMaxHealth() * 0.15f);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_4))
                .thenWaitUntil(() -> h.assertTrue(target.isDeadOrDying(), "finished by the point-blank Red"))
                .thenSucceed();
    }

    // --- Hollow Purple ---

    @GameTest(maxTicks = 160, padding = 8)
    public void hollowPurpleErasesWallAndIgnoresInfinity(GameTestHelper h) {
        floor(h, 6);
        for (int y = 1; y < 5; y++) for (int z = 1; z < 8; z++) {
            h.setBlock(5, y, z, Blocks.STONE);
        }
        h.setBlock(5, 1, 4, Blocks.OBSIDIAN);
        TrainingDummy victim = dummy(h, 7.5, 4);
        TrainingDummy g = gojo(h, 1.5, 4, victim);
        // The victim is Gojo too, with Infinity up.
        CharacterService.assign(victim, Characters.get(GojoCharacter.ID));
        withInfinity(victim);
        face(g, h.absoluteVec(new Vec3(7.5, 2.0, 4.5)));
        float hp = victim.getHealth();
        awaken(g);
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenExecute(() -> release(g, AbilitySlot.SKILL_3))
                .thenExecute(() -> face(g, h.absoluteVec(new Vec3(7.5, 2.0, 4.5))))
                .thenWaitUntil(() -> h.assertTrue(victim.getHealth() < hp - 10 || victim.isDeadOrDying(), "Purple hit through Infinity"))
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(5, 2, 4))).isAir(), "wall erased");
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(5, 1, 4))).is(Blocks.OBSIDIAN), "obsidian is above the hardness cap");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 80)
    public void purpleInterruptedByStunRefunds(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy g = gojo(h, 4, 4, null);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        infinityOff(g);
        awaken(g);
        g.setAutoHeal(false);
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenIdle(10).thenExecute(() -> {
                    h.assertTrue(c.isCasting("hollow_purple"), "charging Purple");
                    float spent = c.energy();
                    dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.HITSTUN, 10);
                    h.assertTrue(!c.isCasting("hollow_purple"), "hitstun interrupts the cast");
                    h.assertTrue(c.energy() > spent, "energy partly refunded");
                    h.assertTrue(h.getLevel().getEntitiesOfClass(HollowPurpleEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40),
                            p -> p.owner() == g).isEmpty(), "nothing was fired");
                })
                .thenSucceed();
    }

    // --- Teleport ---

    @GameTest(maxTicks = 40)
    public void limitlessAppearsInFrontOfTarget(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 6.5, 4.5);
        TrainingDummy g = gojo(h, 0.5, 4.5, target);
        Casters.get(g).setNoCost(false); // cooldowns only run when costs do
        Casters.get(g).setEnergy(Casters.get(g).maxEnergy());
        Vec3 toTarget = target.position().subtract(g.position());
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_5))
                .thenIdle(JJKConfig.get().gojo.limitlessWindup + 1)
                .thenExecute(() -> {
                    Vec3 rel = g.position().subtract(target.position());
                    h.assertTrue(rel.dot(toTarget) < 0, "Gojo is in front of the target (" + rel + ")");
                    h.assertTrue(g.position().distanceTo(target.position()) < 3, "and close to it");
                    h.assertTrue(g.getLookAngle().dot(target.position().subtract(g.position()).normalize()) > 0.5, "facing it");
                    h.assertTrue(h.getLevel().noCollision(g), "not inside blocks");
                    h.assertTrue(Casters.get(g).cooldown(AbilitySlot.SKILL_5) > 0, "15s cooldown started");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 40)
    public void limitlessNeedsATarget(GameTestHelper h) {
        floor(h, 6);
        for (int y = 1; y < 6; y++) for (int z = -2; z < 10; z++) h.setBlock(3, y, z, Blocks.STONE);
        TrainingDummy hidden = dummy(h, 6.5, 4.5);
        TrainingDummy g = gojo(h, 1.5, 4.5, hidden);
        Vec3 before = g.position();
        AbilityCaster c = Casters.get(g);
        boolean ok = c.input(AbilitySlot.SKILL_5, true, 0, 0, null);
        h.assertTrue(!ok, "nobody in sight behind the wall: nothing happens");
        h.assertTrue(g.position().equals(before), "stayed put");
        h.succeed();
    }

    @GameTest(maxTicks = 40, padding = 16, skyAccess = true)
    public void limitlessOnAnAirborneTargetKicksThemDown(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 5.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        float hp = target.getHealth();
        h.startSequence()
                .thenExecute(() -> {
                    target.teleportTo(target.getX(), target.getY() + 4, target.getZ());
                    dev.rick.jjk.core.combat.Statuses.apply(target, CombatStatus.HOVER, 40);
                    face(g, target.getBoundingBox().getCenter());
                    press(g, AbilitySlot.SKILL_5);
                })
                .thenIdle(JJKConfig.get().gojo.limitlessWindup + 1)
                .thenExecute(() -> {
                    h.assertTrue(hp - target.getHealth() >= JJKConfig.get().gojo.limitlessAirKickDamage * 0.6f, "air kick");
                    h.assertTrue(Combat.has(target, CombatStatus.SPIKED) || target.getDeltaMovement().y < 0, "sent toward the floor");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 140, padding = 16, skyAccess = true)
    public void faceGraterFollowsRapidPunches(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 3.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        float[] hp = new float[1];
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenWaitUntil(() -> h.assertTrue(dev.rick.jjk.gojo.GojoState.of(g).faceGraterTarget(h.getLevel().getGameTime()) == target, "punches landed"))
                .thenWaitUntil(() -> h.assertTrue(!Casters.get(g).isBusy(), "punches over"))
                .thenExecute(() -> {
                    hp[0] = target.getHealth();
                    face(g, target.getBoundingBox().getCenter());
                    h.assertTrue(Casters.get(g).input(AbilitySlot.SKILL_5, true, 0, 0, null), "Limitless becomes Face Grater");
                })
                .thenWaitUntil(() -> h.assertTrue(hp[0] - target.getHealth() >= JJKConfig.get().gojo.faceGraterDamage * 0.6f, "dragged and tossed"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void dashBreaksOutOfARagdoll(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy g = gojo(h, 4, 4, null);
        AbilityCaster c = Casters.get(g);
        dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.HITSTUN, 30);
        dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.LAUNCHED, 30);
        h.assertTrue(c.input(AbilitySlot.DASH, true, 0, 0, null), "dash while ragdolled is the escape");
        h.assertTrue(!Combat.has(g, CombatStatus.LAUNCHED) && !Combat.has(g, CombatStatus.HITSTUN), "ragdoll cleared");
        h.assertTrue(Combat.has(g, CombatStatus.EVADING), "with dash i-frames");
        dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.HITSTUN, 30);
        dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.LAUNCHED, 30);
        h.assertTrue(!c.input(AbilitySlot.DASH, true, 0, 0, null), "on its own cooldown");
        dev.rick.jjk.core.combat.Statuses.remove(g, CombatStatus.LAUNCHED);
        dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.GRABBED, 30);
        h.succeed();
    }

    @GameTest(maxTicks = 100)
    public void sixEyesHealsAndLastsSixtySeconds(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy g = gojo(h, 4, 4, null);
        g.setAutoHeal(false);
        AbilityCaster c = Casters.get(g);
        g.setHealth(g.getMaxHealth() * 0.5f);
        float before = g.getHealth();
        float drain = c.character().awakeningDrainPerSecond();
        h.assertTrue(Math.abs(c.maxAwakening() / drain - 60) < 1, "the meter lasts 60 seconds (" + c.maxAwakening() / drain + ")");
        c.setAwakening(c.maxAwakening());
        h.startSequence()
                .thenExecute(() -> h.assertTrue(c.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "awakens"))
                .thenWaitUntil(() -> h.assertTrue(c.isAwakened(), "awakened"))
                .thenExecute(() -> h.assertTrue(g.getHealth() >= before + g.getMaxHealth() * 0.24f, "healed 25% (" + (g.getHealth() - before) + ")"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 200, padding = 16, skyAccess = true)
    public void zeroTwoDomainOverloadsRushesThenBurnsOut(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy victim = dummy(h, 6, 4);
        infinityOff(victim);
        victim.setAutoHeal(false);
        TrainingDummy g = gojo(h, 2, 4, victim);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        c.setAwakening(c.maxAwakening());
        float hp = victim.getHealth();
        h.startSequence()
                .thenExecute(() -> h.assertTrue(c.input(AbilitySlot.ULTIMATE, true, 0, 0, null), "awakening starts"))
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(c.input(AbilitySlot.SKILL_5, true, 0, 0, null), "the Special during the sequence"))
                .thenWaitUntil(() -> h.assertTrue(Combat.has(victim, CombatStatus.OVERLOAD), "the 0.2-second sure hit"))
                .thenWaitUntil(() -> h.assertTrue(hp - victim.getHealth() > 30 || victim.isDeadOrDying(), "the rush lands"))
                .thenWaitUntil(() -> h.assertTrue(!c.isAwakened() && !c.isBusy(), "burnt out afterwards"))
                .thenExecute(() -> {
                    h.assertTrue(c.cooldown(AbilitySlot.SKILL_1) > 0 && c.cooldown(AbilitySlot.SKILL_4) > 0, "base moveset on cooldown");
                    h.assertTrue(c.cooldown(AbilitySlot.SKILL_5) == 0, "except Limitless");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, padding = 32, skyAccess = true)
    public void redMaxReboundDeliversABlackFlash(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 6.5, 4.5);
        infinityOff(target);
        target.setAutoHeal(false);
        TrainingDummy g = gojo(h, 1.5, 4.5, target);
        awaken(g);
        float hp = target.getHealth();
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_2))
                .thenIdle(4)
                .thenExecute(() -> press(g, AbilitySlot.SKILL_5))
                .thenWaitUntil(() -> h.assertTrue(Combat.has(g, CombatStatus.MELEE_ARMOR), "the rebound pulls the target in for a Black Flash (melee i-frames)"))
                .thenIdle(1)
                .thenExecute(() -> h.assertTrue(hp - target.getHealth() >= JJKConfig.get().maxRed.farDamage + JJKConfig.get().maxRed.blackFlashDamage * 0.8f,
                        "pierced, then Black Flash (" + (hp - target.getHealth()) + ")"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, padding = 32, skyAccess = true)
    public void redMaxReboundWithNobodyHurtsGojo(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy g = gojo(h, 1.5, 4.5, null);
        g.setAutoHeal(false);
        face(g, h.absoluteVec(new Vec3(12, 1.6, 4.5)));
        awaken(g);
        float hp = g.getHealth();
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_2))
                .thenIdle(4)
                .thenExecute(() -> press(g, AbilitySlot.SKILL_5))
                .thenWaitUntil(() -> h.assertTrue(hp - g.getHealth() >= JJKConfig.get().maxRed.reboundSelfDamage * 0.6f, "the empty rebound hits Gojo"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 200, padding = 32, skyAccess = true)
    public void unlimitedPurpleFromMaxBlueAndMaxRed(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy g = gojo(h, 0.5, 4.5, null);
        awaken(g);
        TrainingDummy doomed = dummy(h, 6.5, 4.5);
        infinityOff(doomed);
        doomed.setAutoHeal(false);
        doomed.setHealth(1);
        TrainingDummy bystander = dummy(h, 7.5, 7.5);
        infinityOff(bystander);
        bystander.setAutoHeal(false);
        float hp = bystander.getHealth();
        Vec3 core = h.absoluteVec(new Vec3(6.5, 1.8, 4.5));
        BlueEntity[] blue = new BlueEntity[1];
        h.startSequence()
                .thenExecute(() -> blue[0] = BlueEntity.spawn(h.getLevel(), g, core, BlueEntity.Params.max()))
                .thenWaitUntil(() -> h.assertTrue(blue[0].isLingering(), "the kill leaves the orb lingering"))
                .thenExecute(() -> {
                    face(g, blue[0].position());
                    press(g, AbilitySlot.SKILL_2);
                })
                .thenWaitUntil(() -> h.assertTrue(blue[0].isRemoved(), "Red MAX sets it off"))
                .thenExecute(() -> h.assertTrue(Casters.get(g).awakening() == 0 || Casters.get(g).noCost(), "the whole Awakening is spent"))
                .thenWaitUntil(() -> h.assertTrue(hp - bystander.getHealth() >= JJKConfig.get().gojo.unlimitedPurpleMinDamage * 0.5f || bystander.isDeadOrDying(),
                        "the nuke goes off"))
                .thenSucceed();
    }

    // --- Domain ---

    @GameTest(maxTicks = 200, padding = 6)
    public void unlimitedVoidOverloadsThenBurnsOut(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy victim = dummy(h, 6, 4);
        TrainingDummy g = gojo(h, 3, 4, victim);
        CharacterService.assign(victim, Characters.get(GojoCharacter.ID));
        h.startSequence()
                .thenExecute(() -> {
                    awaken(g);
                    press(g, AbilitySlot.SKILL_4);
                })
                .thenWaitUntil(() -> h.assertTrue(DomainManager.ownedBy(g) != null && DomainManager.ownedBy(g).phase() == DomainInstance.Phase.ACTIVE, "domain active"))
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(Combat.has(victim, CombatStatus.OVERLOAD), "victim overloaded");
                    h.assertTrue(!Casters.get(victim).isCasting(), "victim's cast interrupted");
                    h.assertTrue(!Casters.get(victim).input(AbilitySlot.SKILL_1, true, 0, 0, null), "victim can't use techniques");
                    h.assertTrue(!Combat.has(g, CombatStatus.OVERLOAD), "owner unaffected");
                })
                .thenWaitUntil(() -> h.assertTrue(DomainManager.ownedBy(g) == null, "domain expired"))
                .thenExecute(() -> h.assertTrue(!dev.rick.jjk.core.defense.Defenses.isActive(g, "infinity"), "Infinity stops protecting immediately"))
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(Combat.has(g, CombatStatus.BURNOUT), "owner burnt out");
                    h.assertTrue(!Casters.get(g).toggled(InfinityAbility.ID), "Infinity down during burnout");
                    h.assertTrue(!Casters.get(g).input(AbilitySlot.SKILL_2, true, 0, 0, null), "no techniques during burnout");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 600, padding = 6)
    public void domainClashCollapsesOne(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy a = gojo(h, 2, 4, null);
        TrainingDummy b = gojo(h, 6, 4, null);
        DomainInstance[] da = new DomainInstance[1];
        DomainInstance[] db = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> {
                    da[0] = DomainManager.expand(a, dev.rick.jjk.gojo.UnlimitedVoid.INSTANCE);
                    db[0] = DomainManager.expand(b, dev.rick.jjk.gojo.UnlimitedVoid.INSTANCE);
                    h.assertTrue(da[0] != null && db[0] != null, "both expanded");
                    h.assertTrue(db[0].clashingWith() == da[0], "overlap starts a clash");
                })
                .thenWaitUntil(() -> h.assertTrue(!da[0].isLive() || !db[0].isLive(), "one domain lost the clash"))
                .thenExecute(() -> {
                    DomainInstance loser = da[0].isLive() ? db[0] : da[0];
                    DomainInstance winner = loser == da[0] ? db[0] : da[0];
                    h.assertValueEqual(loser.endReason(), DomainInstance.EndReason.CLASH_LOST, "loser reason");
                    h.assertTrue(winner.isLive() && winner.clashingWith() == null, "winner continues");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void domainCollapsesWhenOwnerDies(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = gojo(h, 4, 4, null);
        DomainInstance d = DomainManager.expand(g, dev.rick.jjk.gojo.UnlimitedVoid.INSTANCE);
        h.assertTrue(d != null, "expanded");
        g.kill(h.getLevel());
        h.runAfterDelay(2, () -> {
            h.assertTrue(!d.isLive(), "domain ended with its owner");
            h.succeed();
        });
    }

    // --- Edge cases ---

    @GameTest(maxTicks = 80, padding = 16, skyAccess = true, environment = "jjk-test:pull_c")
    public void targetDyingDuringBlueIsHandled(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy target = dummy(h, 7, 4);
        infinityOff(target);
        target.setAutoHeal(false);
        target.setHealth(1f);
        TrainingDummy g = gojo(h, 1, 4, target);
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_1)).thenExecute(() -> release(g, AbilitySlot.SKILL_1))
                .thenWaitUntil(() -> h.assertTrue(target.isDeadOrDying() || target.isRemoved(), "target died from Blue"))
                .thenIdle(5)
                .thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void casterRemovedMidCastCleansUp(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy g = gojo(h, 2, 4, null);
        h.startSequence()
                .thenExecute(() -> press(g, AbilitySlot.SKILL_1)).thenExecute(() -> release(g, AbilitySlot.SKILL_1))
                .thenIdle(8)
                .thenExecute(() -> awaken(g))
                .thenExecute(() -> press(g, AbilitySlot.SKILL_3))
                .thenIdle(3)
                .thenExecute(() -> g.discard())
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(ownedBlues(h, g).isEmpty(), "Blue disappears with its caster"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 20)
    public void invulnerableAndCreativeTargetsAreImmune(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy target = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, target);
        target.setPermanentlyInvulnerable(true);
        infinityOff(target);
        HitResult r = HitResolver.resolve(Hit.builder(g, "t").damage(5).tag(AttackTag.MELEE).hitstun(10).build(), target);
        h.assertTrue(r.outcome() == HitResult.Outcome.INVALID && !Combat.has(target, CombatStatus.HITSTUN), "invulnerable entity unaffected");
        var creative = h.makeMockServerPlayerInLevel();
        HitResult r2 = HitResolver.resolve(Hit.builder(g, "t").damage(5).tag(AttackTag.MELEE).build(), creative);
        h.assertValueEqual(r2.outcome(), HitResult.Outcome.INVALID, "creative player");
        creative.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 100, padding = 16, skyAccess = true)
    public void simultaneousCastersDontInterfere(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy a = gojo(h, 1.5, 4, null);
        TrainingDummy b = gojo(h, 7.5, 4, null);
        infinityOff(a);
        infinityOff(b);
        face(a, b.getBoundingBox().getCenter());
        face(b, a.getBoundingBox().getCenter());
        float ha = a.getHealth(), hb = b.getHealth();
        h.startSequence()
                .thenExecute(() -> { press(a, AbilitySlot.SKILL_2); press(b, AbilitySlot.SKILL_2); })
                .thenIdle(8).thenExecute(() -> { release(a, AbilitySlot.SKILL_2); release(b, AbilitySlot.SKILL_2); })
                .thenIdle(12).thenExecute(() -> h.assertTrue(a.getHealth() < ha && b.getHealth() < hb, "both Reds landed"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 40)
    public void playerCasterSyncsWithoutErrors(GameTestHelper h) {
        applyConfig();
        var player = h.makeMockServerPlayerInLevel();
        CharacterService.assign(player, Characters.get(GojoCharacter.ID));
        AbilityCaster c = Casters.get(player);
        h.assertTrue(c.character() != null && !c.toggled(InfinityAbility.ID), "player is Gojo (Infinity is out of the moveset)");
        c.tick();
        CharacterService.assign(player, null);
        h.assertTrue(c.character() == null && !c.toggled(InfinityAbility.ID), "unassigned cleanly");
        player.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void dashGivesEvasionFrames(GameTestHelper h) {
        floor(h, 4);
        TrainingDummy attacker = dummy(h, 3, 4);
        TrainingDummy g = gojo(h, 5, 4, attacker);
        infinityOff(g);
        press(g, AbilitySlot.DASH);
        HitResult r = HitResolver.resolve(Hit.builder(attacker, "t").damage(3).tag(AttackTag.MELEE).build(), g);
        h.assertValueEqual(r.outcome(), HitResult.Outcome.WHIFF, "dash evades");
        h.assertTrue(g.getDeltaMovement().horizontalDistance() > 0.5, "dash moves");
        h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void techniquesRespectAlliesAndOwnPets(GameTestHelper h) {
        floor(h, 2);
        TrainingDummy ally = dummy(h, 5, 4);
        TrainingDummy g = gojo(h, 3, 4, ally);
        ServerLevel level = h.getLevel();
        var team = level.getScoreboard().addPlayerTeam("jjk_test_" + level.getGameTime() % 100000);
        level.getScoreboard().addPlayerToTeam(g.getScoreboardName(), team);
        level.getScoreboard().addPlayerToTeam(ally.getScoreboardName(), team);
        HitResult r = HitResolver.resolve(Hit.builder(g, "t").damage(5).tag(AttackTag.MELEE).build(), ally);
        h.assertValueEqual(r.outcome(), HitResult.Outcome.INVALID, "teammates are not hit");
        level.getScoreboard().removePlayerTeam(team);
        h.succeed();
    }
}
