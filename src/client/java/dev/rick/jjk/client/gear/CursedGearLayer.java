package dev.rick.jjk.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.progression.tool.kit.GripProfile;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A holstered cursed tool, on the body: a one-handed blade sheathed at the left hip, edge down and angled back; a dagger
 * across the small of the back; anything two-handed slung across the back from the right shoulder (a long gun muzzle
 * up, a heavy head up). It follows the body as it twists and leans.
 */
public class CursedGearLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    public CursedGearLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector c, int light, S state, float yRot, float xRot) {
        ItemStackRenderState item = state.getData(CursedGear.HOLSTERED);
        GripProfile grip = state.getData(CursedGear.GRIP);
        if (item == null || item.isEmpty() || grip == null || state.isInvisible) return;
        ps.pushPose();
        getParentModel().body.translateAndRotate(ps);
        // The item is drawn as in a frame (its icon plane); the angles below turn its blade, handle-first, where it rides.
        switch (grip.holster) {
            case HIP -> {
                // Along the outside of the left thigh, edge down, the point trailing back.
                ps.translate(5.2f / 16f, 13f / 16f, -1.5f / 16f);
                ps.rotate(Axis.YP.rotationDegrees(-90f));
                ps.rotate(Axis.ZP.rotationDegrees(-15f));
                ps.scale(0.75f, 0.75f, 0.75f);
            }
            case WAIST -> {
                // Across the small of the back.
                ps.translate(0, 11f / 16f, 2.7f / 16f);
                ps.rotate(Axis.ZP.rotationDegrees(45f));
                ps.scale(0.6f, 0.6f, 0.6f);
            }
            case BACK -> {
                // Slung across the back from the left hip up to the right shoulder, the business end up there.
                ps.translate(0, 6f / 16f, 2.9f / 16f);
                ps.rotate(Axis.ZP.rotationDegrees(grip == GripProfile.RANGED ? -60f : 180f));
                ps.scale(0.95f, 0.95f, 0.95f);
            }
        }
        item.submit(ps, c, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        ps.popPose();
    }
}
