package dev.rick.jjk.core.domain.clash;

import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.domain.DomainInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One domain clash, played as a rhythm duel. Every participant gets the same chart of timed prompts; each input is
 * judged on timing alone (PERFECT/GREAT/GOOD/MISS) and pushes a shared tug-of-war meter. Whoever holds the meter when
 * the chart ends (or drives it all the way across) wins. Domain strength, stats and activation order play no part.
 *
 * The meter runs from -1 (participant 1 wins) to +1 (participant 0 wins).
 */
public final class ClashSession {
    public enum Phase { COUNTDOWN, PLAYING, ENDED }

    /** Why the session ended. */
    public enum Outcome { NONE, KNOCKOUT, DECISION, SUDDEN_DEATH, TIEBREAK, CANCELLED }

    public final int id;
    public final ServerLevel level;
    final List<ClashParticipant> participants = new ArrayList<>();
    ClashChart chart;
    /** Game tick at which the chart clock reads 0 (end of the countdown). */
    long startTick;
    Phase phase = Phase.COUNTDOWN;
    float meter;
    /** 0 = main sequence, 1.. = tie breakers. */
    int round;
    Outcome outcome = Outcome.NONE;
    int winner = -1;
    private final Random rng;
    /** Set once the meter has been moved this tick (updates are batched into one sync per judgement). */
    ClashListener listener = ClashListener.NONE;

    ClashSession(int id, ServerLevel level, List<DomainInstance> domains, long seed) {
        this.id = id;
        this.level = level;
        this.rng = new Random(seed);
        for (int i = 0; i < domains.size(); i++) {
            DomainInstance d = domains.get(i);
            LivingEntity owner = d.owner;
            ClashParticipant p = new ClashParticipant(i, owner, d);
            if (!(owner instanceof Player)) {
                float skill = ClashManager.botSkillOf(owner);
                // Negative skill: driven by explicit input (tests, scripted fights), not by the built-in bot.
                if (skill >= 0) p.botSkill = skill;
            }
            participants.add(p);
        }
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        startRound(ClashChart.generate(seed, cfg.bpm, cfg.notes), cfg.countdownTicks);
    }

    // --- Rounds ---

    private void startRound(ClashChart chart, int countdown) {
        this.chart = chart;
        this.startTick = level.getGameTime() + countdown;
        this.phase = Phase.COUNTDOWN;
        for (ClashParticipant p : participants) p.beginRound(chart.size(), rng);
        listener.roundStarted(this);
    }

    void tick() {
        if (phase == Phase.ENDED) return;
        double now = clock();
        if (phase == Phase.COUNTDOWN && now >= 0) phase = Phase.PLAYING;
        if (phase != Phase.PLAYING) return;
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        double missAfter = cfg.goodWindowMs / 50.0 + cfg.missGraceTicks;
        for (ClashParticipant p : participants) {
            if (p.forfeit()) {
                for (int i = 0; i < chart.size() && phase == Phase.PLAYING; i++) if (!p.judged[i]) judge(p, i, ClashJudgement.MISS, 0);
                continue;
            }
            for (int i = 0; i < chart.size() && phase == Phase.PLAYING; i++) {
                if (p.judged[i]) continue;
                double t = chart.times()[i];
                if (p.botSkill != null && now >= t + p.botOffset[i]) {
                    // A non-player sorcerer "presses" when its own timing says so.
                    double offsetMs = p.botOffset[i] * 50.0;
                    if (!p.botMisses[i] && Math.abs(offsetMs) <= cfg.goodWindowMs) {
                        judge(p, i, rate(offsetMs), offsetMs);
                        continue;
                    }
                }
                if (now > t + missAfter) judge(p, i, ClashJudgement.MISS, 0);
            }
        }
        if (phase == Phase.PLAYING && allJudged()) decideRound();
    }

    private boolean allJudged() {
        for (ClashParticipant p : participants) for (boolean j : p.judged) if (!j) return false;
        return true;
    }

    /** Chart clock: ticks since the round's notes started (negative during the countdown). */
    public double clock() {
        return level.getGameTime() - startTick;
    }

    // --- Input ---

    /**
     * A participant pressed {@code lane} at {@code reportedTick} (their game-time clock, with sub-tick precision). The
     * reported time is trusted only within the latency allowance; anything implausible is judged at arrival.
     */
    public void input(LivingEntity who, int lane, double reportedTick) {
        ClashParticipant p = participant(who);
        if (p == null || phase != Phase.PLAYING || lane < 0 || lane > 3) return;
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        double now = level.getGameTime();
        double t = reportedTick;
        if (!Double.isFinite(t) || t > now + 2 || t < now - cfg.maxInputLatencyTicks) t = now;
        double rel = t - startTick;
        double window = cfg.goodWindowMs / 50.0;
        int best = -1;
        for (int i = 0; i < chart.size(); i++) {
            if (p.judged[i] || chart.lanes()[i] != lane) continue;
            double off = rel - chart.times()[i];
            if (Math.abs(off) <= window) {
                best = i;
                break; // earliest open note in this lane
            }
            if (chart.times()[i] - rel > window) break;
        }
        if (best < 0) {
            judge(p, -1, ClashJudgement.GHOST, 0);
            return;
        }
        double offsetMs = (rel - chart.times()[best]) * 50.0;
        judge(p, best, rate(offsetMs), offsetMs);
    }

    public static ClashJudgement rate(double offsetMs) {
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        double a = Math.abs(offsetMs);
        if (a <= cfg.perfectWindowMs) return ClashJudgement.PERFECT;
        if (a <= cfg.greatWindowMs) return ClashJudgement.GREAT;
        if (a <= cfg.goodWindowMs) return ClashJudgement.GOOD;
        return ClashJudgement.MISS;
    }

    private void judge(ClashParticipant p, int note, ClashJudgement j, double offsetMs) {
        if (phase != Phase.PLAYING) return;
        if (note >= 0) p.judged[note] = true;
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        float push;
        switch (j) {
            case PERFECT -> { p.perfect++; p.score += cfg.scorePerfect; push = cfg.pushPerfect; }
            case GREAT -> { p.great++; p.score += cfg.scoreGreat; push = cfg.pushGreat; }
            case GOOD -> { p.good++; p.score += cfg.scoreGood; push = cfg.pushGood; }
            case MISS -> { p.miss++; p.score += cfg.scoreMiss; push = cfg.pushMiss; }
            default -> { p.ghost++; push = cfg.pushGhostTap; }
        }
        if (j.hit()) {
            // Consistency is rewarded, but capped so a short streak can't decide the clash on its own.
            push *= 1f + Math.min(cfg.streakBonusCap, p.streak * cfg.streakBonusPerNote);
            p.streak++;
            p.maxStreak = Math.max(p.maxStreak, p.streak);
            p.offsetSum += Math.abs(offsetMs);
            p.hits++;
        } else {
            p.streak = 0;
        }
        if (note >= 0) p.roundScore += j.hit() ? scoreOf(j) : 0;
        meter = Mth.clamp(meter + (p.index == 0 ? push : -push), -1f, 1f);
        listener.judged(this, p, note, j, offsetMs);
        if (Math.abs(meter) >= 1f) finish(meter > 0 ? 0 : 1, Outcome.KNOCKOUT);
    }

    private static int scoreOf(ClashJudgement j) {
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        return switch (j) {
            case PERFECT -> cfg.scorePerfect;
            case GREAT -> cfg.scoreGreat;
            case GOOD -> cfg.scoreGood;
            default -> cfg.scoreMiss;
        };
    }

    // --- Deciding ---

    private void decideRound() {
        JJKConfig.Clash cfg = JJKConfig.get().clash;
        ClashParticipant a = participants.get(0), b = participants.get(1);
        if (Math.abs(meter) > cfg.tieMeter) {
            finish(meter > 0 ? 0 : 1, round == 0 ? Outcome.DECISION : Outcome.SUDDEN_DEATH);
            return;
        }
        int sa = round == 0 ? a.score : a.roundScore, sb = round == 0 ? b.score : b.roundScore;
        int top = Math.max(1, Math.max(sa, sb));
        if (Math.abs(sa - sb) > top * cfg.tieScoreFraction) {
            finish(sa > sb ? 0 : 1, round == 0 ? Outcome.DECISION : Outcome.SUDDEN_DEATH);
            return;
        }
        boolean finalSequence = "final_sequence".equalsIgnoreCase(cfg.tieMode);
        int maxRounds = finalSequence ? 1 : Math.max(0, cfg.suddenDeathRounds);
        if (round < maxRounds) {
            round++;
            meter = 0;
            int notes = finalSequence ? cfg.finalSequenceNotes : cfg.suddenDeathNotes;
            startRound(ClashChart.generate(rng.nextLong(), cfg.bpm * 1.1f, Math.max(1, notes)), 30);
            return;
        }
        // Still level after every tie breaker: decide on the whole performance, in a fixed order.
        finish(tiebreak(a, b), Outcome.TIEBREAK);
    }

    /** Total score, then perfects, then fewest misses, then tighter timing, then longest streak, then first participant. */
    static int tiebreak(ClashParticipant a, ClashParticipant b) {
        if (a.score != b.score) return a.score > b.score ? 0 : 1;
        if (a.perfect != b.perfect) return a.perfect > b.perfect ? 0 : 1;
        if (a.miss + a.ghost != b.miss + b.ghost) return a.miss + a.ghost < b.miss + b.ghost ? 0 : 1;
        if (Math.abs(a.meanOffsetMs() - b.meanOffsetMs()) > 0.5) return a.meanOffsetMs() < b.meanOffsetMs() ? 0 : 1;
        if (a.maxStreak != b.maxStreak) return a.maxStreak > b.maxStreak ? 0 : 1;
        return 0;
    }

    private void finish(int winner, Outcome outcome) {
        if (phase == Phase.ENDED) return;
        this.phase = Phase.ENDED;
        this.winner = winner;
        this.outcome = outcome;
        listener.ended(this);
    }

    /** Ends without a winner (a participant's domain disappeared). */
    void cancel() {
        if (phase == Phase.ENDED) return;
        phase = Phase.ENDED;
        winner = -1;
        outcome = Outcome.CANCELLED;
        listener.ended(this);
    }

    // --- Accessors ---

    @Nullable
    public ClashParticipant participant(LivingEntity e) {
        for (ClashParticipant p : participants) if (p.entity == e) return p;
        return null;
    }

    public List<ClashParticipant> participants() {
        return Collections.unmodifiableList(participants);
    }

    public ClashChart chart() {
        return chart;
    }

    public long startTick() {
        return startTick;
    }

    public Phase phase() {
        return phase;
    }

    public float meter() {
        return meter;
    }

    public int round() {
        return round;
    }

    public int winner() {
        return winner;
    }

    public Outcome outcome() {
        return outcome;
    }

    @Nullable
    public DomainInstance winnerDomain() {
        return winner < 0 ? null : participants.get(winner).domain;
    }

    /** Callbacks for presentation and domain resolution. */
    public interface ClashListener {
        ClashListener NONE = new ClashListener() {};

        default void roundStarted(ClashSession s) {}

        default void judged(ClashSession s, ClashParticipant p, int note, ClashJudgement j, double offsetMs) {}

        default void ended(ClashSession s) {}
    }
}
