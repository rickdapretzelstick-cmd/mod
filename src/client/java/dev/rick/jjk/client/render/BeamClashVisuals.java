package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.clash.BeamClashClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Where two beams meet in a clash: a churning mass of both energies, each beam's colour on its own side of it and a
 * white-hot heart between, lightning thrown off in every direction, rings shoved out along the line by every push and
 * trembling harder as it swings toward someone's source. Everyone near sees it; it is the world's side of the clash
 * (the contestants' dials are private).
 */
public final class BeamClashVisuals {
    private static final Random RNG = new Random();

    private BeamClashVisuals() {}

    static float[] color(String kind) {
        if ("rifle".equals(kind)) return new float[] {1f, 0.55f, 0.12f};
        return "eld".equals(kind) ? new float[] {0.35f, 0.7f, 1f} : new float[] {1f, 0.42f, 1f};
    }

    public static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
        for (BeamClashClient.View v : BeamClashClient.views()) {
            if (v.phase == BeamClashClient.ENDED || v.broken()) continue;
            if (v.phase == BeamClashClient.COUNTER && !anyOut(v)) continue;
            draw(c, ps, cam, camRot, v, now + partial);
        }
    }

    private static boolean anyOut(BeamClashClient.View v) {
        return LoveBeams.get(v.aId) != null || RyuBeams.get(v.aId) != null || RifleBeams.get(v.aId) != null
                || LoveBeams.get(v.bId) != null || RyuBeams.get(v.bId) != null || RifleBeams.get(v.bId) != null;
    }

    private static void draw(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, BeamClashClient.View v, float time) {
        Vec3 at = v.point();
        if (at.distanceTo(cam) > 200) return;
        Vec3 axis = v.bOrigin.subtract(v.aOrigin);
        if (axis.lengthSqr() < 1e-4) return;
        axis = axis.normalize();
        float[] ca = color(v.aKind), cb = color(v.bKind);
        boolean counter = v.phase == BeamClashClient.COUNTER;
        boolean tie = v.phase == BeamClashClient.RESOLVE && v.outcome == BeamClashClient.TIE;
        float heat = counter ? 0.5f : Math.max(0.6f, v.intensity);
        // It trembles harder the further it is shoved toward either source.
        float strain = Math.abs(v.shown - 0.5f) / 0.4f;
        float size = (counter ? 1.8f : 2.6f + 0.9f * heat) * (tie ? 1f + v.phaseAge * 0.05f : 1f);
        float shake = 0.06f + 0.18f * strain + (v.phase == BeamClashClient.RESOLVE ? 0.25f : 0);
        Vector3f n = new Vector3f((float) axis.x, (float) axis.y, (float) axis.z);
        Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f w = new Vector3f(n).cross(u).normalize();
        RNG.setSeed((long) (time * 0.5f) * 7349L + v.session);
        Vec3 jit = new Vec3((RNG.nextFloat() - 0.5f) * shake, (RNG.nextFloat() - 0.5f) * shake, (RNG.nextFloat() - 0.5f) * shake);
        ps.pushPose();
        ps.translate(at.x + jit.x - cam.x, at.y + jit.y - cam.y, at.z + jit.z - cam.z);
        float throb = 1f + 0.08f * Mth.sin(time * 2.1f) + 0.12f * Math.abs(v.push);
        // Each beam's colour bulging out on its own side of the mass, the white heart between.
        float off = size * 0.32f;
        ps.pushPose();
        ps.translate(-n.x * off, -n.y * off, -n.z * off);
        // (Kept in check up close: it is a mass of light, not a white-out of the screen.)
        float near = (float) Mth.clamp(at.distanceTo(cam) / 14.0, 0.45, 1.0);
        Glow.halo(c, ps, camRot, size * 1.15f * throb, ca[0], ca[1], ca[2], 0.42f * near);
        Glow.halo(c, ps, camRot, size * 0.65f * throb, ca[0], ca[1], ca[2], 0.55f * near);
        ps.popPose();
        ps.pushPose();
        ps.translate(n.x * off, n.y * off, n.z * off);
        Glow.halo(c, ps, camRot, size * 1.15f * throb, cb[0], cb[1], cb[2], 0.42f * near);
        Glow.halo(c, ps, camRot, size * 0.65f * throb, cb[0], cb[1], cb[2], 0.55f * near);
        ps.popPose();
        Glow.halo(c, ps, camRot, size * 0.55f * throb, 1f, 1f, 1f, 0.85f * near);
        Glow.halo(c, ps, camRot, size * 1.8f * throb, 1f, 0.9f, 1f, 0.1f * near);
        Vector3f toCam = new Vector3f((float) (cam.x - at.x), (float) (cam.y - at.y), (float) (cam.z - at.z));
        Glow.sphere(c, ps, size * 0.32f * throb, 1f, 1f, 1f, 0.7f, toCam, false);
        // Rings round the line, turning, and one thrown outward on every push.
        ps.pushPose();
        Flashes.orientY(ps, axis);
        for (int k = 0; k < 3; k++) {
            float r = size * (0.9f + 0.35f * k) * (1f + 0.1f * Mth.sin(time * (1.3f + k * 0.4f) + k));
            float[] col = k == 1 ? new float[] {1f, 1f, 1f} : k == 0 ? ca : cb;
            Glow.ring(c, ps, r, 0.25f, col[0], col[1], col[2], 0.55f);
        }
        float pushed = Math.abs(v.push);
        if (pushed > 0.05f) Glow.ring(c, ps, size * (2.8f - 1.6f * pushed), 0.5f, 1f, 1f, 1f, 0.6f * pushed);
        ps.popPose();
        // Lightning cracking off the collision in every direction, re-forked every tick.
        int bolts = counter ? 4 : 7 + Math.round(heat * 3);
        RNG.setSeed((long) time * 911L + v.session * 31L);
        for (int i = 0; i < bolts; i++) {
            float ang = RNG.nextFloat() * Mth.TWO_PI;
            Vector3f side = new Vector3f(u).mul(Mth.cos(ang)).add(new Vector3f(w).mul(Mth.sin(ang)));
            Vector3f from = new Vector3f(side).mul(size * 0.4f);
            float out = size * (1.2f + RNG.nextFloat() * 1.8f);
            Vector3f to = new Vector3f(side).mul(out).add(new Vector3f(n).mul((RNG.nextFloat() - 0.5f) * size * 1.5f));
            float[] col = RNG.nextInt(3) == 0 ? new float[] {1f, 1f, 1f} : RNG.nextBoolean() ? ca : cb;
            Glow.bolt(c, ps, from, to, 0.07f + 0.03f * heat, col[0], col[1], col[2], 0.9f, RNG.nextLong());
        }
        ps.popPose();
    }
}
