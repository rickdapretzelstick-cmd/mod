package dev.rick.jjk.core.domain.clash;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.domain.DomainInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/** One side of a clash and everything tracked about how they are playing. */
public final class ClashParticipant {
    public final int index;
    public final LivingEntity entity;
    public final DomainInstance domain;
    /** Non-null for non-player participants: how well they play (0..1). */
    @Nullable Float botSkill;

    boolean[] judged = new boolean[0];
    // Pre-rolled bot timing for the current round: when (ticks from the note) it presses, and whether it fumbles.
    double[] botOffset = new double[0];
    boolean[] botMisses = new boolean[0];

    int score;
    int roundScore;
    int streak;
    int maxStreak;
    int perfect, great, good, miss, ghost;
    int hits;
    double offsetSum;

    ClashParticipant(int index, LivingEntity entity, DomainInstance domain) {
        this.index = index;
        this.entity = entity;
        this.domain = domain;
    }

    void beginRound(int notes, Random rng) {
        judged = new boolean[notes];
        botOffset = new double[notes];
        botMisses = new boolean[notes];
        roundScore = 0;
        if (botSkill != null) {
            float skill = Math.max(0f, Math.min(1f, botSkill));
            // Timing spread shrinks with skill; lapses become rare.
            double sigmaMs = 8 + (1 - skill) * 150;
            for (int i = 0; i < notes; i++) {
                botOffset[i] = rng.nextGaussian() * sigmaMs / 50.0;
                botMisses[i] = rng.nextFloat() > 0.25f + 0.75f * skill;
            }
        }
    }

    /** A player who left the game can't play on: every remaining note counts as missed. */
    boolean forfeit() {
        return !entity.isAlive() || entity.isRemoved() || entity instanceof ServerPlayer sp && sp.hasDisconnected();
    }

    public int score() {
        return score;
    }

    public int streak() {
        return streak;
    }

    public int maxStreak() {
        return maxStreak;
    }

    public int perfects() {
        return perfect;
    }

    public int misses() {
        return miss;
    }

    public int judgedCount() {
        return perfect + great + good + miss;
    }

    /** Accuracy in [0, 1]: judgement values relative to all-perfect. */
    public float accuracy() {
        int n = judgedCount() + ghost;
        if (n == 0) return 1f;
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        double got = perfect * 1.0 + great * (double) cfg.scoreGreat / cfg.scorePerfect + good * (double) cfg.scoreGood / cfg.scorePerfect;
        return (float) (got / n);
    }

    /** Timing consistency: mean absolute offset of hits, in milliseconds. */
    public double meanOffsetMs() {
        return hits == 0 ? 999 : offsetSum / hits;
    }

    public boolean isBot() {
        return botSkill != null;
    }
}
