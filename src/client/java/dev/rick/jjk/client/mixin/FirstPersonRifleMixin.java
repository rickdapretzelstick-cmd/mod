package dev.rick.jjk.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rick.jjk.client.rifle.RifleFirstPerson;
import dev.rick.jjk.client.rifle.RifleStance;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person with the Cursed Rifle drawn: both arms on the rifle, drawn by {@link RifleFirstPerson} instead of the hands. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRifleMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void jjk$rifle(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
                           FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        var avatar = playerState.avatarRenderState;
        RifleStance.View v = avatar == null ? null : avatar.getData(RifleStance.VIEW);
        if (v == null) return;
        ci.cancel();
        // The same lag of the hands behind the view that vanilla gives them.
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.XP, (state.viewXRot - state.xBob) * 0.1F);
        poseStack.rotateDegrees(Axis.YP, (state.viewYRot - state.yBob) * 0.1F);
        RifleFirstPerson.submit(poseStack, collector, avatar, v, avatar.lightCoords);
        poseStack.popPose();
    }
}
