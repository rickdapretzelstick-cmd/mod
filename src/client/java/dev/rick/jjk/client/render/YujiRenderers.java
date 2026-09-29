package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.entity.FireArrowEntity;
import dev.rick.jjk.entity.ThrownPropEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Vessel's thrown props (the block itself, tumbling) and Open's arrow of fire. */
public final class YujiRenderers {
    private YujiRenderers() {}

    public static EntityRendererProvider<ThrownPropEntity> prop() {
        return Prop::new;
    }

    public static EntityRendererProvider<FireArrowEntity> arrow() {
        return Arrow::new;
    }

    public static class Prop extends EntityRenderer<ThrownPropEntity, HakariRenderers.State> {
        public Prop(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public HakariRenderers.State createRenderState() {
            return new HakariRenderers.State();
        }

        @Override
        public boolean shouldRender(ThrownPropEntity e, Frustum culler, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(ThrownPropEntity e, HakariRenderers.State s, float partial) {
            super.extractRenderState(e, s, partial);
            s.age = e.tickCount + partial;
            s.velocity = e.getDeltaMovement();
            HakariRenderers.light(e, s, e.blockState());
        }

        @Override
        public void submit(HakariRenderers.State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
            ps.pushPose();
            ps.translate(0, 0.45, 0);
            ps.scale(0.85f, 0.85f, 0.85f);
            ps.rotate(Axis.XP.rotationDegrees(s.age * 23));
            ps.rotate(Axis.ZP.rotationDegrees(s.age * 17));
            ps.translate(-0.5, -0.5, -0.5);
            c.submitMovingBlock(ps, s.block, s.outlineColor);
            ps.popPose();
            super.submit(s, ps, c, cam);
        }
    }

    /** A spear of fire: a white-gold core in an orange sheath, a long burning trail behind it. */
    public static class Arrow extends EntityRenderer<FireArrowEntity, HakariRenderers.State> {
        public Arrow(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public HakariRenderers.State createRenderState() {
            return new HakariRenderers.State();
        }

        @Override
        public boolean shouldRender(FireArrowEntity e, Frustum culler, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(FireArrowEntity e, HakariRenderers.State s, float partial) {
            super.extractRenderState(e, s, partial);
            s.age = e.tickCount + partial;
            s.velocity = e.getDeltaMovement();
        }

        @Override
        public void submit(HakariRenderers.State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
            ps.pushPose();
            ps.translate(0, 0.3, 0);
            Vector3f toCam = new Vector3f((float) (cam.pos.x - s.x), (float) (cam.pos.y - s.y), (float) (cam.pos.z - s.z));
            float flicker = 0.9f + 0.2f * Mth.sin(s.age * 3.1f);
            Glow.sphere(c, ps, 0.35f * flicker, 1f, 0.9f, 0.55f, 0.9f, toCam, true);
            Glow.sphere(c, ps, 0.7f * flicker, 1f, 0.45f, 0.1f, 0.45f, toCam, true);
            Vec3 v = s.velocity;
            float len = (float) v.length();
            if (len > 0.05f) {
                Vec3 n = v.scale(-1 / len);
                ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
                ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
                Glow.beam(c, ps, 5f, 0.35f, 1f, 0.45f, 0.1f, 0.7f);
                Glow.beam(c, ps, 3.5f, 0.14f, 1f, 0.95f, 0.6f, 0.95f);
                // The arrowhead ahead of it.
                ps.rotate(Axis.XP.rotationDegrees(180));
                Glow.beam(c, ps, 1.1f, 0.2f, 1f, 0.95f, 0.7f, 0.95f);
            }
            ps.popPose();
            super.submit(s, ps, c, cam);
        }
    }
}
