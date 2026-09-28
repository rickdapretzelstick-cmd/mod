package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rick.jjk.client.ClientState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The space a domain's interior is drawn in. Normally its own sphere; during a clash, only its side of the split (the
 * boundary plane between the two centers, where the server's blocks change material too), reaching into the rival's
 * sphere as it wins ground; after winning, its sphere plus the territory it conquered. Where two spheres overlap their
 * walls are open (the server merges the space), so a wall is drawn only where no other sphere of the space covers it.
 */
final class DomainSpace {
    /** A sphere of the space: center and radius. */
    record Ball(Vec3 center, float radius) {}

    private DomainSpace() {}

    @Nullable
    static ClientState.Domain rival(ClientState.Domain d) {
        return d.splitWith >= 0 ? ClientState.DOMAINS.get(d.splitWith) : null;
    }

    /** Eases the drawn boundary toward the server's (call once per frame). */
    static void ease(ClientState.Domain d) {
        if (d.splitWith >= 0) d.splitShown += (d.split - d.splitShown) * 0.25f;
    }

    /** Consumed by the rival: nothing of this domain is left to draw. */
    static boolean consumed(ClientState.Domain d) {
        return d.splitWith >= 0 && d.split == dev.rick.jjk.core.net.DomainPayload.CONSUMED;
    }

    static boolean split(ClientState.Domain d) {
        return rival(d) != null;
    }

    /** Whether a world point is on this domain's side of the boundary. */
    static boolean onSide(ClientState.Domain d, Vec3 w) {
        ClientState.Domain o = rival(d);
        if (o == null) return true;
        Vec3 ab = o.center.subtract(d.center);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-4) return true;
        return w.subtract(d.center).dot(ab) / len2 < d.splitShown;
    }

    /** Every sphere making up this domain's space right now (its own first). */
    static List<Ball> balls(ClientState.Domain d) {
        List<Ball> out = new ArrayList<>();
        out.add(new Ball(d.center, d.radius));
        ClientState.Domain o = rival(d);
        if (o != null && o.center != null) out.add(new Ball(o.center, o.radius));
        for (int i = 0; i + 3 < d.annex.length; i += 4) out.add(new Ball(new Vec3(d.annex[i], d.annex[i + 1], d.annex[i + 2]), d.annex[i + 3]));
        return out;
    }

    /** Whether the camera is somewhere in this domain's space (any of its spheres). */
    static boolean holds(ClientState.Domain d, Vec3 w) {
        for (Ball b : balls(d)) if (w.distanceTo(b.center()) < b.radius()) return true;
        return false;
    }

    /**
     * Sphere {@code k} of the space as a starfield-style shell (drawn around its center, pose at its center), keeping only
     * the pieces on this domain's side that no other sphere of the space covers.
     */
    static void shell(PoseStack.Pose pose, VertexConsumer buf, ClientState.Domain d, List<Ball> balls, int k, float r, boolean inward) {
        Ball self = balls.get(k);
        final int lat = 32, lon = 56;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                float tm = (t0 + t1) / 2, pm = (p0 + p1) / 2;
                Vec3 w = self.center().add(r * Mth.sin(tm) * Mth.cos(pm), r * Mth.cos(tm), r * Mth.sin(tm) * Mth.sin(pm));
                if (!keep(d, balls, k, w)) continue;
                float[][] v = {pt(r, t0, p0), pt(r, t1, p0), pt(r, t1, p1), pt(r, t0, p1)};
                if (inward) {
                    for (int q = 3; q >= 0; q--) buf.addVertex(pose, v[q][0], v[q][1], v[q][2]);
                } else {
                    for (int q = 0; q < 4; q++) buf.addVertex(pose, v[q][0], v[q][1], v[q][2]);
                }
            }
        }
    }

    private static boolean keep(ClientState.Domain d, List<Ball> balls, int k, Vec3 w) {
        if (!onSide(d, w)) return false;
        for (int j = 0; j < balls.size(); j++) {
            if (j != k && w.distanceTo(balls.get(j).center()) < balls.get(j).radius()) return false;
        }
        return true;
    }

    private static float[] pt(float r, float theta, float phi) {
        return new float[]{r * Mth.sin(theta) * Mth.cos(phi), r * Mth.cos(theta), r * Mth.sin(theta) * Mth.sin(phi)};
    }
}
