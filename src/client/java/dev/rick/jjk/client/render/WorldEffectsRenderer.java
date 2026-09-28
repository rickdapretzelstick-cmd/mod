package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.fx.ClientFx;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.combat.Combat;
import dev.rick.jjk.core.combat.CombatStatus;
import dev.rick.jjk.core.net.DomainPayload;
import dev.rick.jjk.gojo.HollowPurpleAbility;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/** World-space effects not tied to an entity renderer: charge orbs in hands, domains, Infinity's shimmer. */
public final class WorldEffectsRenderer {
    private WorldEffectsRenderer() {}

    public static void render(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        Quaternionf camRot = ctx.levelState().cameraRenderState.orientation;
        SubmitNodeCollector c = ctx.submitNodeCollector();
        PoseStack ps = ctx.poseStack();
        long now = mc.level.getGameTime();

        for (Map.Entry<Integer, ClientState.Cast> e : ClientState.CASTS.entrySet()) {
            Entity ent = mc.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity user) || ent.isRemoved()) continue;
            renderCast(c, ps, cam, camRot, user, e.getValue(), now, partial);
        }
        for (ClientState.Domain d : ClientState.DOMAINS.values()) renderDomain(c, ps, cam, camRot, d, now, partial);
        renderInfinity(c, ps, cam, mc, partial);
        Flashes.render(c, ps, cam, camRot, now, partial);
    }

    // --- Casting visuals ---

    private static Vec3 hand(LivingEntity e, float partial, boolean left, double forward) {
        float yaw = e.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.75, 0).add(fwd.scale(forward)).add(right.scale(left ? -0.75 : 0.75));
    }

    private static Vec3 fingertip(LivingEntity e, float partial) {
        Vec3 look = e.getViewVector(partial);
        return e.getEyePosition(partial).add(look.scale(0.9)).add(0, -0.25, 0);
    }

    private static void renderCast(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, LivingEntity user, ClientState.Cast cast,
                                   long now, float partial) {
        float phaseAge = now - cast.phaseTick() + partial;
        float totalAge = now - cast.startTick() + partial;
        switch (cast.ability()) {
            case "blue" -> {
                if (cast.phase() == 0) orbAt(c, ps, cam, camRot, fingertip(user, partial), 0.08f + 0.04f * Math.min(1, totalAge / 5), ClientFx.BLUE, 1f);
            }
            case "max_blue" -> {
                if (cast.phase() == 0) {
                    float g = Math.min(1f, totalAge / Math.max(1, JJKConfig.get().maxBlue.startup));
                    Vec3 between = hand(user, partial, true, 0.9).lerp(hand(user, partial, false, 0.9), 0.5);
                    orbAt(c, ps, cam, camRot, between, 0.15f + 0.55f * g, ClientFx.BLUE, 1.3f);
                    push(ps, cam, between);
                    ps.rotate(com.mojang.math.Axis.YP.rotationDegrees(totalAge * 30));
                    ps.rotate(com.mojang.math.Axis.XP.rotationDegrees(60));
                    Glow.ring(c, ps, 0.6f + 1.2f * g, 0.18f, 0.5f, 0.75f, 1f, 0.7f);
                    ps.popPose();
                }
            }
            case "max_red" -> {
                float charge = Math.min(1f, totalAge / Math.max(1, JJKConfig.get().maxRed.maxCharge));
                float flicker = 0.85f + 0.3f * Mth.sin(totalAge * 2.9f);
                Vec3 tip = fingertip(user, partial);
                orbAt(c, ps, cam, camRot, tip, (0.15f + 0.4f * charge) * flicker, ClientFx.RED, cast.phase() == 1 ? 1.8f : 1.3f);
                push(ps, cam, tip);
                ps.rotate(camRot);
                Glow.halo(c, ps, new org.joml.Quaternionf(), 1.2f + 1.5f * charge, 1f, 0.35f, 0.15f, 0.25f);
                ps.popPose();
            }
            case "awaken" -> {
                Vec3 base = user.getPosition(partial);
                float f = Math.min(1f, totalAge / Math.max(1, JJKConfig.get().awakening.transitionTicks));
                for (int i = 0; i < 5; i++) {
                    float t = (totalAge * 0.08f + i / 5f) % 1f;
                    push(ps, cam, base.add(0, t * 3.2, 0));
                    ps.rotate(com.mojang.math.Axis.YP.rotationDegrees(totalAge * 12 + i * 70));
                    Glow.ring(c, ps, (1.8f - t * 1.2f) * (0.5f + f), 0.15f, 0.8f, 0.92f, 1f, 0.7f * (1 - t));
                    ps.popPose();
                }
                orbAt(c, ps, cam, camRot, base.add(0, user.getBbHeight() * 0.55, 0), 0.4f + 0.8f * f, ClientFx.BLUE_LIGHT, 0.6f);
            }
            case "red" -> {
                float charge = Math.min(1f, totalAge / Math.max(1, JJKConfig.get().red.maxCharge));
                float flicker = 0.85f + 0.3f * Mth.sin(totalAge * 2.3f);
                orbAt(c, ps, cam, camRot, fingertip(user, partial), (0.07f + 0.16f * charge) * flicker, ClientFx.RED, cast.phase() == 1 ? 1.5f : 1f);
            }
            case HollowPurpleAbility.ID -> renderPurpleCast(c, ps, cam, camRot, user, cast, phaseAge, totalAge, partial);
            case "unlimited_void" -> {
                Vec3 base = user.getPosition(partial);
                for (int i = 0; i < 3; i++) {
                    float t = (totalAge * 0.06f + i / 3f) % 1f;
                    push(ps, cam, base.add(0, t * 2.6, 0));
                    ps.rotate(Axis.YP.rotationDegrees(totalAge * 6 + i * 40));
                    Glow.ring(c, ps, 1.6f - t * 0.9f, 0.12f, 0.85f, 0.92f, 1f, 0.6f * (1 - t));
                    ps.popPose();
                }
                orbAt(c, ps, cam, camRot, hand(user, partial, false, 0.3).add(0, 0.2, 0), 0.07f, ClientFx.WHITE, 1.2f);
            }
            default -> {}
        }
    }

    private static void renderPurpleCast(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, LivingEntity user, ClientState.Cast cast,
                                         float phaseAge, float totalAge, float partial) {
        JJKConfig.Purple cfg = JJKConfig.get().purple;
        Vec3 left = hand(user, partial, true, 0.5);
        Vec3 right = hand(user, partial, false, 0.5);
        Vec3 front = user.getEyePosition(partial).add(user.getViewVector(partial).scale(1.3));
        switch (cast.phase()) {
            case HollowPurpleAbility.PHASE_BLUE -> {
                float g = Math.min(1f, phaseAge / cfg.blueFormTicks);
                orbAt(c, ps, cam, camRot, left, 0.08f + 0.2f * g, ClientFx.BLUE, 1f);
            }
            case HollowPurpleAbility.PHASE_RED -> {
                float g = Math.min(1f, phaseAge / cfg.redFormTicks);
                orbAt(c, ps, cam, camRot, left, 0.28f, ClientFx.BLUE, 1f);
                orbAt(c, ps, cam, camRot, right, 0.08f + 0.2f * g, ClientFx.RED, 1f);
            }
            case HollowPurpleAbility.PHASE_FUSION -> {
                // The two are brought together; as they touch, purple bleeds out of the collision.
                float f = Mth.clamp(phaseAge / cfg.fusionTicks, 0, 1);
                float ease = f * f * (3 - 2 * f);
                Vec3 lb = left.lerp(front, ease);
                Vec3 rr = right.lerp(front, ease);
                float spin = totalAge * 0.5f;
                Vec3 wob = new Vec3(Math.cos(spin), Math.sin(spin * 1.3), Math.sin(spin)).scale(0.1 * (1 - ease));
                orbAt(c, ps, cam, camRot, lb.add(wob), 0.28f * (1 - 0.4f * ease), ClientFx.BLUE, 1f);
                orbAt(c, ps, cam, camRot, rr.subtract(wob), 0.28f * (1 - 0.4f * ease), ClientFx.RED, 1f);
                if (ease > 0.6f) orbAt(c, ps, cam, camRot, front, 0.6f * (ease - 0.6f) / 0.4f, ClientFx.PURPLE, 1.3f);
            }
            case HollowPurpleAbility.PHASE_CHARGED -> {
                float g = Math.min(1f, phaseAge / Math.max(1, cfg.maxHoldTicks));
                float pulse = 1f + 0.1f * Mth.sin(totalAge * 1.1f);
                orbAt(c, ps, cam, camRot, front, (0.6f + 0.35f * g) * pulse, ClientFx.PURPLE, 1.4f);
                push(ps, cam, front);
                ps.rotate(Axis.YP.rotationDegrees(totalAge * 20));
                ps.rotate(Axis.XP.rotationDegrees(70));
                Glow.ring(c, ps, 1.3f + 0.4f * g, 0.2f, 0.8f, 0.55f, 1f, 0.6f);
                ps.popPose();
            }
            default -> {}
        }
    }

    private static void orbAt(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, Vec3 at, float radius, float[] color, float intensity) {
        push(ps, cam, at);
        Vector3f toCam = new Vector3f((float) (cam.x - at.x), (float) (cam.y - at.y), (float) (cam.z - at.z));
        Glow.orb(c, ps, camRot, toCam, radius, color, intensity);
        ps.popPose();
    }

    private static void push(PoseStack ps, Vec3 cam, Vec3 at) {
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
    }

    // --- Domains ---

    private static void renderDomain(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, long now, float partial) {
        float phaseAge = now - d.phaseStartTick + partial;
        float r = d.radius;
        float edgeGlow = 0.35f;
        switch (d.phase) {
            case DomainPayload.FORMING -> {
                float f = Mth.clamp(phaseAge / 20f, 0, 1);
                r *= 1 - (1 - f) * (1 - f) * (1 - f);
                edgeGlow = 1.2f;
            }
            case DomainPayload.COLLAPSING -> {
                float f = Mth.clamp(phaseAge / 20f, 0, 1);
                r *= 1 - f * f;
                edgeGlow = 1f;
            }
            case DomainPayload.CLASHING -> edgeGlow = 0.6f + 0.5f * Mth.sin(phaseAge * 1.7f) * Mth.sin(phaseAge * 0.37f);
            default -> {}
        }
        if (r < 0.2f) return;
        boolean inside = cam.distanceTo(d.center) < r;
        push(ps, cam, d.center);
        // The barrier: an infinite starfield enclosing the battlefield. Drawn double-sided so it reads from both sides.
        // Just inside the physical barrier so block faces never poke through the starfield.
        float rr = Math.max(0.5f, r - 0.35f);
        c.submitCustomGeometry(ps, RenderTypes.endPortal(), (pose, buf) -> sphereShell(pose, buf, rr, false));
        c.submitCustomGeometry(ps, RenderTypes.endPortal(), (pose, buf) -> sphereShell(pose, buf, rr, true));
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        // Bright edge where the barrier meets the world.
        Glow.sphere(c, ps, r * 0.995f, 0.8f, 0.9f, 1f, 0.35f * edgeGlow, toCam, true);
        if (inside) {
            // Information flowing through the void: slow rings of light sweeping around the center.
            float t = (now + partial) * 0.02f;
            for (int i = 0; i < 6; i++) {
                ps.pushPose();
                ps.rotate(Axis.YP.rotation(t * (1 + i * 0.15f) + i));
                ps.rotate(Axis.XP.rotation(0.4f + i * 0.45f + Mth.sin(t + i) * 0.2f));
                Glow.ring(c, ps, r * (0.55f + i * 0.07f), 0.25f, 0.8f, 0.9f, 1f, 0.22f);
                ps.popPose();
            }
            // A black hole hanging over the battlefield, with a burning accretion disc, and distant galaxies on the walls.
            Vec3 holeOffset = new Vec3(0, r * 0.5, 0);
            ps.pushPose();
            ps.translate(holeOffset.x, holeOffset.y, holeOffset.z);
            float hr = Math.max(1.2f, r * 0.08f);
            c.submitCustomGeometry(ps, RenderTypes.endGateway(), (pose, buf) -> sphereShell(pose, buf, hr, false));
            ps.rotate(com.mojang.math.Axis.XP.rotationDegrees(72));
            ps.rotate(com.mojang.math.Axis.YP.rotation(t * 3));
            for (int i = 0; i < 4; i++) Glow.ring(c, ps, hr * (1.5f + i * 0.45f), hr * 0.35f, 1f, 0.85f - i * 0.12f, 0.6f + i * 0.1f, 0.55f - i * 0.1f);
            ps.popPose();
            Vector3f holeToCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y - holeOffset.y), (float) (cam.z - d.center.z));
            ps.pushPose();
            ps.translate(0, holeOffset.y, 0);
            Glow.sphere(c, ps, hr * 1.3f, 0.6f, 0.75f, 1f, 0.4f, holeToCam, true);
            ps.popPose();
            java.util.Random rnd = new java.util.Random(d.id * 31L);
            for (int i = 0; i < 5; i++) {
                double yaw = rnd.nextDouble() * Math.PI * 2, pitch = 0.15 + rnd.nextDouble() * 0.9;
                Vec3 at = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)).scale(r * 0.9);
                ps.pushPose();
                ps.translate(at.x, at.y, at.z);
                ps.rotate(camRot);
                float gs = 1.5f + rnd.nextFloat() * 2.5f;
                float[] col = rnd.nextBoolean() ? new float[]{0.6f, 0.75f, 1f} : new float[]{0.85f, 0.7f, 1f};
                Glow.halo(c, ps, new org.joml.Quaternionf(), gs, col[0], col[1], col[2], 0.35f);
                ps.rotate(com.mojang.math.Axis.XP.rotationDegrees(90));
                ps.rotate(com.mojang.math.Axis.YP.rotation(t * (0.5f + i * 0.2f)));
                Glow.ring(c, ps, gs * 0.6f, gs * 0.25f, col[0], col[1], col[2], 0.35f);
                ps.popPose();
            }
            if (JJKConfig.get().domain.voidFloor) {
                // A reflective floor at the owner's feet so the ground reads as part of the void.
                ps.pushPose();
                ps.translate(0, -0.45f, 0);
                for (int i = 1; i <= 5; i++) Glow.ring(c, ps, r * i / 5.5f, 0.08f, 0.85f, 0.92f, 1f, 0.25f);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void sphereShell(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buf, float r, boolean inward) {
        final int lat = 16, lon = 28;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                float[][] v = {pt(r, t0, p0), pt(r, t1, p0), pt(r, t1, p1), pt(r, t0, p1)};
                if (inward) {
                    for (int k = 3; k >= 0; k--) buf.addVertex(pose, v[k][0], v[k][1], v[k][2]);
                } else {
                    for (int k = 0; k < 4; k++) buf.addVertex(pose, v[k][0], v[k][1], v[k][2]);
                }
            }
        }
    }

    /** Outward-facing sphere for position-only render types. */
    static void shell(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buf, float r) {
        sphereShell(pose, buf, r, false);
    }

    private static float[] pt(float r, float theta, float phi) {
        return new float[]{r * Mth.sin(theta) * Mth.cos(phi), r * Mth.cos(theta), r * Mth.sin(theta) * Mth.sin(phi)};
    }

    // --- Infinity ---

    private static void renderInfinity(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Minecraft mc, float partial) {
        if (JJKConfig.get().client.particleQuality == 0) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !Combat.has(le, CombatStatus.INFINITY)) continue;
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 center = le.getPosition(partial).add(0, le.getBbHeight() / 2, 0);
            if (center.distanceToSqr(cam) > 48 * 48) continue;
            push(ps, cam, center);
            Vector3f toCam = new Vector3f((float) (cam.x - center.x), (float) (cam.y - center.y), (float) (cam.z - center.z));
            float shimmer = 0.05f + 0.025f * Mth.sin((le.tickCount + partial) * 0.2f);
            // A barely-there lens around the body: space that doesn't quite let you in.
            ps.scale(1f, le.getBbHeight() / 1.6f, 1f);
            Glow.sphere(c, ps, 1.05f, 0.7f, 0.85f, 1f, shimmer, toCam, true);
            ps.popPose();
        }
    }
}
