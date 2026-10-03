package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.Ability;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilityContext;
import dev.rick.jjk.core.ability.AbilityInstance;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.anim.Anim;
import dev.rick.jjk.core.anim.AnimRecovery;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.combat.Statuses;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.domain.structure.DomainStructure;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.UnlimitedVoid;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.ryu.RyuCharacter;
import dev.rick.jjk.ryu.UnsatisfiedAbility;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Nothing gets stuck: Ryu's Unsatisfied ending however it goes (a full combo, a whiff, a kill, an interruption, a lost
 * target), held poses with nothing behind them released while real ones are kept, a cast that never ends stopped by the
 * watchdog (but a held-up one left alone), a fresh start on respawn, and open domains that stay open while their owner
 * or a victim walks outside, ending only through their normal conditions.
 */
public class StabilityTests {
    private static final String ENV = "jjk-test:recovery";

    private static void floor(GameTestHelper h, int size) {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;

        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        return d;
    }

    private static TrainingDummy character(GameTestHelper h, String id, double x, double z) {
        TrainingDummy d = dummy(h, x, z);
        CharacterService.assign(d, Characters.get(id));
        Casters.get(d).setNoCost(true);
        return d;
    }

    private static void face(LivingEntity e, Vec3 target) {
        Vec3 d = target.subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setXRot(0);
    }

    /** Free again: no cast, no held pose, not locked, the move on its cooldown. */
    private static void free(GameTestHelper h, TrainingDummy r, String what) {
        AbilityCaster c = Casters.get(r);
        h.assertTrue(!c.isCasting() && c.cast() == null, what + ": the move is over (" + (c.cast() == null ? "-" : c.cast().ability.id + " " + c.cast().age()) + ")");
        h.assertTrue(!Anim.isHolding(r), what + ": no pose left held");
        h.assertTrue(!Combat.actionsLocked(r), what + ": free to act");
        h.assertTrue(c.cooldown(AbilitySlot.SKILL_2) > 0, what + ": on its cooldown");
    }

    // --- Ryu's Unsatisfied (his second move) ---

    private static TrainingDummy ryuFacing(GameTestHelper h, LivingEntity target) {
        TrainingDummy r = character(h, RyuCharacter.ID, 3.5, 1.5);
        // Real costs: "no cost" also skips cooldowns, and the cooldown is part of what is checked.
        Casters.get(r).setNoCost(false);
        face(r, target != null ? target.getBoundingBox().getCenter() : r.position().add(0, 1.6, 4));
        return r;
    }

    @GameTest(maxTicks = 80, environment = ENV)
    public void unsatisfiedEndsAfterTheFullCombo(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy target = dummy(h, 3.5, 3.8);
        TrainingDummy r = ryuFacing(h, target);
        float hp = target.getHealth();
        h.assertTrue(Casters.get(r).input(AbilitySlot.SKILL_2, true, 0, 0, null), "Unsatisfied");
        h.assertTrue(Casters.get(r).cast() != null && Casters.get(r).cast().ability instanceof UnsatisfiedAbility, "under way");
        h.runAfterDelay(50, () -> {
            h.assertTrue(hp - target.getHealth() >= 9, "the blows, the back clash and the toss landed (" + (hp - target.getHealth()) + ")");
            free(h, r, "after the full combo");
            h.assertTrue(!Combat.has(target, CombatStatus.GRABBED), "the target isn't left held");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void unsatisfiedEndsAfterAWhiff(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy r = ryuFacing(h, null);
        h.assertTrue(Casters.get(r).input(AbilitySlot.SKILL_2, true, 0, 0, null), "Unsatisfied at nobody");
        h.runAfterDelay(25, () -> {
            free(h, r, "after a whiff");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void unsatisfiedEndsWhenItsFirstBlowKills(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy target = dummy(h, 3.5, 3.8);
        target.setHealth(1f);
        TrainingDummy r = ryuFacing(h, target);
        h.assertTrue(Casters.get(r).input(AbilitySlot.SKILL_2, true, 0, 0, null), "Unsatisfied");
        h.runAfterDelay(30, () -> {
            h.assertTrue(!target.isAlive(), "the first blow killed them");
            free(h, r, "after a kill");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void unsatisfiedEndsWhenInterrupted(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy target = dummy(h, 3.5, 3.8);
        TrainingDummy r = ryuFacing(h, target);
        h.assertTrue(Casters.get(r).input(AbilitySlot.SKILL_2, true, 0, 0, null), "Unsatisfied");
        // Hit mid-combo: stunned out of it.
        h.runAfterDelay(8, () -> Statuses.apply(r, CombatStatus.HITSTUN, 6));
        h.runAfterDelay(20, () -> {
            free(h, r, "after an interruption");
            h.assertTrue(!Combat.has(target, CombatStatus.GRABBED), "the target isn't left held");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60, environment = ENV)
    public void unsatisfiedEndsWhenItsTargetIsLost(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy target = dummy(h, 3.5, 3.8);
        TrainingDummy r = ryuFacing(h, target);
        h.assertTrue(Casters.get(r).input(AbilitySlot.SKILL_2, true, 0, 0, null), "Unsatisfied");
        h.runAfterDelay(8, target::discard);
        h.runAfterDelay(24, () -> {
            free(h, r, "after losing the target");
            h.succeed();
        });
    }

    // --- Animation recovery ---

    @GameTest(maxTicks = 80, environment = ENV)
    public void aHeldPoseWithNothingBehindItIsReleased(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 3.5, 3.5);
        int before = AnimRecovery.recovered;
        Anim.play(g, "heavy_charge");
        h.assertTrue(Anim.isHolding(g), "a held pose, but no charge, cast or guard behind it");
        h.runAfterDelay(AnimRecovery.GRACE + 3, () -> {
            h.assertTrue(!Anim.isHolding(g), "released back to idle");
            h.assertTrue(AnimRecovery.recovered > before, "counted as a recovery");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 120, environment = ENV)
    public void aHeldPoseThatBelongsToSomethingIsKept(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 3.5, 3.5);
        Combat.state(g).startGuard(h.getLevel().getGameTime());
        Anim.play(g, "guard");
        h.onEachTick(() -> {
            if (h.getTick() < 60) ClashCommon.hold(g);
        });
        h.runAfterDelay(50, () -> {
            h.assertTrue(Anim.isHolding(g), "a guard (and a clash) keep their pose however long they last");
            Combat.state(g).stopGuard();
        });
        h.runAfterDelay(60 + 4 + AnimRecovery.GRACE + 3, () -> {
            h.assertTrue(!Anim.isHolding(g), "once nothing is behind it, it goes");
            h.succeed();
        });
    }

    /** A cast that declares ten ticks and then never ends (its end event lost). */
    private static AbilityInstance neverEnding(AbilityCaster c, TrainingDummy d, GameTestHelper h) {
        Ability ability = c.ability(AbilitySlot.SKILL_1);
        return new AbilityInstance(ability, new AbilityContext(c, d, (ServerLevel) h.getLevel(), AbilitySlot.SKILL_1, 0, 0, null)) {
            @Override
            public void start() {
                Anim.play(d, "heavy_charge");
                setPhase(0, 10);
            }

            @Override
            public void tick() {}
        };
    }

    @GameTest(maxTicks = 300, environment = ENV)
    public void aCastStuckFarPastItsLengthIsEnded(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 3.5, 3.5);
        AbilityCaster c = Casters.get(g);
        c.begin(neverEnding(c, g, h));
        int limit = 10 + AbilityInstance.STUCK_SLACK;
        h.runAfterDelay(limit - 20, () -> h.assertTrue(c.cast() != null, "left alone while it could still be playing"));
        h.runAfterDelay(limit + 6, () -> {
            h.assertTrue(c.cast() == null && c.recoveries == 1, "ended by the watchdog (" + c.recoveries + ")");
            h.assertTrue(!Anim.isHolding(g), "its held pose released with it");
            h.assertTrue(!c.isBusy(), "free to act");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 320, environment = ENV)
    public void aCastHeldUpByAClashIsNeverCut(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 3.5, 3.5);
        AbilityCaster c = Casters.get(g);
        c.begin(neverEnding(c, g, h));
        h.onEachTick(() -> ClashCommon.hold(g));
        h.runAfterDelay(10 + AbilityInstance.STUCK_SLACK + 60, () -> {
            h.assertTrue(c.cast() != null && c.recoveries == 0, "a long action in a clash is not stuck");
            h.assertTrue(Anim.isHolding(g), "and its pose is kept");
            c.interrupt("death");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 20, environment = ENV)
    public void respawningStartsFromACleanPose(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Anim.play(p, "guard");
        h.assertTrue(Anim.isHolding(p), "held before");
        // What the respawn, join and dimension-change hooks run.
        AnimRecovery.resync(p);
        h.assertTrue(!Anim.isHolding(p), "nothing held after");
        p.discard();
        h.succeed();
    }

    // --- Hakari's Jackpot ---

    @GameTest(maxTicks = 40, environment = ENV)
    public void jackpotHealsEverySurvivableHitAtOnce(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy hk = character(h, dev.rick.jjk.hakari.HakariCharacter.ID, 3.5, 3.5);
        TrainingDummy foe = dummy(h, 5.5, 3.5);
        Casters.get(hk).enterAwakening();
        h.assertTrue(Casters.get(hk).isAwakened(), "in Jackpot");
        float max = hk.getMaxHealth();
        var hit = dev.rick.jjk.core.combat.Hit.builder(foe, "test").damage(max * 0.9f)
                .tag(dev.rick.jjk.core.combat.AttackTag.MELEE, dev.rick.jjk.core.combat.AttackTag.UNBLOCKABLE).noComboScaling().build();
        var r = dev.rick.jjk.core.combat.HitResolver.resolve(hit, hk);
        h.assertTrue(r.outcome() == dev.rick.jjk.core.combat.HitResult.Outcome.HIT, "the hit landed (" + r.outcome() + ")");
        h.assertValueEqual(hk.getHealth(), max, "healed back to full the instant it landed");
        h.assertTrue(hk.isAlive(), "and alive");
        h.runAfterDelay(12, () -> {
            // One blow that would kill him from full health: that is the one thing that ends him.
            var lethal = dev.rick.jjk.core.combat.Hit.builder(foe, "test").damage(max + 10)
                    .tag(dev.rick.jjk.core.combat.AttackTag.MELEE, dev.rick.jjk.core.combat.AttackTag.UNBLOCKABLE).noComboScaling().build();
            dev.rick.jjk.core.combat.HitResolver.resolve(lethal, hk);
            h.assertTrue(hk.isDeadOrDying(), "a single fatal blow kills him");
            foe.discard();
            h.succeed();
        });
    }

    // --- Fall damage from height a move created ---

    /** A dummy standing on a pillar {@code height} blocks up at the plot's centre. */
    private static TrainingDummy onPillar(GameTestHelper h, int x, int z, int height) {
        for (int y = 1; y <= height; y++) h.setBlock(x, y, z, Blocks.STONE);
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x + 0.5, height + 1, z + 0.5));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        Combat.state(d);
        return d;
    }

    @GameTest(maxTicks = 100, padding = 8, skyAccess = true, environment = ENV)
    public void aLaunchThatLandsWhereItStartedDealsNoFallDamage(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy d = dummy(h, 3.5, 3.5);
        Combat.state(d);
        float hp = d.getHealth();
        // Thrown about seven blocks straight up by a move, and back down onto the same floor.
        h.runAfterDelay(2, () -> dev.rick.jjk.util.Motion.set(d, new Vec3(0, 1.6, 0)));
        h.runAfterDelay(8, () -> h.assertTrue(d.getY() > h.absoluteVec(new Vec3(0, 4, 0)).y, "high in the air"));
        h.runAfterDelay(80, () -> {
            h.assertTrue(d.onGround(), "landed");
            h.assertValueEqual(d.getHealth(), hp, "no fall damage: the height was the move's");
            h.assertTrue(!dev.rick.jjk.core.combat.LaunchHeight.tracking(d), "and the launch is forgotten");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 120, padding = 8, skyAccess = true, environment = ENV)
    public void onlyTheDropBelowTheLaunchPointCounts(GameTestHelper h) {
        floor(h, 8);
        // Two dummies on 6-block pillars: one simply walks off, the other is launched off it by a move.
        TrainingDummy dropped = onPillar(h, 1, 1, 6);
        TrainingDummy launched = onPillar(h, 5, 5, 6);
        float[] hp = {dropped.getHealth(), launched.getHealth()};
        h.runAfterDelay(4, () -> {
            // Stepped just off the pillar's edge (no push upward: an ordinary fall).
            Vec3 edge = h.absoluteVec(new Vec3(2.45, 7, 1.5));
            dropped.teleportTo(edge.x, edge.y, edge.z);
            // Thrown several blocks higher than the pillar, then down past it to the floor.
            dev.rick.jjk.util.Motion.set(launched, new Vec3(0.18, 1.3, 0));
        });
        h.runAfterDelay(100, () -> {
            float plain = hp[0] - dropped.getHealth(), afterLaunch = hp[1] - launched.getHealth();
            h.assertTrue(dropped.onGround() && launched.onGround(), "both landed");
            h.assertTrue(plain > 0, "an ordinary six-block fall hurts (" + plain + ")");
            // The launch added several blocks of height above the pillar, none of which count: only the six below it.
            h.assertTrue(Math.abs(afterLaunch - plain) <= 1.01f, "launched: only the six blocks below the launch point count ("
                    + afterLaunch + " vs " + plain + "; ids " + dropped.getId() + "/" + launched.getId() + ")");
            h.succeed();
        });
    }

    /** Fall damage each entity took (by id), recorded from the damage event so other damage doesn't blur it. */
    private static final java.util.Map<Integer, Float> FALLS = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean fallsHooked;

    private static void trackFalls() {
        if (fallsHooked) return;
        fallsHooked = true;
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e, source, base, taken, blocked) -> {
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) FALLS.merge(e.getId(), taken, Float::sum);
        });
    }

    private static float fell(LivingEntity e) {
        return FALLS.getOrDefault(e.getId(), 0f);
    }

    @GameTest(maxTicks = 200, padding = 12, skyAccess = true, environment = ENV)
    public void sukunasRushNeverHurtsEitherOfThemOnTheWayDown(GameTestHelper h) {
        trackFalls();
        floor(h, 18);
        TrainingDummy target = dummy(h, 6.5, 2.5);
        TrainingDummy s = character(h, dev.rick.jjk.yuji.YujiCharacter.ID, 1.5, 2.5);
        s.setOnGround(true);
        Vec3 d = target.getBoundingBox().getCenter().subtract(s.getEyePosition());
        s.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        s.setYHeadRot(s.getYRot());
        Casters.get(s).setNoCost(true);
        Casters.get(s).enterAwakening();
        float hp = target.getHealth();
        boolean[] airborne = new boolean[2];
        h.startSequence()
                .thenExecute(() -> h.assertTrue(Casters.get(s).input(AbilitySlot.SKILL_3, true, 0, 0, null), "Rush (" + Casters.get(s).lastRefusal + ")"))
                .thenExecute(() -> Casters.get(s).input(AbilitySlot.SKILL_3, false, 0, 0, null))
                .thenWaitUntil(() -> {
                    airborne[0] |= !s.onGround() && s.getY() > h.absoluteVec(new Vec3(0, 3, 0)).y;
                    airborne[1] |= !target.onGround() && target.getY() > h.absoluteVec(new Vec3(0, 3, 0)).y;
                    h.assertTrue(!Casters.get(s).isBusy(), "finished");
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    h.assertTrue(airborne[0] && airborne[1], "both went up into the air (" + airborne[0] + "/" + airborne[1] + ")");
                    h.assertTrue(s.onGround() && target.onGround(), "both back down");
                    h.assertTrue(hp - target.getHealth() >= 20, "the move landed (" + (hp - target.getHealth()) + ")");
                    h.assertValueEqual(fell(s), 0f, "Sukuna takes no fall damage from his own leap");
                    h.assertValueEqual(fell(target), 0f, "nor does his victim from the knee and slam");
                    h.assertTrue(s.isAlive() && target.isAlive(), "both alive");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 120, padding = 8, skyAccess = true, environment = ENV)
    public void heightFromATeleportOrAMidComboHitIsNeverFallDamage(GameTestHelper h) {
        trackFalls();
        floor(h, 8);
        TrainingDummy teleported = dummy(h, 1.5, 1.5);
        TrainingDummy juggled = dummy(h, 4.5, 4.5);
        TrainingDummy plain = dummy(h, 6.5, 1.5);
        // Fighters have combat data (it's what remembers where they last stood); the plain one is the same.
        for (TrainingDummy d : new TrainingDummy[] {teleported, juggled, plain}) Combat.state(d);
        h.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // A move teleports one twelve blocks up (as Gojo's Teleport or a blink behind an airborne target does).
                    dev.rick.jjk.core.combat.LaunchHeight.displaced(teleported, teleported.getY());
                    teleported.teleportTo(teleported.getX(), teleported.getY() + 12, teleported.getZ());
                    // Another is up there in hitstun with no launch recorded: where it last stood counts.
                    juggled.teleportTo(juggled.getX(), juggled.getY() + 12, juggled.getZ());
                    Statuses.apply(juggled, CombatStatus.HITSTUN, 6);
                    // The control: simply put twelve blocks up with nothing to do with a fight.
                    plain.teleportTo(plain.getX(), plain.getY() + 12, plain.getZ());
                })
                .thenIdle(90)
                .thenExecute(() -> {
                    h.assertTrue(teleported.onGround() && juggled.onGround() && plain.onGround(), "all landed");
                    h.assertValueEqual(fell(teleported), 0f, "a move's teleport upward: no fall damage");
                    h.assertValueEqual(fell(juggled), 0f, "airborne mid-combo: no fall damage above where it stood");
                    h.assertTrue(fell(plain) > 0, "an ordinary twelve-block fall still hurts (" + fell(plain) + ")");
                })
                .thenSucceed();
    }

    // --- Domains ---

    /**
     * The shared config's domain size and length, set for a domain test (other classes shorten domains for theirs; this
     * needs long enough to walk out) and put back as it was when the test ends, for the classes that rely on defaults.
     */
    private static Runnable domainConfig() {
        // These tests run at the same time: the first in saves the shared values, the last out puts them back (each saving
        // and restoring its own copy would let one restore the other's settings and leak them into later batches).
        JJKConfig.Domain d = JJKConfig.get().domain;
        if (DOMAIN_USERS++ == 0) {
            savedDomain = d;
            saved = new Object[] {d.radius, d.duration, d.startup, d.physicalStructure};
        }
        d.physicalStructure = true;
        d.radius = 6;
        d.duration = 200;
        d.startup = 10;
        boolean[] done = {false};
        return () -> {
            if (done[0]) return;
            done[0] = true;
            if (--DOMAIN_USERS > 0) return;
            savedDomain.radius = (double) saved[0];
            savedDomain.duration = (int) saved[1];
            savedDomain.startup = (int) saved[2];
            savedDomain.physicalStructure = (boolean) saved[3];
        };
    }

    private static int DOMAIN_USERS;
    private static JJKConfig.Domain savedDomain;
    private static Object[] saved;

    @GameTest(maxTicks = 700, padding = 16, environment = ENV)
    public void anOpenDomainOutlastsItsOwnerAndVictimWalkingOut(GameTestHelper h) {
        Runnable restore = domainConfig();
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 4.5, 4.5);
        TrainingDummy victim = dummy(h, 6.5, 4.5);
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> {
                    d[0] = DomainManager.expand(g, UnlimitedVoid.INSTANCE);
                    h.assertTrue(d[0] != null, "expanded");
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ACTIVE, "open"))
                .thenExecute(() -> {
                    // Both walk out past its edge.
                    Vec3 out = d[0].center.add(d[0].radius + 4, 0, 0);
                    g.teleportTo(out.x, out.y, out.z);
                    victim.teleportTo(out.x, out.y, out.z + 2);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(d[0].isLive() && d[0].phase() == DomainInstance.Phase.ACTIVE, "still open with both of them outside ("
                            + d[0].phase() + " " + d[0].endReason() + ", owner removed " + g.isRemoved() + " alive " + g.isAlive() + ")");
                    h.assertTrue(d[0].structure() != null && d[0].structure().state() == DomainStructure.State.BUILT, "its structure stands");
                    h.assertTrue(!Combat.has(victim, CombatStatus.IN_DOMAIN), "its sure-hit doesn't follow anyone outside");
                    // And back in.
                    Vec3 in = d[0].center;
                    g.teleportTo(in.x, in.y, in.z);
                })
                // Its normal end: the duration runs out.
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ENDED, "expired (" + d[0].remaining() + " left)"))
                .thenExecute(() -> h.assertTrue(d[0].endReason() == DomainInstance.EndReason.EXPIRED, "ended by expiring (" + d[0].endReason() + ")"))
                .thenWaitUntil(() -> h.assertTrue(d[0].structure() != null && d[0].structure().state() == DomainStructure.State.DONE, "structure restored"))
                .thenExecute(restore)
                .thenSucceed();
    }

    @GameTest(maxTicks = 400, padding = 16, environment = ENV)
    public void authenticMutualLoveStaysOpenWhenItsEnemyWalksOut(GameTestHelper h) {
        Runnable restore = domainConfig();
        floor(h, 8);
        TrainingDummy y = character(h, dev.rick.jjk.yuta.YutaCharacter.ID, 4.5, 4.5);
        TrainingDummy enemy = dummy(h, 6.5, 4.5);
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> d[0] = DomainManager.expand(y, dev.rick.jjk.yuta.AuthenticMutualLove.INSTANCE))
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ACTIVE && d[0].activeAge() > 5, "open, with an enemy caught"))
                .thenExecute(() -> {
                    Vec3 out = d[0].center.add(d[0].radius + 4, 0, 0);
                    enemy.teleportTo(out.x, out.y, out.z);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(d[0].isLive(), "the enemy walking out doesn't break it (" + d[0].phase() + " " + d[0].endReason() + ")");
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ENDED, "ended when cancelled"))
                .thenExecute(restore)
                .thenSucceed();
    }

    // Its own batch: it sets the shared domain length to "for ever", which the other domain tests here mustn't see.
    @GameTest(maxTicks = 600, padding = 16, environment = "jjk-test:domain_ceiling")
    public void noDomainOutlivesItsCeiling(GameTestHelper h) {
        Runnable restore = domainConfig();
        JJKConfig.Domain cfg = JJKConfig.get().domain;
        int max = cfg.maxLifetimeTicks;
        // Configured to last practically forever: the ceiling still ends it.
        cfg.duration = Integer.MAX_VALUE / 4;
        cfg.maxLifetimeTicks = 300;
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 4.5, 4.5);
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> {
                    d[0] = DomainManager.expand(g, UnlimitedVoid.INSTANCE);
                    h.assertTrue(d[0] != null, "expanded");
                    h.assertTrue(DomainManager.lifetimeLimit(d[0]) == 300, "its ceiling is the configured maximum (" + DomainManager.lifetimeLimit(d[0]) + ")");
                })
                .thenIdle(250)
                .thenExecute(() -> h.assertTrue(d[0].isLive(), "still open before the ceiling"))
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ENDED, "ended at the ceiling (" + d[0].phase() + ")"))
                .thenExecute(() -> {
                    h.assertTrue(d[0].endReason() == DomainInstance.EndReason.EXPIRED, "expired (" + d[0].endReason() + ")");
                    cfg.maxLifetimeTicks = max;
                    restore.run();
                    // With the shipped settings every domain has a finite ceiling no longer than the maximum.
                    for (var def : java.util.List.of(UnlimitedVoid.INSTANCE, dev.rick.jjk.yuta.AuthenticMutualLove.INSTANCE,
                            dev.rick.jjk.yuji.MalevolentShrine.INSTANCE, dev.rick.jjk.hakari.IdleDeathGamble.INSTANCE)) {
                        h.assertTrue(def.duration(g) > 0 && def.duration(g) < cfg.maxLifetimeTicks, def.displayName() + " has a finite duration under the ceiling");
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].structure() == null || d[0].structure().state() == DomainStructure.State.DONE, "structure restored"))
                .thenSucceed();
    }

    @GameTest(maxTicks = 200, padding = 16, environment = ENV)
    public void cancellingAnOpenDomainStillCleansUp(GameTestHelper h) {
        Runnable restore = domainConfig();
        floor(h, 8);
        TrainingDummy g = character(h, GojoCharacter.ID, 4.5, 4.5);
        DomainInstance[] d = new DomainInstance[1];
        h.startSequence()
                .thenExecute(() -> d[0] = DomainManager.expand(g, UnlimitedVoid.INSTANCE))
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ACTIVE, "open"))
                .thenExecute(() -> {
                    Vec3 out = d[0].center.add(d[0].radius + 4, 0, 0);
                    g.teleportTo(out.x, out.y, out.z);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(d[0].isLive(), "open while he is outside");
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ENDED && d[0].structure() != null && d[0].structure().state() == DomainStructure.State.DONE,
                        "ended and restored"))
                .thenExecute(() -> h.assertTrue(DomainManager.ownedBy(g) == null, "nothing of it left"))
                .thenExecute(restore)
                .thenSucceed();
    }
}
