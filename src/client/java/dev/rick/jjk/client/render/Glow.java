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

    private Glow() {}

    /**
     * Sphere with soft edges: brightness falls off toward the silhouette (as seen from {@code toCamera}),
     * so stacked spheres read as a glowing orb rather than a solid ball.
     * @param rim when true the falloff is inverted: bright rim, clear middle (a shell/distortion look)
     */
    public static void sphere(SubmitNodeCollector c, PoseStack ps, float radius, float r, float g, float b, float a, Vector3f toCamera, boolean rim) {
        final int lat = 10, lon = 18;
        Vector3f cam = new Vector3f(toCamera).normalize();
        c.submitCustomGeometry(ps, ADDITIVE, (pose, buf) -> {
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

    /** Camera-facing disc, bright in the middle and fading to nothing at {@code radius}. */
    public static void halo(SubmitNodeCollector c, PoseStack ps, Quaternionf cameraOrientation, float radius, float r, float g, float b, float a) {
        ps.pushPose();
        ps.mulPose(cameraOrientation);
        final int seg = 24;
        c.submitCustomGeometry(ps, ADDITIVE, (pose, buf) -> {
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
        c.submitCustomGeometry(ps, ADDITIVE, (pose, buf) -> {
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

    /** A glowing beam from the origin along +Z of the current pose, as two crossed soft quads. */
    public static void beam(SubmitNodeCollector c, PoseStack ps, float length, float width, float r, float g, float b, float a) {
        c.submitCustomGeometry(ps, ADDITIVE, (pose, buf) -> {
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

    /** Layered energy orb: white-hot core, colored body, soft outer glow, halo. */
    public static void orb(SubmitNodeCollector c, PoseStack ps, Quaternionf camOrientation, Vector3f toCamera, float radius, float[] color,
                           float intensity) {
        sphere(c, ps, radius * 0.45f, 1f, 1f, 1f, 0.9f * intensity, toCamera, false);
        sphere(c, ps, radius, color[0], color[1], color[2], 0.75f * intensity, toCamera, false);
        sphere(c, ps, radius * 1.6f, color[0], color[1], color[2], 0.25f * intensity, toCamera, false);
        halo(c, ps, camOrientation, radius * 3.2f, color[0], color[1], color[2], 0.28f * intensity);
    }
}
