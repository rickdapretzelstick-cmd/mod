package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.yuta.DomainBladeEntity;
import dev.rick.jjk.yuta.DomainTechnique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** An Authentic Mutual Love katana: point-down in the platform (or falling), its technique's colour glowing round it. */
public class DomainBladeRenderer extends EntityRenderer<DomainBladeEntity, DomainBladeRenderer.State> {
    public static class State extends EntityRenderState {
        public float yaw;
        public int technique;
        public float age;
    }

    public DomainBladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(DomainBladeEntity e, Frustum culler, double x, double y, double z, float partial) {
        return true;
    }

    @Override
    public void extractRenderState(DomainBladeEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.getYRot();
        s.technique = e.phase();
        s.age = e.tickCount + partial;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        ps.pushPose();
        ps.rotate(Axis.YP.rotationDegrees(s.yaw));
        // Point-down: the tip a little into the floor, the handle at about waist height.
        ps.translate(0, 18f / 16f, 0);
        ps.rotate(Axis.XP.rotationDegrees(-90f));
        ps.rotate(Axis.ZP.rotationDegrees(8f));
        c.submitCustomGeometry(ps, YutaGear.TYPE, (pose, buf) -> YutaGear.katana(pose, buf, 0xF000F0, true));
        ps.popPose();
        // Its technique, glowing round the blade.
        DomainTechnique[] all = DomainTechnique.values();
        int col = all[Math.floorMod(s.technique, all.length)].color;
        float r = (col >> 16 & 255) / 255f, g = (col >> 8 & 255) / 255f, b = (col & 255) / 255f;
        if (s.technique == DomainTechnique.CURSED_SPEECH.ordinal()) {
            r = 0.6f;
            g = 0.6f;
            b = 0.7f;
        }
        float pulse = 0.7f + 0.3f * (float) Math.sin(s.age * 0.2f);
        ps.pushPose();
        ps.translate(0, 0.7, 0);
        Glow.halo(c, ps, Minecraft.getInstance().gameRenderer.mainCamera().rotation(), 0.7f, r, g, b, 0.35f * pulse);
        ps.popPose();
    }
}
