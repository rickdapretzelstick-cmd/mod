package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.core.net.FxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The impact kit: the feedback that makes a hit, a shove or a slam land, built for the interactive moments.
 * <ul>
 *   <li>{@code impact_frame}: an anime impact frame for the player it is for ({@code dir.x}: the style).</li>
 *   <li>{@code impact_slowmo}: the slowed-time look for the player it is for ({@code s} x 100 ms).</li>
 *   <li>{@code impact_crack}: the ground splits where something hit it: jagged dark fissures branching out from the
 *   point (biased along {@code dir}), lit along their edges for a moment, with grit thrown up; it fades over seconds.</li>
 *   <li>{@code impact_dust}: a ring of dust rolling out along the ground from a heavy landing or a stamp.</li>
 *   <li>{@code impact_shove}: a short, directional burst of dust and air where two bodies push against each other.</li>
 * </ul>
 */
final class ImpactFx {
    private static final RandomSource RNG = RandomSource.create();
    private static final float[] CRACK = {0.05f, 0.03f, 0.06f}, CRACK_GLOW = {1f, 0.55f, 0.25f}, DUST = {0.62f, 0.57f, 0.5f},
            DUST_DARK = {0.35f, 0.32f, 0.3f}, AIR = {0.95f, 0.95f, 1f};

    private ImpactFx() {}

    static void play(FxPayload p, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = ClientFx.lod > 0;
        switch (p.id()) {
            case "impact_frame" -> {
                if (!mine) return;
                ImpactFrames.Style[] styles = ImpactFrames.Style.values();
                ImpactFrames.play(styles[Math.floorMod((int) Math.round(dir.x), styles.length)], s);
            }
            case "impact_finisher_cam" -> {
                // A finisher's cinematic: dir.x the curse, dir.y its length (ms).
                if (mine) dev.rick.jjk.client.finish.FinisherCamera.start((int) Math.round(dir.x), dir.y);
            }
            case "impact_slowmo" -> {
                if (mine) ImpactFrames.slowMotion(Math.round(s * 100));
            }
            case "impact_crack" -> crack(level, pos, dir, s, now, drawn);
            case "impact_dust" -> {
                Flashes.ground(pos.add(0, 0.05, 0), 0.4f * s, 3.2f * s, DUST, 0.45f, 12, now);
                Flashes.ground(pos.add(0, 0.06, 0), 0.2f * s, 1.8f * s, AIR, 0.35f, 7, now);
                if (!drawn) return;
                int n = ClientFx.q(Math.round(18 * s));
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n + RNG.nextDouble() * 0.3;
                    Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                    ClientFx.add(level, pos.add(out.scale(0.4 * s)).add(0, 0.1, 0), out.scale(0.14 * s).add(0, 0.015, 0), Sprite.SMOKE,
                            i % 3 == 0 ? DUST_DARK : DUST, 0.55f, 0.35f * s, 0.9f * s, 18 + RNG.nextInt(10));
                }
                ClientFx.debris(level, pos, ClientFx.q(Math.round(6 * s)), 0.25 * s);
            }
            case "impact_shove" -> {
                Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
                Flashes.ripple(pos, d, 0.2f * s, 1.2f * s, AIR, 0.4f, 5, now);
                if (!drawn) return;
                int n = ClientFx.q(Math.round(8 * s));
                Vec3 side = new Vec3(-d.z, 0, d.x);
                for (int i = 0; i < n; i++) {
                    double t = (RNG.nextDouble() - 0.5) * 2;
                    Vec3 v = d.scale(0.12 + RNG.nextDouble() * 0.1).add(side.scale(t * 0.12)).add(0, 0.02 + RNG.nextDouble() * 0.04, 0);
                    ClientFx.add(level, pos.add(side.scale(t * 0.3)), v.scale(s), Sprite.SMOKE, DUST, 0.5f, 0.25f * s, 0.6f * s, 12 + RNG.nextInt(6));
                }
            }
            case "impact_bind" -> {
                // Two heavies meet: a flash where they lock, a spray of sparks, and the bind on the player's screen.
                if (mine) dev.rick.jjk.client.finish.BindClient.start((int) Math.round(dir.x), dir.y);
                Flashes.flash(pos, 0.2f, 1.4f, SPARK_HOT, 0.9f, 5, now);
                Flashes.ring(pos, 0.3f, 2.2f, AIR, 0.5f, 7, now);
                sparks(level, pos, 20, 0.35, drawn);
            }
            case "impact_bind_spark" -> {
                // The blows grinding: sparks thrown off the contact (a tap's are brighter), the meter for the bind's HUD.
                if (mine) dev.rick.jjk.client.finish.BindClient.meter((float) dir.x);
                Flashes.flash(pos, 0.1f, 0.5f * s + 0.2f, SPARK_HOT, 0.8f, 3, now);
                sparks(level, pos, Math.round(10 * s), 0.25 * s + 0.1, drawn);
            }
            case "impact_bind_end" -> {
                if (mine) dev.rick.jjk.client.finish.BindClient.end(dir.x > 0.5);
                Flashes.flash(pos, 0.3f, 1.8f, SPARK_HOT, 0.8f, 6, now);
                sparks(level, pos, 26, 0.45, drawn);
            }
            case "impact_glint" -> glint(mc, pos, s, now);
            case "impact_shatter" -> shatter(level, pos, dir, s, now, drawn);
            case "impact_eyecatch" -> {
                // A guardian's phase beat: dir.x the guardian, dir.y the beat's length (ms), dir.z the act.
                if (mine) dev.rick.jjk.client.finish.EyecatchCamera.start((int) Math.round(dir.x), dir.y, (int) Math.round(dir.z));
            }
            case "impact_rescue" -> {
                // A slash straight through the curse that was reaching for someone.
                Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
                Vec3 side = new Vec3(-d.z, 0.35, d.x).normalize();
                Vec3 a = pos.subtract(side.scale(1.8 * s)).subtract(d.scale(0.4)), b = pos.add(side.scale(1.8 * s)).add(d.scale(0.4));
                Flashes.beam(a, b, 0.16f * s, AIR, 0.95f, 5, now);
                Flashes.beam(a, b, 0.06f * s, SPARK_HOT, 1f, 7, now);
                Flashes.darkBeam(a.add(0, -0.08, 0), b.add(0, -0.08, 0), 0.05f * s, CRACK, 0.8f, 9, now);
                Flashes.flash(pos, 0.2f, 1.2f * s, AIR, 0.6f, 4, now);
                sparks(level, pos, 14, 0.3, drawn);
            }
            default -> {}
        }
    }

    private static final float[] SPARK_HOT = {1f, 0.78f, 0.35f}, GLINT = {1f, 0.96f, 0.82f}, SHARD = {0.82f, 0.9f, 1f};

    /** Sparks flung out in every direction from {@code pos}, falling as they cool. */
    private static void sparks(ClientLevel level, Vec3 pos, int n, double speed, boolean drawn) {
        if (!drawn) return;
        n = ClientFx.q(Math.max(1, n));
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RNG.nextGaussian(), RNG.nextGaussian() * 0.6 + 0.4, RNG.nextGaussian()).normalize().scale(speed * (0.5 + RNG.nextDouble()));
            ClientFx.add(level, pos, v, Sprite.SPARK, i % 4 == 0 ? AIR : SPARK_HOT, 0.95f, 0.08f, 0.02f, 6 + RNG.nextInt(8));
        }
    }

    /** A blow's glint: a four-pointed star across the view where it will land, there and gone. */
    private static void glint(Minecraft mc, Vec3 pos, float s, long now) {
        var cam = mc.gameRenderer.mainCamera();
        Vec3 left = new Vec3(cam.leftVector().x(), cam.leftVector().y(), cam.leftVector().z());
        Vec3 up = new Vec3(cam.upVector().x(), cam.upVector().y(), cam.upVector().z());
        float r = 1.5f * s;
        Flashes.beam(pos.add(left.scale(r)), pos.subtract(left.scale(r)), 0.11f * s, GLINT, 1f, 6, now);
        Flashes.beam(pos.add(up.scale(r * 0.8)), pos.subtract(up.scale(r * 0.8)), 0.11f * s, GLINT, 1f, 6, now);
        Flashes.ring(pos, 0.2f, 1.1f * s, GLINT, 0.7f, 6, now);
        Vec3 d1 = left.add(up).normalize().scale(r * 0.35), d2 = left.subtract(up).normalize().scale(r * 0.35);
        Flashes.beam(pos.add(d1), pos.subtract(d1), 0.04f * s, GLINT, 0.8f, 4, now);
        Flashes.beam(pos.add(d2), pos.subtract(d2), 0.04f * s, GLINT, 0.8f, 4, now);
        Flashes.flash(pos, 0.15f, 0.8f * s, GLINT, 1f, 5, now);
    }

    /** A posture shattering: shards of light flung out, a ring, a white flash. */
    private static void shatter(ClientLevel level, Vec3 pos, Vec3 dir, float s, long now, boolean drawn) {
        Flashes.flash(pos, 0.3f, 2.4f * s, AIR, 0.9f, 6, now);
        Flashes.ring(pos, 0.5f, 3.4f * s, SHARD, 0.7f, 9, now);
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Flashes.ripple(pos, d, 0.4f, 2.6f * s, AIR, 0.5f, 8, now);
        if (!drawn) return;
        int n = ClientFx.q(Math.round(34 * s));
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(RNG.nextGaussian(), RNG.nextGaussian() * 0.7 + 0.3, RNG.nextGaussian()).normalize().scale(0.18 + RNG.nextDouble() * 0.32).add(d.scale(0.12));
            ClientFx.add(level, pos, v.scale(s), Sprite.SHARD, i % 3 == 0 ? AIR : SHARD, 0.95f, 0.22f * s, 0.06f, 14 + RNG.nextInt(10));
        }
        sparks(level, pos, Math.round(16 * s), 0.4 * s, true);
    }

    /** Fissures: a few main cracks out from the point, each a jagged chain that forks once or twice. */
    private static void crack(ClientLevel level, Vec3 pos, Vec3 dir, float s, long now, boolean drawn) {
        Vec3 bias = dir.multiply(1, 0, 1);
        double biasAngle = bias.lengthSqr() < 1e-4 ? RNG.nextDouble() * Math.PI * 2 : Math.atan2(bias.z, bias.x);
        int mains = 3 + Math.round(2 * Math.min(2f, s));
        Vec3 ground = pos.add(0, 0.03, 0);
        for (int m = 0; m < mains; m++) {
            double a = biasAngle + (m - (mains - 1) / 2.0) * (Math.PI * 2 / mains) * (bias.lengthSqr() < 1e-4 ? 1 : 0.55) + (RNG.nextDouble() - 0.5) * 0.4;
            fissure(ground, a, (1.2 + RNG.nextDouble() * 1.4) * s, 0.12f * s, 2, now);
        }
        Flashes.flash(ground.add(0, 0.1, 0), 0.2f, 1.1f * s, CRACK_GLOW, 0.7f, 4, now);
        if (drawn) ClientFx.debris(level, pos, ClientFx.q(Math.round(8 * s)), 0.3 * s);
    }

    private static void fissure(Vec3 from, double angle, double length, float width, int forks, long now) {
        Vec3 at = from;
        int steps = Math.max(2, (int) Math.round(length / 0.35));
        double a = angle;
        for (int i = 0; i < steps; i++) {
            a += (RNG.nextDouble() - 0.5) * 0.9;
            double len = length / steps * (0.7 + RNG.nextDouble() * 0.6);
            Vec3 next = at.add(Math.cos(a) * len, 0, Math.sin(a) * len);
            float w = width * (1f - i / (float) steps * 0.7f);
            // The fissure itself stays a while; its hot edge only flashes.
            Flashes.darkBeam(at, next, w, CRACK, 0.85f, 70, now);
            Flashes.beam(at.add(0, 0.01, 0), next.add(0, 0.01, 0), w * 0.45f, CRACK_GLOW, 0.65f, 6 + i, now);
            if (forks > 0 && i == steps / 2 && RNG.nextFloat() < 0.8f) {
                fissure(next, a + (RNG.nextBoolean() ? 0.7 : -0.7), length * 0.45, w * 0.7f, forks - 1, now);
            }
            at = next;
        }
    }
}
