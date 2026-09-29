package dev.rick.jjk.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.anim.PoseFrame;
import dev.rick.jjk.client.anim.PoseKeys;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** Knockdown (the whole body tips over onto its back), then the animation's whole-body lean, turn and step. */
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void jjk$knockdown(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        PoseFrame f = state.getData(PoseKeys.FRAME);
        if (f == null) return;
        if (f.lieDown > 0) {
            poseStack.translate(0, 0.25f * f.lieDown, 0);
            poseStack.rotate(Axis.XP.rotationDegrees(-90f * f.lieDown));
        }
        dev.rick.jjk.client.anim.HandPos.applyRoot(poseStack, f);
    }
}
