package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.anim.Part;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.anim.PoseKeys;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    /** Applies attack/technique/reaction poses on top of vanilla's animation. */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void jjk$apply(HumanoidRenderState state, CallbackInfo ci) {
        PoseFrame f = state.getData(PoseKeys.FRAME);
        if (f != null) {
            apply(head, f, Part.HEAD);
            apply(body, f, Part.BODY);
            apply(rightArm, f, Part.RIGHT_ARM);
            apply(leftArm, f, Part.LEFT_ARM);
            apply(rightLeg, f, Part.RIGHT_LEG);
            apply(leftLeg, f, Part.LEFT_LEG);
            if (f.weight[Part.BODY.ordinal()] > 0) {
                // The shoulders ride the torso's twist, so a punch thrown with the body turns the arm with it.
                float twist = body.yRot;
                rightArm.z = Mth.sin(twist) * 5f;
                rightArm.x = -Mth.cos(twist) * 5f;
                leftArm.z = -Mth.sin(twist) * 5f;
                leftArm.x = Mth.cos(twist) * 5f;
                rightArm.yRot += twist;
                leftArm.yRot += twist;
            }
        }
        Integer id = state.getData(PoseKeys.ENTITY);
        if (id != null) dev.rick.jjk.client.anim.HandPos.record(id, rightArm, leftArm);
    }

    private static void apply(ModelPart part, PoseFrame f, Part p) {
        float w = f.weight[p.ordinal()];
        if (w <= 0) return;
        float[] r = f.rot[p.ordinal()];
        part.xRot = Mth.lerp(w, part.xRot, r[0]);
        part.yRot = Mth.lerp(w, part.yRot, r[1]);
        part.zRot = Mth.lerp(w, part.zRot, r[2]);
    }
}
