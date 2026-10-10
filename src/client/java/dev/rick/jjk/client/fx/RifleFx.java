package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.ClientProgression;
import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.ClientBeam;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.client.render.RifleBeams;
import dev.rick.jjk.client.rifle.RifleClient;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static dev.rick.jjk.client.fx.ClientFx.RNG;
import static dev.rick.jjk.client.fx.ClientFx.add;
import static dev.rick.jjk.client.fx.ClientFx.burst;
import static dev.rick.jjk.client.fx.ClientFx.implode;
import static dev.rick.jjk.client.fx.ClientFx.q;
import static dev.rick.jjk.client.fx.ClientFx.randomUnit;
import static dev.rick.jjk.client.fx.ClientFx.sparks;

/**
 * The Cursed Rifle's effects ({@code rifle_*}) and the hunting lodge's ({@code lodge_*}, {@code rack_*},
 * {@code stalker_*}).
 * <ul>
 *   <li>A normal shot: a muzzle flash and a thin violet tracer to exactly where the server says it struck, the
 *   normal_shot clip on the rifle, a kick of the view for the shooter.</li>
 *   <li>The beam's charge: energy drawn into the four lenses and the aperture, building with the charge; ready, a ring
 *   and a steady glow. The beam itself is {@link RifleBeams} (the shared square torrent), from the numbers the server
 *   hits with; its pulses throw debris where it strikes.</li>
 *   <li>The lodge: what the scope shows (a crooked trail through the trees and a figure standing on it, faint until the
 *   aim has been held), the rack's seal breaking, the stalker's eyes before it lunges.</li>
 * </ul>
 */
final class RifleFx {
    static final float[] VIOLET = {0.55f, 0.42f, 1f};
    static final float[] PALE = {0.82f, 0.86f, 1f};
    static final float[] WHITE = {1f, 1f, 1f};
    static final float[] SMOKE = {0.32f, 0.32f, 0.36f};
    static final float[] SHADE = {0.04f, 0.03f, 0.05f};
    static final float[] EYE = {1f, 0.12f, 0.08f};
    static final float[] SOUL = {0.35f, 0.85f, 0.9f};
    private static final Map<Integer, double[]> SHAPES = new HashMap<>();

    private RifleFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = ClientFx.lod > 0;
        Vec3 d = dir.lengthSqr() > 1e-6 ? dir.normalize() : new Vec3(0, 0, 1);
        int id = p.entityId();
        switch (p.id()) {
            // --- Normal shots ---
            case "rifle_shot" -> {
                RifleClient.shot(id);
                Vec3 end = pos.add(dir);
                if (drawn) {
                    Flashes.flash(pos, 0.25f, 0.9f, PALE, 1f, 3, now);
                    Flashes.flash(pos, 0.15f, 0.5f, WHITE, 1f, 2, now);
                    Flashes.beam(pos, end, 0.05f, VIOLET, 0.9f, 4, now);
                    Flashes.beam(pos, end, 0.02f, WHITE, 1f, 2, now);
                    sparks(level, pos, d, q(5), 0.35, PALE, 0.05f, 4);
                    burst(level, pos.add(d.scale(0.3)), q(3), 0.04, Sprite.SMOKE, SMOKE, 0.35f, 14);
                    if (s > 0) {
                        Flashes.flash(end, 0.2f, 0.8f, VIOLET, 0.9f, 4, now);
                        sparks(level, end, d.scale(-1), q(6), 0.3, VIOLET, 0.05f, 6);
                    }
                }
                if (mine) {
                    ScreenEffects.shake(0.12f, 3);
                    ScreenEffects.fovPunch(0.015f);
                }
            }
            case "rifle_impact" -> {
                if (drawn) {
                    Flashes.flash(pos, 0.15f, 0.6f, PALE, 0.9f, 3, now);
                    sparks(level, pos, d, q(8), 0.3, PALE, 0.05f, 6);
                    ClientFx.debris(level, pos.add(0, 0.6, 0), q(3), 0.25);
                    burst(level, pos, q(3), 0.03, Sprite.SMOKE, SMOKE, 0.3f, 16);
                }
            }
            // --- Lens Flare: an arm's lens throws a blinding flash where the aim is ---
            case "rifle_flare" -> {
                Vec3 from = muzzle(id, pos), end = pos.add(dir);
                if (drawn) {
                    Flashes.flash(from, 0.3f, 1.2f, WHITE, 1f, 4, now);
                    Flashes.ring(from, 0.15f, 0.9f, PALE, 0.8f, 5, now);
                    Flashes.beam(from, end, 0.12f, PALE, 0.5f, 4, now);
                    Flashes.beam(from, end, 0.04f, WHITE, 0.9f, 3, now);
                    Flashes.flash(end, 1.2f, 4.5f, WHITE, 1f, 8, now);
                    Flashes.ring(end, 0.6f, 4f, PALE, 0.7f, 8, now);
                    sparks(level, end, d.scale(-1), q(10), 0.4, WHITE, 0.06f, 6);
                }
                if (mc.player != null && mc.player.getId() != id && mc.player.getEyePosition().distanceTo(end) < 5) ScreenEffects.flash(0xB0FFFFFF, 10);
            }
            // --- The beam's charge ---
            case "rifle_charge" -> {
                if (!drawn) return;
                float k = Mth.clamp(s, 0, 1);
                Vec3 at = muzzle(id, pos);
                Vec3[] lenses = lenses(at, d, 0.22);
                for (Vec3 l : lenses) {
                    implode(level, l, 0.6 + 0.3 * (1 - k), q(2 + Math.round(4 * k)), k > 0.7f ? WHITE : RifleBeams.colour(RifleClient.orIdle(id).output), 0.05f + 0.04f * k, 7);
                }
                implode(level, at, 1.2, q(3 + Math.round(8 * k)), k > 0.6f ? WHITE : PALE, 0.07f + 0.05f * k, 9);
                Flashes.flash(at, 0.1f + 0.3f * k, 0.25f + 0.6f * k, PALE, 0.4f + 0.4f * k, 4, now);
                if (k > 0.5f && RNG.nextInt(3) == 0) Flashes.bolt(lenses[RNG.nextInt(4)], at, 0.03f, WHITE, 0.9f, 2, now);
            }
            case "rifle_ready" -> {
                Vec3 at = muzzle(id, pos);
                float[] c = RifleBeams.colour(s);
                if (drawn) {
                    Flashes.flash(at, 0.4f, 1.6f, c, 0.9f, 6, now);
                    Flashes.ring(at, 0.3f, 1.6f, WHITE, 0.7f, 6, now);
                    Flashes.ripple(at, d, 0.2f, 1.4f, c, 0.6f, 7, now);
                }
                if (mine) ScreenEffects.fovPunch(-0.02f);
            }
            // --- The beam (the same numbers as the server's) ---
            case "rifle_beam_shape" -> SHAPES.put(id, new double[] {dir.x, dir.y, dir.z, s});
            case "rifle_beam_fire" -> {
                double reach = dir.length();
                int hold = Math.max(2, (int) s);
                double[] shape = SHAPES.remove(id);
                double half = shape != null ? shape[0] : 1.1;
                int grow = shape != null ? (int) shape[1] : 3, collapse = shape != null ? (int) shape[2] : 8;
                float output = shape != null ? (float) shape[3] : 0.45f;
                Vec3 from = muzzle(id, pos);
                RifleBeams.start(id, new ClientBeam(from, d, reach, half, grow, collapse, hold, now, false), output);
                float[] c = RifleBeams.colour(output);
                if (drawn) {
                    Flashes.flash(from, 1f, 2.5f + 2f * output, c, 1f, 10, now);
                    Flashes.flash(from, 0.6f, 1.2f + output, WHITE, 1f, 7, now);
                    Flashes.ripple(from, d, 0.5f, 3f + 3f * output, c, 0.9f, 10, now);
                    Flashes.ring(from, 0.6f, 4f + 6f * output, WHITE, 0.6f, 9, now);
                    for (int i = 0; i < 6; i++) Flashes.bolt(from, from.add(randomUnit().scale(1 + 1.5 * output)).add(d.scale(1.2)), 0.05f, WHITE, 1f, 4, now + i / 2);
                    burst(level, from, q(12), 0.2, Sprite.SMOKE, SMOKE, 0.7f, 18);
                }
                if (mc.player != null && mc.player.position().distanceTo(from) < 16) ScreenEffects.flash(output >= 0.99f ? 0x40FFA040 : 0x30E07020, 6);
                ClientFx.distanceShake(from, 40 + 40 * output, 0.6f + output * 0.6f);
            }
            case "rifle_beam_pulse" -> {
                ClientBeam b = RifleBeams.get(id);
                if (b == null) return;
                b.strike = pos;
                double[] st = RifleBeams.state(id, now, 0);
                if (st == null || st[1] <= 0.05) return;
                boolean capped = st[0] + 0.5 < pos.distanceTo(b.origin);
                boolean surge = ((int) s) % 8 == 0;
                if (surge) b.surgeAt = now;
                if (capped || !drawn) return;
                float[] c = RifleBeams.colour(RifleBeams.output(id));
                ClientFx.debris(level, pos, q(surge ? 8 : 3), surge ? 0.7 : 0.35);
                sparks(level, pos, d.scale(-1), q(surge ? 12 : 5), 0.6, RNG.nextBoolean() ? WHITE : c, 0.08f, 9);
                burst(level, pos, q(3), 0.2, Sprite.SMOKE, SMOKE, 0.9f, 16);
                if (surge) {
                    // The impact flare: a hard white burst at the strike, then the beam's colour.
                    Flashes.flash(pos, 1.2f, 3.5f * (float) st[1], c, 0.9f, 6, now);
                    Flashes.flash(pos, 0.8f, 1.6f, WHITE, 0.9f, 4, now);
                    if (mc.player != null && mc.player.position().distanceTo(pos) < 12) ScreenEffects.shake(0.3f, 4);
                }
            }
            case "rifle_beam_hold" -> {
                ClientBeam b = RifleBeams.get(id);
                if (b != null) b.held = true;
            }
            case "rifle_beam_reaim" -> {
                ClientBeam b = RifleBeams.get(id);
                if (b != null && d.lengthSqr() > 1e-6) b.dir = d;
            }
            case "rifle_beam_release" -> {
                ClientBeam b = RifleBeams.get(id);
                if (b != null) {
                    b.held = false;
                    b.hold = (int) b.age(now, false) + Math.max(4, (int) s);
                    b.surgeAt = now;
                }
            }
            case "rifle_beam_stop" -> {
                RifleBeams.stop(id, now);
                SHAPES.remove(id);
                if (drawn) burst(level, pos, q(10), 0.15, Sprite.SMOKE, SMOKE, 0.6f, 14);
            }
            // --- The lodge ---
            case "lodge_anomaly" -> anomaly(level, pos, Mth.clamp(s, 0, 1), now);
            case "lodge_reveal" -> {
                anomaly(level, pos, 1f, now);
                Vec3 head = pos.add(0, 1.85, 0);
                Flashes.flash(head.add(-0.1, 0, 0), 0.05f, 0.18f, EYE, 1f, 30, now);
                Flashes.flash(head.add(0.1, 0, 0), 0.05f, 0.18f, EYE, 1f, 30, now);
                Flashes.ripple(pos.add(0, 1, 0), new Vec3(0, 0, 1), 0.3f, 3f, SHADE, 0.6f, 14, now);
                ScreenEffects.flash(0x30200008, 8);
                ScreenEffects.shake(0.15f, 6);
            }
            case "rack_unseal" -> {
                Flashes.flash(pos, 0.3f, 1.8f, VIOLET, 0.9f, 10, now);
                Flashes.ring(pos, 0.2f, 1.6f, PALE, 0.7f, 8, now);
                sparks(level, pos, Vec3.ZERO, q(16), 0.25, new float[] {0.6f, 0.6f, 0.62f}, 0.06f, 12);
                burst(level, pos, q(10), 0.06, Sprite.SMOKE, SHADE, 0.6f, 24);
            }
            case "rack_take" -> {
                Flashes.flash(pos, 0.3f, 1.2f, PALE, 0.8f, 6, now);
                implode(level, pos, 1.0, q(12), VIOLET, 0.06f, 10);
            }
            case "stalker_eyes" -> {
                Entity src = id >= 0 ? level.getEntity(id) : null;
                if (src != null && !ClientProgression.canSee(src)) return;
                Vec3 side = d.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize().scale(0.16);
                Vec3 f = pos.add(d.scale(0.45));
                Flashes.flash(f.add(side), 0.06f, 0.2f, EYE, 1f, 7, now);
                Flashes.flash(f.subtract(side), 0.06f, 0.2f, EYE, 1f, 7, now);
                if (RNG.nextInt(2) == 0) level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 0.5f, 0.5f, false);
            }
            default -> {}
        }
    }

    /** Where the rifle's beam_origin was drawn (third person), else the server's muzzle. */
    private static Vec3 muzzle(int id, Vec3 server) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return server;
        if (mc.player != null && mc.player.getId() == id && mc.options.getCameraType().isFirstPerson()) return server;
        Vec3 m = RifleClient.muzzle(id, mc.level.getGameTime());
        return m != null && m.distanceToSqr(server) < 1.5 * 1.5 ? m : server;
    }

    /** The four lenses round the muzzle (the arms' tips). */
    private static Vec3[] lenses(Vec3 at, Vec3 d, double r) {
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(d).normalize();
        return new Vec3[] {at.add(side.scale(r)), at.subtract(side.scale(r)), at.add(up.scale(r)), at.subtract(up.scale(r))};
    }

    /**
     * What the scope shows out in the trees: a crooked trail of cold light winding to a figure standing on it. Faint at
     * first (the eye slides off it), clearer as the aim is held ({@code k}), fully there once it resolves.
     */
    private static void anomaly(ClientLevel level, Vec3 at, float k, long now) {
        Random r = new Random(Double.doubleToLongBits(at.x * 31 + at.z));
        Vec3 p = at;
        double a = r.nextDouble() * Math.PI * 2;
        int steps = 6 + Math.round(10 * k);
        for (int i = 0; i < steps; i++) {
            a += (r.nextDouble() - 0.5) * 1.6;
            Vec3 next = p.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
            if (RNG.nextFloat() < 0.5f + 0.5f * k) {
                add(level, next.add(0, 0.12, 0), new Vec3(0, 0.004, 0), Sprite.GLOW, SOUL, 0.25f + 0.5f * k, 0.12f, 0.02f, 10).fadeIn();
            }
            p = next;
        }
        // The figure: a tall, thin shape of shadow, its edges wavering.
        float alpha = 0.15f + 0.75f * k;
        double sway = Math.sin(now * 0.3) * 0.05 * (1 - k);
        Flashes.ink(at.add(sway, 0.45, 0), 0.25f, 0.32f, SHADE, alpha, 4, now);
        Flashes.ink(at.add(sway, 1.05, 0), 0.32f, 0.4f, SHADE, alpha, 4, now);
        Flashes.ink(at.add(sway * 2, 1.7, 0), 0.22f, 0.28f, SHADE, alpha, 4, now);
        if (k > 0.6f) {
            Flashes.flash(at.add(-0.08, 1.75, 0), 0.03f, 0.08f, EYE, k, 4, now);
            Flashes.flash(at.add(0.08, 1.75, 0), 0.03f, 0.08f, EYE, k, 4, now);
        }
    }
}
