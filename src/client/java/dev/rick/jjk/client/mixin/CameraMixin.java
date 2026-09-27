package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.fx.ScreenEffects;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** Screen shake: offset the view after vanilla positions the camera. */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void jjk$shake(float partialTicks, CallbackInfo ci) {
        float dy = ScreenEffects.yawOffset(partialTicks), dx = ScreenEffects.pitchOffset(partialTicks);
        if (dy != 0 || dx != 0) setRotation(yRot + dy, xRot + dx);
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void jjk$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float m = ScreenEffects.fovMultiplier(partialTicks);
        if (m != 1f) cir.setReturnValue(cir.getReturnValue() * m);
    }
}
