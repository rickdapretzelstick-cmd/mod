package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Range;
import dev.rick.jjk.battle.BattleMove.Stance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * A one-turn look-ahead opponent: it tries each of its plans against each of the foe's likely plans on a copy of the
 * battle, scores the outcomes, and picks among the best with a little randomness (so it can't be read perfectly). Used
 * for solo battles and to play thousands of AI-vs-AI matches when balancing.
 */
public final class BattleAI {
    private BattleAI() {}

    /** Every plan {@code side} may lock in now: each opener, each combo route from it, and a boosted version. */
    public static List<Plan> plans(BattleState s, int side) {
        Fighter me = s.f[side];
        List<Plan> out = new ArrayList<>();
        for (BattleMove m : me.kit.all()) {
            if (BattleEngine.whyNot(s, side, m) != null) continue;
            if (!m.category.attack()) {
                out.add(Plan.of(m.id));
                continue;
            }
            List<List<String>> routes = new ArrayList<>();
            routes(me.kit, m, BattleEngine.rangeAfter(m, s.range), new ArrayList<>(List.of(m.id)), routes);
            for (List<String> r : routes) {
                for (int boost = 0; boost <= 1; boost++) {
                    Plan p = new Plan(r, boost);
                    if (BattleEngine.validate(s, side, p) == null) out.add(p);
                }
            }
        }
        return out;
    }

    private static void routes(BattleKit kit, BattleMove prev, Range r, List<String> sofar, List<List<String>> out) {
        out.add(List.copyOf(sofar));
        if (sofar.size() >= BattleEngine.MAX_CHAIN) return;
        for (BattleMove m : BattleEngine.links(kit, prev, r)) {
            sofar.add(m.id);
            routes(kit, m, BattleEngine.rangeAfter(m, r), sofar, out);
            sofar.removeLast();
        }
    }

    /** The plan the foe would most likely commit to with each opener: its longest unboosted route. */
    private static List<Plan> likelyFoePlans(BattleState s, int side) {
        Map<String, Plan> best = new LinkedHashMap<>();
        for (Plan p : plans(s, side)) {
            if (p.boost() != 0) continue;
            Plan cur = best.get(p.opener());
            if (cur == null || p.chain().size() > cur.chain().size()) best.put(p.opener(), p);
        }
        return new ArrayList<>(best.values());
    }

    /**
     * Picks a plan for {@code side}. {@code temperature} 0 always takes the best-scoring plan; higher values play looser.
     */
    public static Plan choose(BattleState s, int side, double temperature, long salt) {
        List<Plan> mine = plans(s, side);
        if (mine.isEmpty()) return Plan.of(s.f[side].kit.guard.id);
        List<Plan> theirs = likelyFoePlans(s, 1 - side);
        if (theirs.isEmpty()) theirs = List.of(Plan.of(s.f[1 - side].kit.guard.id));
        double[] score = new double[mine.size()];
        double top = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < mine.size(); i++) {
            double sum = 0;
            for (int j = 0; j < theirs.size(); j++) {
                BattleState c = s.copy(salt * 31 + i * 1_000_003L + j);
                if (side == 0) BattleEngine.resolve(c, mine.get(i), theirs.get(j));
                else BattleEngine.resolve(c, theirs.get(j), mine.get(i));
                sum += evaluate(c, side);
            }
            score[i] = sum / theirs.size();
            top = Math.max(top, score[i]);
        }
        SplittableRandom rng = new SplittableRandom(salt);
        if (temperature <= 0) {
            for (int i = 0; i < mine.size(); i++) if (score[i] == top) return mine.get(i);
        }
        double total = 0;
        double[] w = new double[mine.size()];
        for (int i = 0; i < mine.size(); i++) total += w[i] = Math.exp((score[i] - top) / temperature);
        double pick = rng.nextDouble() * total;
        for (int i = 0; i < mine.size(); i++) if ((pick -= w[i]) <= 0) return mine.get(i);
        return mine.getLast();
    }

    /** How good the battle looks for {@code side}. */
    public static double evaluate(BattleState s, int side) {
        if (s.over()) return s.winner == side ? 1000 : s.winner == 2 ? 0 : -1000;
        Fighter me = s.f[side], foe = s.f[1 - side];
        double v = (me.hp - foe.hp) + 0.15 * (me.ce - foe.ce) + 0.08 * (me.meter - foe.meter) + 3 * (me.momentum - foe.momentum);
        if (foe.stance == Stance.STAGGERED || foe.stance == Stance.AIRBORNE) v += 3;
        if (me.stance == Stance.STAGGERED || me.stance == Stance.AIRBORNE) v -= 3;
        return v;
    }
}
