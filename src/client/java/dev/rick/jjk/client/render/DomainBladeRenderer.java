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

/**
 * An Authentic Mutual Love katana: point-down in the platform (or falling, a streak of its colour behind it). Once it has
 * landed its technique is unmistakable from across the arena: a column of its colour rising from it, a ring on the stone
 * round it, and its name above the handle.
 */
public class DomainBladeRenderer extends EntityRenderer<DomainBladeEntity, DomainBladeRenderer.State> {
    public static class State extends EntityRenderState {
        public float yaw;
        public int technique;
        public float age;
        public boolean landed;
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
        s.landed = landed(e);
    }

    /** Stuck in the floor (the server's flag isn't synced: it has stopped falling). */
    private static boolean landed(DomainBladeEntity e) {
        return e.tickCount > 2 && Math.abs(e.getY() - e.yo) < 1e-3;
    }

    @Override
    protected boolean shouldShowName(DomainBladeEntity e, double distSq) {
        return landed(e) && distSq < 40 * 40;
    }

    @Override
    protected net.minecraft.network.chat.Component getNameTag(DomainBladeEntity e) {
        DomainTechnique t = e.technique();
        return net.minecraft.network.chat.Component.literal(t.title).withColor(t.color & 0xFFFFFF);
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
        float pulse = 0.7f + 0.3f * (float) Math.sin(s.age * 0.2f);
        var camRot = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
        ps.pushPose();
        ps.translate(0, 0.7, 0);
        Glow.halo(c, ps, camRot, 0.9f, r, g, b, 0.45f * pulse);
        ps.popPose();
        ps.pushPose();
        if (s.landed) {
            // A column of its colour, visible from anywhere in the domain, and a ring round it on the stone.
            ps.pushPose();
            ps.rotate(Axis.XP.rotationDegrees(-90f));
            Glow.beam(c, ps, 7f, 0.35f, r, g, b, 0.45f * pulse);
            Glow.beam(c, ps, 4f, 0.12f, 1f, 1f, 1f, 0.35f * pulse);
            ps.popPose();
            ps.translate(0, 0.05, 0);
            Glow.ring(c, ps, 0.95f + 0.08f * pulse, 0.12f, r, g, b, 0.7f);
        } else {
            // Falling: a streak of its colour trailing up behind it.
            ps.translate(0, 1.4, 0);
            ps.rotate(Axis.XP.rotationDegrees(-90f));
            Glow.beam(c, ps, 3f, 0.25f, r, g, b, 0.6f);
        }
        ps.popPose();
        // Its name over the handle.
        super.submit(s, ps, c, cam);
    }
}
