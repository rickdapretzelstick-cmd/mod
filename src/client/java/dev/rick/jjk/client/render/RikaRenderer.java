package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.anim.AnimLibrary;
import dev.rick.jjk.client.anim.AnimPlayer;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.yuta.RikaEntity;
import net.minecraft.client.Minecraft;
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

/**
 * Rika, drawn from her Blockbench model ({@code models/bb/rika.bbmodel}) and animated by the clip engine with her own rig
 * ({@code "rig": "rika"} clips in {@code animations/rika/}). Partly manifested she is a pale, see-through apparition;
 * fully manifested (True Love) she is solid. Her idle loop plays underneath whatever move she is doing.
 */
public class RikaRenderer extends EntityRenderer<RikaEntity, RikaRenderer.State> {
    public static final Identifier TEXTURE = JJK.id("textures/entity/rika.png");
    private static final RenderType GHOST = RenderTypes.entityTranslucent(TEXTURE);
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    /** Model pixels to blocks, at her size in JJS (about twice a player's height). */
    public static final float SCALE = 0.85f / 16f;

    public static class State extends EntityRenderState {
        public float yaw;
        public float alpha;
        public boolean solid;
        @Nullable public PoseFrame pose;
    }

    private final java.util.Map<Integer, Float> fade = new java.util.HashMap<>();

    public RikaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(RikaEntity e, Frustum culler, double x, double y, double z, float partial) {
        return true;
    }

    @Override
    public void extractRenderState(RikaEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        float now = e.level().getGameTime() + partial;
        ensureIdle(e.getId(), now);
        s.pose = ClientAnimations.computeModel(e, now);
        boolean shown = e.has(RikaEntity.VISIBLE) || e.has(RikaEntity.PILOTED) || e.has(RikaEntity.BUSY) || e.has(RikaEntity.FULL);
        s.solid = e.has(RikaEntity.FULL);
        float want = !shown ? 0 : s.solid ? 1 : e.has(RikaEntity.PILOTED) || e.has(RikaEntity.BUSY) ? 0.72f : 0.4f;
        // Manifesting and fading take a moment (she billows out of black smoke and thins away).
        float a = fade.getOrDefault(e.getId(), 0f);
        a += (want - a) * 0.12f;
        if (e.tickCount < 2) a = 0;
        fade.put(e.getId(), a);
        if (fade.size() > 64) fade.clear();
        s.alpha = a;
    }

    /** Her idle loop always runs underneath (a move on her base layer replaces it, and it comes back after). */
    static void ensureIdle(int id, float now) {
        AnimPlayer p = ClientAnimations.player(id);
        if (p != null) {
            for (AnimPlayer.Instance i : p.ordered()) if (!i.stopping) return;
        }
        if (AnimLibrary.get("rika_idle") != null) ClientAnimations.play(id, "rika_idle", 1f, now);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        BbModel model = BbModels.get("rika");
        if (model == null || s.alpha < 0.02f) return;
        ps.pushPose();
        // Her model faces -z in Blockbench: turn it to face where the entity faces.
        ps.rotate(Axis.YP.rotationDegrees(180f - s.yaw));
        ps.scale(SCALE, SCALE, SCALE);
        int alpha = Math.round(Mth.clamp(s.alpha, 0, 1) * 255);
        int argb = s.solid ? 0xFFFFFFFF : (alpha << 24) | 0xF4F4FF;
        BbModel.Posing pose = posing(s.pose);
        int light = s.solid ? s.lightCoords : 0xF000F0;
        c.submitCustomGeometry(ps, s.solid ? SOLID : GHOST, (p, buf) -> model.render(p, buf, pose, light, argb));
        ps.popPose();
    }

    /** The clip engine's frame as a model pose (rest where nothing moves a bone). */
    public static BbModel.Posing posing(@Nullable PoseFrame f) {
        if (f == null) return BbModel.Posing.REST;
        return new BbModel.Posing() {
            @Override
            public float[] rot(String bone) {
                return f.named(bone, Clip.ROT);
            }

            @Override
            public float[] pos(String bone) {
                return f.named(bone, Clip.POS);
            }

            @Override
            public float[] scale(String bone) {
                return f.named(bone, Clip.SCALE);
            }
        };
    }

    /** Where one of her bones is in the world right now (for effects held in her fist or at her eye), or null. */
    @Nullable
    public static net.minecraft.world.phys.Vec3 bone(RikaEntity e, String bone, org.joml.Vector3f offset, float partial) {
        BbModel model = BbModels.get("rika");
        if (model == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        PoseFrame f = ClientAnimations.computeModel(e, mc.level.getGameTime() + partial);
        org.joml.Vector3f p = model.locate(bone, posing(f), offset);
        if (p == null) return null;
        float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        org.joml.Vector3f w = new org.joml.Matrix4f().rotateY((180f - yaw) * Mth.DEG_TO_RAD).scale(SCALE).transformPosition(p);
        return e.getPosition(partial).add(w.x, w.y, w.z);
    }
}
