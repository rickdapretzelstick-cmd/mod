package dev.rick.jjk.client.fx;

import dev.rick.jjk.client.particle.EnergyParticle.Sprite;
import dev.rick.jjk.client.render.Flashes;
import dev.rick.jjk.config.JJKConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import static dev.rick.jjk.client.fx.ClientFx.*;

/**
 * Every Black Flash, layered after the JJS Resolute Black Flash finisher (Cursed Partners): the server decides which one
 * plays from the resolved blow ({@code dev.rick.jjk.core.fx.BlackFlash}).
 * <ol>
 *   <li><b>Contact</b>: a white-hot core punched out at the point of impact, inside a crimson bloom.</li>
 *   <li><b>Impact frame</b>: the crimson/black inked frames (close by only), with a held beat of hit-stop.</li>
 *   <li><b>Lightning</b>: branching black bolts with crimson glow edges, thrown out along the blow and re-crackling.</li>
 *   <li><b>Shockwaves</b>: a ripple across the blow and a ring rolling along the ground, a second one a beat later.</li>
 *   <li><b>Streaks</b>: speed lines driven through the target along the blow.</li>
 *   <li><b>Follow-through</b>: smaller arcs snapping on for a few ticks after, then decaying embers (black and crimson
 *   flecks under gravity) and scorch lines on the ground that fade (drawn, never placed: no terrain is touched).</li>
 *   <li><b>Audio and camera</b>: the layered Black Flash sounds; a restrained shake and FOV kick, near it only.</li>
 * </ol>
 * The lethal one escalates every layer: a longer inked sequence (crimson, inverted, crimson, the hard negative, crimson),
 * a brief slowed-time look, the world washed crimson, a bigger tree of bolts, three shockwaves and the ground split.
 *
 * <p>Reduced Flashes keeps the shapes but drops the impact frames, the screen wash and the strobing, and dims every
 * flash; Reduced Shake (and the shake settings) keep the camera calm. Nothing needs shaders.
 */
final class BlackFlashFx {
    static final float[] RED = {1f, 0.06f, 0.1f};
    static final float[] INK = {0.02f, 0.02f, 0.03f};
    static final float[] WHITE_HOT = {1f, 0.96f, 0.92f};

    private BlackFlashFx() {}

    static boolean reducedFlashes() {
        return JJKConfig.get().client.reducedFlashes;
    }

    static void play(String id, Minecraft mc, ClientLevel level, Vec3 pos, Vec3 dir, float s, boolean mine, long now) {
        boolean drawn = lod > 0;
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        double dist = mc.player == null ? 999 : mc.player.position().distanceTo(pos);
        switch (id) {
            case "bf_fizzle" -> {
                // Nothing (or a guard) took it: the flash sparks and dies, no impact frame.
                sound("black_flash_chain", pos, 0.7f, 1.35f);
                Flashes.flash(pos, 0.9f * s, 0.2f, RED, dim(0.8f), 4, now);
                if (drawn) {
                    for (int i = 0; i < 4; i++) bolt(pos, randomAround(d, 0.8), 0.7 * s, 0.035f, 1, now, 4);
                    sparks(level, pos, d, q(8), 0.35 * s, INK, 0.09f, 6);
                }
            }
            case "bf_impact" -> impact(mc, level, pos, d, s, mine, now, dist, false, drawn);
            case "bf_amp" -> amp(mc, level, pos, d, s, mine, now, dist, drawn);
            case "bf_launch" -> launch(mc, level, pos, d, s, mine, now, dist, drawn);
            case "bf_lethal" -> impact(mc, level, pos, d, Math.max(1.4f, s), mine, now, dist, true, drawn);
            default -> {}
        }
    }

    private static void impact(Minecraft mc, ClientLevel level, Vec3 pos, Vec3 d, float s, boolean mine, long now, double dist, boolean lethal, boolean drawn) {
        boolean reduced = reducedFlashes();
        // Audio: the crack, its heavy body, and (lethal) the roar of it.
        sound(lethal ? "black_flash_heavy" : "black_flash", pos, lethal ? 2.2f : 1.5f, lethal ? 0.85f : 1f);
        sound("black_flash_chain", pos, 1f, lethal ? 0.7f : 0.9f);
        if (lethal) sound("kokusen", pos, 1.6f, 0.9f);

        // 1. Contact: the white-hot core, crimson bloom, then the black pressure behind it.
        Flashes.flash(pos, 0.3f * s, 1.3f * s, WHITE_HOT, dim(1f), 3, now);
        Flashes.flash(pos, 2.4f * s, 0.5f, RED, dim(1f), lethal ? 8 : 6, now);
        Flashes.flash(pos, 1.6f * s, 0.3f, INK, 0.95f, lethal ? 10 : 7, now + 1);

        // 2. Impact frame and hit-stop look: close by only (never across the map).
        if (!reduced && (mine || dist < (lethal ? 22 : 9))) {
            ImpactFrames.play(lethal ? ImpactFrames.Style.BLACK_FLASH_STORM : ImpactFrames.Style.BLACK_FLASH, lethal ? 1.0f : Math.min(1.4f, 0.8f + 0.3f * s));
            if (lethal) {
                ImpactFrames.slowMotion(1500);
                ScreenEffects.tint(0x70B00010, 34);
            }
        }

        // 3. Lightning: a branching tree thrown along the blow, black cores with crimson edges.
        int trunks = Math.round((lethal ? 9 : 5) * Math.min(2f, s));
        for (int i = 0; i < trunks; i++) {
            Vec3 a = randomAround(d, lethal ? 1.1 : 0.85);
            bolt(pos, a, (lethal ? 3.2 : 2.0) * s, 0.09f * s, lethal ? 3 : 2, now, lethal ? 9 : 6);
        }
        // Re-crackling arcs right on the contact for a few ticks.
        for (int k = 0; k < (lethal ? 4 : 2); k++) {
            Vec3 a = pos.add(randomUnit().scale(0.6 * s));
            bolt(a, randomAround(d, 1.2), 1.1 * s, 0.04f * s, 0, now + k, 5 + k * 2);
        }

        // 4. Shockwaves: across the blow, then along the ground (a second, wider one a beat later).
        Flashes.ripple(pos, d, 0.4f * s, 3.2f * s, RED, dim(0.75f), 7, now);
        Vec3 ground = groundBelow(level, pos).add(0, 0.06, 0);
        int waves = lethal ? 3 : 2;
        for (int w = 0; w < waves; w++) {
            Flashes.ground(ground, 0.5f, (2.6f + w * 1.6f) * s, w == 0 ? RED : INK, dim(0.7f - w * 0.15f), 10 + w * 3, now + w * 2L);
        }

        // 5. Streaks: speed lines driven through along the blow.
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 upv = side.cross(d).normalize();
        int streaks = lethal ? 12 : 7;
        for (int i = 0; i < streaks; i++) {
            Vec3 off = side.scale(gauss(0.7 * s)).add(upv.scale(gauss(0.6 * s)));
            Vec3 a = pos.add(off).subtract(d.scale(0.5)), b = a.add(d.scale((2.5 + RNG.nextDouble() * 2.5) * s));
            if (i % 3 == 0) Flashes.darkBeam(a, b, 0.05f, INK, 0.85f, 5 + RNG.nextInt(3), now + i % 2);
            else if (i % 3 == 1) Flashes.darkBeam(a, b, 0.04f, RED, dim(0.85f), 4 + RNG.nextInt(3), now + i % 2);
            else Flashes.beam(a, b, 0.03f, WHITE_HOT, dim(0.7f), 3 + RNG.nextInt(3), now + i % 2);
        }

        // 6. Follow-through: smaller arcs snap on after the hit, then embers and scorch lines decay.
        for (int k = 1; k <= (lethal ? 5 : 3); k++) {
            Vec3 at = pos.add(d.scale(0.4 * k)).add(randomUnit().scale(0.5));
            bolt(at, randomAround(d, 1.0), (0.9 + 0.2 * k) * s, 0.05f * s, 1, now + 2L * k, 5);
        }
        if (drawn) {
            sparks(level, pos, d, q(lethal ? 40 : 18), 0.75 * s, INK, 0.14f, 12);
            sparks(level, pos, d, q(lethal ? 30 : 14), 0.65 * s, RED, 0.12f, 10);
            for (int i = 0; i < q(lethal ? 26 : 10); i++) {
                Vec3 v = randomUnit().scale(0.25).add(d.scale(0.3)).add(0, 0.25, 0);
                add(level, pos, v, i % 2 == 0 ? Sprite.SHARD : Sprite.SPARK, i % 3 == 0 ? INK : RED, 0.95f, 0.1f + RNG.nextFloat() * 0.08f, 0.02f,
                        26 + RNG.nextInt(20)).gravity(0.7f).physics();
            }
        }
        // Scorch: dark lines across the ground that fade (drawn only; no blocks are changed).
        int scorch = lethal ? 7 : 3;
        for (int i = 0; i < scorch; i++) {
            double ang = Math.atan2(d.z, d.x) + gauss(lethal ? 1.2 : 0.5);
            Vec3 a = ground.add(gauss(0.3), 0, gauss(0.3));
            Vec3 b = a.add(Math.cos(ang) * (1.2 + RNG.nextDouble() * 1.6) * s, 0, Math.sin(ang) * (1.2 + RNG.nextDouble() * 1.6) * s);
            Flashes.darkBeam(a, b, 0.045f * s, INK, 0.5f, lethal ? 60 : 30, now);
            Flashes.darkBeam(a.add(0, 0.01, 0), b.add(0, 0.01, 0), 0.025f * s, RED, dim(0.7f), 10, now);
        }

        // 7. Camera: a restrained impulse, near it only (and none under the shake settings).
        if (dist < (lethal ? 30 : 18)) {
            float fall = (float) Math.max(0.25, 1 - dist / (lethal ? 30 : 18));
            ScreenEffects.shake((lethal ? 0.9f : 0.5f) * fall, lethal ? 14 : 8);
            if (mine) ScreenEffects.fovPunch(lethal ? 0.12f : 0.05f);
        }
        if (reduced && mine) ScreenEffects.flash(0x30400008, 8);
    }

    /**
     * The lethal Black Flash's build-up, every other tick while the body is held in the contact: "the user amps the power
     * up, creating intense sparks" (JJS). Crackling black and crimson arcs round the contact, spark showers thrown out,
     * motes of cursed energy drawn in, a crimson pulse now and then, the world kept washed red; {@code s} grows with it.
     */
    private static void amp(Minecraft mc, ClientLevel level, Vec3 pos, Vec3 d, float s, boolean mine, long now, double dist, boolean drawn) {
        float k = Math.min(2.6f, s);
        if (RNG.nextInt(3) == 0) sound("black_flash_chain", pos, 0.5f + 0.2f * k, 0.8f + RNG.nextFloat() * 0.6f);
        if (RNG.nextInt(5) == 0) sound("resolute_bf_windup", pos, 0.35f, 1.3f + RNG.nextFloat() * 0.3f);
        // The arcs: short black bolts with crimson edges whipping round the contact, re-forking every tick.
        for (int i = 0; i < 2 + Math.round(2 * k); i++) {
            Vec3 a = pos.add(randomUnit().scale(0.2 + 0.4 * RNG.nextDouble()));
            bolt(a, randomAround(randomUnit(), 0.4), (0.9 + RNG.nextDouble() * 1.2) * k, 0.045f * k, 1, now, 3 + RNG.nextInt(3));
        }
        Flashes.bolt(pos.add(randomUnit().scale(0.3)), pos.add(randomUnit().scale(1.6 * k)), 0.05f, RED, dim(0.9f), 3, now);
        // The heart of it: a white-hot core flickering inside a crimson bloom, black pressure round it.
        Flashes.flash(pos, 0.25f * k, 0.6f * k, WHITE_HOT, dim(0.8f + 0.2f * RNG.nextFloat()), 3, now);
        Flashes.flash(pos, 1.2f * k, 0.4f, RED, dim(0.55f), 3, now);
        if (RNG.nextInt(3) == 0) Flashes.ring(pos, 0.3f, (1.6f + RNG.nextFloat()) * k, RED, dim(0.7f), 5, now);
        if (RNG.nextInt(4) == 0) Flashes.ripple(pos, randomUnit(), 0.2f, 1.8f * k, INK, 0.6f, 4, now);
        if (drawn) {
            // Spark showers: white-hot and crimson, thrown out and falling.
            for (int i = 0; i < q(Math.round(10 * k)); i++) {
                Vec3 v = randomUnit().scale(0.25 + RNG.nextDouble() * 0.35 * k);
                add(level, pos, v, Sprite.SPARK, i % 3 == 0 ? WHITE_HOT : i % 3 == 1 ? RED : INK, 1f, 0.07f + RNG.nextFloat() * 0.06f, 0.01f, 8 + RNG.nextInt(8))
                        .gravity(0.5f).friction(0.9f);
            }
            // Cursed energy drawn into the blow from all round.
            for (int i = 0; i < q(Math.round(4 * k)); i++) {
                Vec3 from = pos.add(randomUnit().scale(2.2 + RNG.nextDouble() * 1.5));
                add(level, from, Vec3.ZERO, Sprite.GLOW, i % 2 == 0 ? RED : INK, 0.8f, 0.18f, 0.04f, 10).attract(pos, 0.07);
            }
        }
        // The world held red, the view straining in toward it.
        if (dist < 26) {
            ScreenEffects.tint(0x58A00010, 6);
            ScreenEffects.shake(0.12f + 0.12f * k, 4);
            if (mine) ScreenEffects.fovPunch(-0.01f * k);
        }
    }

    /**
     * The lethal Black Flash's launch: the frame burnt white with the bodies black on it, then the shockwave rolling out
     * along the ground, the body thrown far along the blow trailing crimson streaks, debris and black and crimson shards
     * flung out, the bolts at their biggest, a held beat of slowed time, and embers and scorch fading after.
     */
    private static void launch(Minecraft mc, ClientLevel level, Vec3 pos, Vec3 d, float s, boolean mine, long now, double dist, boolean drawn) {
        boolean reduced = reducedFlashes();
        sound("black_flash_heavy", pos, 2.4f, 0.75f);
        sound("kokusen", pos, 2f, 0.85f);
        sound("resolute_bf_finish", pos, 1.6f, 1f);
        if (!reduced && (mine || dist < 24)) {
            ImpactFrames.play(ImpactFrames.Style.BLACK_FLASH_LAUNCH, 1.2f);
            ImpactFrames.slowMotion(900);
            ScreenEffects.tint(0x80B00010, 26);
        }
        if (reduced && mine) ScreenEffects.flash(0x30400008, 10);
        // The blast at the contact.
        Flashes.flash(pos, 0.5f, 4.5f, WHITE_HOT, dim(1f), 5, now);
        Flashes.flash(pos, 5.5f, 0.6f, RED, dim(0.9f), 12, now);
        Flashes.flash(pos, 3.2f, 0.4f, INK, 0.95f, 14, now + 1);
        // Shockwaves: three rolling along the ground, one across the blow, one high.
        Vec3 ground = groundBelow(level, pos).add(0, 0.06, 0);
        for (int w = 0; w < 4; w++) Flashes.ground(ground, 0.6f, 4.5f + w * 2.6f, w % 2 == 0 ? RED : INK, dim(0.85f - w * 0.15f), 12 + w * 3, now + w * 2L);
        Flashes.ripple(pos, d, 0.5f, 6f, RED, dim(0.8f), 9, now);
        Flashes.ring(pos.add(0, 1.2, 0), 0.5f, 7f, RED, dim(0.7f), 10, now + 1);
        // The biggest tree of bolts, thrown along the launch.
        for (int i = 0; i < 12; i++) bolt(pos, randomAround(d, 1.2), 4.5 * Math.max(1f, s * 0.7f), 0.12f, 3, now + i % 3, 10);
        // Crimson streaks along the line the body takes.
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (int i = 0; i < 16; i++) {
            Vec3 off = side.scale(gauss(0.9)).add(0, gauss(0.7), 0);
            Vec3 a = pos.add(off), b = a.add(d.scale(6 + RNG.nextDouble() * 8)).add(0, 1.2 + RNG.nextDouble(), 0);
            if (i % 3 == 0) Flashes.darkBeam(a, b, 0.07f, INK, 0.9f, 8 + RNG.nextInt(5), now + i % 4);
            else Flashes.darkBeam(a, b, 0.05f, RED, dim(0.9f), 7 + RNG.nextInt(5), now + i % 4);
        }
        if (drawn) {
            ClientFx.debris(level, pos, q(40), 0.7);
            for (int i = 0; i < q(60); i++) {
                Vec3 v = randomUnit().scale(0.35 + RNG.nextDouble() * 0.5).add(d.scale(0.4)).add(0, 0.3, 0);
                add(level, pos, v, i % 2 == 0 ? Sprite.SHARD : Sprite.SPARK, i % 3 == 0 ? INK : i % 3 == 1 ? RED : WHITE_HOT, 1f,
                        0.12f + RNG.nextFloat() * 0.1f, 0.02f, 30 + RNG.nextInt(30)).gravity(0.8f).physics();
            }
            // Red haze rolling out low.
            for (int i = 0; i < q(24); i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                add(level, ground.add(out.scale(0.6)).add(0, 0.2, 0), out.scale(0.22).add(0, 0.01, 0), Sprite.SMOKE, i % 3 == 0 ? INK : RED, 0.5f,
                        0.6f, 1.6f, 26 + RNG.nextInt(12));
            }
        }
        // Scorch: long dark lines burnt across the ground from the blast (drawn only; no block is changed).
        for (int i = 0; i < 9; i++) {
            double ang = Math.atan2(d.z, d.x) + gauss(1.4);
            Vec3 a = ground.add(gauss(0.3), 0, gauss(0.3));
            Vec3 b = a.add(Math.cos(ang) * (2 + RNG.nextDouble() * 3), 0, Math.sin(ang) * (2 + RNG.nextDouble() * 3));
            Flashes.darkBeam(a, b, 0.06f, INK, 0.55f, 90, now);
            Flashes.darkBeam(a.add(0, 0.01, 0), b.add(0, 0.01, 0), 0.03f, RED, dim(0.75f), 14, now);
        }
        if (dist < 40) {
            float fall = (float) Math.max(0.3, 1 - dist / 40);
            ScreenEffects.shake(1.1f * fall, 18);
            if (mine) ScreenEffects.fovPunch(0.18f);
        }
    }

    /** A branching bolt: a black jagged core with a crimson glow along it, forking {@code forks} times. */
    private static void bolt(Vec3 from, Vec3 dir, double length, float width, int forks, long now, int life) {
        Vec3 a = from;
        Vec3 d = dir;
        int segs = 3 + forks;
        for (int k = 0; k < segs; k++) {
            Vec3 b = a.add(d.scale(length / segs * (0.7 + RNG.nextDouble() * 0.6))).add(gauss(0.12 * length / segs), gauss(0.12 * length / segs), gauss(0.12 * length / segs));
            float w = width * (1f - k / (float) segs * 0.6f);
            // The glow is blended, not added (added red over grass or sky turns yellow or pink).
            Flashes.darkBeam(a, b, w * 2.4f, RED, dim(0.5f), life + k, now);
            Flashes.darkBeam(a, b, w, INK, 1f, life + k, now);
            if (forks > 0 && k == segs / 2) bolt(b, randomAround(d, 0.9), length * 0.45, w * 0.7f, forks - 1, now, life);
            d = randomAround(d, 0.35);
            a = b;
        }
    }

    /** A direction near {@code d}, spread by about {@code spread}. */
    private static Vec3 randomAround(Vec3 d, double spread) {
        Vec3 v = d.add(randomUnit().scale(spread));
        return v.lengthSqr() < 1e-4 ? d : v.normalize();
    }

    /** An in-world flash's strength, dimmed under Reduced Flashes. */
    private static float dim(float alpha) {
        return reducedFlashes() ? alpha * 0.35f : alpha;
    }
}
