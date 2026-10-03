package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.ClientBeam;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.client.render.RyuBeams;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

import static dev.rick.jjk.client.fx.ClientFx.*;
import static dev.rick.jjk.client.fx.YujiFx.line;

/**
 * True Cannon's visual language: his Cursed Energy Discharge is a cold, bright blue with a white-hot middle, fired from
 * the "cannon" of his pompadour or his fingertip; when he overheats his head smokes; Decadence burns with a gold edge.
 * Also the beam clash's world effects (the collision, its judgements, the breakthrough and the stalemate), which belong
 * to neither side. Sounds reuse the project's own events.
 */
final class RyuFx {
    static final float[] RYU = {0.32f, 0.62f, 1f};
    static final float[] RYU_LIGHT = {0.8f, 0.93f, 1f};
    static final float[] DECADENCE = {1f, 0.78f, 0.35f};
    static final float[] STEAM = {0.75f, 0.75f, 0.78f};
    static final float[] SMOKE = {0.08f, 0.08f, 0.1f};
    static final float[] TLB = {0.97f, 0.42f, 1f};

    /** Every Last Drop: each caster's shape (radius, grow, collapse), sent just before it fires. */
    private static final Map<Integer, double[]> SHAPES = new HashMap<>();

    private RyuFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 d = dir.lengthSqr() > 1e-4 ? dir.normalize() : new Vec3(0, 0, 1);
        switch (p.id()) {
            // --- Cursed Energy Discharge: the M1 ray and Granite Blast ---
            case "ryu_m1_ray" -> {
                sound("red_fire", pos, 0.8f, 1.7f + RNG.nextFloat() * 0.1f);
                if (drawn) {
                    Vec3 to = pos.add(dir);
                    Flashes.beam(pos, to, 0.16f, RYU, 0.9f, 4, now);
                    Flashes.beam(pos, to, 0.06f, WHITE, 1f, 3, now);
                    Flashes.flash(pos, 0.3f, 0.7f, RYU_LIGHT, 1f, 3, now);
                    Flashes.flash(to, 0.4f, 0.9f, RYU, 0.9f, 4, now);
                }
            }
            case "ryu_ray_hit" -> {
                sound("hit_heavy", pos, 0.9f, 1.15f);
                if (drawn) sparks(level, pos, d, q(8), 0.4, RYU_LIGHT, 0.07f, 6);
                victimFeedback(p, 0.35f);
            }
            case "granite_charge" -> {
                // The cannon filling: light sucked into his forehead; at full charge it goes white.
                boolean full = s >= 0.5f;
                sound(full ? "red_compress" : "max_charge", pos, 0.8f, full ? 1.4f : 1.6f);
                if (drawn) {
                    Flashes.flash(pos, 0.2f, full ? 0.9f : 0.5f, full ? WHITE : RYU, 1f, full ? 8 : 20, now);
                    implode(level, pos, full ? 1.6 : 2.4, q(full ? 18 : 10), RYU, 0.06f, 10);
                }
            }
            case "granite_blast", "granite_blast_held" -> {
                boolean held = p.id().endsWith("held");
                sound(held ? "max_red_fire" : "red_fire", pos, held ? 1.6f : 1.1f, held ? 1.2f : 1.35f);
                if (held) sound("ground_impact", pos, 0.8f, 1.3f);
                Vec3 to = pos.add(dir);
                float w = held ? 0.55f : 0.32f;
                if (drawn) {
                    Flashes.beam(pos, to, w * 1.8f, RYU, 0.55f, held ? 8 : 5, now);
                    Flashes.beam(pos, to, w, RYU_LIGHT, 0.95f, held ? 7 : 4, now);
                    Flashes.beam(pos, to, w * 0.35f, WHITE, 1f, held ? 6 : 3, now);
                    Flashes.flash(pos, 0.6f, held ? 2.2f : 1.2f, RYU_LIGHT, 1f, 5, now);
                    Flashes.ripple(pos, d, 0.3f, held ? 2.6f : 1.4f, WHITE, 0.8f, 6, now);
                    Flashes.flash(to, 0.5f, held ? 2.4f : 1.4f, RYU, 1f, 6, now);
                    sparks(level, to, d.scale(-1), q(held ? 18 : 8), 0.45, RYU_LIGHT, 0.07f, 8);
                    if (held) debris(level, to, q(12), 0.6);
                }
                distanceShake(pos, held ? 30 : 14, held ? 0.6f : 0.25f);
            }
            case "granite_hit" -> {
                sound("hit_heavy", pos, 1f, 1f);
                sound("rough_impact", pos, 0.8f, 1.3f);
                if (drawn) {
                    Flashes.flash(pos, 0.8f, 1.2f * s, RYU_LIGHT, 1f, 5, now);
                    impactStar(pos, d, 8, 1.3f * s, 0.06f, RYU, now);
                }
                victimFeedback(p, 0.5f * s);
            }
            case "granite_loop" -> {
                // The dash variant: a blast fired backward to throw himself forward, a ring of discharge behind him.
                sound("red_fire", pos, 1f, 1.1f);
                sound("dash", pos, 0.8f, 0.9f);
                if (drawn) {
                    Flashes.beam(pos, pos.subtract(d.scale(4)), 0.4f, RYU_LIGHT, 0.9f, 5, now);
                    Flashes.ripple(pos.subtract(d.scale(0.6)), d, 0.4f, 2.2f, RYU, 0.8f, 6, now);
                    burst(level, pos, q(10), 0.2, Sprite.SMOKE, STEAM, 0.5f, 14);
                }
            }
            case "appetizer_ray" -> {
                // A sheet of discharge rising out of the ground along the line in front of him (pushes away when he is
                // overheated: then it's thin and red-hot).
                boolean cut = s >= 0.5f;
                sound("open_fire", pos, 1.3f, cut ? 1.6f : 1.25f);
                float[] col = cut ? new float[] {1f, 0.5f, 0.3f} : RYU;
                double len = dir.length();
                if (drawn) {
                    for (double t = 0; t < len; t += 3) {
                        Vec3 g = groundBelow(level, pos.add(d.scale(t)).add(0, 1.5, 0));
                        long at = now + Math.round(t / 6);
                        Flashes.beam(g, g.add(0, cut ? 3 : 5, 0), cut ? 0.4f : 0.8f, col, 0.75f, 8, at);
                        Flashes.beam(g, g.add(0, cut ? 2.5 : 4.5, 0), 0.25f, WHITE, 0.9f, 6, at);
                        Flashes.ground(g.add(0, 0.05, 0), 0.4f, 1.6f, col, 0.7f, 8, at);
                    }
                    debris(level, groundBelow(level, pos.add(d.scale(len * 0.5)).add(0, 1.5, 0)), q(16), 0.6);
                }
                distanceShake(pos, 30, 0.4f);
            }
            // --- Close range ---
            case "ryu_punch" -> {
                sound("hit_heavy", pos, 0.9f, 1.05f + RNG.nextFloat() * 0.1f);
                if (drawn) {
                    Flashes.flash(pos, 0.7f, 0.25f, WHITE, 1f, 3, now);
                    sparks(level, pos, d, q(6), 0.35, RYU_LIGHT, 0.06f, 5);
                }
                victimFeedback(p, 0.3f);
            }
            case "ryu_punch_heavy", "ryu_trade", "ryu_tetsuzanko" -> {
                sound(p.id().equals("ryu_tetsuzanko") ? "rush_slam" : "crushing_hit", pos, 1.1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.3f, 0.4f, RYU_LIGHT, 1f, 5, now);
                    impactStar(pos, d, 10, 1.6f, 0.06f, RYU, now);
                    Flashes.ripple(pos, d, 0.4f, 2f, WHITE, 0.7f, 6, now);
                    sparks(level, pos, d, q(12), 0.5, RYU_LIGHT, 0.08f, 8);
                }
                victimFeedback(p, 0.6f);
                distanceShake(pos, 16, 0.35f);
            }
            case "ryu_kick" -> {
                sound("fever_kick", pos, 1f, 1.1f);
                if (drawn) {
                    Flashes.flash(pos, 1f, 0.3f, WHITE, 1f, 4, now);
                    sparks(level, pos, d, q(8), 0.4, RYU_LIGHT, 0.07f, 6);
                }
                victimFeedback(p, 0.45f);
            }
            case "ryu_slam" -> {
                sound("hit_slam", pos, 1.1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.2f, 0.4f, RYU_LIGHT, 1f, 5, now);
                    debris(level, pos, q(10), 0.5);
                }
                victimFeedback(p, 0.6f);
            }
            case "ryu_unsatisfied_whiff", "ryu_second_helping_whiff", "ryu_dessert_swing", "ryu_invited_whiff" -> {
                sound("swing_heavy", pos, 0.9f, 0.9f + RNG.nextFloat() * 0.1f);
                if (drawn) line(pos, pos.add(d.scale(1.6)), 0.12f, RYU_LIGHT, 0.5f, 3, now);
            }
            case "second_helping_lock" -> {
                sound("rushdown_grab", pos, 1f, 1.1f);
                if (drawn) Flashes.flash(pos, 0.6f, 1.6f, RYU, 1f, 6, now);
            }
            case "ryu_slam_ground", "ryu_floor_slam" -> {
                sound("manji_slam", pos, 1.3f, 0.95f);
                sound("ground_impact", pos, 1.2f, 0.85f);
                Vec3 g = groundBelow(level, pos.add(0, 0.5, 0));
                if (drawn) {
                    Flashes.ground(g.add(0, 0.06, 0), 0.6f, 4.5f, RYU, 0.8f, 10, now);
                    Flashes.ring(g.add(0, 0.2, 0), 0.6f, 5.5f, WHITE, 0.6f, 8, now);
                    debris(level, g.add(0, 0.2, 0), q(22), 0.8);
                    burst(level, g.add(0, 0.3, 0), q(14), 0.3, Sprite.SMOKE, SMOKE, 0.9f, 22);
                }
                distanceShake(pos, 24, 0.7f);
            }
            case "ryu_delayed_impact" -> {
                // The punch lands a beat after the fist: a shock of discharge bursting out of the target.
                sound("crushing_impact", pos, 1.2f, 1.1f);
                if (drawn) {
                    Flashes.flash(pos, 1.5f, 2.6f, RYU_LIGHT, 1f, 7, now);
                    Flashes.ripple(pos, up, 0.5f, 3.2f, WHITE, 0.8f, 8, now);
                    sparks(level, pos, up.scale(-1), q(16), 0.6, RYU_LIGHT, 0.08f, 10);
                }
                distanceShake(pos, 20, 0.5f);
            }
            case "ryu_dessert_scene", "ryu_fist_clash" -> {
                sound(p.id().equals("ryu_fist_clash") ? "crushing_fist" : "fever_rush", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.4f, 2f * s, DECADENCE, 1f, 6, now);
                    impactStar(pos, d, 12, 2f * s, 0.07f, DECADENCE, now);
                    Flashes.ripple(pos, d, 0.4f, 2.6f, WHITE, 0.8f, 7, now);
                }
                distanceShake(pos, 18, 0.5f);
            }
            // --- Restyle and Overheat ---
            case "ryu_sweet" -> {
                // Cooling off: steam rising off the pompadour.
                sound("infinity_off", pos, 0.7f, 1.4f);
                if (drawn) burst(level, pos.add(0, 0.3, 0), q(14), 0.08, Sprite.SMOKE, STEAM, 0.5f, 30);
            }
            case "ryu_comb" -> {
                sound("rika_move", pos, 0.7f, 1.5f);
                if (drawn) for (int i = 0; i < q(6); i++) add(level, pos.add(gauss(0.2), 0.35, gauss(0.2)), new Vec3(0, 0.03, 0), Sprite.GLOW, RYU_LIGHT, 0.8f, 0.08f, 0.02f, 16);
            }
            case "ryu_cooled" -> {
                sound("infinity_off", pos, 0.9f, 1.1f);
                if (drawn) {
                    burst(level, pos, q(20), 0.12, Sprite.SMOKE, STEAM, 0.6f, 26);
                    Flashes.flash(pos, 0.3f, 0.8f, RYU_LIGHT, 0.8f, 6, now);
                }
            }
            case "ryu_restyle_done" -> {
                sound("jackpot_heal", pos, 0.6f, 1.5f);
                if (drawn) Flashes.ripple(pos.add(0, -0.9, 0), up, 0.3f, 1.6f, RYU_LIGHT, 0.7f, 10, now);
            }
            case "ryu_overheat" -> {
                // At 100%: a puff of heat off the top of his head, and it keeps smoking.
                sound("red_explosion", pos, 0.5f, 1.8f);
                sound("infinity_off", pos, 0.8f, 0.6f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 1.2f, new float[] {1f, 0.45f, 0.2f}, 1f, 6, now);
                    burst(level, pos, q(20), 0.1, Sprite.SMOKE, SMOKE, 0.7f, 30);
                }
            }
            case "ryu_smoke" -> {
                if (drawn) {
                    for (int i = 0; i < q(Math.max(1, Math.round(3 * s))); i++) {
                        add(level, pos.add(gauss(0.12), 0, gauss(0.12)), new Vec3(gauss(0.01), 0.04 + RNG.nextDouble() * 0.03, gauss(0.01)), Sprite.SMOKE,
                                RNG.nextBoolean() ? SMOKE : STEAM, 0.7f, 0.25f, 0.6f, 26);
                    }
                }
            }
            case "ryu_feint" -> {
                sound("side_dash", pos, 0.8f, 1.2f);
                if (drawn) Flashes.flash(pos, 0.8f, 0.2f, DECADENCE, 0.8f, 4, now);
            }
            case "ryu_armor" -> {
                sound("block", pos, 0.8f, 0.7f);
                if (drawn) Flashes.lens(pos, 1.3f, 1.6f, DECADENCE, 0.5f, 8, now);
            }
            // --- Awakening ---
            case "ryu_awaken" -> {
                sound("awaken", pos, 1.6f, 1.1f);
                sound("max_red_explosion", pos, 0.8f, 1.5f);
                if (drawn) {
                    Flashes.flash(pos, 1f, 5f, DECADENCE, 1f, 14, now);
                    Flashes.ring(pos, 1f, 9f, WHITE, 0.8f, 10, now);
                    for (int i = 0; i < 3; i++) Flashes.ripple(pos.add(0, -1 + i * 0.8, 0), up, 0.5f, 3f, i == 1 ? WHITE : DECADENCE, 0.9f, 14, now + i * 2);
                    burst(level, pos, q(30), 0.25, Sprite.SMOKE, SMOKE, 1f, 30);
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 16) ScreenEffects.flash(0x50FFD88A, 8);
                distanceShake(pos, 40, 0.8f);
            }
            case "ryu_awaken_end", "ryu_decadence" -> {
                sound(p.id().equals("ryu_decadence") ? "guard_break" : "awaken_end", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.2f, 2.6f, DECADENCE, 0.9f, 8, now);
                    burst(level, pos, q(20), 0.2, Sprite.SMOKE, STEAM, 0.7f, 24);
                }
            }
            // --- "You weren't invited." ---
            case "ryu_invited_charged" -> {
                sound("heavy_charge", pos, 1.1f, 1.1f);
                if (drawn) Flashes.flash(pos, 0.4f, 1.8f, DECADENCE, 1f, 10, now);
            }
            case "ryu_wall_punch" -> {
                sound("rush_break", pos, 1.3f, 0.9f);
                sound("ground_impact", pos, 1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.2f, 2.8f * s, DECADENCE, 1f, 8, now);
                    debris(level, pos, q(30), 0.9);
                    burst(level, pos, q(16), 0.3, Sprite.SMOKE, SMOKE, 1f, 24);
                }
                distanceShake(pos, 30, 0.8f);
            }
            case "ryu_debris" -> {
                // The punched-out wall flying on: a mass of rubble tearing along.
                if (drawn) {
                    debris(level, pos, q(Math.max(4, (int) s * 3)), 0.25);
                    burst(level, pos, q(4), 0.1, Sprite.SMOKE, SMOKE, 0.9f, 14);
                }
                if (RNG.nextInt(4) == 0) sound("ball_ricochet", pos, 0.6f, 0.6f);
            }
            case "ryu_debris_break" -> {
                sound("rush_break", pos, 1.2f, 0.8f);
                if (drawn) {
                    debris(level, pos, q(36), 1.1);
                    burst(level, pos, q(20), 0.4, Sprite.SMOKE, SMOKE, 1.1f, 26);
                }
                distanceShake(pos, 24, 0.6f);
            }
            case "ryu_invited_hit" -> {
                sound("crushing_impact", pos, 1.4f, 0.9f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 3f, DECADENCE, 1f, 8, now);
                    impactStar(pos, d, 14, 2.4f, 0.08f, WHITE, now);
                    Flashes.ripple(pos, d, 0.5f, 3.4f, DECADENCE, 0.9f, 9, now);
                }
                victimFeedback(p, 0.9f);
                distanceShake(pos, 30, 0.9f);
            }
            case "granite_finisher", "appetizer_finisher", "second_helping_finisher", "invited_finisher", "eld_finisher" -> {
                sound("finisher", pos, 1.1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.5f, 3f, RYU_LIGHT, 1f, 10, now);
                    burst(level, pos, q(30), 0.15, Sprite.SMOKE, SMOKE, 1f, 30);
                }
            }
            // --- Every Last Drop ---
            case "eld_voice" -> sound("max_charge", pos, 1.4f, 0.8f);
            case "eld_charge", "eld_counter" -> {
                boolean counter = p.id().equals("eld_counter");
                int charge = counter ? 10 : Math.max(10, (int) s);
                sound("max_red_charge", pos, 1.8f, counter ? 1.4f : 1.1f);
                if (drawn) {
                    Flashes.flash(pos, 0.2f, 1.6f, RYU, 1f, charge, now);
                    Flashes.flash(pos, 0.1f, 0.7f, WHITE, 1f, charge, now);
                    Flashes.lens(pos, 6f, 0.6f, RYU, 0.5f, charge, now);
                    for (int i = 0; i < 3; i++) Flashes.swirl(pos, d, 0.5f + i * 0.4f, 3f + i * 0.5f, 4f, 3f - i * 1.5f, 0.06f, i == 1 ? WHITE : RYU_LIGHT, 0.85f, charge, now + i * 2);
                    implode(level, pos, 4.5, q(40), RYU, 0.12f, 18);
                }
            }
            case "eld_gather" -> {
                if (drawn) {
                    float k = Math.max(0, Math.min(1, s));
                    implode(level, pos, 4 - 2 * k, q((int) (10 + 24 * k)), k > 0.7f ? WHITE : RYU, 0.1f, 8);
                    Flashes.flash(pos, 0.3f + 0.5f * k, 0.5f, k > 0.7f ? WHITE : RYU_LIGHT, 1f, 5, now);
                }
                if (s > 0.85f) sound("red_compress", pos, 0.8f, 1.6f);
            }
            case "eld_shape" -> SHAPES.put(p.entityId(), new double[] {dir.x, dir.y, dir.z});
            case "eld_fire" -> {
                sound("max_red_fire", pos, 3f, 0.9f);
                sound("purple_fire", pos, 1.8f, 1.3f);
                double reach = dir.length();
                int hold = Math.max(2, (int) s);
                double[] shape = SHAPES.remove(p.entityId());
                double half = shape != null ? shape[0] : 1.3;
                int grow = shape != null ? (int) shape[1] : 3, collapse = shape != null ? (int) shape[2] : 6;
                RyuBeams.start(p.entityId(), new ClientBeam(pos, d, reach, half, grow, collapse, hold, now, false));
                if (drawn) {
                    Flashes.flash(pos, 1f, 4.5f, RYU_LIGHT, 1f, 10, now);
                    Flashes.flash(pos, 0.8f, 2f, WHITE, 1f, 7, now);
                    Flashes.ripple(pos, d, 0.6f, 6f, RYU_LIGHT, 0.9f, 10, now);
                    Flashes.ring(pos, 1f, 10f, WHITE, 0.6f, 9, now);
                    burst(level, pos, q(16), 0.25, Sprite.SMOKE, STEAM, 0.8f, 20);
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 18) ScreenEffects.flash(0x404FA8FF, 6);
                distanceShake(pos, 80, 1.2f);
            }
            case "eld_pulse" -> {
                ClientBeam b = RyuBeams.get(p.entityId());
                if (b == null) return;
                b.strike = pos;
                double[] st = RyuBeams.state(p.entityId(), now, 0);
                if (st == null || st[1] <= 0.05) return;
                boolean capped = st[0] + 0.5 < pos.distanceTo(b.origin);
                boolean surge = ((int) s) % 8 == 0;
                if (surge) {
                    b.surgeAt = now;
                    sound("red_fire", b.origin, 1.2f, 0.7f + RNG.nextFloat() * 0.15f);
                }
                if (capped || !drawn) return;
                debris(level, pos, q(surge ? 10 : 4), surge ? 0.8 : 0.4);
                sparks(level, pos, d.scale(-1), q(surge ? 14 : 6), 0.7, RNG.nextBoolean() ? WHITE : RYU_LIGHT, 0.09f, 10);
                burst(level, pos, q(3), 0.2, Sprite.SMOKE, SMOKE, 1f, 16);
                if (surge) {
                    Flashes.flash(pos, 1.5f, 5f * (float) st[1], RYU_LIGHT, 0.9f, 6, now);
                    if (mc.player != null && mc.player.position().distanceTo(pos) < 12) ScreenEffects.shake(0.35f, 4);
                }
            }
            case "eld_hold" -> {
                ClientBeam b = RyuBeams.get(p.entityId());
                if (b != null) b.held = true;
            }
            case "eld_reaim" -> {
                ClientBeam b = RyuBeams.get(p.entityId());
                if (b != null && d.lengthSqr() > 1e-6) b.dir = d;
            }
            case "eld_release" -> {
                ClientBeam b = RyuBeams.get(p.entityId());
                if (b != null) {
                    b.held = false;
                    b.hold = (int) b.age(now, false) + Math.max(4, (int) s);
                    b.surgeAt = now;
                }
            }
            case "eld_stop" -> {
                RyuBeams.stop(p.entityId(), now);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.2f, RYU, 0.8f, 8, now);
                    burst(level, pos, q(14), 0.2, Sprite.SMOKE, STEAM, 0.7f, 16);
                }
            }
            case "eld_hit" -> {
                sound("hit_heavy", pos, 1f, 0.85f);
                victimFeedback(p, 0.9f);
            }
            // --- Beam clash (world side) ---
            case "bclash_threat" -> {
                if (mine) sound("clash_countdown", pos, 1f, 0.6f);
            }
            case "bclash_answer" -> {
                sound("clash_start", pos, 1.4f, 1.3f);
                if (drawn) Flashes.flash(pos, 0.5f, 2f, WHITE, 1f, 6, now);
            }
            case "bclash_pin" -> {
                // The first beam out, held off short of the answer still coming.
                sound("red_explosion", pos, 1f, 1.5f);
                if (drawn) {
                    Flashes.flash(pos, 1f, 3f, WHITE, 0.9f, 8, now);
                    sparks(level, pos, d.scale(-1), q(14), 0.6, WHITE, 0.08f, 10);
                }
            }
            case "bclash_collide" -> {
                // The two meet: a blinding flash, a shockwave round the line, the ground torn beneath.
                sound("purple_collision", pos, 3f, 1f);
                sound("max_red_explosion", pos, 2f, 1.2f);
                if (drawn) {
                    Flashes.flash(pos, 2f, 9f, WHITE, 1f, 12, now);
                    Flashes.ripple(pos, d, 1f, 12f, WHITE, 0.9f, 12, now);
                    Flashes.ring(pos, 1f, 16f, RYU_LIGHT, 0.6f, 12, now);
                    Flashes.ring(pos, 1f, 13f, TLB, 0.6f, 12, now + 1);
                    Vec3 g = groundBelow(level, pos);
                    if (g.distanceTo(pos) < 8) {
                        Flashes.ground(g.add(0, 0.1, 0), 1f, 12f, WHITE, 0.7f, 14, now);
                        debris(level, g.add(0, 0.3, 0), q(40), 1.0);
                    }
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 30) ScreenEffects.flash(0x70FFFFFF, 6);
                distanceShake(pos, 90, 1.4f);
            }
            case "bclash_check" -> {
                if (mine) sound("rhythm_tick", pos, 0.8f, 1.2f);
            }
            case "bclash_great", "bclash_good", "bclash_miss" -> {
                // A push: a surge of the winning side's energy shoved through the collision toward the other.
                boolean miss = p.id().endsWith("miss"), great = p.id().endsWith("great");
                sound(miss ? "clash_miss" : great ? "clash_perfect" : "clash_hit", pos, 1.4f, miss ? 0.8f : 1f);
                sound("red_explosion", pos, great ? 1.4f : 0.8f, great ? 1.1f : 1.4f);
                if (drawn && !miss) {
                    Flashes.flash(pos, 1.5f, great ? 6f : 4f, WHITE, 1f, 6, now);
                    Flashes.ripple(pos.add(d.scale(1.5)), d, 1f, great ? 8f : 5f, WHITE, 0.8f, 8, now);
                    sparks(level, pos, d, q(great ? 24 : 12), 0.9, WHITE, 0.09f, 10);
                    debris(level, pos, q(great ? 14 : 6), 0.8);
                } else if (drawn) {
                    Flashes.flash(pos, 1f, 2.5f, new float[] {0.6f, 0.6f, 0.65f}, 0.7f, 5, now);
                    burst(level, pos, q(8), 0.2, Sprite.SMOKE, SMOKE, 0.9f, 14);
                }
            }
            case "bclash_decide" -> sound("domain_clash", pos, 2f, 1.2f);
            case "bclash_break" -> {
                // The winner punches through: the collision bursts and the loser takes it.
                sound("max_red_explosion", pos, 2.4f, 1f);
                sound("purple_end", pos, 1.6f, 1.3f);
                // The winner's colour floods it.
                float[] col = RyuBeams.get(p.entityId()) != null ? RYU_LIGHT : TLB;
                if (drawn) {
                    Flashes.flash(pos, 2f, 8f, WHITE, 1f, 12, now);
                    Flashes.flash(pos, 2f, 5f, col, 1f, 14, now);
                    Flashes.ripple(pos, d, 1f, 10f, WHITE, 0.9f, 12, now);
                    debris(level, pos, q(40), 1.0);
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 24) ScreenEffects.flash(0x80FFFFFF, 8);
                distanceShake(pos, 80, 1.6f);
            }
            case "bclash_tie" -> {
                // Neither gives: both beams detonate together.
                sound("unlimited_purple_explode", pos, 2.5f, 1.2f);
                sound("max_red_explosion", pos, 2f, 0.9f);
                if (drawn) {
                    Flashes.flash(pos, 3f, 12f, WHITE, 1f, 16, now);
                    Flashes.flash(pos, 2f, 9f, TLB, 0.8f, 14, now + 1);
                    Flashes.flash(pos, 2f, 9f, RYU, 0.8f, 14, now + 2);
                    Flashes.ring(pos, 1f, 22f, WHITE, 0.8f, 14, now);
                    Flashes.ring(pos, 1f, 18f, TLB, 0.6f, 14, now + 2);
                    Flashes.ring(pos, 1f, 14f, RYU_LIGHT, 0.6f, 14, now + 3);
                    debris(level, pos, q(60), 1.3);
                    burst(level, pos, q(40), 0.5, Sprite.SMOKE, SMOKE, 1.4f, 40);
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 30) ScreenEffects.flash(0xB0FFFFFF, 10);
                distanceShake(pos, 100, 2f);
            }
            default -> {
                String id = p.id();
                if (id.startsWith("sfx:")) {
                    // One of his own JJS sounds at a beat of a move (the server names it).
                    sound(id.substring(4), pos, Math.max(0.1f, s), 1f);
                } else if (id.startsWith("ryuh:") || id.startsWith("ryul:")) {
                    // A landed blow with its own JJS hit sound.
                    boolean heavy = id.startsWith("ryuh:");
                    String snd = id.substring(5);
                    if (!snd.equals("none")) sound(snd, pos, 1.1f, 1f);
                    if (drawn) {
                        Flashes.flash(pos, heavy ? 1.3f : 0.7f, heavy ? 0.4f : 0.25f, heavy ? RYU_LIGHT : WHITE, 1f, heavy ? 5 : 3, now);
                        if (heavy) impactStar(pos, d, 10, 1.6f * s, 0.06f, RYU, now);
                        sparks(level, pos, d, q(heavy ? 12 : 6), heavy ? 0.5 : 0.35, RYU_LIGHT, 0.07f, heavy ? 8 : 5);
                    }
                    victimFeedback(p, heavy ? 0.6f : 0.3f);
                }
            }
        }
    }
}
