package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.model.BbModel;
import dev.rick.jjk.client.model.BbModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The inside of Authentic Mutual Love, after the JJS GIF: a pale stone platform (the domain's floor blocks) under a
 * black sky with a purple sheen high up; grave crosses rising from the ground all around; thick rope tied in loose
 * knots circling overhead, their love; and Rika, enormous and grey, looming in the dark behind Yuta.
 */
public final class MutualLoveDomainRenderer {
    private static final Map<Integer, Float> FACING = new HashMap<>();
    private static final net.minecraft.client.renderer.rendertype.RenderType GHOST =
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(RikaRenderer.TEXTURE);

    private MutualLoveDomainRenderer() {}

    private static float facing(ClientState.Domain d, ClientLevel level) {
        return FACING.computeIfAbsent(d.id, k -> {
            if (FACING.size() > 32) FACING.clear();
            Entity owner = level.getEntity(d.ownerId);
            return owner != null ? owner.getYRot() : 0f;
        });
    }

    /** Called with the pose at the domain's center. */
    static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, float r, float edgeGlow,
                       boolean inside, long now, float partial) {
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        // From outside: a black dome with a pink rim.
        if (edgeGlow > 0) Glow.sphere(c, ps, r * 0.995f, 0.97f, 0.42f, 1f, 0.22f * edgeGlow, toCam, true);
        if (!inside) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float time = now + partial;
        // The purple sheen at the top of the black sky.
        ps.pushPose();
        ps.translate(0, r * 0.75f, 0);
        ps.scale(1, 0.25f, 1);
        Glow.sphere(c, ps, r * 0.7f, 0.45f, 0.1f, 0.5f, 0.18f, new Vector3f(0, -50, 0), false);
        ps.popPose();
        crosses(c, ps, d, r);
        ropes(c, ps, d, r, time);
        rika(c, ps, d, r, level, time);
    }

    /** Grave crosses scattered over the platform, leaning a little, none right by the center. */
    private static void crosses(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, float r) {
        Random rnd = new Random(d.id * 131L);
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double dist = r * (0.35 + rnd.nextDouble() * 0.5);
            Vec3 at = new Vec3(Math.cos(a) * dist, -0.5, Math.sin(a) * dist);
            if (!DomainSpace.onSide(d, d.center.add(at))) continue;
            float scale = 1.2f + rnd.nextFloat() * 1.3f;
            float lean = (rnd.nextFloat() - 0.5f) * 14f, turn = rnd.nextFloat() * 360f;
            ps.pushPose();
            ps.translate(at.x, at.y, at.z);
            ps.rotate(Axis.YP.rotationDegrees(turn));
            ps.rotate(Axis.ZP.rotationDegrees(lean));
            ps.scale(scale, scale, scale);
            c.submitCustomGeometry(ps, YutaGear.TYPE, (pose, buf) -> {
                YutaGear.box(pose, buf, -1.2f, 0, -1.2f, 1.2f, 30, 1.2f, YutaGear.RING, 0xF000F0);
                YutaGear.box(pose, buf, -8f, 19, -1.2f, 8f, 22.5f, 1.2f, YutaGear.RING, 0xF000F0);
                YutaGear.box(pose, buf, -2f, 0, -2f, 2f, 2f, 2f, YutaGear.CHAIN, 0xF000F0);
            });
            ps.popPose();
        }
    }

    /** Thick rope circling the sky, tied in loose loops, slowly turning. */
    private static void ropes(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, float r, float time) {
        float h = r * 0.55f, rr = r * 0.8f;
        int segs = 64;
        float spin = time * 0.0015f;
        c.submitCustomGeometry(ps, YutaGear.TYPE, (pose, buf) -> {
            for (int i = 0; i < segs; i++) {
                double a = spin + i * Math.PI * 2 / segs;
                double wave = Math.sin(a * 5) * 1.6;
                float x = (float) (Math.cos(a) * rr), z = (float) (Math.sin(a) * rr), y = (float) (h + wave);
                PoseStack.Pose p = pose.copy();
                p.translate(x, y, z);
                p.rotate(Axis.YP.rotation((float) -a));
                YutaGear.box(p, buf, -3, -3, -9, 3, 3, 9, YutaGear.ROPE, 0xF000F0);
                // Every few segments a knot: a loose loop hanging off the rope.
                if (i % 8 == 0) {
                    for (int k = 0; k < 10; k++) {
                        double t = k * Math.PI * 2 / 10;
                        PoseStack.Pose q = p.copy();
                        q.translate(0, (float) (Math.sin(t) * 1.1 - 1.1), (float) (Math.cos(t) * 1.1));
                        YutaGear.box(q, buf, -2.5f, -2.5f, -2.5f, 2.5f, 2.5f, 2.5f, YutaGear.ROPE, 0xF000F0);
                    }
                }
            }
        });
    }

    /** Rika, enormous and grey, looming in the dark behind where Yuta faced as he opened it. */
    private static void rika(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, float r, ClientLevel level, float time) {
        BbModel model = BbModels.get("rika");
        if (model == null) return;
        float yaw = facing(d, level);
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        float back = r * 0.7f;
        if (!DomainSpace.onSide(d, d.center.add(-look.x * back, 0, -look.z * back))) return;
        ps.pushPose();
        ps.translate(-look.x * back, -1 + Mth.sin(time * 0.02f) * 0.4f, -look.z * back);
        ps.rotate(Axis.YP.rotationDegrees(180f - yaw));
        float s = RikaRenderer.SCALE * Mth.clamp(r / 6f, 1.5f, 4f);
        ps.scale(s, s, s);
        c.submitCustomGeometry(ps, GHOST,
                (pose, buf) -> model.render(pose, buf, BbModel.Posing.REST, 0xF000F0, 0xB8DCDCE4));
        ps.popPose();
    }
}
