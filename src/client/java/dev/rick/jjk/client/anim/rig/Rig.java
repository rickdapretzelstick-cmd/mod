package dev.rick.jjk.client.anim.rig;

import dev.rick.jjk.client.anim.Clip;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.mixin.rig.ModelPartAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Poses a humanoid model from the skeleton. Each bone's world transform is its parent's times its own local pose (all
 * in model space, pixels, y down), and each model part is then set to exactly the transform its bone asks for,
 * relative to the part it hangs off. Bones the playing clips leave alone keep the pose vanilla gave them this frame,
 * so walking, looking and swinging carry on underneath a partial animation.
 */
public final class Rig {
    /** A model part driven by a bone. */
    private record Mesh(ModelPart part, @Nullable Mesh parent, Bone bone, Vector3f offset) {}

    /** The parts of one model, found once. */
    public static final class Parts {
        final ModelPart head, body;
        @Nullable final ModelPart bodyLower;
        final ModelPart[] arm = new ModelPart[2], fore = new ModelPart[2], hand = new ModelPart[2];
        final ModelPart[] leg = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
        final Vector3f[] rest = new Vector3f[Bone.COUNT];
        final List<Mesh> meshes = new ArrayList<>();
        /** (vanilla overlay, a piece of it on a lower segment): the piece copies the overlay's visibility. */
        final List<ModelPart[]> overlays = new ArrayList<>();
        public final boolean jointed;

        Parts(HumanoidModel<?> m) {
            head = m.head;
            body = m.body;
            bodyLower = child(body, MeshSplit.LOWER);
            arm[0] = m.rightArm;
            arm[1] = m.leftArm;
            leg[0] = m.rightLeg;
            leg[1] = m.leftLeg;
            for (int s = 0; s < 2; s++) {
                fore[s] = child(arm[s], MeshSplit.LOWER);
                hand[s] = fore[s] == null ? null : child(fore[s], MeshSplit.END);
                shin[s] = child(leg[s], MeshSplit.LOWER);
                foot[s] = shin[s] == null ? null : child(shin[s], MeshSplit.END);
            }
            jointed = fore[0] != null && shin[0] != null;

            PartPose hp = head.getInitialPose(), bp = body.getInitialPose();
            PartPose lp = leg[0].getInitialPose();
            rest[Bone.ROOT.ordinal()] = new Vector3f(0, lp.y() + 12, 0);
            rest[Bone.HIPS.ordinal()] = new Vector3f(0, lp.y(), 0);
            float waist = bodyLower != null ? bodyLower.getInitialPose().y() : 12;
            rest[Bone.CHEST.ordinal()] = new Vector3f(bp.x(), bp.y() + waist, bp.z());
            rest[Bone.NECK.ordinal()] = new Vector3f(bp.x(), bp.y(), bp.z());
            rest[Bone.HEAD.ordinal()] = new Vector3f(hp.x(), hp.y(), hp.z());
            Bone[][] armBones = {{Bone.RIGHT_ARM, Bone.RIGHT_FOREARM, Bone.RIGHT_HAND}, {Bone.LEFT_ARM, Bone.LEFT_FOREARM, Bone.LEFT_HAND}};
            Bone[][] legBones = {{Bone.RIGHT_THIGH, Bone.RIGHT_SHIN, Bone.RIGHT_FOOT}, {Bone.LEFT_THIGH, Bone.LEFT_SHIN, Bone.LEFT_FOOT}};
            for (int s = 0; s < 2; s++) {
                chainRest(armBones[s], arm[s], fore[s], hand[s]);
                chainRest(legBones[s], leg[s], shin[s], foot[s]);
            }

            Mesh h = new Mesh(head, null, Bone.HEAD, new Vector3f());
            meshes.add(h);
            Mesh b = new Mesh(body, null, Bone.CHEST, sub(new Vector3f(bp.x(), bp.y(), bp.z()), rest[Bone.CHEST.ordinal()]));
            meshes.add(b);
            if (bodyLower != null) {
                PartPose lo = bodyLower.getInitialPose();
                meshes.add(new Mesh(bodyLower, b, Bone.HIPS, sub(new Vector3f(bp.x() + lo.x(), bp.y() + lo.y(), bp.z() + lo.z()), rest[Bone.HIPS.ordinal()])));
            }
            for (int s = 0; s < 2; s++) {
                chainMesh(armBones[s], arm[s], fore[s], hand[s]);
                chainMesh(legBones[s], leg[s], shin[s], foot[s]);
            }

            // Overlay pieces riding the lower segments (sleeves, pants, jacket).
            for (ModelPart top : new ModelPart[]{body, arm[0], arm[1], leg[0], leg[1]}) {
                for (var e : ((ModelPartAccessor) (Object) top).jjk$children().entrySet()) {
                    if (e.getKey().startsWith("jjk_")) continue;
                    ModelPart lower = child(top, MeshSplit.LOWER);
                    while (lower != null) {
                        ModelPart piece = child(lower, e.getKey());
                        if (piece != null) overlays.add(new ModelPart[]{e.getValue(), piece});
                        lower = child(lower, MeshSplit.END);
                    }
                }
            }
        }

        private void chainRest(Bone[] bones, ModelPart top, @Nullable ModelPart mid, @Nullable ModelPart end) {
            PartPose p = top.getInitialPose();
            Vector3f a = new Vector3f(p.x(), p.y(), p.z());
            rest[bones[0].ordinal()] = a;
            Vector3f m = mid != null ? new Vector3f(mid.getInitialPose().x(), mid.getInitialPose().y(), mid.getInitialPose().z()) : new Vector3f(0, 6, 0);
            rest[bones[1].ordinal()] = new Vector3f(a).add(m);
            Vector3f e = end != null ? new Vector3f(end.getInitialPose().x(), end.getInitialPose().y(), end.getInitialPose().z()) : new Vector3f(0, 4, 0);
            rest[bones[2].ordinal()] = new Vector3f(rest[bones[1].ordinal()]).add(e);
        }

        private void chainMesh(Bone[] bones, ModelPart top, @Nullable ModelPart mid, @Nullable ModelPart end) {
            Mesh a = new Mesh(top, null, bones[0], new Vector3f());
            meshes.add(a);
            if (mid == null) return;
            Mesh b = new Mesh(mid, a, bones[1], new Vector3f());
            meshes.add(b);
            if (end != null) meshes.add(new Mesh(end, b, bones[2], new Vector3f()));
        }

        /** Model-space rest pivot of a bone (pixels, y down). */
        public Vector3f rest(Bone b) {
            return rest[b.ordinal()];
        }
    }

    private static final Map<HumanoidModel<?>, Parts> PARTS = new WeakHashMap<>();

    private Rig() {}

    public static Parts parts(HumanoidModel<?> model) {
        return PARTS.computeIfAbsent(model, Parts::new);
    }

    @Nullable
    private static ModelPart child(ModelPart p, String name) {
        return p.hasChild(name) ? p.getChild(name) : null;
    }

    private static Vector3f sub(Vector3f a, Vector3f b) {
        return a.sub(b);
    }

    /** Copies the vanilla overlays' visibility (skin customisation) onto their pieces on the lower segments. */
    public static void syncOverlays(Parts parts) {
        for (ModelPart[] o : parts.overlays) o[1].visible = o[0].visible;
    }

    /** Puts every lower segment back at rest (the first-person arm draws the model's arm straight). */
    public static void resetSegments(Parts parts) {
        for (ModelPart p : new ModelPart[]{parts.fore[0], parts.fore[1], parts.hand[0], parts.hand[1], parts.shin[0], parts.shin[1],
                parts.foot[0], parts.foot[1], parts.bodyLower}) {
            if (p != null) p.resetPose();
        }
    }

    /**
     * Solves the skeleton for {@code f} over the model's current (vanilla) pose and writes it into the parts.
     * {@code world} receives each bone's model-space transform.
     */
    public static void solve(Parts p, PoseFrame f, Matrix4f[] world) {
        // Vanilla's pose, expressed as local bone rotations so an unanimated bone looks exactly as vanilla has it.
        Quaternionf body = q(p.body), bodyInv = new Quaternionf(body).invert();
        Quaternionf headWorld = q(p.head);
        float[][] base = new float[Bone.COUNT][3];
        euler(p.body, base[Bone.CHEST.ordinal()]);
        if (p.body.xRot == 0 && p.body.yRot == 0 && p.body.zRot == 0) {
            // The usual case: take vanilla's angles as they are (no re-derived, wrapped equivalents to blend across).
            euler(p.head, base[Bone.HEAD.ordinal()]);
            euler(p.arm[0], base[Bone.RIGHT_ARM.ordinal()]);
            euler(p.arm[1], base[Bone.LEFT_ARM.ordinal()]);
        } else {
            euler(new Quaternionf(bodyInv).mul(headWorld), base[Bone.HEAD.ordinal()]);
            euler(new Quaternionf(bodyInv).mul(q(p.arm[0])), base[Bone.RIGHT_ARM.ordinal()]);
            euler(new Quaternionf(bodyInv).mul(q(p.arm[1])), base[Bone.LEFT_ARM.ordinal()]);
        }
        euler(p.leg[0], base[Bone.RIGHT_THIGH.ordinal()]);
        euler(p.leg[1], base[Bone.LEFT_THIGH.ordinal()]);

        float[] rot = new float[3], pos = new float[3];
        float[][] scale = new float[Bone.COUNT][3];
        Quaternionf tmpQ = new Quaternionf();
        for (Bone b : Bone.ALL) {
            int i = b.ordinal();
            System.arraycopy(base[i], 0, rot, 0, 3);
            Matrix4f parent = b.parent == null ? new Matrix4f() : world[b.parent.ordinal()];
            if (b == Bone.HEAD && f.look > 0) {
                // Keep looking where the player looks, whatever the body is doing (only under the clip's own keys).
                Quaternionf parentRot = parent.getUnnormalizedRotation(tmpQ).invert();
                float[] look = new float[3];
                euler(parentRot.mul(headWorld), look);
                for (int k = 0; k < 3; k++) rot[k] += (look[k] - rot[k]) * f.look;
            }
            float wr = f.weight[Clip.ROT][i], wp = f.weight[Clip.POS][i], ws = f.weight[Clip.SCALE][i];
            for (int k = 0; k < 3; k++) {
                rot[k] += (f.value[Clip.ROT][i][k] - rot[k]) * wr;
                pos[k] = f.value[Clip.POS][i][k] * wp;
                scale[i][k] = 1 + (f.value[Clip.SCALE][i][k] - 1) * ws;
            }
            Vector3f r = p.rest[i];
            Vector3f pr = b.parent == null ? new Vector3f() : p.rest[b.parent.ordinal()];
            // Authored offsets are (right, up, forward); model space has right at -x, up at -y and forward at -z.
            Matrix4f m = world[i] == null ? (world[i] = new Matrix4f()) : world[i];
            m.set(parent).translate(r.x - pr.x - pos[0], r.y - pr.y - pos[1], r.z - pr.z - pos[2])
                    .rotate(tmpQ.rotationZYX(rot[2], rot[1], rot[0]));
        }

        // Each part: its bone's transform, relative to the part it hangs off.
        Matrix4f[] meshWorld = new Matrix4f[p.meshes.size()];
        Vector3f t = new Vector3f(), e = new Vector3f();
        for (int k = 0; k < p.meshes.size(); k++) {
            Mesh mesh = p.meshes.get(k);
            Matrix4f mw = meshWorld[k] = new Matrix4f(world[mesh.bone.ordinal()]).translate(mesh.offset);
            Matrix4f local = mw;
            float[] sc = scale[mesh.bone.ordinal()];
            float sx = sc[0], sy = sc[1], sz = sc[2];
            if (mesh.parent != null) {
                int pi = p.meshes.indexOf(mesh.parent);
                local = new Matrix4f(meshWorld[pi]).invert().mul(mw);
                float[] ps = scale[mesh.parent.bone.ordinal()];
                local.getTranslation(t);
                t.div(ps[0], ps[1], ps[2]);
                sx /= ps[0];
                sy /= ps[1];
                sz /= ps[2];
            } else {
                local.getTranslation(t);
            }
            local.getEulerAnglesZYX(e);
            ModelPart part = mesh.part;
            part.x = t.x;
            part.y = t.y;
            part.z = t.z;
            part.xRot = e.x;
            part.yRot = e.y;
            part.zRot = e.z;
            part.xScale = sx;
            part.yScale = sy;
            part.zScale = sz;
        }
    }

    private static Quaternionf q(ModelPart p) {
        return new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot);
    }

    private static void euler(ModelPart p, float[] out) {
        out[0] = p.xRot;
        out[1] = p.yRot;
        out[2] = p.zRot;
    }

    private static void euler(Quaternionf q, float[] out) {
        Vector3f v = new Matrix4f().rotation(q).getEulerAnglesZYX(new Vector3f());
        out[0] = v.x;
        out[1] = v.y;
        out[2] = v.z;
    }
}
