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

    /** Each attacker's place in the M1 string: {last swing tick, index 0-3}. The swing effects carry no index. */
    private static final java.util.Map<Integer, long[]> CHAIN = new java.util.HashMap<>();
    private static int lastSwing;
    private static long lastSwingAt = -100;

    /** True Love Beam: each caster's shape (radius, grow, collapse), sent just before it fires. */
    private static final java.util.Map<Integer, double[]> SHAPES = new java.util.HashMap<>();

    private YutaFx() {}

    /** Where a beam from {@code from} along {@code d} first meets the world (it goes on through: this is where it strikes). */
    private static Vec3 beamStrike(ClientLevel level, Vec3 from, Vec3 d, double reach) {
        Vec3 end = from.add(d.scale(reach));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** Within {@code margin} of the beam's axis. */
    private static boolean closeTo(Vec3 p, Vec3 from, Vec3 d, double reach, double margin) {
        Vec3 rel = p.subtract(from);
        double s = Math.max(0, Math.min(reach, rel.dot(d)));
        return rel.subtract(d.scale(s)).length() < margin;
    }

    /** The next swing of this attacker's string (a heavy or the 4th is swing 4; a pause starts it over). */
    private static int swingIndex(int attacker, long now, boolean heavy) {
        if (CHAIN.size() > 64) CHAIN.values().removeIf(c -> now - c[0] > 200 || now < c[0]);
        long[] c = CHAIN.computeIfAbsent(attacker, k -> new long[] {-100, -1});
        int i = now - c[0] > 30 || now < c[0] ? 0 : (int) ((c[1] + 1) % 4);
        if (heavy) i = 3;
        c[0] = now;
        c[1] = i;
        lastSwing = i;
        lastSwingAt = now;
        return i;
    }

    /** The hit that goes with the latest swing (hit effects name the victim, not the attacker). */
    private static int hitIndex(long now) {
        return now - lastSwingAt <= 12 && now >= lastSwingAt ? lastSwing : 0;
    }

    /** A melee hit's visuals, for the hits whose sound comes from the move itself. */
    private static void impact(FxPayload p, ClientLevel level, Vec3 pos, Vec3 d, boolean heavy, boolean drawn, long now) {
        if (drawn) {
            if (heavy) {
                Flashes.flash(pos, 1.4f, 0.3f, PINK_LIGHT, 1f, 4, now);
                impactStar(pos, d, 8, 1.4f, 0.05f, PINK, now);
                sparks(level, pos, d, q(10), 0.45, PINK, 0.08f, 7);
            } else {
                Flashes.flash(pos, 0.7f, 0.2f, WHITE, 1f, 3, now);
                sparks(level, pos, d, q(5), 0.3, PINK, 0.06f, 5);
            }
        }
        victimFeedback(p, heavy ? 0.5f : 0.25f);
    }

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 d = dir.lengthSqr() > 1e-4 ? dir.normalize() : new Vec3(0, 0, 1);
        switch (p.id()) {
            // --- Swordsmanship M1s and the steel arm ---
            case "yuta_swing" -> {
                // Swordsmanship's four swings, in order (JJS Yuta M1 Swing1-4).
                sound("yuta_swing_" + (swingIndex(p.entityId(), now, s >= 1.5f) + 1), pos, 1f, 0.95f + RNG.nextFloat() * 0.1f);
                // The katana's arc: a broad pink band with a white edge (Swordsmanship GIF).
                if (drawn) slash(pos.add(d.scale(0.5)), d, 1.7f * s, RNG.nextFloat() * 1.4f - 0.7f, 5, now);
            }
            case "yuta_steel_swing" -> {
                // The steel-cased fist: the plain punch swing.
                swingIndex(p.entityId(), now, s >= 1.5f);
                sound("swing", pos, 0.9f, 0.9f + RNG.nextFloat() * 0.2f);
                if (drawn) line(pos, pos.add(d.scale(1.2)), 0.08f, PINK_LIGHT, 0.5f, 3, now);
            }
            case "yuta_hit_light", "yuta_steel_hit_light" -> {
                int i = hitIndex(now);
                sound(i >= 3 ? "hit_heavy" : "yuta_hit_" + (i + 1), pos, 1f, 0.95f + RNG.nextFloat() * 0.1f);
                if (p.id().startsWith("yuta_steel")) sound("yuta_metal_hit_" + (i % 3 + 1), pos, 0.8f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 0.7f, 0.2f, WHITE, 1f, 3, now);
                    sparks(level, pos, d, q(5), 0.3, PINK, 0.06f, 5);
                }
                victimFeedback(p, 0.25f);
            }
            case "yuta_hit_heavy", "yuta_steel_hit_heavy", "yuta_hit_launch", "yuta_steel_hit_launch", "yuta_hit_slam", "yuta_steel_hit_slam" -> {
                // The 4th hit; the uppercut and the downslam have their own (JJS HitUp, HitDown).
                sound(p.id().endsWith("slam") ? "yuta_hit_down" : p.id().endsWith("launch") ? "yuta_hit_up" : "hit_heavy", pos, 1f, 1f);
                if (p.id().startsWith("yuta_steel")) sound("yuta_metal_hit_2", pos, 0.9f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.4f, 0.3f, PINK_LIGHT, 1f, 4, now);
                    impactStar(pos, d, 8, 1.4f, 0.05f, PINK, now);
                    sparks(level, pos, d, q(10), 0.45, PINK, 0.08f, 7);
                }
                victimFeedback(p, 0.5f);
            }
            case "yuta_steel_jab" -> {
                sound("yuta_metal_hit_3", pos, 0.7f, 1.2f);
                if (drawn) line(pos, pos.add(d.scale(1.0)), 0.06f, GREY, 0.8f, 3, now);
            }
            // --- Severing Path / Veilstep ---
            case "severing_ready" -> {
                // The blade raised and cursed energy bursting off it, pink with black rims (GIF frame 12).
                sound("severing_start", pos, 1.2f, 1f);
                if (drawn) {
                    Vec3 tip = pos.add(0, 1.4, 0);
                    Flashes.flash(tip, 0.4f, 2.4f, PINK, 0.9f, 7, now);
                    Flashes.flash(tip, 0.2f, 1.0f, WHITE, 1f, 4, now);
                    splat(level, tip, q(16), 0.28);
                }
            }
            case "severing_slide" -> {
                if (drawn) {
                    // The blade sweeping the floor: a broad pink wake along the ground, sparks thrown back.
                    Vec3 g = groundBelow(level, pos).add(0, 0.12, 0);
                    Vec3 side = d.cross(new Vec3(0, 1, 0)).normalize();
                    for (int i = -1; i <= 1; i++) line(g.add(side.scale(0.35 * i)), g.add(side.scale(0.35 * i)).add(d.scale(5)), 0.22f, PINK, 0.55f, 12, now);
                    line(g, g.add(d.scale(5)), 0.06f, WHITE, 1f, 10, now);
                    Flashes.ground(g, 0.4f, 2.2f, PINK, 0.6f, 10, now);
                    sparks(level, g, d.scale(-1).add(0, 0.4, 0), q(14), 0.35, PINK_LIGHT, 0.08f, 10);
                }
            }
            case "severing_sweep" -> {
                sound("severing_hit_1", pos, 1.1f, 1f);
                if (drawn) {
                    slash(pos.add(0, -0.5, 0), d, 2.0f, 0.05f, 6, now);
                    splat(level, pos, q(10), 0.25);
                    Flashes.flash(pos, 1.4f, 0.3f, WHITE, 1f, 4, now);
                }
                victimFeedback(p, 0.4f);
            }
            case "severing_swing_1", "severing_swing_2", "severing_swing_3", "fakeout_swing" -> {
                sound(p.id().equals("fakeout_swing") ? "fakeout_swing" : p.id(), pos, 1.1f, 1f);
                if (drawn) {
                    slash(pos, d, 1.6f * s, RNG.nextFloat() * 1.8f - 0.9f, 6, now);
                    splat(level, pos, q(8), 0.3);
                    impactStar(pos, d, 6, 1.2f * s, 0.04f, WHITE, now);
                }
            }
            case "severing_hit_2", "severing_hit_3", "severing_hit_4", "veilstep_hit", "blade_hit", "outburst_draw_hit", "fakeout_hit",
                 "pummel_hit", "pummel_slash", "pummel_kick", "pummel_final", "second_wind_grab", "rika_throw_hit", "elbow_final" -> {
                String id = p.id();
                boolean heavy = id.equals("severing_hit_4") || id.equals("veilstep_hit") || id.equals("pummel_kick") || id.equals("pummel_final")
                        || id.equals("rika_throw_hit") || id.equals("elbow_final");
                switch (id) {
                    case "veilstep_hit", "pummel_final", "rika_throw_hit" -> sound("yuta_heavy_hit", pos, 1.2f, 1f);
                    case "blade_hit" -> sound("yuta_hit_3", pos, 1.1f, 1f);
                    case "outburst_draw_hit" -> sound("yuta_hit_1", pos, 1f, 1f);
                    case "pummel_hit" -> sound("yuta_hit_" + (RNG.nextInt(3) + 1), pos, 1f, 1f);
                    case "pummel_slash" -> sound("severing_hit_" + (RNG.nextInt(3) + 1), pos, 1f, 1f);
                    case "pummel_kick" -> sound("yuta_hit_down", pos, 1.1f, 1f);
                    case "elbow_final" -> sound("elbow_final", pos, 1.3f, 1f);
                    default -> sound(id, pos, 1.1f, 1f);
                }
                impact(p, level, pos, d, heavy, drawn, now);
            }
            case "yuta_impact", "yuta_impact_light", "elbow_barrage_hit", "rika_grab", "aml_cleave_hit" -> {
                // The move's own effect already carries the sound (or, for the barrage, its own long clip does).
                if (p.id().equals("aml_cleave_hit")) sound("aml_cleave", pos, 1f, 0.95f + RNG.nextFloat() * 0.1f);
                if (p.id().equals("rika_grab")) sound("yuta_hit_1", pos, 0.8f, 0.8f);
                impact(p, level, pos, d, p.id().equals("yuta_impact"), drawn, now);
            }
            case "severing_finisher" -> {
                sound("severing_hit_4", pos, 1.4f, 0.9f);
                sound("yuta_crush", pos, 0.8f, 1.1f);
                if (drawn) {
                    // The beheading: a last wide pink arc at neck height and a burst of blood.
                    slash(pos.add(0, 0.6, 0), d, 2.4f, 0.1f, 9, now);
                    Flashes.flash(pos.add(0, 0.6, 0), 2.2f, 0.4f, WHITE, 1f, 5, now);
                    YujiFx.blood(level, pos.add(0, 0.7, 0), up, q(20));
                }
                if (mine) ScreenEffects.flash(0x70F76BFF, 5);
            }
            case "veilstep" -> {
                sound("veilstep", pos, 1.1f, 1f);
                if (drawn) {
                    afterimage(level, pos);
                    burst(level, pos, q(10), 0.15, Sprite.SMOKE, GREY, 0.5f, 14);
                }
            }
            // --- Resolute Slash ---
            case "resolute_vanish", "resolute_appear", "elbow_appear" -> {
                sound(p.id().equals("resolute_vanish") ? "resolute_grab" : p.id().equals("elbow_appear") ? "elbow_teleport" : "resolute_leap", pos, 1f, 1f);
                if (p.id().equals("elbow_appear")) sound("elbow_barrage", pos, 1.1f, 1f);
                if (drawn) {
                    smoke(level, pos, q(16), 0.6, 0.9f, 1.8f, 16);
                    Flashes.flash(pos, 0.4f, 1.8f, PINK, 0.6f, 5, now);
                }
            }
            case "resolute_slash" -> {
                sound("resolute_swing", pos, 1.1f, 1f);
                if (drawn) {
                    // The long slash aimed at the neck: a tall diagonal arc.
                    slash(pos.add(d.scale(1.0)), d, 2.5f, 0.85f, 7, now);
                    burst(level, pos.subtract(d.scale(0.5)), q(8), 0.1, Sprite.SMOKE, SMOKE, 0.7f, 12);
                }
            }
            case "resolute_hit" -> {
                sound("resolute_slash", pos, 1.3f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.3f, WHITE, 1f, 4, now);
                    shards(level, pos, d, q(10));
                }
                victimFeedback(p, 0.6f);
            }
            case "resolute_finisher" -> {
                sound("resolute_slash", pos, 1.4f, 0.9f);
                sound("yuta_crush", pos, 0.8f, 1.1f);
                if (drawn) {
                    line(pos.add(d.cross(up).scale(-1.5)), pos.add(d.cross(up).scale(1.5)), 0.08f, WHITE, 1f, 8, now);
                    YujiFx.blood(level, pos.add(0, 0.6, 0), up, q(18));
                }
            }
            case "resolute_flash_ready" -> {
                sound("resolute_bf_windup", pos, 1.2f, 1f);
                if (drawn) Flashes.flash(pos, 0.3f, 1.4f, YujiFx.BF_RED, 0.8f, 6, now);
            }
            case "resolute_black_flash" -> {
                sound("resolute_bf_land", pos, 1.5f, 1f);
                if (drawn) YujiFx.blackFlash(level, pos, d, Math.max(1f, s), now, false);
                if (mine || isAttackerClose(mc, pos)) ScreenEffects.flash(0x90000000, 3);
                distanceShake(pos, 24, 0.7f);
            }
            case "resolute_flash_finisher", "rika_throw_finisher" -> {
                sound(p.id().equals("rika_throw_finisher") ? "rika_throw_finish" : "resolute_bf_finish", pos, 1.6f, 1f);
                if (drawn) {
                    YujiFx.blackFlash(level, pos, d, 1.6f, now, true);
                    YujiFx.blood(level, pos, d, q(12));
                }
                if (mine) ScreenEffects.flash(0xA0000000, 5);
            }
            // --- Outburst ---
            case "outburst_grip" -> {
                // Cursed energy gathering round the gripped handle and swirling about his feet.
                sound("outburst_start", pos, 1.2f, 1f);
                if (drawn) {
                    Vec3 g = groundBelow(level, pos).add(0, 0.1, 0);
                    Flashes.swirl(g, up, 0.6f, 1.8f, 4f, 2.5f, 0.1f, PINK, 0.8f, 30, now);
                    Flashes.swirl(g.add(0, 0.4, 0), up, 0.4f, 1.3f, 3f, -3f, 0.07f, PINK_LIGHT, 0.7f, 24, now);
                    Flashes.flash(pos, 0.2f, 0.9f, PINK, 0.9f, 10, now);
                    for (int i = 0; i < q(16); i++) {
                        Vec3 at = g.add(gauss(0.6), 0, gauss(0.6));
                        add(level, at, new Vec3(0, 0.05 + RNG.nextDouble() * 0.06, 0), Sprite.SPARK, i % 3 == 0 ? WHITE : PINK, 0.9f, 0.08f, 0.02f, 18 + RNG.nextInt(10));
                    }
                }
            }
            case "outburst_stage" -> {
                sound("outburst_charge", pos, 0.7f, 1f + s * 0.12f);
                if (drawn) {
                    Flashes.ring(pos, 0.5f, 1.2f + s * 0.5f, PINK, 0.8f, 8, now);
                    sparks(level, pos, up, q(4 + (int) s * 2), 0.2, PINK_LIGHT, 0.06f, 8);
                }
            }
            case "outburst_swing" -> {
                sound("outburst_swing", pos, 1.2f, 1f);
                if (drawn) crescent(pos.add(d.scale(0.8)), d, 2f * s, 0.12f, PINK, 1f, 5, now, -0.3f);
            }
            case "outburst_burst" -> {
                // A giant pink-white burst, whiting out everything nearby (GIF frame 52).
                sound("outburst_explosion", pos, 1.8f, 1f);
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
                sound("outburst_hit", pos, 1f, 0.95f + RNG.nextFloat() * 0.1f);
                if (drawn) sparks(level, pos, up, q(8), 0.4, PINK, 0.08f, 7);
                victimFeedback(p, 0.5f);
            }
            case "outburst_parry" -> {
                sound("outburst_parry", pos, 1.4f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 2f, 0.3f, WHITE, 1f, 5, now);
                    crescent(pos, d, 1.4f, 0.1f, PINK, 1f, 5, now, -0.2f);
                    impactStar(pos, d, 10, 1.8f, 0.06f, PINK_LIGHT, now);
                }
                if (mine) ScreenEffects.fovPunch(-0.06f);
            }
            case "outburst_finisher" -> {
                sound("outburst_hit", pos, 1.5f, 0.85f);
                sound("yuta_crush", pos, 1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 3f, 0.4f, PINK, 1f, 6, now);
                    YujiFx.blood(level, pos, up, q(24));
                }
            }
            // --- Second Wind ---
            case "second_wind" -> {
                sound("second_wind_dash", pos, 1.2f, 1f);
                if (drawn) {
                    Vec3 g = groundBelow(level, pos);
                    Flashes.ground(g, 0.3f, 2.2f, PINK, 0.7f, 8, now);
                    burst(level, g.add(0, 0.1, 0), q(10), 0.2, Sprite.GLOW, PINK, 0.25f, 10);
                    line(pos, pos.add(d.scale(5.5)), 0.1f, PINK_LIGHT, 0.6f, 8, now);
                }
            }
            case "second_wind_slam" -> {
                sound("yuta_slam", pos, 1.4f, 1f);
                if (drawn) {
                    Flashes.ground(pos, 0.5f, 3f, PINK, 0.8f, 10, now);
                    debris(level, pos, q(10), 0.3);
                }
                distanceShake(pos, 14, 0.5f);
            }
            case "second_wind_finisher" -> {
                sound("yuta_crush", pos, 1.5f, 0.9f);
                if (drawn) {
                    debris(level, pos, q(16), 0.4);
                    YujiFx.blood(level, pos, up, q(16));
                }
            }
            // --- Rika ---
            case "rika_summon", "rika_voice" -> {
                if (p.id().equals("rika_voice")) {
                    // "Yuu-taa!"
                    sound("rika_voice", pos, 1.2f, 1f);
                    return;
                }
                sound("rika_summon", pos, 1.3f, 1f);
                // She billows out of a cloud of black smoke (RikaSummon GIF 9-28).
                if (drawn) smoke(level, pos, q(36), 0.8, 1.2f, 2.6f, 30);
            }
            case "rika_dismiss", "true_love_end" -> {
                sound("rika_move", pos, 1f, 0.85f);
                if (drawn) smoke(level, pos, q(28), 0.9, 1.0f, 2.2f, 26);
            }
            case "rika_ring_glow", "rika_station" -> {
                if (p.id().equals("rika_station")) sound("rika_move", pos, 0.8f, 1f);
                else if (mine) sound("true_love_ring", pos, 0.45f, 1.3f);
                if (drawn) Flashes.flash(pos, 0.2f, 0.7f, PINK, 0.8f, 8, now);
            }
            case "rika_fist_grow", "rika_haymaker_windup" -> {
                sound(p.id().equals("rika_haymaker_windup") ? "rika_haymaker" : "rika_move", pos, 1.3f, 1f);
                if (drawn) burst(level, pos, q(10), 0.06, Sprite.SMOKE, SMOKE, 0.6f, 14);
            }
            case "rika_smash", "rika_dunk", "rika_downslam", "rika_double_slam", "rika_slam" -> {
                sound("yuta_slam", pos, 1.5f, 0.95f);
                if (drawn) {
                    // Her fist coming down: a shockwave, the floor thrown up, dust and her black smoke.
                    Flashes.flash(pos.add(0, 0.5, 0), 2.6f * s, 0.4f, WHITE, 1f, 4, now);
                    Flashes.ground(pos, 0.5f, 3.5f * s, GREY, 0.7f, 14, now);
                    Flashes.ripple(pos, up, 0.5f, 4.5f * s, WHITE, 0.7f, 12, now);
                    debris(level, pos, q(24), 0.55);
                    burst(level, pos, q(14), 0.18, Sprite.SMOKE, GREY, 0.9f, 20);
                    smoke(level, pos.add(0, 1.2, 0), q(10), 0.6, 0.8f, 1.6f, 16);
                }
                distanceShake(pos, 20, 0.7f);
            }
            case "rika_haymaker" -> {
                sound("yuta_heavy_hit", pos, 1.5f, 0.9f);
                if (drawn) {
                    Flashes.flash(pos, 2.2f, 0.4f, WHITE, 1f, 5, now);
                    Flashes.ripple(pos, d, 0.5f, 3f, GREY, 0.7f, 8, now);
                    impactStar(pos, d, 12, 2.4f, 0.08f, WHITE, now);
                }
                distanceShake(pos, 20, 0.6f);
            }
            case "rika_launch" -> {
                sound("rika_launch", pos, 1.3f, 1f);
                if (drawn) {
                    burst(level, pos, q(12), 0.2, Sprite.SMOKE, GREY, 0.6f, 14);
                    Flashes.ripple(pos, d, 0.3f, 2f, WHITE, 0.6f, 6, now);
                }
            }
            case "rika_smash_finisher", "rika_crush_finisher" -> {
                sound("yuta_crush", pos, 1.6f, 0.85f);
                if (drawn) {
                    YujiFx.blood(level, pos, up, q(30));
                    Flashes.ground(groundBelow(level, pos), 0.5f, 2.5f, BLOOD, 0.9f, 60, now);
                }
            }
            case "rika_throw_pickup" -> sound("rika_throw_start", pos, 1.3f, 1f);
            case "rika_throw" -> {
                if (drawn) burst(level, pos, q(12), 0.25, Sprite.SMOKE, GREY, 0.6f, 12);
            }
            case "rika_throw_crash" -> {
                // Nobody there: he crashes into the floor himself.
                sound("yuta_crush", pos, 1.2f, 1f);
                sound("ragdoll_fall", pos, 1f, 1f);
                if (drawn) debris(level, pos, q(12), 0.35);
            }
            // --- True Love ---
            case "true_love_start" -> {
                // "Come, Rika. Give me everything." The necklace's chain snapping.
                sound("true_love_start", pos, 1.6f, 1f);
                if (drawn) {
                    Vec3 g = groundBelow(level, pos).add(0, 0.1, 0);
                    Flashes.swirl(g, up, 0.5f, 2.8f, 4f, 1.5f, 0.14f, PINK, 0.9f, 40, now);
                    Flashes.swirl(g.add(0, 0.6, 0), up, 0.4f, 2.0f, 3f, -2f, 0.08f, PINK_LIGHT, 0.8f, 36, now);
                    Flashes.ground(g, 0.5f, 3f, PINK, 0.6f, 40, now);
                    for (int i = 0; i < q(30); i++) {
                        Vec3 at = g.add(gauss(0.9), 0, gauss(0.9));
                        add(level, at, new Vec3(0, 0.05 + RNG.nextDouble() * 0.08, 0), Sprite.GLOW, i % 3 == 0 ? WHITE : PINK, 0.85f, 0.18f, 0.04f, 30 + RNG.nextInt(20));
                    }
                }
            }
            case "true_love_ring" -> {
                sound("true_love_ring", pos, 1.3f, 1f);
                if (drawn) {
                    // The ring on his finger catches the light.
                    Flashes.flash(pos, 0.3f, 1.4f, PINK_LIGHT, 1f, 12, now);
                    Flashes.flash(pos, 0.1f, 0.5f, WHITE, 1f, 8, now);
                    impactStar(pos, Vec3.ZERO, 8, 1.2f, 0.035f, WHITE, now);
                    sparks(level, pos, up, q(10), 0.15, PINK, 0.06f, 12);
                }
            }
            case "true_love_manifest" -> {
                sound("rika_summon", pos, 1.8f, 0.9f);
                if (drawn) {
                    Flashes.flash(pos, 1f, 6f, PINK, 0.9f, 14, now);
                    Flashes.ripple(pos, up, 0.5f, 9f, PINK_LIGHT, 0.8f, 16, now);
                    burst(level, pos.add(d.scale(-1.5)), q(40), 0.12, Sprite.SMOKE, SMOKE, 1f, 30);
                }
                if (mine) ScreenEffects.flash(0x90F76BFF, 8);
                distanceShake(pos, 32, 0.8f);
            }
            case "steel_arm" -> {
                // The casing clamps shut round his arm.
                sound("true_love_arm", pos, 1.3f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.3f, WHITE, 1f, 4, now);
                    impactStar(pos, d, 10, 1.4f, 0.05f, GREY, now);
                    sparks(level, pos, d, q(16), 0.35, WHITE, 0.07f, 10);
                    Flashes.ring(pos, 0.3f, 1.4f, GREY, 0.8f, 8, now);
                }
            }
            // --- Elbow Rush ---
            case "elbow_dash" -> {
                sound("elbow_dash", pos, 1.2f, 1f);
                if (drawn) line(pos, pos.add(d.scale(6)), 0.12f, PINK, 0.6f, 8, now);
            }
            case "elbow_hit" -> {
                sound("elbow_hit", pos, 1.4f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 2f, 0.3f, WHITE, 1f, 5, now);
                    impactStar(pos, d, 10, 2f, 0.07f, PINK, now);
                }
                victimFeedback(p, 0.7f);
            }
            // --- Copy ---
            case "copy_learn" -> {
                // A technique taken: a pink ring rising round him.
                sound("true_love_ring", pos, 1f, 1.2f);
                if (drawn) {
                    Flashes.flash(pos, 0.3f, 1.6f, PINK_LIGHT, 0.9f, 12, now);
                    for (int i = 0; i < 3; i++) Flashes.ripple(pos.add(0, -0.8 + i * 0.6, 0), up, 0.3f, 1.4f, i == 1 ? WHITE : PINK, 0.8f, 12 + i * 3, now + i * 2);
                    sparks(level, pos, up, q(12), 0.2, PINK, 0.06f, 14);
                }
            }
            case "copy_use" -> {
                if (drawn) Flashes.ring(pos, 0.3f, 1.4f, PINK_LIGHT, 0.6f, 8, now);
            }
            case "copy_wheel_open", "copy_wheel_close", "copy_wheel_tick" -> {
                if (mine) mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), p.id().endsWith("open") ? 1.2f : p.id().endsWith("close") ? 0.8f : 1.5f, 0.4f));
            }
            case "speech_marks" -> {
                sound("speech_start", pos, 1.1f, 1f);
                if (drawn) Flashes.flash(pos, 0.1f, 0.5f, WHITE, 0.9f, 10, now);
            }
            case "speech_dont_move", "speech_stop", "speech_plummet", "speech_die" -> {
                // Cursed Speech: the command rolls out from his mouth in shockwaves of white and ink.
                // 動くな! (Don't move!) / 止まれ! (Stop!) / 落ちれ! / 死ね! (Die!)
                sound(p.id().equals("speech_stop") ? "speech_dont_move" : p.id(), pos, 2f, 1f);
                if (drawn) {
                    boolean close = p.id().equals("speech_plummet") || p.id().equals("speech_die");
                    float r = close ? 4f : Math.max(4f, s);
                    Flashes.flash(pos, 0.3f, 1.6f, WHITE, 1f, 5, now);
                    for (int i = 0; i < 3; i++) {
                        Flashes.ripple(pos, d, 0.3f, r * (0.4f + i * 0.2f), i == 1 ? INK : WHITE, 0.75f, 10 + i * 3, now + i * 2);
                        Flashes.ripple(groundBelow(level, pos).add(0, 0.1, 0), up, 0.3f, r * (0.6f + i * 0.2f), WHITE, 0.6f, 12 + i * 3, now + i * 2);
                    }
                    // The Snake Eyes and Fangs: dark streaks thrown out from the mouth.
                    for (int i = 0; i < 10; i++) {
                        Vec3 v = randomUnit().add(d).normalize();
                        line(pos, pos.add(v.scale(1.5 + RNG.nextDouble() * 2)), 0.05f, INK, 0.9f, 6, now);
                    }
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < Math.max(4f, s)) {
                    ScreenEffects.flash(0x60000000, 4);
                    ScreenEffects.shake(0.4f, 8);
                }
            }
            case "speech_bound" -> {
                // Bound by the command: a dark red aura clinging to them.
                if (drawn) {
                    Flashes.ring(pos, 0.9f, 0.5f, WHITE, 0.7f, 20, now);
                    for (int i = 0; i < q(14); i++) {
                        Vec3 at = pos.add(gauss(0.35), gauss(0.6), gauss(0.35));
                        add(level, at, new Vec3(0, 0.03, 0), Sprite.SMOKE, i % 2 == 0 ? BLOOD : SMOKE, 0.85f, 0.3f, 0.6f, 30 + RNG.nextInt(20));
                    }
                }
            }
            case "speech_die_finisher" -> {
                sound("yuta_crush", pos, 1.4f, 0.9f);
                if (drawn) YujiFx.blood(level, pos, up, q(36));
            }
            // --- Energy Ripple / Fakeout ---
            case "ripple_charge" -> {
                sound("ripple_start", pos, 1.2f, 1f);
                if (drawn) Flashes.swirl(pos, up, 0.3f, 1.2f, 3f, 2.5f, 0.08f, PINK, 0.8f, 14, now);
            }
            case "energy_ripple" -> {
                // The blade driven into the floor: a dome of pink cursed energy swelling out over everyone (GIF 47-56).
                sound("ripple_bomb", pos, 2f, 1f);
                if (drawn) {
                    Flashes.flash(pos.add(0, s * 0.25, 0), 1f, s * 1.15f, PINK, 0.75f, 14, now);
                    Flashes.flash(pos.add(0, 0.5, 0), 0.5f, s * 0.6f, PINK_LIGHT, 0.9f, 8, now);
                    Flashes.flash(pos, 0.3f, 2.5f, WHITE, 1f, 5, now);
                    for (int i = 0; i < 3; i++) Flashes.ripple(pos.add(0, 0.1, 0), up, 0.3f, s * (0.7f + i * 0.2f), i == 1 ? WHITE : PINK, 0.9f, 12 + i * 4, now + i * 2);
                    Flashes.ground(pos, 0.5f, s, PINK, 0.7f, 18, now);
                    sphereShell(level, pos, s * 0.7, q(70), PINK, 0.35f, 16, 0.15);
                    // The dome itself: meridians from the rim over the top, and two rings of latitude.
                    for (int i = 0; i < 10; i++) {
                        double az = i * Math.PI * 2 / 10;
                        Vec3 prev = null;
                        for (int k = 0; k <= 8; k++) {
                            double el = k * Math.PI / 2 / 8;
                            Vec3 at = pos.add(Math.cos(az) * Math.cos(el) * s, Math.sin(el) * s * 0.8, Math.sin(az) * Math.cos(el) * s);
                            if (prev != null) line(prev, at, 0.14f, PINK, 0.55f, 12, now);
                            prev = at;
                        }
                    }
                    for (double el : new double[] {0.35, 0.8}) {
                        Vec3 prev = null;
                        for (int k = 0; k <= 24; k++) {
                            double az = k * Math.PI * 2 / 24;
                            Vec3 at = pos.add(Math.cos(az) * Math.cos(el) * s, Math.sin(el) * s * 0.8, Math.sin(az) * Math.cos(el) * s);
                            if (prev != null) line(prev, at, 0.1f, PINK_LIGHT, 0.5f, 12, now);
                            prev = at;
                        }
                    }
                    ring3d(level, pos.add(0, 0.2, 0), up, 1.0, q(40), 0.8, PINK_LIGHT, 0.15f, 14);
                    // Spikes of energy shooting up round the rim.
                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI * 2 / 12 + RNG.nextDouble() * 0.3;
                        Vec3 at = pos.add(Math.cos(a) * s * 0.75, 0, Math.sin(a) * s * 0.75);
                        line(at, at.add(0, 1.5 + RNG.nextDouble() * 2, 0), 0.12f, PINK, 0.8f, 8, now);
                    }
                    debris(level, pos, q(16), 0.5);
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < s) ScreenEffects.flash(0x90FFB8FF, 6);
                distanceShake(pos, s * 3, 0.9f);
            }
            case "ripple_hit" -> {
                sound("hit_heavy", pos, 0.9f, 1f);
                if (drawn) sparks(level, pos, d, q(8), 0.4, PINK, 0.08f, 7);
                victimFeedback(p, 0.6f);
            }
            case "fakeout_burst" -> {
                // The imbued energy let go inside them: pink rays converging, then the burst.
                sound("fakeout_burst", pos, 1.5f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 3.5f, PINK_LIGHT, 1f, 8, now);
                    for (int i = 0; i < 14; i++) {
                        Vec3 r = randomUnit();
                        line(pos.add(r.scale(3.5)), pos.add(r.scale(0.3)), 0.07f, i % 2 == 0 ? PINK : WHITE, 0.9f, 6, now);
                    }
                    sphereShell(level, pos, 1.2, q(24), PINK, 0.18f, 12, 0.35);
                    splat(level, pos, q(10), 0.4);
                }
                victimFeedback(p, 0.8f);
            }
            case "fakeout_finisher" -> {
                sound("fakeout_burst", pos, 1.6f, 0.85f);
                sound("yuta_crush", pos, 1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 0.5f, 4f, PINK, 1f, 8, now);
                    YujiFx.blood(level, pos, up, q(28));
                }
            }
            // --- True Love Beam ---
            case "beam_orb" -> {
                // The small pink orb they conjure between them.
                sound("tlb_charge", pos, 1.4f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 0.1f, 0.7f, PINK, 1f, 30, now);
                    Flashes.flash(pos, 0.05f, 0.3f, WHITE, 1f, 30, now);
                    Flashes.swirl(pos, d, 0.2f, 0.8f, 3f, 4f, 0.04f, PINK_LIGHT, 0.8f, 24, now);
                    implode(level, pos, 1.6, q(14), PINK, 0.1f, 14);
                }
            }
            case "beam_rika_eye" -> {
                // Rika in place, jaw wide over his head: the charge swelling at her mouth, and the path it will take
                // traced out (a faint line and a marker where it first strikes), so it can be read and dodged.
                sound("tlb_power", pos, 2f, 1f);
                int charge = Math.max(10, (int) s);
                double reach = dir.length();
                Vec3 strike = beamStrike(level, pos, d, reach);
                if (drawn) {
                    Flashes.flash(pos, 0.3f, 2.4f, PINK, 1f, charge, now);
                    Flashes.flash(pos, 0.2f, 1.1f, WHITE, 1f, charge, now);
                    Flashes.lens(pos, 6f, 0.6f, PINK, 0.55f, charge, now);
                    for (int i = 0; i < 3; i++) Flashes.swirl(pos, d, 0.5f + i * 0.3f, 2.6f + i * 0.4f, 4f, 3f - i * 2f, 0.07f, i == 1 ? WHITE : PINK_LIGHT, 0.8f, charge, now + i * 3);
                    implode(level, pos, 4.0, q(40), PINK, 0.16f, 20);
                    Flashes.beam(pos, pos.add(d.scale(reach)), 0.12f, PINK, 0.5f, charge, now);
                    Flashes.ground(strike, 0.6f, 2.8f, PINK, 0.7f, charge, now);
                    Flashes.flash(strike, 0.4f, 1.2f, PINK_LIGHT, 0.7f, charge, now);
                }
            }
            case "beam_shape" -> SHAPES.put(p.entityId(), new double[] {dir.x, dir.y, dir.z});
            case "true_love_beam", "beam_quick" -> {
                boolean big = p.id().equals("true_love_beam");
                // The full beam's roar is in tlb_power, timed to its release; the quick one is its own.
                if (!big) sound("tlb_small", pos, 1.8f, 1f);
                double reach = dir.length();
                int life = Math.max(2, (int) s);
                double[] shape = SHAPES.remove(p.entityId());
                double radius = shape != null ? shape[0] : big ? 2.6 : 0.9;
                int grow = shape != null ? (int) shape[1] : big ? 4 : 2, collapse = shape != null ? (int) shape[2] : big ? 8 : 3;
                // The beam itself: the exact shape the server hits with, until it ends or is cut short.
                Flashes.loveBeam(p.entityId(), new Flashes.LoveBeam(pos, d, reach, radius, grow, collapse, life, now, !big));
                if (drawn) {
                    // The release at her mouth (or his hands), and where it first strikes.
                    Flashes.flash(pos, 1f, big ? 4f : 2.2f, PINK_LIGHT, 1f, big ? 10 : 6, now);
                    Flashes.flash(pos, 0.5f, big ? 2f : 1f, WHITE, 1f, big ? 6 : 4, now);
                    Flashes.ripple(pos, d, 0.5f, (float) radius * 2.4f, PINK_LIGHT, 0.85f, big ? 10 : 6, now);
                    Vec3 strike = beamStrike(level, pos, d, reach);
                    long hitAt = now + Math.max(0, Math.round(strike.distanceTo(pos) / reach * grow) - 1);
                    // The first strike: a burst as its front arrives (the pulses keep the impact going after).
                    Flashes.flash(strike, 1f, (float) radius * 4f, PINK_LIGHT, 1f, 10, hitAt);
                    Flashes.ripple(strike, d, 0.5f, (float) radius * 5f, WHITE, 0.8f, 10, hitAt);
                    if (big) {
                        // Shock rings running down it as the front passes, and sparks thrown off its sides.
                        for (double t = 4; t < reach; t += 6) {
                            Flashes.ripple(pos.add(d.scale(t)), d, (float) radius, (float) radius * 1.9f, PINK_LIGHT, 0.6f, 12, now + Math.round(t / reach * grow));
                        }
                        for (int i = 0; i < q(36); i++) {
                            Vec3 at = pos.add(d.scale(RNG.nextDouble() * reach)).add(randomUnit().scale(radius * 0.9));
                            add(level, at, randomUnit().scale(0.25).add(d.scale(0.4)), Sprite.GLOW, i % 3 == 0 ? WHITE : PINK, 0.85f, 0.5f, 0.04f, 10 + RNG.nextInt(life));
                        }
                        debris(level, strike, q(20), 0.6);
                    }
                }
                if (big && mc.player != null && closeTo(mc.player.position(), pos, d, reach, radius + 6)) ScreenEffects.flash(0x40FF9CFF, 6);
                distanceShake(pos, big ? 64 : 24, big ? 1.2f : 0.5f);
            }
            case "beam_pulse", "beam_quick_pulse" -> {
                // Every few ticks of the blast: it keeps dumping energy into whatever it strikes (pos), a mass 4-6 blocks
                // across, while rings travel down it and it lights the ground beneath.
                boolean big = p.id().equals("beam_pulse");
                double[] st = Flashes.loveBeamState(p.entityId(), now, 0);
                if (st == null || st[1] <= 0.05) return;
                float k = (float) st[1];
                float mass = big ? 5f : 1.8f;
                Flashes.flash(pos, mass * 0.5f, mass * k, PINK_LIGHT, 1f, 6, now);
                Flashes.flash(pos, mass * 0.3f, mass * 0.6f * k, WHITE, 1f, 5, now);
                Flashes.ripple(pos, d, mass * 0.3f, mass * 1.1f * k, PINK_LIGHT, 0.85f, 7, now);
                if (big) {
                    // The mass of it: a churning ball of energy 4-6 blocks across, shells bursting off it.
                    Flashes.flash(pos, 2f, 6f * k, PINK, 0.8f, 7, now + 1);
                    Flashes.ink(pos, 1.5f, 3.6f, MAGENTA, 0.5f, 8, now);
                    Flashes.lens(pos, 1.5f, 5.5f * k, PINK_LIGHT, 0.6f, 7, now);
                    Flashes.ring(pos, 1f, 6f * k, WHITE, 0.6f, 6, now + 1);
                    if (drawn) burst(level, pos, q(6), 0.15, Sprite.SMOKE, SMOKE, 0.9f, 14);
                    Vec3 g = groundBelow(level, pos);
                    if (g.distanceTo(pos) < 6) Flashes.ground(g.add(0, 0.1, 0), 1f, 6f * k, PINK, 0.7f, 8, now);
                    // Cursed-energy arcs lashing out of the impact.
                    for (int i = 0; i < 3; i++) {
                        Vec3 out = randomUnit().subtract(d.scale(0.6)).normalize();
                        Flashes.bolt(pos, pos.add(out.scale(2.5 + RNG.nextDouble() * 2.5)), 0.08f, i == 0 ? WHITE : PINK_LIGHT, 0.95f, 4, now);
                    }
                    // A ring running down the beam toward the impact, and its light on the ground below.
                    Vec3 from = pos.subtract(d.scale(Math.min(30, 4 + (s % 9) * 3)));
                    for (int j = 0; j < 4; j++) {
                        Vec3 at = from.add(d.scale(j * 1.6));
                        Flashes.ripple(at, d, 1.6f, 2.4f, PINK_LIGHT, 0.55f, 5, now + j);
                    }
                    Vec3 mid = pos.subtract(d.scale(6));
                    Vec3 gm = groundBelow(level, mid);
                    if (gm.distanceTo(mid) < 5) Flashes.ground(gm.add(0, 0.08, 0), 2f, 3.5f, PINK, 0.35f, 6, now);
                    if (drawn) {
                        sparks(level, pos, d.scale(-1), q(14), 0.7, RNG.nextBoolean() ? WHITE : PINK, 0.09f, 12);
                        debris(level, pos, q(6), 0.5);
                        for (int i = 0; i < q(8); i++) {
                            Vec3 a0 = pos.subtract(d.scale(RNG.nextDouble() * 20)).add(randomUnit().scale(1.6));
                            add(level, a0, randomUnit().scale(0.2).add(d.scale(0.5)), Sprite.GLOW, i % 3 == 0 ? WHITE : PINK, 0.85f, 0.45f, 0.04f, 10);
                        }
                    }
                    if ((int) s % 9 == 0) sound("ripple_bomb", pos, 1.4f, 0.8f + RNG.nextFloat() * 0.2f);
                    if (mc.player != null && mc.player.position().distanceTo(pos) < 10) ScreenEffects.shake(0.35f, 4);
                } else if (drawn) {
                    sparks(level, pos, d.scale(-1), q(6), 0.4, PINK, 0.06f, 8);
                }
            }
            case "beam_stop", "beam_fizzle" -> {
                // Cut short (he fell, left, or Rika is gone): the beam collapses now, the charge drains away.
                Flashes.stopLoveBeam(p.entityId(), now);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.2f, PINK, 0.8f, 8, now);
                    burst(level, pos, q(16), 0.2, Sprite.SMOKE, SMOKE, 0.7f, 16);
                }
                if (p.id().equals("beam_fizzle")) sound("rika_move", pos, 1f, 0.8f);
            }
            case "beam_hit" -> {
                sound("yuta_heavy_hit", pos, 1f, 0.9f);
                victimFeedback(p, 0.9f);
            }
            case "beam_finisher" -> {
                // Atomized into black mist.
                sound("yuta_crush", pos, 1.3f, 0.8f);
                if (drawn) burst(level, pos, q(40), 0.15, Sprite.SMOKE, SMOKE, 1f, 30);
            }
            // --- Authentic Mutual Love ---
            case "aml_charge" -> {
                // "Domain Expansion." and the JJS opening.
                sound("aml_voice", pos, 2f, 1f);
                sound("aml_start", pos, 2.5f, 1f);
                if (drawn) Flashes.swirl(pos, up, 0.5f, 3f, 4f, 1.2f, 0.1f, PINK, 0.7f, 36, now);
            }
            case "blade_land" -> {
                sound("aml_sword_ground", pos, 1.2f, 0.95f + RNG.nextFloat() * 0.1f);
                if (drawn) {
                    Flashes.ground(pos, 0.2f, 1f, WHITE, 0.6f, 8, now);
                    debris(level, pos, q(4), 0.15);
                }
            }
            case "blade_rain" -> {
                // Far off: swords striking the stone, out of reach.
                if (RNG.nextInt(3) == 0) sound("aml_sword_ground", pos.add(gauss(s * 0.6), -s * 0.6, gauss(s * 0.6)), 0.5f, 0.9f + RNG.nextFloat() * 0.2f);
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
                sound("aml_pickup", pos, 1.3f, 1f);
                if (drawn) {
                    // The blade taken up: its technique flares round him in its own colour.
                    float[] c = techColor((int) s);
                    Flashes.flash(pos, 0.3f, 2.0f, c, 0.9f, 10, now);
                    line(pos.add(0, -1, 0), pos.add(0, 3, 0), 0.5f, c, 0.6f, 8, now);
                    Flashes.ripple(groundBelow(level, pos).add(0, 0.1, 0), up, 0.3f, 2.2f, c, 0.8f, 10, now);
                    sparks(level, pos, up, q(12), 0.25, c, 0.07f, 12);
                }
            }
            case "blade_swing" -> {
                sound("aml_run_slash", pos, 1.4f, 1f);
                if (drawn) {
                    float[] c = techColor((int) s);
                    crescent(pos.add(d.scale(0.9)), d, 2f, 0.1f, c, 1f, 6, now, -0.2f);
                    crescent(pos.add(d.scale(0.9)), d, 1.9f, 0.03f, WHITE, 1f, 5, now, -0.2f);
                }
            }
            case "aml_cleave" -> {
                // Shrine's Cleave through the one the blade hit: four clean white cuts with black cores.
                sound("aml_cleave", pos, 1.3f, 1f);
                if (drawn) {
                    for (int i = 0; i < 4; i++) {
                        Vec3 r = randomUnit();
                        line(pos.subtract(r.scale(1.6)), pos.add(r.scale(1.6)), 0.1f, WHITE, 1f, 6 + i, now + i);
                        line(pos.subtract(r.scale(1.5)), pos.add(r.scale(1.5)), 0.04f, INK, 1f, 6 + i, now + i);
                    }
                    YujiFx.blood(level, pos, d, q(8));
                }
            }
            case "aml_dismantle" -> {
                // A large horizontal Dismantle out ahead: a wide white blade flying the length of it.
                sound("aml_dismantle", pos, 1.8f, 1f);
                if (drawn) {
                    Vec3 side = d.cross(up).normalize();
                    for (int k = 0; k < 4; k++) {
                        Vec3 c0 = pos.add(d.scale(2 + k * 3.5));
                        line(c0.subtract(side.scale(3)), c0.add(side.scale(3)), 0.16f, WHITE, 1f - k * 0.18f, 6 + k * 2, now + k);
                        line(c0.subtract(side.scale(2.8)), c0.add(side.scale(2.8)), 0.05f, INK, 0.9f - k * 0.15f, 6 + k * 2, now + k);
                    }
                    line(pos, pos.add(d.scale(14)), 0.05f, WHITE, 0.6f, 6, now);
                }
            }
            case "aml_shrine_finisher", "aml_bisect_finisher" -> {
                sound("aml_dismantle", pos, 1.5f, 0.85f);
                sound("yuta_crush", pos, 1f, 1f);
                if (drawn) YujiFx.blood(level, pos, up, q(30));
            }
            case "thin_ice_breaker" -> {
                // The sky breaks like a thin veil of ice.
                sound("aml_glass", pos, 1.8f, 1f);
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
                sound("aml_clairvoyance", pos, 1.2f, 1f);
                if (drawn) {
                    // A manga panel drawn in their blood hangs over them: a white frame with a black border.
                    var cam = mc.gameRenderer.mainCamera();
                    double yaw = Math.toRadians(cam.yRot());
                    Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
                    Vec3 c0 = pos.add(0, 1.5, 0);
                    Vec3[] corner = {c0.add(right.scale(-0.5)).add(0, -0.35, 0), c0.add(right.scale(0.5)).add(0, -0.35, 0),
                            c0.add(right.scale(0.5)).add(0, 0.35, 0), c0.add(right.scale(-0.5)).add(0, 0.35, 0)};
                    for (int i = 0; i < 4; i++) {
                        line(corner[i], corner[(i + 1) % 4], 0.1f, INK, 1f, 40, now);
                        line(corner[i], corner[(i + 1) % 4], 0.05f, WHITE, 1f, 40, now);
                    }
                    line(corner[0].add(right.scale(0.15)).add(0, 0.1, 0), corner[2].subtract(right.scale(0.15)).add(0, -0.1, 0), 0.04f, BLOOD, 1f, 40, now);
                    Flashes.flash(c0, 0.3f, 1.0f, WHITE, 0.6f, 12, now);
                    YujiFx.blood(level, pos, d, q(6));
                }
            }
            case "shikigami_swarm" -> {
                sound("aml_mini_rika", pos, 1.4f, 1f);
                if (drawn) burst(level, pos, q(12), 0.15, Sprite.SMOKE, SMOKE, 0.5f, 16);
            }
            case "shikigami_bite" -> {
                if (RNG.nextInt(3) == 0) sound("aml_mini_fly", pos, 0.6f, 0.9f + RNG.nextFloat() * 0.3f);
                sound("hit_light", pos, 0.5f, 1.2f);
                if (drawn) {
                    // Three winged Rika heads darting in: pale heads, black wings.
                    for (int i = 0; i < 3; i++) {
                        Vec3 at = pos.add(randomUnit().scale(0.9));
                        Flashes.flash(at, 0.45f, 0.15f, WHITE, 0.95f, 6, now);
                        Vec3 wing = randomUnit().cross(new Vec3(0, 1, 0)).normalize().scale(0.35);
                        line(at.subtract(wing), at.add(wing), 0.14f, INK, 0.9f, 6, now);
                        line(at, pos, 0.03f, WHITE, 0.6f, 3, now);
                    }
                    YujiFx.blood(level, pos, d, q(3));
                }
            }
            case "shikigami_finisher" -> {
                sound("yuta_crush", pos, 1.3f, 1f);
                if (drawn) YujiFx.blood(level, pos, up, q(24));
            }
            case "ladder_mark" -> {
                // Jacob's Ladder aimed: a gold circle closing on the target's ground.
                int life = Math.max(6, (int) s);
                Vec3 g = groundBelow(level, pos).add(0, 0.08, 0);
                Flashes.ground(g, 4f, 1.2f, GOLD_RAY, 0.9f, life, now);
                Flashes.ground(g, 2.4f, 0.6f, WHITE, 0.7f, life, now + 2);
                sound("true_love_ring", pos, 1.2f, 0.7f);
            }
            case "jacobs_ladder" -> {
                // A divine ray coming down from the sky onto them (JacobsLadder GIF 109-218), then holding.
                sound("jacobs_ladder", pos, 2.5f, 1f);
                int life = Math.max(20, (int) s);
                Vec3 top = pos.add(0, 48, 0);
                Vec3 bottom = pos.add(0, -0.5, 0);
                // It descends over the first few ticks: segments lit one after another from the top.
                for (int k = 0; k < 6; k++) {
                    Vec3 a0 = top.add(0, -k * 8, 0), a1 = top.add(0, -(k + 1) * 8, 0);
                    if (k == 5) a1 = bottom;
                    long at = now + k;
                    Flashes.beam(a0, a1, 5.5f, GOLD_RAY, 0.45f, life - k, at);
                    Flashes.beam(a0, a1, 3.0f, WHITE, 0.7f, life - k, at);
                    Flashes.beam(a0, a1, 1.2f, WHITE, 1f, life - k, at);
                }
                Flashes.ground(pos, 0.5f, 5f, GOLD_RAY, 0.8f, life, now + 6);
                Flashes.flash(pos.add(0, 1, 0), 1f, 6f, WHITE, 0.9f, 12, now + 6);
                if (drawn) {
                    for (int i = 0; i < q(40); i++) {
                        Vec3 at = pos.add(gauss(1.0), RNG.nextDouble() * 12, gauss(1.0));
                        add(level, at, new Vec3(0, 0.12 + RNG.nextDouble() * 0.1, 0), Sprite.GLOW, i % 2 == 0 ? WHITE : GOLD_RAY, 0.9f, 0.25f, 0.05f, 40 + RNG.nextInt(20));
                    }
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 40) ScreenEffects.flash(0x80FFFDE8, 10);
            }
            case "ladder_impact" -> {
                // The one blow: a flare of white-gold and a shockwave as the light takes its toll.
                Flashes.flash(pos, 1f, 7f, WHITE, 1f, 10, now);
                Flashes.flash(pos, 0.5f, 3f, GOLD_RAY, 1f, 14, now);
                Flashes.ripple(pos, up, 0.5f, 9f, GOLD_RAY, 0.9f, 14, now);
                Flashes.ring(pos, 0.5f, 6f, WHITE, 0.8f, 10, now);
                Flashes.beam(pos.add(0, 30, 0), pos, 7f, WHITE, 0.9f, 8, now);
                if (drawn) sparks(level, pos, up, q(30), 0.6, GOLD_RAY, 0.1f, 18);
                sound("yuta_heavy_hit", pos, 1.6f, 0.8f);
                if (mc.player != null && mc.player.position().distanceTo(pos) < 30) {
                    ScreenEffects.flash(0xC0FFFDE8, 8);
                    ScreenEffects.shake(0.9f, 14);
                }
                victimFeedback(p, 1f);
            }
            case "aml_shatter" -> {
                // The domain gives way: the black sky cracks into shards of glass falling away.
                sound("aml_glass", pos, 3f, 0.8f);
                Flashes.lens(pos, s * 0.95f, s * 1.1f, PINK_LIGHT, 0.8f, 16, now);
                for (int i = 0; i < 18; i++) {
                    Vec3 v = randomUnit();
                    if (v.y < 0) v = new Vec3(v.x, -v.y, v.z);
                    Vec3 at = pos.add(v.scale(s * 0.9));
                    line(at, at.add(randomUnit().scale(1.5 + RNG.nextDouble() * 2)), 0.06f, i % 2 == 0 ? WHITE : PINK_LIGHT, 1f, 12, now + i / 3);
                }
                if (drawn) burst(level, pos.add(0, s * 0.5, 0), q(30), 0.4, Sprite.SHARD, PINK_LIGHT, 0.2f, 30);
                if (mc.player != null && mc.player.position().distanceTo(pos) < s + 4) ScreenEffects.flash(0x90FFFFFF, 6);
            }
            case "ladder_ready" -> {
                // The fourth direct hit: Jacob's Ladder is ready. A gold burst and a ring rising round him.
                sound("true_love_ring", pos, 1.5f, 0.6f);
                sound("aml_pickup", pos, 1f, 0.8f);
                Flashes.flash(pos, 0.5f, 3f, GOLD_RAY, 1f, 12, now);
                for (int i = 0; i < 3; i++) Flashes.ripple(pos.add(0, -0.9 + i * 0.7, 0), up, 0.4f, 2f, i == 1 ? WHITE : GOLD_RAY, 0.9f, 14, now + i * 2);
                Flashes.beam(pos.add(0, -1, 0), pos.add(0, 12, 0), 0.8f, GOLD_RAY, 0.6f, 14, now);
                if (drawn) sparks(level, pos, up, q(20), 0.3, GOLD_RAY, 0.07f, 16);
            }
            case "ladder_ready_glow" -> {
                // Still ready: motes of gold rising round him, and a thin halo at his feet.
                Flashes.ground(groundBelow(level, pos).add(0, 0.06, 0), 0.9f, 1.3f, GOLD_RAY, 0.55f, 10, now);
                if (drawn) {
                    for (int i = 0; i < q(4); i++) {
                        Vec3 at = pos.add(gauss(0.4), RNG.nextDouble() * 1.6, gauss(0.4));
                        add(level, at, new Vec3(0, 0.07, 0), Sprite.GLOW, GOLD_RAY, 0.85f, 0.12f, 0.03f, 20);
                    }
                }
            }
            case "ladder_hit" -> {
                if (drawn) sparks(level, pos, up, q(6), 0.2, GOLD_RAY, 0.08f, 12);
                victimFeedback(p, 0.3f);
            }
            case "jacobs_ladder_finisher" -> {
                // The ladder's own sound swells to its end here.
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

    /** The colour a technique's blade glows (the same as its light column and name). */
    private static float[] techColor(int index) {
        var all = dev.rick.jjk.yuta.DomainTechnique.values();
        int c = all[Math.floorMod(index, all.length)].color;
        return new float[] {(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    /**
     * A katana arc: a broad translucent pink band, a brighter inner band and a white edge, with two fainter trailing
     * copies inside it so it reads as a swept wedge rather than a line (Swordsmanship GIF).
     */
    static void slash(Vec3 center, Vec3 dir, float radius, float tilt, int life, long now) {
        // Swept round a pivot behind the cut, so the arc passes through {@code center}.
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 pivot = center.subtract(d.scale(radius * 0.75));
        // A faint dark backing first, so the additive colour still shows against bright daylight.
        arc(pivot, d, radius * 0.95f, tilt, 0.5f, INK, 0.12f, life);
        arc(pivot, d, radius, tilt, 0.42f, MAGENTA, 0.75f, life);
        arc(pivot, d, radius * 0.98f, tilt, 0.22f, PINK, 1f, life);
        arc(pivot, d, radius * 1.01f, tilt, 0.08f, WHITE, 1f, life - 1);
        arc(pivot, d, radius * 0.84f, tilt, 0.28f, PINK, 0.5f, life - 1);
        arc(pivot, d, radius * 0.68f, tilt, 0.18f, MAGENTA, 0.32f, life - 2);
    }

    /**
     * An arc of {@code radius} round {@code pivot}, sweeping ±70° either side of {@code dir} in the plane through dir
     * rolled by {@code tilt} about it (0 = flat, ±π/2 = vertical); tapered at both ends.
     */
    private static void arc(Vec3 pivot, Vec3 d, float radius, float tilt, float width, float[] c, float alpha, int life) {
        long now = net.minecraft.client.Minecraft.getInstance().level.getGameTime();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        Vec3 axis = side.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        Vec3 prev = null;
        int n = 16;
        for (int i = 0; i <= n; i++) {
            double a = (i / (double) n - 0.5) * Math.toRadians(140);
            Vec3 at = pivot.add(d.scale(Math.cos(a) * radius)).add(axis.scale(Math.sin(a) * radius));
            if (prev != null) {
                float taper = (float) Math.max(0.15, Math.cos(a * 1.1));
                // Each piece overlaps the next a little so the band has no gaps.
                Vec3 ext = at.subtract(prev).scale(0.15);
                line(prev.subtract(ext), at.add(ext), width * taper, c, alpha, Math.max(1, life), now);
            }
            prev = at;
        }
    }

    /** Cursed energy splashing off: pink blobs, each with a darker rim behind it (GIF). */
    static void splat(ClientLevel level, Vec3 pos, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(speed * (0.4 + RNG.nextDouble() * 0.6)).add(0, 0.05, 0);
            float size = 0.18f + RNG.nextFloat() * 0.18f;
            int life = 12 + RNG.nextInt(8);
            add(level, pos, v, Sprite.SMOKE, INK, 0.95f, size * 1.35f, size * 0.4f, life).friction(0.86f).gravity(0.3f);
            add(level, pos, v, Sprite.GLOW, PINK, 1f, size, size * 0.3f, life).friction(0.86f).gravity(0.3f);
        }
    }

    /** Thick black smoke billowing (Rika manifesting or thinning away). */
    static void smoke(ClientLevel level, Vec3 pos, int n, double spread, float size0, float size1, int life) {
        for (int i = 0; i < n; i++) {
            Vec3 at = pos.add(gauss(spread), gauss(spread * 1.3), gauss(spread));
            add(level, at, new Vec3(gauss(0.02), 0.02 + RNG.nextDouble() * 0.04, gauss(0.02)), Sprite.SMOKE, BLACK, 1f,
                    size0 * (0.8f + RNG.nextFloat() * 0.4f), size1, life + RNG.nextInt(Math.max(1, life / 2)));
        }
    }

    static final float[] BLACK = {0f, 0f, 0f};

    /** Black shards scattered by a cursed-energy cut (GIF). */
    private static void shards(ClientLevel level, Vec3 pos, Vec3 dir, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(0.25).add(dir.scale(0.15));
            add(level, pos, v, Sprite.SHARD, i % 3 == 0 ? PINK : INK, 1f, 0.16f + RNG.nextFloat() * 0.1f, 0.05f, 14 + RNG.nextInt(8)).gravity(0.5f);
        }
    }
}
