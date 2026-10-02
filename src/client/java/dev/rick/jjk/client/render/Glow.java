package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Additive energy geometry (the "lightning" render type: position + color, additive blending). Alpha acts as brightness.
 * All shapes are built around the pose's origin; callers translate to the effect's position first.
 */
public final class Glow {
    public static final RenderType ADDITIVE = RenderTypes.lightning();

    /** Alpha-blended (not additive) position-colour quads: the "ink" copy of a glow, which shows on white. */
    private static final RenderType INK = RenderTypes.debugQuads();
    /** How strongly glows drawn right now get an ink copy where they sit over Idle Death Gamble's white (0 = none). */
    private static float ink;

    private Glow() {}

    /**
     * Light added to the glowing white of Idle Death Gamble has nothing to add to, so it vanishes there (it only read on
     * Infinite Void's side of a clash). While {@code strength} > 0, every glow drawn over that white also lays down a
     * darker, alpha-blended copy of itself in its own hue, so it stays visible. Callers switch it off again with 0.
     */
    public static void ink(float strength) {
        ink = strength;
    }

    private static void submit(SubmitNodeCollector c, PoseStack ps, SubmitNodeCollector.CustomGeometryRenderer geo) {
        c.submitCustomGeometry(ps, ADDITIVE, geo);
        if (ink <= 0) return;
        org.joml.Vector3f t = ps.last().pose().getTranslation(new org.joml.Vector3f());
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
        if (!DomainSpace.overWhite(cam.add(t.x, t.y, t.z))) return;
        float strength = ink;
        c.submitCustomGeometry(ps, INK, (pose, buf) -> geo.render(pose, new Ink(buf, strength)));
    }

    /** Passes vertices through with their colour darkened (hue kept) and their alpha turned into opacity. */
    private record Ink(VertexConsumer out, float strength) implements VertexConsumer {
        private static int dark(int c) {
            return Math.round(c * 0.42f);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(dark(r), dark(g), dark(b), Math.min(215, Math.round(a * 1.25f * strength)));
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            out.setUv3(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            out.setLineWidth(width);
            return this;
        }
    }

    /**
     * Sphere with soft edges: brightness falls off toward the silhouette (as seen from {@code toCamera}),
     * so stacked spheres read as a glowing orb rather than a solid ball.
     * @param rim when true the falloff is inverted: bright rim, clear middle (a shell/distortion look)
     */
    public static void sphere(SubmitNodeCollector c, PoseStack ps, float radius, float r, float g, float b, float alpha, Vector3f toCamera, boolean rim) {
        final int lat = 10, lon = 18;
        float a = alpha * insideFade(ps, radius);
        Vector3f cam = new Vector3f(toCamera).normalize();
        submit(c, ps, (pose, buf) -> {
            for (int i = 0; i < lat; i++) {
                float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
                for (int j = 0; j < lon; j++) {
                    float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                    vert(buf, pose, radius, t0, p0, r, g, b, a, cam, rim);
                    vert(buf, pose, radius, t1, p0, r, g, b, a, cam, rim);
                    vert(buf, pose, radius, t1, p1, r, g, b, a, cam, rim);
                    vert(buf, pose, radius, t0, p1, r, g, b, a, cam, rim);
                }
            }
        });
    }

    private static void vert(VertexConsumer buf, PoseStack.Pose pose, float radius, float theta, float phi, float r, float g, float b, float a,
                             Vector3f cam, boolean rim) {
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        float facing = Math.abs(nx * cam.x + ny * cam.y + nz * cam.z);
        float k = rim ? (1 - facing) * (1 - facing) : facing * facing;
        buf.addVertex(pose, nx * radius, ny * radius, nz * radius).setColor(r, g, b, a * k);
    }

    /**
     * Additive light seen from inside covers the whole screen. Poses are camera-relative, so the translation is the
     * distance to the camera: dim the effect as the camera gets inside it, down to a hint.
     */
    private static float insideFade(PoseStack ps, float radius) {
        org.joml.Vector3f t = ps.last().pose().getTranslation(new org.joml.Vector3f());
        float d = t.length();
        return d >= radius ? 1f : Math.max(0.2f, d / radius);
    }

    /** Camera-facing disc, bright in the middle and fading to nothing at {@code radius}. */
    public static void halo(SubmitNodeCollector c, PoseStack ps, Quaternionf cameraOrientation, float radius, float r, float g, float b, float alpha) {
        float a = alpha * insideFade(ps, radius);
        ps.pushPose();
        ps.rotate(cameraOrientation);
        final int seg = 24;
        submit(c, ps, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0).setColor(r, g, b, 0f);
                buf.addVertex(pose, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
            }
        });
        ps.popPose();
    }

    /** Flat ring band in the XZ plane of the current pose, soft on both edges. */
    public static void ring(SubmitNodeCollector c, PoseStack ps, float radius, float width, float r, float g, float b, float a) {
        final int seg = 40;
        float inner = radius - width / 2, outer = radius + width / 2;
        submit(c, ps, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                // Inner half fades in, outer half fades out.
                buf.addVertex(pose, c0 * inner, 0, s0 * inner).setColor(r, g, b, 0f);
                buf.addVertex(pose, c1 * inner, 0, s1 * inner).setColor(r, g, b, 0f);
                buf.addVertex(pose, c1 * radius, 0, s1 * radius).setColor(r, g, b, a);
                buf.addVertex(pose, c0 * radius, 0, s0 * radius).setColor(r, g, b, a);
                buf.addVertex(pose, c0 * radius, 0, s0 * radius).setColor(r, g, b, a);
                buf.addVertex(pose, c1 * radius, 0, s1 * radius).setColor(r, g, b, a);
                buf.addVertex(pose, c1 * outer, 0, s1 * outer).setColor(r, g, b, 0f);
                buf.addVertex(pose, c0 * outer, 0, s0 * outer).setColor(r, g, b, 0f);
            }
        });
    }

    /**
     * A dark beam (alpha-blended, not additive) from the origin along +Z: solid along its middle, feathered to its edges.
     * Additive light can't draw black; this can (Sukuna's slashes have black cores).
     */
    public static void darkBeam(SubmitNodeCollector c, PoseStack ps, float length, float width, float r, float g, float b, float a) {
        c.submitCustomGeometry(ps, INK, (pose, buf) -> {
            for (int k = 0; k < 2; k++) {
                float wx = k == 0 ? width : 0, wy = k == 0 ? 0 : width;
                // Taper at both ends so a slash comes to a point.
                float cut = Math.min(length * 0.15f, width * 6);
                buf.addVertex(pose, -wx, -wy, cut).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, 0, 0, length).setColor(r, g, b, a);
                buf.addVertex(pose, -wx, -wy, length - cut).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, wx, wy, cut).setColor(r, g, b, 0f);
                buf.addVertex(pose, wx, wy, length - cut).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, length).setColor(r, g, b, a);
            }
        });
    }

    /** A glowing beam from the origin along +Z of the current pose, as two crossed soft quads. */
    public static void beam(SubmitNodeCollector c, PoseStack ps, float length, float width, float r, float g, float b, float a) {
        submit(c, ps, (pose, buf) -> {
            for (int k = 0; k < 2; k++) {
                float wx = k == 0 ? width : 0, wy = k == 0 ? 0 : width;
                buf.addVertex(pose, -wx, -wy, 0).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, 0, 0, length).setColor(r, g, b, 0f);
                buf.addVertex(pose, -wx, -wy, length).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, wx, wy, 0).setColor(r, g, b, 0f);
                buf.addVertex(pose, wx, wy, length).setColor(r, g, b, 0f);
                buf.addVertex(pose, 0, 0, length).setColor(r, g, b, 0f);
            }
        });
    }

    /**
     * Spiral arms in the XZ plane winding from {@code rOuter} into {@code rInner}: the visual signature of pulling inward
     * (Blue) when the phase advances, or of spraying outward (Red) when it runs backwards. Brighter toward the center.
     */
    public static void spiral(SubmitNodeCollector c, PoseStack ps, int arms, float rOuter, float rInner, float turns, float width,
                              float r, float g, float b, float a, float phase) {
        final int steps = 28;
        submit(c, ps, (pose, buf) -> {
            for (int arm = 0; arm < arms; arm++) {
                float base = phase + Mth.TWO_PI * arm / arms;
                for (int i = 0; i < steps; i++) {
                    float t0 = (float) i / steps, t1 = (float) (i + 1) / steps;
                    float r0 = rOuter * (float) Math.pow(rInner / rOuter, t0), r1 = rOuter * (float) Math.pow(rInner / rOuter, t1);
                    float a0 = base + t0 * turns * Mth.TWO_PI, a1 = base + t1 * turns * Mth.TWO_PI;
                    float w0 = width * (1 - t0 * 0.6f), w1 = width * (1 - t1 * 0.6f);
                    float al0 = a * (0.15f + 0.85f * t0), al1 = a * (0.15f + 0.85f * t1);
                    float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                    buf.addVertex(pose, c0 * (r0 - w0), 0, s0 * (r0 - w0)).setColor(r, g, b, 0f);
                    buf.addVertex(pose, c1 * (r1 - w1), 0, s1 * (r1 - w1)).setColor(r, g, b, 0f);
                    buf.addVertex(pose, c1 * r1, 0, s1 * r1).setColor(r, g, b, al1);
                    buf.addVertex(pose, c0 * r0, 0, s0 * r0).setColor(r, g, b, al0);
                    buf.addVertex(pose, c0 * r0, 0, s0 * r0).setColor(r, g, b, al0);
                    buf.addVertex(pose, c1 * r1, 0, s1 * r1).setColor(r, g, b, al1);
                    buf.addVertex(pose, c1 * (r1 + w1), 0, s1 * (r1 + w1)).setColor(r, g, b, 0f);
                    buf.addVertex(pose, c0 * (r0 + w0), 0, s0 * (r0 + w0)).setColor(r, g, b, 0f);
                }
            }
        });
    }

    /** Jagged radial spikes around the origin (unstable, violent energy: Red). {@code seed} changes every tick for flicker. */
    public static void spikes(SubmitNodeCollector c, PoseStack ps, int count, float inner, float outer, float width, float r, float g, float b,
                              float a, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < count; i++) {
            ps.pushPose();
            ps.rotate(com.mojang.math.Axis.YP.rotation(rnd.nextFloat() * Mth.TWO_PI));
            ps.rotate(com.mojang.math.Axis.XP.rotation((rnd.nextFloat() - 0.5f) * Mth.PI));
            ps.translate(0, 0, inner);
            beam(c, ps, (outer - inner) * (0.4f + rnd.nextFloat() * 0.6f), width, r, g, b, a * (0.5f + rnd.nextFloat() * 0.5f));
            ps.popPose();
        }
    }

    // --- Shapes with a direction of their own: ribbons, lightning, swirls, ink ---

    /** Where the camera is in the current pose's local space (poses are camera-relative, so the camera is their origin). */
    static Vector3f cameraLocal(PoseStack ps) {
        return new org.joml.Matrix4f(ps.last().pose()).invert().transformPosition(new Vector3f());
    }

    /**
     * A soft ribbon through {@code pts} (pose-local), turned to face the camera all along its length: bright down its
     * middle, feathered to its edges. Each point has its own half-width and brightness, so a ribbon can taper and fade.
     */
    public static void ribbon(SubmitNodeCollector c, PoseStack ps, Vector3f[] pts, float[] widths, float[] alphas, float r, float g, float b) {
        if (pts.length < 2) return;
        Vector3f cam = cameraLocal(ps);
        Vector3f[] side = new Vector3f[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Vector3f t = new Vector3f(pts[Math.min(pts.length - 1, i + 1)]).sub(pts[Math.max(0, i - 1)]);
            Vector3f v = new Vector3f(cam).sub(pts[i]);
            Vector3f s = t.cross(v, new Vector3f());
            if (s.lengthSquared() < 1e-10f) s.set(0, 1, 0);
            side[i] = s.normalize().mul(widths[i]);
        }
        submit(c, ps, (pose, buf) -> {
            for (int i = 0; i + 1 < pts.length; i++) {
                Vector3f p0 = pts[i], p1 = pts[i + 1], s0 = side[i], s1 = side[i + 1];
                float a0 = alphas[i], a1 = alphas[i + 1];
                buf.addVertex(pose, p0.x - s0.x, p0.y - s0.y, p0.z - s0.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p0.x, p0.y, p0.z).setColor(r, g, b, a0);
                buf.addVertex(pose, p1.x, p1.y, p1.z).setColor(r, g, b, a1);
                buf.addVertex(pose, p1.x - s1.x, p1.y - s1.y, p1.z - s1.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p0.x, p0.y, p0.z).setColor(r, g, b, a0);
                buf.addVertex(pose, p0.x + s0.x, p0.y + s0.y, p0.z + s0.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p1.x + s1.x, p1.y + s1.y, p1.z + s1.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p1.x, p1.y, p1.z).setColor(r, g, b, a1);
            }
        });
    }

    /** Points from {@code a} to {@code b} knocked sideways at random, hardly at the ends and most in the middle. */
    public static Vector3f[] jagged(Vector3f a, Vector3f b, int segments, float jag, java.util.Random rnd) {
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f u = Math.abs(d.y) < 0.9f * d.length() ? d.cross(0, 1, 0, new Vector3f()) : d.cross(1, 0, 0, new Vector3f());
        u.normalize();
        Vector3f w = d.cross(u, new Vector3f()).normalize();
        Vector3f[] pts = new Vector3f[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float amp = jag * Mth.sin(Mth.PI * t) * (0.5f + rnd.nextFloat() * 0.8f);
            float ang = rnd.nextFloat() * Mth.TWO_PI;
            pts[i] = new Vector3f(a).lerp(b, t).add(new Vector3f(u).mul(Mth.cos(ang) * amp)).add(new Vector3f(w).mul(Mth.sin(ang) * amp));
        }
        return pts;
    }

    /**
     * Lightning from {@code a} to {@code b} (pose-local): a jagged coloured channel with a white-hot core and a couple of
     * forks. Pass a {@code seed} that changes every tick or two and it crackles.
     */
    public static void bolt(SubmitNodeCollector c, PoseStack ps, Vector3f a, Vector3f b, float width, float r, float g, float bl, float alpha, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        float len = new Vector3f(b).sub(a).length();
        int segs = Mth.clamp(Math.round(len * 2.2f), 4, 18);
        Vector3f[] main = jagged(a, b, segs, len * 0.16f, rnd);
        channel(c, ps, main, width, r, g, bl, alpha);
        int forks = len > 2 ? 1 + rnd.nextInt(3) : 0;
        for (int f = 0; f < forks; f++) {
            Vector3f from = main[1 + rnd.nextInt(Math.max(1, main.length - 2))];
            Vector3f dir = new Vector3f(b).sub(a).normalize().add(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize();
            Vector3f to = new Vector3f(from).add(dir.mul(len * (0.2f + rnd.nextFloat() * 0.25f)));
            channel(c, ps, jagged(from, to, Math.max(3, segs / 3), len * 0.06f, rnd), width * 0.55f, r, g, bl, alpha * 0.75f);
        }
    }

    private static void channel(SubmitNodeCollector c, PoseStack ps, Vector3f[] pts, float width, float r, float g, float b, float alpha) {
        float[] w = new float[pts.length], a = new float[pts.length], wc = new float[pts.length];
        for (int i = 0; i < pts.length; i++) {
            float t = (float) i / (pts.length - 1);
            float taper = 0.35f + 0.65f * Mth.sin(Mth.PI * Math.min(1f, t * 1.15f + 0.05f));
            w[i] = width * taper;
            wc[i] = width * 0.3f * taper;
            a[i] = alpha;
        }
        ribbon(c, ps, pts, w, a, r, g, b);
        ribbon(c, ps, pts, wc, a, 1f, 1f, 1f);
    }

    /**
     * A comet-tailed arc in the XZ plane of the current pose (tilt the pose to tilt it): its head at {@code head}
     * radians, sweeping back {@code sweep} radians to a faded, thinned tail. Wind and energy whipping round something.
     */
    public static void swirl(SubmitNodeCollector c, PoseStack ps, float radius, float head, float sweep, float width, float r, float g, float b,
                             float alpha, float rise) {
        final int n = 18;
        Vector3f[] pts = new Vector3f[n];
        float[] w = new float[n], a = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            float ang = head - sweep * t;
            float rad = radius * (1 + 0.08f * t);
            pts[i] = new Vector3f(Mth.cos(ang) * rad, -rise * t, Mth.sin(ang) * rad);
            w[i] = width * (1 - 0.75f * t);
            a[i] = alpha * (1 - t) * (1 - t) * Math.min(1f, (1 - t) * 6f);
        }
        ribbon(c, ps, pts, w, a, r, g, b);
    }

    /**
     * A blot of dark, dense energy facing the camera (alpha-blended, so it can be darker than what's behind it): a solid
     * body with a ragged, churning edge. Blue's dark-blue ink, Purple's magenta mass, smoke.
     */
    public static void inkBlob(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, float radius, float r, float g, float b, float alpha,
                               long seed, float time) {
        java.util.Random rnd = new java.util.Random(seed);
        float s1 = rnd.nextFloat() * 10, s2 = rnd.nextFloat() * 10, s3 = rnd.nextFloat() * 10;
        float a = alpha * insideFade(ps, radius);
        ps.pushPose();
        ps.rotate(camRot);
        final int seg = 28;
        float[] edge = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float th = Mth.TWO_PI * i / seg;
            edge[i] = radius * (0.8f + 0.16f * Mth.sin(3 * th + s1 + time * 0.13f) + 0.1f * Mth.sin(5 * th + s2 - time * 0.21f)
                    + 0.06f * Mth.sin(9 * th + s3 + time * 0.37f));
        }
        edge[seg] = edge[0];
        c.submitCustomGeometry(ps, INK, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                float c0 = Mth.cos(a0), n0 = Mth.sin(a0), c1 = Mth.cos(a1), n1 = Mth.sin(a1);
                float i0 = edge[i] * 0.72f, i1 = edge[i + 1] * 0.72f;
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, c0 * i0, n0 * i0, 0).setColor(r, g, b, a * 0.92f);
                buf.addVertex(pose, c1 * i1, n1 * i1, 0).setColor(r, g, b, a * 0.92f);
                buf.addVertex(pose, 0, 0, 0).setColor(r, g, b, a);
                buf.addVertex(pose, c0 * i0, n0 * i0, 0).setColor(r, g, b, a * 0.92f);
                buf.addVertex(pose, c0 * edge[i], n0 * edge[i], 0).setColor(r, g, b, 0f);
                buf.addVertex(pose, c1 * edge[i + 1], n1 * edge[i + 1], 0).setColor(r, g, b, 0f);
                buf.addVertex(pose, c1 * i1, n1 * i1, 0).setColor(r, g, b, a * 0.92f);
            }
        });
        ps.popPose();
    }

    /**
     * A ragged ring of dark energy facing the camera, clear in the middle so whatever burns inside it shows through: the
     * dark rim of Hollow Purple's mass around its white-hot core.
     */
    public static void inkRing(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, float inner, float outer, float r, float g, float b,
                               float alpha, long seed, float time) {
        java.util.Random rnd = new java.util.Random(seed);
        float s1 = rnd.nextFloat() * 10, s2 = rnd.nextFloat() * 10, s3 = rnd.nextFloat() * 10;
        float a = alpha * insideFade(ps, outer);
        ps.pushPose();
        ps.rotate(camRot);
        final int seg = 36;
        float[] out = new float[seg + 1], in = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float th = Mth.TWO_PI * (i % seg) / seg;
            out[i] = outer * (0.84f + 0.1f * Mth.sin(4 * th + s1 + time * 0.11f) + 0.07f * Mth.sin(7 * th + s2 - time * 0.23f)
                    + 0.05f * Mth.sin(13 * th + s3 + time * 0.41f));
            in[i] = inner * (0.95f + 0.05f * Mth.sin(5 * th + s2 + time * 0.3f));
        }
        c.submitCustomGeometry(ps, INK, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                float c0 = Mth.cos(a0), n0 = Mth.sin(a0), c1 = Mth.cos(a1), n1 = Mth.sin(a1);
                float[] r0 = {in[i] * 0.78f, in[i], Mth.lerp(0.72f, in[i], out[i]), out[i]};
                float[] r1 = {in[i + 1] * 0.78f, in[i + 1], Mth.lerp(0.72f, in[i + 1], out[i + 1]), out[i + 1]};
                float[] al = {0f, a, a * 0.9f, 0f};
                for (int k = 0; k < 3; k++) {
                    buf.addVertex(pose, c0 * r0[k], n0 * r0[k], 0).setColor(r, g, b, al[k]);
                    buf.addVertex(pose, c0 * r0[k + 1], n0 * r0[k + 1], 0).setColor(r, g, b, al[k + 1]);
                    buf.addVertex(pose, c1 * r1[k + 1], n1 * r1[k + 1], 0).setColor(r, g, b, al[k + 1]);
                    buf.addVertex(pose, c1 * r1[k], n1 * r1[k], 0).setColor(r, g, b, al[k]);
                }
            }
        });
        ps.popPose();
    }

    /**
     * A shock shell: a sphere lit only at its very silhouette, a crisp bright outline around a faint body (space being
     * shoved outward or dragged in), where {@link #sphere} with {@code rim} is a soft, wide glassy rim.
     */
    public static void shell(SubmitNodeCollector c, PoseStack ps, float radius, float r, float g, float b, float alpha, Vector3f toCamera) {
        final int lat = 16, lon = 32;
        float a = alpha * insideFade(ps, radius);
        Vector3f cam = new Vector3f(toCamera).normalize();
        submit(c, ps, (pose, buf) -> {
            for (int i = 0; i < lat; i++) {
                float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
                for (int j = 0; j < lon; j++) {
                    float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                    shellVert(buf, pose, radius, t0, p0, r, g, b, a, cam);
                    shellVert(buf, pose, radius, t1, p0, r, g, b, a, cam);
                    shellVert(buf, pose, radius, t1, p1, r, g, b, a, cam);
                    shellVert(buf, pose, radius, t0, p1, r, g, b, a, cam);
                }
            }
        });
    }

    private static void shellVert(VertexConsumer buf, PoseStack.Pose pose, float radius, float theta, float phi, float r, float g, float b, float a,
                                  Vector3f cam) {
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        float edge = 1 - Math.abs(nx * cam.x + ny * cam.y + nz * cam.z);
        float e2 = edge * edge, k = e2 * e2 * edge * 1.15f + edge * 0.06f;
        buf.addVertex(pose, nx * radius, ny * radius, nz * radius).setColor(r, g, b, a * k);
    }

    /** {@link #sphere}, alpha-blended instead of added: a dark mass or a dark shell (with {@code rim}) that shows on anything. */
    public static void inkSphere(SubmitNodeCollector c, PoseStack ps, float radius, float r, float g, float b, float alpha, Vector3f toCamera, boolean rim) {
        final int lat = 14, lon = 26;
        float a = alpha * insideFade(ps, radius);
        Vector3f cam = new Vector3f(toCamera).normalize();
        c.submitCustomGeometry(ps, INK, (pose, buf) -> {
            for (int i = 0; i < lat; i++) {
                float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
                for (int j = 0; j < lon; j++) {
                    float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                    vert(buf, pose, radius, t0, p0, r, g, b, a, cam, rim);
                    vert(buf, pose, radius, t1, p0, r, g, b, a, cam, rim);
                    vert(buf, pose, radius, t1, p1, r, g, b, a, cam, rim);
                    vert(buf, pose, radius, t0, p1, r, g, b, a, cam, rim);
                }
            }
        });
    }

    /** Layered energy orb: white-hot core, colored body, soft outer glow, halo. */
    public static void orb(SubmitNodeCollector c, PoseStack ps, Quaternionf camOrientation, Vector3f toCamera, float radius, float[] color,
                           float intensity) {
        sphere(c, ps, radius * 0.45f, 1f, 1f, 1f, 0.9f * intensity, toCamera, false);
        sphere(c, ps, radius, color[0], color[1], color[2], 0.75f * intensity, toCamera, false);
        sphere(c, ps, radius * 1.6f, color[0], color[1], color[2], 0.25f * intensity, toCamera, false);
        halo(c, ps, camOrientation, radius * 3.2f, color[0], color[1], color[2], 0.28f * intensity);
    }

    /** A beam's radius at a distance along it (for {@link #tube}). */
    public interface Radius {
        double at(double s);
    }

    /**
     * A glowing tube from the origin along {@code n} ({@code u}, {@code v} complete the frame), {@code length} long, its
     * radius given along it. Each vertex is as bright as the surface faces the camera ({@code cam}, relative to the
     * origin), so it reads as a volume with soft edges from any side rather than as crossed ribbons.
     */
    public static void tube(SubmitNodeCollector c, PoseStack ps, Vector3f n, Vector3f u, Vector3f v, float length, Radius radius,
                            int rings, int sides, float r, float g, float b, float a, Vector3f cam, float edge) {
        tube(c, ps, n, u, v, length, radius, rings, sides, r, g, b, a, cam, edge, false);
    }

    /**
     * As {@link #tube}; {@code solid} draws it alpha-blended instead of as added light, densest where it faces the camera,
     * so a beam's body reads as a solid mass of energy even against a bright sky (additive light alone washes out there).
     */
    public static void tube(SubmitNodeCollector c, PoseStack ps, Vector3f n, Vector3f u, Vector3f v, float length, Radius radius,
                            int rings, int sides, float r, float g, float b, float a, Vector3f cam, float edge, boolean solid) {
        if (length <= 0.01f || a <= 0.003f) return;
        SubmitNodeCollector.CustomGeometryRenderer geo = (pose, buf) -> {
            float[][] px = new float[rings + 1][], py = new float[rings + 1][], pz = new float[rings + 1][], al = new float[rings + 1][];
            for (int i = 0; i <= rings; i++) {
                float sAt = length * i / rings;
                float rad = (float) radius.at(sAt);
                px[i] = new float[sides]; py[i] = new float[sides]; pz[i] = new float[sides]; al[i] = new float[sides];
                for (int j = 0; j < sides; j++) {
                    float ang = Mth.TWO_PI * j / sides;
                    float cs = Mth.cos(ang), sn = Mth.sin(ang);
                    float nx = u.x * cs + v.x * sn, ny = u.y * cs + v.y * sn, nz = u.z * cs + v.z * sn;
                    float x = n.x * sAt + nx * rad, y = n.y * sAt + ny * rad, z = n.z * sAt + nz * rad;
                    px[i][j] = x; py[i][j] = y; pz[i][j] = z;
                    float tx = cam.x - x, ty = cam.y - y, tz = cam.z - z;
                    float inv = Mth.invSqrt(tx * tx + ty * ty + tz * tz + 1e-6f);
                    float face = Math.abs((nx * tx + ny * ty + nz * tz) * inv);
                    al[i][j] = rad < 0.01f ? 0f : a * (edge + (1 - edge) * (solid ? face : face * face));
                }
            }
            for (int i = 0; i < rings; i++) {
                for (int j = 0; j < sides; j++) {
                    int k = (j + 1) % sides;
                    buf.addVertex(pose, px[i][j], py[i][j], pz[i][j]).setColor(r, g, b, al[i][j]);
                    buf.addVertex(pose, px[i + 1][j], py[i + 1][j], pz[i + 1][j]).setColor(r, g, b, al[i + 1][j]);
                    buf.addVertex(pose, px[i + 1][k], py[i + 1][k], pz[i + 1][k]).setColor(r, g, b, al[i + 1][k]);
                    buf.addVertex(pose, px[i][k], py[i][k], pz[i][k]).setColor(r, g, b, al[i][k]);
                    // Both sides: seen end-on (or from inside its aura) a beam is mostly the far side of its walls.
                    buf.addVertex(pose, px[i][k], py[i][k], pz[i][k]).setColor(r, g, b, al[i][k]);
                    buf.addVertex(pose, px[i + 1][k], py[i + 1][k], pz[i + 1][k]).setColor(r, g, b, al[i + 1][k]);
                    buf.addVertex(pose, px[i + 1][j], py[i + 1][j], pz[i + 1][j]).setColor(r, g, b, al[i + 1][j]);
                    buf.addVertex(pose, px[i][j], py[i][j], pz[i][j]).setColor(r, g, b, al[i][j]);
                }
            }
        };
        if (solid) c.submitCustomGeometry(ps, INK, geo);
        else submit(c, ps, geo);
    }

    /** A dark (alpha-blended) band wrapped round a beam, {@code from} to {@code to} along it at radius {@code rad}. */
    public static void inkBand(SubmitNodeCollector c, PoseStack ps, Vector3f n, Vector3f u, Vector3f v, float from, float to, float rad,
                               int sides, float r, float g, float b, float a) {
        if (rad <= 0.02f || a <= 0.01f) return;
        c.submitCustomGeometry(ps, INK, (pose, buf) -> {
            for (int j = 0; j < sides; j++) {
                float a0 = Mth.TWO_PI * j / sides, a1 = Mth.TWO_PI * (j + 1) / sides;
                float x0 = (u.x * Mth.cos(a0) + v.x * Mth.sin(a0)) * rad, y0 = (u.y * Mth.cos(a0) + v.y * Mth.sin(a0)) * rad,
                        z0 = (u.z * Mth.cos(a0) + v.z * Mth.sin(a0)) * rad;
                float x1 = (u.x * Mth.cos(a1) + v.x * Mth.sin(a1)) * rad, y1 = (u.y * Mth.cos(a1) + v.y * Mth.sin(a1)) * rad,
                        z1 = (u.z * Mth.cos(a1) + v.z * Mth.sin(a1)) * rad;
                buf.addVertex(pose, n.x * from + x0, n.y * from + y0, n.z * from + z0).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * to + x0, n.y * to + y0, n.z * to + z0).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * to + x1, n.y * to + y1, n.z * to + z1).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * from + x1, n.y * from + y1, n.z * from + z1).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * from + x1, n.y * from + y1, n.z * from + z1).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * to + x1, n.y * to + y1, n.z * to + z1).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * to + x0, n.y * to + y0, n.z * to + z0).setColor(r, g, b, a);
                buf.addVertex(pose, n.x * from + x0, n.y * from + y0, n.z * from + z0).setColor(r, g, b, a);
            }
        });
    }

    /** A glowing strip through {@code pts} (relative to the pose), always turned to face {@code cam}: arcs and spirals. */
    public static void strip(SubmitNodeCollector c, PoseStack ps, Vector3f[] pts, float width, float r, float g, float b, float a, Vector3f cam) {
        if (pts.length < 2 || a <= 0.003f) return;
        submit(c, ps, (pose, buf) -> {
            for (int i = 0; i + 1 < pts.length; i++) {
                Vector3f p0 = pts[i], p1 = pts[i + 1];
                Vector3f seg = new Vector3f(p1).sub(p0);
                Vector3f toCam = new Vector3f(cam).sub(p0);
                Vector3f side = seg.cross(toCam, new Vector3f());
                if (side.lengthSquared() < 1e-8f) continue;
                side.normalize(width);
                float a0 = a * (float) Math.sin(Math.PI * i / (pts.length - 1)), a1 = a * (float) Math.sin(Math.PI * (i + 1) / (pts.length - 1));
                buf.addVertex(pose, p0.x - side.x, p0.y - side.y, p0.z - side.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p0.x, p0.y, p0.z).setColor(r, g, b, a0);
                buf.addVertex(pose, p1.x, p1.y, p1.z).setColor(r, g, b, a1);
                buf.addVertex(pose, p1.x - side.x, p1.y - side.y, p1.z - side.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p0.x, p0.y, p0.z).setColor(r, g, b, a0);
                buf.addVertex(pose, p0.x + side.x, p0.y + side.y, p0.z + side.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p1.x + side.x, p1.y + side.y, p1.z + side.z).setColor(r, g, b, 0f);
                buf.addVertex(pose, p1.x, p1.y, p1.z).setColor(r, g, b, a1);
            }
        });
    }
}
