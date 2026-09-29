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
import dev.rick.jjk.core.combat.Knockback;
import dev.rick.jjk.core.combat.melee.MeleeSystem;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.net.MeleeInputPayload;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.hakari.HakariCharacter;
import dev.rick.jjk.registry.ModDamageTypes;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuji.DismantleAbility;
import dev.rick.jjk.yuji.MalevolentShrine;
import dev.rick.jjk.yuji.YujiCharacter;
import dev.rick.jjk.yuji.YujiState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Yuji Itadori / Vessel: both kits, 85 HP, every base move and its variants (Cursed Strikes and its dive, Crushing Blow and
 * its shockwave, Divergent Fist's delayed impact, the Black Flash and its chain, Manji Kick against melee and bullets,
 * Combat Instincts' feints and throw), King of Curses, Shrine's ranged M1s, Cleave, Dismantle, World Cutting Slash, Open,
 * Rush and Malevolent Shrine.
 */
public class YujiGameTests {
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
        // Kept inside one test's area.
        cfg.yuji.worldSlashLength = 13;
        cfg.yuji.worldSlashWidth = 5;
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

    private static TrainingDummy yuji(GameTestHelper h, double x, double z, LivingEntity face) {
        TrainingDummy g = dummy(h, x, z);
        // Standing on the floor from the first tick, so the grounded variants are the ones used.
        g.setOnGround(true);
        CharacterService.assign(g, Characters.get(YujiCharacter.ID));
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

    private static boolean press(LivingEntity e, AbilitySlot slot) {
        return Casters.get(e).input(slot, true, 0, 0, null);
    }

    private static boolean press(LivingEntity e, AbilitySlot slot, LivingEntity hint) {
        return Casters.get(e).input(slot, true, 0, 0, hint);
    }

    // --- Kits ---

    @GameTest(maxTicks = 5)
    public void kitsAndVesselHealth(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy g = yuji(h, 2, 2, null);
        AbilityCaster c = Casters.get(g);
        String[] base = {"cursed_strikes", "crushing_blow", "divergent_fist", "manji_kick", "combat_instincts"};
        String[] awk = {"dismantle", "open", "rush", "malevolent_shrine", "cleave"};
        AbilitySlot[] slots = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4, AbilitySlot.SKILL_5};
        for (int i = 0; i < 5; i++) h.assertValueEqual(c.ability(slots[i]).id, base[i], "base slot " + i);
        h.assertValueEqual(c.ability(AbilitySlot.ULTIMATE).id, "king_of_curses", "awakening key");
        c.enterAwakening();
        for (int i = 0; i < 5; i++) h.assertValueEqual(c.ability(slots[i]).id, awk[i], "King of Curses slot " + i);
        h.assertTrue(Math.abs(g.getMaxHealth() - 200 * 0.85f) < 0.5f, "Vessel has 85% max health (" + g.getMaxHealth() + ")");
        CharacterService.assign(g, Characters.get(HakariCharacter.ID));
        h.assertTrue(Math.abs(g.getMaxHealth() - 200) < 0.5f, "health back to normal after switching (" + g.getMaxHealth() + ")");
        h.succeed();
    }

    // --- Base kit ---

    @GameTest(maxTicks = 120)
    public void cursedStrikesSlidesFlurriesAndKicks(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        c.melee.setChainIndex(2, h.getLevel().getGameTime());
        float hp = target.getHealth();
        h.assertTrue(press(g, AbilitySlot.SKILL_1), "Cursed Strikes");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 13, "grab, flurry and calf kick landed (" + (hp - target.getHealth()) + ")");
            h.assertValueEqual(c.melee.chainIndex(), 2, "his M1 count was kept");
            h.assertTrue(!c.input(AbilitySlot.DASH, true, 1, 0, null) && "front_dash_locked".equals(c.lastRefusal)
                    || h.getLevel().getGameTime() >= YujiState.of(g).frontDashLockedUntil, "front dash shut off for a moment (" + c.lastRefusal + ")");
        });
    }

    @GameTest(maxTicks = 100)
    public void cursedStrikesBlockedCutsTheSlideShort(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 5.5, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        face(target, g.getBoundingBox().getCenter());
        Combat.state(target).startGuard(h.getLevel().getGameTime() - 40);
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_1);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "cut short");
            h.assertTrue(hp - target.getHealth() < 2, "no flurry through the guard");
        });
    }

    @GameTest(maxTicks = 100)
    public void airCursedStrikesDropkicksToTheGround(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 6.5, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, null);
        g.teleportTo(h.absoluteVec(new Vec3(1.5, 5, 2.5)).x, h.absoluteVec(new Vec3(1.5, 5, 2.5)).y, h.absoluteVec(new Vec3(1.5, 5, 2.5)).z);
        face(g, target.position());
        float hp = target.getHealth();
        boolean[] spiked = new boolean[1];
        h.runAfterDelay(1, () -> h.assertTrue(press(g, AbilitySlot.SKILL_1), "aerial Cursed Strikes (" + Casters.get(g).lastRefusal + ")"));
        h.onEachTick(() -> spiked[0] |= Combat.has(target, CombatStatus.SPIKED) || Combat.has(target, CombatStatus.KNOCKDOWN));
        h.succeedWhen(() -> {
            h.assertTrue(hp - target.getHealth() >= 12, "the dropkick landed (" + (hp - target.getHealth()) + ")");
            h.assertTrue(spiked[0], "grounded");
        });
    }

    @GameTest(maxTicks = 100)
    public void crushingBlowSlamsTwiceAndLaunches(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        boolean[] launched = new boolean[1];
        press(g, AbilitySlot.SKILL_2);
        h.onEachTick(() -> launched[0] |= target.getDeltaMovement().y > 0.4);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 10, "two slams (" + (hp - target.getHealth()) + ")");
            h.assertTrue(launched[0], "flung into the sky");
        });
    }

    @GameTest(maxTicks = 60)
    public void crushingBlowWhiffShockwave(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 5.3, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_2);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            float lost = hp - target.getHealth();
            h.assertTrue(lost >= 2 && lost < 5, "only the shockwave's 3 (" + lost + ")");
        });
    }

    @GameTest(maxTicks = 60)
    public void divergentFistPunchThenDelayedImpact(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_3);
        float[] afterPunch = new float[1];
        h.runAfterDelay(JJKConfig.get().yuji.divergentWindup + 2, () -> afterPunch[0] = target.getHealth());
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(hp - afterPunch[0] >= 4, "the punch (" + (hp - afterPunch[0]) + ")");
            h.assertTrue(afterPunch[0] - target.getHealth() >= 4, "the cursed energy a beat later (" + (afterPunch[0] - target.getHealth()) + ")");
        });
    }

    /** Black Flash on the target's back, four times: each keeps Divergent Fist ready, the fourth ends it on cooldown. */
    @GameTest(maxTicks = 260)
    public void blackFlashChainsFromBehind(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 4.3, 2.5);
        TrainingDummy g = yuji(h, 2.0, 2.5, target);
        // Facing away: Yuji is at their back.
        target.setYRot(g.getYRot());
        target.setYHeadRot(g.getYRot());
        target.setYBodyRot(g.getYRot());
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        JJKConfig.Yuji cfg = JJKConfig.get().yuji;
        var seq = h.startSequence();
        for (int i = 1; i <= 4; i++) {
            int n = i;
            seq.thenExecute(() -> {
                g.teleportTo(target.getX() - 1.6 * target.getLookAngle().x, target.getY(), target.getZ() - 1.6 * target.getLookAngle().z);
                face(g, target.getBoundingBox().getCenter());
                target.setYRot(g.getYRot());
                target.setYBodyRot(g.getYRot());
                target.setYHeadRot(g.getYRot());
                h.assertTrue(press(g, AbilitySlot.SKILL_3), "Divergent Fist " + n + " (" + c.lastRefusal + ")");
            }).thenIdle(cfg.blackFlashWindowStart + 1).thenExecute(() -> h.assertTrue(press(g, AbilitySlot.SKILL_3), "Black Flash timing " + n))
                    .thenWaitUntil(() -> h.assertTrue(!c.isBusy(), "punch " + n + " done")).thenExecute(() -> {
                        if (n < 4) {
                            h.assertValueEqual(YujiState.of(g).chain, n, "chain count");
                            h.assertTrue(c.isReady(AbilitySlot.SKILL_3), "Divergent Fist stays off cooldown in the chain");
                            h.assertTrue(!Combat.has(target, CombatStatus.LAUNCHED), "stunned, not ragdolled");
                            target.setDeltaMovement(Vec3.ZERO);
                            target.teleportTo(h.absoluteVec(new Vec3(4.3, 1, 2.5)).x, h.absoluteVec(new Vec3(4.3, 1, 2.5)).y, h.absoluteVec(new Vec3(4.3, 1, 2.5)).z);
                        } else {
                            h.assertValueEqual(YujiState.of(g).chain, 0, "the fourth ends it");
                            h.assertTrue(!c.isReady(AbilitySlot.SKILL_3), "and Divergent Fist goes on cooldown");
                            h.assertTrue(target.position().distanceTo(h.absoluteVec(new Vec3(4.3, 1, 2.5))) > 2, "the heavy one blasts them away");
                        }
                    });
        }
        seq.thenSucceed();
    }

    @GameTest(maxTicks = 60)
    public void blackFlashFromTheFrontBlastsAway(GameTestHelper h) {
        floor(h, 12);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        face(target, g.getBoundingBox().getCenter());
        Casters.get(g).setNoCost(false);
        Casters.get(g).setEnergy(Casters.get(g).maxEnergy());
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_3);
        h.runAfterDelay(JJKConfig.get().yuji.blackFlashWindowStart + 1, () -> press(g, AbilitySlot.SKILL_3));
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 9, "Black Flash (" + (hp - target.getHealth()) + ")");
            h.assertTrue(!Casters.get(g).isReady(AbilitySlot.SKILL_3), "on cooldown: no chain from the front");
        });
    }

    @GameTest(maxTicks = 60)
    public void manjiKickCountersMelee(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy attacker = dummy(h, 3.5, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, attacker);
        press(g, AbilitySlot.SKILL_4);
        float gHp = g.getHealth(), aHp = attacker.getHealth();
        h.runAfterDelay(3, () -> HitResolver.resolve(Hit.builder(attacker, "test").type(ModDamageTypes.MELEE).damage(6).tag(AttackTag.MELEE)
                .origin(attacker.getEyePosition()).knockback(Knockback.NONE).hitstun(10).build(), g));
        h.runAfterDelay(30, () -> {
            h.assertTrue(g.getHealth() >= gHp - 0.01f, "the punch was countered (" + g.getHealth() + ")");
            h.assertTrue(aHp - attacker.getHealth() >= 8, "roundhouse kick (" + (aHp - attacker.getHealth()) + ")");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 70)
    public void manjiKickDodgesBulletsAndSwoops(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy shooter = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 0.5, 2.5, shooter);
        press(g, AbilitySlot.SKILL_4);
        float gHp = g.getHealth(), sHp = shooter.getHealth();
        h.runAfterDelay(3, () -> HitResolver.resolve(Hit.builder(shooter, "test").type(ModDamageTypes.TECHNIQUE).damage(6).tag(AttackTag.PROJECTILE)
                .origin(shooter.getEyePosition()).knockback(Knockback.NONE).hitstun(10).build(), g));
        h.succeedWhen(() -> {
            h.assertTrue(g.getHealth() >= gHp - 0.01f, "the bullet was dodged");
            h.assertTrue(sHp - shooter.getHealth() >= 8, "swooped in on the shooter (" + (sHp - shooter.getHealth()) + ", " + g.distanceTo(shooter) + ")");
        });
    }

    @GameTest(maxTicks = 40)
    public void combatInstinctsFeintsASkill(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        c.setAwakening(50);
        float hp = target.getHealth();
        h.assertTrue(press(g, AbilitySlot.SKILL_2), "Crushing Blow");
        h.runAfterDelay(2, () -> {
            h.assertTrue(press(g, AbilitySlot.SKILL_5), "Combat Instincts (" + c.lastRefusal + ")");
            h.assertTrue(!c.isBusy(), "no endlag");
            h.assertTrue(c.isReady(AbilitySlot.SKILL_2), "Crushing Blow stays off cooldown");
            h.assertTrue(!c.isReady(AbilitySlot.SKILL_5), "the Special's 2s");
            h.assertTrue(c.awakening() < 50 && c.awakening() > 45, "3% of the meter (" + c.awakening() + ")");
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(target.getHealth() >= hp, "nothing landed");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void combatInstinctsFeintsAnM1(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.4, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        float hp = target.getHealth();
        MeleeSystem.handleInput(g, c, MeleeInputPayload.LIGHT, 0, target.getId());
        h.assertTrue(c.melee.inStartup(), "M1 winding up");
        h.assertTrue(press(g, AbilitySlot.SKILL_5), "feint (" + c.lastRefusal + ")");
        h.assertTrue(c.melee.current() == null, "the M1 is gone");
        h.runAfterDelay(8, () -> {
            h.assertTrue(target.getHealth() >= hp, "and never landed");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void combatInstinctsHurlsAThrowable(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 0.5, 2.5, target);
        h.setBlock(1, 1, 2, Blocks.BARREL);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setAwakening(0);
        h.assertTrue(!press(g, AbilitySlot.SKILL_5) && "awakening".equals(c.lastRefusal), "the throw needs the 3% (" + c.lastRefusal + ")");
        c.setAwakening(20);
        float hp = target.getHealth();
        h.assertTrue(press(g, AbilitySlot.SKILL_5), "punched the barrel (" + c.lastRefusal + ")");
        h.succeedWhen(() -> h.assertTrue(hp - target.getHealth() >= 13, "hit by the barrel (" + (hp - target.getHealth()) + ")"));
    }

    // --- King of Curses ---

    @GameTest(maxTicks = 80)
    public void kingOfCursesTakesOverAndHeals(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy g = yuji(h, 2, 2, null);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setAwakening(c.maxAwakening());
        g.setHealth(40);
        h.assertTrue(press(g, AbilitySlot.ULTIMATE), "King of Curses (" + c.lastRefusal + ")");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isBusy() && c.isAwakened(), "Sukuna has taken over");
            h.assertTrue(Combat.has(g, CombatStatus.SUKUNA), "his marks, for everyone");
            float want = 40 + g.getMaxHealth() * 45f / 85f;
            h.assertTrue(Math.abs(g.getHealth() - Math.min(g.getMaxHealth(), want)) < 2, "45 of 85 HP healed (" + g.getHealth() + ")");
        });
    }

    @GameTest(maxTicks = 40)
    public void shrineM1sReachThreeTimesAsFar(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 0.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        float hp = target.getHealth();
        MeleeSystem.handleInput(g, c, MeleeInputPayload.LIGHT, 0, -1);
        h.runAfterDelay(8, () -> {
            h.assertTrue(target.getHealth() >= hp, "base M1s don't reach");
            c.enterAwakening();
            c.melee.reset();
            MeleeSystem.handleInput(g, c, MeleeInputPayload.LIGHT, 0, -1);
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(target.getHealth() < hp, "a Shrine slash from 7 blocks away");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void cleaveTakesFortyPercent(GameTestHelper h) {
        floor(h, 10);
        TrainingDummy target = dummy(h, 3.8, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        Casters.get(g).enterAwakening();
        target.setHealth(150);
        press(g, AbilitySlot.SKILL_5);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(Math.abs(target.getHealth() - 90) < 1.5f, "40% of 150 (" + target.getHealth() + ")");
        });
    }

    @GameTest(maxTicks = 60)
    public void dismantleSlashesFromRange(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 0.5, 2.5, target);
        Casters.get(g).enterAwakening();
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_1, target);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 14, "the barrage (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 160)
    public void worldCuttingSlashChantsAndCutsTheWorld(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 0.5, 2.5, target);
        AbilityCaster c = Casters.get(g);
        c.setNoCost(false);
        c.setEnergy(c.maxEnergy());
        c.enterAwakening();
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_1, target);
        h.runAfterDelay(2, () -> h.assertTrue(press(g, AbilitySlot.SKILL_3), "Rush during the wind-up: SCALE OF THE DRAGON"));
        h.runAfterDelay(14, () -> h.assertTrue(press(g, AbilitySlot.SKILL_2), "Open: RECOIL"));
        h.runAfterDelay(26, () -> h.assertTrue(press(g, AbilitySlot.SKILL_5), "Cleave: TWIN METEORS"));
        h.runAfterDelay(8, () -> {
            // Uninterruptible through the chant.
            dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.HITSTUN, 5);
            h.assertTrue(c.cast() instanceof DismantleAbility.Instance, "still chanting");
        });
        h.succeedWhen(() -> {
            h.assertTrue(!c.isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 70, "the world cut (" + (hp - target.getHealth()) + ")");
            int dis = c.cooldown(AbilitySlot.SKILL_1);
            h.assertTrue(dis > JJKConfig.get().yuji.dismantleCooldown, "Dismantle's cooldown doubled (" + dis + ")");
            h.assertTrue(c.cooldown(AbilitySlot.SKILL_2) > JJKConfig.get().yuji.openCooldown - 60, "Open on its full cooldown");
            h.assertTrue(!c.isReady(AbilitySlot.SKILL_3) && !c.isReady(AbilitySlot.SKILL_5), "Rush and Cleave too");
        });
    }

    @GameTest(maxTicks = 120)
    public void openLoosesTheArrowAndThePillarLifts(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy target = dummy(h, 7.5, 2.5);
        TrainingDummy g = yuji(h, 0.5, 2.5, target);
        Casters.get(g).enterAwakening();
        float hp = target.getHealth();
        boolean[] lifted = new boolean[1];
        press(g, AbilitySlot.SKILL_2);
        h.runAfterDelay(20, () -> dev.rick.jjk.core.combat.Statuses.apply(g, CombatStatus.HITSTUN, 6));
        h.onEachTick(() -> lifted[0] |= target.getDeltaMovement().y > 0.5);
        h.succeedWhen(() -> {
            h.assertTrue(hp - target.getHealth() >= 25, "the pillar of fire (" + (hp - target.getHealth()) + ")");
            h.assertTrue(lifted[0], "lifted");
        });
    }

    @GameTest(maxTicks = 120)
    public void rushHurlsKneesAndSlams(GameTestHelper h) {
        floor(h, 18);
        TrainingDummy target = dummy(h, 6.5, 2.5);
        TrainingDummy g = yuji(h, 1.5, 2.5, target);
        Casters.get(g).enterAwakening();
        float hp = target.getHealth();
        press(g, AbilitySlot.SKILL_3);
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(g).isBusy(), "finished");
            h.assertTrue(hp - target.getHealth() >= 20, "impact, knee and slam (" + (hp - target.getHealth()) + ")");
        });
    }

    @GameTest(maxTicks = 200, padding = 12)
    public void malevolentShrineSlicesEveryoneInside(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 5.5, 5.5);
        TrainingDummy guarder = dummy(h, 3.5, 7.5);
        TrainingDummy g = yuji(h, 3.5, 3.5, target);
        Casters.get(g).enterAwakening();
        h.assertTrue(press(g, AbilitySlot.SKILL_4), "Malevolent Shrine (" + Casters.get(g).lastRefusal + ")");
        float[] start = new float[2];
        h.runAfterDelay(40, () -> {
            h.assertTrue(DomainManager.ownedBy(g) != null && MalevolentShrine.ID.equals(DomainManager.ownedBy(g).definition.id()), "expanded");
            start[0] = target.getHealth();
            start[1] = guarder.getHealth();
            face(guarder, g.getBoundingBox().getCenter());
            Combat.state(guarder).startGuard(h.getLevel().getGameTime() - 20);
        });
        h.runAfterDelay(100, () -> {
            float open = start[0] - target.getHealth(), guarded = start[1] - guarder.getHealth();
            h.assertTrue(open >= 25, "a stream of Dismantles (" + open + ")");
            h.assertTrue(guarded > 0 && guarded < open * 0.4f, "the guard takes most of it (" + guarded + " vs " + open + ")");
            h.assertTrue(!Combat.state(target).actionsLocked(), "the slashes don't stun: they can still move and guard");
            h.succeed();
        });
    }
}
