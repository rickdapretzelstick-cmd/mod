package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.client.particle.EnergyParticle;
import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.*;

/**
 * Hakari's visual language. Where Gojo's techniques are clean light (spirals in, spikes out), Hakari's are casino and
 * brawl: steel pachinko balls and chrome sparks, pink neon, gold coins, heavy dust, and — once he hits the Jackpot —
 * jade-green energy that won't stop pouring off him. Base moves stay restrained; Jackpot moves are loud.
 */
final class HakariFx {
    static final float[] PINK = {1f, 0.25f, 0.63f};
    static final float[] HOT_PINK = {1f, 0.55f, 0.8f};
    static final float[] JADE = {0.36f, 1f, 0.66f};
    static final float[] JADE_LIGHT = {0.8f, 1f, 0.9f};
    static final float[] CHROME = {0.85f, 0.88f, 0.94f};
    static final float[] COIN = {1f, 0.8f, 0.25f};
    static final float[] ROUGH = {0.55f, 1f, 0.75f};

    private HakariFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        Vec3 up = new Vec3(0, 1, 0);
        switch (p.id()) {
            // --- Reserve Balls ---
            case "ball_throw" -> {
                sound("ball_throw", pos, 0.7f, 0.95f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 0.15f, CHROME, 0.8f, 3, now);
                    sparks(level, pos, dir, q(3), 0.25, HOT_PINK, 0.06f, 4);
                }
            }
            case "ball_hit" -> {
                sound("ball_hit", pos, 0.9f, 0.95f + RNG.nextFloat() * 0.15f);
                if (drawn) {
                    Flashes.flash(pos, 0.8f * s, 0.15f, CHROME, 1f, 3, now);
                    impactStar(pos, dir, 5, 0.9f * s, 0.05f, WHITE, now);
                    Flashes.ring(pos, 0.1f, 0.9f * s, HOT_PINK, 0.7f, 5, now);
                    sparks(level, pos, dir, q(6), 0.4, CHROME, 0.08f, 6);
                    for (int i = 0; i < q(3); i++) level.addParticle(ParticleTypes.CRIT, pos.x, pos.y, pos.z, gauss(0.3), gauss(0.3) + 0.1, gauss(0.3));
                }
                victimFeedback(p, 0.2f);
            }
            case "ball_ricochet" -> {
                sound("ball_ricochet", pos, 0.6f * s, 1f + RNG.nextFloat() * 0.3f);
                if (drawn) {
                    Flashes.flash(pos, 0.35f, 0.1f, WHITE, 0.9f, 2, now);
                    sparks(level, pos, dir, q(4), 0.3, CHROME, 0.05f, 5);
                }
            }
            // --- Shutter Doors ---
            case "shutter_appear" -> {
                sound("shutter_rise", pos, 1f, 1f);
                if (drawn) {
                    Vec3 side = dir.lengthSqr() > 1e-4 ? dir.normalize() : new Vec3(1, 0, 0);
                    for (int k = -1; k <= 1; k += 2) {
                        Vec3 at = pos.add(side.scale(2.4 * k));
                        Flashes.ground(groundBelow(level, at), 0.2f, 1.6f, PINK, 0.7f, 8, now);
                        for (int i = 0; i < q(6); i++) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + gauss(0.5), at.y + 0.1, at.z + gauss(0.5), 0, 0.03, 0);
                    }
                }
            }
            case "shutter_slam" -> {
                sound("shutter_slam", pos, 1.2f, 0.95f);
                if (drawn) {
                    Flashes.flash(pos, 2.2f, 0.4f, WHITE, 1f, 4, now);
                    Vec3 side = dir.lengthSqr() > 1e-4 ? dir.normalize() : new Vec3(1, 0, 0);
                    Flashes.ripple(pos, side, 0.2f, 2.2f, HOT_PINK, 0.8f, 7, now);
                    sparks(level, pos, up, q(14), 0.55, CHROME, 0.1f, 7);
                    burst(level, pos.add(0, -1, 0), q(6), 0.12, Sprite.SMOKE, GREY, 0.5f, 14);
                }
                distanceShake(pos, 16, 0.5f);
            }
            case "shutter_crush" -> {
                sound("hit_heavy", pos, 1f, 0.8f);
                victimFeedback(p, 0.6f);
            }
            // --- Rough Energy ---
            case "rough_charge" -> {
                sound("rough_charge", pos, 1f, 1f);
                if (drawn) for (int i = 0; i < q(14); i++) {
                    Vec3 at = pos.add(randomUnit().scale(1.3));
                    add(level, at, pos.subtract(at).scale(0.09), Sprite.SHARD, i % 3 == 0 ? WHITE : ROUGH, 0.8f, 0.18f, 0.04f, 12);
                }
            }
            case "rough_impact" -> {
                sound("rough_impact", pos, 1.2f, 0.9f + RNG.nextFloat() * 0.1f);
                if (drawn) {
                    Flashes.flash(pos, 2.4f * s, 0.4f, ROUGH, 1f, 5, now);
                    impactStar(pos, dir, 9, 2.4f * s, 0.12f, JADE_LIGHT, now);
                    Flashes.ripple(pos, dir, 0.3f, 2.6f * s, ROUGH, 0.8f, 8, now);
                    Flashes.ground(groundBelow(level, pos), 0.3f, 3f * s, JADE, 0.6f, 10, now + 1);
                    sparks(level, pos, dir, q(18), 0.8, ROUGH, 0.14f, 8);
                    debris(level, groundBelow(level, pos.add(dir.scale(0.8))), q(8), 0.5);
                }
                distanceShake(pos, 18, 0.6f);
            }
            case "rough_hit" -> {
                sound("hit_heavy", pos, 1f, 0.75f);
                victimFeedback(p, 0.7f);
            }
            // --- Fever Breaker ---
            case "fever_swing" -> {
                sound("fever_kick", pos, 0.9f, 1f);
                if (drawn) swingTrail(pos, dir, 1.4f, 0.12f, now);
            }
            case "fever_kick" -> {
                sound("hit_heavy", pos, 1f, 1.05f);
                if (drawn) {
                    Flashes.flash(pos, 1.4f, 0.3f, WHITE, 1f, 4, now);
                    impactStar(pos, dir, 6, 1.4f, 0.07f, HOT_PINK, now);
                }
                victimFeedback(p, 0.5f);
            }
            case "fever_rush" -> {
                sound("fever_rush", pos, 1f, 1f);
                if (drawn) afterimage(level, pos);
            }
            case "fever_impact" -> victimFeedback(p, 0.8f);
            case "fever_break" -> {
                sound("fever_break", pos, 1.3f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 3.2f, 0.5f, WHITE, 1f, 5, now);
                    Flashes.flash(pos, 2.2f, 0.4f, PINK, 0.9f, 7, now + 1);
                    impactStar(pos, dir, 12, 3f, 0.14f, WHITE, now);
                    Flashes.ripple(pos, dir, 0.3f, 3.2f, HOT_PINK, 0.9f, 9, now);
                    Flashes.ripple(pos.add(dir.scale(0.6)), dir, 0.2f, 2.2f, WHITE, 0.6f, 8, now + 2);
                    sparks(level, pos, dir, q(22), 0.9, WHITE, 0.14f, 9);
                    burst(level, pos, q(6), 0.14, Sprite.SMOKE, GREY, 0.6f, 16);
                }
                if (mine || isAttackerClose(mc, pos)) {
                    ScreenEffects.fovPunch(0.08f);
                    ScreenEffects.shake(0.7f, 12);
                }
            }
            // --- Door Guard ---
            case "door_guard_up" -> {
                sound("door_open", pos, 1f, 1f);
                if (drawn) Flashes.flash(pos, 1.4f, 0.3f, COIN, 0.7f, 4, now);
            }
            case "door_guard_block" -> {
                sound("door_block", pos, 1f, 0.9f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    Flashes.ripple(pos, dir, 0.3f, 1.6f, COIN, 0.8f, 6, now);
                    sparks(level, pos, dir.reverse(), q(8), 0.45, COIN, 0.08f, 6);
                }
            }
            case "door_guard_counter" -> {
                sound("door_slam", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 2.2f, 0.4f, WHITE, 1f, 4, now);
                    Flashes.ripple(pos, dir, 0.3f, 2.8f, PINK, 0.9f, 8, now);
                    impactStar(pos, dir, 8, 2f, 0.1f, COIN, now);
                }
            }
            case "door_guard_down" -> sound("door_open", pos, 0.6f, 1.3f);
            // --- Idle Death Gamble ---
            case "idg_charge" -> {
                sound("domain_charge", pos, 1.4f, 1.1f);
                sound("gamble_spin", pos, 1f, 1f);
                // The hand sign: white smoke gathering, no neon (the cut-in carries the moment).
                if (drawn) for (int i = 0; i < q(10); i++) {
                    double a = RNG.nextDouble() * Math.PI * 2;
                    level.addParticle(ParticleTypes.CLOUD, pos.x + Math.cos(a) * 1.2, pos.y - 0.8, pos.z + Math.sin(a) * 1.2, -Math.cos(a) * 0.04, 0.02, -Math.sin(a) * 0.04);
                }
            }
            // The white room is silent and still: nothing floating in it to judge depth by.
            case "idg_ambient" -> sound("idg_ambient", pos, 0.8f, 1f);
            case "idg_rules" -> sound("gamble_signal", pos, 0.4f, 1.4f);
            case "gamble_visual" -> {
                // A visual move: the reels jolt and a cascade of balls and coins bursts off Hakari.
                sound("gamble_visual", pos, 1f, 0.9f + s * 0.3f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.3f, HOT_PINK, 0.8f, 5, now);
                    Flashes.ring(pos, 0.3f, 2.2f + s, COIN, 0.8f, 8, now);
                    for (int i = 0; i < q(20); i++) {
                        Vec3 v = randomUnit().scale(0.3).add(0, 0.35, 0);
                        add(level, pos, v, Sprite.CORE, i % 3 == 0 ? COIN : CHROME, 0.9f, 0.1f, 0.1f, 26).gravity(0.04f);
                    }
                }
            }
            case "gamble_riichi" -> {
                sound("gamble_riichi", pos, 1.3f, 1f);
                if (drawn) {
                    Flashes.lens(pos, 0.5f, 4f, PINK, 0.7f, 18, now);
                    Flashes.beam(pos, pos.add(0, 12, 0), 0.4f, HOT_PINK, 0.8f, 30, now);
                }
            }
            case "gamble_signal" -> {
                float[] c = switch (Math.round(s)) {
                    case 0 -> JADE;
                    case 1 -> RED;
                    case 2 -> COIN;
                    default -> HOT_PINK;
                };
                sound("gamble_signal", pos, 1f, 0.8f + Math.round(s) * 0.15f);
                if (drawn) {
                    Flashes.flash(pos, 2.5f, 0.5f, c, 1f, 12, now);
                    Flashes.ring(pos, 0.5f, 4f, c, 0.9f, 14, now);
                }
            }
            case "gamble_hit" -> sound("gamble_stop", pos, 1.3f, 1.2f);
            case "gamble_miss" -> {
                sound("gamble_stop", pos, 1f, 0.8f);
                sound("gamble_miss", pos, 1f, 1f);
                if (drawn) burst(level, pos, q(10), 0.08, Sprite.SMOKE, GREY, 0.5f, 18);
            }
            // --- Jackpot ---
            case "jackpot" -> {
                sound("jackpot", pos, 2.5f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 6f, 0.8f, WHITE, 1f, 12, now);
                    Flashes.lens(pos, 1f, 9f, JADE, 0.9f, 22, now);
                    for (int i = 0; i < 4; i++) Flashes.ground(groundBelow(level, pos), 1f, 5f + i * 2.5f, i % 2 == 0 ? JADE : COIN, 0.9f - i * 0.15f, 18 + i * 4, now + i * 3L);
                    Flashes.beam(groundBelow(level, pos), pos.add(0, 20, 0), 1.6f, JADE, 1f, 30, now);
                    sparks(level, pos, up, q(50), 1.2, JADE, 0.2f, 16);
                    // Coins and balls raining down around him.
                    for (int i = 0; i < q(60); i++) {
                        Vec3 at = pos.add(gauss(3), 4 + RNG.nextDouble() * 4, gauss(3));
                        add(level, at, new Vec3(gauss(0.05), -0.2, gauss(0.05)), Sprite.CORE, i % 2 == 0 ? COIN : CHROME, 1f, 0.14f, 0.14f, 50).gravity(0.03f);
                    }
                }
                if (mine) {
                    ScreenEffects.flash(0xC0FFFFFF, 14);
                    ScreenEffects.fovPunch(0.15f);
                    ScreenEffects.shake(0.9f, 20);
                } else {
                    distanceShake(pos, 40, 0.7f);
                }
            }
            case "jackpot_aura" -> {
                if (drawn) for (int i = 0; i < q(4); i++) {
                    Vec3 at = pos.add(gauss(0.35), gauss(0.6) - 0.3, gauss(0.35));
                    add(level, at, new Vec3(gauss(0.01), 0.06 + RNG.nextDouble() * 0.05, gauss(0.01)), Sprite.GLOW, i % 3 == 0 ? JADE_LIGHT : JADE, 0.7f, 0.3f, 0.05f, 18).fadeIn();
                }
            }
            case "jackpot_heal" -> {
                sound("jackpot_heal", pos, 1.4f, 1f);
                if (drawn) {
                    Flashes.lens(pos, 2.5f, 0.4f, JADE, 0.9f, 14, now);
                    implode(level, pos, 2.5, q(30), JADE_LIGHT, 0.14f, 12);
                }
                if (mine) ScreenEffects.flash(0x5060FFA0, 8);
            }
            case "jackpot_end" -> {
                sound("jackpot_end", pos, 1f, 1f);
                if (drawn) burst(level, pos, q(16), 0.1, Sprite.GLOW, JADE, 0.3f, 16);
            }
            // --- Jackpot moves ---
            case "lucky_hit", "lucky_flurry" -> {
                boolean flurry = p.id().equals("lucky_flurry");
                sound("lucky_hit", pos, flurry ? 0.6f : 0.9f, 0.9f + RNG.nextFloat() * 0.35f);
                if (drawn) {
                    Flashes.flash(pos, flurry ? 0.7f : 1.1f, 0.2f, JADE_LIGHT, 1f, 3, now);
                    impactStar(pos, dir, flurry ? 3 : 5, flurry ? 0.8f : 1.2f, 0.05f, JADE, now);
                    sparks(level, pos, dir, q(flurry ? 3 : 6), 0.5, JADE_LIGHT, 0.08f, 5);
                }
                victimFeedback(p, flurry ? 0.15f : 0.35f);
            }
            case "lucky_final", "rushdown_throw", "overwhelm_final_hit", "surge_kick_hit" -> {
                sound("lucky_final", pos, 1.2f, 0.95f + RNG.nextFloat() * 0.1f);
                if (drawn) {
                    Flashes.flash(pos, 2.6f * s, 0.4f, WHITE, 1f, 5, now);
                    Flashes.flash(pos, 1.8f * s, 0.4f, JADE, 0.9f, 7, now + 1);
                    impactStar(pos, dir, 10, 2.4f * s, 0.12f, JADE_LIGHT, now);
                    Flashes.ripple(pos, dir, 0.3f, 2.6f * s, JADE, 0.9f, 8, now);
                    sparks(level, pos, dir, q(18), 0.9, JADE_LIGHT, 0.13f, 8);
                }
                victimFeedback(p, 0.8f);
                if (isAttackerClose(mc, pos)) ScreenEffects.fovPunch(0.05f);
            }
            case "rushdown_start" -> {
                sound("fever_rush", pos, 1f, 0.9f);
                if (drawn) Flashes.ground(groundBelow(level, pos), 0.3f, 2f, JADE, 0.7f, 8, now);
            }
            case "rushdown_step" -> {
                if (drawn) {
                    for (int i = 0; i < q(3); i++) level.addParticle(ParticleTypes.CLOUD, pos.x + gauss(0.3), pos.y + 0.1, pos.z + gauss(0.3), -dir.x * 0.1, 0.02, -dir.z * 0.1);
                    add(level, pos.add(0, 1, 0), dir.scale(-0.1), Sprite.GLOW, JADE, 0.5f, 0.6f, 0.1f, 8);
                }
            }
            case "rushdown_grab" -> {
                sound("hit_heavy", pos, 1.1f, 0.85f);
                victimFeedback(p, 0.6f);
            }
            case "rushdown_drag" -> {
                if (drawn) {
                    debris(level, groundBelow(level, pos), q(3), 0.3);
                    sparks(level, pos, dir.reverse(), q(3), 0.4, WHITE, 0.08f, 5);
                }
                if (RNG.nextInt(3) == 0) sound("ground_impact", pos, 0.5f, 1.3f);
            }
            case "overwhelm_charge" -> {
                sound("heavy_charge", pos, 1f, 1.2f);
                if (drawn) implode(level, pos, 1.8, q(16), JADE, 0.12f, 10);
            }
            case "overwhelm_punch" -> {
                sound("swing_heavy", pos, 0.8f, 1.1f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    Flashes.ripple(pos, dir, 0.2f, 1.2f * s, JADE, 0.6f, 5, now);
                    swingTrail(pos, dir, 1.1f, 0.1f, now);
                }
            }
            case "overwhelm_hit" -> {
                sound("lucky_hit", pos, 0.9f, 0.8f + s * 0.2f);
                if (drawn) {
                    Flashes.flash(pos, 1.2f * s, 0.2f, JADE_LIGHT, 1f, 3, now);
                    impactStar(pos, dir, 5, 1.2f * s, 0.06f, WHITE, now);
                }
                victimFeedback(p, 0.3f);
            }
            case "overwhelm_final" -> {
                sound("lucky_final", pos, 1.6f, 0.75f);
                sound("rough_impact", pos, 1.2f, 1.1f);
                if (drawn) {
                    Flashes.flash(pos, 4.5f, 0.6f, WHITE, 1f, 6, now);
                    Flashes.lens(pos, 0.5f, 5f, JADE, 0.9f, 12, now);
                    impactStar(pos, dir, 16, 4f, 0.18f, JADE_LIGHT, now);
                    for (int i = 0; i < 3; i++) Flashes.ripple(pos.add(dir.scale(i * 0.8)), dir, 0.3f, 3.5f - i * 0.6f, i == 0 ? WHITE : JADE, 0.9f, 9, now + i);
                    Flashes.ground(groundBelow(level, pos), 0.5f, 5f, JADE, 0.8f, 12, now + 1);
                    sparks(level, pos, dir, q(30), 1.2, JADE_LIGHT, 0.16f, 10);
                    debris(level, groundBelow(level, pos.add(dir)), q(14), 0.7);
                }
                if (isAttackerClose(mc, pos)) {
                    ScreenEffects.fovPunch(0.1f);
                    ScreenEffects.shake(0.9f, 14);
                }
            }
            case "surge_dash", "surge_hit" -> {
                sound(p.id().equals("surge_dash") ? "fever_rush" : "lucky_hit", pos, 1f, 1.2f);
                if (drawn) {
                    afterimage(level, pos);
                    Flashes.beam(pos, pos.add(dir.scale(5)), 0.3f, JADE, 0.8f, 6, now);
                }
                if (p.id().equals("surge_hit")) victimFeedback(p, 0.5f);
            }
            case "surge_vanish" -> {
                sound("surge_vanish", pos, 1.1f, 1f);
                if (drawn) {
                    Flashes.lens(pos, 1.6f, 0.1f, JADE, 0.9f, 6, now);
                    burst(level, pos, q(24), 0.25, Sprite.GLOW, JADE, 0.35f, 10);
                    afterimage(level, pos);
                }
            }
            case "surge_appear" -> {
                sound("surge_appear", pos, 1.1f, 1f);
                if (drawn) {
                    Flashes.lens(pos, 0.1f, 1.8f, JADE, 0.9f, 7, now);
                    Flashes.flash(pos, 1.6f, 0.3f, WHITE, 1f, 4, now);
                    burst(level, pos, q(18), 0.3, Sprite.SPARK, JADE_LIGHT, 0.12f, 8);
                }
            }
            case "surge_kick" -> {
                sound("ground_impact", pos, 1.2f, 0.9f);
                if (drawn) {
                    Flashes.beam(pos.add(0, 2, 0), pos.add(0, -1.5, 0), 0.5f, JADE, 0.9f, 6, now);
                    Flashes.ground(groundBelow(level, pos), 0.4f, 3.5f, JADE, 0.8f, 10, now + 2);
                    debris(level, groundBelow(level, pos), q(10), 0.6);
                }
            }
            case "rhythm_start" -> {
                sound("rhythm_tick", pos, 1f, 1f);
                if (drawn) Flashes.ring(pos, 0.3f, 1.6f, JADE, 0.7f, 8, now);
            }
            case "rhythm_beat" -> {
                // s = judgement: 0 perfect, 1 great, 2 good, 3 miss.
                int j = Math.round(s);
                if (mine) dev.rick.jjk.client.hud.RhythmClient.judged(Math.min(3, j));
                if (j <= 2) {
                    sound("rhythm_beat", pos, 1f, 1.2f - j * 0.1f);
                    if (drawn) {
                        float[] c = j == 0 ? COIN : j == 1 ? JADE : WHITE;
                        Flashes.ring(pos.add(0, -1.2, 0), 0.4f, 2.2f - j * 0.4f, c, 0.9f, 8, now);
                        for (int i = 0; i < q(8); i++) add(level, pos, randomUnit().scale(0.18).add(0, 0.2, 0), Sprite.STAR, c, 1f, 0.16f, 0.04f, 14);
                    }
                } else {
                    sound("clash_miss", pos, 0.5f, 1.2f);
                }
            }
            case "rhythm_streak" -> {
                sound("jackpot_heal", pos, 1.2f, 1.3f);
                if (drawn) {
                    Flashes.lens(pos, 0.3f, 2.8f, COIN, 0.8f, 12, now);
                    sparks(level, pos, up, q(20), 0.8, COIN, 0.12f, 10);
                }
            }
            // --- Combinations, lingering doors, finishers, Renewal ---
            case "combo_doors" -> {
                sound("shutter_rise", pos, 0.8f, 1.3f);
                if (drawn) Flashes.lens(pos, 0.2f, 1.6f, HOT_PINK, 0.8f, 6, now);
            }
            case "shutter_linger" -> {
                sound("door_open", pos, 0.7f, 0.8f);
                if (drawn) Flashes.ripple(pos, dir, 0.2f, 1.8f, PINK, 0.5f, 8, now);
            }
            case "door_bounce" -> {
                sound("door_block", pos, 1f, 1.2f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    Flashes.ripple(pos, up, 0.2f, 2.2f * s, COIN, 0.8f, 7, now);
                    sparks(level, pos, up, q(10), 0.55, CHROME, 0.08f, 6);
                }
            }
            case "shutter_shatter" -> {
                sound("shutter_slam", pos, 1f * s, 1.4f);
                if (drawn) {
                    Flashes.flash(pos, 1.8f * s, 0.3f, WHITE, 0.9f, 4, now);
                    burst(level, pos, q(18), 0.35, Sprite.SHARD, CHROME, 0.9f, 16);
                    burst(level, pos.add(0, -0.8, 0), q(6), 0.12, Sprite.SMOKE, GREY, 0.5f, 14);
                }
            }
            case "shutter_finisher" -> {
                sound("shutter_slam", pos, 1.5f, 0.7f);
                sound("hit_heavy", pos, 1.3f, 0.6f);
                if (drawn) {
                    Flashes.flash(pos, 3.6f, 0.6f, WHITE, 1f, 6, now);
                    Flashes.ripple(pos, up, 0.3f, 4f, HOT_PINK, 1f, 10, now);
                    sparks(level, pos, up, q(26), 0.9, CHROME, 0.14f, 10);
                }
                victimFeedback(p, 1f);
            }
            case "door_counter_punch" -> {
                sound("hit_heavy", pos, 1.2f, 0.9f);
                if (drawn) impactStar(pos, dir, 8, 2f, 0.1f, WHITE, now);
                victimFeedback(p, 0.7f);
            }
            case "rough_stomp" -> {
                sound("rough_impact", pos, 1.4f, 0.8f);
                if (drawn) {
                    Flashes.ground(groundBelow(level, pos), 0.3f, 3.8f * s, JADE, 0.8f, 12, now);
                    Flashes.ripple(pos.add(0, 0.2, 0), up, 0.3f, 3.6f * s, ROUGH, 0.9f, 10, now);
                    sparks(level, pos, up, q(24), 0.9, ROUGH, 0.14f, 9);
                    debris(level, groundBelow(level, pos), q(12), 0.7);
                }
                distanceShake(pos, 22, 0.7f);
            }
            case "fever_suspend" -> {
                sound("shutter_rise", pos, 0.9f, 1.1f);
                if (drawn) Flashes.lens(pos, 0.2f, 1.8f, PINK, 0.7f, 8, now);
            }
            case "fever_crush" -> {
                sound("fever_break", pos, 1.3f, 0.85f);
                if (drawn) {
                    Flashes.ground(groundBelow(level, pos), 0.3f, 3f * s, PINK, 0.8f, 10, now);
                    impactStar(pos.add(0, 0.5, 0), new Vec3(0, -1, 0), 10, 2.6f * s, 0.12f, WHITE, now);
                    debris(level, groundBelow(level, pos), q(10), 0.6);
                }
                distanceShake(pos, 18, 0.7f);
            }
            case "lucky_finisher", "rushdown_finisher" -> {
                sound("fever_break", pos, 1.5f, 1.1f);
                sound("jackpot_heal", pos, 0.8f, 0.7f);
                if (drawn) {
                    Flashes.flash(pos, 4f, 0.6f, WHITE, 1f, 6, now);
                    Flashes.flash(pos, 3f, 0.5f, JADE, 0.9f, 8, now + 1);
                    impactStar(pos, dir, 14, 3.6f, 0.16f, JADE_LIGHT, now);
                    sparks(level, pos, dir, q(30), 1.1, JADE, 0.16f, 10);
                }
                victimFeedback(p, 1f);
                if (mine || isAttackerClose(mc, pos)) ScreenEffects.fovPunch(0.12f);
            }
            case "renewal_mark" -> {
                if (drawn) Flashes.ground(groundBelow(level, pos), 0.2f, 1.2f, JADE, 0.5f, 10, now);
            }
            case "renewal_trail" -> {
                if (drawn) for (int i = 0; i < q(10); i++) {
                    Vec3 at = pos.add(dir.scale(i / 10.0));
                    add(level, at, Vec3.ZERO, Sprite.CORE, JADE_LIGHT, 0.7f, 0.12f, 0.08f, 14);
                }
            }
            case "renewal" -> {
                sound("gamble_spin", pos, 1.2f, 0.7f);
                sound("jackpot_heal", pos, 1f, 1.4f);
                if (drawn) {
                    Flashes.lens(pos, 0.4f, 4f, JADE, 0.9f, 14, now);
                    Flashes.ripple(pos, up, 0.3f, 5f, JADE_LIGHT, 0.8f, 12, now);
                }
                if (mine) ScreenEffects.fovPunch(-0.08f);
            }
            default -> {}
        }
    }
}
