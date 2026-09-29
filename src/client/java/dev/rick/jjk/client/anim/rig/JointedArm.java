package dev.rick.jjk.client.anim.rig;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

/** Carries a held item through the elbow and wrist, keeping vanilla's item placement relative to the arm. */
public final class JointedArm {
    private JointedArm() {}

    /**
     * Applies T(elbow) R(elbow) T(wrist) R(wrist) T(-wrist) T(-elbow): each joint turns the frame about its own pivot,
     * so at rest nothing moves and vanilla's offsets from the shoulder still land the item in the fist.
     */
    public static void apply(ModelPart arm, PoseStack ps) {
        if (!arm.hasChild(MeshSplit.LOWER)) return;
        ModelPart lower = arm.getChild(MeshSplit.LOWER);
        lower.translateAndRotate(ps);
        if (lower.hasChild(MeshSplit.END)) {
            ModelPart end = lower.getChild(MeshSplit.END);
            end.translateAndRotate(ps);
            ps.translate(-end.x / 16f, -end.y / 16f, -end.z / 16f);
        }
        ps.translate(-lower.x / 16f, -lower.y / 16f, -lower.z / 16f);
    }
}
