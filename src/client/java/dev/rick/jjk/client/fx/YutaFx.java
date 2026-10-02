package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.*;
import static dev.rick.jjk.client.fx.YujiFx.crescent;
import static dev.rick.jjk.client.fx.YujiFx.line;

/**
 * Cursed Partners' visual language, after the JJS GIFs. Yuta's cursed energy is a hot pink-magenta that runs along his
 * blade and leaves white-hot arcs with black shards; big bursts white the screen out in pink. Rika is black smoke and
 * pale grey; True Love and its beam are a blinding pink. The domain's techniques keep their owners' colours (Shrine
 * white, Thin Ice Breaker icy blue, Clairvoyance blood red, Cursed Speech ink, Shikigami white).
 */
final class YutaFx {
    static final float[] PINK = {0.97f, 0.42f, 1f};
    static final float[] PINK_LIGHT = {1f, 0.8f, 1f};
    static final float[] MAGENTA = {0.8f, 0.1f, 0.85f};
    static final float[] INK = YujiFx.INK;
    static final float[] SMOKE = {0.06f, 0.05f, 0.08f};
    static final float[] ICE = {0.62f, 0.9f, 1f};
    static final float[] BLOOD = YujiFx.BLOOD;
    static final float[] GOLD_RAY = {1f, 0.98f, 0.6f};

    private YutaFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 d = dir.lengthSqr() > 1e-4 ? dir.normalize() : new Vec3(0, 0, 1);
        switch (p.id()) {
            // --- Swordsmanship M1s and the steel arm ---
            case "yuta_swing", "yuta_steel_swing" -> {
                sound("swing", pos, 0.9f, 1.1f + RNG.nextFloat() * 0.2f);
                if (drawn && p.id().equals("yuta_swing")) crescent(pos, d, 1.2f, 0.05f, PINK_LIGHT, 0.8f * s, 3, now, RNG.nextFloat() - 0.5f);
            }
            case "yuta_hit_light", "yuta_steel_hit_light" -> {
                sound("hit_light", pos, 1f, 1f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    Flashes.flash(pos, 0.7f, 0.2f, WHITE, 1f, 3, now);
                    sparks(level, pos, d, q(5), 0.3, PINK, 0.06f, 5);
                }
                victimFeedback(p, 0.25f);
            }
            case "yuta_hit_heavy", "yuta_steel_hit_heavy", "yuta_hit_launch", "yuta_steel_hit_launch", "yuta_hit_slam", "yuta_steel_hit_slam" -> {
                sound(p.id().endsWith("slam") ? "hit_slam" : "hit_heavy", pos, 1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.4f, 0.3f, PINK_LIGHT, 1f, 4, now);
                    impactStar(pos, d, 8, 1.4f, 0.05f, PINK, now);
                    sparks(level, pos, d, q(10), 0.45, PINK, 0.08f, 7);
                }
                victimFeedback(p, 0.5f);
            }
            case "yuta_steel_jab" -> {
                sound("hit_light", pos, 0.9f, 1.3f);
                if (drawn) line(pos, pos.add(d.scale(1.0)), 0.06f, GREY, 0.8f, 3, now);
            }
            // --- Severing Path / Veilstep ---
            case "severing_ready" -> {
                sound("heavy_charge", pos, 0.8f, 1.4f);
                if (drawn) Flashes.flash(pos, 0.5f, 1.2f, PINK, 0.8f, 6, now);
            }
            case "severing_slide" -> {
                sound("dash", pos, 1f, 1.1f);
                if (drawn) {
                    // The blade sweeping the floor: a long pink trail along the ground.
                    Vec3 g = groundBelow(level, pos).add(0, 0.1, 0);
                    line(g, g.add(d.scale(5)), 0.12f, PINK, 0.9f, 10, now);
                    line(g, g.add(d.scale(5)), 0.04f, WHITE, 1f, 8, now);
                    burst(level, g, q(8), 0.12, Sprite.SPARK, PINK_LIGHT, 0.1f, 10);
                }
            }
            case "severing_sweep" -> {
                sound("dismantle_slash", pos, 1f, 1.2f);
                if (drawn) {
                    crescent(pos, d, 1.6f, 0.09f, PINK, 1f, 5, now, 0.1f);
                    shards(level, pos, d, q(8));
                }
                victimFeedback(p, 0.4f);
            }
            case "severing_swing", "fakeout_swing" -> {
                sound("dismantle_slash", pos, 1f, 1f + RNG.nextFloat() * 0.3f);
                if (drawn) {
                    crescent(pos, d, 1.5f * s, 0.08f, PINK, 1f, 5, now, RNG.nextFloat() * 1.6f - 0.8f);
                    crescent(pos, d, 1.4f * s, 0.03f, WHITE, 1f, 4, now, RNG.nextFloat() - 0.5f);
                    shards(level, pos, d, q(6));
                }
            }
            case "severing_finisher" -> {
                sound("dismantle_finish", pos, 1.4f, 1f);
                if (drawn) {
                    crescent(pos.add(0, 0.6, 0), d, 2.2f, 0.12f, PINK, 1f, 8, now, 0f);
                    YujiFx.blood(level, pos.add(0, 0.7, 0), up, q(16));
                }
                if (mine) ScreenEffects.flash(0x70F76BFF, 5);
            }
            case "veilstep" -> {
                sound("side_dash", pos, 1f, 0.9f);
                if (drawn) {
                    afterimage(level, pos);
                    burst(level, pos, q(10), 0.15, Sprite.SMOKE, GREY, 0.5f, 14);
                }
            }
            // --- Resolute Slash ---
            case "resolute_vanish", "resolute_appear", "elbow_appear" -> {
                sound(p.id().equals("resolute_vanish") ? "teleport_out" : "teleport_in", pos, 0.8f, 1.2f);
                if (drawn) {
                    burst(level, pos, q(14), 0.18, Sprite.SMOKE, SMOKE, 0.6f, 14);
                    Flashes.flash(pos, 0.4f, 1.6f, PINK, 0.6f, 5, now);
                }
            }
            case "resolute_slash" -> {
                sound("dismantle_spin", pos, 1f, 1.2f);
                if (drawn) {
                    crescent(pos.add(d.scale(1.2)), d, 2.2f, 0.1f, PINK, 1f, 6, now, 0.25f);
                    crescent(pos.add(d.scale(1.2)), d, 2.1f, 0.03f, WHITE, 1f, 5, now, 0.25f);
                }
            }
            case "resolute_hit" -> {
                sound("hit_heavy", pos, 1.2f, 1.1f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.3f, WHITE, 1f, 4, now);
                    shards(level, pos, d, q(10));
                }
                victimFeedback(p, 0.6f);
            }
            case "resolute_finisher" -> {
                sound("dismantle_finish", pos, 1.4f, 1.1f);
                if (drawn) {
                    line(pos.add(d.cross(up).scale(-1.5)), pos.add(d.cross(up).scale(1.5)), 0.08f, WHITE, 1f, 8, now);
                    YujiFx.blood(level, pos.add(0, 0.6, 0), up, q(18));
                }
            }
            case "resolute_flash_ready" -> {
                sound("black_flash_windup", pos, 1f, 1f);
                if (drawn) Flashes.flash(pos, 0.3f, 1.4f, YujiFx.BF_RED, 0.8f, 6, now);
            }
            case "resolute_black_flash" -> {
                sound("black_flash", pos, 1.4f, 1f);
                if (drawn) YujiFx.blackFlash(level, pos, d, Math.max(1f, s), now, false);
                if (mine || isAttackerClose(mc, pos)) ScreenEffects.flash(0x90000000, 3);
                distanceShake(pos, 24, 0.7f);
            }
            case "resolute_flash_finisher", "rika_throw_finisher" -> {
                sound("kokusen", pos, 1.4f, 1f);
                if (drawn) {
                    YujiFx.blackFlash(level, pos, d, 1.6f, now, true);
                    YujiFx.blood(level, pos, d, q(12));
                }
                if (mine) ScreenEffects.flash(0xA0000000, 5);
            }
            // --- Outburst ---
            case "outburst_grip" -> {
                sound("heavy_charge", pos, 1f, 0.8f);
                if (drawn) Flashes.swirl(pos, up, 0.4f, 1.4f, 3f, 2f, 0.08f, PINK, 0.8f, 12, now);
            }
            case "outburst_stage" -> {
                sound("gamble_signal", pos, 0.6f, 1f + s * 0.15f);
                if (drawn) {
                    Flashes.ring(pos, 0.5f, 1.2f + s * 0.5f, PINK, 0.8f, 8, now);
                    sparks(level, pos, up, q(4 + (int) s * 2), 0.2, PINK_LIGHT, 0.06f, 8);
                }
            }
            case "outburst_swing" -> {
                sound("swing_heavy", pos, 1f, 1f);
                if (drawn) crescent(pos.add(d.scale(0.8)), d, 2f * s, 0.12f, PINK, 1f, 5, now, -0.3f);
            }
            case "outburst_burst" -> {
                // A giant pink-white burst, whiting out everything nearby (GIF frame 52).
                sound("red_explosion", pos, 1.4f, 1.3f);
                sound("dismantle_finish", pos, 1f, 0.8f);
                if (drawn) {
                    Flashes.flash(pos, 0.6f, s * 1.2f, PINK_LIGHT, 1f, 8, now);
                    Flashes.flash(pos, 0.3f, s * 0.8f, WHITE, 1f, 5, now);
                    Flashes.ripple(pos, up, 0.5f, s * 1.3f, PINK, 0.8f, 10, now);
                    sphereShell(level, pos, s * 0.6, q(30), PINK, 0.2f, 14, 0.25);
                    shards(level, pos, up, q(14));
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < s * 1.6) ScreenEffects.flash(0xB0FFD8FF, 5);
                distanceShake(pos, s * 4, 0.7f);
            }
            case "outburst_hit" -> {
                sound("hit_heavy", pos, 0.9f, 1.2f);
                if (drawn) sparks(level, pos, up, q(8), 0.4, PINK, 0.08f, 7);
                victimFeedback(p, 0.5f);
            }
            case "outburst_parry" -> {
                sound("parry", pos, 1.3f, 1.1f);
                if (drawn) {
                    Flashes.flash(pos, 2f, 0.3f, WHITE, 1f, 5, now);
                    crescent(pos, d, 1.4f, 0.1f, PINK, 1f, 5, now, -0.2f);
                    impactStar(pos, d, 10, 1.8f, 0.06f, PINK_LIGHT, now);
                }
                if (mine) ScreenEffects.fovPunch(-0.06f);
            }
            case "outburst_finisher" -> {
                sound("dismantle_finish", pos, 1.5f, 0.9f);
                if (drawn) {
                    Flashes.flash(pos, 3f, 0.4f, PINK, 1f, 6, now);
                    YujiFx.blood(level, pos, up, q(24));
                }
            }
            // --- Second Wind ---
            case "second_wind" -> {
                sound("surge_dash", pos, 1f, 1.1f);
                if (drawn) {
                    Vec3 g = groundBelow(level, pos);
                    Flashes.ground(g, 0.3f, 2.2f, PINK, 0.7f, 8, now);
                    burst(level, g.add(0, 0.1, 0), q(10), 0.2, Sprite.GLOW, PINK, 0.25f, 10);
                    line(pos, pos.add(d.scale(5.5)), 0.1f, PINK_LIGHT, 0.6f, 8, now);
                }
            }
            case "second_wind_slam" -> {
                sound("rush_slam", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.ground(pos, 0.5f, 3f, PINK, 0.8f, 10, now);
                    debris(level, pos, q(10), 0.3);
                }
                distanceShake(pos, 14, 0.5f);
            }
            case "second_wind_finisher" -> {
                sound("crushing_impact", pos, 1.4f, 1f);
                if (drawn) {
                    debris(level, pos, q(16), 0.4);
                    YujiFx.blood(level, pos, up, q(16));
                }
            }
            // --- Rika ---
            case "rika_summon", "rika_voice" -> {
                if (p.id().equals("rika_voice")) {
                    sound("shrine_voice", pos, 0.5f, 1.6f);
                    return;
                }
                sound("domain_charge", pos, 0.9f, 1.4f);
                if (drawn) {
                    // She billows out of black smoke.
                    for (int i = 0; i < q(30); i++) {
                        Vec3 at = pos.add(gauss(0.7), gauss(1.0), gauss(0.7));
                        add(level, at, new Vec3(gauss(0.02), 0.03 + RNG.nextDouble() * 0.04, gauss(0.02)), Sprite.SMOKE, SMOKE, 0.85f, 0.7f, 1.4f, 26 + RNG.nextInt(10));
                    }
                }
            }
            case "rika_dismiss", "true_love_end" -> {
                sound("infinity_off", pos, 0.8f, 0.6f);
                if (drawn) burst(level, pos, q(24), 0.08, Sprite.SMOKE, SMOKE, 0.9f, 24);
            }
            case "rika_ring_glow", "rika_station" -> {
                sound("infinity_on", pos, 0.5f, 1.6f);
                if (drawn) Flashes.flash(pos, 0.2f, 0.7f, PINK, 0.8f, 8, now);
            }
            case "rika_fist_grow", "rika_haymaker_windup" -> {
                sound("crushing_charge", pos, 1f, 0.7f);
                if (drawn) burst(level, pos, q(10), 0.06, Sprite.SMOKE, SMOKE, 0.6f, 14);
            }
            case "rika_smash", "rika_dunk", "rika_downslam", "rika_double_slam", "rika_slam" -> {
                sound(s > 1.1f ? "ground_impact" : "ground_impact", pos, 1.3f, 0.8f);
                if (drawn) {
                    Flashes.ground(pos, 0.5f, 3.5f * s, GREY, 0.7f, 12, now);
                    Flashes.ripple(pos, up, 0.5f, 4f * s, WHITE, 0.6f, 10, now);
                    debris(level, pos, q(18), 0.45);
                    burst(level, pos, q(12), 0.15, Sprite.SMOKE, GREY, 0.8f, 18);
                }
                distanceShake(pos, 20, 0.7f);
            }
            case "rika_haymaker" -> {
                sound("overwhelm_fist", pos, 1.3f, 0.7f);
                if (drawn) {
                    Flashes.flash(pos, 2.2f, 0.4f, WHITE, 1f, 5, now);
                    Flashes.ripple(pos, d, 0.5f, 3f, GREY, 0.7f, 8, now);
                    impactStar(pos, d, 12, 2.4f, 0.08f, WHITE, now);
                }
                distanceShake(pos, 20, 0.6f);
            }
            case "rika_launch" -> {
                sound("surge_launch", pos, 1f, 1f);
                if (drawn) {
                    burst(level, pos, q(12), 0.2, Sprite.SMOKE, GREY, 0.6f, 14);
                    Flashes.ripple(pos, d, 0.3f, 2f, WHITE, 0.6f, 6, now);
                }
            }
            case "rika_smash_finisher", "rika_crush_finisher" -> {
                sound("manji_crush", pos, 1.5f, 0.8f);
                if (drawn) {
                    YujiFx.blood(level, pos, up, q(30));
                    Flashes.ground(groundBelow(level, pos), 0.5f, 2.5f, BLOOD, 0.9f, 60, now);
                }
            }
            case "rika_throw" -> {
                sound("swing_heavy", pos, 1.3f, 0.6f);
                if (drawn) burst(level, pos, q(12), 0.25, Sprite.SMOKE, GREY, 0.6f, 12);
            }
            case "rika_throw_crash" -> {
                sound("ragdoll_fall", pos, 1.2f, 1f);
                if (drawn) debris(level, pos, q(12), 0.35);
            }
            // --- True Love ---
            case "true_love_start" -> {
                // "Come, Rika. Give me everything."
                sound("awaken", pos, 1.2f, 0.9f);
                if (drawn) Flashes.swirl(pos, up, 0.5f, 2.5f, 4f, 1.5f, 0.1f, PINK, 0.7f, 30, now);
            }
            case "true_love_ring" -> {
                sound("gamble_signal", pos, 1f, 1.6f);
                if (drawn) {
                    Flashes.flash(pos, 0.2f, 0.9f, PINK_LIGHT, 1f, 10, now);
                    sparks(level, pos, up, q(8), 0.15, PINK, 0.05f, 10);
                }
            }
            case "true_love_manifest" -> {
                sound("sukuna_awaken", pos, 1.3f, 1.2f);
                sound("domain_expand", pos, 0.8f, 1.4f);
                if (drawn) {
                    Flashes.flash(pos, 1f, 6f, PINK, 0.9f, 14, now);
                    Flashes.ripple(pos, up, 0.5f, 9f, PINK_LIGHT, 0.8f, 16, now);
                    burst(level, pos.add(d.scale(-1.5)), q(40), 0.12, Sprite.SMOKE, SMOKE, 1f, 30);
                }
                if (mine) ScreenEffects.flash(0x90F76BFF, 8);
                distanceShake(pos, 32, 0.8f);
            }
            case "steel_arm" -> {
                sound("door_slam", pos, 0.8f, 1.6f);
                if (drawn) sparks(level, pos, d, q(10), 0.25, GREY, 0.06f, 8);
            }
            // --- Elbow Rush ---
            case "elbow_dash" -> {
                sound("surge_dash", pos, 1f, 1f);
                if (drawn) line(pos, pos.add(d.scale(6)), 0.12f, PINK, 0.6f, 8, now);
            }
            case "elbow_hit" -> {
                sound("crushing_fist", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 2f, 0.3f, WHITE, 1f, 5, now);
                    impactStar(pos, d, 10, 2f, 0.07f, PINK, now);
                }
                victimFeedback(p, 0.7f);
            }
            // --- Copy ---
            case "copy_learn" -> {
                sound("gamble_visual", pos, 0.8f, 1.4f);
                if (drawn) Flashes.flash(pos, 0.3f, 1.2f, PINK_LIGHT, 0.8f, 10, now);
            }
            case "copy_use" -> {
                if (drawn) Flashes.ring(pos, 0.3f, 1.4f, PINK_LIGHT, 0.6f, 8, now);
            }
            case "copy_wheel_open", "copy_wheel_close", "copy_wheel_tick" -> {
                if (mine) sound("rhythm_tick", pos, 0.6f, p.id().endsWith("open") ? 1.3f : p.id().endsWith("close") ? 0.9f : 1.6f);
            }
            case "speech_marks" -> {
                sound("shrine_ready", pos, 0.6f, 1.5f);
                if (drawn) Flashes.flash(pos, 0.1f, 0.5f, WHITE, 0.9f, 10, now);
            }
            case "speech_dont_move", "speech_stop", "speech_plummet", "speech_die" -> {
                // Cursed Speech: the command rolls out in a white shockwave.
                sound("shrine_voice", pos, 1.2f, p.id().equals("speech_die") ? 0.7f : 1.1f);
                sound("infinity_ripple", pos, 1f, 0.6f);
                if (drawn) {
                    float r = p.id().equals("speech_plummet") || p.id().equals("speech_die") ? 4f : Math.max(4f, s);
                    Flashes.ripple(pos, up, 0.3f, r, WHITE, 0.7f, 14, now);
                    Flashes.ripple(pos, d, 0.3f, r * 0.6f, GREY, 0.5f, 12, now);
                }
                if (mine) ScreenEffects.shake(0.3f, 8);
            }
            case "speech_bound" -> {
                if (drawn) Flashes.ring(pos, 0.9f, 0.5f, WHITE, 0.7f, 20, now);
            }
            case "speech_die_finisher" -> {
                sound("manji_crush", pos, 1.4f, 1f);
                if (drawn) YujiFx.blood(level, pos, up, q(36));
            }
            // --- Energy Ripple / Fakeout ---
            case "ripple_charge" -> {
                sound("blue_cast", pos, 1f, 1.3f);
                if (drawn) Flashes.swirl(pos, up, 0.3f, 1.2f, 3f, 2.5f, 0.08f, PINK, 0.8f, 14, now);
            }
            case "energy_ripple" -> {
                sound("rough_impact", pos, 1.4f, 1f);
                sound("infinity_ripple", pos, 1.2f, 0.7f);
                if (drawn) {
                    for (int i = 0; i < 3; i++) Flashes.ripple(pos, up, 0.3f, s * (0.6f + i * 0.25f), i == 1 ? WHITE : PINK, 0.85f, 10 + i * 4, now + i * 2);
                    Flashes.ground(pos, 0.5f, s, PINK, 0.6f, 14, now);
                    ring3d(level, pos.add(0, 0.2, 0), up, 1.0, q(30), 0.6, PINK, 0.15f, 12);
                    debris(level, pos, q(10), 0.4);
                }
                distanceShake(pos, s * 3, 0.8f);
            }
            case "ripple_hit" -> {
                sound("hit_heavy", pos, 1f, 1.1f);
                if (drawn) sparks(level, pos, d, q(8), 0.4, PINK, 0.08f, 7);
                victimFeedback(p, 0.6f);
            }
            case "fakeout_burst" -> {
                sound("red_explosion", pos, 1.2f, 1.4f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 3.5f, PINK_LIGHT, 1f, 8, now);
                    sphereShell(level, pos, 1.2, q(20), PINK, 0.16f, 12, 0.3);
                }
            }
            case "fakeout_finisher" -> {
                sound("dismantle_finish", pos, 1.4f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 4f, PINK, 1f, 8, now);
                    YujiFx.blood(level, pos, up, q(28));
                }
            }
            // --- True Love Beam ---
            case "beam_orb" -> {
                sound("red_charge", pos, 1f, 1.4f);
                if (drawn) Flashes.flash(pos, 0.1f, 0.5f, PINK, 1f, 24, now);
            }
            case "beam_rika_eye" -> {
                sound("max_red_charge", pos, 1.2f, 1.2f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 1.6f, PINK, 1f, 36, now);
                    Flashes.swirl(pos, d, 0.4f, 2.4f, 4f, 3f, 0.08f, PINK_LIGHT, 0.8f, 36, now);
                }
            }
            case "true_love_beam", "beam_quick" -> {
                boolean big = p.id().equals("true_love_beam");
                sound(big ? "purple_fire" : "red_fire", pos, 1.6f, big ? 1.2f : 1.4f);
                if (drawn) {
                    Vec3 end = pos.add(d.scale(s));
                    int life = big ? 30 : 8;
                    Flashes.beam(pos, end, big ? 2.4f : 0.9f, PINK, 0.9f, life, now);
                    Flashes.beam(pos, end, big ? 1.2f : 0.4f, PINK_LIGHT, 1f, life, now);
                    Flashes.beam(pos, end, big ? 0.5f : 0.15f, WHITE, 1f, life, now);
                    Flashes.flash(pos, 1f, big ? 5f : 2f, PINK_LIGHT, 1f, big ? 16 : 6, now);
                    for (int i = 0; i < q(big ? 30 : 10); i++) {
                        Vec3 at = pos.add(d.scale(RNG.nextDouble() * s));
                        add(level, at, randomUnit().scale(0.15), Sprite.GLOW, PINK, 0.8f, big ? 0.6f : 0.25f, 0.05f, 16);
                    }
                }
                if (big && mc.player != null && mc.player.position().distanceTo(pos) < s) ScreenEffects.flash(0x80FF9CFF, 10);
                distanceShake(pos, big ? 64 : 24, big ? 1.2f : 0.5f);
            }
            case "beam_hit" -> {
                sound("hit_heavy", pos, 1f, 0.8f);
                victimFeedback(p, 0.9f);
            }
            case "beam_finisher" -> {
                // Atomized into black mist.
                sound("purple_end", pos, 1.4f, 1.2f);
                if (drawn) burst(level, pos, q(40), 0.15, Sprite.SMOKE, SMOKE, 1f, 30);
            }
            // --- Authentic Mutual Love ---
            case "aml_charge" -> {
                sound("domain_charge", pos, 1.2f, 1.1f);
                if (drawn) Flashes.swirl(pos, up, 0.5f, 3f, 4f, 1.2f, 0.1f, PINK, 0.7f, 36, now);
            }
            case "blade_land" -> {
                sound("door_block", pos, 0.6f, 1.6f);
                if (drawn) {
                    Flashes.ground(pos, 0.2f, 1f, WHITE, 0.6f, 8, now);
                    debris(level, pos, q(4), 0.15);
                }
            }
            case "blade_rain" -> {
                if (drawn) {
                    // Swords falling far off, out of reach.
                    for (int i = 0; i < 2; i++) {
                        double a = RNG.nextDouble() * Math.PI * 2, r = s * (0.5 + RNG.nextDouble() * 0.4);
                        Vec3 at = pos.add(Math.cos(a) * r, RNG.nextDouble() * 4, Math.sin(a) * r);
                        line(at, at.add(0, -3, 0), 0.04f, GREY, 0.7f, 6, now);
                    }
                }
            }
            case "blade_pickup" -> {
                sound("shrine_ready", pos, 0.9f, 1.2f);
                if (drawn) Flashes.flash(pos, 0.3f, 1.6f, techColor((int) s), 0.9f, 8, now);
            }
            case "blade_swing" -> {
                sound("dismantle_slash", pos, 1.2f, 1f);
                if (drawn) {
                    float[] c = techColor((int) s);
                    crescent(pos.add(d.scale(0.9)), d, 2f, 0.1f, c == INK ? PINK : c, 1f, 6, now, -0.2f);
                    crescent(pos.add(d.scale(0.9)), d, 1.9f, 0.03f, WHITE, 1f, 5, now, -0.2f);
                }
            }
            case "aml_cleave" -> {
                sound("dismantle_spin", pos, 1.3f, 1f);
                if (drawn) {
                    for (int i = 0; i < 4; i++) crescent(pos, randomUnit(), 1.4f, 0.07f, WHITE, 1f, 6 + i, now + i, RNG.nextFloat() * 3f);
                    YujiFx.blood(level, pos, d, q(8));
                }
            }
            case "aml_dismantle" -> {
                sound("dismantle_finish", pos, 1.4f, 1f);
                if (drawn) {
                    Vec3 side = d.cross(up).normalize();
                    Vec3 c0 = pos.add(d.scale(7));
                    line(c0.subtract(side.scale(3)), c0.add(side.scale(3)), 0.12f, WHITE, 1f, 8, now);
                    line(pos, pos.add(d.scale(14)), 0.05f, WHITE, 0.6f, 6, now);
                }
            }
            case "aml_shrine_finisher", "aml_bisect_finisher" -> {
                sound("dismantle_finish", pos, 1.5f, 0.9f);
                if (drawn) YujiFx.blood(level, pos, up, q(30));
            }
            case "thin_ice_breaker" -> {
                // The sky breaks like a thin veil of ice.
                sound("purple_collision", pos, 1.2f, 1.6f);
                if (drawn) {
                    for (int i = 0; i < 14; i++) {
                        Vec3 a = pos.add(gauss(0.3), gauss(0.3), gauss(0.3));
                        line(a, a.add(randomUnit().scale(1.5 + RNG.nextDouble() * 2) .scale(s)), 0.05f, i % 2 == 0 ? ICE : WHITE, 1f, 8, now);
                    }
                    Flashes.flash(pos, 2.5f * s, 0.5f, ICE, 1f, 6, now);
                    burst(level, pos, q(16), 0.35, Sprite.SHARD, ICE, 0.18f, 18);
                }
                if (mine) ScreenEffects.flash(0x80CFF4FF, 4);
            }
            case "clairvoyance_mark" -> {
                sound("gamble_stop", pos, 1f, 1.3f);
                if (drawn) {
                    // A blood-drawn manga panel.
                    Flashes.flash(pos.add(0, 1.2, 0), 0.8f, 0.8f, WHITE, 0.9f, 20, now);
                    YujiFx.blood(level, pos, d, q(6));
                }
            }
            case "shikigami_swarm" -> {
                sound("blue_hum", pos, 0.8f, 1.6f);
                if (drawn) burst(level, pos, q(12), 0.15, Sprite.SMOKE, SMOKE, 0.5f, 16);
            }
            case "shikigami_bite" -> {
                sound("hit_light", pos, 0.9f, 1.3f);
                if (drawn) {
                    for (int i = 0; i < 3; i++) {
                        Vec3 at = pos.add(randomUnit().scale(0.8));
                        Flashes.flash(at, 0.4f, 0.1f, WHITE, 0.9f, 5, now);
                    }
                    YujiFx.blood(level, pos, d, q(3));
                }
            }
            case "shikigami_finisher" -> {
                sound("manji_crush", pos, 1.2f, 1.2f);
                if (drawn) YujiFx.blood(level, pos, up, q(24));
            }
            case "jacobs_ladder" -> {
                // A divine ray from the sky.
                sound("jackpot", pos, 1.2f, 1.4f);
                if (drawn) {
                    Vec3 top = pos.add(0, 40, 0);
                    int life = Math.max(20, (int) s);
                    Flashes.beam(top, pos.add(0, -0.5, 0), 2.6f, GOLD_RAY, 0.55f, life, now);
                    Flashes.beam(top, pos.add(0, -0.5, 0), 1.0f, WHITE, 0.8f, life, now);
                    Flashes.ground(pos, 0.5f, 3f, GOLD_RAY, 0.7f, life, now);
                }
                if (mine) ScreenEffects.flash(0x60FFF6B0, 10);
            }
            case "ladder_hit" -> {
                if (drawn) sparks(level, pos, up, q(6), 0.2, GOLD_RAY, 0.08f, 12);
                victimFeedback(p, 0.3f);
            }
            case "jacobs_ladder_finisher" -> {
                sound("jackpot_end", pos, 1.3f, 1.4f);
                if (drawn) {
                    // The soul keeps rising while the body falls.
                    for (int i = 0; i < q(20); i++) {
                        Vec3 at = pos.add(gauss(0.3), RNG.nextDouble() * 1.8, gauss(0.3));
                        add(level, at, new Vec3(0, 0.08 + RNG.nextDouble() * 0.05, 0), Sprite.GLOW, WHITE, 0.8f, 0.3f, 0.1f, 50);
                    }
                }
            }
            default -> {}
        }
    }

    private static float[] techColor(int index) {
        return switch (index) {
            case 1 -> ICE;
            case 2 -> BLOOD;
            case 3 -> INK;
            default -> WHITE;
        };
    }

    /** Black shards scattered by a cursed-energy cut (GIF). */
    private static void shards(ClientLevel level, Vec3 pos, Vec3 dir, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(0.25).add(dir.scale(0.15));
            add(level, pos, v, Sprite.SHARD, i % 3 == 0 ? PINK : INK, 1f, 0.16f + RNG.nextFloat() * 0.1f, 0.05f, 14 + RNG.nextInt(8)).gravity(0.5f);
        }
    }
}
