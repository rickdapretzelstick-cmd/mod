package dev.rick.jjk.progression.training;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scores one attempt against its schedule. The server's instance is the authority; the client runs an identical one
 * only to answer a press the instant it happens (the server's verdict replaces it when it arrives).
 *
 * <ul>
 *   <li>Each input carries the check it is for and when (on the sender's clock) it happened. That stamp is honoured only
 *   within a bounded window behind the receiver's own clock (latency compensation), never ahead of it beyond a little
 *   slack, and never earlier than an input already taken; outside that it is clamped, so a late packet can't buy a
 *   better timing.</li>
 *   <li>A press is taken only on a key that is up (a held key never presses again, so it can't satisfy a later tap),
 *   and only for the check the receiver itself finds for that key at that moment; anything else is stale and ignored.</li>
 *   <li>A press while the needle is only travelling (no check cued on that key) does nothing. Once a check is cued, a
 *   press outside its window (early or late) is its miss. A check let pass is a miss once its window (plus the
 *   latency allowance) is over. Each check is decided once, so it can cost at most one mistake.</li>
 *   <li>A hold check: the press must land in its window, then the release in its release window: one success or one
 *   miss for the pair.</li>
 * </ul>
 */
public final class TrainingEvaluator {
    public static final int PRESS = 0, RELEASE = 1;

    /** A decided (or held) check. */
    public record Outcome(int check, int result) {}

    private final List<TrainingCheck> checks;
    private final int maxMisses, latencyMs, slackMs;
    /** Per check: -1 undecided, HOLDING, PERFECT, GOOD or MISS. */
    private final int[] result;
    private final int[] pressJudge;
    private final boolean[] down = new boolean[TrainingSchedule.GAUGES];
    private int misses, successes, perfects;
    private int lastSeq = -1;
    private double lastStamp = Double.NEGATIVE_INFINITY;

    public TrainingEvaluator(List<TrainingCheck> checks, int maxMisses, int latencyMs, int slackMs) {
        this.checks = checks;
        this.maxMisses = Math.max(1, maxMisses);
        this.latencyMs = Math.max(0, latencyMs);
        this.slackMs = Math.max(0, slackMs);
        result = new int[checks.size()];
        pressJudge = new int[checks.size()];
        Arrays.fill(result, -1);
    }

    public List<TrainingCheck> checks() {
        return checks;
    }

    public int misses() {
        return misses;
    }

    public int successes() {
        return successes;
    }

    public int perfects() {
        return perfects;
    }

    /** -1 undecided, HOLDING, PERFECT, GOOD or MISS. */
    public int result(int check) {
        return check < 0 || check >= result.length ? -1 : result[check];
    }

    public int[] results() {
        return result;
    }

    public boolean failed() {
        return misses >= maxMisses;
    }

    public boolean isDown(int gauge) {
        return down[gauge];
    }

    /** Every check decided (none waiting, none held). */
    public boolean allDecided() {
        for (int r : result) if (r < 0 || r == TrainingCheck.HOLDING) return false;
        return true;
    }

    /** The check an input on {@code gauge} at {@code t} would be for (see {@link TrainingSchedule#candidate}). */
    @Nullable
    public TrainingCheck candidate(int gauge, double t) {
        return TrainingSchedule.candidate(checks, result, gauge, t);
    }

    /** The hold check currently held on {@code gauge}, if any. */
    @Nullable
    public TrainingCheck holding(int gauge) {
        for (TrainingCheck c : checks) if (c.gauge() == gauge && result[c.id()] == TrainingCheck.HOLDING) return c;
        return null;
    }

    /**
     * An input. {@code seq} increases with every input the sender sends; {@code stampMs} is the sender's time for it and
     * {@code nowMs} the receiver's. Returns what it decided, or null if it was ignored (a duplicate, stale, a key
     * already down, or only needle travel).
     */
    @Nullable
    public Outcome input(int seq, int action, int gauge, int check, double stampMs, double nowMs) {
        if (seq <= lastSeq) return null;
        lastSeq = seq;
        if (gauge < 0 || gauge >= TrainingSchedule.GAUGES || failed()) return null;
        double t = Math.max(nowMs - latencyMs, Math.min(nowMs + slackMs, stampMs));
        t = Math.max(t, lastStamp);
        lastStamp = t;
        if (action == PRESS) {
            if (down[gauge]) return null;
            down[gauge] = true;
            TrainingCheck c = candidate(gauge, t);
            if (c == null || c.id() != check || result[c.id()] == TrainingCheck.HOLDING) return null;
            int j = c.judge(c.atMs(), t);
            if (j == TrainingCheck.MISS) return decide(c, TrainingCheck.MISS);
            if (c.hold()) {
                result[c.id()] = TrainingCheck.HOLDING;
                pressJudge[c.id()] = j;
                return new Outcome(c.id(), TrainingCheck.HOLDING);
            }
            return decide(c, j);
        }
        if (action == RELEASE) {
            if (!down[gauge]) return null;
            down[gauge] = false;
            TrainingCheck c = holding(gauge);
            if (c == null || c.id() != check) return null;
            int j = c.judge(c.releaseMs(), t);
            if (j == TrainingCheck.MISS) return decide(c, TrainingCheck.MISS);
            return decide(c, j == TrainingCheck.PERFECT && pressJudge[c.id()] == TrainingCheck.PERFECT ? TrainingCheck.PERFECT : TrainingCheck.GOOD);
        }
        return null;
    }

    /** Checks let pass (or held past their release window): misses, once {@code nowMs} is beyond any allowance. */
    public List<Outcome> expire(double nowMs) {
        List<Outcome> out = new ArrayList<>();
        if (failed()) return out;
        for (TrainingCheck c : checks) {
            int r = result[c.id()];
            if (r >= 0 && r != TrainingCheck.HOLDING) continue;
            int deadline = r == TrainingCheck.HOLDING ? c.releaseEnd() : c.windowEnd();
            if (nowMs > deadline + latencyMs) {
                out.add(decide(c, TrainingCheck.MISS));
                if (failed()) break;
            }
        }
        return out;
    }

    /** Takes the authority's verdict over a prediction (client side). */
    public void force(int check, int verdict, int authoritativeMisses) {
        if (check < 0 || check >= result.length) return;
        int was = result[check];
        if (was == verdict) {
            misses = authoritativeMisses;
            return;
        }
        if (was == TrainingCheck.PERFECT) {
            successes--;
            perfects--;
        } else if (was == TrainingCheck.GOOD) {
            successes--;
        }
        result[check] = verdict;
        if (verdict == TrainingCheck.PERFECT) {
            successes++;
            perfects++;
        } else if (verdict == TrainingCheck.GOOD) {
            successes++;
        }
        misses = authoritativeMisses;
    }

    /** Lets go of every key (focus lost, a screen opened): held checks are released as of now. */
    public void forgetKeys() {
        Arrays.fill(down, false);
    }

    private Outcome decide(TrainingCheck c, int verdict) {
        result[c.id()] = verdict;
        if (verdict == TrainingCheck.MISS) misses++;
        else {
            successes++;
            if (verdict == TrainingCheck.PERFECT) perfects++;
        }
        return new Outcome(c.id(), verdict);
    }
}
