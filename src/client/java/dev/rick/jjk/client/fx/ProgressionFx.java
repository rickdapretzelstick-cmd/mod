package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.DARK;
import static dev.rick.jjk.client.fx.ClientFx.RNG;
import static dev.rick.jjk.client.fx.ClientFx.WHITE;
import static dev.rick.jjk.client.fx.ClientFx.add;
import static dev.rick.jjk.client.fx.ClientFx.burst;
import static dev.rick.jjk.client.fx.ClientFx.implode;
import static dev.rick.jjk.client.fx.ClientFx.q;
import static dev.rick.jjk.client.fx.ClientFx.randomUnit;
import static dev.rick.jjk.client.fx.ClientFx.ring3d;

/**
 * Survival progression effects ("prog_*"): drawing a soul out of Cursed Soul Sand, pouring cursed energy, the glasses'
 * infusion, and what a Cursed Finger does to whoever eats it. Restrained on purpose (these happen in plain Survival,
 * not in a fight): soul-blue and the dark violet of cursed energy, a few motes, one flash at the climax.
 */
final class ProgressionFx {
    static final float[] SOUL = {0.45f, 0.93f, 1f};
    static final float[] SOUL_LIGHT = {0.85f, 0.99f, 1f};
    static final float[] CURSED = {0.48f, 0.2f, 0.72f};
    static final float[] CURSED_LIGHT = {0.83f, 0.65f, 1f};
    static final float[] CURSED_DEEP = {0.16f, 0.05f, 0.26f};
    static final float[] BLOOD = {0.75f, 0.08f, 0.12f};

    private ProgressionFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = ClientFx.lod > 0;
        Vec3 up = new Vec3(0, 1, 0);
        switch (p.id()) {
            case "prog_extract" -> {
                // The soul leaves the sand in a thin stream that curls up into the bottle.
                if (!drawn) return;
                Vec3 to = pos.add(dir.lengthSqr() > 1e-4 ? dir.scale(0.8) : up);
                for (int i = 0; i < q(14); i++) {
                    Vec3 from = pos.add(RNG.nextGaussian() * 0.25, -0.1 + RNG.nextDouble() * 0.1, RNG.nextGaussian() * 0.25);
                    add(level, from, new Vec3(0, 0.03, 0), Sprite.GLOW, i % 3 == 0 ? SOUL_LIGHT : SOUL, 0.85f, 0.12f, 0.03f, 18 + RNG.nextInt(8))
                            .attract(to, 0.025).fadeIn();
                }
                for (int i = 0; i < q(6); i++) {
                    level.addParticle(ParticleTypes.SOUL, pos.x + RNG.nextGaussian() * 0.2, pos.y, pos.z + RNG.nextGaussian() * 0.2, 0, 0.04, 0);
                }
                burst(level, pos, q(4), 0.02, Sprite.SMOKE, CURSED_DEEP, 0.35f, 20);
                Flashes.flash(pos, 0.2f, 0.7f, SOUL, 0.6f, 6, now);
            }
            case "prog_pour" -> {
                // A splash of dark energy and the surface rising; every level a little brighter.
                if (!drawn) return;
                float k = Mth.clamp(s / 4f, 0.25f, 1f);
                ring3d(level, pos, up, 0.3, q(8), 0.03, CURSED, 0.12f, 12);
                burst(level, pos, q(5), 0.04, Sprite.SMOKE, CURSED_DEEP, 0.3f, 16);
                Flashes.ripple(pos, up, 0.1f, 0.45f, CURSED_LIGHT, 0.5f * k, 10, now);
            }
            case "prog_cauldron_full" -> {
                // 4/4: the energy settles and gathers itself, a ring of light round the rim.
                if (!drawn) return;
                Flashes.ring(pos.add(0, 0.45, 0), 0.2f, 0.9f, CURSED_LIGHT, 0.7f, 14, now);
                implode(level, pos.add(0, 0.45, 0), 1.2, q(16), CURSED, 0.12f, 18);
            }
            case "prog_infuse" -> {
                // The energy reacts to the glasses: each stage draws more of it spiralling in.
                if (!drawn) return;
                int stage = Math.round(s);
                double radius = 0.9 - stage * 0.08;
                int n = q(6 + stage * 4);
                for (int i = 0; i < n; i++) {
                    double a = Mth.TWO_PI * i / n + now * 0.4;
                    Vec3 from = pos.add(Math.cos(a) * radius, -0.05 + RNG.nextDouble() * 0.15, Math.sin(a) * radius);
                    Vec3 tangent = new Vec3(-Math.sin(a), 0.02, Math.cos(a)).scale(0.07);
                    add(level, from, tangent, Sprite.GLOW, i % 4 == 0 ? CURSED_LIGHT : CURSED, 0.85f, 0.11f + stage * 0.01f, 0.03f, 16)
                            .attract(pos.add(0, 0.12, 0), 0.03 + stage * 0.006).fadeIn();
                }
                Flashes.swirl(pos, up, 0.8f - stage * 0.1f, 0.15f, 2.6f, 0.45f, 0.06f, CURSED_LIGHT, 0.35f + stage * 0.08f, 10, now);
                if (stage >= 3) Flashes.ripple(pos, up, 0.6f, 0.1f, CURSED_DEEP, 0.6f, 8, now);
            }
            case "prog_infuse_abort" -> {
                if (drawn) burst(level, pos, q(6), 0.04, Sprite.SMOKE, CURSED_DEEP, 0.35f, 16);
            }
            case "prog_infuse_done" -> {
                // Collapse inward, one flash, and the glasses rise out with the last of it clinging to them.
                Flashes.flash(pos, 0.2f, 1.6f, WHITE, 0.9f, 5, now);
                Flashes.flash(pos, 0.4f, 1.1f, CURSED_LIGHT, 0.8f, 9, now);
                if (drawn) {
                    implode(level, pos, 1.4, q(22), CURSED, 0.14f, 12);
                    ring3d(level, pos, up, 0.2, q(14), 0.12, CURSED_LIGHT, 0.12f, 14);
                    for (int i = 0; i < q(8); i++) {
                        add(level, pos.add(randomUnit().scale(0.2)), new Vec3(RNG.nextGaussian() * 0.01, 0.05 + RNG.nextDouble() * 0.04, RNG.nextGaussian() * 0.01),
                                Sprite.STAR, CURSED_LIGHT, 0.9f, 0.08f, 0.02f, 24);
                    }
                }
                if (mc.player != null && mc.player.position().distanceTo(pos) < 6) ScreenEffects.flash(0x40B080FF, 6);
            }
            case "prog_finger_claim" -> {
                // Something ancient takes root: a dark pulse out of them, red marks of light, and then nothing visible.
                if (drawn) {
                    Flashes.ink(pos, 0.4f, 2.2f, DARK, 0.7f, 14, now);
                    ring3d(level, pos, up, 0.4, q(18), 0.16, BLOOD, 0.14f, 16);
                    burst(level, pos, q(10), 0.1, Sprite.SMOKE, DARK, 0.5f, 20);
                }
                if (mine) {
                    ScreenEffects.flash(0x90200008, 18);
                    ScreenEffects.shake(0.5f, 14);
                }
            }
            case "prog_finger_absorb" -> {
                if (drawn) implode(level, pos, 1.0, q(10), BLOOD, 0.1f, 14);
                if (mine) ScreenEffects.shake(0.25f, 8);
            }
            case "prog_finger_overload" -> {
                // Far too much for them: it tears out of them in black and red.
                if (drawn) {
                    Flashes.ink(pos, 0.6f, 3.2f, DARK, 0.85f, 18, now);
                    burst(level, pos, q(24), 0.25, Sprite.SMOKE, DARK, 0.6f, 24);
                    burst(level, pos, q(16), 0.35, Sprite.SPARK, BLOOD, 0.18f, 12);
                    for (int i = 0; i < 4; i++) Flashes.bolt(pos, pos.add(randomUnit().scale(1.8)), 0.06f, BLOOD, 0.9f, 6, now + i);
                }
                if (mine) {
                    ScreenEffects.flash(0xD0000000, 30);
                    ScreenEffects.shake(1.2f, 24);
                }
            }
            default -> {}
        }
    }
}
