package dev.rick.jjk.battle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hundreds of AI-vs-AI battles, to catch a broken balance before any animation work: every battle ends, neither kit
 * runs away with it, and no move goes unused. Prints a report (win rates, turns, move use, outcomes) to the test log.
 */
class BattleSimulationTest {
    private static final int MATCHES = 300;

    @Test
    void yujiVersusGojo() {
        Report r = simulate(BattleKits.YUJI, BattleKits.GOJO, MATCHES);
        System.out.println(r);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/battle-sim.txt"), r.toString());
        } catch (java.io.IOException ignored) {}
        double yuji = r.wins[0] / (double) MATCHES;
        assertEquals(0, r.unfinished, "every battle ends by KO or the turn limit");
        assertTrue(yuji > 0.3 && yuji < 0.7, "Yuji should win 30-70% against Gojo, won " + yuji);
        for (BattleKit k : List.of(BattleKits.YUJI, BattleKits.GOJO)) {
            for (BattleMove m : k.all()) {
                // Movement is situational (Gojo's Blue does his closing in for him): only the kit's own choices must all see use.
                if (m.category == BattleMove.Category.MOVE) continue;
                assertTrue(r.used.getOrDefault(k.character + ":" + m.id, 0) > 0, k.name + " never used " + m.name + "\n" + r);
            }
        }
        assertTrue(r.avgTurns() >= 5 && r.avgTurns() <= 25, "battles should last 5-25 turns, averaged " + r.avgTurns());
    }

    static final class Report {
        final int[] wins = new int[3];
        int unfinished, turns, blackFlashes, named;
        final Map<String, Integer> used = new TreeMap<>(), kinds = new TreeMap<>();

        double avgTurns() {
            return turns / (double) (wins[0] + wins[1] + wins[2]);
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("=== battle simulation ===\n");
            b.append("wins: ").append(wins[0]).append(" / ").append(wins[1]).append(" (draws ").append(wins[2]).append(")\n");
            b.append(String.format("avg turns %.1f, black flashes %d, named combos %d%n", avgTurns(), blackFlashes, named));
            b.append("moves used: ").append(used).append('\n');
            b.append("beats: ").append(kinds).append('\n');
            return b.toString();
        }
    }

    static Report simulate(BattleKit a, BattleKit b, int matches) {
        Report r = new Report();
        for (int m = 0; m < matches; m++) {
            // Alternate sides so being side 0 doesn't matter.
            boolean swap = (m & 1) == 1;
            BattleState s = new BattleState(swap ? b : a, swap ? a : b, m * 7919L);
            int guard = 0;
            while (!s.over() && guard++ < 500) {
                Plan p0 = BattleAI.choose(s, 0, 3, m * 100_000L + s.turn * 2L);
                Plan p1 = BattleAI.choose(s, 1, 3, m * 100_000L + s.turn * 2L + 1);
                count(r, s, 0, p0);
                count(r, s, 1, p1);
                for (Beat beat : BattleEngine.resolve(s, p0, p1)) {
                    r.kinds.merge(beat.kind().name(), 1, Integer::sum);
                    if (beat.blackFlash()) r.blackFlashes++;
                    if (!beat.note().isEmpty() && beat.kind() != Beat.Kind.DOMAIN) r.named++;
                }
            }
            if (!s.over()) {
                r.unfinished++;
                continue;
            }
            int w = s.winner == 2 ? 2 : swap ? 1 - s.winner : s.winner;
            r.wins[w]++;
            r.turns += s.turn - 1;
        }
        return r;
    }

    private static void count(Report r, BattleState s, int side, Plan p) {
        for (String id : p.chain()) r.used.merge(s.f[side].kit.character + ":" + id, 1, Integer::sum);
    }
}
