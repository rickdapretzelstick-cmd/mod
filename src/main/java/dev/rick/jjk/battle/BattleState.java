package dev.rick.jjk.battle;

import dev.rick.jjk.battle.BattleMove.Range;

/** The whole battle: two fighters, the distance between them, the turn count and a deterministic random stream. */
public final class BattleState {
    public static final int TURN_LIMIT = 60;

    public final Fighter[] f;
    public Range range = Range.MID;
    public int turn = 1;
    /** -1 while the battle runs; then 0 or 1, or 2 for a draw. */
    public int winner = -1;
    private long seed;

    public BattleState(BattleKit a, BattleKit b, long seed) {
        this(new Fighter[] {new Fighter(a), new Fighter(b)}, seed);
    }

    private BattleState(Fighter[] f, long seed) {
        this.f = f;
        this.seed = seed;
    }

    public boolean over() {
        return winner >= 0;
    }

    /** A copy for look-ahead. {@code reseed} gives it its own random stream so a search can't peek at the real rolls. */
    public BattleState copy(long reseed) {
        BattleState s = new BattleState(new Fighter[] {f[0].copy(), f[1].copy()}, reseed);
        s.range = range;
        s.turn = turn;
        s.winner = winner;
        return s;
    }

    public long seed() {
        return seed;
    }

    /** SplitMix64: small, fast, and the same everywhere (server, client, tests). */
    public double roll() {
        long z = (seed += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
