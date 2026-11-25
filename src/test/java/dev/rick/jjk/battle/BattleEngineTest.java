package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleMove.Stance;
import dev.rick.jjk.battle.Beat.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The turn rules, one at a time. */
class BattleEngineTest {
    private static BattleState close(BattleKit a, BattleKit b) {
        BattleState s = new BattleState(a, b, 42);
        s.range = Range.CLOSE;
        return s;
    }

    private static Beat find(List<Beat> beats, int actor, Kind kind) {
        return beats.stream().filter(b -> b.actor() == actor && b.kind() == kind).findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " by " + actor + " in " + beats));
    }

    @Test
    void lightStrikeCutsOffAHeavyTechnique() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist"), Plan.of("red"));
        find(beats, 0, Kind.HIT);
        find(beats, 1, Kind.INTERRUPTED);
        assertEquals(Fighter.MAX_HP, s.f[0].hp);
        assertEquals(Stance.STAGGERED, s.f[1].stance);
    }

    @Test
    void slowerStrikeTradesThroughAFasterTechnique() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("crushing_blow"), Plan.of("blue"));
        find(beats, 1, Kind.HIT);
        find(beats, 0, Kind.TRADE);
        assertTrue(s.f[0].hp < Fighter.MAX_HP && s.f[1].hp < Fighter.MAX_HP);
    }

    @Test
    void guardParriesAHeavyStrike() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("crushing_blow"), Plan.of("guard"));
        Beat parry = find(beats, 0, Kind.PARRY);
        assertEquals(BattleKits.GOJO.parryDamage, parry.damage());
        assertEquals(Fighter.MAX_HP - BattleKits.GOJO.parryDamage, s.f[0].hp);
        assertEquals(Fighter.MAX_HP, s.f[1].hp);
        assertEquals(Stance.STAGGERED, s.f[0].stance);
    }

    @Test
    void guardBlocksAStrikeForChipAndLeavesTheAttackerOpen() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("cursed_strikes"), Plan.of("guard"));
        assertEquals(3, find(beats, 0, Kind.BLOCK).damage());
        assertEquals(Stance.STAGGERED, s.f[0].stance);
    }

    @Test
    void divergentFistGoesThroughAGuard() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist"), Plan.of("guard"));
        assertEquals(5, find(beats, 0, Kind.PIERCE).damage()); // Infinity halves it
    }

    @Test
    void infinityHalvesAPiercingTechnique() {
        BattleState s = close(BattleKits.GOJO, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("red"), Plan.of("guard"));
        assertEquals(11, find(beats, 0, Kind.PIERCE).damage());
    }

    @Test
    void equalInitiativeClashes() {
        BattleState s = close(BattleKits.YUJI, BattleKits.YUJI);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist"), Plan.of("divergent_fist"));
        find(beats, 0, Kind.CLASH);
        find(beats, 1, Kind.CLASH);
        assertEquals(97, s.f[0].hp);
        assertEquals(97, s.f[1].hp);
    }

    @Test
    void backingOffMakesASlowStrikeMiss() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("crushing_blow"), Plan.of("back_off"));
        find(beats, 1, Kind.MOVE);
        find(beats, 0, Kind.WHIFF);
        assertEquals(Range.MID, s.range);
    }

    @Test
    void blueReachesFromFarAndPullsIn() {
        BattleState s = new BattleState(BattleKits.GOJO, BattleKits.YUJI, 1);
        s.range = Range.FAR;
        BattleEngine.resolve(s, Plan.of("blue"), Plan.of("focus"));
        assertEquals(Range.CLOSE, s.range);
        assertEquals(Stance.STAGGERED, s.f[1].stance);
    }

    @Test
    void fullComboLandsOnAFocusingFoeAndEarnsItsName() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        s.f[0].control = 1; // three-move combos need control
        s.f[1].ce = 0;      // and no cursed energy for a reversal
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist", "manji_kick", "crushing_blow"), Plan.of("focus"));
        assertEquals(List.of(0, 1, 2), beats.stream().filter(b -> b.actor() == 0 && b.kind() == Kind.HIT).map(Beat::link).toList());
        assertEquals("Divergent Black Flash", beats.stream().filter(b -> b.move().equals("crushing_blow")).findFirst().orElseThrow().note());
        find(beats, 1, Kind.INTERRUPTED);
        assertEquals(Stance.DOWN, s.f[1].stance);
    }

    @Test
    void guardCatchesSlowStrikeLinksAfterAPiercingTechnique() {
        BattleState s = close(BattleKits.GOJO, BattleKits.YUJI);
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("blue", "twofold_kick"), Plan.of("guard"));
        find(beats, 0, Kind.PIERCE);
        find(beats, 0, Kind.BLOCK);

        BattleState t = close(BattleKits.GOJO, BattleKits.YUJI);
        List<Beat> boosted = BattleEngine.resolve(t, Plan.of(2, "blue", "twofold_kick"), Plan.of("guard"));
        assertEquals(2, boosted.stream().filter(b -> b.actor() == 0 && b.kind() == Kind.PIERCE).count());
    }

    @Test
    void unlimitedVoidCancelsTheFoesMove() {
        BattleState s = close(BattleKits.GOJO, BattleKits.YUJI);
        s.f[0].meter = Fighter.MAX_METER;
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("domain"), Plan.of("divergent_fist"));
        find(beats, 0, Kind.DOMAIN);
        find(beats, 1, Kind.STUNNED);
        assertEquals(Fighter.MAX_HP, s.f[0].hp);
        assertEquals(BattleEngine.EFFECT_TURNS, s.f[1].slowTurns + 1); // one turn already ticked
    }

    @Test
    void malevolentShrineBurnsEachTurn() {
        BattleState s = new BattleState(BattleKits.YUJI, BattleKits.GOJO, 3);
        s.f[0].meter = Fighter.MAX_METER;
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("domain"), Plan.of("guard"));
        find(beats, 0, Kind.DOMAIN);
        find(beats, 0, Kind.BURN);
        assertEquals(Fighter.MAX_HP - 10 - BattleEngine.BURN_DAMAGE, s.f[1].hp);
    }

    @Test
    void illegalPlansAreRefusedAndBecomeAGuard() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        assertNotNull(BattleEngine.validate(s, 0, Plan.of("divergent_fist", "crushing_blow")));
        assertNotNull(BattleEngine.validate(s, 0, Plan.of("manji_kick")));
        assertNotNull(BattleEngine.validate(s, 0, Plan.of("divergent_fist", "manji_kick", "crushing_blow")), "three moves need control");
        s.f[0].control = 1;
        assertNull(BattleEngine.validate(s, 0, Plan.of("divergent_fist", "manji_kick", "crushing_blow")));
        s.f[0].control = 0;
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("manji_kick"), Plan.of("twofold_kick"));
        find(beats, 1, Kind.BLOCK);
    }

    @Test
    void aCleanWinTakesControlAndTheFoeLosesIt() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        s.f[1].control = 2;
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist"), Plan.of("red"));
        assertTrue(beats.stream().anyMatch(b -> b.actor() == 0 && b.control()), "the winning opener is part of the sequence");
        assertEquals(1, s.f[0].control);
        assertEquals(0, s.f[1].control);
        // A trade gives nobody control.
        BattleState t = close(BattleKits.YUJI, BattleKits.GOJO);
        BattleEngine.resolve(t, Plan.of("crushing_blow"), Plan.of("blue"));
        assertEquals(0, t.f[0].control);
        assertEquals(0, t.f[1].control);
    }

    @Test
    void fullControlEndsAFullComboInTheFinisher() {
        BattleState s = close(BattleKits.YUJI, BattleKits.GOJO);
        s.f[0].control = Fighter.MAX_CONTROL;
        s.f[1].ce = 0;
        List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist", "manji_kick", "crushing_blow"), Plan.of("focus"));
        Beat fin = find(beats, 0, Kind.FINISHER);
        assertEquals(3, fin.link());
        assertTrue(fin.control());
        assertEquals("Divergent Black Flash", beats.stream().filter(b -> b.move().equals("crushing_blow")).findFirst().orElseThrow().note());
    }

    @Test
    void theDefenderCanReverseAndStealControl() {
        int reversals = 0;
        for (long seed = 0; seed < 200; seed++) {
            BattleState s = new BattleState(BattleKits.YUJI, BattleKits.GOJO, seed);
            s.range = Range.CLOSE;
            s.f[0].control = 1;
            s.f[1].ce = 100;
            List<Beat> beats = BattleEngine.resolve(s, Plan.of("divergent_fist", "manji_kick", "crushing_blow"), Plan.of("focus"));
            if (beats.stream().anyMatch(b -> b.kind() == Kind.REVERSAL)) {
                reversals++;
                assertEquals(1, s.f[1].control, "the reverser takes control");
                assertEquals(0, s.f[0].control);
            }
        }
        assertTrue(reversals > 10 && reversals < 120, "reversals should happen sometimes, not always: " + reversals);
    }

    @Test
    void sameSeedSameFight() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(play(seed), play(seed));
        }
    }

    private static List<List<Beat>> play(long seed) {
        BattleState s = new BattleState(BattleKits.YUJI, BattleKits.GOJO, seed);
        List<List<Beat>> log = new java.util.ArrayList<>();
        while (!s.over()) {
            Plan a = BattleAI.choose(s, 0, 3, seed * 1000 + s.turn), b = BattleAI.choose(s, 1, 3, seed * 2000 + s.turn);
            log.add(BattleEngine.resolve(s, a, b));
        }
        return log;
    }
}
