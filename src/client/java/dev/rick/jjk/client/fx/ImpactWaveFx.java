package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.config.JJKConfig;
import dev.rick.jjk.core.fx.ImpactWave;
import dev.rick.jjk.core.fx.ImpactWave.Profile;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static dev.rick.jjk.client.fx.ClientFx.*;

/**
 * Draws impact waves ({@code core/fx/ImpactWave}): the one description of a blow's shock, rendered from its profile and
 * the event's point, direction and strength, so every client sees the same thing. Rings, pressure waves along the
 * blow, the shock shell of displaced air, the ground answering, dust, grit, the energy motif the move carries, the
 * camera's share, and a follow-through a few ticks on. Nothing here touches a block.
 */
public final class ImpactWaveFx {
    private record Later(long at, Runnable run) {}

    private static final List<Later> LATER = new ArrayList<>();
    private static final float[] INK = {0.03f, 0.03f, 0.04f};

    private ImpactWaveFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        String name = ImpactWave.profileName(p.id());
        Profile w = name == null ? null : ImpactWave.get(name);
        if (w == null) return;
        boolean drawn = lod > 0, blocked = ImpactWave.isBlocked(p.id()), reduced = JJKConfig.get().client.reducedFlashes;
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        float dim = reduced ? 0.45f : 1f;
        float[] core = w.theme().core(), energy = w.theme().energy(), accent = w.theme().accent(), dust = w.theme().dust();
        float r = w.radius() * s, flash = w.flash() * s;
        int life = Math.max(2, (int) Math.round(w.life() * Math.sqrt(s)));

        // The contact: a flash with a white-hot heart, and the dark pressure behind it.
        Flashes.flash(pos, flash, 0.25f, core, dim, 3, now);
        Flashes.flash(pos, 0.3f * flash, 0.1f, Flashes_WHITE, dim, 2, now);
        if (blocked) {
            // Stopped on a guard: the flash, a small ripple flattened against it, a few sparks, nothing else.
            Flashes.ripple(pos, d, 0.1f, 0.6f * r, energy, 0.6f * dim, 4, now);
            if (drawn) sparks(level, pos, d.scale(-1), q(Math.max(2, w.particles() / 3)), 0.3, accent, 0.09f, 5);
            return;
        }
        if (w.ink() > 0 && !reduced) Flashes.ink(pos, 0.2f * r, 0.55f * r, INK, w.ink(), life + 2, now);

        // The pressure, the way the move sends it.
        Vec3 up = new Vec3(0, 1, 0);
        switch (w.direction()) {
            case RADIAL -> rings(pos, r, life, w, energy, dim, now);
            case ALONG -> {
                // A ring in the plane of the blow leaving the contact: the air driven through, then thinner ones further on.
                Vec3 flat = new Vec3(d.x, d.y * 0.4, d.z).normalize();
                for (int i = 0; i < w.rings(); i++) {
                    float f = 1 - i * 0.25f;
                    Flashes.ripple(pos.add(flat.scale(i * w.lift() * 0.3f * s)), flat, 0.15f * r, r * f, energy, (0.75f - i * 0.2f) * dim, life + i * 2,
                            now + i, w.thickness() * f);
                }
                Flashes.ripple(pos.subtract(flat.scale(0.2)), flat, 0.1f, 0.5f * r, core, 0.5f * dim, Math.max(2, life - 2), now, w.thickness());
            }
            case DOWN -> {
                Vec3 g = groundBelow(level, pos);
                Flashes.beam(pos.add(0, 0.8 * s, 0), g, 0.1f * flash, core, 0.7f * dim, 4, now);
                Flashes.ripple(pos, up, 0.1f, 0.6f * r, energy, 0.6f * dim, Math.max(2, life - 2), now, w.thickness());
                ground(level, pos, r, life, w, energy, dim, now, true);
            }
            case UP -> {
                // A column of pressure rising off the point, rings climbing it.
                float h = w.lift() * s;
                Flashes.beam(pos.add(0, -0.3, 0), pos.add(0, h, 0), 0.08f * flash, energy, 0.7f * dim, life, now);
                for (int i = 0; i < w.rings(); i++) {
                    Flashes.ripple(pos.add(0, h * (0.15 + 0.35 * i), 0), up, 0.15f * r, r * (1 - i * 0.2f), energy, (0.7f - i * 0.18f) * dim, life + i,
                            now + i * 2L, w.thickness());
                }
            }
            case SWEEP -> {
                // The path of a blade or a shin: a crescent through the point and a ring across the blow's line.
                crescent(pos, d, 0.7f * r, 0.08f * flash, core, 0.9f * dim, 4, now);
                Flashes.ripple(pos, d, 0.1f * r, 0.8f * r, energy, 0.6f * dim, life, now, w.thickness());
            }
        }
        if (w.direction() != ImpactWave.Direction.DOWN) ground(level, pos, r, life, w, energy, dim, now, false);
        // Displaced air: a crisp shell leaving the point.
        if (w.lens() > 0 && !reduced) Flashes.lens(pos, 0.2f, w.lens() * s, core, 0.55f, Math.max(3, life - 1), now);

        if (drawn) {
            sparks(level, pos, w.direction() == ImpactWave.Direction.DOWN ? up : w.direction() == ImpactWave.Direction.UP ? up : d, q(w.particles()),
                    w.velocity() * s, accent, 0.11f, 7);
            signature(level, pos, d, w, r, dim, now);
            if (w.dust() > 0) dustBurst(level, groundBelow(level, pos), w.dust() * s, dust, w.direction(), d);
            if (w.debris() > 0 && nearGround(level, pos)) debris(level, pos, q(Math.round(8 * w.debris() * s)), 0.3 * w.debris() * s);
        }

        // The camera's share: shake fading with distance, the fighters' FOV kick, the one hit.
        if (w.shake() > 0) distanceShake(pos, w.shakeRadius() * Math.max(1f, s), w.shake() * s);
        if (w.fov() > 0 && (mine || isAttackerClose(mc, pos))) ScreenEffects.fovPunch(w.fov());
        if (w.victim() > 0) victimFeedback(p, w.victim());

        // The follow-through: a trailing ring and the dust settling, a few ticks on.
        if (w.delay() > 0) {
            Vec3 g = groundBelow(level, pos);
            float r2 = r * 1.5f;
            later(now + w.delay(), () -> {
                long t = level.getGameTime();
                Flashes.ground(g.add(0, 0.05, 0), 0.5f * r, r2, energy, 0.35f * dim, life + 3, t, w.thickness());
                if (lod > 0 && w.dust() > 0) dustBurst(level, g, w.dust() * 0.5f * s, dust, ImpactWave.Direction.RADIAL, d);
            });
        }
    }

    private static final float[] Flashes_WHITE = {1f, 1f, 1f};

    private static void rings(Vec3 pos, float r, int life, Profile w, float[] energy, float dim, long now) {
        for (int i = 0; i < w.rings(); i++) {
            float f = 1 + i * 0.35f;
            Flashes.ring(pos, 0.15f * r, r * f, energy, (0.8f - i * 0.22f) * dim, life + i * 2, now + i * 2L, w.thickness() * (1 - i * 0.25f));
        }
    }

    private static boolean nearGround(ClientLevel level, Vec3 pos) {
        return pos.y - groundBelow(level, pos).y < 1.8;
    }

    private static void ground(ClientLevel level, Vec3 pos, float r, int life, Profile w, float[] energy, float dim, long now, boolean always) {
        if (w.ground() == ImpactWave.Ground.NEVER) return;
        if (w.ground() == ImpactWave.Ground.AUTO && !always && !nearGround(level, pos)) return;
        Vec3 g = groundBelow(level, pos).add(0, 0.05, 0);
        for (int i = 0; i < w.rings(); i++) {
            Flashes.ground(g, 0.3f * r, r * (1.3f + i * 0.4f), i == 0 ? energy : w.theme().dust(), (0.7f - i * 0.2f) * dim, life + 2 + i * 2, now + i * 2L,
                    w.thickness() * 1.4f);
        }
    }

    /** Dust rolling out along the floor; driven one way when the blow was. */
    private static void dustBurst(ClientLevel level, Vec3 g, float amount, float[] dust, ImpactWave.Direction dirMode, Vec3 d) {
        int n = q(Math.round(12 * amount));
        Vec3 push = dirMode == ImpactWave.Direction.ALONG ? new Vec3(d.x, 0, d.z).normalize().scale(0.12) : Vec3.ZERO;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + RNG.nextDouble() * 0.4;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(0.1 + 0.08 * amount).add(push);
            add(level, g.add(out.scale(2)).add(0, 0.15, 0), out.add(0, 0.02 + RNG.nextDouble() * 0.03, 0), Sprite.SMOKE, dust, 0.55f, 0.3f * amount + 0.2f,
                    0.9f * amount + 0.3f, 16 + RNG.nextInt(10));
        }
    }

    private static void signature(ClientLevel level, Vec3 pos, Vec3 d, Profile w, float r, float dim, long now) {
        float[] accent = w.theme().accent(), core = w.theme().core();
        switch (w.signature()) {
            case NONE -> {}
            case SPEEDLINES -> {
                Vec3 side = d.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
                side = side.normalize();
                Vec3 upv = side.cross(d).normalize();
                for (int i = 0; i < 6; i++) {
                    Vec3 off = side.scale(gauss(0.4 * r)).add(upv.scale(gauss(0.35 * r)));
                    Vec3 a = pos.add(off).subtract(d.scale(0.3)), b = a.add(d.scale(w.lift() * (0.5 + RNG.nextDouble() * 0.5)));
                    Flashes.beam(a, b, 0.035f, i % 2 == 0 ? core : accent, 0.75f * dim, 4 + RNG.nextInt(3), now + i % 2);
                }
            }
            case BOLTS -> {
                for (int i = 0; i < 5; i++) {
                    Vec3 to = pos.add(randomUnit().add(d.scale(0.4)).normalize().scale(r * (0.6 + RNG.nextDouble() * 0.6)));
                    Flashes.bolt(pos, to, 0.05f * r, accent, 0.9f * dim, 5 + RNG.nextInt(3), now);
                }
            }
            case CRESCENT -> {
                crescent(pos, d, r * 0.9f, 0.07f, core, 0.9f * dim, 5, now);
                crescent(pos, d, r * 0.8f, 0.04f, accent, 0.7f * dim, 6, now + 1);
            }
            case SPIKES -> impactStar(pos, Vec3.ZERO, 12, 1.4f * r, 0.06f, accent, now);
            case SPIRAL -> {
                Flashes.swirl(pos, d, r * 0.9f, 0.1f, 2.4f, -0.9f, 0.08f, accent, 0.8f * dim, 6, now);
                Flashes.swirl(pos, d, r * 0.9f, 0.1f, 2.4f, 0.9f, 0.08f, core, 0.6f * dim, 6, now + 1);
            }
            case SMOKE -> {
                float[] smoke = w.theme().dust();
                for (int i = 0; i < 4; i++) {
                    Vec3 at = pos.add(gauss(0.3 * r), 0.2 * r + RNG.nextDouble() * 0.4 * r, gauss(0.3 * r));
                    Flashes.ink(at, 0.4f * r, (0.9f + RNG.nextFloat() * 0.4f) * r, smoke, 0.6f, 20 + RNG.nextInt(10), now + i);
                }
            }
            case DISCHARGE -> {
                for (int i = 0; i < 4; i++) {
                    Vec3 to = pos.add(randomUnit().add(d.scale(0.5)).normalize().scale(r * (0.5 + RNG.nextDouble() * 0.5)));
                    Flashes.bolt(pos, to, 0.04f * r, accent, 0.9f * dim, 4 + RNG.nextInt(3), now);
                }
                burst(level, pos, q(6), 0.08, Sprite.SMOKE, w.theme().dust(), 0.35f, 12);
            }
            case FLARE -> {
                impactStar(pos, d, 6, 1.2f * r, 0.05f, accent, now);
                burst(level, pos, q(8), 0.3 * r, Sprite.STAR, core, 0.14f, 9);
            }
            case SLASH -> {
                Vec3 side = d.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
                side = side.normalize();
                Vec3 upv = side.cross(d).normalize();
                for (int i = 0; i < 2; i++) {
                    double tilt = (RNG.nextDouble() - 0.5) * 1.2 + i * 1.1;
                    Vec3 axis = side.scale(Math.cos(tilt)).add(upv.scale(Math.sin(tilt))).scale(r);
                    Flashes.beam(pos.subtract(axis), pos.add(axis), 0.09f * r, core, dim, 5, now);
                    Flashes.darkBeam(pos.subtract(axis), pos.add(axis), 0.045f * r, INK, 0.95f, 6, now);
                }
            }
        }
    }

    /** A crescent across {@code dir}: the curve a blade or a shin leaves in the air. */
    static void crescent(Vec3 center, Vec3 dir, float radius, float width, float[] c, float alpha, int life, long now) {
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 upv = side.cross(dir).normalize();
        float tilt = RNG.nextFloat() - 0.5f;
        Vec3 axis = side.scale(Math.cos(tilt)).add(upv.scale(Math.sin(tilt)));
        Vec3 bend = upv.scale(Math.cos(tilt)).subtract(side.scale(Math.sin(tilt)));
        Vec3 prev = null;
        for (int i = 0; i <= 8; i++) {
            double t = (i / 8.0 - 0.5) * 2.4;
            Vec3 at = center.add(axis.scale(Math.sin(t) * radius)).add(bend.scale((Math.cos(t) - 0.7) * radius * 0.35));
            if (prev != null) Flashes.beam(prev, at, width * (float) Math.max(0.2, Math.cos(t * 0.8)), c, alpha, life, now);
            prev = at;
        }
    }

    static void later(long at, Runnable run) {
        LATER.add(new Later(at, run));
    }

    /** Every client tick: the follow-throughs whose time has come. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            LATER.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (int i = LATER.size() - 1; i >= 0; i--) {
            Later l = LATER.get(i);
            if (now >= l.at) {
                LATER.remove(i);
                l.run.run();
            }
        }
        if (LATER.size() > 128) LATER.subList(0, LATER.size() - 128).clear();
    }

    static double gaussD(double s) {
        return RNG.nextGaussian() * s;
    }

    static float clamp01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }
}
