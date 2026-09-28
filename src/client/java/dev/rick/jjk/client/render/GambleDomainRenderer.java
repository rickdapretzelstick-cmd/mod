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
final class GambleDomainRenderer {
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
        trainWalls(c, ps, d, b, r, time);
        snakeTrain(c, ps, d, b, r, time);
        ClientState.Gamble gamble = ClientState.GAMBLES.get(d.id);
        counters(c, ps, cam, d, b, gamble, r, now, time, calm);
    }

    /** Rings of white bullet trains stacked up the walls, each ring running the other way from the one below. */
    private static void trainWalls(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, Blocks b, float r, float time) {
        float carLen = CAR * CAR_SCALE + 0.35f;
        for (int tier = 0; tier < 3; tier++) {
            float h = 0.05f + tier * 1.55f;
            // Stay clear of the curving shell: the room's radius at the top of this tier, less the shell and a margin.
            float top = h + 1.5f;
            float rw = (float) Math.sqrt(Math.max(1, r * r - top * top)) - 2.4f;
            if (rw < 3) continue;
            int n = Math.max(3, (int) (Mth.TWO_PI * rw / carLen));
            float speed = 0.06f * (tier % 2 == 0 ? 1 : -1);
            float offset = time * speed / rw;
            for (int i = 0; i < n; i++) {
                float a = offset + Mth.TWO_PI * i / n;
                Vec3 at = new Vec3(Mth.cos(a) * rw, h, Mth.sin(a) * rw);
                // Tangent to the ring, in the direction the ring runs.
                Vec3 dir = new Vec3(-Mth.sin(a), 0, Mth.cos(a)).scale(speed >= 0 ? 1 : -1);
                // Mid-clash only this side of the split is Hakari's.
                if (DomainSpace.onSide(d, d.center.add(at))) car(c, ps, b, at, dir);
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
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.rotate(Axis.YP.rotation((float) Math.atan2(-dir.z, dir.x)));
        ps.rotate(Axis.ZP.rotation((float) Math.atan2(dir.y, horiz)));
        ps.scale(CAR_SCALE, CAR_SCALE, CAR_SCALE);
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
        int reveal = dev.rick.jjk.config.JJKConfig.get().hakari.riichiTicks - dev.rick.jjk.hakari.Gamble.REVEAL_OFFSET;
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
