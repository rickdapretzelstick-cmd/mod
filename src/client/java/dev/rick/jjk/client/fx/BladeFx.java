package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.RNG;
import static dev.rick.jjk.client.fx.ClientFx.add;
import static dev.rick.jjk.client.fx.ClientFx.burst;
import static dev.rick.jjk.client.fx.ClientFx.implode;
import static dev.rick.jjk.client.fx.ClientFx.q;
import static dev.rick.jjk.client.fx.ClientFx.randomUnit;

/**
 * The Cursed Blade's effects ("cb_*"): crimson cursed energy thrown off heavy two-handed cuts (a red crescent with a
 * hot core over a black one), a crescent wave that travels, and the black thorns: dark spikes bursting out of the ground
 * with a red seam, short-lived and jagged. Every big moment has one flash; the rest is shape and particles.
 */
final class BladeFx {
    static final float[] RED = {0.86f, 0.06f, 0.15f};
    static final float[] HOT = {1f, 0.42f, 0.48f};
    static final float[] DEEP = {0.32f, 0.02f, 0.06f};
    static final float[] BLACK = {0.05f, 0.02f, 0.04f};
    static final float[] WHITE = {1f, 0.95f, 0.95f};

    private BladeFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = ClientFx.lod > 0;
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        switch (p.id()) {
            case "cb_swing" -> {
                // A basic cut: a thin crimson crescent with a bright edge.
                float tilt = (RNG.nextFloat() - 0.5f) * 1.6f;
                arc(pos, d, 1.5f * s, 0.10f * s, tilt, 5, now);
                if (drawn) trail(level, pos, d, q(5), 0.8f);
            }
            case "cb_hit_light", "cb_hit_heavy", "cb_hit_launch", "cb_hit_slam" -> {
                boolean heavy = !p.id().equals("cb_hit_light");
                ClientFx.victimFeedback(p, heavy ? 0.6f : 0.3f);
                Flashes.flash(pos, 0.2f, (heavy ? 1.3f : 0.8f) * s, RED, 0.9f, heavy ? 6 : 4, now);
                Flashes.flash(pos, 0.1f, 0.5f * s, WHITE, 0.8f, 3, now);
                if (drawn) {
                    sparks(level, pos, d, q(heavy ? 14 : 7), 0.45 * s, RED);
                    sparks(level, pos, d, q(heavy ? 8 : 4), 0.35 * s, BLACK);
                }
                if (p.id().equals("cb_hit_slam") && drawn) ClientFx.debris(level, pos, q(10), 0.3);
            }
            case "cb_charge" -> {
                // Cursed energy gathering along the blade.
                if (!drawn) return;
                implode(level, pos, 1.6 + s, q(6 + Math.round(6 * s)), RED, 0.12f, 10);
                implode(level, pos, 1.2 + s, q(3), BLACK, 0.16f, 12);
                if (s > 0.8f) Flashes.flash(pos, 0.3f, 0.6f, RED, 0.5f, 3, now);
            }
            case "cb_arc", "cb_cross" -> {
                // Heavy Slash: a huge diagonal crimson crescent (the cross comes back on the other diagonal).
                float tilt = p.id().equals("cb_arc") ? 0.85f : -0.85f;
                arc(pos, d, 2.2f * s, 0.24f * s, tilt, 8, now);
                Flashes.flash(pos, 0.5f, 2.2f * s, RED, 0.7f, 5, now);
                if (drawn) {
                    trail(level, pos, d, q(18), 1.6f * s);
                    burst(level, pos, q(6), 0.15, Sprite.SMOKE, DEEP, 0.4f, 18);
                }
            }
            case "cb_rising" -> {
                // Rising Cut: the crescent stood on end, sweeping up.
                arc(pos, d, 1.9f * s, 0.18f * s, (float) (Math.PI / 2), 7, now);
                if (drawn) for (int i = 0; i < q(10); i++) {
                    add(level, pos.add(gauss(0.4), RNG.nextDouble() * 1.5, gauss(0.4)), new Vec3(0, 0.15 + RNG.nextDouble() * 0.1, 0), Sprite.GLOW, i % 3 == 0 ? HOT : RED,
                            0.9f, 0.14f, 0.02f, 12);
                }
            }
            case "cb_wave_release" -> {
                Flashes.flash(pos, 0.5f, 1.8f * s, RED, 0.8f, 5, now);
                Flashes.ring(pos, 0.3f, 2.2f * s, HOT, 0.7f, 6, now);
                if (drawn) burst(level, pos, q(10), 0.25, Sprite.SPARK, HOT, 0.12f, 10);
            }
            case "cb_wave" -> {
                // The crescent in flight: a flat arc across its path, redrawn every tick as it travels, a wake behind it.
                arc(pos, d, 1.7f * s * 0.6f + 0.6f, 0.22f, 0f, 3, now);
                Vec3 side = new Vec3(-d.z, 0, d.x);
                if (drawn) for (int i = 0; i < q(6); i++) {
                    double t = RNG.nextDouble() * 2 - 1;
                    Vec3 at = pos.add(side.scale(t * 1.5 * s)).subtract(d.scale(0.3 + RNG.nextDouble() * 0.6));
                    add(level, at, d.scale(-0.02).add(0, 0.02, 0), Sprite.GLOW, i % 2 == 0 ? RED : HOT, 0.9f, 0.18f, 0.03f, 10);
                }
                if (drawn && RNG.nextInt(2) == 0) add(level, pos.subtract(d.scale(0.5)), Vec3.ZERO, Sprite.SMOKE, BLACK, 0.5f, 0.4f, 0.6f, 14);
            }
            case "cb_wave_burst" -> {
                Flashes.flash(pos, 0.4f, 1.6f * s, RED, 0.8f, 5, now);
                if (drawn) {
                    burst(level, pos, q(14), 0.35, Sprite.SHARD, RED, 0.14f, 16).forEach(x -> x.gravity(0.6f));
                    burst(level, pos, q(6), 0.2, Sprite.SMOKE, BLACK, 0.4f, 18);
                }
            }
            case "cb_lunge_trail" -> {
                Vec3 a = pos.subtract(d.scale(1.8)), b = pos.add(d.scale(1.2));
                Flashes.beam(a, b, 0.12f, RED, 0.8f, 4, now);
                Flashes.darkBeam(a.add(0, -0.1, 0), b.add(0, -0.1, 0), 0.07f, BLACK, 0.8f, 4, now);
                if (drawn) trail(level, pos, d.scale(-1), q(4), 0.6f);
            }
            case "cb_thorns" -> thorns(level, pos, s, 7, 1.3, now, drawn);
            case "cb_guard" -> {
                // The guard bristles: short black thorns all round the blade, a red glint along them.
                for (int i = 0; i < 12; i++) {
                    double a = Mth.TWO_PI * i / 12;
                    Vec3 out = new Vec3(Math.cos(a), (RNG.nextDouble() - 0.5) * 0.6, Math.sin(a)).normalize();
                    Vec3 tip = pos.add(out.scale(0.8 + RNG.nextDouble() * 0.5));
                    Flashes.darkBeam(pos, tip, 0.06f, BLACK, 1f, 12, now);
                    Flashes.beam(pos.lerp(tip, 0.3), pos.lerp(tip, 0.7), 0.02f, RED, 0.9f, 10, now);
                }
                Flashes.flash(pos, 0.3f, 0.9f, RED, 0.5f, 4, now);
            }
            case "cb_ult_charge" -> {
                // Black Thorn gathering: the energy drawn up into the raised blade, the ground darkening round them.
                if (!drawn) return;
                Vec3 up = pos.add(0, 1.4, 0);
                implode(level, up, 2.5 + s, q(12), RED, 0.14f, 12);
                implode(level, up, 2.0 + s, q(6), BLACK, 0.2f, 14);
                Flashes.ground(pos.add(0, -1.1, 0), 0.5f, 2.5f * s, DEEP, 0.4f, 8, now);
            }
            case "cb_ult_impact" -> {
                // The blade goes in: one hard flash, the ground ring, and the first cluster of thorns at the centre.
                Flashes.flash(pos.add(0, 1, 0), 1f, 5f * s, RED, 1f, 8, now);
                Flashes.flash(pos.add(0, 1, 0), 0.5f, 2.2f * s, WHITE, 0.9f, 4, now);
                Flashes.ground(pos.add(0, 0.05, 0), 0.5f, 6f * s, RED, 0.7f, 14, now);
                thorns(level, pos, 1.8f * s, 12, 1.0, now, drawn);
                if (drawn) {
                    ClientFx.debris(level, pos, q(28), 0.5);
                    burst(level, pos.add(0, 0.5, 0), q(20), 0.5, Sprite.SHARD, BLACK, 0.18f, 22).forEach(x -> x.gravity(1f).physics());
                }
            }
            case "cb_thorn_ring" -> {
                // One ring of the spreading thorns at radius s: spikes all round it, left standing a moment.
                int n = Math.max(6, Math.round(s * 3));
                for (int i = 0; i < n; i++) {
                    double a = Mth.TWO_PI * (i + RNG.nextDouble() * 0.6) / n;
                    Vec3 at = pos.add(Math.cos(a) * s, 0, Math.sin(a) * s);
                    spike(at, 1.1 + RNG.nextDouble() * 1.6, 0.11f, a, now, 16);
                }
                Flashes.ground(pos.add(0, 0.05, 0), Math.max(0.2f, s - 0.6f), s + 0.4f, DEEP, 0.5f, 6, now);
                if (drawn) for (int i = 0; i < q(n / 2 + 2); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI;
                    ClientFx.debris(level, pos.add(Math.cos(a) * s, 0, Math.sin(a) * s), 1, 0.25);
                }
            }
            default -> {}
        }
    }

    /** A crimson crescent: a black underside, the red body, a white-hot core along it. */
    static void arc(Vec3 pos, Vec3 d, float radius, float width, float tilt, int life, long now) {
        YujiFx.crescent(pos, d, radius * 1.03f, width * 1.6f, BLACK, 0.8f, life, now, tilt);
        YujiFx.crescent(pos, d, radius, width, RED, 1f, life, now, tilt);
        YujiFx.crescent(pos, d, radius * 0.98f, width * 0.35f, HOT, 1f, Math.max(2, life - 2), now, tilt);
    }

    /** Black thorns bursting out of the ground round {@code pos}. */
    static void thorns(ClientLevel level, Vec3 pos, float s, int n, double spread, long now, boolean drawn) {
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Mth.TWO_PI;
            double r = RNG.nextDouble() * spread * s * 0.7;
            spike(pos.add(Math.cos(a) * r, 0, Math.sin(a) * r), (1.0 + RNG.nextDouble() * 1.4) * s, 0.12f * Math.max(0.7f, s), a, now, 14);
        }
        Flashes.flash(pos.add(0, 0.6, 0), 0.3f, 1.2f * s, RED, 0.6f, 4, now);
        if (drawn) {
            ClientFx.debris(level, pos, q(8), 0.3);
            burst(level, pos.add(0, 0.4, 0), q(8), 0.3, Sprite.SHARD, BLACK, 0.12f, 16).forEach(x -> x.gravity(0.8f));
        }
    }

    /** One thorn: a dark spike leaning out from its base, kinked once, a thin red seam up its middle. */
    static void spike(Vec3 base, double height, float width, double lean, long now, int life) {
        Vec3 out = new Vec3(Math.cos(lean), 0, Math.sin(lean)).scale(0.25 + RNG.nextDouble() * 0.35);
        Vec3 mid = base.add(out.scale(height * 0.5)).add(0, height * 0.55, 0);
        Vec3 tip = base.add(out.scale(height)).add(gauss(0.1), height, gauss(0.1));
        Flashes.darkBeam(base, mid, width, BLACK, 1f, life, now);
        Flashes.darkBeam(mid, tip, width * 0.55f, BLACK, 1f, life, now);
        Flashes.beam(base.add(0, height * 0.1, 0), mid, width * 0.25f, RED, 0.9f, Math.max(3, life - 4), now);
    }

    private static void trail(ClientLevel level, Vec3 pos, Vec3 d, int n, float spread) {
        for (int i = 0; i < n; i++) {
            Vec3 at = pos.add(randomUnit().scale(RNG.nextDouble() * spread));
            add(level, at, d.scale(0.05).add(0, 0.01, 0), Sprite.GLOW, i % 3 == 0 ? HOT : RED, 0.85f, 0.14f, 0.02f, 10 + RNG.nextInt(6));
        }
    }

    private static void sparks(ClientLevel level, Vec3 pos, Vec3 d, int n, double speed, float[] c) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomUnit().scale(speed * (0.5 + RNG.nextDouble())).add(d.scale(speed * 0.4));
            add(level, pos, v, Sprite.SPARK, c, 1f, 0.12f, 0.02f, 8 + RNG.nextInt(5));
        }
    }

    private static double gauss(double s) {
        return RNG.nextGaussian() * s;
    }
}
