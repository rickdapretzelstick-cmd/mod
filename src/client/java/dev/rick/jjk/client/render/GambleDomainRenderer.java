package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.net.GamblePayload;
import dev.rick.jjk.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The inside of Idle Death Gamble, after the domain as Jujutsu Shenanigans draws it: a bright white room with a pale
 * floor and framed hatches (the domain's real blocks), walls lined with white bullet trains running in stacked rings, one
 * more train snaking through the air around the arena, and three giant red seven-segment counters showing the gamble's
 * reels as it happens: counting while Hakari builds toward a Riichi, locking two numbers, stopping the third on the
 * reveal. Everything is real block models (train cars, LED segments) lit by the model itself, so the room stays bright.
 */
public final class GambleDomainRenderer {
    private static final float[] PINK = {1f, 0.25f, 0.63f}, GOLD = {1f, 0.8f, 0.25f}, JADE = {0.36f, 1f, 0.66f};
    /** Seven-segment masks (bits a..g from bit 0) for 0..9. */
    private static final int[] SEGMENTS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
    /** Length of one train car model (blocks, before scaling) and the scale the cars are drawn at. */
    private static final float CAR = 3f, CAR_SCALE = 1.5f;

    private GambleDomainRenderer() {}

    /** Called with the pose at the domain's center. */
    static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, float r, float edgeGlow,
                       boolean inside, long now, float partial) {
        float time = now + partial;
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        float calm = dev.rick.jjk.client.clash.ClashFocus.active() ? 0.35f : 1f;
        // A faint white sheen on the barrier's rim.
        if (edgeGlow > 0) Glow.sphere(c, ps, r * 0.995f, 1f, 0.96f, 0.98f, 0.18f * edgeGlow * calm, toCam, true);
        if (!inside) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos at = BlockPos.containing(d.center);
        Blocks b = new Blocks(mc.level, at, mc.level.getBiome(at));
        float rush = rushAge(d, now, partial);
        if (rush >= 0 && rush < RUSH) {
            // Just sealed: a rush through a tunnel of trains before the room settles.
            rushTunnel(c, ps, cam, camRot, d, b, rush);
            return;
        }
        // The room glows: its walls bloom into a white haze, and so does everything standing in it.
        Glow.sphere(c, ps, r * 0.97f, 1f, 1f, 1f, 0.45f * calm, toCam, true);
        Glow.sphere(c, ps, r * 0.9f, 1f, 0.98f, 0.95f, 0.12f * calm, toCam, false);
        standingCars(c, ps, camRot, d, b, r, calm);
        ClientState.Gamble gamble = ClientState.GAMBLES.get(d.id);
        GamblePayload p = gamble != null ? gamble.p : null;
        // The giant counters and the winding train play the Riichi out.
        if (p != null && (p.state() == GamblePayload.RIICHI || p.state() == GamblePayload.JACKPOT)) {
            snakeTrain(c, ps, d, b, r, time);
            counters(c, ps, cam, d, b, gamble, r, now, time, calm);
        }
    }

    private static final java.util.Map<Integer, Long> FLOOD_TICK = new java.util.HashMap<>();

    /**
     * While Idle Death Gamble forms: a burst of white smoke off the caster, then white spreading over the ground from
     * their feet (the real floor blocks going down) with black ink splashing up along its edge, and white smoke rolling
     * up the walls as they rise.
     */
    static void formation(ClientState.Domain d, float progress, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || FLOOD_TICK.getOrDefault(d.id, Long.MIN_VALUE) == now) return;
        FLOOD_TICK.put(d.id, now);
        if (FLOOD_TICK.size() > 64) FLOOD_TICK.clear();
        var level = mc.level;
        java.util.Random rnd = new java.util.Random(now * 31 + d.id);
        float R = d.radius + d.thickness;
        Vec3 c0 = d.center;
        double floorY = c0.y - 0.45;
        if (progress < 0.18f) {
            // The burst of white smoke.
            for (int i = 0; i < 8; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double v = 0.08 + rnd.nextDouble() * 0.18;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, c0.x, c0.y + 0.4 + rnd.nextDouble(), c0.z,
                        Math.cos(a) * v, 0.02 + rnd.nextDouble() * 0.05, Math.sin(a) * v);
            }
        }
        float ground = dev.rick.jjk.core.domain.structure.DomainFormation.GROUND_END;
        if (progress < ground) {
            // Ink splashing up along the spreading white.
            double rad = Math.max(0.5, R * progress / ground);
            int n = (int) Math.min(24, 6 + rad * 1.5);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double x = c0.x + Math.cos(a) * rad, z = c0.z + Math.sin(a) * rad;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SQUID_INK, x, floorY + 0.15, z,
                        Math.cos(a) * 0.08, 0.06 + rnd.nextDouble() * 0.08, Math.sin(a) * 0.08);
                if (i % 3 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, x, floorY + 0.3, z, 0, 0.03, 0);
            }
        } else if (progress < dev.rick.jjk.core.domain.structure.DomainFormation.CEILING_END) {
            // White smoke rolling up the rising walls.
            for (int i = 0; i < 10; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double y = rnd.nextDouble() * R * 0.8;
                double rr = Math.sqrt(Math.max(0, R * R - y * y)) - 1;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, c0.x + Math.cos(a) * rr, c0.y + y, c0.z + Math.sin(a) * rr,
                        -Math.cos(a) * 0.05, 0.04, -Math.sin(a) * 0.05);
            }
        }
    }

    /** Ticks of the rush through the trains when the domain seals. */
    public static final int RUSH = 46;

    /** How long ago this domain sealed (straight out of its formation), or -1. */
    public static float rushAge(ClientState.Domain d, long now, float partial) {
        if (d.phase != dev.rick.jjk.core.net.DomainPayload.ACTIVE || d.prevPhase != dev.rick.jjk.core.net.DomainPayload.FORMING) return -1;
        return now - d.phaseStartTick + partial;
    }

    /**
     * The rush: two walls of train cars, stacked three high on either side of the camera, streaming past toward it; for
     * the last third they tumble apart and fly off (the room settles behind them).
     */
    private static void rushTunnel(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, Blocks b, float age) {
        Vector3f fw = new Vector3f(0, 0, -1).rotate(camRot);
        Vec3 f = new Vec3(fw.x, 0, fw.z);
        f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        Vec3 eye = cam.subtract(d.center);
        float tumble = Mth.clamp((age - 28) / 18f, 0, 1);
        float speed = 2.4f, spacing = 5.2f, length = 52f;
        int k = 0;
        for (int wall = -1; wall <= 1; wall += 2) {
            for (int col = 0; col < 2; col++) {
                for (int row = 0; row < 3; row++) {
                    for (int n = 0; n < 10; n++, k++) {
                        float z = length - 8 - ((n * spacing + col * 2.6f + row * 1.3f + age * speed) % length);
                        double x = wall * (2.9 + col * 1.7 + tumble * tumble * (6 + (k % 5) * 2.5));
                        double y = -1.5 + row * 1.45 + tumble * tumble * ((k % 7) - 2) * 1.6;
                        Vec3 at = eye.add(f.scale(z)).add(side.scale(x)).add(0, y, 0);
                        float roll = tumble * (k % 2 == 0 ? 1 : -1) * (2.5f + (k % 3));
                        car(c, ps, b, at, f.scale(-1), roll, 1.1f);
                    }
                }
            }
        }
    }

    /** Cars standing about the white floor, facing in: the settled room. */
    private static void standingCars(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, ClientState.Domain d, Blocks b, float r, float calm) {
        for (int ring = 0; ring < 2; ring++) {
            int n = ring == 0 ? 10 : 14;
            float rr = r * (ring == 0 ? 0.5f : 0.74f);
            for (int i = 0; i < n; i++) {
                float a = Mth.TWO_PI * (i + ring * 0.5f) / n;
                Vec3 at = new Vec3(Mth.cos(a) * rr, 0.02, Mth.sin(a) * rr);
                if (!DomainSpace.onSide(d, d.center.add(at))) continue;
                car(c, ps, b, at, at.scale(-1), 0, 0.9f);
                ps.pushPose();
                ps.translate(at.x, at.y + 0.6, at.z);
                Glow.halo(c, ps, camRot, 1.9f, 1f, 1f, 1f, 0.3f * calm);
                ps.popPose();
            }
        }
    }

    /** One more train winding around the arena in the air, rising and dipping as it goes. */
    private static void snakeTrain(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, Blocks b, float r, float time) {
        float rs = r * 0.55f;
        float spacing = (CAR * CAR_SCALE + 0.3f) / rs;
        for (int i = 0; i < 9; i++) {
            float u = time * 0.09f / rs - i * spacing;
            Vec3 at = snake(u, r, rs), next = snake(u + 0.01f, r, rs);
            if (DomainSpace.onSide(d, d.center.add(at))) car(c, ps, b, at, next.subtract(at));
        }
    }

    private static Vec3 snake(float u, float r, float rs) {
        float y = r * 0.42f + r * 0.12f * Mth.sin(u * 2);
        return new Vec3(Mth.cos(u) * rs, y, Mth.sin(u) * rs);
    }

    /** A car with its base centered at {@code at}, nose pointing along {@code dir}. */
    private static void car(SubmitNodeCollector c, PoseStack ps, Blocks b, Vec3 at, Vec3 dir) {
        car(c, ps, b, at, dir, 0, CAR_SCALE);
    }

    /** ...rolled about its length by {@code roll} radians, at {@code scale}. */
    private static void car(SubmitNodeCollector c, PoseStack ps, Blocks b, Vec3 at, Vec3 dir, float roll, float scale) {
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.rotate(Axis.YP.rotation((float) Math.atan2(-dir.z, dir.x)));
        ps.rotate(Axis.ZP.rotation((float) Math.atan2(dir.y, horiz)));
        if (roll != 0) {
            ps.translate(0, 0.45 * scale, 0);
            ps.rotate(Axis.XP.rotation(roll));
            ps.translate(0, -0.45 * scale, 0);
        }
        ps.scale(scale, scale, scale);
        // The model runs from x=-1 to x=2; center it along its length and width, base on the path.
        ps.translate(-0.5, 0, -0.5);
        c.submitMovingBlock(ps, b.state(ModBlocks.PropBlock.TRAIN_CAR), 0);
        ps.popPose();
    }

    /** Three giant seven-segment counters around the room, one per reel, turned to face whoever is looking. */
    private static void counters(SubmitNodeCollector c, PoseStack ps, Vec3 cam, ClientState.Domain d, Blocks b, ClientState.Gamble gamble,
                                 float r, long now, float time, float calm) {
        GamblePayload p = gamble != null ? gamble.p : null;
        boolean riichi = p != null && p.state() == GamblePayload.RIICHI;
        float stateAge = gamble != null ? now - gamble.stateStart + (time - now) : 0;
        int reveal = p != null ? GamblePayload.revealAt(p) : 1;
        boolean won = p != null && (p.state() == GamblePayload.JACKPOT || riichi && stateAge >= reveal && p.reel2() == p.reel0() && p.reel2() != 0);
        float height = Math.min(r * 0.62f, 5.5f), width = height * 0.52f;
        float dist = r * 0.5f, lift = height / 2 + 1.2f;
        for (int i = 0; i < 3; i++) {
            boolean spinning = p == null || p.state() == GamblePayload.SPINNING || riichi && (i == 2 ? stateAge < reveal : stateAge < 8 + i * 4);
            int digit;
            if (spinning) {
                // Counting through the numbers (the third reel slows toward the reveal).
                float speed = riichi && i == 2 ? Mth.lerp(Mth.clamp(stateAge / reveal, 0, 1), 0.5f, 0.1f) : 0.5f;
                digit = 1 + Math.floorMod((int) (time * speed) + i * 3 + (p != null ? p.reel0() : 0), 7);
            } else {
                int v = i == 0 ? p.reel0() : i == 1 ? p.reel1() : p.reel2();
                digit = v <= 0 ? -1 : v;
            }
            float a = Mth.PI / 2 + (i - 1) * Mth.TWO_PI / 3;
            Vec3 at = new Vec3(Mth.cos(a) * dist, lift, Mth.sin(a) * dist);
            Vec3 world = d.center.add(at);
            if (!DomainSpace.onSide(d, world)) continue;
            ps.pushPose();
            ps.translate(at.x, at.y, at.z);
            ps.rotate(Axis.YP.rotation((float) Math.atan2(cam.x - world.x, cam.z - world.z)));
            // A win lights the counters up in jade; a Riichi pulses its signal colour behind them.
            if (won || riichi && stateAge >= 16) {
                float[] col = won ? JADE : signal(p.signal(), time);
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.5f);
                Glow.halo(c, ps, new Quaternionf(), height * 0.8f, col[0], col[1], col[2], (0.25f + 0.3f * pulse) * calm);
            }
            // A digit that has not stopped yet flickers between frames of the count.
            if (!spinning || ((int) time + i) % 4 != 0) digit(c, ps, b, digit, width, height);
            ps.popPose();
        }
    }

    /** A seven-segment digit centered on the pose, facing +z. -1 draws a dash. */
    private static void digit(SubmitNodeCollector c, PoseStack ps, Blocks b, int digit, float w, float h) {
        int mask = digit < 0 ? 0x40 : SEGMENTS[digit];
        float t = w * 0.2f, depth = 0.3f, gap = t * 0.25f;
        float hl = w - t - 2 * gap, vl = h / 2 - t / 2 - 2 * gap;
        float[][] seg = {
                {0, h / 2 - t / 2, hl, t}, {w / 2 - t / 2, h / 4, t, vl}, {w / 2 - t / 2, -h / 4, t, vl},
                {0, -h / 2 + t / 2, hl, t}, {-w / 2 + t / 2, -h / 4, t, vl}, {-w / 2 + t / 2, h / 4, t, vl}, {0, 0, hl, t}};
        for (int s = 0; s < 7; s++) {
            if ((mask & 1 << s) == 0) continue;
            float[] g = seg[s];
            ps.pushPose();
            ps.translate(g[0], g[1], 0);
            ps.scale(g[2], g[3], depth);
            ps.translate(-0.5, -0.5, -0.5);
            c.submitMovingBlock(ps, b.state(ModBlocks.PropBlock.LED_SEGMENT), 0);
            ps.popPose();
        }
    }

    private static float[] signal(int s, float time) {
        return switch (s) {
            case 0 -> JADE;
            case 1 -> new float[]{1f, 0.18f, 0.2f};
            case 2 -> GOLD;
            default -> ((int) (time / 3)) % 3 == 0 ? PINK : ((int) (time / 3)) % 3 == 1 ? GOLD : JADE;
        };
    }

    /** Render states for the domain's props, lit from the domain's center (the models carry their own light anyway). */
    private record Blocks(ClientLevel level, BlockPos at, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        MovingBlockRenderState state(int part) {
            BlockState bs = ModBlocks.IDG_PROP.defaultBlockState().setValue(ModBlocks.PropBlock.PART, part);
            MovingBlockRenderState s = new MovingBlockRenderState();
            s.blockPos = at;
            s.blockState = bs;
            s.cardinalLighting = level.cardinalLighting();
            s.lightEngine = level.getLightEngine();
            s.biome = biome;
            return s;
        }
    }
}
