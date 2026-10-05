package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.clash.BeamClashClient;
import dev.rick.jjk.yuta.TrueLoveBeamProfile;
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
 * True Love Beam drawn: a SQUARE five-by-five torrent of cursed energy, its silhouette a block-world prism, built of
 * independently moving layers.
 * <ul>
 *   <li>the main body, filling the whole square: a dense alpha-blended mass (so it holds against a bright sky) with
 *   added pink light over it, bands of brightness racing from Rika's mouth to the impact;</li>
 *   <li>a hot inner body and a near-white core two and a half blocks across, pulsing and surging, churning fast;</li>
 *   <li>square sheets of light across it, so seen head-on it is a solid square, not the rim of a hollow tube;</li>
 *   <li>an unstable aura round the square: a looser square that heaves, streaks racing along its edges, arcs spiralling
 *   round it, lightning cracking off its faces, dark bands rolling down it;</li>
 *   <li>surges every few tenths of a second: a wave of white running down it, the whole body flaring as it passes;</li>
 *   <li>at the source a flare as wide as the beam; at the impact a mass of energy seven to nine blocks across.</li>
 * </ul>
 * Everything is built from the beam's own numbers each frame, nothing is spawned per tick, and the finer layers drop
 * out with distance.
 */
public final class LoveBeams {
    /**
     * A beam's colours: its main hue, tinted toward white by {@code t} for the hotter layers, the dense body under the
     * light, and the dark bands rolling down it. Every Last Drop is the same square torrent in True Cannon's blue.
     */
    public record Palette(float r, float g, float b, float[] solid, float[] band) {
        public float r(float t) {
            return r + (1 - r) * t;
        }

        public float g(float t) {
            return g + (1 - g) * t;
        }

        public float b(float t) {
            return b + (1 - b) * t;
        }
    }

    public static final Palette PINK = new Palette(1f, 0.4f, 1f, new float[] {0.78f, 0.16f, 0.86f}, new float[] {0.24f, 0.02f, 0.3f});
    public static final Palette BLUE = new Palette(0.04f, 0.3f, 1f, new float[] {0.03f, 0.16f, 0.78f}, new float[] {0.01f, 0.04f, 0.28f});

    private static final Map<Integer, ClientBeam> BEAMS = new HashMap<>();
    private static final Random RNG = new Random();

    private LoveBeams() {}

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

    /** {front, scale} of a beam right now (null once it's over): for effects that follow it. */
    public static double[] state(int key, long now, float partial) {
        ClientBeam b = BEAMS.get(key);
        if (b == null) return null;
        float t = b.age(now + partial, false);
        double front = Math.min(b.front(t), BeamClashClient.reach(key));
        return new double[] {front, b.scale(t)};
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
            draw(c, ps, cam, camRot, e.getKey(), b, t, time, scale, PINK);
        }
    }

    static void draw(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, int key, ClientBeam b, float t, float time, float scale, Palette P) {
        double cap = BeamClashClient.reach(key);
        boolean capped = cap < b.front(t);
        final double front = Math.min(b.front(t), cap);
        float len = (float) front;
        if (len < 0.2f) return;
        Vec3[] fr = TrueLoveBeamProfile.frame(b.dir);
        Vector3f n = v(b.dir), u = v(fr[0]), w = v(fr[1]);
        Vector3f camRel = v(cam.subtract(b.origin));
        // How far the camera is from the beam: the finest layers only up close.
        double along = Mth.clamp(cam.subtract(b.origin).dot(b.dir), 0, front);
        double dist = cam.distanceTo(b.origin.add(b.dir.scale(along)));
        boolean near = dist < 80, mid = dist < 160;
        // Clash pressure: the winning beam blazes brighter and churns harder, the losing one flickers and struggles.
        float p = BeamClashClient.pressure(key);
        float flicker = p < 0 ? 1f + p * 0.35f * (0.5f + 0.5f * Mth.sin(time * 2.7f) * Mth.sin(time * 1.13f)) : 1f;
        // A surge: a wave of energy running from the source to the end over five ticks, everything flaring as it passes.
        float since = time - b.surgeAt;
        float wave = since >= 0 && since < 6 ? since / 5f * len : -100f;
        float boost = since >= 0 && since < 9 ? 1 - since / 9f : 0;
        float bright = (1f + 0.4f * boost + 0.35f * Math.max(0, p)) * flicker;
        float throb = 1f + 0.035f * Mth.sin(t * 1.3f) + 0.06f * boost;
        float fade = Math.min(1f, scale * 1.3f);
        double H = b.half * scale * throb;
        // The swell from her mouth and the blunt nose, from the shared profile (so the drawn square is the hitbox).
        Glow.Radius at = s -> TrueLoveBeamProfile.half(s, front, 1.0) * H;
        int rings = b.quick ? 10 : Math.max(8, Math.min(48, (int) (len / 1.6f)));

        ps.pushPose();
        ps.translate(b.origin.x - cam.x, b.origin.y - cam.y, b.origin.z - cam.z);

        // 1. The main body, filling the square: dense deep magenta (alpha-blended), then the pink light over it with bands
        // of brightness racing forward along it.
        Glow.prism(c, ps, n, u, w, 0, len, s -> at.at(s) * 0.985, s -> 1f, rings, P.solid()[0], P.solid()[1], P.solid()[2], 0.72f * fade, camRel, 0.6f, true);
        Glow.Shade race = s -> bright * (0.72f + 0.2f * Mth.sin((float) s * 0.55f - t * 2.2f) + 0.1f * Mth.sin((float) s * 1.4f - t * 3.9f)
                + bump((float) s, wave, 3f) * 0.9f);
        Glow.prism(c, ps, n, u, w, 0, len, at, race, rings, P.r(0), P.g(0), P.b(0), 0.42f * fade, camRel, 0.35f, false);
        // 2. The hot inner body and the near-white core (2.5 blocks across), churning fast and surging.
        Glow.prism(c, ps, n, u, w, 0, len, s -> at.at(s) * 0.72, race, rings, P.r(0.25f), P.g(0.25f), P.b(0.25f), 0.42f * fade, camRel, 0.5f, false);
        Glow.Shade churn = s -> bright * (0.85f + 0.15f * Mth.sin((float) s * 1.7f - t * 3.4f)) + bump((float) s, wave, 2f);
        Glow.prism(c, ps, n, u, w, 0, len, s -> at.at(s) * (0.5 + 0.03 * Math.sin(s * 2.1 - t * 4.1)), churn, rings, P.r(0.75f), P.g(0.75f), P.b(0.75f), 0.7f * fade, camRel,
                0.7f, false);
        Glow.prism(c, ps, n, u, w, 0, len, s -> at.at(s) * 0.26, churn, rings, 1f, 1f, 1f, 1f * fade, camRel, 0.9f, false);
        // 3. Square sheets across it, moving with the flow: head-on they stack into a solid square of energy.
        float gap = b.quick ? 3f : 2.2f;
        for (float sAt = 0.8f + (t * 1.8f) % gap; sAt < len - 0.3f; sAt += gap) {
            float h = (float) at.at(sAt);
            Glow.slice(c, ps, n, u, w, sAt, h * 0.98f, P.r(0.08f), P.g(0.08f), P.b(0.08f), 0.06f * fade * bright, 3.2f);
        }
        if (!b.quick) {
            // 4. The unstable aura: a looser square that heaves and bulges at its edges, and a faint wide haze.
            Glow.prism(c, ps, n, u, w, 0, len, s -> at.at(s) * (1.13 + 0.07 * Math.sin(s * 0.9 - t * 1.1) + 0.05 * Math.sin(s * 2.7 + t * 2.3)),
                    s -> bright, rings, P.r(0.02f), P.g(0.02f), P.b(0.02f), 0.24f * fade, camRel, 0.05f, false);
            if (mid) {
                Glow.prism(c, ps, n, u, w, 0, len, s -> at.at(s) * (1.36 + 0.16 * Math.sin(s * 0.45 - t * 0.6)), s -> 1f, rings, P.r(0), P.g(0), P.b(0),
                        0.09f * fade, camRel, 0.02f, false);
            }
            // Dark bands rolling down it.
            float band = 6f, off = (t * 2.1f) % band;
            for (float sAt = 2.5f + off; sAt + 1f < len - 1.5f; sAt += band) {
                Glow.prism(c, ps, n, u, w, sAt, sAt + 0.7f, s -> at.at(s) * 1.035, s -> 1f, 1, P.band()[0], P.band()[1], P.band()[2], 0.4f * fade, camRel, 0.4f, true);
            }
            if (near) {
                // Streaks racing along its edges and faces, from the source to the impact.
                for (int k = 0; k < 12; k++) {
                    float s0 = (t * 2.9f + k * 9.7f) % (len + 6f) - 6f;
                    float cu = k % 4 < 2 ? 1 : -1, cv = k % 2 == 0 ? 1 : -1;
                    if (k >= 8) {
                        cu = k == 8 ? 1.05f : k == 9 ? -1.05f : 0;
                        cv = k == 10 ? 1.05f : k == 11 ? -1.05f : (k % 2 == 0 ? 0.3f : -0.3f);
                    }
                    Vector3f[] pts = new Vector3f[5];
                    for (int i = 0; i < 5; i++) {
                        float sAt = Mth.clamp(s0 + i * 1.4f, 0, len);
                        float h = (float) at.at(sAt) * 1.04f;
                        pts[i] = new Vector3f(n).mul(sAt).add(new Vector3f(u).mul(cu * h)).add(new Vector3f(w).mul(cv * h));
                    }
                    Glow.strip(c, ps, pts, 0.13f, P.r(k % 3 == 0 ? 1f : 0.7f), P.g(k % 3 == 0 ? 1f : 0.7f), P.b(k % 3 == 0 ? 1f : 0.7f), 0.75f * fade * bright, camRel);
                }
                // Two arcs spiralling round it on a squared path, turning as it pours forward.
                for (int k = 0; k < 2; k++) {
                    int m = Math.max(8, (int) (len * 1.4f));
                    Vector3f[] pts = new Vector3f[m + 1];
                    for (int i = 0; i <= m; i++) {
                        float sAt = len * i / m;
                        float ang = sAt * 0.5f - t * 0.7f + k * Mth.PI;
                        float cs = Mth.cos(ang), sn = Mth.sin(ang);
                        float h = (float) at.at(sAt) * 1.22f;
                        float qx = Math.signum(cs) * (float) Math.sqrt(Math.abs(cs)), qy = Math.signum(sn) * (float) Math.sqrt(Math.abs(sn));
                        pts[i] = new Vector3f(n).mul(sAt).add(new Vector3f(u).mul(qx * h)).add(new Vector3f(w).mul(qy * h));
                    }
                    Glow.strip(c, ps, pts, 0.17f, P.r(0.77f), P.g(0.77f), P.b(0.77f), 0.6f * fade, camRel);
                }
                // Lightning cracking off its faces, re-forked every tick.
                RNG.setSeed(key * 7919L + (long) t * 31L);
                for (int k = 0; k < 6; k++) {
                    float sAt = RNG.nextFloat() * len;
                    float h = (float) at.at(sAt);
                    float cu = RNG.nextBoolean() ? 1 : -1, cv = (RNG.nextFloat() * 2 - 1);
                    if (RNG.nextBoolean()) {
                        float tmp = cu;
                        cu = cv;
                        cv = tmp;
                    }
                    Vector3f from = new Vector3f(n).mul(sAt).add(new Vector3f(u).mul(cu * h)).add(new Vector3f(w).mul(cv * h));
                    Vector3f out = new Vector3f(u).mul(cu).add(new Vector3f(w).mul(cv)).normalize().mul(1.5f + RNG.nextFloat() * 2.5f)
                            .add(new Vector3f(n).mul((RNG.nextFloat() - 0.3f) * 2f));
                    Glow.bolt(c, ps, from, new Vector3f(from).add(out), 0.07f, P.r(0.67f), P.g(0.67f), P.b(0.67f), 0.9f * fade, RNG.nextLong());
                }
            }
            // 5. The surge itself: a band of white running down it, and a square shock front with it.
            if (wave > -50 && wave < len) {
                float w0 = Math.max(0, wave - 2.5f), w1 = Math.min(len, wave + 1f);
                float a = 0.85f * (1 - since / 6f);
                Glow.prism(c, ps, n, u, w, w0, w1, s -> at.at(s) * 1.16, s -> 1f, 3, P.r(0.87f), P.g(0.87f), P.b(0.87f), a * fade, camRel, 0.3f, false);
                Glow.squareRing(c, ps, n, u, w, wave, (float) at.at(wave) * 1.45f, 0.6f, 0, P.r(0.58f), P.g(0.58f), P.b(0.58f), a * fade);
            }
            // 6. The source: Rika pouring everything into it, a flare as wide as the beam and a turning square frame.
            ps.pushPose();
            ps.translate(n.x * 0.4f, n.y * 0.4f, n.z * 0.4f);
            Glow.halo(c, ps, camRot, (float) (4.6 * scale) * bright, P.r(0.03f), P.g(0.03f), P.b(0.03f), 0.42f * fade);
            Glow.halo(c, ps, camRot, (float) (2.2 * scale) * bright, P.r(0.92f), P.g(0.92f), P.b(0.92f), 0.8f * fade);
            ps.popPose();
            Glow.squareRing(c, ps, n, u, w, 0.3f, (float) (b.half * 1.5 * scale), 0.5f, t * 0.08f, P.r(0.33f), P.g(0.33f), P.b(0.33f), 0.6f * fade);
            Glow.squareRing(c, ps, n, u, w, 0.6f, (float) (b.half * 1.15 * scale), 0.35f, -t * 0.13f, P.r(0.92f), P.g(0.92f), P.b(0.92f), 0.7f * fade);
        }
        // 7. The impact: where it strikes now (unless a clash has it, which draws its own collision).
        if (!capped) {
            double sImp = Mth.clamp(b.strike.subtract(b.origin).dot(b.dir), 1, front);
            impact(c, ps, camRot, n, u, w, (float) sImp, (float) H, t, since, fade * bright, b.quick, near, P);
        }
        ps.popPose();
    }

    /** The impact: a mass of energy 7-9 blocks across, square shock fronts, arcs splashing sideways, every surge harder. */
    private static void impact(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, Vector3f n, Vector3f u, Vector3f w, float s, float H, float t,
                               float since, float a, boolean quick, boolean near, Palette P) {
        float k = quick ? 0.45f : 1f;
        float hit = since >= 0 && since < 7 ? 1 - since / 7f : 0;
        ps.pushPose();
        ps.translate(n.x * s, n.y * s, n.z * s);
        float pulse = 1f + 0.08f * Mth.sin(t * 2.3f) + 0.25f * hit;
        Glow.halo(c, ps, camRot, 4.6f * k * pulse, P.r(0.00f), P.g(0.00f), P.b(0.00f), 0.5f * a);
        Glow.halo(c, ps, camRot, 2.6f * k * pulse, P.r(0.67f), P.g(0.67f), P.b(0.67f), 0.75f * a);
        Glow.halo(c, ps, camRot, 1.3f * k * pulse, 1f, 1f, 1f, 0.95f * a);
        ps.popPose();
        // Square shock fronts round the impact, and one thrown out wide on every surge.
        Glow.squareRing(c, ps, n, u, w, s - 0.2f, H * 1.5f * pulse, 0.6f, t * 0.21f, P.r(0.33f), P.g(0.33f), P.b(0.33f), 0.55f * a);
        Glow.squareRing(c, ps, n, u, w, s - 0.4f, H * 1.85f * pulse, 0.4f, -t * 0.17f, P.r(0.75f), P.g(0.75f), P.b(0.75f), 0.35f * a);
        if (hit > 0) Glow.squareRing(c, ps, n, u, w, s - 0.6f, H * (1.6f + 3.2f * (1 - hit)), 0.9f, 0, P.r(0.83f), P.g(0.83f), P.b(0.83f), 0.7f * hit * a);
        if (!near) return;
        // Energy splashing out sideways off whatever it is boring into.
        RNG.setSeed((long) (t * 1.7f) * 977L + 13);
        for (int i = 0; i < (quick ? 3 : 8); i++) {
            float ang = RNG.nextFloat() * Mth.TWO_PI;
            Vector3f side = new Vector3f(u).mul(Mth.cos(ang)).add(new Vector3f(w).mul(Mth.sin(ang)));
            Vector3f from = new Vector3f(n).mul(s - 0.5f).add(new Vector3f(side).mul(H * 0.6f));
            Vector3f to = new Vector3f(from).add(new Vector3f(side).mul((2.5f + RNG.nextFloat() * 3.5f) * k)).add(new Vector3f(n).mul(-RNG.nextFloat() * 2.5f));
            Glow.bolt(c, ps, from, to, 0.08f, P.r(0.70f), P.g(0.70f), P.b(0.70f), 0.9f * a, RNG.nextLong());
        }
    }

    /** A soft bump of brightness {@code width} wide round {@code at}. */
    private static float bump(float s, float at, float width) {
        float d = (s - at) / width;
        return d * d > 1 ? 0 : (1 - d * d) * (1 - d * d);
    }

    private static Vector3f v(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }
}
