package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.domain.structure.DomainFormation;
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
        renderClashFront(c, ps, cam, camRot, now, partial);
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
            case "rough_energy" -> {
                if (cast.phase() == 0) {
                    // Coarse cursed energy packing around the cocked fist.
                    float f = Math.min(1f, totalAge / Math.max(1, JJKConfig.get().hakari.roughWindup));
                    Vec3 fist = hand(user, partial, false, -0.2).add(0, -0.2, 0);
                    orbAt(c, ps, cam, camRot, fist, 0.12f + 0.3f * f, new float[]{0.36f, 1f, 0.66f}, 0.8f + f);
                    push(ps, cam, fist);
                    ps.rotate(camRot);
                    Glow.spikes(c, ps, 7, 0.1f, 0.35f + 0.5f * f, 0.05f, 0.8f, 1f, 0.9f, 0.8f, (long) (totalAge * 0.7f));
                    ps.popPose();
                }
            }
            case "idle_death_gamble" -> {
                Vec3 base = user.getPosition(partial);
                for (int i = 0; i < 4; i++) {
                    float t = (totalAge * 0.07f + i / 4f) % 1f;
                    push(ps, cam, base.add(0, t * 2.8, 0));
                    ps.rotate(Axis.YP.rotationDegrees(-totalAge * 9 + i * 45));
                    // Soft white, like the smoke gathering around the hand sign in Jujutsu Shenanigans.
                    Glow.ring(c, ps, 1.7f - t * 0.9f, 0.13f, 1f, 1f, 1f, 0.45f * (1 - t));
                    ps.popPose();
                }
            }
            case "rhythm" -> {
                // Beat rings pulsing out from his feet with the music.
                Vec3 base = user.getPosition(partial).add(0, 0.05, 0);
                float beat = (totalAge - JJKConfig.get().hakari.rhythmLeadIn) / Math.max(1, JJKConfig.get().hakari.rhythmBeatTicks);
                float fr = beat - (float) Math.floor(beat);
                if (beat > -0.5f) {
                    push(ps, cam, base);
                    Glow.ring(c, ps, 0.4f + fr * 1.8f, 0.12f, 1f, 0.8f, 0.25f, 0.8f * (1 - fr));
                    ps.popPose();
                }
            }
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
                // The opposites repel as they meet: jagged arcs crackling between them, more violent the closer they get.
                java.util.Random arc = new java.util.Random((long) (totalAge * 2));
                for (int k = 0; k < 1 + (int) (ease * 3); k++) {
                    Vec3 a = lb.add(wob), b = rr.subtract(wob);
                    Vec3 prev = a;
                    for (int i = 1; i <= 5; i++) {
                        Vec3 next = a.lerp(b, i / 5.0);
                        if (i < 5) next = next.add((arc.nextDouble() - 0.5) * 0.3, (arc.nextDouble() - 0.5) * 0.3, (arc.nextDouble() - 0.5) * 0.3);
                        segment(c, ps, cam, prev, next, 0.03f + 0.03f * ease, k % 2 == 0 ? ClientFx.PURPLE_LIGHT : ClientFx.WHITE, 0.9f);
                        prev = next;
                    }
                }
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

    /**
     * The newest edge of a forming domain, where the blocks are being laid right now: a ring racing outward across the
     * ground from the caster's feet, then climbing the wall and curving over into the ceiling, then sealing underneath.
     * Drawn on both faces of the shell so it reads from inside and outside.
     */
    private static void formationEdge(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, float p, float time) {
        float R = d.radius + d.thickness;
        float flicker = 0.85f + 0.15f * Mth.sin(time * 2.3f);
        float floorY = -0.45f;
        if (p < DomainFormation.GROUND_END) {
            float g = p / DomainFormation.GROUND_END;
            float rad = Math.max(0.4f, R * g);
            ps.pushPose();
            ps.translate(0, floorY, 0);
            Glow.ring(c, ps, rad, 1.4f, 0.55f, 0.8f, 1f, 0.9f * flicker);
            Glow.ring(c, ps, rad, 0.35f, 1f, 1f, 1f, 1f);
            // Energy still pouring out of the sorcerer at the centre.
            Glow.ring(c, ps, 0.9f + 0.3f * Mth.sin(time), 0.6f, 1f, 1f, 1f, 0.8f * (1 - g));
            Glow.spikes(c, ps, 10, rad - 0.4f, rad + 1.6f, 0.08f, 0.8f, 0.92f, 1f, 0.6f, (long) (time * 0.7f));
            ps.popPose();
            return;
        }
        if (p < DomainFormation.CEILING_END) {
            double a = DomainFormation.wallAngle(p);
            float y = (float) (R * Math.sin(a)), rad = (float) (R * Math.cos(a));
            ps.pushPose();
            ps.translate(0, y, 0);
            float inner = Math.max(0.3f, rad - d.thickness - 0.6f);
            Glow.ring(c, ps, rad + 0.9f, 2.4f, 0.55f, 0.8f, 1f, 1f * flicker);
            Glow.ring(c, ps, rad + 0.9f, 0.5f, 1f, 1f, 1f, 1f);
            Glow.ring(c, ps, inner, 2.4f, 0.55f, 0.8f, 1f, 1f * flicker);
            Glow.ring(c, ps, inner, 0.5f, 1f, 1f, 1f, 1f);
            Glow.spikes(c, ps, 14, rad - 1f, rad + 2.5f, 0.12f, 0.8f, 0.92f, 1f, 0.7f, (long) (time * 0.7f));
            ps.popPose();
            // The ground ring it rose from keeps humming.
            ps.pushPose();
            ps.translate(0, floorY, 0);
            Glow.ring(c, ps, R + 0.6f, 0.8f, 0.5f, 0.75f, 1f, 0.4f);
            ps.popPose();
            return;
        }
        double a = DomainFormation.underAngle(p);
        float y = (float) (-R * Math.sin(a)), rad = (float) (R * Math.cos(a));
        ps.pushPose();
        ps.translate(0, y, 0);
        Glow.ring(c, ps, rad + 0.7f, 1.2f, 0.55f, 0.8f, 1f, 0.8f * flicker);
        ps.popPose();
        ps.pushPose();
        ps.translate(0, floorY, 0);
        Glow.ring(c, ps, R + 0.6f, 1.2f, 1f, 1f, 1f, 0.7f * flicker);
        ps.popPose();
    }

    /**
     * Where two clashing domains meet: the boundary between the two interiors (a real wall of blocks the server moves
     * with the clash), crackling with colliding energy and harder the better both sides are playing. A PERFECT sends a
     * pulse of the player's colour from their side across the floor into the boundary, shoving it.
     */
    private static void renderClashFront(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, long now, float partial) {
        dev.rick.jjk.client.clash.ClashClient.View v = dev.rick.jjk.client.clash.ClashClient.view();
        if (v == null || v.domains.length < 2) return;
        ClientState.Domain a = ClientState.DOMAINS.get(v.domains[0]), b = ClientState.DOMAINS.get(v.domains[1]);
        if (a == null || b == null || a.center == null || b.center == null) return;
        Vec3 ab = b.center.subtract(a.center);
        double dist = ab.length();
        if (dist < 0.1) return;
        Vec3 n = ab.scale(1 / dist);
        // The boundary as the server has it (the blocks move with it); the meter if the split isn't known yet.
        float t = a.splitWith == b.id ? a.splitShown : 0.5f + v.shownMeter * 0.35f;
        Vec3 front = a.center.add(ab.scale(t)).add(0, 1.5, 0);
        float size = Math.max(3f, Math.min(a.radius, b.radius) * 0.85f);
        float heat = Math.min(4f, v.heat[0] + v.heat[1]);
        float time = now + partial;
        float[] ca = rgb(v.colors[0]), cb = rgb(v.colors[1]);
        push(ps, cam, front);
        Flashes.orientY(ps, n);
        // Two walls of energy pressing into each other, with waves rolling out from the contact.
        ps.pushPose();
        ps.translate(0, -0.35f, 0);
        Glow.ring(c, ps, size * (0.96f + 0.03f * Mth.sin(time * 0.9f)), size * 0.35f, ca[0], ca[1], ca[2], 0.35f + 0.1f * heat);
        ps.popPose();
        ps.pushPose();
        ps.translate(0, 0.35f, 0);
        Glow.ring(c, ps, size * (0.96f + 0.03f * Mth.cos(time * 1.1f)), size * 0.35f, cb[0], cb[1], cb[2], 0.35f + 0.1f * heat);
        ps.popPose();
        for (int i = 0; i < 3; i++) {
            float w = ((time * (0.06f + 0.02f * heat)) + i / 3f) % 1f;
            Glow.ring(c, ps, size * w, 0.4f + size * 0.05f, 1f, 1f, 1f, (1 - w) * (0.35f + 0.1f * heat));
        }
        Glow.spikes(c, ps, 6 + Math.round(heat * 3), 0.3f, size * 0.9f, 0.12f, 1f, 1f, 1f, 0.6f + 0.1f * heat, (long) (time * 0.5f));
        ps.popPose();
        push(ps, cam, front);
        Glow.halo(c, ps, camRot, 2.5f + heat, 1f, 1f, 1f, 0.5f + 0.1f * heat);
        ps.popPose();
        // PERFECT pulses: a ring of the side's colour races from its owner's side to the boundary and slams into it.
        ClientState.Domain[] ds = {a, b};
        float[][] cols = {ca, cb};
        for (int i = 0; i < 2; i++) {
            ClientState.Domain d = ds[i];
            if (d.pulseTick == Long.MIN_VALUE) continue;
            float pa = now - d.pulseTick + partial;
            if (pa < 0 || pa >= 14) continue;
            Vec3 from = d.center.add(0, 1.5, 0);
            float travel = Mth.clamp(pa / 8f, 0, 1);
            Vec3 at = from.add(front.subtract(from).scale(travel * travel * (3 - 2 * travel)));
            float fade = pa < 8 ? 1 : 1 - (pa - 8) / 6f;
            push(ps, cam, at);
            Flashes.orientY(ps, i == 0 ? n : n.scale(-1));
            float ring = size * (0.35f + 0.45f * travel);
            Glow.ring(c, ps, ring, 0.6f + 0.4f * d.pulseStrength, cols[i][0], cols[i][1], cols[i][2], 0.8f * fade);
            Glow.ring(c, ps, ring * 0.92f, 0.25f, 1f, 1f, 1f, 0.9f * fade);
            ps.popPose();
            // The impact: the boundary flares on arrival.
            if (pa >= 8) {
                push(ps, cam, front);
                Glow.halo(c, ps, camRot, size * 0.7f * (0.6f + 0.4f * d.pulseStrength), cols[i][0], cols[i][1], cols[i][2], 0.6f * fade);
                ps.popPose();
            }
        }
    }

    private static float[] rgb(int c) {
        return new float[] {(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    private static void segment(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Vec3 a, Vec3 b, float width, float[] col, float alpha) {
        Vec3 d = b.subtract(a);
        float len = (float) d.length();
        if (len < 1e-3) return;
        Vec3 n = d.scale(1 / len);
        push(ps, cam, a);
        ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
        ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
        Glow.beam(c, ps, len, width, col[0], col[1], col[2], alpha);
        ps.popPose();
    }

    private static void push(PoseStack ps, Vec3 cam, Vec3 at) {
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
    }

    // --- Domains ---

    private static void renderDomain(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, long now, float partial) {
        DomainSpace.ease(d);
        // Consumed by the winner of a clash: its space is already the winner's.
        if (DomainSpace.consumed(d)) return;
        float phaseAge = now - d.phaseStartTick + partial;
        float r = d.radius;
        float edgeGlow = 0.35f;
        // Formation progress (0..1): the real blocks build on the same schedule (DomainFormation).
        float progress = d.phase == DomainPayload.FORMING ? Mth.clamp(phaseAge / d.formationTicks, 0, 1) : 1f;
        switch (d.phase) {
            case DomainPayload.FORMING -> edgeGlow = 1.2f;
            case DomainPayload.COLLAPSING -> {
                float f = Mth.clamp(phaseAge / 20f, 0, 1);
                r *= 1 - f * f;
                edgeGlow = 1f;
            }
            case DomainPayload.CLASHING -> edgeGlow = 0.6f + 0.5f * Mth.sin(phaseAge * 1.7f) * Mth.sin(phaseAge * 0.37f);
            default -> {}
        }
        if (r < 0.2f) return;
        // Clash feedback: a perfect input makes the whole domain flare; a miss makes it flicker and dim.
        float pulse = 0f;
        if (d.pulseTick != Long.MIN_VALUE) {
            float pa = now - d.pulseTick + partial;
            if (pa >= 0 && pa < 14) pulse = (1 - pa / 14f) * d.pulseStrength;
        }
        edgeGlow += pulse * 0.9f;
        if (now < d.unstableUntil) edgeGlow *= 0.35f + 0.65f * Math.abs(Mth.sin((now + partial) * 4.7f));
        // Split in a clash, or grown by conquered territory: the interior covers its part of more than one sphere.
        boolean shared = d.phase != DomainPayload.COLLAPSING && (DomainSpace.split(d) || d.annex.length > 0);
        boolean inside = shared ? DomainSpace.holds(d, cam) : cam.distanceTo(d.center) < r;
        push(ps, cam, d.center);
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        if (progress < 1f) {
            // Idle Death Gamble floods the ground white instead (and the sky with it as it seals).
            if (dev.rick.jjk.hakari.IdleDeathGamble.ID.equals(d.definition)) GambleDomainRenderer.formation(d, progress, now);
            else formationEdge(c, ps, d, progress, now + partial);
        }
        // Until the ceiling has closed the sky is still visible: the void only replaces it once it is sealed over.
        if (progress < DomainFormation.CEILING_END) {
            ps.popPose();
            return;
        }
        // Every domain has its own interior: Idle Death Gamble is a casino, not a void.
        if (dev.rick.jjk.hakari.IdleDeathGamble.ID.equals(d.definition)) {
            boolean sealed = progress >= DomainFormation.SEALED;
            GambleDomainRenderer.render(c, ps, cam, camRot, d, r, shared ? 0 : edgeGlow, inside && sealed, now, partial);
            ps.popPose();
            return;
        }
        // The barrier: an infinite starfield enclosing the battlefield. Drawn double-sided so it reads from both sides.
        // Just inside the physical barrier so block faces never poke through the starfield.
        float rr = Math.max(0.5f, r - 0.35f);
        if (shared) {
            // Only this domain's side, across every sphere of its space (its own, the rival's it is winning into, conquests).
            var balls = DomainSpace.balls(d);
            for (int k = 0; k < balls.size(); k++) {
                int kk = k;
                Vec3 bc = balls.get(k).center();
                float br = Math.max(0.5f, balls.get(k).radius() - 0.35f);
                ps.pushPose();
                ps.translate(bc.x - d.center.x, bc.y - d.center.y, bc.z - d.center.z);
                c.submitCustomGeometry(ps, RenderTypes.endPortal(), (pose, buf) -> DomainSpace.shell(pose, buf, d, balls, kk, br, false));
                c.submitCustomGeometry(ps, RenderTypes.endPortal(), (pose, buf) -> DomainSpace.shell(pose, buf, d, balls, kk, br, true));
                ps.popPose();
            }
        } else {
            c.submitCustomGeometry(ps, RenderTypes.endPortal(), (pose, buf) -> sphereShell(pose, buf, rr, false));
            c.submitCustomGeometry(ps, RenderTypes.endPortal(), (pose, buf) -> sphereShell(pose, buf, rr, true));
        }
        // Mid-clash the void's wide washes of light are toned down, so they don't haze over the lanes.
        float calm = dev.rick.jjk.client.clash.ClashFocus.active() ? 0.35f : 1f;
        // Bright edge where the barrier meets the world (a whole sphere, so not while the space is shared).
        if (!shared) Glow.sphere(c, ps, r * 0.995f, 0.8f, 0.9f, 1f, 0.35f * edgeGlow * calm, toCam, true);
        if (progress < DomainFormation.SEALED) inside = false;
        if (inside) {
            // Information flowing through the void: slow rings of light sweeping around the center.
            float t = (now + partial) * 0.02f;
            for (int i = 0; i < (DomainSpace.split(d) ? 0 : 6); i++) {
                ps.pushPose();
                ps.rotate(Axis.YP.rotation(t * (1 + i * 0.15f) + i));
                ps.rotate(Axis.XP.rotation(0.4f + i * 0.45f + Mth.sin(t + i) * 0.2f));
                Glow.ring(c, ps, r * (0.55f + i * 0.07f), 0.25f, 0.8f, 0.9f, 1f, 0.22f * (1 + pulse) * calm);
                ps.popPose();
            }
            // A black hole hanging over the battlefield, with a burning accretion disc, and distant galaxies on the walls.
            // Nebula haze: vast, faint clouds of blue and violet so the void has depth rather than flat black.
            java.util.Random neb = new java.util.Random(d.id * 17L);
            for (int i = 0; i < 6; i++) {
                double yaw = neb.nextDouble() * Math.PI * 2, pitch = 0.1 + neb.nextDouble() * 1.1;
                Vec3 at = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)).scale(r * 0.85);
                if (!DomainSpace.onSide(d, d.center.add(at))) continue;
                ps.pushPose();
                ps.translate(at.x, at.y, at.z);
                boolean violet = i % 2 == 1;
                Glow.halo(c, ps, camRot, r * (0.35f + neb.nextFloat() * 0.25f), violet ? 0.45f : 0.2f, violet ? 0.25f : 0.35f, 1f, 0.1f * calm);
                ps.popPose();
            }
            Vec3 holeOffset = new Vec3(0, r * 0.42, 0);
            boolean holeShown = DomainSpace.onSide(d, d.center.add(holeOffset));
            if (holeShown) {
            ps.pushPose();
            ps.translate(holeOffset.x, holeOffset.y, holeOffset.z);
            float hr = Math.max(1.8f, r * 0.11f);
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
            }
            java.util.Random rnd = new java.util.Random(d.id * 31L);
            for (int i = 0; i < 5; i++) {
                double yaw = rnd.nextDouble() * Math.PI * 2, pitch = 0.15 + rnd.nextDouble() * 0.9;
                Vec3 at = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)).scale(r * 0.9);
                float gs = 1.5f + rnd.nextFloat() * 2.5f;
                float[] col = rnd.nextBoolean() ? new float[]{0.6f, 0.75f, 1f} : new float[]{0.85f, 0.7f, 1f};
                if (!DomainSpace.onSide(d, d.center.add(at))) continue;
                ps.pushPose();
                ps.translate(at.x, at.y, at.z);
                ps.rotate(camRot);
                Glow.halo(c, ps, new org.joml.Quaternionf(), gs, col[0], col[1], col[2], 0.35f);
                ps.rotate(com.mojang.math.Axis.XP.rotationDegrees(90));
                ps.rotate(com.mojang.math.Axis.YP.rotation(t * (0.5f + i * 0.2f)));
                Glow.ring(c, ps, gs * 0.6f, gs * 0.25f, col[0], col[1], col[2], 0.35f);
                ps.popPose();
            }
            if (JJKConfig.get().domain.voidFloor && !DomainSpace.split(d)) {
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
