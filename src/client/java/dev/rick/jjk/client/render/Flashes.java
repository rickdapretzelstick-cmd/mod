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
 *   <li>LENS: rim-lit sphere that grows or collapses (space distorting: teleport, Awakening).</li>
 *   <li>BEAM: glowing line between two points (teleport streaks, the Awakening pillar, swing trails).</li>
 * </ul>
 * Every effect can start after a delay, so impacts can be sequenced (compression → release → aftershock).
 */
public final class Flashes {
    public enum Type { FLASH, RING, RIPPLE, GROUND, LENS, BEAM }

    private static final List<Effect> ACTIVE = new ArrayList<>();
    private static final int MAX = 160;

    private record Effect(Type type, Vec3 pos, Vec3 dir, float[] color, float from, float to, float alpha, int life, long start, float width) {}

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

    public static void clear() {
        ACTIVE.clear();
    }

    public static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
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
            float a = f.alpha * (1 - t);
            if (f.type == Type.FLASH || f.type == Type.LENS || f.type == Type.RING) {
                // Seen from inside, a flash covers the whole screen: keep a hint of it, not a white-out.
                double d = cam.distanceTo(f.pos);
                if (d < size) a *= (float) Math.max(0.2, d / size);
            }
            float[] col = f.color;
            ps.pushPose();
            ps.translate(f.pos.x - cam.x, f.pos.y - cam.y, f.pos.z - cam.z);
            switch (f.type) {
                case FLASH -> {
                    a *= 1 - t;
                    Glow.halo(c, ps, camRot, size, col[0], col[1], col[2], a);
                    Glow.halo(c, ps, camRot, size * 0.35f, 1f, 1f, 1f, a);
                }
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
                    Glow.sphere(c, ps, Math.max(0.05f, size), col[0], col[1], col[2], a, toCam, true);
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
