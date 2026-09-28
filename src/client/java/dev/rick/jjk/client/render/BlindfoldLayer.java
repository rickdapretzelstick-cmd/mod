package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.anim.PoseKeys;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Gojo's blindfold (a cloth band around the head) while in his base state, and the Six Eyes glowing once it's off.
 * Driven by the BLINDFOLD / AWAKENED statuses, so everyone sees it.
 */
public class BlindfoldLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final RenderType CLOTH = RenderTypes.entityCutout(JJK.id("textures/entity/blindfold.png"));

    public BlindfoldLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector c, int light, S state, float yRot, float xRot) {
        Integer flags = state.getData(PoseKeys.VISUAL);
        if (flags == null || flags == 0 || state.isInvisible) return;
        ps.pushPose();
        getParentModel().head.translateAndRotate(ps);
        if ((flags & PoseKeys.BLINDFOLD) != 0) {
            float off = state.getDataOrDefault(PoseKeys.BLINDFOLD_OFF, 0f);
            // While being pulled off it slides down over the nose.
            ps.translate(0, off * 3.5f / 16f, 0);
            c.submitCustomGeometry(ps, CLOTH, (pose, buf) -> band(pose, buf, light));
        }
        if ((flags & PoseKeys.AWAKENED) != 0) {
            float pulse = 0.75f + 0.25f * Mth.sin(state.ageInTicks * 0.3f);
            c.submitCustomGeometry(ps, Glow.ADDITIVE, (pose, buf) -> {
                eye(pose, buf, -2.5f, pulse);
                eye(pose, buf, 1.5f, pulse);
            });
        }
        ps.popPose();
    }

    /** A cloth band around the head at eye level, slightly larger than the head. */
    private static void band(PoseStack.Pose pose, VertexConsumer buf, int light) {
        float s = 4.35f / 16f, y0 = -5.3f / 16f, y1 = -3.3f / 16f;
        float[][] sides = {{-s, -s, s, -s}, {s, -s, s, s}, {s, s, -s, s}, {-s, s, -s, -s}};
        for (float[] side : sides) {
            float nx = side[1] == side[3] ? 0 : Math.signum(side[0]);
            float nz = side[1] == side[3] ? Math.signum(side[1]) : 0;
            v(buf, pose, side[0], y0, side[1], 0, 1, nx, nz, light);
            v(buf, pose, side[2], y0, side[3], 1, 1, nx, nz, light);
            v(buf, pose, side[2], y1, side[3], 1, 0, nx, nz, light);
            v(buf, pose, side[0], y1, side[1], 0, 0, nx, nz, light);
        }
    }

    private static void v(VertexConsumer buf, PoseStack.Pose pose, float x, float y, float z, float u, float vv, float nx, float nz, int light) {
        buf.addVertex(pose, x, y, z).setColor(-1).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, 0, nz);
    }

    /** A small glowing eye on the face (the face is at z = -4 px). */
    private static void eye(PoseStack.Pose pose, VertexConsumer buf, float x, float pulse) {
        float z = -4.05f / 16f, y0 = -4.6f / 16f, y1 = -3.6f / 16f, x0 = x / 16f, x1 = (x + 1f) / 16f;
        float a = 0.9f * pulse;
        buf.addVertex(pose, x0, y0, z).setColor(0.7f, 0.9f, 1f, a);
        buf.addVertex(pose, x1, y0, z).setColor(0.7f, 0.9f, 1f, a);
        buf.addVertex(pose, x1, y1, z).setColor(0.7f, 0.9f, 1f, a);
        buf.addVertex(pose, x0, y1, z).setColor(0.7f, 0.9f, 1f, a);
    }
}
