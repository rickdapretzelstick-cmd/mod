package dev.rick.jjk.test;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.ability.AbilityCaster;
import dev.rick.jjk.core.ability.AbilitySlot;
import dev.rick.jjk.core.ability.Casters;
import dev.rick.jjk.core.character.CharacterService;
import dev.rick.jjk.core.character.Characters;
import dev.rick.jjk.core.clash.BeamClashManager;
import dev.rick.jjk.core.clash.BeamClashSession;
import dev.rick.jjk.core.clash.ClashBeam;
import dev.rick.jjk.core.clash.ClashCommon;
import dev.rick.jjk.entity.TrainingDummy;
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.ryu.EveryLastDropAbility;
import dev.rick.jjk.ryu.RyuCharacter;
import dev.rick.jjk.yuta.TrueLoveBeamAbility;
import dev.rick.jjk.yuta.YutaCharacter;
import dev.rick.jjk.yuta.YutaState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Beam clashes between the same characters (Yuta against Yuta, Ryu against Ryu), the counter window opening the moment
 * the opponent starts charging (not before, not after it closes, and once per charge), an instant overpower when one
 * beam is far stronger, the sustained duel lasting twice its original length otherwise, and a clash ending cleanly when
 * a beam is cancelled. Real beams for the matchups; stand-in beams of chosen strength for the session rules. Run in the
 * beam batch (with RyuGameTests: their ranges reach neighbouring plots).
 */
public class BeamClashMatchupTests {
    private static final String ENV = "jjk-test:ryu";

    private static void floor(GameTestHelper h, int size) {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;
        // Kept inside one test's area (the same values RyuGameTests sets: the classes share the live config).
        cfg.yuta.beamRange = 13;
        cfg.yuta.beamQuickRange = 11;
        cfg.ryu.graniteRange = 14;
        cfg.ryu.graniteHeldRange = 14;
        cfg.ryu.eldRange = 14;
        cfg.ryu.appetizerRange = 14;
        cfg.ryu.appetizerRayRange = 12;
        cfg.ryu.secondHelpingRange = 12;
        cfg.ryu.invitedWallRange = 12;
        cfg.ryu.invitedWallHeldRange = 14;
        for (int x = -2; x < size; x++) for (int z = -2; z < size; z++) {
            h.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 1; y < 8; y++) if (h.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).is(Blocks.BARRIER)) h.setBlock(x, y, z, Blocks.AIR);
        }
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

    private static void rikaAwakened(GameTestHelper h, TrainingDummy y, Runnable then) {
        Casters.get(y).enterAwakening();
        YutaState.of(y).fists = true;
        h.assertTrue(press(y, AbilitySlot.SKILL_5), "Rika (fully manifested: she comes out)");
        h.runAfterDelay(10, () -> {
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "her moveset");
            then.run();
        });
    }

    /** Watches a clash: when it entered each phase, and the session itself. */
    private static final class Watch {
        BeamClashSession session;
        long duelAt = -1, resolveAt = -1;

        void see(GameTestHelper h, LivingEntity e) {
            BeamClashSession s = BeamClashManager.sessionOf(e);
            if (s != null) session = s;
            if (session == null) return;
            if (duelAt < 0 && session.phase() == BeamClashSession.Phase.DUEL) duelAt = h.getTick();
            if (resolveAt < 0 && session.phase() == BeamClashSession.Phase.RESOLVE) resolveAt = h.getTick();
        }
    }

    // --- The same character on both sides ---

    @GameTest(maxTicks = 700, padding = 8, environment = ENV)
    public void yutaAgainstYutaClashesFromTheChargeAndIsDecided(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy a = character(h, YutaCharacter.ID, 3.5, 1.6);
        TrainingDummy b = character(h, YutaCharacter.ID, 3.5, 6.2);
        face(a, b.getBoundingBox().getCenter());
        face(b, a.getBoundingBox().getCenter());
        AbilityCaster bc = Casters.get(b);
        bc.setAwakening(bc.maxAwakening());
        long[] chargeAt = {-1}, windowAt = {-1};
        boolean[] answered = new boolean[1];
        Watch w = new Watch();
        rikaAwakened(h, a, () -> {
            face(a, b.getBoundingBox().getCenter());
            h.assertTrue(!BeamClashManager.windowOpen(b), "no window before he starts charging");
            h.assertTrue(press(a, AbilitySlot.SKILL_3), "True Love Beam (" + Casters.get(a).lastRefusal + ")");
            chargeAt[0] = h.getTick();
        });
        h.onEachTick(() -> {
            if (windowAt[0] < 0 && BeamClashManager.windowOpen(b)) windowAt[0] = h.getTick();
            // Answer a little into the charge (still conjuring: the beam hasn't fired).
            if (!answered[0] && windowAt[0] >= 0 && h.getTick() >= windowAt[0] + 8) {
                h.assertTrue(Casters.get(a).cast() instanceof TrueLoveBeamAbility.Instance i && !i.beamLive(), "his beam is still charging");
                h.assertTrue(press(b, AbilitySlot.ULTIMATE), "the other Yuta's counter (" + bc.lastRefusal + ")");
                answered[0] = true;
                h.assertTrue(!BeamClashManager.tryCounter(bc), "one counter per charge");
            }
            w.see(h, a);
        });
        h.succeedWhen(() -> {
            h.assertTrue(windowAt[0] >= 0 && windowAt[0] - chargeAt[0] <= 1, "the window opened as the charge began (charge "
                    + chargeAt[0] + ", window " + windowAt[0] + "; " + BeamClashManager.lastMiss + ")");
            h.assertTrue(w.session != null && w.session.entity(0) == a && w.session.entity(1) == b, "one session, Yuta against Yuta");
            h.assertTrue(w.session.phase() == BeamClashSession.Phase.ENDED, "played out");
            BeamClashSession.Outcome o = w.session.outcome();
            h.assertTrue(o == BeamClashSession.Outcome.A || o == BeamClashSession.Outcome.B || o == BeamClashSession.Outcome.TIE,
                    "decided (" + o + ": " + w.session.reason() + ")");
            h.assertTrue(!w.session.overpowered() && w.resolveAt - w.duelAt >= BeamClashSession.DUEL_TICKS, "a full, doubled struggle ("
                    + (w.resolveAt - w.duelAt) + " ticks)");
            h.assertTrue(!ClashCommon.clashing(a) && !ClashCommon.clashing(b), "both free");
        });
    }

    @GameTest(maxTicks = 600, padding = 8, environment = ENV)
    public void ryuAgainstRyuClashesFromTheChargeAndIsDecided(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy a = character(h, RyuCharacter.ID, 3.5, 0.8);
        TrainingDummy b = character(h, RyuCharacter.ID, 3.5, 6.0);
        face(a, b.getBoundingBox().getCenter());
        face(b, a.getBoundingBox().getCenter());
        AbilityCaster ac = Casters.get(a), bc = Casters.get(b);
        ac.setAwakening(ac.maxAwakening());
        bc.setAwakening(bc.maxAwakening());
        long[] windowAt = {-1};
        boolean[] answered = new boolean[1];
        Watch w = new Watch();
        h.assertTrue(!BeamClashManager.windowOpen(b) && !BeamClashManager.tryCounter(bc), "nothing to counter before the charge");
        h.assertTrue(press(a, AbilitySlot.ULTIMATE), "Every Last Drop (" + ac.lastRefusal + ")");
        h.assertTrue(BeamClashManager.windowOpen(b), "the window opens the tick the charge starts (" + BeamClashManager.lastMiss + ")");
        h.assertTrue(!BeamClashManager.windowOpen(a), "never a window on his own beam");
        long chargeAt = h.getTick();
        h.onEachTick(() -> {
            if (windowAt[0] < 0 && BeamClashManager.windowOpen(b)) windowAt[0] = h.getTick();
            if (!answered[0] && h.getTick() >= chargeAt + 10) {
                h.assertTrue(ac.cast() instanceof EveryLastDropAbility.Instance i && !i.beamLive(), "still charging when answered");
                h.assertTrue(press(b, AbilitySlot.ULTIMATE), "the other Ryu's counter (" + bc.lastRefusal + ")");
                answered[0] = true;
                h.assertTrue(!BeamClashManager.tryCounter(bc), "one counter per charge");
            }
            w.see(h, a);
        });
        h.succeedWhen(() -> {
            h.assertTrue(w.session != null && w.session.entity(0) == a && w.session.entity(1) == b, "one session, Ryu against Ryu");
            h.assertTrue(w.session.phase() == BeamClashSession.Phase.ENDED, "played out");
            BeamClashSession.Outcome o = w.session.outcome();
            h.assertTrue(o == BeamClashSession.Outcome.A || o == BeamClashSession.Outcome.B || o == BeamClashSession.Outcome.TIE,
                    "decided (" + o + ": " + w.session.reason() + ")");
            h.assertTrue(w.resolveAt - w.duelAt >= BeamClashSession.DUEL_TICKS, "a full, doubled struggle (" + (w.resolveAt - w.duelAt) + ")");
            h.assertTrue(!ClashCommon.clashing(a) && !ClashCommon.clashing(b), "both free");
        });
    }

    @GameTest(maxTicks = 120, padding = 8, environment = ENV)
    public void theCounterWindowClosesAfterTheBeamFires(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy a = character(h, RyuCharacter.ID, 3.5, 0.8);
        TrainingDummy b = character(h, RyuCharacter.ID, 3.5, 6.0);
        face(a, b.getBoundingBox().getCenter());
        face(b, a.getBoundingBox().getCenter());
        AbilityCaster ac = Casters.get(a), bc = Casters.get(b);
        ac.setAwakening(ac.maxAwakening());
        bc.setAwakening(bc.maxAwakening());
        h.assertTrue(press(a, AbilitySlot.ULTIMATE), "Every Last Drop");
        h.assertTrue(BeamClashManager.windowOpen(b), "open from the charge");
        int fire = JJKConfig.get().ryu.eldCharge;
        h.runAfterDelay(fire + BeamClashManager.GRACE + 2, () -> {
            h.assertTrue(!BeamClashManager.windowOpen(b), "closed once its moment after the release is over");
            h.assertTrue(!BeamClashManager.tryCounter(bc), "a late press is no counter");
            h.assertTrue(BeamClashManager.sessionOf(a) == null, "and no clash");
            a.discard();
            b.discard();
            h.succeed();
        });
    }

    // --- Session rules, on stand-in beams ---

    /** A beam with a chosen strength, standing in for a real one: records what the clash did to it. */
    static final class StandIn implements ClashBeam {
        final LivingEntity owner;
        final Vec3 origin, dir;
        final float strength;
        boolean live = true;
        Boolean won;

        StandIn(LivingEntity owner, Vec3 origin, Vec3 toward, float strength) {
            this.owner = owner;
            this.origin = origin;
            this.dir = toward.subtract(origin).normalize();
            this.strength = strength;
        }

        @Override public LivingEntity beamOwner() { return owner; }
        @Override public ServerLevel beamLevel() { return (ServerLevel) owner.level(); }
        @Override public Vec3 beamOrigin() { return origin; }
        @Override public Vec3 beamDir() { return dir; }
        @Override public double beamRange() { return 30; }
        @Override public boolean beamLive() { return live; }
        @Override public String beamKind() { return "eld"; }
        @Override public float beamStrength() { return strength; }
        @Override public void enterClash(BeamClashSession session, Vec3 o, Vec3 d) {}
        @Override public void leaveClash(boolean w, int extraTicks) {
            won = w;
            live = w;
        }
    }

    private static StandIn[] pair(GameTestHelper h, float sa, float sb) {
        floor(h, 16);
        TrainingDummy a = character(h, RyuCharacter.ID, 3.5, 0.8);
        TrainingDummy b = character(h, RyuCharacter.ID, 3.5, 6.0);
        Vec3 oa = a.getEyePosition(), ob = b.getEyePosition();
        return new StandIn[] {new StandIn(a, oa, ob, sa), new StandIn(b, ob, oa, sb)};
    }

    @GameTest(maxTicks = 80, padding = 8, environment = ENV)
    public void aFarStrongerBeamOverpowersAtOnce(GameTestHelper h) {
        StandIn[] p = pair(h, 1f, 0.3f);
        BeamClashSession s = BeamClashManager.clashNow(p[0], p[1]);
        h.assertTrue(s.overpowered() && s.phase() == BeamClashSession.Phase.RESOLVE && s.outcome() == BeamClashSession.Outcome.A,
                "decided on contact (" + s.phase() + " " + s.outcome() + ")");
        h.succeedWhen(() -> {
            h.assertTrue(s.phase() == BeamClashSession.Phase.ENDED, "resolved");
            h.assertTrue(Boolean.TRUE.equals(p[0].won) && Boolean.FALSE.equals(p[1].won), "the strong beam goes through, the spent one collapses");
            h.assertTrue(!ClashCommon.clashing(p[0].owner) && !ClashCommon.clashing(p[1].owner), "both free");
            p[0].owner.discard();
            p[1].owner.discard();
        });
    }

    @GameTest(maxTicks = 420, padding = 8, environment = ENV)
    public void evenBeamsStruggleTwiceAsLongThenResolve(GameTestHelper h) {
        StandIn[] p = pair(h, 1f, 0.8f);
        BeamClashSession s = BeamClashManager.clashNow(p[0], p[1]);
        h.assertTrue(!s.overpowered() && s.phase() == BeamClashSession.Phase.INTRO, "close in strength: no instant win");
        h.assertValueEqual(BeamClashSession.DUEL_TICKS, 2 * 104, "the sustained duel is twice the original 104 ticks");
        Watch w = new Watch();
        w.session = s;
        h.onEachTick(() -> w.see(h, p[0].owner));
        h.succeedWhen(() -> {
            h.assertTrue(s.phase() == BeamClashSession.Phase.ENDED && w.duelAt >= 0 && w.resolveAt >= 0, "played out (" + s.phase() + ")");
            long duel = w.resolveAt - w.duelAt;
            h.assertTrue(duel >= BeamClashSession.DUEL_TICKS && duel <= BeamClashSession.DUEL_TICKS + 31, "duel lasted " + duel + " ticks");
            h.assertTrue(s.outcome() != BeamClashSession.Outcome.CANCELLED && s.outcome() != BeamClashSession.Outcome.NONE, "decided");
            p[0].owner.discard();
            p[1].owner.discard();
        });
    }

    @GameTest(maxTicks = 160, padding = 8, environment = ENV)
    public void aCancelledBeamEndsTheClashCleanly(GameTestHelper h) {
        StandIn[] p = pair(h, 1f, 1f);
        BeamClashSession s = BeamClashManager.clashNow(p[0], p[1]);
        h.runAfterDelay(BeamClashSession.INTRO_TICKS + 20, () -> {
            h.assertTrue(s.phase() == BeamClashSession.Phase.DUEL, "mid-struggle");
            p[1].live = false;
            BeamClashManager.beamGone(p[1]);
            h.assertTrue(s.phase() == BeamClashSession.Phase.ENDED && s.outcome() == BeamClashSession.Outcome.CANCELLED, "ended at once");
            h.assertTrue(Boolean.TRUE.equals(p[0].won), "the other beam goes on uncontested");
            h.runAfterDelay(4, () -> {
                h.assertTrue(!ClashCommon.clashing(p[0].owner) && !ClashCommon.clashing(p[1].owner), "nobody left held");
                h.assertTrue(BeamClashManager.sessionOf(p[0].owner) == null, "the session is gone");
                p[0].owner.discard();
                p[1].owner.discard();
                h.succeed();
            });
        });
    }
}
