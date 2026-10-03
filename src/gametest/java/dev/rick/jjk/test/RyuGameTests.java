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
import dev.rick.jjk.registry.ModEntities;
import dev.rick.jjk.ryu.RyuCharacter;
import dev.rick.jjk.ryu.RyuCombat;
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
 * Ryu Ishigori (True Cannon): his kit and Overheat (Granite Blast heats him, 100% shuts his discharges off, Restyle
 * cools him), Every Last Drop awakening him when fired from 80% or more, and the beam clash: an Every Last Drop answered
 * by True Love Beam (and the reverse) becomes one session that is played out and decided, and is cleaned up when a
 * contestant dies. They run as their own batch (their beams and windows reach far enough to touch neighbouring plots).
 */
public class RyuGameTests {
    /**
     * Only the settings these tests need, set on whichever config is live: the test classes run side by side and share
     * one config, so replacing it would change numbers under another class's running tests.
     */
    private static void applyConfig() {
        JJKConfig cfg = JJKConfig.get();
        cfg.general.autoAssignGojo = false;
        // True Love Beam kept inside one test's area (as YutaGameTests sets it).
        cfg.yuta.beamRange = 13;
        cfg.yuta.beamQuickRange = 11;
        // True Cannon's long-range discharges kept inside one test's area.
        cfg.ryu.graniteRange = 14;
        cfg.ryu.graniteHeldRange = 14;
        cfg.ryu.eldRange = 14;
        cfg.ryu.appetizerRange = 14;
        cfg.ryu.appetizerRayRange = 12;
        cfg.ryu.secondHelpingRange = 12;
        cfg.ryu.invitedWallRange = 12;
        cfg.ryu.invitedWallHeldRange = 14;
    }

    private static void floor(GameTestHelper h, int size) {
        applyConfig();
        for (int x = -2; x < size; x++) for (int z = -2; z < size; z++) {
            h.setBlock(x, 0, z, Blocks.STONE);
            // The plot's own barrier ring: his discharges stop at solid blocks, and an answer needs to see its beam.
            for (int y = 1; y < 8; y++) if (h.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).is(Blocks.BARRIER)) h.setBlock(x, y, z, Blocks.AIR);
        }
    }

    private static TrainingDummy dummy(GameTestHelper h, double x, double z) {
        TrainingDummy d = h.spawn(ModEntities.TRAINING_DUMMY, new Vec3(x, 1, z));
        d.setMode(TrainingDummy.Mode.STAND);
        d.setAutoHeal(false);
        return d;
    }

    private static TrainingDummy character(GameTestHelper h, String id, double x, double z, LivingEntity face) {
        TrainingDummy d = dummy(h, x, z);
        d.setOnGround(true);
        CharacterService.assign(d, Characters.get(id));
        Casters.get(d).setNoCost(true);
        if (face != null) face(d, face.getBoundingBox().getCenter());
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

    private static void release(LivingEntity e, AbilitySlot slot) {
        Casters.get(e).input(slot, false, 0, 0, null);
    }

    // --- Kit ---

    @GameTest(maxTicks = 20, padding = 8, environment = "jjk-test:ryu")
    public void hisKitIsBoundAndSelectable(GameTestHelper h) {
        floor(h, 6);
        TrainingDummy r = character(h, RyuCharacter.ID, 2, 2, null);
        AbilityCaster c = Casters.get(r);
        h.assertValueEqual(c.character().id, RyuCharacter.ID, "True Cannon assigned");
        AbilitySlot[] slots = {AbilitySlot.SKILL_1, AbilitySlot.SKILL_2, AbilitySlot.SKILL_3, AbilitySlot.SKILL_4, AbilitySlot.SKILL_5, AbilitySlot.ULTIMATE};
        String[] base = {"granite_blast", "unsatisfied", "second_helping", "appetizer", "restyle", "every_last_drop"};
        for (int i = 0; i < slots.length; i++) h.assertValueEqual(c.ability(slots[i]).id, base[i], "base slot " + slots[i]);
        c.enterAwakening();
        String[] awk = {"what_are_you_after", "no_idea", "dessert", "not_invited"};
        for (int i = 0; i < 4; i++) h.assertValueEqual(c.ability(slots[i]).id, awk[i], "Decadence slot " + slots[i]);
        h.succeed();
    }

    // --- Overheat ---

    @GameTest(maxTicks = 60, padding = 8, environment = "jjk-test:ryu")
    public void graniteBlastHitsAndHeatsHim(GameTestHelper h) {
        floor(h, 14);
        TrainingDummy target = dummy(h, 2.5, 10.5);
        TrainingDummy r = character(h, RyuCharacter.ID, 2.5, 2.5, target);
        float hp = target.getHealth();
        h.assertTrue(press(r, AbilitySlot.SKILL_1), "Granite Blast (" + Casters.get(r).lastRefusal + ")");
        h.runAfterDelay(1, () -> {
            face(r, target.getBoundingBox().getCenter());
            release(r, AbilitySlot.SKILL_1);
        });
        h.succeedWhen(() -> {
            float dealt = hp - target.getHealth();
            h.assertTrue(Math.abs(RyuCombat.heat(r) - RyuCombat.cfg().heatGranite) < 0.6f, "+20% Overheat: it fired (" + RyuCombat.heat(r) + ")");
            h.assertTrue(dealt >= RyuCombat.cfg().graniteDamage - 0.6f, "the tapped blast hit (" + dealt + "; ray now finds " + RyuCombat.ray(r, RyuCombat.cannon(r), r.getLookAngle(), 14, 0.35, false).size()
                    + ", look " + r.getLookAngle() + ", clear for " + RyuCombat.rayEnd(r, RyuCombat.cannon(r), r.getLookAngle(), 14).distanceTo(RyuCombat.cannon(r))
                    + " to " + h.getLevel().getBlockState(net.minecraft.core.BlockPos.containing(RyuCombat.rayEnd(r, RyuCombat.cannon(r), r.getLookAngle(), 14).add(r.getLookAngle().scale(0.05)))) + ", target at " + target.position().subtract(r.position()) + ", last " + (target.getLastDamageSource() == null ? "-" : target.getLastDamageSource().getMsgId()) + ")");
        });
    }

    @GameTest(maxTicks = 80, padding = 8, environment = "jjk-test:ryu")
    public void overheatedHeCannotDischargeUntilHeRestyles(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy r = character(h, RyuCharacter.ID, 2.5, 2.5, null);
        RyuCombat.setHeat(r, 100);
        h.assertTrue(!press(r, AbilitySlot.SKILL_1), "Granite Blast refused at 100%");
        h.assertTrue(press(r, AbilitySlot.SKILL_5), "Restyle (" + Casters.get(r).lastRefusal + ")");
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(r).isBusy(), "the comb is done");
            h.assertTrue(RyuCombat.heat(r) < 1f, "from 100% the comb cools him all the way (" + RyuCombat.heat(r) + ")");
            h.assertTrue(RyuCombat.canDischarge(r), "his discharges are back");
        });
    }

    @GameTest(maxTicks = 60, padding = 8, environment = "jjk-test:ryu")
    public void restyleCoolsSixtyPercent(GameTestHelper h) {
        floor(h, 8);
        TrainingDummy r = character(h, RyuCharacter.ID, 2.5, 2.5, null);
        RyuCombat.setHeat(r, 80);
        h.assertTrue(press(r, AbilitySlot.SKILL_5), "Restyle (" + Casters.get(r).lastRefusal + ")");
        h.succeedWhen(() -> {
            h.assertTrue(!Casters.get(r).isBusy(), "done");
            h.assertTrue(Math.abs(RyuCombat.heat(r) - 20f) < 1f, "80% -> 20% (" + RyuCombat.heat(r) + ")");
        });
    }

    // --- Every Last Drop ---

    @GameTest(maxTicks = 200, padding = 8, environment = "jjk-test:ryu")
    public void everyLastDropFromEightyPercentAwakensHim(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy target = dummy(h, 2.5, 12.5);
        TrainingDummy r = character(h, RyuCharacter.ID, 2.5, 2.5, target);
        AbilityCaster c = Casters.get(r);
        c.setAwakening(c.maxAwakening());
        RyuCombat.setHeat(r, 85);
        r.setHealth(r.getMaxHealth() * 0.5f);
        float hp = target.getHealth(), mine = r.getHealth();
        h.assertTrue(press(r, AbilitySlot.ULTIMATE), "Every Last Drop (" + c.lastRefusal + ")");
        h.succeedWhen(() -> {
            h.assertTrue(hp - target.getHealth() >= RyuCombat.cfg().eldMinDamage * 0.5f, "it hit hard (" + (hp - target.getHealth()) + ")");
            h.assertTrue(c.isAwakened(), "fired from 85%: Decadence");
            h.assertTrue(RyuCombat.heat(r) >= 99.9f, "Overheat set to 100%");
            h.assertTrue(r.getHealth() > mine + 10, "and he healed (" + mine + " -> " + r.getHealth() + ")");
        });
    }

    // --- Beam clash ---

    /** Yuta awakened with Rika's moveset up (True Love Beam on skill 3). */
    private static void rikaAwakened(GameTestHelper h, TrainingDummy y, Runnable then) {
        Casters.get(y).enterAwakening();
        YutaState.of(y).fists = true;
        h.assertTrue(press(y, AbilitySlot.SKILL_5), "Rika (fully manifested: she comes out)");
        h.runAfterDelay(10, () -> {
            h.assertTrue(press(y, AbilitySlot.SKILL_5), "her moveset");
            then.run();
        });
    }

    @GameTest(maxTicks = 420, padding = 8, environment = "jjk-test:ryu")
    public void everyLastDropAnswersTrueLoveBeamAndTheClashIsDecided(GameTestHelper h) {
        floor(h, 16);
        // Both inside the plot (entities beyond it don't tick), Rika's spot behind Yuta too.
        TrainingDummy y = character(h, YutaCharacter.ID, 3.5, 1.6, null);
        TrainingDummy r = character(h, RyuCharacter.ID, 3.5, 7.2, y);
        face(y, r.getBoundingBox().getCenter());
        AbilityCaster rc = Casters.get(r);
        rc.setAwakening(rc.maxAwakening());
        BeamClashSession[] session = new BeamClashSession[1];
        boolean[] answered = new boolean[1];
        StringBuilder trace = new StringBuilder();
        rikaAwakened(h, y, () -> {
            face(y, r.getBoundingBox().getCenter());
            h.assertTrue(press(y, AbilitySlot.SKILL_3), "True Love Beam (" + Casters.get(y).lastRefusal + ")");
        });
        h.onEachTick(() -> {
            // His Ultimate only counters while the window is open (otherwise it would be an ordinary cast).
            if (!answered[0] && BeamClashManager.windowOpen(r)) {
                h.assertTrue(press(r, AbilitySlot.ULTIMATE), "the counter (" + rc.lastRefusal + ")");
                answered[0] = true;
            }
            BeamClashSession s = BeamClashManager.sessionOf(r);
            if (s != null) session[0] = s;
            AbilityCaster yc = Casters.get(y);
            if (answered[0] && trace.length() < 900) trace.append(yc.cast() == null ? "-" : yc.cast().ability.id.substring(0, 3) + yc.cast().age() + (yc.cast().isFinished() ? "F" : "") + (dev.rick.jjk.core.combat.Combat.state(y).shouldInterruptCasting() ? "S" : "")).append(' ');
        });
        h.succeedWhen(() -> {
            h.assertTrue(answered[0], "the counter window opened for him (ryu " + r.getId() + ", " + BeamClashManager.lastMiss + ")");
            h.assertTrue(session[0] != null, "one session for the two of them");
            h.assertTrue(session[0].entity(0) == y || session[0].entity(1) == y, "Yuta is in it");
            h.assertTrue(session[0].phase() == BeamClashSession.Phase.ENDED, "played out");
            BeamClashSession.Outcome o = session[0].outcome();
            h.assertTrue(o == BeamClashSession.Outcome.A || o == BeamClashSession.Outcome.B || o == BeamClashSession.Outcome.TIE, "decided (" + o + ": " + session[0].reason() + "; yuta casts " + trace + ")");
            h.assertTrue(BeamClashManager.sessionOf(y) == null && BeamClashManager.sessionOf(r) == null, "and gone");
            h.assertTrue(!ClashCommon.clashing(y) && !ClashCommon.clashing(r), "both free to move again");
        });
    }

    @GameTest(maxTicks = 360, padding = 8, environment = "jjk-test:ryu")
    public void trueLoveBeamAnswersEveryLastDropAndDeathEndsTheClash(GameTestHelper h) {
        floor(h, 16);
        TrainingDummy r = character(h, RyuCharacter.ID, 3.5, 0.8, null);
        TrainingDummy y = character(h, YutaCharacter.ID, 3.5, 6.0, r);
        face(r, y.getBoundingBox().getCenter());
        AbilityCaster rc = Casters.get(r), yc = Casters.get(y);
        rc.setAwakening(rc.maxAwakening());
        yc.setAwakening(yc.maxAwakening());
        BeamClashSession[] session = new BeamClashSession[1];
        boolean[] answered = new boolean[1];
        long[] killedAt = {-1};
        h.assertTrue(press(r, AbilitySlot.ULTIMATE), "Every Last Drop (" + rc.lastRefusal + ")");
        h.onEachTick(() -> {
            if (!answered[0] && BeamClashManager.windowOpen(y)) {
                h.assertTrue(press(y, AbilitySlot.ULTIMATE), "Yuta's counter (" + yc.lastRefusal + ")");
                answered[0] = true;
            }
            BeamClashSession s = BeamClashManager.sessionOf(y);
            if (s != null) session[0] = s;
            // Mid-duel, Ryu dies: the clash must end and let Yuta go.
            if (killedAt[0] < 0 && s != null && s.phase() == BeamClashSession.Phase.DUEL) {
                r.kill((ServerLevel) h.getLevel());
                killedAt[0] = h.getTick();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(answered[0], "the counter window opened for Yuta (yuta " + y.getId() + ", " + BeamClashManager.lastMiss + ")");
            h.assertTrue(session[0] != null && killedAt[0] >= 0, "they clashed, and he died in it (" + (session[0] == null ? "no session" : session[0].phase() + " " + session[0].reason()) + ")");
            h.assertTrue(session[0].phase() == BeamClashSession.Phase.ENDED, "the session ended");
            h.assertTrue(BeamClashManager.sessionOf(y) == null, "nothing left of it");
            h.assertTrue(!ClashCommon.clashing(y), "Yuta free to move");
        });
    }
}
