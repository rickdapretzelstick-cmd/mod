package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.anim.PoseKeys;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

import static dev.rick.jjk.client.render.YutaGear.*;

/**
 * Swordsmanship's cosmetics (JJS): the katana's sheath at his left hip and the katana in it, or drawn in his right hand
 * while he fights with it; the necklace with the cursed ring on his chest; and in True Love, the steel casing Rika wraps
 * around his right arm (the ring is on his finger then, the necklace gone). Driven by the synced statuses, so everyone
 * sees them.
 */
public class YutaGearLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    public YutaGearLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector c, int light, S state, float yRot, float xRot) {
        Integer flags = state.getData(PoseKeys.VISUAL);
        if (flags == null || (flags & PoseKeys.CURSED_PARTNERS) == 0 || state.isInvisible) return;
        boolean drawn = (flags & PoseKeys.KATANA) != 0;
        boolean steel = (flags & PoseKeys.STEEL_ARM) != 0;
        M model = getParentModel();

        // The sheath at the left hip, slanting back and down; the katana sits in it unless drawn.
        ps.pushPose();
        model.body.translateAndRotate(ps);
        ps.translate(4.6f / 16f, 10.5f / 16f, -2.5f / 16f);
        ps.rotate(Axis.XP.rotationDegrees(-158f));
        ps.rotate(Axis.YP.rotationDegrees(8f));
        c.submitCustomGeometry(ps, TYPE, (pose, buf) -> {
            sheath(pose, buf, light);
            if (!drawn) {
                // Only the handle and guard show above the mouth.
                PoseStack.Pose p2 = pose.copy();
                p2.rotate(Axis.YP.rotationDegrees(180f));
                katana(p2, buf, light, false);
            }
        });
        ps.popPose();

        if (!steel) {
            // The necklace and Rika's ring on his chest.
            ps.pushPose();
            model.body.translateAndRotate(ps);
            c.submitCustomGeometry(ps, TYPE, (pose, buf) -> {
                box(pose, buf, -2.6f, 0.2f, -2.15f, -2.1f, 3.2f, -2.05f, CHAIN, light);
                box(pose, buf, 2.1f, 0.2f, -2.15f, 2.6f, 3.2f, -2.05f, CHAIN, light);
                box(pose, buf, -2.1f, 3.0f, -2.15f, 2.1f, 3.4f, -2.05f, CHAIN, light);
                box(pose, buf, -0.8f, 3.4f, -2.4f, 0.8f, 5.0f, -2.1f, RING, light);
                box(pose, buf, -0.35f, 3.85f, -2.45f, 0.35f, 4.55f, -2.05f, BLACK, light);
            });
            ps.popPose();
        }

        if (drawn) {
            ps.pushPose();
            model.translateToHand(state, HumanoidArm.RIGHT, ps);
            // In the fist, pointing forward from the hand like a held item.
            ps.translate(-1f / 16f, 9.5f / 16f, -0.5f / 16f);
            ps.rotate(Axis.XP.rotationDegrees(90f));
            c.submitCustomGeometry(ps, TYPE, (pose, buf) -> katana(pose, buf, light, true));
            ps.popPose();
        }

        if (steel) {
            // True Love's steel casing over the right arm, banded, with the ring glinting on the hand.
            ps.pushPose();
            model.rightArm.translateAndRotate(ps);
            c.submitCustomGeometry(ps, TYPE, (pose, buf) -> {
                box(pose, buf, -3.45f, -2.3f, -2.45f, 1.45f, 4.0f, 2.45f, CASING, light);
                box(pose, buf, -3.55f, 1.0f, -2.55f, 1.55f, 1.8f, 2.55f, CASING_DARK, light);
                box(pose, buf, -3.55f, -2.4f, -2.55f, 1.55f, -1.6f, 2.55f, CASING_LIGHT, light);
            });
            ps.popPose();
            ps.pushPose();
            model.translateToHand(state, HumanoidArm.RIGHT, ps);
            c.submitCustomGeometry(ps, TYPE, (pose, buf) -> {
                box(pose, buf, -3.4f, 4.0f, -2.4f, 1.4f, 10.4f, 2.4f, CASING, light);
                box(pose, buf, -3.5f, 6.5f, -2.5f, 1.5f, 7.3f, 2.5f, CASING_DARK, light);
                box(pose, buf, -3.5f, 9.3f, -2.5f, 1.5f, 10.1f, 2.5f, CASING_DARK, light);
                for (int i = 0; i < 3; i++) box(pose, buf, -1.6f + i * 0.0f, 4.6f + i * 1.8f, -2.6f, -0.8f, 5.2f + i * 1.8f, -2.45f, RIVET, light);
            });
            ps.popPose();
        }
    }
}
