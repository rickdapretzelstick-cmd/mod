package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.anim.ClientAnimations;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import dev.rick.jjk.progression.prison.PrisonRealmEntity;
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

import java.util.HashMap;
import java.util.Map;

/**
 * The Prison Realm in the world, drawn from the supplied {@code models/bb/prison_realm.bbmodel} and animated by its own
 * clips ({@code animations/prison_realm/}): the whole opening-restraint-closing sequence while it seals someone, the
 * closed idle while it holds them, the opening when they are released. Sealed, a crimson beam rises from it so it can be
 * found from far off (the vanilla glow outline shows it through walls too); someone opening it from outside makes it
 * shudder harder the closer they are.
 */
public class PrisonRealmRenderer extends EntityRenderer<PrisonRealmEntity, PrisonRealmRenderer.State> {
    public static final Identifier TEXTURE = JJK.id("textures/entity/prison_realm.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    /** Model pixels to blocks: the closed cube is 12 px, a quarter of a block here (as small as a die; open, under one). */
    public static final float SCALE = 0.33f / 16f;
    static final String FULL = "prison_realm_full_sequence", IDLE = "prison_realm_idle", OPEN = "prison_realm_open";

    public static class State extends EntityRenderState {
        public float yaw;
        public int phase;
        public float rescue;
        public float age;
        @Nullable public PoseFrame pose;
    }

    private final Map<Integer, Integer> shownPhase = new HashMap<>();

    public PrisonRealmRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.6f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(PrisonRealmEntity e, Frustum culler, double x, double y, double z, float partial) {
        // Its beam reaches well beyond its box.
        return true;
    }

    @Override
    public void extractRenderState(PrisonRealmEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.getYRot();
        s.phase = e.realmPhase();
        s.rescue = e.rescue() / 100f;
        s.age = e.tickCount + partial;
        float now = e.level().getGameTime() + partial;
        Integer was = shownPhase.put(e.getId(), s.phase);
        if (shownPhase.size() > 32) shownPhase.clear();
        String clip = s.phase == PrisonRealmEntity.SEALING ? FULL : s.phase == PrisonRealmEntity.OPENING ? OPEN : IDLE;
        if (was == null || was != s.phase) ClientAnimations.play(e.getId(), clip, 1f, now);
        s.pose = ClientAnimations.computeModel(e, now);
        // The closed idle loops for good (anything that let it go comes back to it).
        if (s.pose == null && s.phase == PrisonRealmEntity.SEALED) ClientAnimations.play(e.getId(), IDLE, 1f, now);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        BbModel model = BbModels.get("prison_realm");
        ps.pushPose();
        if (s.rescue > 0) {
            // Shuddering as someone outside pries it open.
            float k = 0.02f + 0.06f * s.rescue;
            ps.translate(Mth.sin(s.age * 2.7f) * k, Math.abs(Mth.sin(s.age * 3.9f)) * k, Mth.cos(s.age * 3.1f) * k);
        }
        if (model != null) {
            ps.pushPose();
            // The model faces -z in Blockbench: turn it to face where the entity faces.
            ps.rotate(Axis.YP.rotationDegrees(180f - s.yaw));
            ps.scale(SCALE, SCALE, SCALE);
            BbModel.Posing pose = RikaRenderer.posing(s.pose);
            int light = s.lightCoords;
            c.submitCustomGeometry(ps, SOLID, (p, buf) -> model.render(p, buf, pose, light, 0xFFFFFFFF));
            ps.popPose();
        }
        if (s.phase != PrisonRealmEntity.SEALING) {
            // The marker: a crimson beam straight up, pulsing slowly.
            float pulse = 0.75f + 0.25f * Mth.sin(s.age * 0.12f);
            ps.pushPose();
            ps.translate(0, 0.9, 0);
            ps.rotate(Axis.XP.rotationDegrees(-90f));
            // A blended crimson core (reads against a bright sky, where added light alone vanishes) under the glow.
            Glow.darkBeam(c, ps, 96f, 0.3f, 0.6f, 0.02f, 0.14f, 0.75f);
            Glow.beam(c, ps, 96f, 0.22f, 0.95f, 0.12f, 0.45f, 0.8f * pulse);
            Glow.beam(c, ps, 72f, 0.6f, 0.6f, 0.05f, 0.35f, 0.3f * pulse);
            ps.popPose();
        }
        ps.popPose();
        super.submit(s, ps, c, cam);
    }
}
