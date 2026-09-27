package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived flashes and shockwave rings, drawn as additive geometry (large particles don't blend well).
 * Flashes shrink and fade; rings expand and fade, always facing the camera.
 */
public final class Flashes {
    private static final List<Flash> ACTIVE = new ArrayList<>();
    private static final int MAX = 96;

    private record Flash(Vec3 pos, float[] color, float from, float to, float alpha, int life, boolean ring, long start) {}

    private Flashes() {}

    public static void flash(Vec3 pos, float size, float[] color, int life, long now) {
        add(new Flash(pos, color, size, size * 0.25f, 0.9f, life, false, now));
    }

    public static void ring(Vec3 pos, float from, float to, float[] color, float alpha, int life, long now) {
        add(new Flash(pos, color, from, to, alpha, life, true, now));
    }

    private static void add(Flash f) {
        if (ACTIVE.size() >= MAX) ACTIVE.removeFirst();
        ACTIVE.add(f);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
        Iterator<Flash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Flash f = it.next();
            float t = (now - f.start + partial) / f.life;
            if (t >= 1f) {
                it.remove();
                continue;
            }
            float ease = 1 - (1 - t) * (1 - t);
            float size = Mth.lerp(ease, f.from, f.to);
            float a = f.alpha * (1 - t) * (f.ring ? 1 : (1 - t));
            ps.pushPose();
            ps.translate(f.pos.x - cam.x, f.pos.y - cam.y, f.pos.z - cam.z);
            if (f.ring) {
                ps.rotate(camRot);
                ps.rotate(Axis.XP.rotationDegrees(90));
                Glow.ring(c, ps, size, Math.max(0.15f, size * 0.18f), f.color[0], f.color[1], f.color[2], a);
            } else {
                Glow.halo(c, ps, camRot, size, f.color[0], f.color[1], f.color[2], a);
                Glow.halo(c, ps, camRot, size * 0.35f, 1f, 1f, 1f, a);
            }
            ps.popPose();
        }
    }
}
