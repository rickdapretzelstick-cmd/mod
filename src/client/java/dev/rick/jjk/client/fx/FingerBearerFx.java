package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.ClientProgression;
import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.RNG;
import static dev.rick.jjk.client.fx.ClientFx.add;
import static dev.rick.jjk.client.fx.ClientFx.burst;
import static dev.rick.jjk.client.fx.ClientFx.implode;
import static dev.rick.jjk.client.fx.ClientFx.q;
import static dev.rick.jjk.client.fx.ClientFx.randomUnit;
import static dev.rick.jjk.client.fx.ClientFx.ring3d;

/**
 * The Finger Bearer's effects ("fb_*"): raw cursed energy, nothing refined. A near-black core with a sickly violet
 * glow and heavy smoke; impacts throw dust and stone. Danger areas are drawn flat on the floor for exactly as long as
 * the windup lasts (the server sends the duration), bright enough to read without hiding the player's view. Effects
 * that carry the curse's id are skipped on a client that can't perceive it, like the curse itself.
 */
final class FingerBearerFx {
    static final float[] CORE = {0.07f, 0.01f, 0.1f};
    static final float[] VIOLET = {0.55f, 0.16f, 0.85f};
    static final float[] VIOLET_LIGHT = {0.85f, 0.6f, 1f};
    static final float[] WARN = {0.9f, 0.15f, 0.35f};
    static final float[] DUST = {0.35f, 0.33f, 0.36f};

    private FingerBearerFx() {}

    static void play(FxPayload p, ClientLevel level, Vec3 pos, Vec3 dir, float s, long now) {
        Entity source = p.entityId() >= 0 ? level.getEntity(p.entityId()) : null;
        if (source != null && !ClientProgression.canSee(source)) return;
        if (ClientFx.lod <= 0) return;
        Vec3 up = new Vec3(0, 1, 0);
        switch (p.id()) {
            case "fb_manifest" -> {
                // It takes shape over the seal out of black smoke.
                for (int i = 0; i < q(40); i++) {
                    Vec3 o = randomUnit().multiply(1.6, 2.2, 1.6);
                    add(level, pos.add(o), o.scale(-0.04), Sprite.SMOKE, CORE, 0.8f, 0.9f, 0.3f, 24 + RNG.nextInt(10)).fadeIn();
                }
                Flashes.flash(pos, 0.5f, 3f, VIOLET, 0.5f, 14, now);
            }
            case "fb_roar" -> {
                Flashes.ripple(pos, dir.lengthSqr() > 1e-4 ? dir : up, 0.4f, 4.5f, VIOLET_LIGHT, 0.55f, 12, now);
                for (int i = 0; i < q(16); i++) {
                    Vec3 v = dir.scale(0.25).add(randomUnit().scale(0.12));
                    add(level, pos, v, Sprite.SMOKE, CORE, 0.6f, 0.4f, 1.3f, 20).friction(0.9f);
                }
            }
            case "fb_shot_windup" -> implode(level, pos, 1.1, q(12), VIOLET, 0.1f, 11);
            case "fb_shot_fire" -> {
                Flashes.flash(pos, 0.3f, 1.2f, VIOLET, 0.8f, 5, now);
                ClientFx.sparks(level, pos, dir, q(10), 0.5, VIOLET_LIGHT, 0.08f, 8);
                burst(level, pos, q(5), 0.05, Sprite.SMOKE, CORE, 0.4f, 14);
            }
            case "fb_shot_land", "fb_fizzle" -> {
                Flashes.flash(pos, 0.3f, 1.4f * Math.max(1, s), VIOLET, 0.7f, 6, now);
                burst(level, pos, q(10), 0.18, Sprite.GLOW, VIOLET, 0.12f, 10);
                burst(level, pos, q(6), 0.06, Sprite.SMOKE, CORE, 0.5f, 18);
            }
            case "fb_gather" -> {
                // Energy dragged in between its claws: more of it, faster, and a darker heart as the charge fills.
                float k = Mth.clamp(s, 0, 1);
                implode(level, pos, 2.6 - k, q(6 + Math.round(k * 12)), k > 0.6f ? VIOLET_LIGHT : VIOLET, 0.09f + k * 0.06f, 10);
                add(level, pos, Vec3.ZERO, Sprite.CORE, CORE, 0.9f, 0.25f + k * 0.55f, 0.2f + k * 0.5f, 4);
                Flashes.flash(pos, 0.2f + k * 0.6f, 0.4f + k * 1.1f, VIOLET, 0.25f + k * 0.4f, 4, now);
                if (k > 0.5f && RNG.nextInt(3) == 0) ClientFx.sparks(level, pos, randomUnit(), q(3), 0.25, VIOLET_LIGHT, 0.05f, 6);
            }
            case "fb_blast_fire" -> {
                Flashes.flash(pos, 0.8f, 3.2f, VIOLET_LIGHT, 0.9f, 7, now);
                Flashes.ripple(pos, dir, 0.5f, 3.5f, VIOLET, 0.6f, 9, now);
                burst(level, pos, q(16), 0.12, Sprite.SMOKE, CORE, 0.7f, 22);
            }
            case "fb_blast_land" -> {
                float r = Math.max(1.5f, s);
                Flashes.flash(pos, 1f, r * 1.6f, VIOLET_LIGHT, 0.85f, 9, now);
                Flashes.ground(pos.add(0, -0.4, 0), 0.5f, r, VIOLET, 0.5f, 14, now);
                burst(level, pos, q(24), 0.32, Sprite.GLOW, VIOLET, 0.16f, 14);
                burst(level, pos, q(14), 0.1, Sprite.SMOKE, CORE, 0.9f, 28);
                ClientFx.debris(level, pos, q(10), 0.35);
            }
            case "fb_burst_warn", "fb_smash_warn" -> {
                // The danger area on the floor for the whole windup (dir.x = ticks): the edge in warning red, a pale fill.
                int life = Math.max(4, Math.round((float) dir.x));
                float r = Math.max(0.5f, s);
                boolean smash = p.id().equals("fb_smash_warn");
                Flashes.ground(pos, r, r, smash ? WARN : VIOLET, 0.28f, life, now);
                int n = q(Math.round(r * 10));
                for (int i = 0; i < n; i++) {
                    double a = Mth.TWO_PI * i / n;
                    add(level, pos.add(Math.cos(a) * r, 0.06, Math.sin(a) * r), new Vec3(0, 0.004, 0), Sprite.GLOW, WARN, 0.85f, 0.14f, 0.18f, life);
                }
                if (!smash) implode(level, pos.add(0, 1.4, 0), r, q(18), VIOLET, 0.12f, life);
            }
            case "fb_burst" -> {
                float r = Math.max(1f, s);
                Flashes.flash(pos.add(0, 1.4, 0), 1f, r * 1.3f, VIOLET_LIGHT, 0.85f, 7, now);
                Flashes.ground(pos, 0.5f, r + 0.5f, VIOLET, 0.6f, 12, now);
                ring3d(level, pos.add(0, 0.3, 0), up, 0.6, q(36), 0.55, VIOLET, 0.16f, 12);
                ring3d(level, pos.add(0, 1.4, 0), up, 0.4, q(24), 0.45, VIOLET_LIGHT, 0.1f, 10);
                burst(level, pos.add(0, 1.2, 0), q(18), 0.18, Sprite.SMOKE, CORE, 0.9f, 24);
            }
            case "fb_rush_windup" -> burst(level, pos, q(8), 0.05, Sprite.SMOKE, CORE, 0.5f, 18);
            case "fb_rush" -> {
                for (int i = 0; i < q(4); i++) {
                    add(level, pos.add(RNG.nextGaussian() * 0.4, 0, RNG.nextGaussian() * 0.4), dir.scale(-0.05).add(0, 0.03, 0),
                            Sprite.SMOKE, DUST, 0.5f, 0.4f, 0.9f, 16).friction(0.9f);
                }
                add(level, pos.add(0, 1.6, 0), dir.scale(-0.1), Sprite.SMOKE, CORE, 0.5f, 0.8f, 0.4f, 8);
            }
            case "fb_wall" -> {
                ClientFx.debris(level, pos, q(14), 0.3);
                burst(level, pos, q(10), 0.08, Sprite.SMOKE, DUST, 0.7f, 22);
                Flashes.flash(pos, 0.4f, 1.5f, DUST, 0.5f, 5, now);
            }
            case "fb_stagger" -> {
                for (int i = 0; i < q(6); i++) {
                    double a = Mth.TWO_PI * i / 6;
                    add(level, pos.add(Math.cos(a) * 0.6, 0.5, Math.sin(a) * 0.6), new Vec3(0, 0.01, 0), Sprite.STAR, VIOLET_LIGHT, 0.8f, 0.12f, 0.06f, 30)
                            .spin(0.3f);
                }
            }
            case "fb_smash" -> {
                float r = Math.max(1f, s);
                Flashes.flash(pos.add(0, 0.5, 0), 0.8f, r * 1.4f, VIOLET_LIGHT, 0.8f, 7, now);
                Flashes.ground(pos, 0.3f, r + 0.8f, VIOLET, 0.6f, 16, now);
                ring3d(level, pos.add(0, 0.15, 0), up, 0.4, q(30), 0.45, DUST, 0.22f, 14);
                ClientFx.debris(level, pos.add(0, 0.2, 0), q(20), 0.45);
                burst(level, pos.add(0, 0.4, 0), q(14), 0.1, Sprite.SMOKE, DUST, 0.9f, 26);
            }
            case "fb_impact" -> {
                float k = Math.max(0.6f, s);
                Flashes.flash(pos, 0.2f, 0.9f * k, VIOLET_LIGHT, 0.75f, 5, now);
                ClientFx.sparks(level, pos, dir, q(Math.round(8 * k)), 0.4, VIOLET, 0.07f, 8);
                burst(level, pos, q(4), 0.04, Sprite.SMOKE, CORE, 0.4f * k, 14);
            }
            case "fb_death" -> {
                Flashes.flash(pos, 0.6f, 3f, VIOLET, 0.6f, 12, now);
                burst(level, pos, q(20), 0.06, Sprite.SMOKE, CORE, 0.9f, 40);
            }
            case "fb_dissolve" -> {
                // The body comes apart into black smoke and motes that rise and fade.
                for (int i = 0; i < q(60); i++) {
                    Vec3 o = new Vec3(RNG.nextGaussian() * 0.9, RNG.nextDouble() * 1.2, RNG.nextGaussian() * 1.6);
                    add(level, pos.add(o), new Vec3(0, 0.03 + RNG.nextDouble() * 0.04, 0), i % 4 == 0 ? Sprite.GLOW : Sprite.SMOKE,
                            i % 4 == 0 ? VIOLET : CORE, 0.8f, i % 4 == 0 ? 0.08f : 0.6f, 0.2f, 30 + RNG.nextInt(20));
                }
            }
            case "fb_finger" -> {
                Flashes.flash(pos, 0.3f, 1.2f, new float[] {1f, 0.3f, 0.3f}, 0.7f, 10, now);
                for (int i = 0; i < q(10); i++) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x + RNG.nextGaussian() * 0.2, pos.y + 0.1, pos.z + RNG.nextGaussian() * 0.2, 0, 0.03, 0);
                }
            }
            default -> {}
        }
    }
}
