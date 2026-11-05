package dev.rick.jjk.client.training;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.Shots;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingNet;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A strength contest shot like a grapple in a fighting game's throw cinematic: the exchange is cut on its verdicts.
 *
 * <ul>
 *   <li>It opens on the wide: side-on to the two locked together, framing both, drifting round ~25 degrees with a gentle
 *   push-in (the field of view easing from 65 to 58).</li>
 *   <li>Every verdict cuts to the next shot of that curse kind's list: a good tap to a shot that sells your push (low on
 *   you as you drive it back, over its shoulder at your face, close on the hands), a miss to one that sells its weight
 *   (low under it as it bears down, over your shoulder into it, or from above for the big ones). Each shot is held at
 *   least {@link #MIN_HOLD_MS} (no strobing) and drifts while it holds; with no verdict for a while it goes back to the
 *   wide. A perfect tap punches the lens in, a miss knocks it out.</li>
 *   <li>The result: broken free, the camera drops low behind you for the reversal as it reels, then pulls out; overpowered,
 *   it follows you off it, side-on and pulled back.</li>
 * </ul>
 *
 * It never goes through a wall ({@link Shots#clip}). With Reduced Camera Motion it is one still wide shot (no cuts, no
 * drift, no lens punches). The HUD stays fixed either way; the third-person view comes from {@link CameraDirector}.
 */
public final class ContestCamera {
    private static long since;
    /** Which side of the pair the shots are on (chosen once per contest, the clearer side). */
    private static int side;

    static final float FOV_START = 65f, FOV_END = 58f, FOV_STILL = 62f;
    static final double ORBIT_DEGREES = 25;
    /** Each cut is held at least this long; with no verdict for {@link #BACK_TO_WIDE_MS} it returns to the wide. */
    static final double MIN_HOLD_MS = 900, BACK_TO_WIDE_MS = 2400;

    /** The shots a contest cuts between. */
    public enum Shot { WIDE, LOW_ON_YOU, OVER_ITS_SHOULDER, HANDS, LOW_UNDER_IT, OVER_YOUR_SHOULDER, ABOVE, REVERSAL, THROWN }

    /** A kind's cut list: the shots a good tap cuts to, in turn, and the ones a miss does. */
    record Cuts(List<Shot> good, List<Shot> miss) {}

    static final Cuts DEFAULT = new Cuts(List.of(Shot.LOW_ON_YOU, Shot.OVER_ITS_SHOULDER, Shot.HANDS), List.of(Shot.LOW_UNDER_IT, Shot.OVER_YOUR_SHOULDER));
    /** Per kind: what each grapple is about (a maw's jaws, a bride looming, a toad's bulk pinning you). */
    static final Map<String, Cuts> CUTS = Map.of(
            "school_maw", new Cuts(List.of(Shot.LOW_ON_YOU, Shot.HANDS, Shot.OVER_ITS_SHOULDER), List.of(Shot.OVER_YOUR_SHOULDER, Shot.LOW_UNDER_IT)),
            "school_crawler", new Cuts(List.of(Shot.HANDS, Shot.LOW_ON_YOU), List.of(Shot.LOW_UNDER_IT, Shot.OVER_YOUR_SHOULDER)),
            "forest_stalker", new Cuts(List.of(Shot.OVER_ITS_SHOULDER, Shot.LOW_ON_YOU), List.of(Shot.LOW_UNDER_IT, Shot.OVER_YOUR_SHOULDER)),
            "grave_hound", new Cuts(List.of(Shot.LOW_ON_YOU, Shot.OVER_ITS_SHOULDER), List.of(Shot.HANDS, Shot.ABOVE)),
            "veiled_bride", new Cuts(List.of(Shot.LOW_ON_YOU, Shot.OVER_ITS_SHOULDER), List.of(Shot.OVER_YOUR_SHOULDER, Shot.LOW_UNDER_IT)),
            "bloat_toad", new Cuts(List.of(Shot.LOW_ON_YOU, Shot.WIDE), List.of(Shot.ABOVE, Shot.LOW_UNDER_IT)),
            "brood_mother", new Cuts(List.of(Shot.OVER_ITS_SHOULDER, Shot.LOW_ON_YOU), List.of(Shot.LOW_UNDER_IT, Shot.ABOVE)));

    /** A Grade 1 guardian's grip: it towers over you. */
    static final Cuts GUARDIAN = new Cuts(List.of(Shot.LOW_ON_YOU, Shot.OVER_ITS_SHOULDER, Shot.HANDS), List.of(Shot.LOW_UNDER_IT, Shot.OVER_YOUR_SHOULDER));

    private static Shot shot = Shot.WIDE;
    /** When the current shot was cut to (ns), the last verdict's check (each check cuts once), and turns through the lists. */
    private static long cutAt;
    private static int lastCheck = -1, goodTurn, missTurn;
    /** The lens punch: when, and which way (negative narrows). */
    private static long punchAt;
    private static float punch;
    /** Tests: one shot held, whatever happens (null lets the cut list run). */
    @Nullable private static Shot pinned;

    private ContestCamera() {}

    static void begin() {
        since = System.nanoTime();
        side = 0;
        shot = Shot.WIDE;
        cutAt = since;
        lastCheck = -1;
        goodTurn = 0;
        missTurn = 0;
        punch = 0;
    }

    public static boolean active() {
        return TrainingClient.contest() && since != 0;
    }

    public static void tick(Minecraft mc) {
        if (!TrainingClient.contest() || mc.player == null || !mc.player.isAlive()) since = 0;
    }

    public static void reset() {
        since = 0;
    }

    /** The shot on screen now (tests and the gallery). */
    public static Shot shot() {
        return shot;
    }

    /** Tests: holds {@code s} whatever happens (null lets the cut list run again). */
    public static void pinForTest(@Nullable Shot s) {
        pinned = s;
        if (s != null) cut(s, System.nanoTime());
    }

    /** Tests: a verdict, as the client would learn it. */
    public static void verdictForTest(int check, int result) {
        verdict(check, result);
    }

    static Cuts cuts(String kind) {
        if (kind.startsWith("guardian_")) return GUARDIAN;
        return CUTS.getOrDefault(kind, DEFAULT);
    }

    /** A verdict, as this client first learns it (its own prediction or the server's): it may cut. */
    static void verdict(int check, int result) {
        // Each place in the order cuts once for its right key and once for a wrong one.
        int key = check * 4 + result;
        if (!active() || result == TrainingCheck.HOLDING || key == lastCheck || pinned != null) return;
        lastCheck = key;
        if (CameraDirector.reducedMotion()) return;
        long now = System.nanoTime();
        punchAt = now;
        punch = result == TrainingCheck.PERFECT ? -4.5f : result == TrainingCheck.MISS ? 3f : -1.5f;
        if ((now - cutAt) / 1e6 < MIN_HOLD_MS && shot != Shot.WIDE) return;
        Cuts c = cuts(TrainingClient.kind);
        List<Shot> from = result == TrainingCheck.MISS ? c.miss() : c.good();
        Shot next = from.get(Math.floorMod(result == TrainingCheck.MISS ? missTurn++ : goodTurn++, from.size()));
        if (next == shot && from.size() > 1) next = from.get(Math.floorMod(result == TrainingCheck.MISS ? missTurn++ : goodTurn++, from.size()));
        cut(next, now);
    }

    private static void cut(Shot to, long now) {
        shot = to;
        cutAt = now;
    }

    /** A contest has no set length: the slow drift and push-in run over this long (then hold). */
    static final double NOMINAL_MS = 8000;

    /** 0 at the start of the contest to 1 a while in. */
    private static double progress(TrainingClient.Attempt a) {
        double total = a.start.introMs() + NOMINAL_MS;
        return Mth.clamp((a.clock + a.start.introMs()) / Math.max(1, total), 0, 1);
    }

    /** The field of view to use (degrees), or NaN when not in use. */
    public static float fov() {
        TrainingClient.Attempt a = TrainingClient.attempt();
        if (a == null || !active()) return Float.NaN;
        if (CameraDirector.reducedMotion()) return FOV_STILL;
        float base = (float) Mth.lerp(Shots.ease(progress(a)), FOV_START, FOV_END);
        double since = (System.nanoTime() - punchAt) / 1e6;
        float kick = since < 0 || since > 260 ? 0 : (float) (punch * (1 - Shots.ease(since / 260)));
        return base + kick;
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        TrainingClient.Attempt a = TrainingClient.attempt();
        Minecraft mc = Minecraft.getInstance();
        if (a == null || !active() || mc.player == null || mc.level == null) return null;
        TrainingClient.advance();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 feet = mc.player.getPosition(partial);
        Vec3 me = feet.add(0, 1.2, 0);
        Entity foeEntity = TrainingClient.foeId >= 0 ? mc.level.getEntity(TrainingClient.foeId) : null;
        Vec3 foeFeet = foeEntity != null ? foeEntity.getPosition(partial) : a.start.look().subtract(0, 1.2, 0);
        double foeHeight = foeEntity != null ? foeEntity.getBbHeight() : 2, foeWidth = foeEntity != null ? foeEntity.getBbWidth() : 1;
        Vec3 foe = foeFeet.add(0, Math.min(foeHeight * 0.6, 1.6), 0);
        Vec3 n = Shots.flatDir(me, foe);
        Vec3 mid = me.lerp(foe, 0.5);
        double span = Math.max(2, me.distanceTo(foe));
        double reach = span + 2.4;
        if (side == 0) {
            side = Shots.clearance(mid, mid.add(Shots.perpendicular(n, 1).scale(reach)).add(0, 1.1, 0))
                    >= Shots.clearance(mid, mid.add(Shots.perpendicular(n, -1).scale(reach)).add(0, 1.1, 0)) ? 1 : -1;
        }
        boolean still = CameraDirector.reducedMotion();
        long now = System.nanoTime();
        // The result has its own shot; and with no verdict for a while it goes back to the wide.
        if (!still && pinned == null) {
            if (a.state == TrainingNet.SUCCESS && shot != Shot.REVERSAL) cut(Shot.REVERSAL, now);
            else if (a.state == TrainingNet.FAILURE && shot != Shot.THROWN) cut(Shot.THROWN, now);
            else if (a.state == TrainingNet.ACTIVE && shot != Shot.WIDE && (now - cutAt) / 1e6 > BACK_TO_WIDE_MS) cut(Shot.WIDE, now);
        }
        Shot s = still ? Shot.WIDE : shot;
        double held = (now - cutAt) / 1e6;
        Frame f = headroom(frame(s, a, me, feet, foe, foeFeet, foeHeight, foeWidth, n, mid, span, reach, held, still));
        // Into the first shot from the player's own view over the first moments.
        double in = still ? 1 : Shots.ease((now - since) / 3e8);
        return f.blendFrom(vanillaPos, vanillaYaw, vanillaPitch, in);
    }

    /**
     * Aims every level shot a little under its subjects, so they sit in the upper part of the frame: the meter and the
     * gauges have the lower (a shot from straight above is already composed for it).
     */
    static Frame headroom(Frame f) {
        if (Math.abs(f.pitch()) > 60) return f;
        return new Frame(f.pos(), f.look().subtract(0, 0.16 * f.pos().distanceTo(f.look()), 0));
    }

    static Frame frame(Shot s, TrainingClient.Attempt a, Vec3 me, Vec3 feet, Vec3 foe, Vec3 foeFeet, double foeHeight, double foeWidth, Vec3 n, Vec3 mid,
                       double span, double reach, double heldMs, boolean still) {
        // How far into this shot's hold (drives its drift).
        double k = still ? 0 : Shots.ease(Math.min(1, heldMs / 1800));
        Vec3 sideDir = Shots.perpendicular(n, side);
        return switch (s) {
            case WIDE -> {
                double eased = still ? 0 : Shots.ease(progress(a));
                // Side-on, a little behind the contestant, turning ~25 degrees across the contest.
                double orbit = Math.toRadians(-20 * side) + Math.toRadians(ORBIT_DEGREES) * eased * side;
                Vec3 out = Shots.rotate(sideDir, orbit);
                double dist = reach * (1 - 0.18 * eased);
                // Aimed a little under them, so the pair sits above the meter and the gauges.
                yield Shots.place(mid, mid.add(out.scale(dist)).add(0, 0.9 + span * 0.12, 0));
            }
            // Low and close on you, looking up past your arms at it: you drive it back.
            case LOW_ON_YOU -> {
                Vec3 at = feet.subtract(n.scale(0.8)).add(sideDir.scale(2.5 - 0.3 * k)).add(0, 0.35, 0);
                Frame f = Shots.place(me, at);
                yield new Frame(f.pos(), me.lerp(foe, 0.4 + 0.1 * k).subtract(0, 0.15, 0));
            }
            // Over its shoulder (above it, and off to the side, clear of its bulk) down at your face, straining.
            case OVER_ITS_SHOULDER -> {
                Vec3 face = me.add(0, 0.45, 0);
                Vec3 at = foeFeet.add(n.scale(foeWidth * 0.5 + 0.9 - 0.2 * k)).add(sideDir.scale(foeWidth * 0.5 + 0.7))
                        .add(0, Math.max(1.9, foeHeight * 0.85 + 0.35), 0);
                yield new Frame(Shots.place(face, at).pos(), face);
            }
            // Close on the hands where they meet, from the side, pushing in.
            case HANDS -> {
                Vec3 hands = me.lerp(foe, 0.45).add(0, -0.1, 0);
                yield Shots.place(hands, hands.add(sideDir.scale(1.9 - 0.4 * k)).add(n.scale(-0.3)).add(0, 0.25, 0));
            }
            // Low beside you, under it, looking up as it bears down: it towers over you.
            case LOW_UNDER_IT -> {
                Vec3 at = feet.add(sideDir.scale(2.0 - 0.2 * k)).subtract(n.scale(1.2)).add(0, 0.25, 0);
                Vec3 look = foeFeet.add(0, foeHeight * 0.7, 0);
                yield new Frame(Shots.place(look, at).pos(), look);
            }
            // Over your shoulder into it.
            case OVER_YOUR_SHOULDER -> Shots.overShoulder(me.add(0, 0.25, 0), foe, side, 1.2 - 0.2 * k);
            // From above: pinned under its bulk.
            case ABOVE -> {
                // Straight down, turned so the pair lies across the screen, and set a little off them so they sit
                // in the upper half (the HUD has the lower).
                Vec3 base = mid.subtract(sideDir.scale(0.9)).subtract(0, 0.6, 0);
                Vec3 at = Shots.clip(base, base.add(0, 4.6 + Math.max(0, foeHeight - 2) - 0.6 * k, 0));
                yield new Frame(at, base.add(sideDir.scale(0.02)));
            }
            // The reversal: low behind you as it reels, then pulled out to the wide.
            case REVERSAL -> {
                Vec3 lowAt = feet.subtract(n.scale(1.4)).add(sideDir.scale(2.2)).add(0, 0.3, 0);
                Vec3 reeling = foeFeet.add(0, foeHeight * 0.6, 0);
                Frame low = new Frame(Shots.place(reeling, lowAt).pos(), reeling);
                Frame wide = Shots.place(mid, mid.add(sideDir.scale(reach * 1.15)).add(0, 1.6 + span * 0.2, 0));
                yield heldMs < 650 ? low : low.lerp(wide, Shots.ease((heldMs - 650) / 900));
            }
            // Thrown: side-on to you as you go, pulled back, following.
            case THROWN -> Shots.place(me, me.add(sideDir.scale(4.6)).add(n.scale(-1.2)).add(0, 1.2, 0));
        };
    }
}
