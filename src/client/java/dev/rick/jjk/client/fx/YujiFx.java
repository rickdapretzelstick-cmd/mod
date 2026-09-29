package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import dev.rick.jjk.yuji.DismantleAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.*;

/**
 * Vessel's and Sukuna's visual language, after the JJS GIFs. Yuji's cursed energy is cyan, crackling and liquid (it
 * splashes off his fists); his eyes glow red for Cursed Strikes; the Black Flash is red lightning and black sparks with a
 * red-and-black impact frame. Sukuna's slashes are clean white arcs with black cores that draw blood; his aura is dark
 * red smoke; Open is fire, gold and orange, ending in a pillar.
 */
final class YujiFx {
    static final float[] CE = {0.35f, 0.9f, 1f};
    static final float[] CE_LIGHT = {0.8f, 0.97f, 1f};
    static final float[] BF_RED = {1f, 0.06f, 0.1f};
    static final float[] INK = {0.02f, 0.02f, 0.03f};
    static final float[] BLOOD = {0.62f, 0.02f, 0.05f};
    static final float[] AURA = {0.45f, 0.02f, 0.04f};
    static final float[] FIRE = {1f, 0.5f, 0.12f};
    static final float[] FIRE_CORE = {1f, 0.92f, 0.55f};

    private YujiFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        Vec3 up = new Vec3(0, 1, 0);
        switch (p.id()) {
            // --- Cursed Strikes ---
            case "cursed_strikes_start" -> {
                sound("cursed_strikes_start", pos, 1f, 1f);
                // His eyes glow red.
                Flashes.flash(pos.add(dir.scale(0.25)), 0.35f, 0.08f, BF_RED, 1f, 8, now);
                if (drawn) sparks(level, pos, dir, q(4), 0.1, BF_RED, 0.05f, 6);
            }
            case "cursed_strikes_slide" -> {
                sound("cursed_strikes_slide", pos, 1f, 1f);
                if (drawn) {
                    // Twin red streaks trailing from his eyes along the slide.
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    for (int k = -1; k <= 1; k += 2) {
                        Vec3 a = pos.add(0, 0.7, 0).add(side.scale(0.1 * k));
                        line(a, a.subtract(dir.scale(3.5)).add(0, -0.4, 0), 0.06f, BF_RED, 0.9f, 10, now);
                    }
                    Flashes.ground(groundBelow(level, pos), 0.3f, 2f, CE_LIGHT, 0.5f, 7, now);
                    burst(level, pos.add(0, -0.8, 0), q(6), 0.12, Sprite.SMOKE, GREY, 0.45f, 12);
                }
            }
            case "cursed_strikes_punch" -> {
                sound("hit_light", pos, 0.9f, 0.95f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    Flashes.flash(pos, 0.8f, 0.2f, WHITE, 1f, 3, now);
                    // Red slash trails around the fists (GIF).
                    crescent(pos, dir, 0.7f, 0.06f, BF_RED, 0.9f, 4, now, RNG.nextFloat() - 0.5f);
                    sparks(level, pos, dir, q(4), 0.3, BF_RED, 0.07f, 5);
                }
                victimFeedback(p, 0.25f);
            }
            case "cursed_strikes_kick" -> {
                sound("cursed_strikes_hit", pos, 1f, 1f);
                if (drawn) {
                    // The red burst of spikes on the calf kick.
                    Flashes.flash(pos, 1.6f, 0.3f, BF_RED, 1f, 5, now);
                    impactStar(pos, dir, 10, 1.6f * s, 0.07f, BF_RED, now);
                    impactStar(pos, dir, 4, 1.0f * s, 0.04f, WHITE, now);
                    sparks(level, pos, dir, q(10), 0.5, BF_RED, 0.1f, 7);
                }
                victimFeedback(p, 0.55f);
            }
            case "cursed_strikes_floor" -> {
                sound("crushing_impact", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.ground(groundBelow(level, pos), 0.4f, 3f, CE, 0.9f, 10, now);
                    line(pos, pos.add(0, 3, 0), 0.3f, CE_LIGHT, 0.9f, 8, now);
                    debris(level, pos, q(14), 0.4);
                }
                distanceShake(pos, 16, 0.5f);
            }
            case "cursed_strikes_finisher" -> {
                sound("cursed_strikes_spin", pos, 1f, 1f);
                sound("hit_heavy", pos, 1.2f, 0.8f);
                if (drawn) {
                    Flashes.flash(pos, 2.6f, 0.5f, BF_RED, 1f, 6, now);
                    impactStar(pos, dir, 12, 2.4f, 0.1f, BF_RED, now);
                    Flashes.ripple(pos, dir, 0.3f, 3f, WHITE, 0.8f, 9, now);
                }
            }
            case "cursed_strikes_spin" -> {
                sound("cursed_strikes_spin", pos, 1f, 1f);
                if (drawn) for (int i = 0; i < 3; i++) Flashes.ring(pos.add(0, -0.3 + i * 0.3, 0), 0.6f, 1.8f, WHITE, 0.4f, 8, now + i);
            }
            case "cursed_strikes_dive" -> {
                sound("cursed_strikes_slide", pos, 1f, 1.1f);
                if (drawn) line(pos, pos.subtract(dir.scale(3)), 0.25f, CE_LIGHT, 0.7f, 8, now);
            }
            case "cursed_strikes_impact" -> {
                // The ground-impact vortex off the dropkick (GIF): white whirls and a cyan crater.
                sound("cursed_strikes_impact", pos, 1.2f * s, 1f);
                if (drawn) {
                    Vec3 g = groundBelow(level, pos);
                    for (int i = 0; i < 3; i++) Flashes.ground(g, 0.4f, (2.4f + i) * s, i == 0 ? CE : WHITE, 0.8f - i * 0.2f, 10 + i * 3, now + i);
                    Flashes.flash(pos.add(0, 0.5, 0), 2f * s, 0.4f, CE_LIGHT, 1f, 6, now);
                    debris(level, pos, q(18), 0.45);
                    burst(level, pos, q(10), 0.25, Sprite.SMOKE, GREY, 0.7f, 18);
                }
                distanceShake(pos, 20, 0.6f * s);
            }
            // --- Crushing Blow ---
            case "crushing_charge" -> {
                sound("crushing_charge", pos, 1f, 1f);
                if (drawn) {
                    Vec3 fist = pos.add(dir.scale(0.4)).add(0, -0.2, 0);
                    implode(level, fist, 1.0, q(10), CE, 0.12f, 8);
                    for (int i = 0; i < q(6); i++) add(level, fist.add(gauss(0.2), gauss(0.2), gauss(0.2)), randomUnit().scale(0.03), Sprite.SPARK, CE_LIGHT, 1f, 0.12f, 0.02f, 8);
                }
            }
            case "crushing_dash" -> {
                sound("crushing_fist", pos, 1f, 1f);
                if (drawn) {
                    line(pos, pos.subtract(dir.scale(2.5)), 0.2f, CE, 0.8f, 8, now);
                    for (int i = 0; i < q(10); i++) add(level, pos.subtract(dir.scale(RNG.nextDouble() * 2)), randomUnit().scale(0.05), Sprite.GLOW, CE, 0.8f, 0.14f, 0.02f, 10);
                }
            }
            case "crushing_grab" -> sound("crushing_fist", pos, 1f, 1f);
            case "crushing_slam" -> {
                sound("crushing_hit", pos, 1.2f, 1f);
                victimFeedback(p, 0.7f);
            }
            case "crushing_impact" -> {
                sound("crushing_impact", pos, 1.3f, 1f);
                if (drawn) {
                    // A cyan splash of cursed energy out of the cracked floor (GIF).
                    Vec3 g = groundBelow(level, pos);
                    Flashes.flash(g.add(0, 0.4, 0), 2.4f * s, 0.4f, CE_LIGHT, 1f, 5, now);
                    Flashes.ground(g, 0.4f, 3.4f * s, CE, 0.9f, 10, now);
                    Flashes.ground(g, 0.4f, 4.4f * s, WHITE, 0.5f, 12, now + 2);
                    for (int i = 0; i < q(14); i++) {
                        Vec3 v = randomUnit();
                        v = new Vec3(v.x, Math.abs(v.y) + 0.4, v.z).normalize().scale(0.25 + RNG.nextDouble() * 0.3);
                        add(level, g.add(0, 0.2, 0), v, Sprite.GLOW, i % 3 == 0 ? CE_LIGHT : CE, 0.9f, 0.22f, 0.04f, 14).gravity(0.9f);
                    }
                    debris(level, pos, q(18), 0.5);
                }
                distanceShake(pos, 18, 0.6f);
            }
            case "crushing_suplex" -> {
                sound("crushing_impact", pos, 1.5f, 0.85f);
                if (drawn) {
                    Flashes.flash(pos, 3f, 0.5f, CE_LIGHT, 1f, 6, now);
                    Flashes.ground(groundBelow(level, pos), 0.5f, 5f, CE, 0.9f, 14, now);
                    debris(level, pos, q(24), 0.6);
                }
            }
            case "crushing_finisher" -> {
                sound("hit_slam", pos, 1.3f, 0.8f);
                if (drawn) burst(level, pos, q(12), 0.4, Sprite.SHARD, CE_LIGHT, 0.14f, 14).forEach(x -> x.gravity(0.8f).physics());
            }
            // --- Divergent Fist ---
            case "divergent_charge" -> {
                sound("divergent_charge", pos, 1f, 1f);
                if (drawn) implode(level, pos.add(dir.scale(-0.3)), 1.1, q(10), CE, 0.12f, 9);
            }
            case "divergent_flash" -> {
                // His body flashes white: the moment for a Black Flash.
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.9f, WHITE, 0.9f, 4, now);
                    Flashes.ring(pos, 0.5f, 1.6f, WHITE, 0.7f, 5, now);
                }
                if (mine) ScreenEffects.flash(0x30FFFFFF, 3);
            }
            case "divergent_punch" -> {
                sound("hit_heavy", pos, 0.9f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.2f, 0.3f, WHITE, 1f, 3, now);
                    // Cyan energy clinging to the fist, splashing on contact.
                    for (int i = 0; i < q(8); i++) add(level, pos, randomUnit().scale(0.12).add(dir.scale(0.1)), Sprite.GLOW, CE, 0.9f, 0.16f, 0.03f, 8);
                }
                victimFeedback(p, 0.4f);
            }
            case "divergent_impact" -> {
                // The delayed impact: cyan spikes and a flash that swallows the target (GIF frame 3).
                sound("divergent", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 2.6f, 0.4f, CE_LIGHT, 1f, 5, now);
                    impactStar(pos, dir, 14, 2.6f, 0.1f, CE, now);
                    impactStar(pos, dir, 6, 1.6f, 0.05f, WHITE, now);
                    Flashes.ripple(pos, dir, 0.3f, 2.8f, CE_LIGHT, 0.9f, 8, now);
                    for (int i = 0; i < q(14); i++) add(level, pos, randomUnit().scale(0.3).add(dir.scale(0.3)), Sprite.GLOW, CE, 0.9f, 0.2f, 0.03f, 12).gravity(0.5f);
                }
            }
            case "divergent_hit" -> {
                sound("divergent_hit", pos, 1f, 1f);
                victimFeedback(p, 0.6f);
            }
            case "divergent_shatter" -> {
                sound("divergent", pos, 1.4f, 0.8f);
                if (drawn) {
                    // The body shatters into pieces (GIF).
                    burst(level, pos, q(24), 0.45, Sprite.SHARD, BLOOD, 0.18f, 26).forEach(x -> x.gravity(0.9f).physics());
                    burst(level, pos, q(12), 0.35, Sprite.GLOW, CE, 0.2f, 14);
                    Flashes.flash(pos, 3f, 0.5f, CE_LIGHT, 1f, 6, now);
                }
            }
            // --- Black Flash ---
            case "black_flash_ready" -> sound("black_flash_windup", pos, 0.8f, 1.2f);
            case "black_flash", "black_flash_kokusen" -> {
                boolean fourth = p.id().equals("black_flash_kokusen");
                sound(fourth ? "black_flash_heavy" : "black_flash", pos, fourth ? 2f : 1.4f, 1f);
                if (fourth) sound("kokusen", pos, 2f, 1f);
                else if (s <= 1f) sound("black_flash_chain", pos, 1f, 1f);
                if (drawn) blackFlash(level, pos, dir, fourth ? 2f : s, now, fourth);
                if (mc.player != null && mc.player.position().distanceTo(pos) < (fourth ? 40 : 24)) {
                    ScreenEffects.impact(fourth ? 8 : 5, fourth ? 2 : 1);
                    ScreenEffects.shake(fourth ? 1.2f : 0.7f, fourth ? 18 : 10);
                }
                if (fourth && mine) ScreenEffects.fovPunch(0.15f);
            }
            case "black_flash_finisher" -> {
                sound("black_flash_heavy", pos, 2f, 0.9f);
                if (drawn) blackFlash(level, pos, dir, 2.2f, now, true);
            }
            // --- Manji Kick ---
            case "manji_startup" -> {
                sound("manji_startup", pos, 1f, 1f);
                if (drawn) Flashes.flash(pos.add(0, 0.6, 0), 0.6f, 0.2f, WHITE, 0.8f, 4, now);
            }
            case "manji_dodge" -> {
                // Dodged: a dark silhouette flash (GIF frame 9).
                sound("manji_dodge", pos, 1f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.8f, 0.5f, INK, 0.9f, 5, now);
                    impactStar(pos, Vec3.ZERO, 6, 1.5f, 0.05f, WHITE, now);
                }
            }
            case "manji_swoop" -> {
                sound("cursed_strikes_spin", pos, 1f, 1.1f);
                if (drawn) line(pos, pos.add(dir.normalize().scale(2)), 0.3f, WHITE, 0.5f, 6, now);
            }
            case "manji_hit" -> {
                sound("manji_swing", pos, 1f, 1f);
                sound("hit_heavy", pos, 1f, 1.1f);
                if (drawn) {
                    // The white starburst (GIF frame 12).
                    Flashes.flash(pos, 2.2f, 0.4f, WHITE, 1f, 5, now);
                    impactStar(pos, dir, 10, 2.4f, 0.1f, WHITE, now);
                    Flashes.ring(pos, 0.3f, 2.5f, WHITE, 0.6f, 8, now);
                }
                victimFeedback(p, 0.6f);
            }
            case "manji_crush" -> {
                sound("manji_crush", pos, 1.5f, 1f);
                sound("manji_slam", pos, 1.2f, 1f);
                if (drawn) {
                    // Crushed to bits: a shower of red pieces and a crater (GIF).
                    burst(level, pos.add(0, 0.5, 0), q(30), 0.5, Sprite.SHARD, BLOOD, 0.18f, 30).forEach(x -> x.gravity(1f).physics());
                    Flashes.ground(groundBelow(level, pos), 0.5f, 4f, BLOOD, 0.8f, 30, now);
                    debris(level, pos, q(20), 0.5);
                }
                distanceShake(pos, 20, 0.8f);
            }
            // --- Combat Instincts ---
            case "instincts_feint" -> {
                sound("manji_dodge", pos, 0.7f, 1.3f);
                if (drawn) {
                    // The crackling white ring around him (GIF).
                    for (int i = 0; i < 2; i++) Flashes.ripple(pos, dir, 1.6f + i * 0.3f, 1.7f + i * 0.3f, WHITE, 0.8f - i * 0.3f, 10, now + i * 2);
                    impactStar(pos, dir, 4, 1.6f, 0.03f, WHITE, now);
                }
            }
            case "instincts_throw" -> {
                sound("hit_heavy", pos, 1.1f, 0.9f);
                if (drawn) {
                    Flashes.flash(pos, 1.8f, 0.4f, WHITE, 1f, 5, now);
                    for (int i = 0; i < 3; i++) Flashes.ripple(pos, dir, 0.3f + i * 0.3f, 2f + i * 0.6f, WHITE, 0.7f, 8, now + i);
                }
            }
            case "prop_smash" -> {
                sound("hit_slam", pos, 1f, 1f);
                if (drawn) {
                    var state = Block.stateById(Math.round(s));
                    for (int i = 0; i < q(24); i++) {
                        Vec3 v = randomUnit().scale(0.3);
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, v.x, v.y + 0.2, v.z);
                    }
                    burst(level, pos, q(6), 0.2, Sprite.SMOKE, GREY, 0.6f, 14);
                }
            }
            // --- King of Curses ---
            case "sukuna_start" -> {
                sound("sukuna_awaken", pos, 1.6f, 1f);
                if (drawn) burst(level, pos, q(16), 0.1, Sprite.SMOKE, INK, 0.9f, 24);
            }
            case "sukuna_awaken" -> {
                // Red spikes of cursed energy burst out in black smoke (GIF); "You're such an annoying brat."
                dev.rick.jjk.client.hud.GojoPresentation.say(p.entityId(), "YOU'RE\nSUCH AN\nANNOYING\nBRAT.", Math.max(20, Math.round(s)), 1f);
                if (drawn) {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.toRadians(i * 60 + 30), tilt = 0.5 + RNG.nextDouble() * 0.6;
                        Vec3 d = new Vec3(Math.cos(a), tilt, Math.sin(a)).normalize();
                        line(pos, pos.add(d.scale(2.2 + RNG.nextDouble())), 0.35f, BF_RED, 1f, 14, now);
                    }
                    Flashes.flash(pos, 3f, 0.6f, BF_RED, 1f, 8, now);
                    for (int i = 0; i < q(40); i++) {
                        Vec3 v = randomUnit().scale(0.2 + RNG.nextDouble() * 0.25);
                        add(level, pos.add(gauss(0.4), gauss(0.6), gauss(0.4)), v, Sprite.SMOKE, i % 4 == 0 ? AURA : INK, 0.9f, 0.9f, 1.6f, 30).friction(0.9f);
                    }
                }
                distanceShake(pos, 30, 0.8f);
                if (mine) ScreenEffects.flash(0x60800010, 10);
            }
            case "sukuna_aura" -> {
                // The red smoke that never stops pouring off him (GIF), following him as he moves.
                var owner = level.getEntity(p.entityId());
                Vec3 base = owner != null ? owner.position().add(0, 1, 0) : pos;
                if (drawn) for (int i = 0; i < q(6); i++) {
                    Vec3 at = base.add(gauss(0.3), -0.9 + RNG.nextDouble() * 1.8, gauss(0.3));
                    add(level, at, new Vec3(gauss(0.012), 0.035 + RNG.nextDouble() * 0.02, gauss(0.012)), Sprite.SMOKE, i % 3 == 0 ? BLOOD : AURA,
                            0.6f, 0.3f, 0.75f, 16 + RNG.nextInt(8)).fadeIn();
                }
            }
            case "sukuna_end" -> {
                sound("awaken_end", pos, 1f, 0.8f);
                if (drawn) burst(level, pos, q(20), 0.12, Sprite.SMOKE, AURA, 0.7f, 20);
            }
            // --- Shrine M1s: white slashes landing wherever they reach ---
            case "shrine_swing" -> {
                sound("dismantle_finish", pos, 0.8f, 0.95f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    // A white sweep off his hand, and the slash out at range along his aim.
                    swingTrail(pos, dir, 1.1f, 0.1f, now);
                    Vec3 far = pos.add(dir.normalize().scale(4 + RNG.nextDouble() * 3));
                    crescent(far, dir, 1.4f, 0.1f, WHITE, 0.9f, 5, now, RNG.nextFloat() - 0.5f);
                }
            }
            case "shrine_hit_light", "shrine_hit_heavy", "shrine_hit_launch", "shrine_hit_slam" -> {
                boolean big = !p.id().equals("shrine_hit_light");
                sound("dismantle_slash", pos, big ? 1.2f : 0.9f, 1f + RNG.nextFloat() * 0.2f);
                if (drawn) {
                    crescent(pos, dir, big ? 1.8f : 1.3f, big ? 0.14f : 0.1f, WHITE, 1f, 5, now, RNG.nextFloat() - 0.5f);
                    crescent(pos, dir, big ? 1.6f : 1.1f, 0.05f, INK, 1f, 5, now, RNG.nextFloat() - 0.5f);
                    blood(level, pos, dir, q(big ? 10 : 6));
                }
                victimFeedback(p, big ? 0.5f : 0.25f);
            }
            // --- Cleave ---
            case "cleave_reach" -> sound("swing_heavy", pos, 0.9f, 0.9f);
            case "cleave_grab" -> sound("crushing_fist", pos, 1f, 0.9f);
            case "cleave", "cleave_finisher" -> {
                boolean fin = p.id().equals("cleave_finisher");
                sound("dismantle_slash", pos, 1.5f, 0.8f);
                sound("dismantle_finish", pos, 1.3f, 0.9f);
                if (drawn) {
                    // A storm of cuts, and the frame goes red and black (GIF frame 7).
                    for (int i = 0; i < (fin ? 14 : 9); i++) {
                        Vec3 d = randomUnit();
                        line(pos.subtract(d.scale(1.6)), pos.add(d.scale(1.6)), 0.05f, i % 2 == 0 ? BF_RED : WHITE, 1f, 6, now + i / 3);
                    }
                    blood(level, pos, dir, q(fin ? 30 : 16));
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 24) ScreenEffects.impact(3, 1);
            }
            case "cleave_hit" -> victimFeedback(p, 0.8f);
            case "cleave_dice" -> {
                sound("hit_heavy", pos, 1.2f, 0.7f);
                if (drawn) burst(level, pos, q(30), 0.35, Sprite.SHARD, BLOOD, 0.16f, 30).forEach(x -> x.gravity(1f).physics());
            }
            // --- Dismantle ---
            case "dismantle_windup" -> sound("swing_heavy", pos, 0.7f, 0.9f);
            case "dismantle_air_start" -> sound("dismantle_spin", pos, 1f, 1f);
            case "dismantle_swing" -> {
                sound("dismantle_finish", pos, 1.1f, 1f);
                if (drawn) swingTrail(pos, dir, 1.3f, 0.12f, now);
            }
            case "dismantle_slash" -> {
                sound("dismantle_slash", pos, 1f, 0.9f + RNG.nextFloat() * 0.3f);
                if (drawn) {
                    // The big black crescents with white edges across the target (GIF).
                    float tilt = (RNG.nextFloat() - 0.5f) * 2f;
                    crescent(pos, dir, 2.2f, 0.18f, WHITE, 1f, 6, now, tilt);
                    crescent(pos, dir, 2.0f, 0.09f, INK, 1f, 6, now, tilt);
                    blood(level, pos, dir, q(6));
                }
            }
            case "dismantle_hit" -> victimFeedback(p, 0.5f);
            case "dismantle_line" -> {
                // The long aerial cut: a white line along the ground that splits it (GIF).
                sound("dismantle_finish", pos, 1.5f, 0.8f);
                sound("dismantle_slash", pos, 1.5f, 0.7f);
                Vec3 end = pos.add(dir.normalize().scale(s));
                line(pos, end, 0.25f, WHITE, 1f, 10, now);
                line(pos, end, 0.1f, INK, 1f, 12, now);
                if (drawn) for (int i = 0; i < q(10); i++) debris(level, pos.lerp(end, RNG.nextDouble()), 3, 0.35);
            }
            case "dismantle_finisher", "dismantle_dismember", "dismantle_halve", "world_slash_halve" -> {
                sound("dismantle_slash", pos, 1.4f, 0.7f);
                if (drawn) {
                    blood(level, pos, dir, q(30));
                    burst(level, pos, q(12), 0.3, Sprite.SHARD, BLOOD, 0.2f, 30).forEach(x -> x.gravity(1f).physics());
                }
            }
            // --- World Cutting Slash ---
            case "wcs_chant" -> {
                int line = Mth.clamp(Math.round(s), 1, 3);
                sound("wcs_line_" + line, pos, 1.6f, 1f);
                dev.rick.jjk.client.hud.GojoPresentation.say(p.entityId(), DismantleAbility.CHANT[line], 34, 1f);
                if (drawn) burst(level, pos.add(0, -0.6, 0), q(8), 0.08, Sprite.SMOKE, AURA, 0.6f, 20);
            }
            case "world_slash" -> {
                // The world cut: a black band across everything, white streaks tearing out of it (GIF frame 10).
                sound("dismantle_finish", pos, 3f, 0.6f);
                sound("dismantle_slash", pos, 3f, 0.5f);
                Vec3 f = dir.normalize();
                Vec3 side = new Vec3(-f.z, 0, f.x).normalize();
                Vec3 mid = pos.add(f.scale(s * 0.5));
                float half = s * 0.6f;
                line(mid.subtract(side.scale(half)), mid.add(side.scale(half)), 0.9f, INK, 1f, 16, now);
                line(mid.subtract(side.scale(half)), mid.add(side.scale(half)), 0.25f, WHITE, 1f, 10, now);
                for (int i = 0; i < 12; i++) {
                    Vec3 a = mid.add(side.scale((RNG.nextDouble() - 0.5) * half * 2)).add(0, gauss(0.3), 0);
                    line(a, a.add(f.scale(-2 - RNG.nextDouble() * 4)), 0.08f, WHITE, 0.9f, 8, now + RNG.nextInt(4));
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < s * 1.5) {
                    ScreenEffects.impact(8, 2);
                    ScreenEffects.shake(1.4f, 20);
                }
            }
            case "world_slash_hit" -> victimFeedback(p, 1f);
            // --- Open ---
            case "open_hands" -> {
                sound("open_hands", pos, 1.2f, 1f);
                if (drawn) fire(level, pos.add(0, 0.6, 0), q(20), 0.6);
            }
            case "open_clap" -> {
                sound("open_clap", pos, 1.3f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.6f, 0.4f, FIRE_CORE, 1f, 5, now);
                    fire(level, pos, q(24), 0.8);
                }
            }
            case "open_draw" -> {
                sound("open_arrow", pos, 1.2f, 1f);
                sound("open_idle", pos, 1f, 1f);
                if (drawn) {
                    line(pos, pos.add(dir.scale(1.6)), 0.15f, FIRE_CORE, 1f, 16, now);
                    fire(level, pos.add(dir.scale(0.8)), q(16), 0.4);
                }
            }
            case "open_fire" -> {
                sound("open_fire", pos, 1.6f, 1f);
                line(pos, pos.add(dir.scale(10)), 0.4f, FIRE_CORE, 1f, 6, now);
                if (drawn) fire(level, pos.add(dir), q(30), 1.2);
                if (mine) ScreenEffects.fovPunch(0.08f);
            }
            case "open_trail" -> {
                if (drawn) {
                    line(pos, pos.subtract(dir.scale(3)), 0.3f, FIRE, 0.9f, 5, now);
                    fire(level, pos, q(6), 0.15);
                }
            }
            case "open_pillar" -> {
                // The massive pillar of fire (GIF frames 10-11).
                sound("open_explode", pos, 3f, 1f);
                float r = Math.max(1.5f, s);
                Vec3 g = groundBelow(level, pos);
                Flashes.flash(pos, r * 1.5f, r * 0.5f, FIRE_CORE, 1f, 10, now);
                line(g, g.add(0, 14, 0), r * 0.9f, FIRE, 0.95f, 26, now);
                line(g, g.add(0, 16, 0), r * 0.45f, FIRE_CORE, 1f, 22, now);
                Flashes.ground(g, 0.5f, r * 2f, FIRE, 0.9f, 30, now);
                if (drawn) {
                    for (int i = 0; i < q(90); i++) {
                        double a = RNG.nextDouble() * Mth.TWO_PI, rr = RNG.nextDouble() * r;
                        Vec3 at = g.add(Math.cos(a) * rr, RNG.nextDouble() * 10, Math.sin(a) * rr);
                        add(level, at, new Vec3(gauss(0.05), 0.25 + RNG.nextDouble() * 0.3, gauss(0.05)), Sprite.GLOW, i % 3 == 0 ? FIRE_CORE : FIRE,
                                0.9f, 0.5f, 0.1f, 20 + RNG.nextInt(12));
                    }
                    burst(level, g.add(0, 1, 0), q(20), 0.3, Sprite.SMOKE, GREY, 1.2f, 40);
                    for (int i = 0; i < q(20); i++) level.addParticle(ParticleTypes.FLAME, g.x + gauss(r * 0.5), g.y + 0.2, g.z + gauss(r * 0.5), 0, 0.1, 0);
                }
                distanceShake(pos, 48, 1.2f);
                if (mc.player != null && mc.player.position().distanceTo(pos) < 30) ScreenEffects.flash(0x60FF8020, 10);
            }
            case "open_hit" -> victimFeedback(p, 0.9f);
            case "open_burn" -> {
                // Burnt to a crisp: black smoke and embers pouring off them.
                sound("open_explode", pos, 1.2f, 1.3f);
                if (drawn) {
                    burst(level, pos, q(24), 0.15, Sprite.SMOKE, INK, 0.9f, 40);
                    for (int i = 0; i < q(16); i++) add(level, pos.add(gauss(0.3), gauss(0.5), gauss(0.3)), new Vec3(gauss(0.03), 0.06, gauss(0.03)), Sprite.SPARK, FIRE, 1f, 0.08f, 0.02f, 30);
                }
            }
            // --- Rush ---
            case "rush_start" -> {
                sound("rush_start", pos, 1.2f, 1f);
                if (drawn) Flashes.ground(groundBelow(level, pos), 0.3f, 2f, WHITE, 0.6f, 8, now);
            }
            case "rush_step" -> {
                if (drawn) {
                    burst(level, pos.add(0, 0.1, 0), q(3), 0.08, Sprite.SMOKE, GREY, 0.5f, 10);
                    add(level, pos.add(0, 0.9, 0), Vec3.ZERO, Sprite.SMOKE, AURA, 0.5f, 0.5f, 0.3f, 8);
                }
            }
            case "rush_hit" -> {
                sound("rush_hit", pos, 1.2f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 1.8f, 0.3f, WHITE, 1f, 4, now);
                    impactStar(pos, dir, 8, 1.8f, 0.07f, WHITE, now);
                }
                victimFeedback(p, 0.6f);
            }
            case "rush_knee" -> {
                sound("rush_break", pos, 1.3f, 1f);
                if (drawn) {
                    Flashes.flash(pos, 2f, 0.4f, WHITE, 1f, 5, now);
                    line(pos.add(0, -1, 0), pos.add(0, 3, 0), 0.2f, WHITE, 0.9f, 6, now);
                    for (int i = 0; i < 3; i++) Flashes.ring(pos, 0.4f, 2f + i, WHITE, 0.5f, 8, now + i);
                }
                victimFeedback(p, 0.8f);
            }
            case "rush_slam" -> {
                sound("rush_slam", pos, 1.4f, 1f);
                if (drawn) {
                    Vec3 g = groundBelow(level, pos);
                    Flashes.ground(g, 0.5f, 4f, WHITE, 0.8f, 12, now);
                    debris(level, g, q(24), 0.55);
                    burst(level, g, q(10), 0.25, Sprite.SMOKE, GREY, 0.9f, 20);
                }
                distanceShake(pos, 24, 0.8f);
            }
            // --- Malevolent Shrine ---
            case "shrine_charge" -> {
                sound("shrine_voice", pos, 2f, 1f);
                sound("shrine_ready", pos, 1.5f, 1f);
                if (drawn) for (int i = 0; i < q(24); i++) {
                    Vec3 from = pos.add(randomUnit().scale(3 + RNG.nextDouble() * 2));
                    add(level, from, Vec3.ZERO, Sprite.SMOKE, i % 2 == 0 ? AURA : INK, 0.7f, 0.5f, 0.2f, 26).attract(pos, 0.012).fadeIn();
                }
            }
            case "shrine_slash" -> {
                if (RNG.nextInt(3) == 0) sound("dismantle_slash", pos, 0.7f * s, 0.9f + RNG.nextFloat() * 0.3f);
                if (RNG.nextInt(6) == 0) sound("shrine_ring", pos, 0.5f, 1f);
                if (drawn) {
                    Vec3 d = randomUnit();
                    crescent(pos, d, 1.5f + RNG.nextFloat(), 0.08f, WHITE, 0.9f * s, 4, now, RNG.nextFloat() - 0.5f);
                    blood(level, pos, d, q(s < 1 ? 1 : 3));
                }
                if (mine) ScreenEffects.shake(0.15f, 3);
            }
            default -> {}
        }
    }

    /** A beam; black ones are drawn alpha-blended so they actually show (additive light can't be black). */
    static void line(Vec3 a, Vec3 b, float width, float[] c, float alpha, int life, long now) {
        if (c == INK || c == BLOOD) Flashes.darkBeam(a, b, width * 1.2f, c, alpha, life, now);
        else Flashes.beam(a, b, width, c, alpha, life, now);
    }

    /** A curved slash: an arc of beams across {@code center}, facing along {@code dir}, tilted by {@code tilt}. */
    static void crescent(Vec3 center, Vec3 dir, float radius, float width, float[] c, float alpha, int life, long now, float tilt) {
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 upv = side.cross(d).normalize();
        Vec3 axis = side.scale(Math.cos(tilt)).add(upv.scale(Math.sin(tilt)));
        Vec3 bend = upv.scale(Math.cos(tilt)).subtract(side.scale(Math.sin(tilt)));
        Vec3 prev = null;
        for (int i = 0; i <= 8; i++) {
            double t = (i / 8.0 - 0.5) * 2.4;
            Vec3 at = center.add(axis.scale(Math.sin(t) * radius)).add(bend.scale((Math.cos(t) - 0.7) * radius * 0.35));
            if (prev != null) line(prev, at, width * (float) Math.max(0.2, Math.cos(t * 0.8)), c, alpha, life, now);
            prev = at;
        }
    }

    /** Red lightning and black sparks: the Black Flash (GIF). */
    private static void blackFlash(ClientLevel level, Vec3 pos, Vec3 dir, float s, long now, boolean heavy) {
        Flashes.flash(pos, 2.5f * s, 0.6f, BF_RED, 1f, 6, now);
        Flashes.flash(pos, 1.4f * s, 0.3f, INK, 1f, 8, now + 1);
        for (int i = 0; i < 10 + (heavy ? 10 : 0); i++) {
            // Jagged bolts: a few kinked segments each.
            Vec3 a = pos;
            Vec3 d = randomUnit().add(dir.lengthSqr() > 1e-4 ? dir.normalize().scale(0.5) : Vec3.ZERO).normalize();
            for (int k = 0; k < 3; k++) {
                Vec3 b = a.add(d.scale((0.5 + RNG.nextDouble() * 0.8) * s)).add(gauss(0.2), gauss(0.2), gauss(0.2));
                line(a, b, 0.07f * s, i % 3 == 0 ? INK : BF_RED, 1f, 6 + k, now);
                a = b;
            }
        }
        sparks(level, pos, dir, q(heavy ? 30 : 16), 0.7 * s, INK, 0.14f, 10);
        sparks(level, pos, dir, q(heavy ? 20 : 10), 0.6 * s, BF_RED, 0.12f, 8);
        if (heavy) {
            // The fourth: rainbow rays and a blue shock across the frame (GIF).
            float[][] rainbow = {{1f, 0.2f, 0.2f}, {1f, 0.7f, 0.2f}, {1f, 1f, 0.3f}, {0.3f, 1f, 0.4f}, {0.3f, 0.7f, 1f}, {0.7f, 0.3f, 1f}};
            for (int i = 0; i < rainbow.length; i++) {
                Vec3 d = randomUnit();
                line(pos, pos.add(d.scale(5 * s)), 0.12f * s, rainbow[i], 0.9f, 10, now);
            }
            Vec3 side = dir.lengthSqr() > 1e-4 ? new Vec3(-dir.z, 0, dir.x).normalize() : new Vec3(1, 0, 0);
            line(pos.subtract(side.scale(8)), pos.add(side.scale(8)), 0.25f, CE_LIGHT, 1f, 8, now);
            impactStar(pos, dir, 16, 3f * s, 0.1f, CE, now);
        }
    }

    private static void blood(ClientLevel level, Vec3 pos, Vec3 dir, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(0.2).add(dir.lengthSqr() > 1e-4 ? dir.normalize().scale(0.15) : Vec3.ZERO);
            add(level, pos, v.add(0, 0.1, 0), Sprite.SHARD, BLOOD, 1f, 0.12f + RNG.nextFloat() * 0.08f, 0.06f, 24).gravity(1f).physics();
        }
    }

    private static void fire(ClientLevel level, Vec3 pos, int n, double spread) {
        for (int i = 0; i < n; i++) {
            Vec3 at = pos.add(gauss(spread * 0.5), gauss(spread * 0.5), gauss(spread * 0.5));
            add(level, at, new Vec3(gauss(0.02), 0.05 + RNG.nextDouble() * 0.05, gauss(0.02)), Sprite.GLOW, i % 3 == 0 ? FIRE_CORE : FIRE,
                    0.9f, 0.25f, 0.04f, 12 + RNG.nextInt(8));
        }
    }
}
