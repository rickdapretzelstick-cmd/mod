package dev.rick.jjk.client.clash;

import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.core.net.BeamClashCheckPayload;
import dev.rick.jjk.core.net.BeamClashInputPayload;
import dev.rick.jjk.core.net.BeamClashJudgePayload;
import dev.rick.jjk.core.net.BeamClashStatePayload;
import dev.rick.jjk.core.net.BeamCounterPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client side of a beam clash. Everyone near one is told its world state (the two sources, where the beams meet, who is
 * pressing harder) so they can see it; only the two contestants get their own skill checks, which this keeps the
 * timing of: the needle starts the moment a check arrives, and a press reports how far round it had got, which the
 * server then judges against its own clock.
 */
public final class BeamClashClient {
    /** Phases, as the server numbers them. */
    public static final int COUNTER = 0, INTRO = 1, DUEL = 2, RESOLVE = 3, ENDED = 4;
    /** Outcomes, as the server numbers them. */
    public static final int NONE = 0, A_WINS = 1, B_WINS = 2, TIE = 3, CANCELLED = 4;
    /** Judgements, as the server numbers them. */
    public static final int GREAT = 0, GOOD = 1, MISS = 2;
    /** When a winner breaks through, counted from the start of RESOLVE (the server's hesitate + break). */
    static final int BREAK_AT = 26;

    /** One clash as this client sees it. */
    public static final class View {
        public final int session;
        public int phase, phaseAge, aId, bId, outcome;
        public String aKind = "", bKind = "";
        public Vec3 aOrigin = Vec3.ZERO, bOrigin = Vec3.ZERO;
        /** The server's collision (0 = at A's source, 1 = at B's), and the one drawn (eased toward it). */
        public float collision = 0.5f, shown = 0.5f;
        public float push, intensity;
        public int powerA, powerB;
        /** Game time of the last state seen (a session nobody hears from for a while is dropped). */
        public long seenAt, phaseSince;
        /** The local player's side (0 = A, 1 = B), or -1 when only watching. */
        public int local = -1;
        /** Last judgement per side: kind, when (ms), and where the needle stopped. */
        public final int[] judged = {-1, -1};
        public final long[] judgedAt = new long[2];
        public final float[] judgedAngle = new float[2];

        View(int session) {
            this.session = session;
        }

        public Vec3 point() {
            return aOrigin.add(bOrigin.subtract(aOrigin).scale(shown));
        }

        /** How far along the line the local side has pushed (-1 = into their own source, +1 = into the other's). */
        public float lead(int side) {
            float c = (shown - 0.5f) / 0.4f;
            return Mth.clamp(side == 0 ? c : -c, -1, 1);
        }

        public boolean broken() {
            return phase == RESOLVE && phaseAge >= BREAK_AT || phase == ENDED;
        }
    }

    /** The local player's current skill check. */
    public static final class Check {
        public final int session, id, periodMs;
        public final float zoneStart, goodWidth, greatWidth;
        public final long shownAt;
        /** Once pressed (or let go by): what the client predicted, and where the needle stopped. */
        public int predicted = -1;
        public float stoppedAt = -1;
        public long doneAt;

        Check(BeamClashCheckPayload p) {
            session = p.session();
            id = p.check();
            periodMs = Math.max(200, p.periodMs());
            zoneStart = p.zoneStart();
            goodWidth = p.goodWidth();
            greatWidth = p.greatWidth();
            shownAt = System.currentTimeMillis();
        }

        public float angle(long ms) {
            return stoppedAt >= 0 ? stoppedAt : (ms - shownAt) / (float) periodMs * 360f;
        }

        int judge(float angle) {
            if (angle >= zoneStart && angle <= zoneStart + greatWidth) return GREAT;
            if (angle >= zoneStart && angle <= zoneStart + goodWidth) return GOOD;
            return MISS;
        }
    }

    private static final Map<Integer, View> SESSIONS = new HashMap<>();
    @Nullable private static Check check;
    /** A counter prompt: "your ultimate answers this beam", until this game time. */
    @Nullable private static BeamCounterPayload prompt;
    private static long promptUntil, promptAt;

    private BeamClashClient() {}

    // --- Queries (renderers, HUD, camera) ---

    public static Iterable<View> views() {
        return SESSIONS.values();
    }

    /** The clash the local player is in, if any. */
    @Nullable
    public static View mine() {
        for (View v : SESSIONS.values()) if (v.local >= 0 && v.phase != ENDED) return v;
        return null;
    }

    /** The local player is duelling: the skill check takes the space bar. */
    public static boolean playing() {
        View v = mine();
        return v != null && (v.phase == INTRO || v.phase == DUEL);
    }

    @Nullable
    public static Check check() {
        return check;
    }

    @Nullable
    public static BeamCounterPayload prompt(long now) {
        return prompt != null && now < promptUntil ? prompt : null;
    }

    public static long promptAt() {
        return promptAt;
    }

    public static long promptUntil() {
        return promptUntil;
    }

    @Nullable
    private static View of(int ownerId) {
        for (View v : SESSIONS.values()) if (v.aId == ownerId || v.bId == ownerId) return v;
        return null;
    }

    /** How far the beam cast by {@code ownerId} reaches: to where it meets the other in a clash, else unlimited. */
    public static double reach(int ownerId) {
        View v = of(ownerId);
        if (v == null || v.broken() || v.outcome == CANCELLED) return Double.POSITIVE_INFINITY;
        Vec3 from = ownerId == v.aId ? v.aOrigin : v.bOrigin;
        return Math.max(1.5, from.distanceTo(v.point()) + 0.6);
    }

    /** How hard the beam cast by {@code ownerId} is winning (-1 losing badly, +1 winning), 0 outside a clash. */
    public static float pressure(int ownerId) {
        View v = of(ownerId);
        if (v == null || v.phase == COUNTER || v.phase == ENDED) return 0;
        if (v.broken()) return ownerId == v.aId == (v.outcome == A_WINS) ? 1f : -1f;
        return v.lead(ownerId == v.aId ? 0 : 1);
    }

    // --- Network ---

    public static void state(BeamClashStatePayload p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        View v = SESSIONS.computeIfAbsent(p.session(), View::new);
        boolean first = v.seenAt == 0;
        if (v.phase != p.phase() || first) v.phaseSince = now - p.phaseAge();
        int was = v.phase;
        v.phase = p.phase();
        v.phaseAge = p.phaseAge();
        v.aId = p.aId();
        v.bId = p.bId();
        v.aKind = p.aKind();
        v.bKind = p.bKind();
        v.aOrigin = p.aOrigin();
        v.bOrigin = p.bOrigin();
        v.collision = p.collision();
        if (first || was == COUNTER && v.phase != COUNTER) v.shown = v.collision;
        v.push = p.push();
        v.intensity = p.intensity();
        v.powerA = p.powerA();
        v.powerB = p.powerB();
        v.outcome = p.outcome();
        v.seenAt = now;
        int me = mc.player != null ? mc.player.getId() : Integer.MIN_VALUE;
        v.local = v.aId == me ? 0 : v.bId == me ? 1 : -1;
        if (v.local >= 0) prompt = null;
        if (v.phase == ENDED) {
            if (check != null && check.session == v.session) check = null;
            SESSIONS.remove(v.session);
        }
    }

    public static void check(BeamClashCheckPayload p) {
        check = new Check(p);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) ClientFx.sound("clash_countdown", mc.player.position(), 0.7f, 1.5f);
    }

    public static void judge(BeamClashJudgePayload p) {
        View v = SESSIONS.get(p.session());
        if (v == null || p.side() < 0 || p.side() > 1) return;
        v.judged[p.side()] = p.judgement();
        v.judgedAt[p.side()] = System.currentTimeMillis();
        v.judgedAngle[p.side()] = p.angle();
        if (p.side() == v.local && check != null && check.id == p.check()) {
            // The server's verdict stands; the needle stays where it judged it.
            check.predicted = p.judgement();
            if (p.angle() >= 0) check.stoppedAt = p.angle();
            if (check.doneAt == 0) check.doneAt = System.currentTimeMillis();
        }
    }

    public static void counter(BeamCounterPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        prompt = p;
        promptAt = mc.level.getGameTime();
        promptUntil = promptAt + Math.max(1, p.ticks());
        if (mc.player != null) ClientFx.sound("clash_countdown", mc.player.position(), 1f, 0.7f);
    }

    // --- Input ---

    /** The skill-check key went down. Returns true if the clash took it. */
    public static boolean press() {
        View v = mine();
        if (v == null) return false;
        Check c = check;
        if (c == null || c.session != v.session || c.predicted >= 0) return true;
        long ms = System.currentTimeMillis();
        int elapsed = (int) (ms - c.shownAt);
        ClientPlayNetworking.send(new BeamClashInputPayload(c.session, c.id, elapsed));
        // Predict at once, so the dial answers the moment the key goes down.
        float angle = c.angle(ms);
        c.stoppedAt = angle;
        c.predicted = c.judge(angle);
        c.doneAt = ms;
        return true;
    }

    // --- Lifecycle ---

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            reset();
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<View> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            View v = it.next();
            // Eased toward the server's collision, faster as the clash breaks.
            float k = v.broken() || v.phase == RESOLVE ? 0.45f : 0.22f;
            v.shown += (v.collision - v.shown) * k;
            // The two beams grinding against each other: a low roar under the clash, louder as it strains.
            if ((v.phase == INTRO || v.phase == DUEL) && (now - v.phaseSince) % 16 == 0) {
                ClientFx.sound("max_blue_hum", v.point(), 1.4f + v.intensity * 0.3f, 0.7f + Math.abs(v.shown - 0.5f) * 0.6f);
            }
            if (now - v.seenAt > 60 || now < v.seenAt) it.remove();
        }
        Check c = check;
        if (c != null) {
            long ms = System.currentTimeMillis();
            View v = mine();
            // Let go by: the needle ran past the zone (the server's own miss follows); cleared once shown.
            if (c.predicted < 0 && c.angle(ms) > c.zoneStart + c.goodWidth + 25) {
                c.predicted = MISS;
                c.stoppedAt = Math.min(360, c.angle(ms));
                c.doneAt = ms;
            }
            if (v == null || v.session != c.session || c.doneAt > 0 && ms - c.doneAt > 450) check = null;
        }
        if (prompt != null && (now >= promptUntil || mine() != null)) prompt = null;
    }

    public static void reset() {
        SESSIONS.clear();
        check = null;
        prompt = null;
    }
}
