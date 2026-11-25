package dev.rick.jjk.battle;

import java.util.EnumSet;
import java.util.Set;

/**
 * One move of the turn-based battle mode. Turn-based moves are their own definitions, not the real-time abilities: a
 * move says how fast it is, what it needs, what it does to the target (stance, range) and how it chains; the cinematic
 * layer plays it from the beats the engine produces.
 *
 * <p>No Minecraft types here: the engine runs (and is unit-tested and simulated) on its own.
 */
public final class BattleMove {
    /** Strike beats Technique (interrupts the cast), Technique beats Guard (pierces it), Guard beats Strike (parry). */
    public enum Category {
        STRIKE, TECHNIQUE, GUARD, FOCUS, MOVE, DOMAIN;

        public boolean beats(Category o) {
            return this == STRIKE && o == TECHNIQUE || this == TECHNIQUE && o == GUARD || this == GUARD && o == STRIKE;
        }

        public boolean attack() {
            return this == STRIKE || this == TECHNIQUE;
        }
    }

    /** How fast an attack comes out: its base initiative. Heavier hits harder but can be beaten to the punch. */
    public enum Weight {
        LIGHT(3), MEDIUM(2), HEAVY(1);
        public final int speed;

        Weight(int speed) {
            this.speed = speed;
        }
    }

    /** How far apart the two fighters stand. Strikes mostly need CLOSE; techniques reach further. */
    public enum Range {
        CLOSE, MID, FAR;

        public Range closer() {
            return this == FAR ? MID : CLOSE;
        }

        public Range further() {
            return this == CLOSE ? MID : FAR;
        }
    }

    /** What a hit leaves the target in. Follow-ups need a stance; it lasts until the target's next turn without a hit. */
    public enum Stance { GROUNDED, STAGGERED, AIRBORNE, DOWN }

    public final String id, name, line;
    public final Category category;
    public final Weight weight;
    public final int power, cost, hits;
    /** Ranges the move can be used from. */
    public final Set<Range> from;
    /** A stance the target must be in for the move to be usable at all (null = any). */
    public final Stance needs;
    /** Stances the move can follow in a combo (the previous link left the target in one of them). */
    public final Set<Stance> linkFrom;
    public final Stance inflicts;
    /** Range after the move lands (null = unchanged). */
    public final Range rangeAfter;
    public final boolean blackFlash, pierce;
    /** Damage multiplier against an airborne target (a slam). */
    public final float vsAirborne;
    /** MOVE only: -1 closes in to CLOSE, +1 backs off one step. */
    public final int moveStep;

    private BattleMove(Builder b) {
        id = b.id; name = b.name; line = b.line; category = b.category; weight = b.weight; power = b.power; cost = b.cost;
        hits = b.hits; from = b.from; needs = b.needs; linkFrom = b.linkFrom; inflicts = b.inflicts; rangeAfter = b.rangeAfter;
        blackFlash = b.blackFlash; pierce = b.pierce; vsAirborne = b.vsAirborne; moveStep = b.moveStep;
    }

    /** Whether this move can follow {@code prev} in a combo. */
    public boolean linksAfter(BattleMove prev) {
        return category.attack() && prev.category.attack() && linkFrom.contains(prev.inflicts);
    }

    @Override
    public String toString() {
        return id;
    }

    public static Builder of(String id, String name, Category category) {
        return new Builder(id, name, category);
    }

    public static final class Builder {
        private final String id, name;
        private final Category category;
        private String line = "";
        private Weight weight = Weight.MEDIUM;
        private int power, cost, hits = 1, moveStep;
        private Set<Range> from = EnumSet.allOf(Range.class);
        private Stance needs;
        private Set<Stance> linkFrom = EnumSet.noneOf(Stance.class);
        private Stance inflicts = Stance.GROUNDED;
        private Range rangeAfter;
        private boolean blackFlash, pierce;
        private float vsAirborne = 1f;

        private Builder(String id, String name, Category category) {
            this.id = id; this.name = name; this.category = category;
        }

        public Builder line(String s) { line = s; return this; }
        public Builder weight(Weight w) { weight = w; return this; }
        public Builder power(int p) { power = p; return this; }
        public Builder cost(int c) { cost = c; return this; }
        public Builder hits(int h) { hits = h; return this; }
        public Builder from(Range first, Range... rest) { from = EnumSet.of(first, rest); return this; }
        public Builder needs(Stance s) { needs = s; linkFrom = EnumSet.of(s); return this; }
        public Builder linkFrom(Stance first, Stance... rest) { linkFrom = EnumSet.of(first, rest); return this; }
        public Builder inflicts(Stance s) { inflicts = s; return this; }
        public Builder rangeAfter(Range r) { rangeAfter = r; return this; }
        public Builder blackFlash() { blackFlash = true; return this; }
        public Builder pierce() { pierce = true; return this; }
        public Builder vsAirborne(float m) { vsAirborne = m; return this; }
        public Builder moveStep(int s) { moveStep = s; return this; }

        public BattleMove build() {
            return new BattleMove(this);
        }
    }
}
