package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.rick.jjk.JJK;
import dev.rick.jjk.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The inside of Malevolent Shrine, after the Jujutsu Shenanigans GIF:
 * <ul>
 *   <li>A black void over a pool of blood that spreads dark red from the shrine and fades to black at the edges.</li>
 *   <li>The shrine stands behind Sukuna, facing the way he looked as he opened it: a low stone platform, two thick red
 *       pillars and a red lintel framing the doorway, the enormous grinning mouth filling it, orange brick eaves and a
 *       green-grey hipped roof. It appears grey and colourless as the domain seals, then its colour floods in.</li>
 *   <li>The sure hit made visible: long white slashes with black cores cutting through the air from every direction,
 *       never stopping, and red mist drifting through the dark.</li>
 * </ul>
 * The shrine is real block models (lit as if by its own light, so the void around it can stay black) and the mouth one
 * textured panel.
 */
public final class ShrineDomainRenderer {
    private record Part(int x, int y, int z, BlockState color, BlockState grey) {}

    private static final List<Part> SHRINE = build();
    private static final RenderType MOUTH = RenderTypes.entityCutout(JJK.id("textures/entity/shrine_mouth.png"));
    private static final RenderType MOUTH_GREY = RenderTypes.entityCutout(JJK.id("textures/entity/shrine_mouth_grey.png"));
    /** Ticks after sealing: the shrine greys in, then its colour floods in (GIF frames 150-200). */
    public static final int REVEAL = 26, COLOUR_AT = 14;
    /** The way the owner faced when the domain was first seen here (the shrine stands behind them, facing out). */
    private static final Map<Integer, Float> FACING = new HashMap<>();

    private ShrineDomainRenderer() {}

    private static List<Part> build() {
        List<Part> out = new ArrayList<>();
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState(), stoneEdge = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState red = Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState();
        BlockState brick = Blocks.BRICKS.defaultBlockState(), roof = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState ridge = Blocks.DYED_TERRACOTTA.pick(DyeColor.GREEN).defaultBlockState(), maw = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
        BlockState greyStone = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState(), greyPillar = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
        BlockState greyBrick = Blocks.STONE_BRICKS.defaultBlockState(), greyRoof = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        // The platform: low and wide, a step out in front of the doorway.
        for (int x = -8; x <= 8; x++) for (int z = -6; z <= 6; z++) {
            boolean edge = Math.abs(x) == 8 || Math.abs(z) == 6;
            out.add(new Part(x, 0, z, edge ? stoneEdge : stone, edge ? greyBrick : greyStone));
        }
        for (int x = -5; x <= 5; x++) out.add(new Part(x, 0, -7, stoneEdge, greyBrick));
        // Four thick red pillars (2x2), the front pair framing the doorway.
        for (int y = 1; y <= 8; y++) {
            for (int px : new int[] {-6, -5, 5, 6}) for (int pz : new int[] {-4, -3, 3, 4}) out.add(new Part(px, y, pz, red, greyPillar));
        }
        // The lintels: a heavy red beam across the front and back, and along the sides.
        for (int y = 9; y <= 10; y++) {
            for (int x = -7; x <= 7; x++) for (int z : new int[] {-4, -3, 3, 4}) out.add(new Part(x, y, z, red, greyPillar));
            for (int z = -2; z <= 2; z++) for (int x : new int[] {-6, 6}) out.add(new Part(x, y, z, red, greyPillar));
        }
        // Walls behind the pillars and the dark of the doorway (the mouth hangs in front of it).
        for (int y = 1; y <= 8; y++) {
            for (int x = -4; x <= 4; x++) out.add(new Part(x, y, 3, maw, maw));
            for (int z = -2; z <= 2; z++) for (int x : new int[] {-6, 6}) out.add(new Part(x, y, z, brick, greyBrick));
        }
        // The eaves: two courses of orange brick jutting out well past the pillars.
        for (int x = -10; x <= 10; x++) for (int z = -8; z <= 8; z++) if (Math.abs(x) >= 9 || Math.abs(z) >= 7) out.add(new Part(x, 11, z, brick, greyBrick));
        for (int x = -9; x <= 9; x++) for (int z = -7; z <= 7; z++) out.add(new Part(x, 12, z, brick, greyBrick));
        // The hipped roof of green-grey tiles, stepping in to the ridge.
        for (int step = 0; step < 6; step++) {
            int w = 8 - step, d = 6 - step;
            for (int x = -w; x <= w; x++) for (int z = -d; z <= d; z++) {
                boolean top = step == 5;
                out.add(new Part(x, 13 + step, z, top ? ridge : roof, greyRoof));
            }
        }
        return out;
    }

    /** How long ago this shrine sealed (straight out of its formation), or -1 if it was already standing. */
    public static float revealAge(ClientState.Domain d, long now, float partial) {
        if (d.phase != dev.rick.jjk.core.net.DomainPayload.ACTIVE || d.prevPhase != dev.rick.jjk.core.net.DomainPayload.FORMING) return -1;
        return now - d.phaseStartTick + partial;
    }

    private static float facing(ClientState.Domain d, ClientLevel level) {
        return FACING.computeIfAbsent(d.id, k -> {
            if (FACING.size() > 32) FACING.clear();
            Entity owner = level.getEntity(d.ownerId);
            return owner != null ? owner.getYRot() : 0f;
        });
    }

    /** Called with the pose at the domain's center. */
    static void render(SubmitNodeCollector c, PoseStack ps, Vec3 cam, Quaternionf camRot, ClientState.Domain d, float r, float edgeGlow,
                       boolean inside, long now, float partial) {
        Vector3f toCam = new Vector3f((float) (cam.x - d.center.x), (float) (cam.y - d.center.y), (float) (cam.z - d.center.z));
        float calm = dev.rick.jjk.client.clash.ClashFocus.active() ? 0.35f : 1f;
        // From outside: a black dome with a dark red sheen on its rim.
        if (edgeGlow > 0) Glow.sphere(c, ps, r * 0.995f, 0.6f, 0.02f, 0.05f, 0.25f * edgeGlow * calm, toCam, true);
        if (!inside) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float reveal = revealAge(d, now, partial);
        boolean revealing = reveal >= 0 && reveal < REVEAL;
        float appear = revealing ? Mth.clamp(reveal / COLOUR_AT, 0, 1) : 1f;
        boolean colour = !revealing || reveal >= COLOUR_AT;
        float time = now + partial;
        float yaw = facing(d, level);
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        // The pool of blood: dark red spreading from under the shrine, fading to black toward the barrier.
        // Split in a clash, everything stays on its side of the boundary.
        boolean split = DomainSpace.split(d);
        if (!split) pool(c, ps, d, r, look, colour ? 1f : 0.15f, time, calm);
        // Red mist drifting through the void.
        if (colour) mist(c, ps, camRot, d, r, time, calm);
        // The shrine, behind the owner, facing where they looked.
        float scale = Mth.clamp(r / 26f, 0.3f, 0.75f);
        float back = Math.min(r * 0.55f, 5f + 8f * scale);
        boolean shrineHere = DomainSpace.onSide(d, d.center.add(-look.x * back, 0, -look.z * back));
        if (shrineHere) {
            ps.pushPose();
            ps.translate(-look.x * back, -0.5 - (1 - appear) * 1.5f, -look.z * back);
            ps.rotate(Axis.YP.rotationDegrees(-yaw + 180));
            ps.scale(scale, scale, scale);
            int light = colour ? 15 : Math.round(9 + 6 * appear);
            BlockPos at = BlockPos.containing(d.center).above(2);
            for (Part p : SHRINE) {
                ps.pushPose();
                ps.translate(p.x - 0.5, p.y, p.z - 0.5);
                c.submitMovingBlock(ps, new Lit(level, at, colour ? p.color : p.grey, light), 0);
                ps.popPose();
            }
            // The mouth, filling the doorway, grinning out at everyone.
            float grey = colour ? 1f : 0.55f + 0.2f * appear;
            c.submitCustomGeometry(ps, colour ? MOUTH : MOUTH_GREY, (pose, buf) -> mouth(pose, buf, grey));
            ps.popPose();
        }
        if (colour && shrineHere) {
            // The glow from the doorway.
            ps.pushPose();
            ps.translate(-look.x * (back - 3.2f * scale), -0.5 + 5f * scale, -look.z * (back - 3.2f * scale));
            Glow.halo(c, ps, camRot, 5.5f * scale, 1f, 0.15f, 0.2f, 0.22f * calm);
            ps.popPose();
        }
        if (colour) slashes(c, ps, d, r, time, calm);
    }

    /** The doorway's panel, just in front of the dark behind it (model units, pose already at the shrine's scale). */
    private static void mouth(PoseStack.Pose pose, VertexConsumer buf, float shade) {
        float x0 = -4.6f, x1 = 4.6f, y0 = 1f, y1 = 8.9f, z = -2.4f;
        int col = colorOf(shade);
        int light = 0xF000F0;
        buf.addVertex(pose, x0, y0, z).setColor(col).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        buf.addVertex(pose, x1, y0, z).setColor(col).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        buf.addVertex(pose, x1, y1, z).setColor(col).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        buf.addVertex(pose, x0, y1, z).setColor(col).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
    }

    private static int colorOf(float shade) {
        int v = Math.round(255 * Mth.clamp(shade, 0, 1));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    /** The blood pool: a flattened glow, brightest under the shrine, and a slow ripple running out through it. */
    private static void pool(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, float r, Vec3 look, float strength, float time, float calm) {
        Vector3f up = new Vector3f(0, 50, 0);
        ps.pushPose();
        ps.translate(-look.x * r * 0.25f, -0.42, -look.z * r * 0.25f);
        ps.scale(1, 0.01f, 1);
        Glow.sphere(c, ps, r * 0.8f, 0.5f, 0.0f, 0.02f, 0.35f * strength * calm, up, false);
        Glow.sphere(c, ps, r * 0.45f, 0.65f, 0.01f, 0.03f, 0.35f * strength * calm, up, false);
        ps.popPose();
        for (int i = 0; i < 2; i++) {
            float f = ((time * 0.012f) + i * 0.5f) % 1f;
            ps.pushPose();
            ps.translate(-look.x * r * 0.25f, -0.4, -look.z * r * 0.25f);
            Glow.ring(c, ps, 1f + f * r * 0.9f, 0.3f + f * 0.8f, 0.8f, 0.05f, 0.08f, 0.18f * (1 - f) * strength * calm);
            ps.popPose();
        }
    }

    /** Red mist hanging in the void, turning slowly. */
    private static void mist(SubmitNodeCollector c, PoseStack ps, Quaternionf camRot, ClientState.Domain d, float r, float time, float calm) {
        Random rnd = new Random(d.id * 31L);
        for (int i = 0; i < 10; i++) {
            float a = rnd.nextFloat() * Mth.TWO_PI + time * 0.002f * (i % 2 == 0 ? 1 : -1);
            float rr = r * (0.35f + rnd.nextFloat() * 0.5f);
            float y = 1 + rnd.nextFloat() * r * 0.45f;
            ps.pushPose();
            ps.translate(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
            Glow.halo(c, ps, camRot, 3f + rnd.nextFloat() * 4f, 0.7f, 0.02f, 0.05f, 0.06f * calm);
            ps.popPose();
        }
    }

    /**
     * The sure hit: long white blades with black cores, crossing the whole domain from every angle. A new volley every
     * two ticks, each blade flashing and fading within them.
     */
    private static void slashes(SubmitNodeCollector c, PoseStack ps, ClientState.Domain d, float r, float time, float calm) {
        int batch = (int) (time / 2);
        float age = (time - batch * 2) / 2f;
        Random rnd = new Random(batch * 7919L + d.id);
        for (int i = 0; i < 7; i++) {
            Vec3 p = new Vec3((rnd.nextFloat() - 0.5f) * r * 1.2f, 0.3f + rnd.nextFloat() * r * 0.35f, (rnd.nextFloat() - 0.5f) * r * 1.2f);
            float len = r * (0.4f + rnd.nextFloat() * 0.8f);
            float yawR = rnd.nextFloat() * Mth.TWO_PI, pitch = (rnd.nextFloat() - 0.5f) * 1.6f;
            float fade = (1 - age) * calm;
            if (!DomainSpace.onSide(d, d.center.add(p))) continue;
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.rotate(Axis.YP.rotation(yawR));
            ps.rotate(Axis.XP.rotation(pitch));
            ps.translate(0, 0, -len / 2);
            Glow.beam(c, ps, len, 0.45f, 1f, 0.85f, 0.85f, 0.35f * fade);
            Glow.beam(c, ps, len, 0.14f, 1f, 1f, 1f, 0.95f * fade);
            if (i % 2 == 0) Glow.darkBeam(c, ps, len * 0.95f, 0.06f, 0.02f, 0.01f, 0.02f, 0.9f * fade);
            ps.popPose();
        }
    }

    /**
     * While it forms: black ink spreading out over the ground from the caster's feet with red embers along its edge, and
     * black smoke rolling up the rising walls (GIF frames 60-130).
     */
    static void formation(ClientState.Domain d, float progress, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LAST_FORM.getOrDefault(d.id, Long.MIN_VALUE) == now) return;
        LAST_FORM.put(d.id, now);
        if (LAST_FORM.size() > 64) LAST_FORM.clear();
        ClientLevel level = mc.level;
        Random rnd = new Random(now * 31 + d.id);
        float R = d.radius + d.thickness;
        Vec3 c0 = d.center;
        double floorY = c0.y - 0.45;
        float ground = dev.rick.jjk.core.domain.structure.DomainFormation.GROUND_END;
        if (progress < 0.2f) {
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, v = 0.06 + rnd.nextDouble() * 0.12;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, c0.x, c0.y + 0.3 + rnd.nextDouble(), c0.z,
                        Math.cos(a) * v, 0.02, Math.sin(a) * v);
            }
        }
        if (progress < ground) {
            double rad = Math.max(0.5, R * progress / ground);
            int n = (int) Math.min(28, 8 + rad * 1.6);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double x = c0.x + Math.cos(a) * rad, z = c0.z + Math.sin(a) * rad;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SQUID_INK, x, floorY + 0.15, z, Math.cos(a) * 0.05, 0.05, Math.sin(a) * 0.05);
                if (i % 3 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.CRIMSON_SPORE, x, floorY + 0.3, z, 0, 0.02, 0);
            }
        } else if (progress < dev.rick.jjk.core.domain.structure.DomainFormation.CEILING_END) {
            for (int i = 0; i < 12; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, y = rnd.nextDouble() * R * 0.8;
                double rr = Math.sqrt(Math.max(0, R * R - y * y)) - 1;
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, c0.x + Math.cos(a) * rr, c0.y + y, c0.z + Math.sin(a) * rr,
                        -Math.cos(a) * 0.04, 0.03, -Math.sin(a) * 0.04);
            }
        }
    }

    private static final Map<Integer, Long> LAST_FORM = new HashMap<>();

    /** A block model lit as if by its own light (the void around the shrine stays black). */
    private static final class Lit extends MovingBlockRenderState {
        private final int light;

        Lit(ClientLevel level, BlockPos at, BlockState state, int light) {
            this.blockPos = at;
            this.randomSeedPos = BlockPos.ZERO;
            this.blockState = state;
            this.cardinalLighting = level.cardinalLighting();
            this.lightEngine = level.getLightEngine();
            this.biome = level.getBiome(at);
            this.light = light;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return light;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darken) {
            return light;
        }
    }
}
