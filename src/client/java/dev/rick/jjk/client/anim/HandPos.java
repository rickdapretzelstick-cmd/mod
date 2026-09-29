package dev.rick.jjk.client.anim;

import dev.rick.jjk.client.anim.rig.Bone;
import dev.rick.jjk.client.anim.rig.MeshSplit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where an entity's hands (and, for the debugger, its bones) really are. Each time a humanoid model is posed, its arm
 * chains (shoulder, elbow, wrist) are recorded in model space; the renderer's own entity transform turns them back into
 * world positions, so energy held in a fist follows the fist through its wind-up and swing.
 */
public final class HandPos {
    /** Model-space transforms (pixels): each arm chain ending at the wrist, and the skeleton when it was solved. */
    private record Pose(Matrix4f right, Matrix4f left, boolean jointed, @Nullable Matrix4f[] bones, long frame) {}

    private static final Map<Integer, Pose> POSES = new HashMap<>();

    private HandPos() {}

    public static void record(int entityId, ModelPart right, ModelPart left, @Nullable Matrix4f[] bones) {
        if (POSES.size() > 256) POSES.clear();
        Matrix4f[] copy = null;
        if (bones != null) {
            copy = new Matrix4f[bones.length];
            for (int i = 0; i < bones.length; i++) copy[i] = new Matrix4f(bones[i]);
        }
        POSES.put(entityId, new Pose(chain(right), chain(left), right.hasChild(MeshSplit.LOWER), copy, frame()));
    }

    private static Matrix4f chain(ModelPart arm) {
        Matrix4f m = new Matrix4f();
        apply(m, arm);
        if (arm.hasChild(MeshSplit.LOWER)) {
            ModelPart lower = arm.getChild(MeshSplit.LOWER);
            apply(m, lower);
            if (lower.hasChild(MeshSplit.END)) apply(m, lower.getChild(MeshSplit.END));
        }
        return m;
    }

    private static void apply(Matrix4f m, ModelPart p) {
        m.translate(p.x, p.y, p.z).rotate(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot));
    }

    private static long frame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void forget(int entityId) {
        POSES.remove(entityId);
    }

    /** The renderer's model-to-world transform (model pixels in, world blocks out). */
    public static Matrix4f modelToWorld(LivingEntity e, float partial) {
        Vec3 pos = e.getPosition(partial);
        Matrix4f m = new Matrix4f().translate((float) pos.x, (float) pos.y, (float) pos.z);
        m.scale(e.getScale());
        m.rotateY((180f - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)) * Mth.DEG_TO_RAD);
        m.scale(-1, -1, 1).translate(0, -1.501f, 0);
        return m.scale(1 / 16f);
    }

    /**
     * The world position of a hand ({@code reach} blocks past the knuckles along the forearm), or the best guess from
     * the body's facing when its model hasn't been drawn lately (the local player in first person).
     */
    public static Vec3 of(LivingEntity e, float partial, boolean right, float reach) {
        Pose p = POSES.get(e.getId());
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = e == mc.player && mc.options.getCameraType().isFirstPerson();
        if (p == null || firstPerson || frame() - p.frame > 3) return guess(e, partial, right, reach);
        Matrix4f m = modelToWorld(e, partial).mul(right ? p.right : p.left);
        // The fist's centre: past the wrist joint on a jointed arm, near the end of the arm on a plain one.
        Vector3f fist = p.jointed ? new Vector3f(0, 1.5f + reach * 16, 0) : new Vector3f(right ? -1 : 1, 9.5f + reach * 16, 0);
        Vector3f w = m.transformPosition(fist);
        return new Vec3(w.x, w.y, w.z);
    }

    /** World positions of every bone's joint, or null when the skeleton wasn't solved for this entity lately. */
    @Nullable
    public static Vec3[] bones(LivingEntity e, float partial) {
        Pose p = POSES.get(e.getId());
        if (p == null || p.bones == null || frame() - p.frame > 3) return null;
        Matrix4f m = modelToWorld(e, partial);
        Vec3[] out = new Vec3[Bone.COUNT];
        for (Bone b : Bone.ALL) {
            Vector3f v = new Matrix4f(m).mul(p.bones[b.ordinal()]).transformPosition(new Vector3f());
            out[b.ordinal()] = new Vec3(v.x, v.y, v.z);
        }
        return out;
    }

    /** A bone's world axes (right, up, forward of the bone), scaled to {@code len} blocks, or null. */
    @Nullable
    public static Vec3[] axes(LivingEntity e, float partial, Bone b, float len) {
        Pose p = POSES.get(e.getId());
        if (p == null || p.bones == null) return null;
        Matrix4f m = modelToWorld(e, partial).mul(p.bones[b.ordinal()]);
        Vector3f o = m.transformPosition(new Vector3f());
        float px = len * 16;
        Vector3f r = m.transformPosition(new Vector3f(-px, 0, 0)).sub(o);
        Vector3f u = m.transformPosition(new Vector3f(0, -px, 0)).sub(o);
        Vector3f f = m.transformPosition(new Vector3f(0, 0, -px)).sub(o);
        return new Vec3[]{new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), new Vec3(f.x, f.y, f.z)};
    }

    /** A fist held in front of the chest, from the body's facing and the look direction. */
    private static Vec3 guess(LivingEntity e, float partial, boolean right, float reach) {
        float yaw = e.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(right ? 0.4 : -0.4);
        Vec3 look = e.getViewVector(partial);
        return e.getEyePosition(partial).add(look.scale(0.55 + reach)).add(side).add(0, -0.35, 0);
    }
}
