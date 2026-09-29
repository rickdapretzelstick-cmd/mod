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

    /** Layered energy orb: white-hot core, colored body, soft outer glow, halo. */
    public static void orb(SubmitNodeCollector c, PoseStack ps, Quaternionf camOrientation, Vector3f toCamera, float radius, float[] color,
                           float intensity) {
        sphere(c, ps, radius * 0.45f, 1f, 1f, 1f, 0.9f * intensity, toCamera, false);
        sphere(c, ps, radius, color[0], color[1], color[2], 0.75f * intensity, toCamera, false);
        sphere(c, ps, radius * 1.6f, color[0], color[1], color[2], 0.25f * intensity, toCamera, false);
        halo(c, ps, camOrientation, radius * 3.2f, color[0], color[1], color[2], 0.28f * intensity);
    }
}
