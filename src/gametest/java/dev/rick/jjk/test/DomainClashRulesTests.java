package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.domain.DomainDefinition;
import dev.rick.jjk.core.domain.DomainInstance;
import dev.rick.jjk.core.domain.DomainManager;
import dev.rick.jjk.core.domain.clash.ClashManager;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.gojo.GojoCharacter;
import dev.rick.jjk.gojo.UnlimitedVoid;
import dev.rick.jjk.hakari.Gamble;
import dev.rick.jjk.hakari.HakariCharacter;
import dev.rick.jjk.hakari.IdleDeathGamble;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.yuji.MalevolentShrine;
import dev.rick.jjk.yuji.YujiCharacter;
import dev.rick.jjk.yuta.AuthenticMutualLove;
import dev.rick.jjk.yuta.DomainBladeEntity;
import dev.rick.jjk.yuta.YutaCharacter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * After winning a domain clash, every domain runs by its own rules, however it got into the clash: countered while it
 * was still forming (so it had never activated) or already open. Yuta's blades are planted and the domain doesn't break
 * as if it opened on nobody; Hakari's reels run (and are paused while the clash lasts); Malevolent Shrine slices and
 * Infinite Void overloads whoever is inside. Each runs in its own batch (the domains are large).
 */
public class DomainClashRulesTests {
    /** The shared config, set for these tests and put back after. */
    private static Runnable config() {
        JJKConfig cfg = JJKConfig.get();
        double radius = cfg.domain.radius;
        int duration = cfg.domain.duration, startup = cfg.domain.startup, countdown = cfg.clash.countdownTicks, notes = cfg.clash.notes;
        boolean structure = cfg.domain.physicalStructure, auto = cfg.general.autoAssignGojo;
        cfg.domain.radius = 8;
        cfg.domain.duration = 400;
        cfg.domain.startup = 10;
        cfg.domain.physicalStructure = true;
        cfg.clash.countdownTicks = 10;
        cfg.clash.notes = 12;
        cfg.general.autoAssignGojo = false;
        return () -> {
            cfg.domain.radius = radius;
            cfg.domain.duration = duration;
            cfg.domain.startup = startup;
            cfg.domain.physicalStructure = structure;
            cfg.clash.countdownTicks = countdown;
            cfg.clash.notes = notes;
            cfg.general.autoAssignGojo = auto;
        };
    }

    private static TrainingDummy fighter(GameTestHelper h, String character, double x, double z, float skill) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        CharacterService.assign(d, Characters.get(character));
        ClashManager.setBotSkill(d, skill);
        Combat.state(d);
        return d;
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /**
     * The winner's domain against a losing Infinite Void. {@code openFirst}: the winner's domain is already open when the
     * Void comes; otherwise the Void counters it while it is still forming. Runs {@code after} once the clash is won and
     * the winner's domain runs again.
     */
    private static void winClash(GameTestHelper h, String character, DomainDefinition def, boolean openFirst, Check after) {
        Runnable restore = config();
        floor(h);
        TrainingDummy w = fighter(h, character, 3.5, 6.5, 0.97f);
        TrainingDummy l = fighter(h, GojoCharacter.ID, 8.5, 6.5, 0.03f);
        DomainInstance[] d = new DomainInstance[2];
        var seq = h.startSequence().thenExecute(() -> {
            d[0] = DomainManager.expand(w, def);
            h.assertTrue(d[0] != null, def.displayName() + " expanded");
        });
        if (openFirst) seq = seq.thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ACTIVE, "open before the clash"));
        seq.thenExecute(() -> {
                    if (openFirst) after.beforeClash(d[0], w);
                    d[1] = DomainManager.expand(l, UnlimitedVoid.INSTANCE);
                    h.assertTrue(d[1] != null && d[0].clash() != null, "the Void meets it in a clash");
                    if (!openFirst) h.assertTrue(d[0].phase() == DomainInstance.Phase.FORMING, "countered while it was still forming");
                })
                .thenWaitUntil(() -> h.assertTrue(!d[1].isLive(), "the clash was decided"))
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ACTIVE, def.displayName() + " runs again ("
                        + d[0].phase() + ")"))
                .thenExecute(() -> h.assertTrue(d[1].endReason() == DomainInstance.EndReason.CLASH_LOST, "the Void lost the clash"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(d[0].isLive() && d[0].phase() == DomainInstance.Phase.ACTIVE, def.displayName() + " is still open after its win ("
                            + d[0].phase() + " " + d[0].endReason() + ")");
                    after.check(d[0], w, l);
                    DomainManager.cancel(d[0], DomainInstance.EndReason.CANCELLED);
                })
                .thenWaitUntil(() -> h.assertTrue(d[0].phase() == DomainInstance.Phase.ENDED, "ended"))
                .thenExecute(restore)
                .thenSucceed();
    }

    interface Check {
        void check(DomainInstance won, TrainingDummy owner, TrainingDummy loser);

        default void beforeClash(DomainInstance d, TrainingDummy owner) {}
    }

    private static Check amlRules(GameTestHelper h) {
        return (won, owner, loser) -> {
            AABB area = new AABB(won.center, won.center).inflate(won.radius + 4);
            int blades = h.getLevel().getEntitiesOfClass(DomainBladeEntity.class, area, b -> b.domainId() == won.id).size();
            h.assertTrue(blades >= 1, "Yuta's blades are planted in the domain he won (" + blades + ")");
        };
    }

    private static Check idgRules(GameTestHelper h, boolean pausedDuringClash) {
        Gamble[] before = new Gamble[1];
        return new Check() {
            @Override
            public void beforeClash(DomainInstance d, TrainingDummy owner) {
                before[0] = IdleDeathGamble.gambleOf(owner);
            }

            @Override
            public void check(DomainInstance won, TrainingDummy owner, TrainingDummy loser) {
                Gamble g = IdleDeathGamble.gambleOf(owner);
                h.assertTrue(g != null, "Hakari's gamble runs in the domain he won");
                h.assertTrue(g.state() == Gamble.State.SPINNING || g.state() == Gamble.State.RIICHI || g.state() == Gamble.State.MISS,
                        "the reels are spinning (" + g.state() + ")");
                if (pausedDuringClash) h.assertTrue(g == before[0], "the same gamble carries on after the clash");
                h.assertTrue(Combat.has(loser, CombatStatus.IN_DOMAIN), "the rules are imparted to whoever is inside");
            }
        };
    }

    @GameTest(maxTicks = 900, padding = 40, environment = "jjk-test:clash_rules_a")
    public void authenticMutualLoveCounteredWhileFormingRunsItsRulesAfterWinning(GameTestHelper h) {
        winClash(h, YutaCharacter.ID, AuthenticMutualLove.INSTANCE, false, amlRules(h));
    }

    @GameTest(maxTicks = 900, padding = 40, environment = "jjk-test:clash_rules_b")
    public void authenticMutualLoveAlreadyOpenRunsItsRulesAfterWinning(GameTestHelper h) {
        winClash(h, YutaCharacter.ID, AuthenticMutualLove.INSTANCE, true, amlRules(h));
    }

    @GameTest(maxTicks = 900, padding = 40, environment = "jjk-test:clash_rules_c")
    public void idleDeathGambleCounteredWhileFormingSpinsAfterWinning(GameTestHelper h) {
        winClash(h, HakariCharacter.ID, IdleDeathGamble.INSTANCE, false, idgRules(h, false));
    }

    @GameTest(maxTicks = 900, padding = 40, environment = "jjk-test:clash_rules_d")
    public void idleDeathGambleAlreadyOpenKeepsItsGambleThroughTheClash(GameTestHelper h) {
        winClash(h, HakariCharacter.ID, IdleDeathGamble.INSTANCE, true, idgRules(h, true));
    }

    @GameTest(maxTicks = 900, padding = 40, environment = "jjk-test:clash_rules_e")
    public void malevolentShrineSlicesAfterWinning(GameTestHelper h) {
        float[] hp = new float[1];
        winClash(h, YujiCharacter.ID, MalevolentShrine.INSTANCE, false, (won, owner, loser) -> {
            h.assertTrue(won.contains(loser), "the loser is inside the Shrine");
            h.assertTrue(loser.getHealth() < loser.getMaxHealth(), "the Shrine's slashes land on them (" + loser.getHealth() + "/" + loser.getMaxHealth() + ")");
        });
    }

    @GameTest(maxTicks = 900, padding = 40, environment = "jjk-test:clash_rules_f")
    public void infiniteVoidOverloadsAfterWinning(GameTestHelper h) {
        winClash(h, GojoCharacter.ID, UnlimitedVoid.INSTANCE, false, (won, owner, loser) ->
                h.assertTrue(Combat.has(loser, CombatStatus.IN_DOMAIN) || Combat.has(loser, CombatStatus.OVERLOAD), "the Void's sure-hit takes them"));
    }
}
