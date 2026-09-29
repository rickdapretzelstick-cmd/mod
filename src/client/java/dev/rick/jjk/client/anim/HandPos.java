package dev.rick.jjk.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where an entity's hands really are. Each time a humanoid model is posed its arms are recorded here, and the same
 * transforms the renderer uses (body turn, the animation's root motion, the model flip, the arm's pivot and rotation)
 * turn them back into world positions, so energy held in a fist follows the fist through its wind-up and swing.
 */
public final class HandPos {
    private record Arms(float[] right, float[] left, long frame) {}

    private static final Map<Integer, Arms> ARMS = new HashMap<>();
    /** Hips height the root motion turns about (blocks). */
    static final float ROOT_PIVOT = 0.9f;

    private HandPos() {}

    public static void record(int entityId, ModelPart right, ModelPart left) {
        if (ARMS.size() > 256) ARMS.clear();
        ARMS.put(entityId, new Arms(part(right), part(left), frame()));
    }

    private static float[] part(ModelPart p) {
        return new float[] {p.x, p.y, p.z, p.xRot, p.yRot, p.zRot};
    }

    private static long frame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void forget(int entityId) {
        ARMS.remove(entityId);
    }

    /** The animation's whole-body motion, applied right after the body's own turn (forward is +Z, left is +X here). */
    public static void applyRoot(PoseStack ps, PoseFrame f) {
        float wp = f.weight[Part.ROOT_POS.ordinal()], wr = f.weight[Part.ROOT.ordinal()];
        if (wp > 0) {
            float[] p = f.rot[Part.ROOT_POS.ordinal()];
            ps.translate(-p[0] / 16f * wp, p[1] / 16f * wp, p[2] / 16f * wp);
        }
        if (wr > 0) {
            float[] r = f.rot[Part.ROOT.ordinal()];
            ps.translate(0, ROOT_PIVOT, 0);
            ps.rotate(Axis.YP.rotation(r[1] * wr));
            ps.rotate(Axis.XP.rotation(r[0] * wr));
            ps.rotate(Axis.ZP.rotation(r[2] * wr));
            ps.translate(0, -ROOT_PIVOT, 0);
        }
    }

    private static void applyRoot(Matrix4f m, PoseFrame f) {
        float wp = f.weight[Part.ROOT_POS.ordinal()], wr = f.weight[Part.ROOT.ordinal()];
        if (wp > 0) {
            float[] p = f.rot[Part.ROOT_POS.ordinal()];
            m.translate(-p[0] / 16f * wp, p[1] / 16f * wp, p[2] / 16f * wp);
        }
        if (wr > 0) {
            float[] r = f.rot[Part.ROOT.ordinal()];
            m.translate(0, ROOT_PIVOT, 0);
            m.rotateY(r[1] * wr).rotateX(r[0] * wr).rotateZ(r[2] * wr);
            m.translate(0, -ROOT_PIVOT, 0);
        }
    }

    /**
     * The world position of a hand ({@code reach} blocks past the knuckles along the arm), or the best guess from the
     * body's facing when its model hasn't been drawn lately (the local player in first person).
     */
    public static Vec3 of(LivingEntity e, float partial, boolean right, float reach) {
        Arms a = ARMS.get(e.getId());
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = e == mc.player && mc.options.getCameraType().isFirstPerson();
        if (a == null || firstPerson || frame() - a.frame > 3) return guess(e, partial, right, reach);
        float[] arm = right ? a.right : a.left;
        Vec3 pos = e.getPosition(partial);
        Matrix4f m = new Matrix4f().translate((float) pos.x, (float) pos.y, (float) pos.z);
        m.scale(e.getScale());
        m.rotateY((180f - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)) * Mth.DEG_TO_RAD);
        PoseFrame f = ClientAnimations.compute(e, e.level().getGameTime() + partial);
        if (f != null) {
            if (f.lieDown > 0) {
                m.translate(0, 0.25f * f.lieDown, 0);
                m.rotateX(-90f * f.lieDown * Mth.DEG_TO_RAD);
            }
            applyRoot(m, f);
        }
        m.scale(-1, -1, 1).translate(0, -1.501f, 0);
        m.translate(arm[0] / 16f, arm[1] / 16f, arm[2] / 16f);
        m.rotate(new Quaternionf().rotationZYX(arm[5], arm[4], arm[3]));
        Vector3f hand = m.transformPosition(new Vector3f(right ? -1f / 16f : 1f / 16f, 9.5f / 16f + reach, 0));
        return new Vec3(hand.x, hand.y, hand.z);
    }

    /** A fist held in front of the chest, from the body's facing and the look direction. */
    private static Vec3 guess(LivingEntity e, float partial, boolean right, float reach) {
        float yaw = e.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(right ? 0.4 : -0.4);
        Vec3 look = e.getViewVector(partial);
        return e.getEyePosition(partial).add(look.scale(0.55 + reach)).add(side).add(0, -0.35, 0);
    }
}
