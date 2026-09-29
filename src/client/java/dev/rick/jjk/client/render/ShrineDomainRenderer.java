package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The inside of Malevolent Shrine, after the JJS GIF: a black void over a shallow pool of blood (the domain's real
 * blocks), the shrine standing in the middle — a stone base, red pillars, a great mouth of teeth in its doorway and a
 * green-tiled roof over orange eaves, drawn from real block models — and the sure hit made visible: white slashes flashing
 * through the air, never stopping.
 */
public final class ShrineDomainRenderer {
    private record Part(int x, int y, int z, BlockState state) {}

    private static final List<Part> SHRINE = build();
    private static final float SCALE = 1.6f;

    private ShrineDomainRenderer() {}

    private static List<Part> build() {
        List<Part> out = new ArrayList<>();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), dark = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        BlockState pillar = Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState(), beam = Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState();
        BlockState eave = Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), roof = Blocks.DYED_TERRACOTTA.pick(DyeColor.GREEN).defaultBlockState();
        BlockState ridge = Blocks.DARK_PRISMARINE.defaultBlockState(), lip = Blocks.CONCRETE.pick(DyeColor.PINK).defaultBlockState();
        BlockState tooth = Blocks.QUARTZ_BLOCK.defaultBlockState(), maw = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        // The base: two steps of stone.
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) out.add(new Part(x, 0, z, Math.abs(x) == 4 || Math.abs(z) == 3 ? dark : stone));
        for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) out.add(new Part(x, 1, z, stone));
        // The four red pillars and the beams across them.
        for (int y = 2; y <= 6; y++) for (int x : new int[] {-3, 3}) for (int z : new int[] {-2, 2}) out.add(new Part(x, y, z, pillar));
        for (int x = -3; x <= 3; x++) for (int z : new int[] {-2, 2}) out.add(new Part(x, 7, z, beam));
        for (int z = -2; z <= 2; z++) for (int x : new int[] {-3, 3}) out.add(new Part(x, 7, z, beam));
        // The back wall, and the mouth filling the doorway at the front (z = -2 faces outward): lips, two rows of teeth.
        for (int x = -2; x <= 2; x++) for (int y = 2; y <= 6; y++) out.add(new Part(x, y, 2, maw));
        for (int x = -2; x <= 2; x++) {
            out.add(new Part(x, 6, -2, lip));
            out.add(new Part(x, 2, -2, lip));
            out.add(new Part(x, 5, -2, x % 2 == 0 ? tooth : maw));
            out.add(new Part(x, 3, -2, x % 2 != 0 ? tooth : maw));
            out.add(new Part(x, 4, -2, maw));
        }
        // The roof: orange eaves, then green tiles stepping up to a dark ridge.
        for (int x = -5; x <= 5; x++) for (int z = -4; z <= 4; z++) if (Math.abs(x) == 5 || Math.abs(z) == 4) out.add(new Part(x, 8, z, eave));
        for (int step = 0; step < 4; step++) {
            int w = 4 - step, d = 3 - Math.min(step, 2);
            for (int x = -w; x <= w; x++) for (int z = -d; z <= d; z++) out.add(new Part(x, 8 + step, z, step == 3 ? ridge : roof));
        }
        // Bones heaped around the foot of it.
        Random r = new Random(13);
        for (int i = 0; i < 18; i++) {
            int x = r.nextInt(13) - 6, z = r.nextInt(11) - 5;
            if (Math.abs(x) <= 4 && Math.abs(z) <= 3) continue;
            out.add(new Part(x, 0, z, bone));
            if (r.nextInt(3) == 0) out.add(new Part(x, 1, z, bone));
        }
        return out;
    }

    /** Called with the pose at the domain's center. */
    static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, float r, float edgeGlow,
                       boolean inside, long now, float partial) {
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        float calm = dev.rick.jjk.client.clash.ClashFocus.active() ? 0.35f : 1f;
        // A dark red sheen on the barrier's rim from outside.
        if (edgeGlow > 0) Glow.sphere(c, ps, r * 0.995f, 0.6f, 0.02f, 0.05f, 0.25f * edgeGlow * calm, toCam, true);
        if (!inside) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        // The red haze of the void.
        Glow.sphere(c, ps, r * 0.95f, 0.5f, 0.02f, 0.04f, 0.18f * calm, toCam, false);
        // The shrine itself, facing the one who opened it... it stands behind the caster, looking out over everyone.
        BlockPos at = BlockPos.containing(d.center).above(2);
        ps.pushPose();
        float back = Math.min(r * 0.62f, 12f);
        ps.translate(0, -0.5, back);
        ps.scale(SCALE, SCALE, SCALE);
        for (Part p : SHRINE) {
            ps.pushPose();
            ps.translate(p.x - 0.5, p.y, p.z - 0.5);
            c.submitMovingBlock(ps, state(level, at, p.state), 0);
            ps.popPose();
        }
        ps.popPose();
        // The mouth's glow.
        ps.pushPose();
        ps.translate(0, 4.5 * SCALE - 0.5, back - 2.6 * SCALE);
        Glow.halo(c, ps, camRot, 3.5f, 1f, 0.2f, 0.25f, 0.25f * calm);
        ps.popPose();
        // The sure hit, visible: white slashes cutting through the air all around, a new set every couple of ticks.
        float time = now + partial;
        int batch = (int) (time / 2);
        Random rnd = new Random(batch * 7919L + d.id);
        for (int i = 0; i < 9; i++) {
            float age = (time - batch * 2) / 2f;
            Vec3 p = new Vec3((rnd.nextFloat() - 0.5f) * r * 1.4f, rnd.nextFloat() * r * 0.5f, (rnd.nextFloat() - 0.5f) * r * 1.4f);
            if (p.length() > r * 0.9f) continue;
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.rotate(Axis.YP.rotation(rnd.nextFloat() * Mth.TWO_PI));
            ps.rotate(Axis.XP.rotation((rnd.nextFloat() - 0.5f) * 2.2f));
            float len = 2.5f + rnd.nextFloat() * 5f;
            ps.translate(0, 0, -len / 2);
            Glow.beam(c, ps, len, 0.09f, 1f, 1f, 1f, 0.85f * (1 - age) * calm);
            Glow.beam(c, ps, len * 0.9f, 0.25f, 1f, 0.3f, 0.3f, 0.3f * (1 - age) * calm);
            ps.popPose();
        }
    }

    private static MovingBlockRenderState state(ClientLevel level, BlockPos at, BlockState bs) {
        MovingBlockRenderState s = new MovingBlockRenderState();
        s.blockPos = at;
        s.blockState = bs;
        s.cardinalLighting = level.cardinalLighting();
        s.lightEngine = level.getLightEngine();
        s.biome = level.getBiome(at);
        return s;
    }
}
