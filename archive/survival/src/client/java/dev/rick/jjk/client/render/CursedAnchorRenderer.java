package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.rick.jjk.progression.investigation.CursedAnchorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;

/**
 * A cursed anchor, drawn: a black obelisk floating a little above the ground and turning slowly, crimson veins pulsing in
 * it, a smaller block of it orbiting its crown. While guarded, a translucent shell of cursed energy turns round it;
 * when the shell drops, the veins burn brighter (it can be broken now). It flashes when struck.
 */
public class CursedAnchorRenderer extends EntityRenderer<CursedAnchorEntity, CursedAnchorRenderer.State> {
    private static final net.minecraft.client.renderer.rendertype.RenderType SOLID = net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads();

    public static class State extends EntityRenderState {
        float age, hurt;
        boolean guarded;
    }

    public CursedAnchorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CursedAnchorEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.age = e.tickCount + partial;
        s.guarded = e.guarded();
        s.hurt = e.hurtTime > 0 ? (e.hurtTime - partial) / 10f : 0f;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        float t = s.age;
        float bob = Mth.sin(t * 0.06f) * 0.06f;
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.15f);
        ps.pushPose();
        ps.translate(0, 0.35f + bob, 0);
        ps.rotate(Axis.YP.rotationDegrees(t * 0.8f));
        float flash = s.hurt;
        // The obelisk: a tall tapering block of black.
        c.submitCustomGeometry(ps, SOLID, (pose, buf) -> {
            float r = 0.05f + 0.6f * flash, g = 0.0f + 0.1f * flash, b = 0.04f + 0.1f * flash;
            prism(pose, buf, 0.42f, 0.3f, 0f, 1.9f, r, g, b, 1f);
            prism(pose, buf, 0.3f, 0.02f, 1.9f, 2.25f, r, g, b, 1f);
        });
        // Veins: thin glowing strips on each face (brighter once unguarded).
        float va = (s.guarded ? 0.35f : 0.85f) * (0.6f + 0.4f * pulse);
        c.submitCustomGeometry(ps, Glow.ADDITIVE, (pose, buf) -> {
            for (int k = 0; k < 4; k++) {
                PoseStack.Pose p2 = pose.copy();
                p2.rotate(Axis.YP.rotationDegrees(90f * k));
                for (int v = 0; v < 3; v++) {
                    float y0 = 0.2f + v * 0.55f, y1 = y0 + 0.4f;
                    float x = -0.12f + v * 0.09f;
                    quad(p2, buf, x, y0, 0.43f, x + 0.05f, y1, 0.43f, 0.85f, 0.1f, 0.22f, va);
                }
            }
        });
        // The orbiting shard at its crown.
        ps.pushPose();
        ps.rotate(Axis.YP.rotationDegrees(t * 3f));
        ps.translate(0.75f, 2.3f + Mth.sin(t * 0.1f) * 0.1f, 0);
        c.submitCustomGeometry(ps, SOLID, (pose, buf) -> prism(pose, buf, 0.12f, 0.12f, -0.12f, 0.12f, 0.08f, 0.0f, 0.05f, 1f));
        ps.popPose();
        // The guard shell.
        if (s.guarded) {
            c.submitCustomGeometry(ps, Glow.ADDITIVE, (pose, buf) -> {
                int seg = 24;
                for (int i = 0; i < seg; i++) {
                    float a0 = Mth.TWO_PI * i / seg + t * 0.02f, a1 = Mth.TWO_PI * (i + 1) / seg + t * 0.02f;
                    float rr = 0.9f;
                    float alpha = 0.08f + 0.06f * pulse;
                    buf.addVertex(pose, Mth.cos(a0) * rr, -0.1f, Mth.sin(a0) * rr).setColor(0.55f, 0.15f, 0.7f, alpha);
                    buf.addVertex(pose, Mth.cos(a0) * rr, 2.5f, Mth.sin(a0) * rr).setColor(0.55f, 0.15f, 0.7f, alpha * 0.3f);
                    buf.addVertex(pose, Mth.cos(a1) * rr, 2.5f, Mth.sin(a1) * rr).setColor(0.55f, 0.15f, 0.7f, alpha * 0.3f);
                    buf.addVertex(pose, Mth.cos(a1) * rr, -0.1f, Mth.sin(a1) * rr).setColor(0.55f, 0.15f, 0.7f, alpha);
                }
            });
        }
        ps.popPose();
        super.submit(s, ps, c, cam);
    }

    /** A square prism from {@code y0} to {@code y1}, half-width {@code w0} at the bottom tapering to {@code w1}. */
    private static void prism(PoseStack.Pose p, VertexConsumer buf, float w0, float w1, float y0, float y1, float r, float g, float b, float a) {
        float[][] bot = {{-w0, -w0}, {w0, -w0}, {w0, w0}, {-w0, w0}};
        float[][] top = {{-w1, -w1}, {w1, -w1}, {w1, w1}, {-w1, w1}};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float shade = 0.75f + 0.25f * (i % 2);
            buf.addVertex(p, bot[i][0], y0, bot[i][1]).setColor(r * shade, g * shade, b * shade, a);
            buf.addVertex(p, top[i][0], y1, top[i][1]).setColor(r * shade, g * shade, b * shade, a);
            buf.addVertex(p, top[j][0], y1, top[j][1]).setColor(r * shade, g * shade, b * shade, a);
            buf.addVertex(p, bot[j][0], y0, bot[j][1]).setColor(r * shade, g * shade, b * shade, a);
        }
        for (float[][] cap : new float[][][] {top, bot}) {
            float y = cap == top ? y1 : y0;
            buf.addVertex(p, cap[0][0], y, cap[0][1]).setColor(r, g, b, a);
            buf.addVertex(p, cap[1][0], y, cap[1][1]).setColor(r, g, b, a);
            buf.addVertex(p, cap[2][0], y, cap[2][1]).setColor(r, g, b, a);
            buf.addVertex(p, cap[3][0], y, cap[3][1]).setColor(r, g, b, a);
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer buf, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
        buf.addVertex(p, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(p, x1, y0, z1).setColor(r, g, b, a);
        buf.addVertex(p, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(p, x0, y1, z0).setColor(r, g, b, a);
    }
}
