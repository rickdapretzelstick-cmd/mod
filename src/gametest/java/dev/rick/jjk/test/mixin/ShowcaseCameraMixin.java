package dev.rick.jjk.test.mixin;

import dev.rick.jjk.test.ShowcaseCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the presentation recording place the camera (see {@link ShowcaseCamera}). */
@Mixin(value = Camera.class, priority = 900)
public abstract class ShowcaseCameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void jjkTest$showcase(float partialTicks, CallbackInfo ci) {
        double[] s = ShowcaseCamera.apply(partialTicks);
        if (s == null) return;
        setPosition(new Vec3(s[0], s[1], s[2]));
        setRotation((float) s[3], (float) s[4]);
    }
}
