package dev.rick.jjk.progression.training;

import dev.rick.jjk.config.JJKConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Builds a training attempt's skill checks from a seed: the same seed and settings always give the same schedule, and
 * the server sends the result to the client, so both judge against exactly the same checks. Every check obeys the
 * rules that keep it fair:
 * <ul>
 *   <li>its cue comes at least {@link JJKConfig.Training#telegraphMs} before its window (longer for an announced
 *   disturbance), and the needle never laps the arc on the way in;</li>
 *   <li>on one key, a check's cue never comes before the previous check on that key is over, and the windows are at
 *   least {@link JJKConfig.Training#sameKeyGapMs} apart (so a key that must stay held is never asked to tap);</li>
 *   <li>windows on different keys are staggered by {@link JJKConfig.Training#staggerMs}, unless deliberately aligned
 *   (announced as a paired or triple input);</li>
 *   <li>the GOOD window narrows by phase (0-15 s, 15-30 s, 30-45 s); PERFECT is a fixed fraction of it.</li>
 * </ul>
 * Each character shapes the overlap differently (one at a time, then two, then all three), but every one of them is
 * built from these rules.
 */
public final class TrainingSchedule {
    public static final int GAUGES = 3;
    /** The needles' resting speed and starting angles (before any cue). */
    public static final int REST_PERIOD = 2400;
    private static final double[] START_ANGLE = {0, 120, 240};
    /** Where needle travel is integrated from (the start of the intro, well before the first cue). */
    private static final int ORIGIN_MS = -4000;

    private TrainingSchedule() {}

    // --- Building ---

    public static List<TrainingCheck> generate(String kit, long seed, JJKConfig.Training cfg) {
        Plan p = new Plan(cfg, new SplittableRandom(seed));
        switch (kit) {
            case "gojo" -> gojo(p);
            case "yuta" -> yuta(p);
            case "ryu" -> ryu(p);
            case "hakari" -> hakari(p);
            default -> yuji(p);
        }
        return p.finish();
    }

    /** Yuji: three steady speeds; then emotional disturbances (announced) on pairs; then all three overlapping. */
    private static void yuji(Plan p) {
        JJKConfig.Training c = p.cfg;
        int[] base = {1500, 1800, 2100};
        int t = 1100, last = -1;
        while (t < c.phase2AtMs) {
            int g = p.other(last);
            TrainingCheck k = p.place(g, t, 0, base[g], TrainingCheck.PLAIN, 850);
            if (k == null) break;
            last = g;
            t = k.atMs() + 1150 + p.r.nextInt(300);
        }
        int[] calm = {2000, 2150, 2300};
        int n = 0;
        while (t < c.phase3AtMs) {
            int[] o = p.order();
            int mod = n % 2 == 1 ? (n % 4 == 1 ? TrainingCheck.FAST : TrainingCheck.SLOW) : TrainingCheck.PLAIN;
            TrainingCheck a = p.place(o[0], t, 0, disturbed(calm[o[0]], mod), mod, mod == TrainingCheck.PLAIN ? 800 : c.disturbanceTelegraphMs);
            if (a == null) break;
            TrainingCheck b = p.place(o[1], a.atMs() + 280 + p.r.nextInt(220), 0, calm[o[1]], TrainingCheck.PLAIN, 800);
            t = Math.max(a.atMs(), b == null ? 0 : b.atMs()) + 1300 + p.r.nextInt(300);
            n++;
        }
        while (t < c.durationMs - 1200) {
            int[] o = p.order();
            int disturbed = n++ % 2 == 0 ? p.r.nextInt(3) : -1;
            int at = t;
            TrainingCheck lastPlaced = null;
            for (int i = 0; i < 3; i++) {
                int mod = i == disturbed ? (p.r.nextBoolean() ? TrainingCheck.FAST : TrainingCheck.SLOW) : TrainingCheck.PLAIN;
                TrainingCheck k = p.place(o[i], at, 0, disturbed(calm[o[i]], mod), mod, mod == TrainingCheck.PLAIN ? 700 : c.disturbanceTelegraphMs);
                if (k == null) break;
                lastPlaced = k;
                at = k.atMs() + 200 + p.r.nextInt(140);
            }
            if (lastPlaced == null) break;
            t = lastPlaced.atMs() + 1250 + p.r.nextInt(250);
        }
    }

    /**
     * The settings a containment is built and judged with: the training's rules squeezed into its few seconds (the
     * same telegraph, gaps and stagger; its three phases a third of it each).
     */
    public static JJKConfig.Training containmentRules(JJKConfig.Training cfg) {
        JJKConfig.Training c = new JJKConfig.Training();
        c.durationMs = cfg.captureDurationMs;
        c.introMs = cfg.captureIntroMs;
        c.phase2AtMs = cfg.captureDurationMs / 3;
        c.phase3AtMs = cfg.captureDurationMs * 2 / 3;
        c.phase1GoodMs = cfg.phase1GoodMs;
        c.phase2GoodMs = cfg.phase2GoodMs;
        c.phase3GoodMs = cfg.phase2GoodMs;
        c.perfectFraction = cfg.perfectFraction;
        c.telegraphMs = cfg.telegraphMs;
        c.disturbanceTelegraphMs = cfg.disturbanceTelegraphMs;
        c.sameKeyGapMs = cfg.sameKeyGapMs;
        c.staggerMs = cfg.staggerMs;
        c.maxMisses = cfg.captureMaxMisses;
        c.maxLatencyMs = cfg.maxLatencyMs;
        c.clockSlackMs = cfg.clockSlackMs;
        c.syncTimeoutMs = cfg.syncTimeoutMs;
        return c;
    }

    /**
     * Containing a Grade 1 essence: a few seconds with all three circles going at once, their windows overlapping
     * (one key's check is coming while another's is live), staggered by the same rules as training.
     */
    public static List<TrainingCheck> containment(long seed, JJKConfig.Training cfg) {
        Plan p = new Plan(containmentRules(cfg), new SplittableRandom(seed));
        int[] speed = {1500, 1700, 1900};
        int t = 900;
        int[] o = p.order();
        for (int n = 0; t < p.cfg.durationMs - 400; n++) {
            int g = o[n % 3];
            if (n % 3 == 2) o = p.order();
            TrainingCheck k = p.place(g, t, 0, speed[g], TrainingCheck.PLAIN, 650);
            if (k == null) break;
            t = k.atMs() + 330 + p.r.nextInt(160);
        }
        return p.finish();
    }

    /** The settings a strength contest runs with (no skill-check schedule: its keys are its own, see StrengthContests). */
    public static JJKConfig.Training contestRules(JJKConfig.Training cfg) {
        JJKConfig.Training c = containmentRules(cfg);
        c.introMs = cfg.contestIntroMs;
        c.maxMisses = 1_000;
        return c;
    }

    private static int disturbed(int period, int mod) {
        return switch (mod) {
            case TrainingCheck.FAST -> Math.round(period * 0.72f);
            case TrainingCheck.SLOW -> Math.round(period * 1.35f);
            default -> period;
        };
    }

    /** Gojo: one object at a time, then two staggered, then all three with narrow crossings. */
    private static void gojo(Plan p) {
        JJKConfig.Training c = p.cfg;
        int[] base = {1400, 1650, 1900};
        int t = 1100;
        while (t < c.phase2AtMs) {
            int[] o = p.order();
            for (int i = 0; i < 3 && t < c.phase2AtMs; i++) {
                TrainingCheck k = p.place(o[i], t, 0, base[o[i]], TrainingCheck.PLAIN, 800);
                if (k == null) return;
                t = k.atMs() + 1250 + p.r.nextInt(200);
            }
        }
        while (t < c.phase3AtMs) {
            int[] o = p.order();
            TrainingCheck a = p.place(o[0], t, 0, base[o[0]], TrainingCheck.PLAIN, 700);
            if (a == null) return;
            TrainingCheck b = p.place(o[1], a.atMs() + 320 + p.r.nextInt(200), 0, base[o[1]], TrainingCheck.PLAIN, 700);
            t = Math.max(a.atMs(), b == null ? 0 : b.atMs()) + 1500 + p.r.nextInt(300);
        }
        while (t < c.durationMs - 1200) {
            int[] o = p.order();
            int at = t;
            TrainingCheck last = null;
            for (int i = 0; i < 3; i++) {
                TrainingCheck k = p.place(o[i], at, 0, base[o[i]], TrainingCheck.PLAIN, 650);
                if (k == null) break;
                last = k;
                at = k.atMs() + 170 + p.r.nextInt(60);
            }
            if (last == null) return;
            t = last.atMs() + 1500 + p.r.nextInt(300);
        }
    }

    /** Yuta: taps; then a labelled hold on one gauge at a time; then a hold under two taps; three staggered releases. */
    private static void yuta(Plan p) {
        JJKConfig.Training c = p.cfg;
        int[] base = {1500, 1700, 1900};
        int t = 1100, last = -1;
        while (t < c.phase2AtMs) {
            int g = p.other(last);
            TrainingCheck k = p.place(g, t, 0, base[g], TrainingCheck.PLAIN, 800);
            if (k == null) break;
            last = g;
            t = k.atMs() + 1200 + p.r.nextInt(250);
        }
        int h = p.r.nextInt(3);
        while (t < c.phase3AtMs) {
            int hold = 700 + p.r.nextInt(200);
            TrainingCheck k = p.place(h, t, hold, hold + 650, TrainingCheck.PLAIN, 800);
            if (k == null) break;
            int other = (h + 1 + p.r.nextInt(2)) % 3;
            TrainingCheck tap = p.place(other, k.releaseMs() + 450, 0, base[other], TrainingCheck.PLAIN, 750);
            t = (tap == null ? k.releaseMs() : tap.atMs()) + 1000 + p.r.nextInt(250);
            h = (h + 1) % 3;
        }
        while (t < 38_500) {
            int hold = 1100 + p.r.nextInt(200);
            TrainingCheck k = p.place(h, t, hold, hold + 600, TrainingCheck.PLAIN, 750);
            if (k == null) break;
            int a = (h + 1) % 3, b = (h + 2) % 3;
            if (p.r.nextBoolean()) {
                int s = a;
                a = b;
                b = s;
            }
            TrainingCheck ta = p.place(a, k.atMs() + 360, 0, base[a], TrainingCheck.PLAIN, 650);
            if (ta != null) p.place(b, ta.atMs() + 330, 0, base[b], TrainingCheck.PLAIN, 650);
            t = k.releaseMs() + 1000 + p.r.nextInt(200);
            h = (h + 1) % 3;
        }
        // The finish: all three held, released one after another, each release clearly cued.
        int f = Math.min(Math.max(t, 39_800), 41_500);
        int[] o = p.order();
        for (int i = 0; i < 3; i++) p.place(o[i], f + i * 450, 1800, 2450, TrainingCheck.FINALE, 800);
    }

    /** Ryu: moderate and staggered; faster with more overlap; all three; then three tight, well telegraphed checks. */
    private static void ryu(Plan p) {
        JJKConfig.Training c = p.cfg;
        int[] base = {1600, 1450, 1750};
        int t = 1100, last = -1;
        while (t < c.phase2AtMs) {
            int g = p.other(last);
            TrainingCheck k = p.place(g, t, 0, base[g], TrainingCheck.PLAIN, 800);
            if (k == null) break;
            last = g;
            t = k.atMs() + 1050 + p.r.nextInt(250);
        }
        while (t < c.phase3AtMs) {
            int[] o = p.order();
            TrainingCheck a = p.place(o[0], t, 0, Math.round(base[o[0]] * 0.85f), TrainingCheck.PLAIN, 700);
            if (a == null) break;
            TrainingCheck b = p.place(o[1], a.atMs() + 250 + p.r.nextInt(150), 0, Math.round(base[o[1]] * 0.85f), TrainingCheck.PLAIN, 700);
            t = Math.max(a.atMs(), b == null ? 0 : b.atMs()) + 1350 + p.r.nextInt(250);
        }
        while (t < 40_500) {
            int[] o = p.order();
            int at = t;
            TrainingCheck lastPlaced = null;
            for (int i = 0; i < 3; i++) {
                TrainingCheck k = p.place(o[i], at, 0, Math.round(base[o[i]] * 0.85f), TrainingCheck.PLAIN, 650);
                if (k == null) break;
                lastPlaced = k;
                at = k.atMs() + 200 + p.r.nextInt(100);
            }
            if (lastPlaced == null) break;
            t = lastPlaced.atMs() + 1450 + p.r.nextInt(250);
        }
        int f = Math.max(t, 42_900);
        for (int g = 0; g < 3; g++) p.place(g, f + g * 450, 0, 1700, TrainingCheck.FINALE, 1000);
    }

    /** Hakari: a predictable beat; alternating and paired; rapid bursts; then the announced triple, all aligned. */
    private static void hakari(Plan p) {
        JJKConfig.Training c = p.cfg;
        int t = 1200, i = 0;
        while (t < c.phase2AtMs) {
            TrainingCheck k = p.place(i % 3, t, 0, 1500, TrainingCheck.PLAIN, 750);
            if (k == null) break;
            t = Math.max(t + 750, k.atMs() + 750);
            i++;
        }
        int n = 0;
        while (t < c.phase3AtMs) {
            if (n++ % 2 == 0) {
                TrainingCheck k = p.place(p.r.nextInt(3), t, 0, 1450, TrainingCheck.PLAIN, 700);
                t = (k == null ? t : k.atMs()) + 900;
            } else {
                int skip = p.r.nextInt(3);
                List<TrainingCheck> pair = p.placeAligned(new int[] {(skip + 1) % 3, (skip + 2) % 3}, t, 1450, TrainingCheck.ALIGNED, 750);
                t = (pair.isEmpty() ? t : pair.get(0).atMs()) + 1100;
            }
        }
        int[] periods = {1300, 1500, 1700};
        while (t < 41_000) {
            int[] o = p.order();
            int at = t;
            TrainingCheck lastPlaced = null;
            for (int k = 0; k < 3; k++) {
                TrainingCheck ck = p.place(o[k], at, 0, periods[o[k]], TrainingCheck.PLAIN, 650);
                if (ck == null) break;
                lastPlaced = ck;
                at = ck.atMs() + 200;
            }
            if (lastPlaced == null) break;
            t = lastPlaced.atMs() + 1700 + p.r.nextInt(200);
        }
        p.placeAligned(new int[] {0, 1, 2}, Math.max(t + 600, 43_400), 1800, TrainingCheck.ALIGNED, 1300);
    }

    /** The schedule being built. */
    private static final class Plan {
        final JJKConfig.Training cfg;
        final SplittableRandom r;
        final List<TrainingCheck> out = new ArrayList<>();
        final int[] lastEnd = {Integer.MIN_VALUE / 4, Integer.MIN_VALUE / 4, Integer.MIN_VALUE / 4};

        Plan(JJKConfig.Training cfg, SplittableRandom r) {
            this.cfg = cfg;
            this.r = r;
        }

        int good(int at) {
            return at < cfg.phase2AtMs ? cfg.phase1GoodMs : at < cfg.phase3AtMs ? cfg.phase2GoodMs : cfg.phase3GoodMs;
        }

        int perfect(int good) {
            return Math.max(16, Math.round(good * cfg.perfectFraction));
        }

        int other(int last) {
            if (last < 0) return r.nextInt(3);
            return (last + 1 + r.nextInt(2)) % 3;
        }

        int[] order() {
            int[] o = {0, 1, 2};
            for (int i = 2; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int s = o[i];
                o[i] = o[j];
                o[j] = s;
            }
            return o;
        }

        /** Places one check as near {@code at} as the rules allow (only ever later), or null when it no longer fits. */
        TrainingCheck place(int gauge, int at, int holdMs, int period, int mod, int lead) {
            List<TrainingCheck> got = placeAligned(new int[] {gauge}, at, holdMs, period, mod, lead);
            return got.isEmpty() ? null : got.get(0);
        }

        List<TrainingCheck> placeAligned(int[] gauges, int at, int period, int mod, int lead) {
            return placeAligned(gauges, at, 0, period, mod, lead);
        }

        /** Places checks on {@code gauges} with one shared centre (aligned with each other, staggered from the rest). */
        List<TrainingCheck> placeAligned(int[] gauges, int at, int holdMs, int period, int mod, int lead) {
            lead = Math.max(lead, cfg.telegraphMs);
            if (mod == TrainingCheck.FAST || mod == TrainingCheck.SLOW) lead = Math.max(lead, cfg.disturbanceTelegraphMs);
            at = Math.max(at, lead + cfg.phase1GoodMs + 200);
            for (int iter = 0; iter < 40; iter++) {
                int good = good(at);
                int need = at;
                for (int g : gauges) {
                    // The cue (the telegraph before the window opens) never comes before the last check on this key is over.
                    need = Math.max(need, lastEnd[g] + lead + good / 2);
                    need = Math.max(need, lastEnd[g] + cfg.sameKeyGapMs + good / 2 + 1);
                }
                int[] mine = holdMs > 0 ? new int[] {at, at + holdMs} : new int[] {at};
                for (TrainingCheck c : out) {
                    if (contains(gauges, c.gauge())) continue;
                    int[] theirs = c.hold() ? new int[] {c.atMs(), c.releaseMs()} : new int[] {c.atMs()};
                    for (int m : mine) {
                        for (int o : theirs) {
                            if (Math.abs(m - o) < cfg.staggerMs) need = Math.max(need, at + (o + cfg.staggerMs - m));
                        }
                    }
                }
                if (need == at) break;
                at = need;
            }
            int good = good(at);
            int pf = perfect(good);
            int cue = at - good / 2 - lead;
            int per = Math.max(period, at - cue + good + 150);
            if (holdMs > 0) per = Math.max(per, holdMs + good + 200);
            int end = holdMs > 0 ? at + holdMs + (good - good / 2) : at + (good - good / 2);
            List<TrainingCheck> placed = new ArrayList<>();
            if (end > cfg.durationMs - 150) return placed;
            for (int g : gauges) {
                TrainingCheck c = new TrainingCheck(-1, g, holdMs > 0, cue, at, holdMs > 0 ? at + holdMs : at, good, pf, per, mod);
                out.add(c);
                lastEnd[g] = c.endMs();
                placed.add(c);
            }
            return placed;
        }

        private static boolean contains(int[] a, int v) {
            for (int x : a) if (x == v) return true;
            return false;
        }

        /** Sorted by centre, numbered in that order (a check's id is its index). */
        List<TrainingCheck> finish() {
            out.sort(Comparator.comparingInt(TrainingCheck::atMs).thenComparingInt(TrainingCheck::gauge));
            List<TrainingCheck> done = new ArrayList<>(out.size());
            for (int i = 0; i < out.size(); i++) {
                TrainingCheck c = out.get(i);
                done.add(new TrainingCheck(i, c.gauge(), c.hold(), c.cueMs(), c.atMs(), c.releaseMs(), c.goodMs(), c.perfectMs(), c.periodMs(), c.mod()));
            }
            return List.copyOf(done);
        }
    }

    // --- Reading a schedule (both sides) ---

    /**
     * Where a gauge's needle points at {@code t} (degrees clockwise from the top, 0-360): it travels at the resting speed
     * until the first cue on that gauge, then at each check's own speed from its cue on. Speeds change only at cues.
     */
    public static double angle(List<TrainingCheck> checks, int gauge, double t) {
        double a = START_ANGLE[gauge];
        double cursor = ORIGIN_MS;
        int period = REST_PERIOD;
        for (TrainingCheck c : checks) {
            if (c.gauge() != gauge) continue;
            if (c.cueMs() >= t) break;
            a += (c.cueMs() - cursor) / period * 360.0;
            cursor = c.cueMs();
            period = c.periodMs();
        }
        a += (t - cursor) / period * 360.0;
        a %= 360.0;
        return a < 0 ? a + 360 : a;
    }

    /** Degrees of arc a window {@code ms} wide covers on a check's gauge. */
    public static double arcDegrees(TrainingCheck c, int ms) {
        return ms * 360.0 / c.periodMs();
    }

    /**
     * The check an input on {@code gauge} at {@code t} is for: the first one on that gauge, not yet decided
     * ({@code result[i] < 0} or HOLDING), whose cue has come. Null while the needle is only travelling between checks.
     */
    public static TrainingCheck candidate(List<TrainingCheck> checks, int[] result, int gauge, double t) {
        for (TrainingCheck c : checks) {
            if (c.gauge() != gauge) continue;
            int r = result[c.id()];
            if (r >= 0 && r != TrainingCheck.HOLDING) continue;
            return c.cueMs() <= t ? c : null;
        }
        return null;
    }

    // --- Checking a schedule (tests, and a guard before an attempt starts) ---

    /** Everything about a schedule that breaks the fairness rules (empty when it is sound). */
    public static List<String> problems(List<TrainingCheck> checks, JJKConfig.Training cfg) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < checks.size(); i++) {
            TrainingCheck c = checks.get(i);
            if (c.id() != i) out.add("check " + i + " has id " + c.id());
            int lead = c.windowStart() - c.cueMs();
            int minLead = c.mod() == TrainingCheck.FAST || c.mod() == TrainingCheck.SLOW ? cfg.disturbanceTelegraphMs : cfg.telegraphMs;
            if (lead < minLead) out.add("check " + i + " telegraphed only " + lead + " ms before its window");
            int good = c.atMs() < cfg.phase2AtMs ? cfg.phase1GoodMs : c.atMs() < cfg.phase3AtMs ? cfg.phase2GoodMs : cfg.phase3GoodMs;
            if (c.goodMs() != good) out.add("check " + i + " window " + c.goodMs() + " in a " + good + " ms phase");
            if (Math.abs(c.perfectMs() - Math.round(good * cfg.perfectFraction)) > 1 && c.perfectMs() != 16) out.add("check " + i + " perfect " + c.perfectMs());
            if (c.cueMs() < 0 || c.endMs() > cfg.durationMs) out.add("check " + i + " outside the challenge");
            if (c.periodMs() < c.atMs() - c.cueMs() + c.goodMs()) out.add("check " + i + " needle laps its arc on the way in");
            if (c.hold()) {
                if (c.releaseStart() - c.windowEnd() < 150) out.add("check " + i + " release window too close to its press");
                if (c.periodMs() < c.releaseMs() - c.atMs() + c.goodMs()) out.add("check " + i + " needle laps between hold and release");
            }
        }
        for (int g = 0; g < GAUGES; g++) {
            TrainingCheck prev = null;
            for (TrainingCheck c : checks) {
                if (c.gauge() != g) continue;
                if (prev != null) {
                    if (c.cueMs() < prev.endMs()) out.add("check " + c.id() + " cued before check " + prev.id() + " on the same key is over");
                    if (c.windowStart() - prev.endMs() < cfg.sameKeyGapMs) out.add("checks " + prev.id() + "/" + c.id() + " closer than the same-key gap");
                }
                prev = c;
            }
        }
        for (TrainingCheck a : checks) {
            for (TrainingCheck b : checks) {
                if (a.id() >= b.id() || a.gauge() == b.gauge()) continue;
                boolean aligned = a.mod() == TrainingCheck.ALIGNED && b.mod() == TrainingCheck.ALIGNED && a.atMs() == b.atMs();
                if (aligned) continue;
                for (int m : a.hold() ? new int[] {a.atMs(), a.releaseMs()} : new int[] {a.atMs()}) {
                    for (int n : b.hold() ? new int[] {b.atMs(), b.releaseMs()} : new int[] {b.atMs()}) {
                        if (Math.abs(m - n) < cfg.staggerMs) out.add("checks " + a.id() + "/" + b.id() + " cross " + Math.abs(m - n) + " ms apart");
                    }
                }
            }
        }
        return out;
    }

    /** The most checks live at once (cue to end) in [from, to). */
    public static int maxConcurrent(List<TrainingCheck> checks, int from, int to) {
        int best = 0;
        for (TrainingCheck c : checks) {
            int t = Math.max(from, c.cueMs());
            if (t >= to || t > c.endMs()) continue;
            int n = 0;
            for (TrainingCheck d : checks) if (d.cueMs() <= t && t <= d.endMs()) n++;
            best = Math.max(best, n);
        }
        return best;
    }

    /** The phase (1-3) a moment of the challenge falls in. */
    public static int phase(JJKConfig.Training cfg, double t) {
        return t < cfg.phase2AtMs ? 1 : t < cfg.phase3AtMs ? 2 : 3;
    }
}
