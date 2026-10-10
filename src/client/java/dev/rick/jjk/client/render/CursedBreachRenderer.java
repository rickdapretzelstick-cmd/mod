package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.client.particle.EnergyParticle;
import dev.rick.jjk.progression.investigation.CursedBreachEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A Cursed Breach, drawn: a vertical tear in the air, black at its heart, its ragged edge a dull blood-crimson that
 * crawls; two thin rings of cursed energy turning slowly round it at odd angles, a crown of short flickering cracks, and
 * every few seconds a pulse that swells and fades. Dust and dark motes drift in from around it and are swallowed. Not a
 * portal's even purple sheet: something wrong with the space itself. Faster and brighter once someone is through.
 */
public class CursedBreachRenderer extends EntityRenderer<CursedBreachEntity, CursedBreachRenderer.State> {
    static final float[] INK = {0.03f, 0.0f, 0.02f}, RIM = {0.42f, 0.03f, 0.1f}, GLOW = {0.78f, 0.12f, 0.24f}, VIOLET = {0.45f, 0.16f, 0.62f};

    public static class State extends EntityRenderState {
        float age;
        int breach;
        long seed;
    }

    public CursedBreachRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CursedBreachEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.age = e.tickCount + partial;
        s.breach = e.breachState();
        s.seed = e.getId() * 31L;
    }

    private static final net.minecraft.client.renderer.rendertype.RenderType INK_TYPE = net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads();
    /** Half-width and half-height of the tear. */
    static final float W = 0.42f, H = 0.95f;

    /** The tear's edge at angle {@code th} (0 at the top), ragged and slowly crawling. */
    private static float edge(float th, float t, long seed, float swell) {
        float s1 = (seed % 97) * 0.13f, s2 = (seed % 53) * 0.21f;
        return 1f + 0.10f * Mth.sin(5 * th + s1 + t * 0.09f) + 0.07f * Mth.sin(9 * th + s2 - t * 0.17f)
                + 0.05f * Mth.sin(17 * th + t * 0.31f) + 0.08f * swell;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        float t = s.age * (s.breach == 1 ? 1.8f : 1f);
        // A slow heartbeat: a swell every 80 ticks (faster once someone is through).
        float beatPhase = (t % 80f) / 80f;
        float beat = beatPhase < 0.2f ? Mth.sin(beatPhase / 0.2f * Mth.PI) : 0f;
        ps.pushPose();
        ps.translate(0, 0.95f, 0);
        // Upright, turned to face the viewer round its vertical axis (a tear in the air stays upright).
        double dx = cam.pos.x - s.x, dz = cam.pos.z - s.z;
        ps.rotate(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        final int seg = 40;
        float[] ex = new float[seg + 1], ey = new float[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float th = Mth.TWO_PI * (i % seg) / seg;
            float k = edge(th, t, s.seed, beat);
            // A lens: pointed top and bottom, widest in the middle.
            float sx = Mth.sin(th), cy = Mth.cos(th);
            float lens = (float) Math.pow(Math.abs(sx), 0.75);
            ex[i] = Math.signum(sx) * lens * W * k;
            ey[i] = cy * H * (0.96f + 0.04f * k);
        }
        // 1. The faint haze round it: air going wrong.
        c.submitCustomGeometry(ps, Glow.ADDITIVE, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                float m = 2.1f + 0.3f * beat;
                buf.addVertex(pose, ex[i], ey[i], 0).setColor(GLOW[0], GLOW[1], GLOW[2], 0.10f + 0.08f * beat);
                buf.addVertex(pose, ex[i] * m, ey[i] * 1.35f, 0).setColor(GLOW[0], GLOW[1], GLOW[2], 0f);
                buf.addVertex(pose, ex[i + 1] * m, ey[i + 1] * 1.35f, 0).setColor(GLOW[0], GLOW[1], GLOW[2], 0f);
                buf.addVertex(pose, ex[i + 1], ey[i + 1], 0).setColor(GLOW[0], GLOW[1], GLOW[2], 0.10f + 0.08f * beat);
            }
        });
        // 2. The black heart, opaque at the centre, its edge a dark crimson.
        c.submitCustomGeometry(ps, INK_TYPE, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                buf.addVertex(pose, 0, 0, 0).setColor(INK[0], INK[1], INK[2], 1f);
                buf.addVertex(pose, ex[i] * 0.8f, ey[i] * 0.9f, 0).setColor(INK[0], INK[1], INK[2], 0.98f);
                buf.addVertex(pose, ex[i + 1] * 0.8f, ey[i + 1] * 0.9f, 0).setColor(INK[0], INK[1], INK[2], 0.98f);
                buf.addVertex(pose, 0, 0, 0).setColor(INK[0], INK[1], INK[2], 1f);
                buf.addVertex(pose, ex[i] * 0.8f, ey[i] * 0.9f, 0).setColor(INK[0], INK[1], INK[2], 0.98f);
                buf.addVertex(pose, ex[i], ey[i], 0).setColor(RIM[0], RIM[1], RIM[2], 0.9f);
                buf.addVertex(pose, ex[i + 1], ey[i + 1], 0).setColor(RIM[0], RIM[1], RIM[2], 0.9f);
                buf.addVertex(pose, ex[i + 1] * 0.8f, ey[i + 1] * 0.9f, 0).setColor(INK[0], INK[1], INK[2], 0.98f);
            }
        });
        // 3. The burning rim, just outside the edge (additive), brightest on each pulse.
        float rimA = 0.55f + 0.35f * beat + (s.breach == 1 ? 0.15f : 0f);
        c.submitCustomGeometry(ps, Glow.ADDITIVE, (pose, buf) -> {
            for (int i = 0; i < seg; i++) {
                float o = 1.18f + 0.05f * beat;
                buf.addVertex(pose, ex[i] * 0.97f, ey[i] * 0.98f, 0.01f).setColor(GLOW[0], GLOW[1], GLOW[2], rimA);
                buf.addVertex(pose, ex[i] * o, ey[i] * 1.06f, 0.01f).setColor(GLOW[0], GLOW[1], GLOW[2], 0f);
                buf.addVertex(pose, ex[i + 1] * o, ey[i + 1] * 1.06f, 0.01f).setColor(GLOW[0], GLOW[1], GLOW[2], 0f);
                buf.addVertex(pose, ex[i + 1] * 0.97f, ey[i + 1] * 0.98f, 0.01f).setColor(GLOW[0], GLOW[1], GLOW[2], rimA);
            }
        });
        // 4. Cracks in the air running out from the edge, re-drawn a few times a second.
        java.util.Random rnd = new java.util.Random(s.seed + (long) (t / 4f));
        c.submitCustomGeometry(ps, Glow.ADDITIVE, (pose, buf) -> {
            for (int k = 0; k < 6; k++) {
                int i = rnd.nextInt(seg);
                float x0 = ex[i], y0 = ey[i];
                float len = 0.25f + rnd.nextFloat() * 0.45f;
                float dxn = x0 == 0 ? 0 : Math.signum(x0), ang = (rnd.nextFloat() - 0.5f) * 1.2f;
                float x1 = x0 + (dxn * Mth.cos(ang)) * len, y1 = y0 + Mth.sin(ang) * len + (y0 > 0 ? 0.1f : -0.1f) * len;
                float wd = 0.018f, a = (0.35f + 0.4f * beat) * (0.5f + rnd.nextFloat() * 0.5f);
                buf.addVertex(pose, x0, y0 - wd, 0.02f).setColor(GLOW[0], GLOW[1], GLOW[2], a);
                buf.addVertex(pose, x1, y1 - wd * 0.3f, 0.02f).setColor(GLOW[0], GLOW[1], GLOW[2], 0f);
                buf.addVertex(pose, x1, y1 + wd * 0.3f, 0.02f).setColor(GLOW[0], GLOW[1], GLOW[2], 0f);
                buf.addVertex(pose, x0, y0 + wd, 0.02f).setColor(GLOW[0], GLOW[1], GLOW[2], a);
            }
        });
        // 5. One thin ring of cursed energy turning slowly round it, tilted.
        ps.pushPose();
        ps.rotate(Axis.ZP.rotationDegrees(18f));
        ps.rotate(Axis.XP.rotationDegrees(78f));
        ps.rotate(Axis.YP.rotationDegrees(t * 1.4f));
        Glow.ring(c, ps, 0.85f + 0.12f * beat, 0.035f, VIOLET[0], VIOLET[1], VIOLET[2], 0.25f + 0.25f * beat);
        ps.popPose();
        ps.popPose();
        super.submit(s, ps, c, cam);
    }

    private static final RandomSource RNG = RandomSource.create();

    /** The client's per-tick look: motes and dust drawn in and swallowed, a rare whisper. */
    public static void clientTick(CursedBreachEntity e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(e) > 48 * 48) return;
        Vec3 core = e.position().add(0, 0.95, 0);
        boolean through = e.breachState() == 1;
        int n = through ? 3 : 2;
        for (int i = 0; i < n; i++) {
            if (RNG.nextFloat() > 0.7f) continue;
            double a = RNG.nextDouble() * Mth.TWO_PI, up = (RNG.nextDouble() - 0.5) * 2.4;
            double r = 1.6 + RNG.nextDouble() * 1.6;
            Vec3 from = core.add(Math.cos(a) * r, up, Math.sin(a) * r);
            Vec3 vel = core.subtract(from).scale(0.045 + RNG.nextDouble() * 0.02);
            boolean dark = RNG.nextInt(3) != 0;
            ClientFx.add(mc.level, from, vel, dark ? EnergyParticle.Sprite.SMOKE : EnergyParticle.Sprite.GLOW, dark ? INK : GLOW,
                    dark ? 0.75f : 0.6f, dark ? 0.14f : 0.05f, 0.01f, 22).fadeIn();
        }
        if (e.tickCount % 160 == 37 && RNG.nextInt(3) == 0) {
            mc.level.playLocalSound(core.x, core.y, core.z, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundSource.AMBIENT, 0.5f, 0.6f, false);
        }
    }
}
