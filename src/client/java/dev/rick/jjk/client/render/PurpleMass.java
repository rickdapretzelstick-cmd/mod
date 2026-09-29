package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hollow Purple's imaginary mass, as JJS draws it: a white-hot, sparkling core inside a ragged ring of dark magenta
 * energy, a hot magenta rim between the two, lightning crackling off it and the light it throws on everything round
 * it. Shared by the charge in Gojo's hands, the mass in flight and Unlimited Purple's fuse.
 */
public final class PurpleMass {
    public static final float[] INK = {0.34f, 0f, 0.32f};
    public static final float[] HOT = {1f, 0.22f, 0.92f};

    private PurpleMass() {}

    /**
     * Draws the mass round the pose's origin.
     * @param core  radius of the white core (the ink reaches about 2.4 times as far)
     * @param bolts lightning arcs crackling off it, each up to {@code reach} times the core's radius long
     */
    public static void draw(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, Vector3f toCam, float core, float t, long seed, float[] ink,
                            float[] hot, int bolts, float reach) {
        Glow.halo(c, ps, camRot, core * 6f, hot[0], hot[1], hot[2], 0.32f);
        Glow.inkRing(c, ps, camRot, core * 1.05f, core * 2.4f, ink[0], ink[1], ink[2], 0.92f, seed, t);
        Glow.inkRing(c, ps, camRot, core * 1.6f, core * 2.9f, ink[0] * 0.6f, ink[1] * 0.6f, ink[2] * 0.6f, 0.5f, seed + 1, -t * 0.7f);
        Glow.sphere(c, ps, core * 1.3f, hot[0], hot[1], hot[2], 0.95f, toCam, true);
        Glow.sphere(c, ps, core, 1f, 0.92f, 1f, 1f, toCam, false);
        Glow.sphere(c, ps, core * 0.6f, 1f, 1f, 1f, 1f, toCam, false);
        if (bolts <= 0) return;
        java.util.Random rnd = new java.util.Random(seed ^ ((long) (t / 2f) * 7919L));
        for (int i = 0; i < bolts; i++) {
            Vector3f dir = new Vector3f(rnd.nextFloat() - 0.5f, (rnd.nextFloat() - 0.5f) * 0.8f, rnd.nextFloat() - 0.5f).normalize();
            Vector3f from = new Vector3f(dir).mul(core * 1.15f);
            Vector3f to = new Vector3f(dir).mul(core * (1.8f + rnd.nextFloat() * (reach - 1.8f)));
            float w = core * (0.12f + 0.08f * rnd.nextFloat());
            if (i % 4 == 0) Glow.bolt(c, ps, from, to, w, 1f, 1f, 1f, 0.9f, rnd.nextLong());
            else Glow.bolt(c, ps, from, to, w, hot[0], hot[1], hot[2], 0.95f, rnd.nextLong());
        }
    }

    public static void draw(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, Vector3f toCam, float core, float t, long seed, int bolts,
                            float reach) {
        draw(c, ps, camRot, toCam, core, t, seed, INK, HOT, bolts, reach);
    }

    public static float pulse(float t) {
        return 1 + 0.06f * Mth.sin(t * 1.7f);
    }
}
