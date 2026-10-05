package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.BeamClashSession;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.progression.tool.rifle.RifleBeam;
import dev.rick.jjk.progression.tool.rifle.RifleServer;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.ryu.RyuCharacter;
import dev.rick.jjk.yuta.YutaCharacter;
import dev.rick.jjk.yuta.YutaState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Cursed Rifle in the shared beam-clash system, with no scripted imitation: its beam is answered by, and answers,
 * True Love Beam, Every Last Drop and another rifle under the same rules (the window opens when the opponent starts
 * charging; a rifle can answer only once its beam is ready; charging alone starts nothing). At Maximum Output it meets
 * them on even terms (the sustained, doubled duel); its first unlock is overpowered by a fresh full-power beam. Run in
 * their own batch: beams reach neighbouring plots.
 */
public class RifleClashTests {
    private static final String ENV = "jjk-test:rifle_beam";

    private static void floor(GameTestHelper h) {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;
        cfg.progression.enabled = true;
        cfg.rifle.beamRange = 14;
        cfg.yuta.beamRange = 13;
        cfg.yuta.beamQuickRange = 11;
        cfg.ryu.eldRange = 14;
        for (int x = -2; x < 16; x++) for (int z = -2; z < 16; z++) {
            h.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 1; y < 8; y++) if (h.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).is(Blocks.BARRIER)) h.setBlock(x, y, z, Blocks.AIR);
        }
    }

    /** A rifle at the given level of the tree, its beam already charged and held ready. */
    private static ServerPlayer ready(GameTestHelper h, double x, double z, String[] nodes) {
        ServerPlayer p = RifleTests.shooter(h, x, z, GameType.SURVIVAL);
        RifleTests.learn(p, nodes);
        return p;
    }

    private static void arm(ServerPlayer p) {
        p.setShiftKeyDown(true);
        RifleServer.forcePhaseForTest(p, RifleServer.Phase.READY);
        p.startUsingItem(InteractionHand.MAIN_HAND);
    }

    private static TrainingDummy character(GameTestHelper h, String id, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        d.setOnGround(true);
        CharacterService.assign(d, Characters.get(id));
        Casters.get(d).setNoCost(true);
        return d;
    }

    private static boolean press(LivingEntity e, AbilitySlot slot) {
        return Casters.get(e).input(slot, true, 0, 0, null);
    }

    /** Watches one clash to its end. */
    private static final class Watch {
        BeamClashSession session;
        String k0 = "", k1 = "";
        long duelAt = -1, resolveAt = -1;

        void see(GameTestHelper h, LivingEntity e) {
            BeamClashSession s = BeamClashManager.sessionOf(e);
            if (s != null) session = s;
            if (session == null) return;
            // The beams are let go when it ends: note what clashed while it runs.
            if (k0.isEmpty() && session.beam(0) != null && session.beam(1) != null) {
                k0 = session.beam(0).beamKind();
                k1 = session.beam(1).beamKind();
            }
            if (duelAt < 0 && session.phase() == BeamClashSession.Phase.DUEL) duelAt = h.getTick();
            if (resolveAt < 0 && session.phase() == BeamClashSession.Phase.RESOLVE) resolveAt = h.getTick();
        }
    }

    /** Two rifles: A fires first, B (ready, in the window) answers it. */
    private static void rifleDuel(GameTestHelper h, String[] aNodes, String[] bNodes, boolean expectOverpower) {
        floor(h);
        ServerPlayer a = ready(h, 3.5, 0.8, aNodes), b = ready(h, 3.5, 7.0, bNodes);
        RifleTests.face(a, b.getEyePosition());
        RifleTests.face(b, a.getEyePosition());
        Watch w = new Watch();
        // B's beam is charged and held; A starts charging: from this moment B may answer.
        arm(b);
        a.setShiftKeyDown(true);
        h.assertTrue(RifleTests.press(a) && RifleServer.phase(a) == RifleServer.Phase.DEPLOY, "A starts deploying");
        h.assertTrue(BeamClashManager.windowOpen(b), "B's window opens as A starts charging (" + BeamClashManager.lastMiss + ")");
        h.assertTrue(BeamClashManager.sessionOf(a) == null, "charging alone starts nothing");
        boolean[] answered = new boolean[1];
        long start = h.getTick();
        h.onEachTick(() -> {
            if (!answered[0] && h.getTick() >= start + 8) {
                answered[0] = true;
                RifleTests.letGo(b);
                h.assertTrue(RifleServer.phase(b) == RifleServer.Phase.FIRE, "B answered (" + BeamClashManager.lastMiss + ")");
            }
            // A keeps charging and fires the moment it can.
            if (answered[0] && RifleServer.phase(a) == RifleServer.Phase.READY) RifleTests.letGo(a);
            w.see(h, a);
            w.see(h, b);
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.session != null, "a clash (" + BeamClashManager.lastMiss + ")");
            h.assertTrue("rifle".equals(w.k0) && "rifle".equals(w.k1), "rifle against rifle: " + w.k0 + "/" + w.k1);
            h.assertTrue(w.session.phase() == BeamClashSession.Phase.ENDED, "played out");
            if (expectOverpower) {
                h.assertTrue(w.session.overpowered(), "the first unlock is overpowered");
                LivingEntity winner = w.session.outcome() == BeamClashSession.Outcome.A ? w.session.entity(0) : w.session.entity(1);
                h.assertTrue(winner == b, "by the Maximum Output rifle");
            } else {
                h.assertTrue(!w.session.overpowered() && w.resolveAt - w.duelAt >= BeamClashSession.DUEL_TICKS, "even: the full, doubled duel ("
                        + (w.resolveAt - w.duelAt) + ")");
            }
            h.assertTrue(!ClashCommon.clashing(a) && !ClashCommon.clashing(b), "both free");
        });
    }

    @GameTest(maxTicks = 700, padding = 8, environment = ENV)
    public void rifleAgainstRifleAtMaximumOutputIsAnEvenDuel(GameTestHelper h) {
        rifleDuel(h, RifleTests.TO_MAX, RifleTests.TO_MAX, false);
    }

    @GameTest(maxTicks = 400, padding = 8, environment = ENV)
    public void aFirstUnlockRifleIsOverpoweredByMaximumOutput(GameTestHelper h) {
        rifleDuel(h, RifleTests.TO_BEAM, RifleTests.TO_MAX, true);
    }

    @GameTest(maxTicks = 600, padding = 8, environment = ENV)
    public void maximumOutputAnswersEveryLastDropOnEvenTerms(GameTestHelper h) {
        floor(h);
        ServerPlayer p = ready(h, 3.5, 7.0, RifleTests.TO_MAX);
        TrainingDummy ryu = character(h, RyuCharacter.ID, 3.5, 0.8);
        RifleTests.face(ryu, p.getEyePosition());
        RifleTests.face(p, ryu.getEyePosition());
        AbilityCaster rc = Casters.get(ryu);
        rc.setAwakening(rc.maxAwakening());
        h.assertTrue(BeamClashManager.sessionOf(p) == null, "nothing before anything fires");
        arm(p);
        h.assertTrue(!BeamClashManager.windowOpen(p), "a ready rifle alone opens no window");
        h.assertTrue(press(ryu, AbilitySlot.ULTIMATE), "Every Last Drop (" + rc.lastRefusal + ")");
        h.assertTrue(BeamClashManager.windowOpen(p), "the window opens on the rifle as he starts charging (" + BeamClashManager.lastMiss + ")");
        Watch w = new Watch();
        long start = h.getTick();
        boolean[] answered = new boolean[1];
        h.onEachTick(() -> {
            if (!answered[0] && h.getTick() >= start + 10) {
                answered[0] = true;
                RifleTests.face(p, ryu.getEyePosition());
                RifleTests.letGo(p);
                h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.FIRE, "the rifle answered (" + BeamClashManager.lastMiss + ")");
            }
            w.see(h, p);
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.session != null, "a clash");
            String k0 = w.k0, k1 = w.k1;
            h.assertTrue(("eld".equals(k0) && "rifle".equals(k1)) || ("rifle".equals(k0) && "eld".equals(k1)), "Every Last Drop against the rifle: " + k0 + "/" + k1);
            h.assertTrue(w.session.phase() == BeamClashSession.Phase.ENDED, "played out");
            h.assertTrue(!w.session.overpowered() && w.resolveAt - w.duelAt >= BeamClashSession.DUEL_TICKS, "even terms: the full duel (" + (w.resolveAt - w.duelAt) + ")");
            h.assertTrue(!ClashCommon.clashing(p) && !ClashCommon.clashing(ryu), "both free");
            ryu.discard();
        });
    }

    @GameTest(maxTicks = 700, padding = 8, environment = ENV)
    public void trueLoveBeamIsAnsweredByTheRifle(GameTestHelper h) {
        floor(h);
        ServerPlayer p = ready(h, 3.5, 7.0, RifleTests.TO_MAX);
        TrainingDummy y = character(h, YutaCharacter.ID, 3.5, 1.6);
        RifleTests.face(y, p.getBoundingBox().getCenter());
        RifleTests.face(p, y.getEyePosition());
        Casters.get(y).enterAwakening();
        YutaState.of(y).fists = true;
        h.assertTrue(press(y, AbilitySlot.SKILL_5), "Rika comes out");
        Watch w = new Watch();
        long[] chargeAt = {-1};
        boolean[] answered = new boolean[1];
        h.runAfterDelay(10, () -> {
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "her moveset");
            RifleTests.face(y, p.getBoundingBox().getCenter());
            arm(p);
            h.assertTrue(press(y, AbilitySlot.SKILL_3), "True Love Beam (" + Casters.get(y).lastRefusal + ")");
            chargeAt[0] = h.getTick();
        });
        h.onEachTick(() -> {
            if (chargeAt[0] >= 0 && !answered[0] && h.getTick() >= chargeAt[0] + 8) {
                answered[0] = true;
                h.assertTrue(BeamClashManager.windowOpen(p), "the window is open on the rifle (" + BeamClashManager.lastMiss + ")");
                RifleTests.letGo(p);
                h.assertTrue(RifleServer.phase(p) == RifleServer.Phase.FIRE, "answered");
            }
            w.see(h, p);
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.session != null, "a clash");
            String k0 = w.k0, k1 = w.k1;
            h.assertTrue(k0.equals(RifleBeam.KIND) || k1.equals(RifleBeam.KIND), "the rifle is in it: " + k0 + "/" + k1);
            h.assertTrue(w.session.phase() == BeamClashSession.Phase.ENDED, "played out");
            h.assertTrue(!ClashCommon.clashing(p) && !ClashCommon.clashing(y), "both free");
            y.discard();
        });
    }
}
