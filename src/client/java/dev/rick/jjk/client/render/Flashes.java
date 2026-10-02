package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived geometric effects drawn with additive light. These are the building blocks each technique composes into
 * its own visual language:
 * <ul>
 *   <li>FLASH: camera-facing glow that shrinks and fades (impact light).</li>
 *   <li>RING: camera-facing shockwave ring.</li>
 *   <li>RIPPLE: ring in a plane facing a direction (Infinity's spatial ripple, bow shocks).</li>
 *   <li>GROUND: flat horizontal shockwave rolling along the ground.</li>
 *   <li>LENS: a shock shell, crisp at its silhouette, that grows or collapses (space distorting: teleport, Awakening).</li>
 *   <li>BEAM: glowing line between two points (teleport streaks, the Awakening pillar, swing trails).</li>
 *   <li>BOLT: lightning between two points, re-forking every tick so it crackles.</li>
 *   <li>SWIRL: a comet-tailed arc whipping round a point (wind and energy wrapping a charge).</li>
 *   <li>INK: an alpha-blended blot of dark energy or smoke that swells and thins away.</li>
 * </ul>
 * Every effect can start after a delay, so impacts can be sequenced (compression → release → aftershock).
 */
public final class Flashes {
    public enum Type { FLASH, RING, RIPPLE, GROUND, LENS, BEAM, DARK_BEAM, BOLT, SWIRL, INK }

    private static final List<Effect> ACTIVE = new ArrayList<>();
    private static final int MAX = 320;

    private record Effect(Type type, Vec3 pos, Vec3 dir, float[] color, float from, float to, float alpha, int life, long start, float width,
                          float spin, int seed) {
        Effect(Type type, Vec3 pos, Vec3 dir, float[] color, float from, float to, float alpha, int life, long start, float width) {
            this(type, pos, dir, color, from, to, alpha, life, start, width, 0, SEEDS.nextInt());
        }
    }

    private static final java.util.Random SEEDS = new java.util.Random();

    private Flashes() {}

    private static void add(Effect e) {
        if (ACTIVE.size() >= MAX) ACTIVE.removeFirst();
        ACTIVE.add(e);
    }

    public static void flash(Vec3 pos, float size, float[] c, int life, long now) {
        add(new Effect(Type.FLASH, pos, Vec3.ZERO, c, size, size * 0.25f, 0.9f, life, now, 0));
    }

    public static void flash(Vec3 pos, float from, float to, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.FLASH, pos, Vec3.ZERO, c, from, to, alpha, life, start, 0));
    }

    public static void ring(Vec3 pos, float from, float to, float[] c, float alpha, int life, long now) {
        add(new Effect(Type.RING, pos, Vec3.ZERO, c, from, to, alpha, life, now, 0));
    }

    public static void ripple(Vec3 pos, Vec3 normal, float from, float to, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.RIPPLE, pos, normal, c, from, to, alpha, life, start, 0));
    }

    public static void ground(Vec3 pos, float from, float to, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.GROUND, pos, new Vec3(0, 1, 0), c, from, to, alpha, life, start, 0));
    }

    public static void lens(Vec3 pos, float from, float to, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.LENS, pos, Vec3.ZERO, c, from, to, alpha, life, start, 0));
    }

    /** Beam from {@code a} to {@code b}. */
    public static void beam(Vec3 a, Vec3 b, float width, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.BEAM, a, b.subtract(a), c, 1, 1, alpha, life, start, width));
    }

    /** A dark (alpha-blended, not additive) beam: the black cores of Sukuna's slashes, visible against anything. */
    public static void darkBeam(Vec3 a, Vec3 b, float width, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.DARK_BEAM, a, b.subtract(a), c, 1, 1, alpha, life, start, width));
    }

    /** Lightning from {@code a} to {@code b}: it re-forks every tick, so it crackles for its whole life. */
    public static void bolt(Vec3 a, Vec3 b, float width, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.BOLT, a, b.subtract(a), c, 1, 1, alpha, life, start, width));
    }

    /**
     * An arc whipping round {@code pos} in the plane facing {@code normal}: its radius runs {@code from} to {@code to} while
     * its head turns {@code spin} radians a tick (negative the other way), trailing a tail {@code sweep} radians long.
     */
    public static void swirl(Vec3 pos, Vec3 normal, float from, float to, float sweep, float spin, float width, float[] c, float alpha, int life,
                             long start) {
        add(new Effect(Type.SWIRL, pos, normal, c, from, to, alpha, life, start, width, spin, Float.floatToIntBits(sweep)));
    }

    /** A blot of dark energy or smoke (drawn darker than what's behind it) swelling from {@code from} to {@code to}. */
    public static void ink(Vec3 pos, float from, float to, float[] c, float alpha, int life, long start) {
        add(new Effect(Type.INK, pos, Vec3.ZERO, c, from, to, alpha, life, start, 0));
    }

    /**
     * True Love Beam: drawn from {@link dev.rick.jjk.yuta.TrueLoveBeamProfile}, the same shape the server hits with, keyed by
     * its caster so a beam cut short on the server is cut short here too. {@code dir} is a unit vector.
     */
    public record LoveBeam(Vec3 origin, Vec3 dir, double length, double radius, int grow, int collapse, int life, long start, boolean quick) {}

    private static final java.util.Map<Integer, LoveBeam> LOVE_BEAMS = new java.util.HashMap<>();
    /** When each beam was first drawn: its grow-in counts from there (the packet's own tick can be a couple of ticks stale). */
    private static final java.util.Map<Integer, Float> LOVE_SEEN = new java.util.HashMap<>();

    public static void loveBeam(int key, LoveBeam b) {
        LOVE_BEAMS.put(key, b);
        LOVE_SEEN.remove(key);
    }

    /** The beam's age in ticks at {@code time}: from the later of its stamp and its first drawn frame. */
    private static float loveAge(int key, LoveBeam b, float time) {
        float seen = LOVE_SEEN.computeIfAbsent(key, k -> time);
        return time - Math.max(b.start(), Math.min(seen, b.start() + 4));
    }

    /** Stops a beam early: it collapses over the next few ticks instead of holding. */
    public static void stopLoveBeam(int key, long now) {
        LoveBeam b = LOVE_BEAMS.get(key);
        if (b == null) return;
        int at = (int) loveAge(key, b, now);
        int quickEnd = Math.min(3, b.collapse());
        if (at + quickEnd < b.life()) {
            LOVE_BEAMS.put(key, new LoveBeam(b.origin(), b.dir(), b.length(), b.radius(), b.grow(), quickEnd, at + quickEnd, b.start(), b.quick()));
        }
    }

    /** How far a beam's front has got and how thick it is right now (0 once it is over), for effects that follow it. */
    public static double[] loveBeamState(int key, long now, float partial) {
        LoveBeam b = LOVE_BEAMS.get(key);
        if (b == null) return null;
        float t = loveAge(key, b, now + partial);
        return new double[] {dev.rick.jjk.yuta.TrueLoveBeamProfile.front(b.length(), b.grow(), t),
                dev.rick.jjk.yuta.TrueLoveBeamProfile.scale(t, b.life(), b.collapse())};
    }

    public static void clear() {
        ACTIVE.clear();
        LOVE_BEAMS.clear();
        LOVE_SEEN.clear();
    }

    private static void renderLoveBeams(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
        var it = LOVE_BEAMS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            LoveBeam b = entry.getValue();
            float t = loveAge(entry.getKey(), b, now + partial);
            if (t >= b.life() || t > 400) {
                it.remove();
                LOVE_SEEN.remove(entry.getKey());
                continue;
            }
            if (t < 0) continue;
            double front = dev.rick.jjk.yuta.TrueLoveBeamProfile.front(b.length(), b.grow(), t);
            float scale = dev.rick.jjk.yuta.TrueLoveBeamProfile.scale(t, b.life(), b.collapse());
            if (scale <= 0.01f) continue;
            Vector3f n = new Vector3f((float) b.dir().x, (float) b.dir().y, (float) b.dir().z).normalize();
            Vector3f u = Math.abs(n.y) < 0.95f ? new Vector3f(0, 1, 0).cross(n, new Vector3f()).normalize() : new Vector3f(1, 0, 0).cross(n, new Vector3f()).normalize();
            Vector3f v = n.cross(u, new Vector3f()).normalize();
            Vector3f camRel = new Vector3f((float) (cam.x - b.origin().x), (float) (cam.y - b.origin().y), (float) (cam.z - b.origin().z));
            ps.pushPose();
            ps.translate(b.origin().x - cam.x, b.origin().y - cam.y, b.origin().z - cam.z);
            float fade = Math.min(1f, scale * 1.4f);
            // A slow throb along it, like the JJS beam's pulsing body.
            float throb = 1f + 0.05f * Mth.sin(t * 1.3f);
            double R = b.radius() * scale * throb;
            int rings = b.quick() ? 14 : 36, sides = b.quick() ? 12 : 20;
            float len = (float) front;
            java.util.function.DoubleUnaryOperator at = s -> dev.rick.jjk.yuta.TrueLoveBeamProfile.radius(s, front, 1.0);
            // A cannon blast, not a laser. Its solid body fills the whole hitbox (radius R: 3 blocks across), opaque so it
            // reads as a mass of energy even against a bright sky.
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R * 0.98, rings, sides, 0.86f, 0.22f, 0.92f, 0.85f * fade, camRel, 0.5f, true);
            // A churning outer aura past it: lumps rolling along, swelling the whole thing to 4 blocks and more at moments.
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R * (1.32 + 0.14 * Math.sin(s * 0.9 - t * 0.8) + 0.08 * Math.sin(s * 2.3 + t * 1.7)),
                    rings, sides, 0.97f, 0.36f, 1f, 0.3f * fade, camRel, 0.04f);
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R * (1.6 + 0.2 * Math.sin(s * 0.5 - t * 0.5)), rings, sides,
                    0.9f, 0.3f, 1f, 0.12f * fade, camRel, 0.02f);
            // The pink body, the hot inner body, and a near-white core a block and a quarter thick.
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R, rings, sides, 1f, 0.45f, 1f, 0.6f * fade, camRel, 0.3f);
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R * 0.7, rings, sides, 1f, 0.78f, 1f, 0.85f * fade, camRel, 0.45f);
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R * 0.42, rings, sides, 1f, 0.97f, 1f, 1f * fade, camRel, 0.65f);
            Glow.tube(c, ps, n, u, v, len, s -> at.applyAsDouble(s) * R * 0.28, rings, sides, 1f, 1f, 1f, 1f * fade, camRel, 0.9f);
            // Glow masses along its axis, always facing the camera: head-on they stack into a disc of energy the beam's
            // full width (a tube seen end-on is only its rim), from the side they churn inside the body.
            float step = b.quick() ? 2.5f : 2f;
            for (float sAt = 1f + (t * 0.9f) % step; sAt < len - 0.5f; sAt += step) {
                float rad = (float) (at.applyAsDouble(sAt) * R);
                ps.pushPose();
                ps.translate(n.x * sAt, n.y * sAt, n.z * sAt);
                Glow.halo(c, ps, camRot, rad * 1.3f, 1f, 0.4f, 1f, 0.28f * fade);
                Glow.halo(c, ps, camRot, rad * 0.55f, 1f, 0.92f, 1f, 0.4f * fade);
                ps.popPose();
            }
            if (!b.quick()) {
                // Dark bands rolling out along it (the black rings round the JJS beam).
                float gap = 5.5f;
                float off = (t * 1.8f) % gap;
                for (float sAt = 2f + off; sAt + 1f < len - 1.5f; sAt += gap) {
                    float rad = (float) (at.applyAsDouble(sAt) * R * 1.03);
                    Glow.inkBand(c, ps, n, u, v, sAt, sAt + 0.8f, rad, sides, 0.24f, 0.02f, 0.3f, 0.42f * fade);
                }
                // Two arcs of light spiralling round it, turning as it pours forward.
                for (int k = 0; k < 2; k++) {
                    int m = Math.max(8, (int) (len * 1.5f));
                    Vector3f[] pts = new Vector3f[m + 1];
                    for (int i = 0; i <= m; i++) {
                        float sAt = len * i / m;
                        float ang = sAt * 0.55f - t * 0.6f + k * Mth.PI;
                        float rad = (float) (at.applyAsDouble(sAt) * R * 1.12);
                        pts[i] = new Vector3f(n).mul(sAt).add(new Vector3f(u).mul(Mth.cos(ang) * rad)).add(new Vector3f(v).mul(Mth.sin(ang) * rad));
                    }
                    Glow.strip(c, ps, pts, 0.18f, 1f, 0.85f, 1f, 0.7f * fade, camRel);
                }
            }
            ps.popPose();
        }
    }

    public static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
        renderLoveBeams(c, ps, cam, camRot, now, partial);
        Iterator<Effect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Effect f = it.next();
            float age = now - f.start + partial;
            if (age < 0) continue;
            float t = age / f.life;
            if (t >= 1f) {
                it.remove();
                continue;
            }
            float ease = 1 - (1 - t) * (1 - t);
            float size = Mth.lerp(ease, f.from, f.to);
            // A flash holds full strength for its first moments, so even a three-tick hit flash is seen, then goes fast.
            float hold = t < 0.2f ? 1f : (1 - t) / 0.8f;
            float a = f.type == Type.FLASH ? f.alpha * hold * hold : f.alpha * (1 - t);
            if (f.type == Type.FLASH || f.type == Type.LENS || f.type == Type.RING) {
                // Seen from inside, a flash covers the whole screen: keep a hint of it, not a white-out.
                double d = cam.distanceTo(f.pos);
                if (d < size) a *= (float) Math.max(0.2, d / size);
                // During a clash, nothing may tint the whole view over the lanes.
                a *= dev.rick.jjk.client.clash.ClashFocus.world(cam, f.pos, size);
            }
            float[] col = f.color;
            ps.pushPose();
            ps.translate(f.pos.x - cam.x, f.pos.y - cam.y, f.pos.z - cam.z);
            switch (f.type) {
                case FLASH -> {
                    Glow.halo(c, ps, camRot, size, col[0], col[1], col[2], a);
                    Glow.halo(c, ps, camRot, size * 0.35f, 1f, 1f, 1f, a);
                }
                case BOLT -> {
                    Vec3 d = f.dir;
                    Glow.bolt(c, ps, new Vector3f(), new Vector3f((float) d.x, (float) d.y, (float) d.z), f.width, col[0], col[1], col[2],
                            f.alpha * (1 - t * t), f.seed * 31L + (long) age);
                }
                case SWIRL -> {
                    orientY(ps, f.dir);
                    float sweep = Float.intBitsToFloat(f.seed);
                    float fade = Math.min(1f, age / 2f) * (1 - t);
                    Glow.swirl(c, ps, size, (f.seed & 7) + f.spin * age, sweep, f.width, col[0], col[1], col[2], f.alpha * fade, 0);
                }
                case INK -> Glow.inkBlob(c, ps, camRot, size, col[0], col[1], col[2], Math.min(1f, f.alpha * (1 - t) * Math.min(1f, age / 1.5f)),
                        f.seed, age);
                case RING -> {
                    ps.rotate(camRot);
                    ps.rotate(Axis.XP.rotationDegrees(90));
                    Glow.ring(c, ps, size, Math.max(0.12f, size * 0.16f), col[0], col[1], col[2], a);
                }
                case RIPPLE, GROUND -> {
                    orientY(ps, f.dir);
                    Glow.ring(c, ps, size, Math.max(0.08f, size * (f.type == Type.GROUND ? 0.22f : 0.12f)), col[0], col[1], col[2], a);
                }
                case LENS -> {
                    Vector3f toCam = new Vector3f((float) (cam.x - f.pos.x), (float) (cam.y - f.pos.y), (float) (cam.z - f.pos.z));
                    // A huge shell filling the view reads as a bubble, not a shockwave: the bigger, the fainter.
                    float big = Mth.clamp(5f / Math.max(0.1f, size), 0.35f, 1f);
                    Glow.shell(c, ps, Math.max(0.05f, size), col[0], col[1], col[2], Math.min(1f, a * 1.3f * big), toCam);
                }
                case DARK_BEAM -> {
                    Vec3 d = f.dir;
                    float len = (float) d.length();
                    if (len > 1e-3) {
                        Vec3 n = d.scale(1 / len);
                        ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
                        ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
                        Glow.darkBeam(c, ps, len, f.width * (1 - t * 0.4f), col[0], col[1], col[2], Math.min(1f, f.alpha * (1 - t * t)));
                    }
                }
                case BEAM -> {
                    Vec3 d = f.dir;
                    float len = (float) d.length();
                    if (len > 1e-3) {
                        Vec3 n = d.scale(1 / len);
                        ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
                        ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
                        float w = f.width * (1 - t * 0.6f);
                        Glow.beam(c, ps, len, w, col[0], col[1], col[2], a);
                        Glow.beam(c, ps, len, w * 0.35f, 1f, 1f, 1f, a);
                    }
                }
            }
            ps.popPose();
        }
    }

    /** Rotates the pose so its +Y axis points along {@code normal} (rings are drawn in the XZ plane). */
    static void orientY(PoseStack ps, Vec3 normal) {
        if (normal.lengthSqr() < 1e-6) return;
        Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z).normalize();
        ps.rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), n));
    }
}
