package dev.rick.jjk.client.mixin;

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

    @Shadow
    protected abstract void setPosition(net.minecraft.world.phys.Vec3 position);

    @Shadow
    public abstract net.minecraft.world.phys.Vec3 position();

    /** Every cinematic camera, then the screen shake: decided by the camera director after vanilla places the view. */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void jjk$shake(float partialTicks, CallbackInfo ci) {
        // Never shake the view under the rifle's scope: what's centred is what's hit.
        double[] r = dev.rick.jjk.client.camera.CameraDirector.apply(position(), yRot, xRot, partialTicks, dev.rick.jjk.client.rifle.RifleScope.steady());
        if (r == null) return;
        if (r[5] != 0) setPosition(new net.minecraft.world.phys.Vec3(r[0], r[1], r[2]));
        setRotation((float) r[3], (float) r[4]);
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void jjk$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValue();
        float f = dev.rick.jjk.client.camera.CameraDirector.fov(vanilla, partialTicks, dev.rick.jjk.client.rifle.RifleClient.zoom());
        if (f != vanilla) cir.setReturnValue(f);
    }
}
