package dev.rick.jjk.client.clash;

import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.core.domain.clash.ClashJudgement;
import dev.rick.jjk.core.net.ClashEndPayload;
import dev.rick.jjk.core.net.ClashInputPayload;
import dev.rick.jjk.core.net.ClashStartPayload;
import dev.rick.jjk.core.net.ClashUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client side of a domain clash: the round being played, a sub-tick clock shared by drawing and input, local
 * prediction of the player's own presses (so feedback is instant), and the server's authoritative judgements.
 */
public final class ClashClient {
    /** Everything the HUD and world effects need about the clash in progress. */
    public static final class View {
        public final int session;
        public final int round;
        public final long startTick;
        public final int[] entities, domains, colors;
        public final List<String> names;
        public final float[] times;
        public final byte[] lanes;
        public final int perfectMs, greatMs, goodMs;
        public final float beatTicks;
        /** Index of the local player, or -1 when watching someone else's clash. */
        public final int local;
        public float meter;
        public float shownMeter;
        public final int[] score, streak;
        public final float[] accuracy;
        /** Per participant, per note: judgement id + 1 once judged (0 = open). */
        public final byte[][] result;
        public final int[] lastJudge;
        public final long[] lastJudgeAt;
        public final float[] lastOffset;
        public final long[][] laneFlashAt;
        /** Cumulative perfect streak energy per side (drives HUD intensity). */
        public final float[] heat;

        View(ClashStartPayload p, int local) {
            this.local = local;
            this.session = p.session();
            this.round = p.round();
            this.startTick = p.startTick();
            this.entities = p.entities();
            this.domains = p.domains();
            this.colors = p.colors().clone();
            // Mirror matches (the same domain on both sides): the opponent is drawn in a contrasting colour.
            if (colors.length == 2 && (colors[0] & 0xFFFFFF) == (colors[1] & 0xFFFFFF)) {
                int opponent = local == 1 ? 0 : 1;
                colors[opponent] = 0xFFFF5A7A;
            }
            this.names = p.names();
            this.times = p.times();
            this.lanes = p.lanes();
            this.perfectMs = p.perfectMs();
            this.greatMs = p.greatMs();
            this.goodMs = p.goodMs();
            this.beatTicks = 1200f / Math.max(40f, p.bpm());
            int n = entities.length;
            score = new int[n];
            streak = new int[n];
            accuracy = new float[n];
            java.util.Arrays.fill(accuracy, 1f);
            result = new byte[n][times.length];
            lastJudge = new int[n];
            java.util.Arrays.fill(lastJudge, -1);
            lastJudgeAt = new long[n];
            lastOffset = new float[n];
            laneFlashAt = new long[n][4];
            heat = new float[n];
        }

        public double clock() {
            return now() - startTick;
        }

        public float end() {
            return times.length == 0 ? 0 : times[times.length - 1];
        }

        public int other(int i) {
            return i == 0 ? 1 : 0;
        }
    }

    @Nullable private static View current;
    private static long tickNanos;
    private static long tickTime;
    private static long lastBeat = Long.MIN_VALUE;
    private static int lastCount = Integer.MAX_VALUE;

    private ClashClient() {}

    @Nullable
    public static View view() {
        return current;
    }

    /** The local player is duelling right now: lanes take the arrow/WASD keys. */
    public static boolean playing() {
        return current != null && current.local >= 0;
    }

    /** A recording stepping the game frame by frame pins the sub-tick moment here (0..1); -1 = live. */
    public static volatile float recordingPartial = -1;

    /** Game time with sub-tick precision (the same clock draws the notes and stamps the presses). */
    public static double now() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        if (recordingPartial >= 0) return mc.level.getGameTime() + recordingPartial;
        if (tickTime != mc.level.getGameTime()) {
            tickTime = mc.level.getGameTime();
            tickNanos = System.nanoTime();
        }
        // Sub-tick progress at the current tick rate (/tick rate changes it; 50 ms at the normal 20 TPS).
        double perTick = Math.max(1, mc.level.tickRateManager().nanosecondsPerTick());
        return tickTime + Mth.clamp((System.nanoTime() - tickNanos) / perTick, 0, 1.2);
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            current = null;
            return;
        }
        now();
        View v = current;
        if (v == null || v.local < 0) return;
        double clock = v.clock();
        // Countdown blips, then a soft beat to play along with.
        if (clock < 0) {
            int count = (int) Math.ceil(-clock / 10.0);
            if (count <= 3 && count != lastCount) ClientFx.sound("clash_countdown", mc.player.position(), 0.8f, count == 1 ? 1.2f : 1f);
            lastCount = count;
        } else if (clock <= v.end() + v.beatTicks) {
            long beat = (long) Math.floor(clock / v.beatTicks);
            if (beat != lastBeat) ClientFx.sound("clash_beat", mc.player.position(), 0.35f, 1f);
            lastBeat = beat;
        }
    }

    // --- Network ---

    public static void start(ClashStartPayload p) {
        Minecraft mc = Minecraft.getInstance();
        int local = -1;
        if (mc.player != null) for (int i = 0; i < p.entities().length; i++) if (p.entities()[i] == mc.player.getId()) local = i;
        View old = current;
        View v = new View(p, local);
        if (old != null && old.session == p.session()) {
            // A tie breaker keeps the running totals.
            System.arraycopy(old.score, 0, v.score, 0, Math.min(old.score.length, v.score.length));
            System.arraycopy(old.accuracy, 0, v.accuracy, 0, Math.min(old.accuracy.length, v.accuracy.length));
        }
        current = v;
        lastBeat = Long.MIN_VALUE;
        lastCount = Integer.MAX_VALUE;
    }

    public static void update(ClashUpdatePayload p) {
        View v = current;
        if (v == null || v.session != p.session() || p.participant() < 0 || p.participant() >= v.entities.length) return;
        int i = p.participant();
        v.meter = p.meter();
        v.score[i] = p.score();
        v.streak[i] = p.streak();
        v.accuracy[i] = p.accuracy();
        if (p.note() >= 0 && p.note() < v.times.length) v.result[i][p.note()] = (byte) (p.judgement() + 1);
        // The local player already saw their prediction; the server's verdict replaces it only if it differs.
        boolean predicted = i == v.local && p.note() >= 0 && v.lastJudge[i] == p.judgement() && System.currentTimeMillis() - v.lastJudgeAt[i] < 400;
        if (!predicted) {
            v.lastJudge[i] = p.judgement();
            v.lastJudgeAt[i] = System.currentTimeMillis();
            v.lastOffset[i] = p.offsetMs();
        }
        ClashJudgement j = ClashJudgement.byId(p.judgement());
        v.heat[i] = j == ClashJudgement.PERFECT ? Math.min(3f, v.heat[i] + 0.4f) : j.hit() ? v.heat[i] : Math.max(0, v.heat[i] - 1f);
        if (p.note() >= 0 && i != v.local) v.laneFlashAt[i][v.lanes[p.note()]] = System.currentTimeMillis();
    }

    public static void end(ClashEndPayload p) {
        if (current != null && current.session == p.session()) current = null;
    }

    public static void reset() {
        current = null;
    }

    // --- Input ---

    /** A lane key went down. Returns true if the clash took it. */
    public static boolean press(int lane) {
        View v = current;
        if (v == null || v.local < 0 || lane < 0 || lane > 3) return false;
        double time = now();
        double clock = time - v.startTick;
        v.laneFlashAt[v.local][lane] = System.currentTimeMillis();
        if (clock < -2) return true; // countdown: swallow the key, nothing to hit yet
        ClientPlayNetworking.send(new ClashInputPayload(v.session, lane, time));
        // Predict locally so the hit registers on screen the moment the key goes down.
        double window = v.goodMs / 50.0;
        int me = v.local;
        for (int i = 0; i < v.times.length; i++) {
            if (v.result[me][i] != 0 || v.lanes[i] != lane) continue;
            double off = clock - v.times[i];
            if (Math.abs(off) <= window) {
                double ms = Math.abs(off * 50);
                ClashJudgement j = ms <= v.perfectMs ? ClashJudgement.PERFECT : ms <= v.greatMs ? ClashJudgement.GREAT : ClashJudgement.GOOD;
                v.result[me][i] = (byte) (j.ordinal() + 1);
                v.lastJudge[me] = j.ordinal();
                v.lastJudgeAt[me] = System.currentTimeMillis();
                v.lastOffset[me] = (float) (off * 50);
                return true;
            }
            if (v.times[i] - clock > window) break;
        }
        return true;
    }
}
