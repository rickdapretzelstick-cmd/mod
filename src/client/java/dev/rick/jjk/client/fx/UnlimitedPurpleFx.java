package dev.rick.jjk.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.client.render.Glow;
import dev.rick.jjk.client.render.PurpleMass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Unlimited Purple, after the JJS GIF: Max Red tears into the Max Blue orb left lingering after a kill.
 * <ul>
 *   <li>The collision: a black-and-white impact frame, a flare of magenta light streaking across the view, and the world
 *       lit magenta.</li>
 *   <li>The fuse (3 s): the orb's dark-blue ink turns magenta around a white-hot core that swells and pulses, magenta
 *       lightning crackles out across the whole blast radius, and a pink dome marks what it will erase.</li>
 *   <li>Its last moments: a dark shell collapses in from the edge of the dome onto the core.</li>
 *   <li>The detonation: a white-out, a sphere of white light filling the radius, shockwaves along the ground, lightning
 *       thrown outward, then pink sparkles drifting over the crater.</li>
 * </ul>
 * The server sends the start (with the fuse and radius) and the detonation; everything in between runs here.
 */
public final class UnlimitedPurpleFx {
    private static final float[] BLUE_INK = {0.02f, 0.07f, 0.42f}, BLUE_HOT = {0.4f, 0.72f, 1f};
    private static final float[] PURPLE_INK = PurpleMass.INK, MAGENTA = PurpleMass.HOT;

    private record Nuke(Vec3 center, double ground, float radius, int fuse, long start, long seed) {}

    private record Aftermath(Vec3 center, double ground, float radius, long start) {}

    private static final List<Nuke> ACTIVE = new ArrayList<>();
    private static final List<Aftermath> AFTER = new ArrayList<>();
    /** Its own clock, counted in client ticks: game time can jump when the server catches the client up. */
    private static long clock;

    private UnlimitedPurpleFx() {}

    /** The orb has been shot: the collision, and the mass it becomes for {@code fuse} ticks. */
    public static void start(ClientLevel level, Vec3 center, float radius, int fuse, long now) {
        Minecraft mc = Minecraft.getInstance();
        double ground = ClientFx.groundBelow(level, center).y;
        ACTIVE.add(new Nuke(center, ground, Math.max(4f, radius), Math.max(10, fuse), clock, ClientFx.RNG.nextLong()));
        // Red striking blue: a white crack of light, the blue torn open, a shock racing out.
        Flashes.flash(center, 2f, 9f, ClientFx.WHITE, 1f, 8, now);
        Flashes.ring(center, 0.5f, radius * 0.7f, MAGENTA, 0.9f, 12, now);
        Flashes.ring(center, 0.5f, radius * 0.45f, ClientFx.WHITE, 0.8f, 9, now + 1);
        for (int i = 0; i < 6; i++) {
            Vec3 d = ClientFx.randomUnit();
            Flashes.bolt(center, center.add(d.scale(radius * (0.4 + ClientFx.RNG.nextDouble() * 0.4))), 0.45f, i % 2 == 0 ? ClientFx.ORANGE : MAGENTA,
                    1f, 6, now + i / 2);
        }
        if (ClientFx.lod > 0) {
            ClientFx.sparks(level, center, Vec3.ZERO, ClientFx.q(40), 1.2, ClientFx.PURPLE_LIGHT, 0.24f, 12);
            ClientFx.sparks(level, center, Vec3.ZERO, ClientFx.q(16), 0.9, ClientFx.ORANGE, 0.2f, 10);
        }
        if (mc.player != null) {
            double d = mc.player.getEyePosition().distanceTo(center);
            if (d < radius * 2.2) {
                ScreenEffects.impact(6, 3);
                ScreenEffects.fovPunch(-0.1f);
            }
            ClientFx.distanceShake(center, radius * 4, 0.7f);
        }
    }

    /** The mass erases everything in its radius. */
    public static void detonate(ClientLevel level, Vec3 center, float radius, long now) {
        Minecraft mc = Minecraft.getInstance();
        ACTIVE.removeIf(n -> n.center.distanceTo(center) < 3);
        float r = Math.max(4f, radius);
        Vec3 ground = ClientFx.groundBelow(level, center);
        Flashes.flash(center, r * 0.4f, r * 1.5f, ClientFx.WHITE, 1f, 20, now);
        Flashes.lens(center, r * 0.15f, r * 1.2f, ClientFx.WHITE, 1f, 12, now);
        Flashes.lens(center, r * 0.4f, r * 1.5f, ClientFx.PURPLE_LIGHT, 0.9f, 22, now + 3);
        Flashes.flash(center, r * 1.2f, r * 0.5f, MAGENTA, 0.8f, 40, now + 6);
        for (int i = 0; i < 3; i++) {
            Flashes.ground(ground, 1f, r * (1.1f + 0.35f * i), i == 0 ? ClientFx.WHITE : MAGENTA, 0.95f - 0.2f * i, 18 + 5 * i, now + 2 + 3L * i);
        }
        Flashes.beam(ground, ground.add(0, r * 2.6, 0), r * 0.28f, ClientFx.PURPLE_LIGHT, 0.9f, 30, now + 1);
        for (int i = 0; i < 14; i++) {
            Vec3 d = ClientFx.randomUnit();
            Flashes.bolt(center, center.add(d.x * r * 1.2, Math.abs(d.y) * r * 0.9, d.z * r * 1.2), 0.6f, i % 3 == 0 ? ClientFx.WHITE : MAGENTA, 1f,
                    10, now + i % 5);
        }
        if (ClientFx.lod > 0) {
            ClientFx.sparks(level, center, Vec3.ZERO, ClientFx.q(90), 1.9, ClientFx.PURPLE_LIGHT, 0.3f, 16);
            ClientFx.burst(level, center, ClientFx.q(40), 1.0, Sprite.GLOW, MAGENTA, 0.6f, 24);
            ClientFx.debris(level, center, ClientFx.q(70), 1.2);
        }
        AFTER.add(new Aftermath(center, ground.y, r, clock));
        if (mc.player != null) {
            double d = mc.player.getEyePosition().distanceTo(center);
            // Swallowed by the light: a white-out that slowly gives the world back.
            if (d < r * 2.2) ScreenEffects.flash(0xF8FFFFFF, 36);
            else if (d < r * 6) ScreenEffects.flash(0x90FFFFFF, 16);
            ClientFx.distanceShake(center, r * 6, 1.2f);
        }
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = ++clock;
        for (Iterator<Nuke> it = ACTIVE.iterator(); it.hasNext(); ) {
            Nuke n = it.next();
            long t = now - n.start;
            if (t > n.fuse + 20) {
                it.remove();
                continue;
            }
            float end = Mth.clamp((t - (n.fuse - 12)) / 12f, 0, 1);
            float core = core(n, t, end);
            // Sparkles in the core, magenta energy dragged in, the ground coming apart under it.
            for (int i = 0; i < ClientFx.q(4); i++) {
                Vec3 at = n.center.add(ClientFx.randomUnit().scale(core * (0.4 + ClientFx.RNG.nextDouble())));
                ClientFx.add(level, at, ClientFx.randomUnit().scale(0.02), Sprite.STAR, i % 2 == 0 ? ClientFx.WHITE : ClientFx.PURPLE_LIGHT, 0.95f,
                        0.12f + ClientFx.RNG.nextFloat() * 0.15f, 0.02f, 6 + ClientFx.RNG.nextInt(5));
            }
            for (int i = 0; i < ClientFx.q(end > 0 ? 7 : 3); i++) {
                Vec3 from = n.center.add(ClientFx.randomUnit().scale(n.radius * (0.4 + ClientFx.RNG.nextDouble() * 0.5)));
                ClientFx.add(level, from, Vec3.ZERO, Sprite.GLOW, i % 3 == 0 ? ClientFx.WHITE : MAGENTA, 0.8f, 0.3f, 0.05f, 18)
                        .attract(n.center, 0.05 + 0.1 * end).fadeIn();
            }
            if (t % 3 == 0 && ClientFx.lod > 0) {
                double a = ClientFx.RNG.nextDouble() * Mth.TWO_PI, rr = ClientFx.RNG.nextDouble() * n.radius * 0.8;
                ClientFx.debris(level, new Vec3(n.center.x + Math.cos(a) * rr, n.ground + 0.2, n.center.z + Math.sin(a) * rr), ClientFx.q(4), 0.25);
            }
            if (t % 12 == 0) ClientFx.sound("infinity_hold", n.center, 2.5f, 0.4f + 0.3f * end);
            // Everyone near it sees the world lit magenta, darkening as the shell closes in.
            if (mc.player != null) {
                double d = mc.player.getEyePosition().distanceTo(n.center);
                if (d < n.radius * 1.8) {
                    float k = (float) (1 - d / (n.radius * 1.8));
                    int alpha = Math.round((0.14f + 0.2f * k) * 255);
                    ScreenEffects.tint(alpha << 24 | (end > 0.3f ? 0x3A0038 : 0xE020C8), 3);
                    if (end > 0) ScreenEffects.shake(0.15f + 0.4f * end, 4);
                }
            }
        }
        for (Iterator<Aftermath> it = AFTER.iterator(); it.hasNext(); ) {
            Aftermath a = it.next();
            long t = now - a.start;
            if (t > 90) {
                it.remove();
                continue;
            }
            // Pink sparkles drifting up out of the crater.
            for (int i = 0; i < ClientFx.q(t < 30 ? 5 : 2); i++) {
                double ang = ClientFx.RNG.nextDouble() * Mth.TWO_PI, rr = Math.sqrt(ClientFx.RNG.nextDouble()) * a.radius * 0.85;
                Vec3 at = new Vec3(a.center.x + Math.cos(ang) * rr, a.ground + 0.3 + ClientFx.RNG.nextDouble() * 3, a.center.z + Math.sin(ang) * rr);
                ClientFx.add(level, at, new Vec3(0, 0.02 + ClientFx.RNG.nextDouble() * 0.03, 0), Sprite.STAR,
                        i % 3 == 0 ? ClientFx.WHITE : ClientFx.PURPLE_LIGHT, 0.9f, 0.18f + ClientFx.RNG.nextFloat() * 0.2f, 0.02f, 24 + ClientFx.RNG.nextInt(20))
                        .fadeIn();
            }
        }
    }

    private static float core(Nuke n, float t, float end) {
        float grow = Mth.clamp(t / 12f, 0, 1);
        grow = 1 - (1 - grow) * (1 - grow);
        return (1.1f + 1.0f * grow) * (1 + 0.06f * Mth.sin(t * 1.7f)) * (1 + 0.45f * end);
    }

    public static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, float partial) {
        for (Nuke n : ACTIVE) {
            // Held fully collapsed past the fuse until the detonation arrives from the server.
            float t = Math.min(clock - n.start + partial, n.fuse);
            if (t < 0) continue;
            float R = n.radius;
            float end = Mth.clamp((t - (n.fuse - 12)) / 12f, 0, 1);
            float purple = Mth.clamp(t / 8f, 0, 1);
            float[] ink = mix(BLUE_INK, PURPLE_INK, purple), hot = mix(BLUE_HOT, MAGENTA, purple);
            float core = core(n, t, end);
            ps.pushPose();
            ps.translate(n.center.x - cam.x, n.center.y - cam.y, n.center.z - cam.z);
            Vector3f toCam = new Vector3f((float) (cam.x - n.center.x), (float) (cam.y - n.center.y), (float) (cam.z - n.center.z));
            // The mass: dark ink churning round a white-hot, sparkling core (the Max Blue orb's ink, turning magenta).
            PurpleMass.draw(c, ps, camRot, toCam, core, t, n.seed, ink, hot, 0, 0);
            // The flare of the collision, streaking across the view, and again as it goes critical.
            float flare = Math.max(1 - t / 26f, end * 0.8f);
            if (flare > 0.01f) flare(c, ps, camRot, R * (0.6f + 0.5f * flare), core * 0.4f, hot, 0.75f * flare, t);
            // Lightning crackling out across the whole radius, more and more of it.
            int bolts = 2 + (int) (t / 9f) + (end > 0 ? 5 : 0);
            java.util.Random rnd = new java.util.Random(n.seed ^ ((long) (t / 2f) * 7919L));
            float down = (float) (n.ground - n.center.y);
            for (int i = 0; i < bolts; i++) {
                double a = rnd.nextDouble() * Mth.TWO_PI, reach = R * (0.35 + rnd.nextDouble() * 0.6);
                float y = Math.max(down, (float) ((rnd.nextDouble() - 0.35) * R * 0.6));
                Vector3f dir = new Vector3f((float) Math.cos(a), 0, (float) Math.sin(a));
                Vector3f from = new Vector3f(dir).mul(core * 1.2f);
                Vector3f to = new Vector3f(dir).mul((float) reach).add(0, y, 0);
                float[] col = i % 4 == 0 ? ClientFx.WHITE : hot;
                Glow.bolt(c, ps, from, to, 0.12f + 0.08f * rnd.nextFloat(), col[0], col[1], col[2], 0.95f, rnd.nextLong());
            }
            // What it will erase: a pink dome over its radius.
            float dome = (0.14f + 0.16f * t / n.fuse) * (1 - end);
            Glow.shell(c, ps, R, hot[0], hot[1] * 0.6f, hot[2], dome, toCam);
            ps.pushPose();
            ps.translate(0, down + 0.06f, 0);
            Glow.ring(c, ps, R * (0.97f + 0.02f * Mth.sin(t * 0.9f)), 1.3f, hot[0], hot[1], hot[2], 0.55f * (1 - end));
            Glow.ring(c, ps, R * 0.97f, 0.3f, 1f, 1f, 1f, 0.6f * (1 - end));
            ps.popPose();
            // At the end, a dark shell closes in from the edge of the dome onto the core.
            if (end > 0) {
                float rr = Mth.lerp(end * end, R, core * 1.6f);
                Glow.inkSphere(c, ps, rr, 0.08f, 0f, 0.1f, 0.6f + 0.35f * end, toCam, true);
                Glow.shell(c, ps, rr * 1.01f, hot[0], hot[1] * 0.5f, hot[2], 0.45f, toCam);
            }
            ps.popPose();
        }
    }

    /** Streaks of light through the core across the view (a lens flare), a long pair and a short, turning pair. */
    private static void flare(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, float length, float width, float[] col, float a, float t) {
        ps.pushPose();
        ps.rotate(camRot);
        for (int k = 0; k < 3; k++) {
            ps.pushPose();
            ps.rotate(Axis.ZP.rotationDegrees(k == 0 ? 8 : k == 1 ? 8 + 90 + t * 0.6f : 8 + 35 - t * 0.9f));
            float len = length * (k == 0 ? 1f : k == 1 ? 0.45f : 0.3f);
            Vector3f[] pts = {new Vector3f(-len, 0, 0), new Vector3f(0, 0, 0), new Vector3f(len, 0, 0)};
            Glow.ribbon(c, ps, pts, new float[] {width * 0.15f, width, width * 0.15f}, new float[] {0f, a, 0f}, col[0], col[1], col[2]);
            Glow.ribbon(c, ps, pts, new float[] {width * 0.05f, width * 0.35f, width * 0.05f}, new float[] {0f, a, 0f}, 1f, 1f, 1f);
            ps.popPose();
        }
        ps.popPose();
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    public static void clear() {
        ACTIVE.clear();
        AFTER.clear();
    }
}
