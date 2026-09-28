package dev.rick.jjk.core.domain;

import dev.rick.jjk.core.domain.structure.ClashTerritory;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The boundary between two clashing domains. While the duel runs it follows the clash meter (the side that is playing
 * better pushes it toward the other); once the duel is decided the winner's side sweeps across and consumes the loser's
 * space step by step (50/50, 60/40, 75/25, 90/10, all of it) before the loser's domain ends.
 */
public final class ClashFront {
    /** How far the boundary can be pushed each way during the duel (as a fraction of the distance between centers). */
    static final double SWING = 0.35;
    public static final int CONQUEST_TICKS = 60;

    /** A is the clash's first participant (the meter's +1 side), B the second. */
    final DomainInstance a, b;
    final ClashTerritory territory;
    /** The boundary along the line from A's center (0) to B's (1). */
    double t = 0.5;
    @Nullable DomainInstance winner;
    int conquestAge;
    private double conquestFrom, conquestTo;

    ClashFront(DomainInstance a, DomainInstance b, ClashTerritory territory) {
        this.a = a;
        this.b = b;
        this.territory = territory;
        territory.paint(t);
    }

    /** Follows the duel's meter (+1: A winning). */
    void follow(float meter) {
        double target = 0.5 + Mth.clamp(meter, -1, 1) * SWING;
        t += (target - t) * 0.25;
        territory.paint(t);
    }

    /** The duel is decided: the winner's side starts consuming the loser's. */
    void beginConquest(DomainInstance winner) {
        this.winner = winner;
        conquestAge = 0;
        conquestFrom = t;
        // All the way past the loser's far wall.
        var loserStructure = (winner == a ? territory.second() : territory.first());
        double reach = loserStructure.spec != null ? loserStructure.spec.radius() + 1.5 : (winner == a ? b : a).radius + 2;
        conquestTo = winner == a ? 1 + territory.axisFraction(reach) : -territory.axisFraction(reach);
    }

    /** Advances the conquest; returns true once the loser's space is entirely taken. */
    boolean conquer() {
        conquestAge++;
        float k = Mth.clamp(conquestAge / (float) CONQUEST_TICKS, 0, 1);
        // Slow at first, then the loser's space caves in.
        double eased = k * k * (3 - 2 * k);
        t = Mth.lerp(eased, conquestFrom, conquestTo);
        territory.paint(t);
        return conquestAge >= CONQUEST_TICKS;
    }

    public boolean conquering() {
        return winner != null;
    }

    @Nullable
    public DomainInstance winner() {
        return winner;
    }

    public DomainInstance first() {
        return a;
    }

    public DomainInstance second() {
        return b;
    }

    /** The boundary as a fraction of the way from {@code d}'s center to the other domain's. */
    public double splitFor(DomainInstance d) {
        return d == a ? t : 1 - t;
    }

    public DomainInstance other(DomainInstance d) {
        return d == a ? b : a;
    }

    /**
     * The share of the clash each side holds right now (A's share), from 0.5 at the start to 1 or 0 once conquered.
     * Measured along the axis between the two far walls.
     */
    public double shareOfFirst() {
        double lo = -territory.axisFraction(territory.first().spec.radius() + 1.5), hi = 1 + territory.axisFraction(territory.second().spec.radius() + 1.5);
        return Mth.clamp((t - lo) / (hi - lo), 0, 1);
    }
}
