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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders Blue, Red and Hollow Purple. Each technique has its own visual language so it can be told apart by shape and
 * motion alone:
 * <ul>
 *   <li>Blue: a compact dark core with arms of light spiralling <i>into</i> it. Max Blue adds an event horizon, a photon
 *   ring, a tilted accretion disk and huge lensing shells.</li>
 *   <li>Red: an unstable, jittering core throwing jagged spikes <i>outward</i>, with a short hot trail. Max Red is the
 *   same idea at catastrophic size, with bow-shock rings around its flight path.</li>
 *   <li>Purple: the imaginary mass, a white-hot core in a ragged ring of dark magenta crackling with lightning, a bow
 *   shock ahead, torn wind whipping back and a very long trail.</li>
 * </ul>
 */
public class TechniqueRenderer<T extends TechniqueEntity> extends EntityRenderer<T, TechniqueRenderer.State> {
    public enum Kind { BLUE, RED, PURPLE }

    /** Scale at and above which a Blue or Red is rendered as its Max version. */
    public static final float MAX_BLUE_SCALE = 3f, MAX_RED_SCALE = 4.5f;

    public static class State extends EntityRenderState {
        public Kind kind;
        public float scale;
        public int phase;
        public float age;
        /** Ticks since Blue began collapsing, or -1. */
        public float collapse = -1;
        public long tick;
        public Vec3 velocity = Vec3.ZERO;
    }

    private static final Map<TechniqueEntity, Float> COLLAPSE_START = new WeakHashMap<>();

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
        state.tick = entity.tickCount;
        state.velocity = entity.getDeltaMovement();
        if (entity instanceof BlueEntity b && b.isCollapsing()) {
            state.collapse = state.age - COLLAPSE_START.computeIfAbsent(entity, e -> state.age);
        } else {
            state.collapse = -1;
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        Vector3f toCam = new Vector3f((float) (cam.pos.x - s.x), (float) (cam.pos.y - s.y), (float) (cam.pos.z - s.z));
        ps.pushPose();
        switch (s.kind) {
            case BLUE -> {
                if (s.scale >= MAX_BLUE_SCALE) maxBlue(s, ps, c, cam, toCam);
                else blue(s, ps, c, cam, toCam);
            }
            case RED -> {
                if (s.scale >= MAX_RED_SCALE) maxRed(s, ps, c, cam, toCam);
                else red(s, ps, c, cam, toCam);
            }
            case PURPLE -> purple(s, ps, c, cam, toCam);
        }
        ps.popPose();
    }

    /** How far through the implosion Blue is: 1 while active, shrinking to 0 as it collapses. */
    private static float collapseScale(State s) {
        if (s.collapse < 0) return 1f;
        float t = Mth.clamp(s.collapse / 6f, 0, 1);
        return 1f - t * t * 0.85f;
    }

    // --- Blue ---

    private static void blue(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.5f, 0);
        float grow = Math.min(1f, s.age / 4f);
        float k = grow * collapseScale(s) * (1f + 0.06f * Mth.sin(s.age * 1.1f));
        float bright = s.collapse >= 0 ? 1.5f : 1f;
        float r = 0.45f * s.scale * k;
        // A small, dense point: dark heart ringed by blue light.
        Glow.orb(c, ps, cam.orientation, toCam, r, ClientFx.BLUE, bright);
        Glow.sphere(c, ps, r * 0.3f, 0.05f, 0.1f, 0.35f, 0.6f, toCam, false);
        // Space bending around it.
        Glow.sphere(c, ps, r * 3.2f, 0.45f, 0.7f, 1f, 0.3f, toCam, true);
        // Light being dragged in: arms wind inward over time (phase runs forward = inward flow).
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.rotate(Axis.XP.rotationDegrees(i == 0 ? 70 : -35));
            ps.rotate(Axis.ZP.rotationDegrees(i * 50));
            Glow.spiral(c, ps, 3, r * 5.5f * (2 - k), r * 0.6f, 0.9f, r * 0.35f, 0.5f, 0.75f, 1f, 0.55f * bright, s.age * 0.35f + i);
            ps.popPose();
        }
        ps.pushPose();
        ps.rotate(Axis.YP.rotationDegrees(s.age * 22));
        ps.rotate(Axis.XP.rotationDegrees(62));
        Glow.ring(c, ps, r * 2.1f, r * 0.3f, 0.55f, 0.8f, 1f, 0.5f);
        ps.popPose();
    }

    private static void maxBlue(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.5f, 0);
        // Longer buildup than base Blue: it swells into existence over a second.
        float grow = Mth.clamp(s.age / 20f, 0, 1);
        grow = 1 - (1 - grow) * (1 - grow);
        float k = grow * collapseScale(s) * (1f + 0.04f * Mth.sin(s.age * 0.6f));
        float bright = s.collapse >= 0 ? 1.6f : 1f;
        float r = 0.5f * s.scale * k;
        // Event horizon: a hole in the world with the void visible through it.
        c.submitCustomGeometry(ps, RenderTypes.endGateway(), (pose, buf) -> WorldEffectsRenderer.shell(pose, buf, r * 0.55f));
        // Photon ring hugging the horizon, always facing the viewer.
        ps.pushPose();
        ps.rotate(cam.orientation);
        ps.rotate(Axis.XP.rotationDegrees(90));
        Glow.ring(c, ps, r * 0.62f, r * 0.12f, 0.85f, 0.95f, 1f, 0.95f * bright);
        Glow.ring(c, ps, r * 0.75f, r * 0.35f, 0.4f, 0.65f, 1f, 0.5f * bright);
        ps.popPose();
        Glow.sphere(c, ps, r * 1.05f, 0.35f, 0.6f, 1f, 0.7f * bright, toCam, true);
        // Accretion disk: dense arms pouring inward, tilted so it reads from any angle.
        ps.pushPose();
        ps.rotate(Axis.ZP.rotationDegrees(18));
        ps.rotate(Axis.XP.rotationDegrees(12));
        Glow.spiral(c, ps, 6, r * 4.5f, r * 0.7f, 1.1f, r * 0.28f, 0.45f, 0.72f, 1f, 0.7f * bright, s.age * 0.18f);
        Glow.spiral(c, ps, 4, r * 3.2f, r * 0.7f, 1.4f, r * 0.18f, 0.85f, 0.93f, 1f, 0.5f * bright, s.age * 0.26f + 1.3f);
        for (int i = 0; i < 3; i++) {
            ps.pushPose();
            ps.rotate(Axis.YP.rotationDegrees(s.age * (9 + i * 5)));
            Glow.ring(c, ps, r * (1.4f + i * 0.9f), r * 0.25f, 0.4f, 0.65f, 1f, 0.45f - i * 0.1f);
            ps.popPose();
        }
        ps.popPose();
        // A second, steeper disk of debris-light, like matter falling in from above and below.
        ps.pushPose();
        ps.rotate(Axis.XP.rotationDegrees(75));
        ps.rotate(Axis.YP.rotationDegrees(-s.age * 4));
        Glow.spiral(c, ps, 3, r * 3.6f, r * 0.8f, 0.8f, r * 0.2f, 0.3f, 0.5f, 1f, 0.35f * bright, s.age * 0.22f);
        ps.popPose();
        // Gravitational lensing: nested shells and a wide pale haze owning the whole area.
        Glow.sphere(c, ps, r * 2.4f, 0.35f, 0.55f, 1f, 0.35f, toCam, true);
        Glow.sphere(c, ps, r * 4.2f, 0.25f, 0.45f, 1f, 0.25f, toCam, true);
        Glow.halo(c, ps, cam.orientation, r * 7f, 0.25f, 0.45f, 1f, 0.16f * bright);
    }

    // --- Red ---

    private static void red(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.3f, 0);
        // Unstable: the core jitters and flickers every frame.
        java.util.Random j = new java.util.Random(s.tick * 31);
        float jit = 0.05f * s.scale;
        ps.translate((j.nextFloat() - 0.5f) * jit, (j.nextFloat() - 0.5f) * jit, (j.nextFloat() - 0.5f) * jit);
        float flicker = 0.85f + 0.3f * j.nextFloat();
        float r = 0.3f * s.scale * flicker;
        Glow.orb(c, ps, cam.orientation, toCam, r, ClientFx.RED, 1.2f);
        Glow.spikes(c, ps, 7, r * 0.5f, r * 3.2f, r * 0.18f, 1f, 0.35f, 0.2f, 0.9f, s.tick * 7919L);
        Glow.halo(c, ps, cam.orientation, r * 4f, 1f, 0.4f, 0.2f, 0.18f);
        trail(s, ps, c, 3f * s.scale, r * 1.3f, ClientFx.RED, 0.85f);
    }

    private static void maxRed(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.3f, 0);
        java.util.Random j = new java.util.Random(s.tick * 31);
        float r = 0.35f * s.scale * (0.9f + 0.2f * j.nextFloat());
        // Catastrophic outward force: a searing core, dense spikes, an angry corona.
        Glow.orb(c, ps, cam.orientation, toCam, r, ClientFx.RED, 1.4f);
        Glow.sphere(c, ps, r * 0.35f, 1f, 1f, 1f, 1f, toCam, false);
        Glow.spikes(c, ps, 16, r * 0.4f, r * 3.6f, r * 0.2f, 1f, 0.3f, 0.15f, 1f, s.tick * 7919L);
        Glow.spikes(c, ps, 8, r * 0.6f, r * 2.2f, r * 0.12f, 1f, 0.8f, 0.6f, 0.9f, s.tick * 104729L);
        Glow.sphere(c, ps, r * 1.9f, 1f, 0.3f, 0.15f, 0.45f, toCam, true);
        Glow.halo(c, ps, cam.orientation, r * 5f, 1f, 0.35f, 0.15f, 0.25f);
        Vec3 v = s.velocity;
        if (v.lengthSqr() > 1e-4) {
            // Bow shock rings peeling off the front as it tears through the air.
            ps.pushPose();
            Flashes.orientY(ps, v);
            for (int i = 0; i < 3; i++) {
                float t = ((s.age * 0.25f) + i / 3f) % 1f;
                ps.pushPose();
                ps.translate(0, -t * r * 4f, 0);
                Glow.ring(c, ps, r * (1.1f + t * 1.6f), r * 0.3f, 1f, 0.45f, 0.25f, 0.6f * (1 - t));
                ps.popPose();
            }
            ps.popPose();
        }
        trail(s, ps, c, 4.5f * s.scale, r * 1.5f, ClientFx.RED, 1f);
    }

    // --- Purple ---

    private static void purple(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Vector3f toCam) {
        ps.translate(0, 0.75f, 0);
        float r = s.scale;
        float[] p = ClientFx.PURPLE;
        // The imaginary mass (JJS): a white-hot, sparkling core in a ragged ring of dark magenta, lightning crackling off it.
        PurpleMass.draw(c, ps, cam.orientation, toCam, r * 0.55f * PurpleMass.pulse(s.age), s.age, 0x5eedL, 5, 3.2f);
        Vec3 v = s.velocity;
        if (v.lengthSqr() > 1e-4) {
            // Space shoved aside ahead of it, and torn wind whipping back along its path.
            ps.pushPose();
            Flashes.orientY(ps, v);
            ps.translate(0, r * 0.9f, 0);
            Glow.ring(c, ps, r * 1.5f, r * 0.35f, 1f, 0.6f, 1f, 0.5f);
            ps.translate(0, -r * 1.2f, 0);
            Glow.ring(c, ps, r * 2.1f, r * 0.3f, p[0], p[1], p[2], 0.35f);
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.translate(0, -r * (0.6f + i * 0.9f), 0);
                Glow.swirl(c, ps, r * (1.2f + i * 0.35f), s.age * 0.9f + i * 2.1f, 2.6f, r * 0.12f, 1f, 0.7f, 1f, 0.55f - i * 0.12f, 0);
                ps.popPose();
            }
            ps.popPose();
        }
        trail(s, ps, c, 14f * r, r * 1.3f, p, 0.75f);
        trail(s, ps, c, 8f * r, r * 0.45f, new float[] {1f, 1f, 1f}, 0.6f);
    }

    /** A tapering beam pointing back along the direction of travel. */
    private static void trail(State s, PoseStack ps, SubmitNodeCollector c, float length, float width, float[] col, float a) {
        Vec3 v = s.velocity;
        if (v.lengthSqr() < 1e-4) return;
        Vec3 back = v.normalize().reverse();
        ps.pushPose();
        ps.rotate(Axis.YP.rotation((float) Math.atan2(back.x, back.z)));
        ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-back.y, -1, 1))));
        Glow.beam(c, ps, length, width, col[0], col[1], col[2], a);
        Glow.beam(c, ps, length * 0.5f, width * 0.35f, 1f, 1f, 1f, a * 0.8f);
        ps.popPose();
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
