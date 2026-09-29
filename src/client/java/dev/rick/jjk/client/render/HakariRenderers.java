package dev.rick.jjk.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.entity.HakariDoorEntity;
import dev.rick.jjk.entity.PachinkoBallEntity;
import dev.rick.jjk.registry.ModBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Hakari's manifestations, drawn from real block models (see tools/gen_hakari_assets.py) so they belong in a
 * Minecraft world: steel pachinko balls that tumble as they fly, corrugated steel shutters with a pink hazard strip,
 * and the red lacquer gamble door of Door Guard.
 */
public final class HakariRenderers {
    private HakariRenderers() {}

    public static class State extends EntityRenderState {
        public final MovingBlockRenderState block = new MovingBlockRenderState();
        public float age;
        public float scale = 1f;
        public float yaw;
        public float open;
        public int kind;
        public Vec3 velocity = Vec3.ZERO;
    }

    public static void light(Entity e, State s, BlockState state) {
        BlockPos pos = e.blockPosition().above();
        s.block.randomSeedPos = BlockPos.ZERO;
        s.block.blockPos = pos;
        s.block.blockState = state;
        if (e.level() instanceof ClientLevel cl) {
            s.block.biome = cl.getBiome(pos);
            s.block.cardinalLighting = cl.cardinalLighting();
            s.block.lightEngine = cl.getLightEngine();
        }
    }

    /** A Reserve Ball: a tumbling steel ball with a chrome glint and a short pink streak behind it. */
    public static class Ball extends EntityRenderer<PachinkoBallEntity, State> {
        public Ball(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRender(PachinkoBallEntity e, Frustum culler, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(PachinkoBallEntity e, State s, float partial) {
            super.extractRenderState(e, s, partial);
            s.age = e.tickCount + partial;
            s.scale = e.scale();
            s.velocity = e.getDeltaMovement();
            light(e, s, ModBlocks.PACHINKO_BALL.defaultBlockState());
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
            ps.pushPose();
            float k = 0.9f * s.scale;
            ps.translate(0, 0.18, 0);
            ps.scale(k, k, k);
            ps.rotate(Axis.XP.rotationDegrees(s.age * 47));
            ps.rotate(Axis.YP.rotationDegrees(s.age * 31));
            ps.translate(-0.5, -0.5, -0.5);
            c.submitMovingBlock(ps, s.block, s.outlineColor);
            ps.popPose();
            // The glint and the streak (additive light over the solid model).
            ps.pushPose();
            ps.translate(0, 0.18, 0);
            Vector3f toCam = new Vector3f((float) (cam.pos.x - s.x), (float) (cam.pos.y - s.y), (float) (cam.pos.z - s.z));
            Glow.sphere(c, ps, 0.22f * s.scale, 1f, 0.55f, 0.8f, 0.35f, toCam, true);
            Vec3 v = s.velocity;
            float len = (float) v.length();
            if (len > 0.05f) {
                Vec3 n = v.scale(-1 / len);
                ps.rotate(Axis.YP.rotation((float) Math.atan2(n.x, n.z)));
                ps.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(-n.y, -1, 1))));
                Glow.beam(c, ps, Math.min(2.2f, len * 1.3f), 0.12f * s.scale, 1f, 0.35f, 0.7f, 0.55f);
                Glow.beam(c, ps, Math.min(1.4f, len * 0.8f), 0.05f * s.scale, 1f, 1f, 1f, 0.7f);
            }
            ps.popPose();
            super.submit(s, ps, c, cam);
        }
    }

    /**
     * A door: Shutter Doors' steel shutters (2 blocks wide, nearly 3 tall) or Door Guard's gamble door, which can swing
     * open on its hinge for the counter.
     */
    public static class Door extends EntityRenderer<HakariDoorEntity, State> {
        public Door(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRender(HakariDoorEntity e, Frustum culler, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(HakariDoorEntity e, State s, float partial) {
            super.extractRenderState(e, s, partial);
            s.age = e.tickCount + partial;
            s.kind = e.kind();
            s.yaw = e.getYRot(partial);
            s.open = e.open();
            light(e, s, (s.kind == HakariDoorEntity.GUARD ? ModBlocks.GAMBLE_DOOR : ModBlocks.SHUTTER_PANEL).defaultBlockState());
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
            ps.pushPose();
            ps.rotate(Axis.YP.rotationDegrees(-s.yaw));
            if (s.kind == HakariDoorEntity.GUARD) {
                // Door Guard (JJS GIF): a grey double door in front of Hakari. It grows out of nothing, and for the counter
                // both leaves burst open outward (toward the attacker) on their outer hinges.
                float grow = Mth.clamp(s.age / 3f, 0, 1);
                ps.scale(1, grow, 1);
                for (int side = -1; side <= 1; side += 2) {
                    ps.pushPose();
                    ps.translate(side * 0.8f, 0, 0); // the hinge
                    ps.rotate(Axis.YP.rotationDegrees(side * 105 * s.open));
                    ps.translate(-side * 0.4f, 0, 0);
                    leaf(ps, c, s, 0.8f, 2.4f);
                    ps.popPose();
                }
            } else if (s.kind == HakariDoorEntity.UPRIGHT) {
                // Fever Breaker's doors: one leaf of a standing double door behind the target.
                leaf(ps, c, s, 1.0f, 2.4f);
            } else {
                // Shutter doors lie completely flat on the floor: the panel's face turned up, its length along the door's
                // facing, its underside at the entity's feet. Same size as its hitbox (HakariDoorEntity.SHUTTER_*).
                float w = HakariDoorEntity.SHUTTER_WIDTH, h = HakariDoorEntity.SHUTTER_LENGTH, t = HakariDoorEntity.SHUTTER_THICKNESS;
                ps.translate(0, t / 2, -h / 2);
                ps.rotate(Axis.XP.rotationDegrees(90));
                ps.scale(w, h, t / 0.125f);
                ps.translate(-0.5, 0, -0.5);
                c.submitMovingBlock(ps, s.block, s.outlineColor);
            }
            ps.popPose();
            super.submit(s, ps, c, cam);
        }

        /** One upright door leaf, centred on the current origin (the block model is a thin slab across Z). */
        private static void leaf(PoseStack ps, SubmitNodeCollector c, State s, float w, float h) {
            ps.pushPose();
            ps.scale(w, h, 1);
            ps.translate(-0.5, 0, -0.5);
            c.submitMovingBlock(ps, s.block, s.outlineColor);
            ps.popPose();
        }
    }

    public static EntityRendererProvider<PachinkoBallEntity> ball() {
        return Ball::new;
    }

    public static EntityRendererProvider<HakariDoorEntity> door() {
        return Door::new;
    }
}
