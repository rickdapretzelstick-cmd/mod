package dev.rick.jjk.client.training;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.Shots;
import dev.rick.jjk.progression.training.TrainingCheck;
import dev.rick.jjk.progression.training.TrainingNet;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The training's camera, cut like an anime training montage: one shot per phase of the challenge.
 *
 * <ul>
 *   <li>Phase 1: the scene's own established shot (sent by the server), reached by a slow push-in through the intro.</li>
 *   <li>Phase 2: a cut to a closer side angle on the same subject.</li>
 *   <li>Phase 3: a cut to a low hero angle, close, looking up.</li>
 *   <li>Success: the climax, a slow orbit round the subject pulling out.</li>
 * </ul>
 *
 * A cut never lands on a press: it waits (at most {@link #MAX_WAIT_MS}) for a quiet moment with no check cued or open
 * nearby, and each cut gets a title beat on the HUD ({@link #titleBeat}). Each shot drifts gently while it holds, and
 * never sits in a wall. With Reduced Camera Motion it is the one still established shot throughout (no cuts, no drift).
 * The participant's own view comes back the moment the session ends.
 */
public final class TrainingCamera {
    private static long since;
    /** The shot showing (1-3 the phases, 4 the climax), and when it was cut to (ns). */
    private static int shot;
    private static long cutAt;

    /** A cut waits this long at most for a quiet moment; a check this close to cueing or open counts as busy. */
    static final double MAX_WAIT_MS = 1500, QUIET_MS = 450;

    private TrainingCamera() {}

    static void begin() {
        if (since == 0) since = System.nanoTime();
        shot = 1;
        cutAt = since;
    }

    public static boolean active() {
        return TrainingClient.attempt() != null && since != 0;
    }

    /** The director gives the player's own view back once the session is over (see CameraDirector). */
    public static void tick(Minecraft mc) {
        if (TrainingClient.attempt() == null || mc.player == null || !mc.player.isAlive()) since = 0;
    }

    public static void reset() {
        since = 0;
    }

    /** The shot showing now (1-3 per phase, 4 the climax), for the HUD and tests. */
    public static int shot() {
        return shot;
    }

    /** Tests: cuts to a shot now (1-4), as the montage would. */
    public static void cutForTest(int s) {
        shot = s;
        cutAt = System.nanoTime();
    }

    /** The phase title beat to show (2 or 3) while a cut is fresh, else 0. */
    public static int titleBeat() {
        if (since == 0 || shot < 2 || shot > 3 || CameraDirector.reducedMotion()) return 0;
        return (System.nanoTime() - cutAt) / 1e6 < 1100 ? shot : 0;
    }

    /** No check cued, open or about to be, around {@code t}. */
    static boolean quiet(TrainingClient.Attempt a, double t) {
        for (TrainingCheck c : a.checks()) {
            int r = a.eval.result(c.id());
            if (r >= 0 && r != TrainingCheck.HOLDING) continue;
            if (c.cueMs() - QUIET_MS < t && t < c.endMs() + 150) return false;
        }
        return true;
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        TrainingClient.Attempt a = TrainingClient.attempt();
        if (a == null || since == 0) return null;
        TrainingClient.advance();
        Vec3 cam = a.start.camera(), look = a.start.look();
        boolean still = CameraDirector.reducedMotion();
        long now = System.nanoTime();
        // Which shot the montage is on: the phase's, once a quiet moment comes (or it has waited long enough).
        if (!still) {
            int want = a.state == TrainingNet.SUCCESS ? 4 : a.clock >= a.start.phase3AtMs() && a.start.phase3AtMs() > 0 ? 3
                    : a.clock >= a.start.phase2AtMs() && a.start.phase2AtMs() > 0 ? 2 : 1;
            if (want > shot) {
                double boundary = want == 3 ? a.start.phase3AtMs() : a.start.phase2AtMs();
                if (want == 4 || quiet(a, a.clock) || a.clock - boundary > MAX_WAIT_MS) {
                    shot = want;
                    cutAt = now;
                }
            }
        }
        double held = (now - cutAt) / 1e6;
        Minecraft mc = Minecraft.getInstance();
        Vec3 me = mc.player != null ? mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) : look;
        Frame f = frame(still ? 1 : shot, a, cam, look, me, held, still);
        // Blended in from the player's own view over the first moments (the realm's fade covers the rest).
        double in = Mth.clamp((now - since) / 4e8, 0, 1);
        in = in * in * (3 - 2 * in);
        return f.blendFrom(vanillaPos, vanillaYaw, vanillaPitch, in);
    }

    static Frame frame(int s, TrainingClient.Attempt a, Vec3 cam, Vec3 look, Vec3 me, double heldMs, boolean still) {
        Vec3 out = cam.subtract(look);
        double dist = Math.max(1.5, out.length());
        Vec3 flat = new Vec3(out.x, 0, out.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        double drift = still ? 0 : Shots.ease(Math.min(1, heldMs / 6000));
        return switch (s) {
            case 2 -> {
                // Closer, from the side, drifting a little further round.
                Vec3 dir = Shots.rotate(flat, Math.toRadians(62 + 10 * drift));
                yield Shots.place(look, look.add(dir.scale(dist * (0.72 - 0.06 * drift))).add(0, out.y * 0.5, 0));
            }
            case 3 -> {
                // Low and close on the participant from the other side, looking up past them at what they train on:
                // the hero angle.
                Vec3 chest = me.add(0, 1.1, 0);
                Vec3 toCam = new Vec3(cam.x - me.x, 0, cam.z - me.z);
                toCam = toCam.lengthSqr() < 1e-4 ? flat : toCam.normalize();
                // Whichever side of them has room (a small room may have none: then the side angle again).
                Vec3 best = null;
                double room = 0;
                for (int side : new int[] {-1, 1}) {
                    Vec3 dir = Shots.rotate(toCam, Math.toRadians(side * (50 + 8 * drift)));
                    Vec3 want = me.add(dir.scale(2.4 - 0.25 * drift)).add(0, 0.35, 0);
                    double clear = Shots.clearance(chest, want);
                    if (clear > room + 0.05) {
                        room = clear;
                        best = want;
                    }
                }
                if (best == null || room < 1.4) yield frame(2, a, cam, look, me, heldMs, still);
                Vec3 pos = Shots.clip(chest, best);
                if (!Shots.clear(pos)) yield frame(2, a, cam, look, me, heldMs, still);
                yield new Frame(pos, chest.lerp(look, 0.25));
            }
            case 4 -> {
                // The climax: a slow orbit round the subject, pulling out.
                double k = Shots.ease(Math.min(1, heldMs / 3200));
                Vec3 dir = Shots.rotate(flat, Math.toRadians(-38 + 128 * k));
                yield Shots.place(look, look.add(dir.scale(dist * (0.6 + 0.6 * k))).add(0, 0.4 + out.y * 0.4 + 1.2 * k, 0));
            }
            default -> {
                // The established shot: a slow push in through the intro, from a little further back and higher; then it holds.
                double introMs = Math.max(1, a.start.introMs());
                double k = a.state == TrainingNet.INTRO ? Mth.clamp((a.clock + introMs) / introMs, 0, 1) : 1;
                k = Shots.ease(k);
                Vec3 back = out.normalize().scale(2.6).add(0, 0.9, 0);
                yield new Frame(cam.add(back.scale(1 - k)), look);
            }
        };
    }
}
