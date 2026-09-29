package dev.rick.jjk.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rick.jjk.client.anim.rig.JointedArm;
import dev.rick.jjk.client.anim.rig.Rig;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    /** The skin's overlay pieces on the forearms, shins and belly show or hide with the overlay they belong to. */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void jjk$overlays(AvatarRenderState state, CallbackInfo ci) {
        Rig.syncOverlays(Rig.parts((PlayerModel) (Object) this));
    }

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void jjk$hand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        PlayerModel self = (PlayerModel) (Object) this;
        JointedArm.apply(arm == HumanoidArm.LEFT ? self.leftArm : self.rightArm, poseStack);
    }
}
