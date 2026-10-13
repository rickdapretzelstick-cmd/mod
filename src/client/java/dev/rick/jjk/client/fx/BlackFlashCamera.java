package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.camera.CameraDirector;
import dev.rick.jjk.client.camera.Frame;
import dev.rick.jjk.client.camera.ShotSequence;
import dev.rick.jjk.client.camera.Shots;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The lethal Black Flash's cinematic, on the striker's screen (and the struck player's): after JJS's Resolute Black Flash
 * finisher. A low, close side-on shot on the contact as it lands; a slow orbit pushing in on the body held in the blow
 * while the power amps up; then, as it is launched, the camera pulls back and up, following the body away. Hard cut in,
 * eased moves within, never through a wall. With Reduced Camera Motion there is no cinematic.
 */
public final class BlackFlashCamera {
    private static long since;
    private static double lengthMs;
    private static int bodyId = -1, strikerId = -1;
    private static Vec3 contact = Vec3.ZERO, side = new Vec3(1, 0, 0);
    @Nullable private static Vec3 lastBody;
    @Nullable private static ShotSequence seq;

    private BlackFlashCamera() {}

    /** A lethal Black Flash at {@code at}: the body {@code body}, its build-up {@code buildMs} long, struck by {@code striker}. */
    static void start(Vec3 at, int body, int striker, double buildMs) {
        if (CameraDirector.reducedMotion()) return;
        Minecraft mc = Minecraft.getInstance();
        bodyId = body;
        strikerId = striker;
        contact = at;
        lastBody = null;
        Entity s = mc.level == null ? null : mc.level.getEntity(striker);
        Vec3 from = s != null ? s.position() : at.add(0, 0, -2);
        Vec3 along = Shots.flatDir(from, at);
        side = Shots.perpendicular(along, 1);
        double build = Math.max(400, buildMs);
        double follow = 1300;
        lengthMs = build + follow;
        since = System.nanoTime();
        seq = new ShotSequence()
                // The contact: low and close, side-on, the two of them locked in it.
                .then(k -> Shots.place(contact, contact.add(side.scale(2.6 - 0.4 * k)).add(0, -0.6, 0).subtract(along.scale(0.6))), build * 0.28)
                // The power amping up: a slow orbit round the held body, pushing in.
                .then(k -> {
                    double a = Math.atan2(side.x, side.z) + Math.toRadians(-20 + 110 * Shots.ease(k));
                    return Shots.orbit(body(), 3.4 - 1.1 * k, 0.5 + 0.4 * k, a);
                }, build * 0.72, ShotSequence.Join.MOVE, 160)
                // The launch: pulled back and up, following the body away.
                .then(k -> {
                    Vec3 b = body();
                    Vec3 want = contact.add(side.scale(4.5 + 6 * k)).add(0, 1.5 + 3.5 * k, 0).subtract(along.scale(2.5 + 2 * k));
                    return Shots.place(b, want);
                }, follow, ShotSequence.Join.MOVE, 220);
    }

    private static Vec3 body() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(bodyId);
        if (e != null && !e.isRemoved()) {
            lastBody = e.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0, e.getBbHeight() * 0.55, 0);
        }
        return lastBody != null ? lastBody : contact;
    }

    public static boolean active() {
        return since != 0 && !CameraDirector.reducedMotion();
    }

    public static void tick(Minecraft mc) {
        if (since != 0 && ((System.nanoTime() - since) / 1e6 > lengthMs || mc.player == null || !mc.player.isAlive() && mc.player.getId() != bodyId)) {
            since = 0;
        }
    }

    /** {x, y, z, yaw, pitch}, or null when not in use. */
    @Nullable
    public static double[] apply(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch) {
        if (!active() || seq == null) return null;
        Frame f = seq.at((System.nanoTime() - since) / 1e6);
        return f == null ? null : f.array();
    }

    /** Tests: whether the cinematic is running. */
    public static boolean running() {
        return since != 0;
    }
}
