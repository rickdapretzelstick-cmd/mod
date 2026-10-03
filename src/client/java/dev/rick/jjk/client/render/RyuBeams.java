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
 * Every Last Drop drawn: True Cannon's whole reserve fired from his fingertip as the same five-by-five square torrent as
 * True Love Beam (the same layers, surges and impact, from the same shared profile the server hits with), in his cold
 * blue with a white-hot core. Capped where a clash holds it.
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
            LoveBeams.draw(c, ps, cam, camRot, e.getKey(), b, t, time, scale, LoveBeams.BLUE);
        }
    }

    private static Vector3f v(Vec3 d) {
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }
}
