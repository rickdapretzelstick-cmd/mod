package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.clash.BeamClashClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Every Last Drop drawn: True Cannon's whole reserve fired from his fingertip as one round, dense, blue-white cannon
 * shot. Where True Love Beam is a square torrent, this is a tight cylinder: a white-hot core, a body of cold blue light
 * wound round with rings racing down it, sparks peeling off, a muzzle flare at the finger and a burst where it lands.
 * Drawn from the server's own numbers (the cylinder it hits with), capped where a clash holds it.
 */
public final class RyuBeams {
    private static final Map<Integer, ClientBeam> BEAMS = new HashMap<>();
    private static final Random RNG = new Random();

    private RyuBeams() {}

    public static void start(int key, ClientBeam b) {
        BEAMS.put(key, b);
    }

    public static ClientBeam get(int key) {
        return BEAMS.get(key);
    }

    public static void stop(int key, long now) {
        ClientBeam b = BEAMS.get(key);
        if (b != null) b.stop(now);
    }

    public static void clear() {
        BEAMS.clear();
    }

    /** {front, scale} of a beam right now (null once it's over). */
    public static double[] state(int key, long now, float partial) {
        ClientBeam b = BEAMS.get(key);
        if (b == null) return null;
        float t = b.age(now + partial, false);
        return new double[] {Math.min(b.front(t), BeamClashClient.reach(key)), b.scale(t)};
    }

    public static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
        Iterator<Map.Entry<Integer, ClientBeam>> it = BEAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ClientBeam> e = it.next();
            ClientBeam b = e.getValue();
            float time = now + partial;
            float t = b.age(time, true);
            if (t >= b.life() || !b.held && t > 600) {
                it.remove();
                continue;
            }
            if (t < 0) continue;
            float scale = b.scale(t);
            if (scale <= 0.01f) continue;
            draw(c, ps, cam, camRot, e.getKey(), b, t, time, scale);
        }
    }

    private static void draw(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, int key, ClientBeam b, float t, float time, float scale) {
        double cap = BeamClashClient.reach(key);
        boolean capped = cap < b.front(t);
        float len = (float) Math.min(b.front(t), cap);
        if (len < 0.2f) return;
        Vector3f n = v(b.dir);
        Vector3f u = v(b.dir.cross(Math.abs(b.dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize());
        Vector3f w = new Vector3f(n).cross(u).normalize();
        Vector3f camRel = v(cam.subtract(b.origin));
        double along = Mth.clamp(cam.subtract(b.origin).dot(b.dir), 0, len);
        boolean near = cam.distanceTo(b.origin.add(b.dir.scale(along))) < 80;
        float p = BeamClashClient.pressure(key);
        float flicker = p < 0 ? 1f + p * 0.35f * (0.5f + 0.5f * Mth.sin(time * 3.1f) * Mth.sin(time * 1.3f)) : 1f;
        float since = time - b.surgeAt;
        float wave = since >= 0 && since < 5 ? since / 4f * len : -100f;
        float boost = since >= 0 && since < 8 ? 1 - since / 8f : 0;
        float bright = (1f + 0.4f * boost + 0.35f * Math.max(0, p)) * flicker;
        float fade = Math.min(1f, scale * 1.3f);
        float R = (float) (b.half * scale * (1f + 0.04f * Mth.sin(t * 1.7f) + 0.08f * boost));
        // Narrow at the fingertip, opening to full width within a few blocks, a rounded head.
        final float L = len;
        Glow.Radius at = s -> R * Math.min(1.0, 0.35 + s / 3.0) * (s > L - 1 ? Math.sqrt(Math.max(0.05, (L - s))) : 1.0);
        int rings = Math.max(8, Math.min(40, (int) (len / 1.5f)));

        ps.pushPose();
        ps.translate(b.origin.x - cam.x, b.origin.y - cam.y, b.origin.z - cam.z);
        // The body: a dense deep blue (alpha-blended so it holds against the sky), cold blue light over it, a hot inner
        // body and a white core.
        Glow.tube(c, ps, n, u, w, len, s -> at.at(s) * 0.98, rings, 14, 0.1f, 0.3f, 0.85f, 0.7f * fade, camRel, 0.6f, true);
        Glow.tube(c, ps, n, u, w, len, at, rings, 14, 0.2f, 0.5f, 1f, 0.5f * fade * bright, camRel, 0.35f);
        Glow.tube(c, ps, n, u, w, len, s -> at.at(s) * (0.62 + 0.04 * Math.sin(s * 1.9 - t * 4.3)), rings, 12, 0.4f, 0.72f, 1f, 0.55f * fade * bright, camRel, 0.6f);
        Glow.tube(c, ps, n, u, w, len, s -> at.at(s) * 0.24, rings, 10, 0.9f, 0.97f, 1f, 0.85f * fade, camRel, 0.9f);
        // A loose shimmering sheath.
        Glow.tube(c, ps, n, u, w, len, s -> at.at(s) * (1.25 + 0.08 * Math.sin(s * 0.8 - t * 1.4)), rings, 14, 0.3f, 0.6f, 1f, 0.16f * fade, camRel, 0.05f);
        // Rings racing down it with the flow.
        float gap = 2.4f;
        for (float s0 = 0.6f + (t * 2.6f) % gap; s0 < len - 0.5f; s0 += gap) {
            ps.pushPose();
            ps.translate(n.x * s0, n.y * s0, n.z * s0);
            Flashes.orientY(ps, b.dir);
            Glow.ring(c, ps, (float) at.at(s0) * 1.12f, 0.12f, 0.75f, 0.92f, 1f, 0.55f * fade * bright);
            ps.popPose();
        }
        // The surge: a white band running down it.
        if (wave > -50 && wave < len) {
            float w0 = Math.max(0, wave - 2f);
            ps.pushPose();
            ps.translate(n.x * w0, n.y * w0, n.z * w0);
            Glow.tube(c, ps, n, u, w, Math.min(2.6f, len - w0), s -> at.at(w0 + s) * 1.15, 3, 12, 1f, 1f, 1f, 0.7f * (1 - since / 5f) * fade, camRel, 0.3f);
            ps.popPose();
        }
        if (near) {
            // Two threads of light spiralling tight round it.
            for (int k = 0; k < 2; k++) {
                int m = Math.max(8, (int) (len * 1.6f));
                Vector3f[] pts = new Vector3f[m + 1];
                for (int i = 0; i <= m; i++) {
                    float s = len * i / m;
                    float ang = s * 0.9f - t * 1.1f + k * Mth.PI;
                    float r = (float) at.at(s) * 1.18f;
                    pts[i] = new Vector3f(n).mul(s).add(new Vector3f(u).mul(Mth.cos(ang) * r)).add(new Vector3f(w).mul(Mth.sin(ang) * r));
                }
                Glow.strip(c, ps, pts, 0.1f, 0.8f, 0.95f, 1f, 0.7f * fade, camRel);
            }
            // Arcs cracking off it.
            RNG.setSeed(key * 104729L + (long) t * 17L);
            for (int k = 0; k < 4; k++) {
                float s = RNG.nextFloat() * len;
                float ang = RNG.nextFloat() * Mth.TWO_PI;
                Vector3f side = new Vector3f(u).mul(Mth.cos(ang)).add(new Vector3f(w).mul(Mth.sin(ang)));
                Vector3f from = new Vector3f(n).mul(s).add(new Vector3f(side).mul((float) at.at(s)));
                Vector3f to = new Vector3f(from).add(new Vector3f(side).mul(1f + RNG.nextFloat() * 1.8f)).add(new Vector3f(n).mul(RNG.nextFloat() * 1.5f));
                Glow.bolt(c, ps, from, to, 0.05f, 0.75f, 0.92f, 1f, 0.9f * fade, RNG.nextLong());
            }
        }
        // The muzzle at his fingertip.
        Glow.halo(c, ps, camRot, 2.4f * scale * bright, 0.35f, 0.65f, 1f, 0.5f * fade);
        Glow.halo(c, ps, camRot, 1.0f * scale * bright, 1f, 1f, 1f, 0.9f * fade);
        // Where it lands (a clash draws its own collision).
        if (!capped) {
            float s = (float) Mth.clamp(b.strike.subtract(b.origin).dot(b.dir), 1, len);
            float hit = since >= 0 && since < 6 ? 1 - since / 6f : 0;
            ps.pushPose();
            ps.translate(n.x * s, n.y * s, n.z * s);
            float pulse = 1f + 0.1f * Mth.sin(t * 2.6f) + 0.3f * hit;
            Glow.halo(c, ps, camRot, 3.8f * pulse, 0.3f, 0.6f, 1f, 0.5f * fade * bright);
            Glow.halo(c, ps, camRot, 1.8f * pulse, 0.8f, 0.95f, 1f, 0.8f * fade * bright);
            Flashes.orientY(ps, b.dir);
            Glow.ring(c, ps, R * (1.6f + 2.4f * (1 - hit)), 0.3f, 0.7f, 0.9f, 1f, 0.6f * hit * fade);
            ps.popPose();
        }
        ps.popPose();
    }

    private static Vector3f v(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }
}
