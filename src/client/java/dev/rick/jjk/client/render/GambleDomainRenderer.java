package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.ClientState;
import dev.rick.jjk.core.net.GamblePayload;
import dev.rick.jjk.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The inside of Idle Death Gamble: a casino. Neon bands chase around the dome in pink and gold, spotlights sweep the
 * floor, and three giant slot reels hang over the middle of the arena showing the gamble as it happens — spinning while
 * Hakari builds toward a Riichi, locking two numbers, and stopping the third on the reveal. The walls and floor
 * themselves are the domain's real blocks (neon pachinko wall, casino floor).
 */
final class GambleDomainRenderer {
    private static final float[] PINK = {1f, 0.25f, 0.63f}, GOLD = {1f, 0.8f, 0.25f}, JADE = {0.36f, 1f, 0.66f};
    private static final MovingBlockRenderState[] REEL = {new MovingBlockRenderState(), new MovingBlockRenderState(), new MovingBlockRenderState()};

    private GambleDomainRenderer() {}

    /** Called with the pose at the domain's center. */
    static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, float r, float edgeGlow,
                       boolean inside, long now, float partial) {
        float time = now + partial;
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        float calm = dev.rick.jjk.client.clash.ClashFocus.active() ? 0.35f : 1f;
        // The barrier's neon rim.
        Glow.sphere(c, ps, r * 0.995f, 1f, 0.3f, 0.65f, 0.3f * edgeGlow * calm, toCam, true);
        if (!inside) return;
        ClientState.Gamble gamble = ClientState.GAMBLES.get(d.id);
        GamblePayload p = gamble != null ? gamble.p : null;
        boolean riichi = p != null && p.state() == GamblePayload.RIICHI;
        float riichiAge = gamble != null ? now - gamble.stateStart + partial : 0;
        // Neon bands at several heights, lights chasing around them (faster, and in the signal colour, during a Riichi).
        for (int i = 0; i < 4; i++) {
            float h = r * (0.1f + i * 0.2f);
            float rr = (float) Math.sqrt(Math.max(0.1, r * r * 0.92 - h * h));
            ps.pushPose();
            ps.translate(0, h, 0);
            ps.rotate(Axis.YP.rotation(time * (riichi ? 0.08f : 0.02f) * (i % 2 == 0 ? 1 : -1)));
            float[] col = riichi && riichiAge >= 16 ? signal(p.signal(), time) : i % 2 == 0 ? PINK : GOLD;
            Glow.ring(c, ps, rr, 0.14f, col[0], col[1], col[2], 0.55f * calm);
            // Bulbs along the band.
            for (int k = 0; k < 24; k++) {
                if (((int) (time / 2) + k) % 3 != 0) continue;
                float a = Mth.TWO_PI * k / 24;
                ps.pushPose();
                ps.translate(Mth.cos(a) * rr, 0, Mth.sin(a) * rr);
                ps.rotate(camRot);
                Glow.halo(c, ps, new Quaternionf(), 0.35f, col[0], col[1], col[2], 0.8f * calm);
                ps.popPose();
            }
            ps.popPose();
        }
        // Spotlights sweeping the floor from the crown of the dome.
        for (int i = 0; i < 3; i++) {
            float a = time * 0.03f + i * Mth.TWO_PI / 3;
            Vec3 top = new Vec3(0, r * 0.85, 0), floor = new Vec3(Mth.cos(a) * r * 0.5, -0.4, Mth.sin(a) * r * 0.5);
            Vec3 dir = floor.subtract(top);
            float len = (float) dir.length();
            Vec3 n = dir.scale(1 / len);
            ps.pushPose();
            ps.translate(top.x, top.y, top.z);
            ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
            ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
            float[] col = i == 0 ? PINK : i == 1 ? GOLD : new float[]{1f, 1f, 1f};
            Glow.beam(c, ps, len, 1.1f, col[0], col[1], col[2], 0.12f * calm);
            ps.popPose();
            ps.pushPose();
            ps.translate(floor.x, floor.y + 0.05, floor.z);
            Glow.ring(c, ps, 1.6f, 0.9f, col[0], col[1], col[2], 0.25f * calm);
            ps.popPose();
        }
        reels(c, ps, cam, d, p, gamble, riichiAge, time, r);
    }

    /** Three giant slot reels hanging over the arena, turned to face whoever is looking. */
    private static void reels(SubmitNodeCollector c, PoseStack ps, Vec3 cam, ClientState.Domain d, GamblePayload p, ClientState.Gamble gamble,
                              float stateAge, float time, float r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float height = r * 0.5f;
        Vec3 at = d.center.add(0, height, 0);
        float yaw = (float) Math.atan2(cam.x - at.x, cam.z - at.z);
        int reveal = dev.rick.jjk.config.JJKConfig.get().hakari.riichiTicks - dev.rick.jjk.hakari.Gamble.REVEAL_OFFSET;
        boolean riichi = p != null && p.state() == GamblePayload.RIICHI;
        float size = 2.6f, gap = 0.5f;
        ps.pushPose();
        ps.translate(0, height, 0);
        ps.rotate(Axis.YP.rotation(yaw));
        // The gold cabinet frame behind the reels.
        float fw = size * 3 + gap * 2 + 1.2f;
        ps.pushPose();
        ps.translate(-fw / 2, -size / 2 - 0.6f, -0.2f);
        ps.rotate(Axis.ZP.rotationDegrees(-90));
        Glow.beam(c, ps, fw, 0.25f, GOLD[0], GOLD[1], GOLD[2], 0.7f);
        ps.popPose();
        ps.pushPose();
        ps.translate(-fw / 2, size / 2 + 0.6f, -0.2f);
        ps.rotate(Axis.ZP.rotationDegrees(-90));
        Glow.beam(c, ps, fw, 0.25f, PINK[0], PINK[1], PINK[2], 0.7f);
        ps.popPose();
        for (int i = 0; i < 3; i++) {
            int digit;
            boolean spinning = p == null || p.state() == GamblePayload.SPINNING || riichi && (i == 2 ? stateAge < reveal : stateAge < 8 + i * 4);
            if (spinning) {
                // Blur with numbers flashing past (the third reel slows toward the reveal).
                float speed = riichi && i == 2 ? Mth.lerp(Mth.clamp(stateAge / reveal, 0, 1), 1f, 0.2f) : 1f;
                int step = (int) (time * speed + i * 3);
                digit = step % 3 == 0 ? 0 : 1 + Math.floorMod(step + (p != null ? p.reel0() : 0), 7);
            } else {
                int v = i == 0 ? p.reel0() : i == 1 ? p.reel1() : p.reel2();
                digit = v <= 0 ? 0 : v;
            }
            MovingBlockRenderState s = REEL[i];
            BlockPos lightAt = BlockPos.containing(at);
            s.blockPos = lightAt;
            s.blockState = ModBlocks.REEL.defaultBlockState().setValue(ModBlocks.ReelBlock.DIGIT, digit);
            s.cardinalLighting = mc.level.cardinalLighting();
            s.lightEngine = mc.level.getLightEngine();
            s.biome = mc.level.getBiome(lightAt);
            ps.pushPose();
            float x = (i - 1) * (size + gap);
            // A spinning reel rolls; a stopped one sits square.
            ps.translate(x, 0, 0);
            if (spinning) ps.rotate(Axis.XP.rotationDegrees((time * 40 + i * 50) % 90 - 45));
            ps.scale(size, size, size);
            ps.translate(-0.5, -0.5, -0.5);
            c.submitMovingBlock(ps, s, 0);
            ps.popPose();
        }
        // A win lights the whole cabinet up.
        if (p != null && (p.state() == GamblePayload.JACKPOT || riichi && stateAge >= reveal && p.reel2() == p.reel0() && p.reel2() != 0)) {
            ps.pushPose();
            Glow.halo(c, ps, new Quaternionf(), size * 3f, JADE[0], JADE[1], JADE[2], 0.6f);
            ps.popPose();
        }
        ps.popPose();
    }

    private static float[] signal(int s, float time) {
        return switch (s) {
            case 0 -> JADE;
            case 1 -> new float[]{1f, 0.18f, 0.2f};
            case 2 -> GOLD;
            default -> ((int) (time / 3)) % 3 == 0 ? PINK : ((int) (time / 3)) % 3 == 1 ? GOLD : JADE;
        };
    }
}
