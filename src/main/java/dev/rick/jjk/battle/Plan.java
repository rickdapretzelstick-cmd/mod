package dev.rick.jjk.battle;

import java.util.List;

/**
 * What a fighter locks in for a turn: an opener, then up to two combo links, and how much cursed energy they pour in
 * (each level costs {@link BattleEngine#BOOST_COST} and adds one initiative to every move of the plan).
 */
public record Plan(List<String> chain, int boost) {
    public Plan {
        chain = List.copyOf(chain);
    }

    public static Plan of(int boost, String... moves) {
        return new Plan(List.of(moves), boost);
    }

    public static Plan of(String... moves) {
        return of(0, moves);
    }

    public String opener() {
        return chain.isEmpty() ? null : chain.getFirst();
    }
}
