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
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.hakari.HakariCharacter;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuta.AuthenticMutualLove;
import dev.rick.jjk.yuta.Copies;
import dev.rick.jjk.yuta.DomainBladeEntity;
import dev.rick.jjk.yuta.RikaEntity;
import dev.rick.jjk.yuta.YutaCharacter;
import dev.rick.jjk.yuta.YutaState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Yuta Okkotsu and Rika / Cursed Partners: the four movesets and 90 HP, Severing Path and Veilstep, Resolute Slash's
 * Black Flash, Outburst's parry and its burst through a guard, Second Wind's second try, Rika's summon, moveset and
 * shared cooldown, a kill copying the victim's technique, True Love, the Copy Wheel and Cursed Speech, Energy Ripple's
 * Fakeout, Elbow Rush halting the Awakening's drain, Rika Throw, and Authentic Mutual Love (it breaks with nobody inside;
 * blades fall; Jacob's Ladder shatters it).
 */
public class YutaGameTests {
    private static JJKConfig testConfig;

    private static void applyConfig() {
        if (testConfig != null && JJKConfig.get() == testConfig) return;
        JJKConfig cfg = new JJKConfig();
        cfg.general.autoAssignGojo = false;
        // Shared settings match the other test classes (they share one config).
        cfg.purple.range = 14;
        cfg.purple.maxBlocksDestroyed = 400;
        cfg.domain.radius = 6;
        cfg.domain.duration = 60;
        cfg.domain.startup = 10;
        cfg.clash.notes = 8;
        cfg.clash.countdownTicks = 10;
        cfg.red.range = 12;
        cfg.hakari.domainRadius = 6;
        cfg.hakari.domainStartup = 8;
        cfg.hakari.domainDuration = 500;
        cfg.hakari.domainFormationTicks = 20;
        cfg.hakari.visualMovesRequired = 2;
        cfg.hakari.riichiTicks = 40;
        cfg.hakari.missTicks = 10;
        cfg.hakari.rainbowChance = 0;
        cfg.yuji.shrineRadius = 7;
        cfg.yuji.shrineStartup = 8;
        cfg.yuji.shrineFormationTicks = 20;
        cfg.yuji.shrineDuration = 200;
        cfg.yuji.shrineRevealTicks = 10;
        cfg.yuji.worldSlashLength = 13;
        cfg.yuji.worldSlashWidth = 5;
        // Authentic Mutual Love kept inside one test's area.
        cfg.yuta.domainRadius = 6;
        cfg.yuta.domainStartup = 8;
        cfg.yuta.domainFormationTicks = 20;
        cfg.yuta.domainDuration = 400;
        // True Love Beam kept inside one test's area.
        cfg.yuta.beamRange = 13;
        cfg.yuta.beamQuickRange = 11;
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

    private static TrainingDummy yuta(GameTestHelper h, double x, double z, LivingEntity face) {
        TrainingDummy y = dummy(h, x, z);
        y.setOnGround(true);
        CharacterService.assign(y, Characters.get(YutaCharacter.ID));
        Casters.get(y).setNoCost(true);
        if (face != null) face(y, face.getBoundingBox().getCenter());
        return y;
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

    private static boolean press(LivingEntity e, AbilitySlot slot) {
        return Casters.get(e).input(slot, true, 0, 0, null);
    }

    private static boolean press(LivingEntity e, AbilitySlot slot, LivingEntity hint) {
        return Casters.get(e).input(slot, true, 0, 0, hint);
    }

    private static void release(LivingEntity e, AbilitySlot slot) {
        Casters.get(e).input(slot, false, 0, 0, null);
    }

    private static void awaken(LivingEntity y) {
        AbilityCaster c = Casters.get(y);
        c.enterAwakening();
        YutaState.of(y).fists = true;
    }

    // --- Kits ---

    @GameTest(maxTicks = 40)
    public void fourMovesetsAndNinetyHealth(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy y = yuta(h, 2, 2, null);
        AbilityCaster c = Casters.get(y);
        AbilitySlot[] slots = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4};
        String[] base = {"severing_path", "resolute_slash", "outburst", "second_wind"};
        for (int i = 0; i < 4; i++) h.assertValueEqual(c.ability(slots[i]).id, base[i], "base slot " + i);
        h.assertValueEqual(c.ability(AbilitySlot.SKILL_5).id, "rika", "special");
        h.assertValueEqual(c.ability(AbilitySlot.ULTIMATE).id, "true_love", "awakening key");
        h.assertTrue(Math.abs(y.getMaxHealth() - 200 * 0.9f) < 0.5f, "Cursed Partners has 90% max health (" + y.getMaxHealth() + ")");
        h.assertTrue(press(y, AbilitySlot.SKILL_5), "summon Rika (" + c.lastRefusal + ")");
        h.runAfterDelay(10, () -> {
            h.assertTrue(YutaState.of(y).rika() != null, "Rika is out");
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "switch to her moveset");
            h.assertValueEqual(c.mode(), YutaCharacter.RIKA, "Rika's moveset");
            String[] rika = {"rika_smash", "rika_launch", "rika_haymaker"};
            for (int i = 0; i < 3; i++) h.assertValueEqual(c.ability(slots[i]).id, rika[i], "Rika slot " + i);
            YutaState.of(y).rikaMode = false;
            awaken(y);
            String[] awk = {"elbow_rush", "copy", "energy_ripple", "authentic_mutual_love"};
            for (int i = 0; i < 4; i++) h.assertValueEqual(c.ability(slots[i]).id, awk[i], "True Love slot " + i);
            h.assertValueEqual(c.ability(AbilitySlot.ULTIMATE).id, "copy_wheel", "the Copy Wheel on the Awakening key");
            YutaState.of(y).rikaMode = true;
            String[] awkRika = {"rika_downslam", "rika_slam", "true_love_beam", "rika_throw"};
            for (int i = 0; i < 4; i++) h.assertValueEqual(c.ability(slots[i]).id, awkRika[i], "awakened Rika slot " + i);
            CharacterService.assign(y, Characters.get(HakariCharacter.ID));
            h.assertTrue(Math.abs(y.getMaxHealth() - 200) < 0.5f, "health back to normal after switching");
            h.assertTrue(YutaState.get(y) == null || YutaState.get(y).rika() == null, "Rika leaves with him");
            h.succeed();
        });
    }

    // --- Base kit ---

    @GameTest(maxTicks = 100)
    public void severingPathLocksSwingsAndLaunches(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 5.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        boolean[] launched = new boolean[1];
        h.assertTrue(press(y, AbilitySlot.SKILL_1), "Severing Path");
        h.onEachTick(() -> launched[0] |= target.getDeltaMovement().y > 0.5);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(y).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 8, "sweep and swings landed (" + (hp - target.getHealth()) + ")");
            h.assertTrue(launched[0], "the last swing launched them");
            h.assertTrue(Combat.has(y, CombatStatus.KATANA), "the katana is out");
        });
    }

    @GameTest(maxTicks = 60)
    public void veilstepRollsBackAndLaunches(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy y = yuta(h, 8.5, 2.5, null);
        face(y, h.absoluteVec(new Vec3(12.5, 1.6, 2.5)));
        TrainingDummy behind = dummy(h, 7.0, 2.5);
        Vec3 start = y.position();
        float hp = behind.getHealth();
        h.assertTrue(Casters.get(y).input(AbilitySlot.SKILL_1, true, -1, 0, null), "Severing Path walking backward: Veilstep");
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(y).isBusy(), "finished");
            h.assertTrue(y.position().distanceTo(start) > 4, "rolled well back (" + y.position().distanceTo(start) + ")");
            h.assertTrue(hp - behind.getHealth() >= 8, "whoever was in the way was hit (" + (hp - behind.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 80)
    public void resoluteSlashUsedAgainIsABlackFlash(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 6.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        h.assertTrue(press(y, AbilitySlot.SKILL_2, target), "Resolute Slash");
        h.runAfterDelay(JJKConfig.get().yuta.resoluteVanishTicks + 1, () -> h.assertTrue(press(y, AbilitySlot.SKILL_2, target),
                "pressed again as he reappears (" + Casters.get(y).lastRefusal + ")"));
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(y).isBusy(), "finished");
            h.assertTrue(!y.isInvisible(), "visible again");
            h.assertTrue(y.distanceTo(target) < 4.5, "reappeared by the target");
            h.assertTrue(hp - target.getHealth() >= 12, "the Black Flash landed (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 40)
    public void outburstParriesMeleeOnAShortCooldown(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy attacker = dummy(h, 3.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, attacker);
        AbilityCaster c = Casters.get(y);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        // Rika's cooldowns, to see two seconds come off them.
        c.startCooldown(AbilitySlot.SKILL_1, YutaCharacter.RIKA, 200);
        h.assertTrue(press(y, AbilitySlot.SKILL_3), "Outburst");
        h.runAfterDelay(2, () -> {
            Hit punch = Hit.builder(attacker, "test_punch").damage(6).tag(AttackTag.MELEE).origin(attacker.getEyePosition()).knockback(Knockback.NONE).build();
            HitResult r = HitResolver.resolve(punch, y);
            h.assertValueEqual(r.outcome(), HitResult.Outcome.NEGATED, "the swing parries it");
        });
        h.runAfterDelay(8, () -> {
            h.assertTrue(Combat.has(attacker, CombatStatus.GUARD_BROKEN), "the attacker is pushed off stunned");
            int cd = c.cooldown(AbilitySlot.SKILL_3);
            h.assertTrue(cd > 0 && cd <= JJKConfig.get().yuta.outburstParryCooldown, "a short cooldown (" + cd + ")");
            h.assertTrue(c.cooldown(AbilitySlot.SKILL_1, YutaCharacter.RIKA) <= 200 - JJKConfig.get().yuta.outburstRikaRefund,
                    "Rika's cooldowns skipped ahead (" + c.cooldown(AbilitySlot.SKILL_1, YutaCharacter.RIKA) + ")");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void outburstBurstsThroughAGuardButCannotKill(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy target = dummy(h, 3.3, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        face(target, y.getBoundingBox().getCenter());
        Combat.state(target).startGuard(h.getLevel().getGameTime() - 40);
        target.setHealth(3f);
        h.assertTrue(press(y, AbilitySlot.SKILL_3), "Outburst");
        h.runAfterDelay(1, () -> release(y, AbilitySlot.SKILL_3));
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(y).isBusy(), "finished");
            h.assertTrue(target.isAlive(), "a guard keeps them alive");
            h.assertTrue(target.getHealth() <= 1.01f, "but the burst's damage went through it (" + target.getHealth() + ")");
        });
    }

    @GameTest(maxTicks = 80)
    public void secondWindGetsASecondTryAfterAWhiff(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy y = yuta(h, 1.5, 2.5, null);
        face(y, h.absoluteVec(new Vec3(12.5, 1.6, 2.5)));
        AbilityCaster c = Casters.get(y);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        h.assertTrue(press(y, AbilitySlot.SKILL_4), "Second Wind");
        boolean[] second = new boolean[1];
        h.succeedWhen(() -> {
            if (!second[0]) {
                h.assertTrue(!c.isBusy(), "first try over");
                h.assertTrue(c.isReady(AbilitySlot.SKILL_4), "no cooldown after a whiff");
                h.assertTrue(YutaState.of(y).secondWindRetry, "the second try is ready");
                h.assertTrue(press(y, AbilitySlot.SKILL_4), "second try (" + c.lastRefusal + ")");
                second[0] = true;
                h.fail("waiting for the second try");
            }
            h.assertTrue(!c.isBusy(), "second try over");
            h.assertTrue(!c.isReady(AbilitySlot.SKILL_4), "its cooldown runs after the second try");
        });
    }

    // --- Rika ---

    @GameTest(maxTicks = 100)
    public void rikaSmashPutsHerMovesetAwayAndSharesItsCooldown(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 5.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(y);
        float hp = target.getHealth();
        h.assertTrue(press(y, AbilitySlot.SKILL_5, target), "summon Rika");
        h.runAfterDelay(10, () -> {
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "her moveset");
            h.assertTrue(press(y, AbilitySlot.SKILL_1, target), "Rika Smash (" + c.lastRefusal + ")");
            h.assertValueEqual(c.mode(), 0, "back to his own moveset");
            for (AbilitySlot s : new AbilitySlot[] {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3}) {
                h.assertTrue(c.cooldown(s, YutaCharacter.RIKA) > 0, "her whole moveset is on cooldown (" + s + ")");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(hp - target.getHealth() >= 9, "the smash landed (" + (hp - target.getHealth()) + ")");
            RikaEntity r = YutaState.of(y).rika();
            h.assertTrue(r != null && !r.has(RikaEntity.BUSY), "she's free again");
        });
    }

    @GameTest(maxTicks = 100)
    public void aRikaKillGivesYutaTheirTechnique(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy gojo = dummy(h, 5.0, 2.5);
        CharacterService.assign(gojo, Characters.get(GojoCharacter.ID));
        gojo.setHealth(gojo.getMaxHealth() * 0.1f);
        TrainingDummy y = yuta(h, 1.5, 2.5, gojo);
        h.assertTrue(press(y, AbilitySlot.SKILL_5, gojo), "summon Rika");
        h.runAfterDelay(10, () -> {
            press(y, AbilitySlot.SKILL_5);
            h.assertTrue(press(y, AbilitySlot.SKILL_1, gojo), "Rika Smash");
        });
        h.succeedWhen(() -> {
            h.assertTrue(!gojo.isAlive(), "finished off");
            h.assertTrue(YutaState.of(y).copied.contains(Copies.LIMITLESS), "Limitless copied (" + YutaState.of(y).copied + ")");
        });
    }

    // --- True Love ---

    @GameTest(maxTicks = 100)
    public void trueLoveManifestsRikaFullyAndWrapsTheSteelArm(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy y = yuta(h, 3, 3, null);
        AbilityCaster c = Casters.get(y);
        y.setHealth(50);
        h.assertTrue(press(y, AbilitySlot.ULTIMATE), "True Love (" + c.lastRefusal + ")");
        h.succeedWhen(() -> {
            h.assertTrue(c.isAwakened(), "awakened");
            h.assertTrue(!c.isBusy(), "finished");
            RikaEntity r = YutaState.of(y).rika();
            h.assertTrue(r != null && r.has(RikaEntity.FULL), "Rika fully manifested");
            h.assertTrue(Combat.has(y, CombatStatus.STEEL_ARM), "the steel casing on his arm");
            h.assertTrue(y.getHealth() > 50, "healed");
        });
    }

    @GameTest(maxTicks = 60)
    public void copyWheelPicksCursedSpeechAndItStuns(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 5.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        awaken(y);
        YutaState s = YutaState.of(y);
        h.assertTrue(press(y, AbilitySlot.ULTIMATE), "open the Copy Wheel");
        h.assertTrue(s.wheelOpen, "the wheel is open");
        h.assertTrue(press(y, AbilitySlot.SKILL_1), "pick its first technique");
        h.assertTrue(!s.wheelOpen && Copies.SPEECH.equals(s.selected), "Cursed Speech selected, the wheel closed");
        h.assertTrue(press(y, AbilitySlot.SKILL_2), "Copy (" + Casters.get(y).lastRefusal + ")");
        h.runAfterDelay(14, () -> {
            h.assertTrue(Combat.has(target, CombatStatus.HITSTUN), "\"Don't move!\"");
            h.assertTrue(s.copyReadyAt.getOrDefault(Copies.SPEECH, 0L) > h.getLevel().getGameTime(), "Cursed Speech is on its own cooldown");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void energyRippleUsedAgainIsTheFakeout(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        awaken(y);
        float hp = target.getHealth();
        h.assertTrue(press(y, AbilitySlot.SKILL_3), "Energy Ripple");
        h.runAfterDelay(4, () -> h.assertTrue(press(y, AbilitySlot.SKILL_3), "used again before the blade lands"));
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(y).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 18, "the swing and the burst (" + (hp - target.getHealth()) + ")");
            h.assertTrue(!YutaState.of(y).fists, "the katana again afterward");
        });
    }

    @GameTest(maxTicks = 120)
    public void elbowRushHaltsTheAwakeningsDrain(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy target = dummy(h, 6.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        awaken(y);
        AbilityCaster c = Casters.get(y);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        c.setAwakening(c.maxAwakening());
        float hp = target.getHealth();
        float[] meter = new float[1];
        h.assertTrue(press(y, AbilitySlot.SKILL_1), "Elbow Rush (" + c.lastRefusal + ")");
        meter[0] = c.awakening();
        h.succeedWhen(() -> {
            h.assertTrue(!c.isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 12, "elbow, barrage and the last blow (" + (hp - target.getHealth()) + ")");
            h.assertTrue(c.awakening() >= meter[0] - 1f, "the meter didn't drain meanwhile (" + meter[0] + " -> " + c.awakening() + ")");
        });
    }

    @GameTest(maxTicks = 120)
    public void rikaThrowCrashesHimIntoThem(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy target = dummy(h, 9.5, 2.5);
        TrainingDummy y = yuta(h, 1.5, 2.5, target);
        awaken(y);
        float hp = target.getHealth();
        h.assertTrue(press(y, AbilitySlot.SKILL_5), "Rika (fully manifested: she comes out)");
        h.runAfterDelay(10, () -> {
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "her moveset");
            face(y, target.getBoundingBox().getCenter());
            h.assertTrue(press(y, AbilitySlot.SKILL_4), "Rika Throw (" + Casters.get(y).lastRefusal + ")");
        });
        h.succeedWhen(() -> h.assertTrue(hp - target.getHealth() >= 8, "he crashed into them (" + (hp - target.getHealth()) + ")"));
    }

    // --- True Love Beam ---

    /** Awakened, Rika out and her moveset up: the beam is on skill 3. */
    private static void rikaAwakened(GameTestHelper h, TrainingDummy y, Runnable then) {
        awaken(y);
        h.assertTrue(press(y, AbilitySlot.SKILL_5), "Rika (fully manifested: she comes out)");
        h.runAfterDelay(10, () -> {
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "her moveset");
            then.run();
        });
    }

    @GameTest(maxTicks = 260)
    public void trueLoveBeamHitsExactlyWhatItDrawsOnce(GameTestHelper h) {
        floor(h, 16);
        JJKConfig.Yuta cfg = JJKConfig.get().yuta;
        TrainingDummy onAxis = dummy(h, 3.5, 13.5);
        TrainingDummy edge = dummy(h, 4.7, 13.5);      // 1.2 off the axis: inside its 3-block body
        TrainingDummy wide = dummy(h, 7.5, 13.5);      // 4 off: outside it
        TrainingDummy behind = dummy(h, 3.5, 0.5);     // behind Yuta and Rika
        TrainingDummy y = yuta(h, 3.5, 4.5, onAxis);
        TrainingDummy[] all = {onAxis, edge, wide, behind};
        float[] hp = new float[4];
        long[] pressed = {-1};
        rikaAwakened(h, y, () -> {
            face(y, onAxis.getBoundingBox().getCenter());
            // Health from the moment it is cast (Rika coming out can jostle whoever stands behind him).
            for (int i = 0; i < 4; i++) hp[i] = all[i].getHealth();
            h.assertTrue(press(y, AbilitySlot.SKILL_3), "True Love Beam (" + Casters.get(y).lastRefusal + ")");
            pressed[0] = h.getTick();
        });
        h.onEachTick(() -> {
            if (pressed[0] < 0) return;
            long t = h.getTick() - pressed[0];
            // Nothing is hit before it fires (the charge is only a telegraph).
            // (Only the beam's scale of damage counts: Rika planting herself beside someone can jostle them a little.)
            if (t < cfg.beamWindup) for (int i = 0; i < 4; i++) h.assertTrue(hp[i] - all[i].getHealth() < 5, "hit before it fired: " + i + " by " + (all[i].getLastDamageSource() == null ? "?" : all[i].getLastDamageSource().getMsgId() + "/" + all[i].getLastDamageSource().getEntity()) + " t=" + t);
            // Rika is planted behind him while she charges it.
            if (t == cfg.beamConjureTicks + 4) {
                RikaEntity r = YutaState.of(y).rika();
                h.assertTrue(r != null && r.position().distanceTo(y.position()) < 2.5, "Rika planted behind him");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pressed[0] >= 0 && h.getTick() - pressed[0] > cfg.beamWindup + cfg.beamTicks + 2, "the beam is over");
            float a = hp[0] - onAxis.getHealth(), b = hp[1] - edge.getHealth();
            h.assertTrue(a > 50 && a <= cfg.beamDamage + 0.5f, "on the axis: hit once for its full damage (" + a + ")");
            h.assertTrue(b > 50 && b <= cfg.beamDamage + 0.5f, "inside its radius: hit once (" + b + ")");
            h.assertTrue(hp[2] - wide.getHealth() < 5, "outside its radius: untouched by it (" + (hp[2] - wide.getHealth()) + ")");
            h.assertTrue(hp[3] - behind.getHealth() < 5, "behind: untouched by it (" + (hp[3] - behind.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 240)
    public void trueLoveBeamIsCancelledIfYutaDiesInTheWindup(GameTestHelper h) {
        floor(h, 16);
        JJKConfig.Yuta cfg = JJKConfig.get().yuta;
        TrainingDummy target = dummy(h, 2.5, 11.5);
        TrainingDummy y = yuta(h, 2.5, 2.5, target);
        float hp = target.getHealth();
        long[] pressed = {-1};
        rikaAwakened(h, y, () -> {
            face(y, target.getBoundingBox().getCenter());
            h.assertTrue(press(y, AbilitySlot.SKILL_3), "True Love Beam");
            pressed[0] = h.getTick();
        });
        h.onEachTick(() -> {
            if (pressed[0] >= 0 && h.getTick() - pressed[0] == cfg.beamConjureTicks + 6 && y.isAlive()) {
                y.kill((net.minecraft.server.level.ServerLevel) h.getLevel());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pressed[0] >= 0 && h.getTick() - pressed[0] > cfg.beamWindup + cfg.beamTicks + 4, "past when it would have fired");
            h.assertValueEqual(target.getHealth(), hp, "no beam once he was dead");
        });
    }

    @GameTest(maxTicks = 120)
    public void trueLoveBeamPressedAgainIsTheQuickBeam(GameTestHelper h) {
        floor(h, 16);
        JJKConfig.Yuta cfg = JJKConfig.get().yuta;
        TrainingDummy target = dummy(h, 2.5, 10.5);
        TrainingDummy y = yuta(h, 2.5, 2.5, target);
        float hp = target.getHealth();
        long[] pressed = {-1};
        rikaAwakened(h, y, () -> {
            face(y, target.getBoundingBox().getCenter());
            h.assertTrue(press(y, AbilitySlot.SKILL_3), "True Love Beam");
            pressed[0] = h.getTick();
            h.runAfterDelay(6, () -> {
                face(y, target.getBoundingBox().getCenter());
                h.assertTrue(press(y, AbilitySlot.SKILL_3), "pressed again in the wind-up");
            });
        });
        h.succeedWhen(() -> {
            float dealt = hp - target.getHealth();
            h.assertTrue(pressed[0] >= 0 && h.getTick() - pressed[0] < cfg.beamWindup, "it fired long before the full beam would");
            h.assertTrue(Math.abs(dealt - cfg.beamQuickDamage) < 0.6f, "the quick beam's damage, once (" + dealt + ")");
            int cd = Casters.get(y).cooldown(AbilitySlot.SKILL_3);
            h.assertTrue(cd > 0 && cd <= cfg.beamQuickCooldown, "on the quick beam's shorter cooldown (" + cd + ")");
        });
    }

    // --- Authentic Mutual Love ---

    @GameTest(maxTicks = 120)
    public void authenticMutualLoveBreaksWithNobodyInside(GameTestHelper h) {
        floor(h, 18);
        TrainingDummy y = yuta(h, 8, 8, null);
        awaken(y);
        boolean[] opened = new boolean[1];
        h.assertTrue(press(y, AbilitySlot.SKILL_4), "Authentic Mutual Love");
        h.onEachTick(() -> opened[0] |= DomainManager.ownedBy(y) != null);
        h.succeedWhen(() -> {
            h.assertTrue(opened[0], "it opened");
            h.assertTrue(DomainManager.ownedBy(y) == null, "and broke at once with no enemy inside");
        });
    }

    @GameTest(maxTicks = 260)
    public void bladesFallAndJacobsLadderShattersTheDomain(GameTestHelper h) {
        floor(h, 18);
        TrainingDummy target = dummy(h, 9.5, 8.5);
        TrainingDummy y = yuta(h, 6.5, 8.5, target);
        awaken(y);
        CharacterService.assign(target, Characters.get(HakariCharacter.ID));
        AbilityCaster tc = Casters.get(target);
        tc.setAwakening(tc.maxAwakening());
        float hp = target.getHealth();
        h.assertTrue(press(y, AbilitySlot.SKILL_4), "Authentic Mutual Love");
        boolean[] ladder = new boolean[1];
        long[] pressedAt = {0};
        int impact = 14 + 6 + JJKConfig.get().yuta.ladderTicks;
        h.succeedWhen(() -> {
            var d = DomainManager.ownedBy(y);
            if (!ladder[0]) {
                h.assertTrue(d != null && d.definition == AuthenticMutualLove.INSTANCE && d.isLive(), "the domain is up");
                AABB area = new AABB(d.center, d.center).inflate(d.radius + 2);
                int blades = h.getLevel().getEntitiesOfClass(DomainBladeEntity.class, area, b -> b.domainId() == d.id).size();
                h.assertTrue(blades >= 1, "blades fall inside it");
                // Four direct katana hits: Jacob's Ladder is ready.
                YutaState.of(y).ladderHits = JJKConfig.get().yuta.ladderHits;
                face(y, target.getBoundingBox().getCenter());
                h.assertTrue(press(y, AbilitySlot.SKILL_4, target), "Jacob's Ladder (" + Casters.get(y).lastRefusal + ")");
                ladder[0] = true;
                pressedAt[0] = h.getTick();
                h.fail("waiting for the ladder");
            }
            long t = h.getTick() - pressedAt[0];
            // One blow at the end of the lift: nothing before it, and the domain holds until then.
            if (t < impact - 1) {
                h.assertValueEqual(target.getHealth(), hp, "no damage before the final blow (t=" + t + ")");
                h.assertTrue(DomainManager.ownedBy(y) != null, "the domain holds through the lift");
                h.fail("lifting");
            }
            h.assertTrue(DomainManager.ownedBy(y) == null, "the domain shattered");
            h.assertTrue(!Casters.get(y).isBusy(), "the ladder is over");
            float dealt = hp - target.getHealth();
            h.assertTrue(!target.isAlive() || Math.abs(dealt - JJKConfig.get().yuta.ladderDamage) < 0.6f, "one blow of its damage (" + dealt + ")");
            float left = tc.awakening() / tc.maxAwakening();
            h.assertTrue(Math.abs(left - (1 - JJKConfig.get().yuta.ladderDrain)) < 0.02f, "and its share of their Awakening meter, once (" + left + ")");
        });
    }
}
