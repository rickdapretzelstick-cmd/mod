package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.ClientProgression;
import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.AnimPlayer;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.progression.curse.CursedEnergyShotEntity;
import dev.rick.jjk.progression.curse.FingerBearerEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Finger Bearer, drawn from the supplied {@code models/bb/cursed_spirit.bbmodel} and its texture, animated by the
 * clip engine on the model's own bones ({@code "rig": "cursed_spirit"} clips in {@code animations/cursed_spirit/}).
 * Its breathing idle always runs underneath; on top of the clips the renderer adds what follows the entity's motion: a
 * heavy walk cycle on the legs and arms while it walks (not while a move's clip drives the legs) and the head turning
 * toward where it looks. Only drawn for a player who perceives curses.
 */
public class FingerBearerRenderer extends EntityRenderer<FingerBearerEntity, FingerBearerRenderer.State> {
    public static final Identifier TEXTURE = JJK.id("textures/entity/cursed_spirit.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    /** Model pixels to blocks: the model is 72.5 px tall, the curse about 3.3 blocks. */
    public static final float SCALE = 0.73f / 16f;
    static final String IDLE = "finger_bearer_idle";

    public static class State extends EntityRenderState {
        public float yaw;
        public float headYaw;
        public float headPitch;
        public float walkPos;
        public float walkSpeed;
        public float hurt;
        public boolean visible;
        @Nullable public PoseFrame pose;
    }

    public FingerBearerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.9f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(FingerBearerEntity e, Frustum culler, double x, double y, double z, float partial) {
        // Overrides the default, so it asks about perception itself (CurseRenderMixin hooks the default).
        return ClientProgression.canSee(e) && super.shouldRender(e, culler, x, y, z, partial);
    }

    @Override
    public void extractRenderState(FingerBearerEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.visible = ClientProgression.canSee(e);
        s.yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        float head = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot);
        s.headYaw = Mth.clamp(Mth.wrapDegrees(head - s.yaw), -55f, 55f);
        s.headPitch = Mth.clamp(e.getXRot(partial), -30f, 30f);
        boolean legsFree = e.syncedMove() == FingerBearerEntity.Move.NONE && e.deathTime == 0;
        s.walkPos = e.walkAnimation.position(partial);
        s.walkSpeed = legsFree ? Math.min(1f, e.walkAnimation.speed(partial) * 1.6f) : 0f;
        s.hurt = e.hurtTime > 0 ? e.hurtTime / 10f : 0f;
        float now = e.level().getGameTime() + partial;
        ensureIdle(e.getId(), now);
        s.pose = ClientAnimations.computeModel(e, now);
    }

    /** Its idle loop always runs underneath (a move on its base layer replaces it, and it comes back after). */
    static void ensureIdle(int id, float now) {
        AnimPlayer p = ClientAnimations.player(id);
        if (p != null) {
            for (AnimPlayer.Instance i : p.ordered()) if (!i.stopping) return;
        }
        if (AnimLibrary.get(IDLE) != null) ClientAnimations.play(id, IDLE, 1f, now);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        BbModel model = BbModels.get("cursed_spirit");
        if (model == null || !s.visible) return;
        ps.pushPose();
        // The model faces -z in Blockbench: turn it to face where the entity faces.
        ps.rotate(Axis.YP.rotationDegrees(180f - s.yaw));
        ps.scale(SCALE, SCALE, SCALE);
        BbModel.Posing pose = posing(s);
        // A hit flashes it pale red (it has no vanilla hurt overlay: it isn't a LivingEntityRenderer).
        int tint = s.hurt > 0 ? 0xFF000000 | 0xFF << 16 | Math.round(255 - 110 * s.hurt) << 8 | Math.round(255 - 110 * s.hurt) : 0xFFFFFFFF;
        int light = s.lightCoords;
        c.submitCustomGeometry(ps, SOLID, (p, buf) -> model.render(p, buf, pose, light, tint));
        ps.popPose();
        super.submit(s, ps, c, cam);
    }

    /** The clip pose with the walk cycle and the head's look added on top. */
    static BbModel.Posing posing(State s) {
        BbModel.Posing base = RikaRenderer.posing(s.pose);
        float w = s.walkSpeed, phase = s.walkPos * 0.55f;
        float swing = Mth.sin(phase) * 30f * w, knee = Math.max(0, -Mth.cos(phase)) * 28f * w, knee2 = Math.max(0, Mth.cos(phase)) * 28f * w;
        return new BbModel.Posing() {
            @Override
            public float[] rot(String bone) {
                float[] r = base.rot(bone);
                float dx = 0, dy = 0, dz = 0;
                switch (bone) {
                    case "head" -> {
                        dy = s.headYaw * 0.7f;
                        dx = s.headPitch * 0.6f;
                    }
                    case "neck" -> dy = s.headYaw * 0.3f;
                    case "right_thigh" -> dx = -swing;
                    case "left_thigh" -> dx = swing;
                    case "right_shin" -> dx = knee;
                    case "left_shin" -> dx = knee2;
                    case "right_upper_arm" -> dx = swing * 0.5f;
                    case "left_upper_arm" -> dx = -swing * 0.5f;
                    case "chest" -> {
                        dy = swing * 0.15f;
                        dz = -swing * 0.08f;
                    }
                    default -> {
                        return r;
                    }
                }
                if (dx == 0 && dy == 0 && dz == 0) return r;
                float[] o = r == null ? new float[3] : r.clone();
                o[0] += dx * Mth.DEG_TO_RAD;
                o[1] += dy * Mth.DEG_TO_RAD;
                o[2] += dz * Mth.DEG_TO_RAD;
                return o;
            }

            @Override
            public float[] pos(String bone) {
                float[] p = base.pos(bone);
                if (!bone.equals("body") || w <= 0) return p;
                // Heavy steps: the hips drop at each footfall.
                float[] o = p == null ? new float[3] : p.clone();
                o[1] -= Math.abs(Mth.sin(phase)) * 1.4f * w;
                return o;
            }

            @Override
            public float[] scale(String bone) {
                return base.scale(bone);
            }
        };
    }

    /** Raw cursed energy in flight: a near-black core in a violet shell, a smoky tail. The Blast is twice the size. */
    public static class Shot extends EntityRenderer<CursedEnergyShotEntity, HakariRenderers.State> {
        public Shot(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public HakariRenderers.State createRenderState() {
            return new HakariRenderers.State();
        }

        @Override
        public boolean shouldRender(CursedEnergyShotEntity e, Frustum culler, double x, double y, double z, float partial) {
            return ClientProgression.canSee(e);
        }

        @Override
        public void extractRenderState(CursedEnergyShotEntity e, HakariRenderers.State s, float partial) {
            super.extractRenderState(e, s, partial);
            s.age = e.tickCount + partial;
            s.velocity = e.getDeltaMovement();
            s.scale = e.scale();
        }

        @Override
        public void submit(HakariRenderers.State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
            ps.pushPose();
            Vector3f toCam = new Vector3f((float) (cam.pos.x - s.x), (float) (cam.pos.y - s.y), (float) (cam.pos.z - s.z));
            float r = Math.max(0.3f, s.scale);
            float pulse = 0.92f + 0.12f * Mth.sin(s.age * 1.7f);
            Glow.inkSphere(c, ps, r * 0.55f * pulse, 0.06f, 0.01f, 0.1f, 0.95f, toCam, false);
            Glow.sphere(c, ps, r * 0.8f * pulse, 0.55f, 0.18f, 0.85f, 0.75f, toCam, true);
            Glow.sphere(c, ps, r * 1.25f * pulse, 0.35f, 0.08f, 0.6f, 0.35f, toCam, false);
            var v = s.velocity;
            float len = (float) v.length();
            if (len > 0.05f) {
                var n = v.scale(-1 / len);
                ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
                ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
                Glow.darkBeam(c, ps, r * 5f, r * 0.7f, 0.08f, 0.02f, 0.14f, 0.6f);
                Glow.beam(c, ps, r * 4f, r * 0.35f, 0.5f, 0.2f, 0.85f, 0.5f);
            }
            ps.popPose();
            super.submit(s, ps, c, cam);
        }
    }
}
