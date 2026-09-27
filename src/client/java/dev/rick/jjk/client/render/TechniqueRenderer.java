package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.entity.BlueEntity;
import dev.rick.jjk.entity.HollowPurpleEntity;
import dev.rick.jjk.entity.RedEntity;
import dev.rick.jjk.entity.TechniqueEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Renders Blue, Red and Hollow Purple as layered additive energy. */
public class TechniqueRenderer<T extends TechniqueEntity> extends EntityRenderer<T, TechniqueRenderer.State> {
    public enum Kind { BLUE, RED, PURPLE }

    public static class State extends EntityRenderState {
        public Kind kind;
        public float scale;
        public int phase;
        public float age;
        public Vec3 velocity = Vec3.ZERO;
    }

    private final Kind kind;

    public TechniqueRenderer(EntityRendererProvider.Context ctx, Kind kind) {
        super(ctx);
        this.kind = kind;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(T entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
        return true;
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = kind;
        state.scale = entity.scale();
        state.phase = entity.phase();
        state.age = entity.tickCount + partialTicks;
        state.velocity = entity.getDeltaMovement();
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        Vector3f toCam = new Vector3f((float) (cam.pos.x - s.x), (float) (cam.pos.y - s.y), (float) (cam.pos.z - s.z));
        ps.pushPose();
        switch (s.kind) {
            case BLUE -> blue(s, ps, c, cam, toCam);
            case RED -> red(s, ps, c, cam, toCam);
            case PURPLE -> purple(s, ps, c, cam, toCam);
        }
        ps.popPose();
    }

    private static void blue(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.5f, 0);
        boolean collapsing = s.phase == BlueEntity.COLLAPSING;
        float grow = Math.min(1f, s.age / 5f);
        float pulse = 1f + 0.08f * Mth.sin(s.age * 0.9f);
        float k = collapsing ? 1.6f : grow * pulse;
        float r = 0.55f * s.scale * k;
        // Dark core: the point where space folds in.
        Glow.sphere(c, ps, r * 0.35f, 0.9f, 0.97f, 1f, 1f, toCam, false);
        Glow.orb(c, ps, cam.orientation, toCam, r, ClientFx.BLUE, collapsing ? 1.4f : 1f);
        // Distortion shell: a faint rim, like light bending around the attraction.
        Glow.sphere(c, ps, r * 4.2f, 0.45f, 0.7f, 1f, 0.35f, toCam, true);
        // Accretion rings spinning around the core.
        for (int i = 0; i < 3; i++) {
            ps.pushPose();
            ps.rotate(Axis.YP.rotationDegrees(s.age * (14 + i * 9)));
            ps.rotate(Axis.XP.rotationDegrees(55 + i * 40));
            Glow.ring(c, ps, r * (2.2f + i * 0.7f), r * 0.35f, 0.5f, 0.75f, 1f, 0.55f - i * 0.12f);
            ps.popPose();
        }
    }

    private static void red(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.3f, 0);
        float flicker = 0.9f + 0.2f * Mth.sin(s.age * 3.1f);
        float r = 0.32f * s.scale * flicker;
        Glow.orb(c, ps, cam.orientation, toCam, r, ClientFx.RED, 1.2f);
        Glow.halo(c, ps, cam.orientation, r * 5f, 1f, 0.45f, 0.2f, 0.15f);
        // Trail behind the bolt.
        Vec3 v = s.velocity;
        if (v.lengthSqr() > 1e-4) {
            Vec3 back = v.normalize().reverse();
            ps.pushPose();
            float yaw = (float) Math.atan2(back.x, back.z);
            float pitch = (float) Math.asin(Mth.clamp(-back.y, -1, 1));
            ps.rotate(Axis.YP.rotation(yaw));
            ps.rotate(Axis.XP.rotation(pitch));
            Glow.beam(c, ps, 3.5f * s.scale, r * 1.4f, 1f, 0.25f, 0.15f, 0.8f);
            ps.popPose();
        }
    }

    private static void purple(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.75f, 0);
        float r = s.scale;
        float pulse = 1f + 0.05f * Mth.sin(s.age * 1.3f);
        Glow.sphere(c, ps, r * 0.55f * pulse, 1f, 1f, 1f, 1f, toCam, false);
        Glow.sphere(c, ps, r * 0.95f * pulse, ClientFx.PURPLE[0], ClientFx.PURPLE[1], ClientFx.PURPLE[2], 0.9f, toCam, false);
        Glow.sphere(c, ps, r * 1.35f, 0.75f, 0.45f, 1f, 0.4f, toCam, false);
        Glow.sphere(c, ps, r * 1.8f, 0.6f, 0.3f, 1f, 0.5f, toCam, true);
        Glow.halo(c, ps, cam.orientation, r * 4f, 0.7f, 0.4f, 1f, 0.3f);
        // Blue and red still orbiting inside the imaginary mass.
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.rotate(Axis.YP.rotationDegrees(s.age * 25 + i * 180));
            ps.translate(r * 0.6f, 0, 0);
            float[] col = i == 0 ? ClientFx.BLUE : ClientFx.RED;
            Glow.sphere(c, ps, r * 0.2f, col[0], col[1], col[2], 0.8f, toCam, false);
            ps.popPose();
        }
        Vec3 v = s.velocity;
        if (v.lengthSqr() > 1e-4) {
            Vec3 back = v.normalize().reverse();
            ps.pushPose();
            ps.rotate(Axis.YP.rotation((float) Math.atan2(back.x, back.z)));
            ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-back.y, -1, 1))));
            Glow.beam(c, ps, 10f * r, r * 1.2f, 0.65f, 0.35f, 1f, 0.6f);
            ps.popPose();
        }
    }

    public static EntityRendererProvider<BlueEntity> blue() {
        return ctx -> new TechniqueRenderer<>(ctx, Kind.BLUE);
    }

    public static EntityRendererProvider<RedEntity> red() {
        return ctx -> new TechniqueRenderer<>(ctx, Kind.RED);
    }

    public static EntityRendererProvider<HollowPurpleEntity> purple() {
        return ctx -> new TechniqueRenderer<>(ctx, Kind.PURPLE);
    }
}
