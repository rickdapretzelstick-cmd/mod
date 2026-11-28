package dev.rick.jjk.client.debug;

import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.AnimPlayer;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.rig.Bone;
import dev.rick.jjk.core.combat.CombatEvents;
import dev.rick.jjk.core.debug.Trace;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a fight as text. While tracing, it records for the chosen entities:
 * <ul>
 *   <li>from the server ({@link Trace}): casts beginning, changing phase, ending and being interrupted, every clip
 *   started, and hits;</li>
 *   <li>from the client, every rendered frame: which clips play (time, weight, fading), body yaw, head yaw and pitch, and
 *   each bone's final local rotation as drawn (vanilla frames are solved too, so a clip's last frame and the pose after
 *   it can be compared).</li>
 * </ul>
 * {@link #stop} writes one merged timeline to {@code build/anim-trace/<name>.txt} and checks it:
 * <ul>
 *   <li><b>pop</b>: a bone turning over 150 degrees a tick in one frame, 3x faster than around it, at a transition
 *   (a clip starting, ending or fading): the engine snapped it. The same inside one clip at full weight is the clip's
 *   own fast motion, listed as a style note;</li>
 *   <li><b>linger</b>: a move's one-shot clips still showing well after the move ended;</li>
 *   <li><b>idle gap</b>: a move still running on the server with nothing animating it;</li>
 *   <li><b>facing</b>: the body turned away from the aim while casting (after the first 3 ticks, its turn-in);</li>
 *   <li><b>stuck</b>: a held pose still showing long after its move ended.</li>
 * </ul>
 */
public final class AnimTrace {
    /** The bones written in the timeline (all are checked). */
    private static final Bone[] SHOWN = {Bone.CHEST, Bone.HEAD, Bone.RIGHT_ARM, Bone.RIGHT_FOREARM, Bone.LEFT_ARM, Bone.LEFT_FOREARM,
            Bone.RIGHT_THIGH, Bone.LEFT_THIGH};

    /** One server event. */
    public record Event(long tick, int entity, String kind, String detail) {}

    /** One clip as it showed in a frame. */
    public record ClipState(String name, float timeMs, float weight, boolean stopping) {}

    /** One rendered frame of one entity. {@code rot[bone]} is the bone's local rotation (null for an unsolved frame). */
    public record Frame(double time, int entity, float body, float head, float pitch, List<ClipState> clips, Quaternionf @Nullable [] rot) {}

    /** Something that looks wrong, at a time. */
    public record Anomaly(double time, int entity, String kind, String detail) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "t=%.2f  #%d  %-9s %s", time, entity, kind.toUpperCase(Locale.ROOT), detail);
        }
    }

    /** A cast as the trace saw it: when it ran, the clips it started and how long they showed. */
    public record CastSummary(int entity, String ability, long begin, long end, List<String> clips, double lastOneShotEndTick,
                              double visualEndTick, boolean heldAtEnd) {
        public int ticks() {
            return (int) (end - begin);
        }

        /** How long the move's own clips would have run past its end (ms; negative: they ended first). */
        public double overrunMs() {
            return (lastOneShotEndTick - end) * 50;
        }

        /** How long the move kept running after its clips had all ended, with no held pose (ms). */
        public double idleGapMs() {
            return heldAtEnd || lastOneShotEndTick <= begin ? 0 : Math.max(0, (end - lastOneShotEndTick) * 50);
        }
    }

    public record Report(String name, List<Event> events, List<Frame> frames, List<Anomaly> anomalies, List<CastSummary> casts, Path file) {}

    private static final Set<Integer> TRACED = new HashSet<>();
    private static final List<Event> EVENTS = new ArrayList<>();
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static String name = "";
    private static boolean hitsHooked;

    private AnimTrace() {}

    public static synchronized void start(String traceName, int... entities) {
        TRACED.clear();
        for (int e : entities) TRACED.add(e);
        EVENTS.clear();
        FRAMES.clear();
        name = traceName;
        if (!hitsHooked) {
            hitsHooked = true;
            CombatEvents.HIT_RESOLVED.add(r -> {
                if (Trace.on()) Trace.event(r.hit().attacker, "hit", "#" + r.target().getId() + " " + r.outcome() + " " + Math.round(r.damageDealt() * 10) / 10f);
            });
        }
        Trace.sink = (tick, entity, kind, detail) -> {
            synchronized (AnimTrace.class) {
                // (Every entity's clips too: Rika's play on her while her blows are dealt in Yuta's name.)
                if (TRACED.contains(entity) || kind.equals("hit") || kind.equals("anim")) EVENTS.add(new Event(tick, entity, kind, detail));
            }
        };
    }

    public static boolean tracing(int entity) {
        return !TRACED.isEmpty() && TRACED.contains(entity);
    }

    /** From the model: this frame's solved bones for {@code entity} (model space), or null when vanilla drew it unsolved. */
    public static void frame(int entity, Matrix4f @Nullable [] bones) {
        if (!tracing(entity)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entity);
        double time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<ClipState> clips = new ArrayList<>();
        AnimPlayer p = ClientAnimations.player(entity);
        if (p != null) for (AnimPlayer.Instance i : p.ordered()) clips.add(new ClipState(i.clip.name, i.time, i.weight(), i.stopping));
        Quaternionf[] rot = null;
        if (bones != null) {
            rot = new Quaternionf[Bone.COUNT];
            for (Bone b : Bone.ALL) {
                Matrix4f m = bones[b.ordinal()];
                if (m == null) continue;
                Matrix4f local = b.parent == null || bones[b.parent.ordinal()] == null ? new Matrix4f(m)
                        : new Matrix4f(bones[b.parent.ordinal()]).invert().mul(m);
                rot[b.ordinal()] = local.getUnnormalizedRotation(new Quaternionf()).normalize();
            }
        }
        float body = e instanceof LivingEntity le ? le.yBodyRot : 0, head = e instanceof LivingEntity le2 ? le2.getYHeadRot() : 0;
        float pitch = e == null ? 0 : e.getXRot();
        synchronized (AnimTrace.class) {
            if (!FRAMES.isEmpty()) {
                Frame last = FRAMES.getLast();
                if (last.entity == entity && Math.abs(last.time - time) < 1e-4) return; // the same frame drawn twice
            }
            FRAMES.add(new Frame(time, entity, body, head, pitch, clips, rot));
        }
    }

    // ------------------------------------------------------------------------------------------------ analysis

    public static synchronized Report stop() {
        Trace.sink = null;
        List<Event> events = new ArrayList<>(EVENTS);
        List<Frame> frames = new ArrayList<>(FRAMES);
        TRACED.clear();
        List<Anomaly> anomalies = new ArrayList<>();
        FAST.clear();
        List<CastSummary> casts = casts(events, frames);
        pops(frames, anomalies);
        for (CastSummary c : casts) {
            // Follow-through may play 300 ms past the move and fade in 160: anything showing past 500 ms lingers.
            if (c.overrunMs() > 150 && c.visualEndTick > c.end + 11)
                anomalies.add(new Anomaly(c.end, c.entity, "linger", String.format(Locale.ROOT, "%s: clips showed %.0f ms past the move's end (would have run %.0f ms)",
                        c.ability, (c.visualEndTick - c.end) * 50, c.overrunMs())));
            if (c.idleGapMs() > 250)
                anomalies.add(new Anomaly(c.end, c.entity, "idle gap", String.format(Locale.ROOT, "%s: ran %.0f ms after its clips ended with nothing held",
                        c.ability, c.idleGapMs())));
        }
        facing(events, frames, anomalies);
        stuck(events, frames, anomalies);
        anomalies.sort((a, b) -> Double.compare(a.time, b.time));
        Path file = write(name, events, frames, anomalies, casts);
        return new Report(name, events, frames, anomalies, casts, file);
    }

    private static List<CastSummary> casts(List<Event> events, List<Frame> frames) {
        List<CastSummary> out = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            Event b = events.get(i);
            if (!b.kind.equals("cast_begin")) continue;
            long end = -1;
            List<String> clips = new ArrayList<>();
            double lastEnd = b.tick;
            boolean held = false;
            for (int j = i + 1; j < events.size(); j++) {
                Event e = events.get(j);
                if (e.entity != b.entity) continue;
                if (e.kind.equals("cast_end") || e.kind.equals("cast_begin")) {
                    end = e.tick;
                    break;
                }
                if (!e.kind.equals("anim")) continue;
                String[] parts = e.detail.split(" x");
                String clipName = parts[0];
                if (clipName.equals("(release)")) {
                    held = false;
                    continue;
                }
                float speed = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
                Clip c = AnimLibrary.get(clipName);
                String contact = "";
                if (c != null) {
                    // A clip's marked contact frame against the server's nearest hit: how far the blow and the hit are apart.
                    for (Clip.Marker mk : c.markers) {
                        if (!mk.label().equals("contact")) continue;
                        double ct = e.tick + mk.time() / (speed * c.speed) / 50.0;
                        Event near = null;
                        for (Event h : events) {
                            if (!h.kind.equals("hit") || h.entity != b.entity || Math.abs(h.tick - ct) > 6) continue;
                            if (near == null || Math.abs(h.tick - ct) < Math.abs(near.tick - ct)) near = h;
                        }
                        contact = near == null ? " contact, no hit near" : String.format(Locale.ROOT, " contact %+.0fms from the hit", (ct - near.tick) * 50);
                        break;
                    }
                }
                clips.add(clipName + "@" + (e.tick - b.tick) + contact);
                if (c == null) continue;
                held = c.endless();
                // A one-shot clip runs its duration at its speed; a held one lasts until released.
                if (!c.endless()) lastEnd = Math.max(lastEnd, e.tick + c.duration / (speed * c.speed) / 50.0);
            }
            if (end < 0) continue;
            // When the cast's clips last showed (any of them with weight).
            Set<String> names = new HashSet<>();
            for (String s : clips) names.add(s.substring(0, s.indexOf('@')));
            double visual = b.tick;
            for (Frame f : frames) {
                if (f.entity != b.entity || f.time < b.tick) continue;
                for (ClipState cs : f.clips) if (names.contains(cs.name) && cs.weight > 0.01) visual = Math.max(visual, f.time);
            }
            out.add(new CastSummary(b.entity, b.detail, b.tick, end, clips, lastEnd, visual, held));
        }
        return out;
    }

    /** A bone turning far faster in one frame than in its neighbours: a snap. */
    private static void pops(List<Frame> frames, List<Anomaly> out) {
        List<Frame> solved = new ArrayList<>();
        for (Frame f : frames) if (f.rot != null) solved.add(f);
        for (Bone b : Bone.ALL) {
            if (b == Bone.ROOT) continue;
            int bi = b.ordinal();
            for (int k = 1; k + 1 < solved.size(); k++) {
                Frame a = solved.get(k - 1), m = solved.get(k), c = solved.get(k + 1);
                if (a.entity != m.entity || m.entity != c.entity) continue;
                if (a.rot[bi] == null || m.rot[bi] == null || c.rot[bi] == null) continue;
                double dt1 = Math.max(0.05, m.time - a.time), dt2 = Math.max(0.05, c.time - m.time);
                double v1 = angle(a.rot[bi], m.rot[bi]) / dt1, v2 = angle(m.rot[bi], c.rot[bi]) / dt2;
                double before = k >= 2 && solved.get(k - 2).rot != null && solved.get(k - 2).rot[bi] != null
                        ? angle(solved.get(k - 2).rot[bi], a.rot[bi]) / Math.max(0.05, a.time - solved.get(k - 2).time) : v2;
                // A jump of more than 70 degrees per tick that is 3x what came before and after. Inside one clip playing at
                // full weight it's the clip's own motion (a punch, a smear): authored, a style note, not the engine.
                boolean authored = steady(a, m);
                if (v1 > 70 && v1 > 3 * Math.max(5, before) && v1 > 3 * Math.max(5, v2) && authored) {
                    FAST.merge(m.entity + " " + clipText(m.clips).replaceAll(" \\d+ms w[0-9.]+", ""), 1, Integer::sum);
                } else if (v1 > 150 && v1 > 3 * Math.max(5, before) && v1 > 3 * Math.max(5, v2))
                    out.add(new Anomaly(m.time, m.entity, "pop", String.format(Locale.ROOT, "%s snapped %.0f° in %.2f ticks (around it: %.0f°/t, %.0f°/t) clips: %s",
                            b.id, angle(a.rot[bi], m.rot[bi]), dt1, before, v2, clipText(m.clips))));
            }
        }
    }

    private static void facing(List<Event> events, List<Frame> frames, List<Anomaly> out) {
        for (Frame f : frames) {
            if (!casting(events, f.entity, f.time)) continue;
            // (The body turns in over the cast's first ticks: 30 degrees a tick, from up to vanilla's 50 of lag.)
            if (f.time - castStart(events, f.entity, f.time) < 3) continue;
            // (Nor the tick after the server turns them: the head jumps at once, the body is eased across the tick.)
            if (headJumped(frames, f)) continue;
            float off = Math.abs(Mth.wrapDegrees(f.body - f.head));
            if (off > 12) {
                out.add(new Anomaly(f.time, f.entity, "facing", String.format(Locale.ROOT, "body %.0f° off the aim while casting", off)));
                return; // once is enough to know
            }
        }
    }

    /** The entity's head turned more than 75 degrees between two frames within the tick before {@code f}. */
    private static boolean headJumped(List<Frame> frames, Frame f) {
        Frame prev = null;
        for (Frame g : frames) {
            if (g.entity != f.entity || g.time > f.time) continue;
            if (prev != null && g.time >= f.time - 1 && Math.abs(Mth.wrapDegrees(g.head - prev.head)) > 75) return true;
            prev = g;
        }
        return false;
    }

    /** When the cast running at {@code time} began (its cast_begin tick). */
    private static double castStart(List<Event> events, int entity, double time) {
        double start = time;
        for (Event e : events) if (e.entity == entity && e.tick <= time && e.kind.equals("cast_begin")) start = e.tick;
        return start;
    }

    private static boolean casting(List<Event> events, int entity, double time) {
        boolean on = false;
        for (Event e : events) {
            if (e.entity != entity || e.tick > time) continue;
            if (e.kind.equals("cast_begin")) on = true;
            else if (e.kind.equals("cast_end")) on = false;
        }
        return on;
    }

    /** A held clip still showing 30+ ticks after the last cast ended. */
    private static void stuck(List<Event> events, List<Frame> frames, List<Anomaly> out) {
        for (Frame f : frames) {
            if (casting(events, f.entity, f.time)) continue;
            long lastEnd = -1;
            for (Event e : events) if (e.entity == f.entity && e.kind.equals("cast_end") && e.tick <= f.time) lastEnd = e.tick;
            if (lastEnd < 0 || f.time - lastEnd < 30) continue;
            for (ClipState c : f.clips) {
                Clip clip = AnimLibrary.get(c.name);
                if (clip != null && clip.endless() && c.weight > 0.5 && !c.stopping) {
                    out.add(new Anomaly(f.time, f.entity, "stuck", c.name + " still held " + Math.round(f.time - lastEnd) + " ticks after the move ended"));
                    return;
                }
            }
        }
    }

    /** Authored fast motion seen (entity + clip -> frames), for the style notes. */
    private static final java.util.Map<String, Integer> FAST = new java.util.TreeMap<>();

    /**
     * The same clip on top at full weight in both frames (anything under it fading out doesn't show): nothing but that
     * clip's own keys moved the bones.
     */
    private static boolean steady(Frame a, Frame b) {
        ClipState x = top(a), y = top(b);
        return x != null && y != null && x.name.equals(y.name) && x.weight >= 0.99 && y.weight >= 0.99 && y.timeMs >= x.timeMs;
    }

    @Nullable
    private static ClipState top(Frame f) {
        ClipState t = null;
        for (ClipState c : f.clips) if (!c.stopping) t = c;
        return t;
    }

    static double angle(Quaternionf a, Quaternionf b) {
        double d = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return Math.toDegrees(2 * Math.acos(Math.min(1, d)));
    }

    // ------------------------------------------------------------------------------------------------ writing

    private static String clipText(List<ClipState> clips) {
        if (clips.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (ClipState c : clips) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(c.name).append(String.format(Locale.ROOT, " %.0fms w%.2f", c.timeMs, c.weight)).append(c.stopping ? " fading" : "");
        }
        return sb.toString();
    }

    private static String eulerText(Quaternionf q) {
        Vector3f e = new Matrix4f().rotation(q).getEulerAnglesZYX(new Vector3f());
        return String.format(Locale.ROOT, "(%.0f,%.0f,%.0f)", Math.toDegrees(e.x), Math.toDegrees(e.y), Math.toDegrees(e.z));
    }

    private static Path write(String name, List<Event> events, List<Frame> frames, List<Anomaly> anomalies, List<CastSummary> casts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Animation trace: ").append(name).append("\n\n## Anomalies (").append(anomalies.size()).append(")\n");
        for (Anomaly a : anomalies) sb.append(a).append('\n');
        sb.append("\n## Authored fast motion (style notes, not engine faults): clip -> frames\n");
        for (var e : FAST.entrySet()) sb.append(e.getKey()).append(" -> ").append(e.getValue()).append('\n');
        sb.append("\n## Casts\n");
        for (CastSummary c : casts) {
            sb.append(String.format(Locale.ROOT, "#%d %-24s ticks %d-%d (%d)  one-shot clips end at %+.1f t  shown until %+.1f t  held at end: %s  clips: %s%n",
                    c.entity, c.ability, c.begin, c.end, c.ticks(), c.lastOneShotEndTick - c.end, c.visualEndTick - c.end, c.heldAtEnd, c.clips));
        }
        sb.append("\n## Timeline (server events; client frames: body/head/pitch, clips, local bone rotations in degrees)\n");
        int ei = 0;
        for (Frame f : frames) {
            while (ei < events.size() && events.get(ei).tick <= f.time) {
                Event e = events.get(ei++);
                sb.append(String.format(Locale.ROOT, "t=%d     #%d SERVER %-14s %s%n", e.tick, e.entity, e.kind, e.detail));
            }
            sb.append(String.format(Locale.ROOT, "t=%.2f  #%d body %.0f head %.0f pitch %.0f | %s", f.time, f.entity, f.body, f.head, f.pitch, clipText(f.clips)));
            if (f.rot != null) {
                sb.append(" |");
                for (Bone b : SHOWN) if (f.rot[b.ordinal()] != null) sb.append(' ').append(b.id).append(eulerText(f.rot[b.ordinal()]));
            } else sb.append(" | (vanilla, unsolved)");
            sb.append('\n');
        }
        while (ei < events.size()) {
            Event e = events.get(ei++);
            sb.append(String.format(Locale.ROOT, "t=%d     #%d SERVER %-14s %s%n", e.tick, e.entity, e.kind, e.detail));
        }
        Path dir = Path.of(System.getProperty("jjk.traceDir", "anim-trace"));
        Path file = dir.resolve(name.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, sb.toString());
        } catch (IOException ex) {
            dev.rick.jjk.JJK.LOGGER.warn("[anim-trace] couldn't write {}: {}", file, ex.toString());
        }
        return file;
    }
}
