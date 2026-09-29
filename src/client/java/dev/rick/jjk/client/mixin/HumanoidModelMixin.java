package dev.rick.jjk.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.anim.HandPos;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.anim.PoseKeys;
import dev.rick.jjk.client.anim.rig.Bone;
import dev.rick.jjk.client.anim.rig.JointedArm;
import dev.rick.jjk.client.anim.rig.Rig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Unique private final Matrix4f[] jjk$bones = new Matrix4f[Bone.COUNT];

    /** Poses the skeleton from the playing clips, on top of vanilla's own animation. */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void jjk$apply(HumanoidRenderState state, CallbackInfo ci) {
        PoseFrame f = state.getData(PoseKeys.FRAME);
        Rig.Parts parts = Rig.parts((HumanoidModel<?>) (Object) this);
        boolean solved = false;
        if (f != null) {
            Rig.solve(parts, f, jjk$bones);
            solved = true;
        } else if (dev.rick.jjk.client.anim.AnimDebug.wantsSkeleton(state.getDataOrDefault(PoseKeys.ENTITY, Integer.MIN_VALUE))) {
            // The debugger's skeleton needs the bones even with nothing playing; solving an empty frame is vanilla.
            Rig.solve(parts, new PoseFrame(), jjk$bones);
            solved = true;
        }
        Integer id = state.getData(PoseKeys.ENTITY);
        if (id != null) HandPos.record(id, rightArm, leftArm, solved ? jjk$bones : null);
    }

    /** Items held in a hand follow the forearm and wrist, not just the upper arm. */
    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void jjk$hand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        JointedArm.apply(arm == HumanoidArm.LEFT ? leftArm : rightArm, poseStack);
    }
}
