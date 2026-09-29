package dev.rick.jjk.client.render;

import dev.rick.jjk.JJK;
import dev.rick.jjk.entity.TrainingDummy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Training dummies use the humanoid model so every combat pose (hitstun, knockdown, overload) shows on them too. */
public class DummyRenderer extends HumanoidMobRenderer<TrainingDummy, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = JJK.id("textures/entity/training_dummy.png");

    /** A humanoid model with the player's joints (elbows, knees, waist), so the dummy shows every pose in full. */
    public static final net.minecraft.client.model.geom.ModelLayerLocation LAYER = new net.minecraft.client.model.geom.ModelLayerLocation(JJK.id("training_dummy"), "main");

    public static void registerLayer() {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(LAYER, () -> {
            var mesh = HumanoidModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, 0f);
            dev.rick.jjk.client.anim.rig.MeshSplit.split(mesh);
            return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64);
        });
    }

    public DummyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(LAYER)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
