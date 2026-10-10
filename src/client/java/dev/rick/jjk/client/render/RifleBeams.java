package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.clash.BeamClashClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The Cursed Rifle's beam drawn: the same square torrent as True Love Beam and Every Last Drop (the same layers, surges,
 * streaks and impact, from the same shared profile the server hits with), sized by the server's half-width and coloured
 * by its output: orange outside, white inside. The first unlock is narrower and dimmer; Maximum Output blazes as wide as
 * the others. Capped where a clash holds it.
 */
public final class RifleBeams {
    private static final Map<Integer, ClientBeam> BEAMS = new HashMap<>();
    private static final Map<Integer, Float> OUTPUT = new HashMap<>();

    // Orange outside, white-hot inside (the core layers always run to white): the first unlock a deeper, dimmer ember,
    // Maximum Output a blazing orange.
    private static final LoveBeams.Palette BASE = new LoveBeams.Palette(0.95f, 0.38f, 0.06f, new float[] {0.7f, 0.2f, 0.02f}, new float[] {0.22f, 0.05f, 0f});
    private static final LoveBeams.Palette MAX = new LoveBeams.Palette(1f, 0.55f, 0.1f, new float[] {0.95f, 0.36f, 0.03f}, new float[] {0.32f, 0.09f, 0f});

    private RifleBeams() {}

    /** The palette for a beam of this output (0.45 the first unlock .. 1 Maximum Output). */
    public static LoveBeams.Palette palette(float output) {
        float k = Mth.clamp((output - 0.45f) / 0.55f, 0f, 1f);
        return new LoveBeams.Palette(Mth.lerp(k, BASE.r(), MAX.r()), Mth.lerp(k, BASE.g(), MAX.g()), Mth.lerp(k, BASE.b(), MAX.b()),
                lerp(k, BASE.solid(), MAX.solid()), lerp(k, BASE.band(), MAX.band()));
    }

    /** A beam's main colour (clash visuals, HUD). */
    public static float[] colour(float output) {
        LoveBeams.Palette p = palette(output);
        return new float[] {p.r(), p.g(), p.b()};
    }

    private static float[] lerp(float k, float[] a, float[] b) {
        return new float[] {Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
    }

    public static void start(int key, ClientBeam b, float output) {
        BEAMS.put(key, b);
        OUTPUT.put(key, output);
    }

    public static ClientBeam get(int key) {
        return BEAMS.get(key);
    }

    public static float output(int key) {
        return OUTPUT.getOrDefault(key, 0.45f);
    }

    public static void stop(int key, long now) {
        ClientBeam b = BEAMS.get(key);
        if (b != null) b.stop(now);
    }

    public static void clear() {
        BEAMS.clear();
        OUTPUT.clear();
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
                OUTPUT.remove(e.getKey());
                continue;
            }
            if (t < 0) continue;
            float scale = b.scale(t);
            if (scale <= 0.01f) continue;
            LoveBeams.draw(c, ps, cam, camRot, e.getKey(), b, t, time, scale, palette(output(e.getKey())));
        }
    }
}
