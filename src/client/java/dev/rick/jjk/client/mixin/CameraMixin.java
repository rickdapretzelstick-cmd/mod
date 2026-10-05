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

    @Shadow
    protected abstract void setPosition(net.minecraft.world.phys.Vec3 position);

    @Shadow
    public abstract net.minecraft.world.phys.Vec3 position();

    /** Screen shake: offset the view after vanilla positions the camera. */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void jjk$shake(float partialTicks, CallbackInfo ci) {
        // Domain clash: frame the two domains and where they collide.
        double[] clash = dev.rick.jjk.client.clash.ClashCamera.apply(position(), yRot, xRot);
        if (clash != null) {
            setPosition(new net.minecraft.world.phys.Vec3(clash[0], clash[1], clash[2]));
            setRotation((float) clash[3], (float) clash[4]);
        }
        // The lodge's mounted scope: the view is from its front lens (past the scope's own body).
        net.minecraft.world.phys.Vec3 scope = dev.rick.jjk.client.rifle.RifleClient.scopeEye();
        if (scope != null) setPosition(scope);
        // Beam clash: a brief side-on shot of the two beams meeting.
        double[] beams = dev.rick.jjk.client.clash.BeamClashCamera.apply(position(), yRot, xRot);
        if (beams != null) {
            setPosition(new net.minecraft.world.phys.Vec3(beams[0], beams[1], beams[2]));
            setRotation((float) beams[3], (float) beams[4]);
        }
        // The Prison Realm's outside view: around the grounded realm, watching only (last: it is a different place).
        double[] prison = dev.rick.jjk.client.prison.PrisonClient.apply(yRot, xRot);
        if (prison != null) {
            setPosition(new net.minecraft.world.phys.Vec3(prison[0], prison[1], prison[2]));
            setRotation((float) prison[3], (float) prison[4]);
            return;
        }
        float dy = ScreenEffects.yawOffset(partialTicks), dx = ScreenEffects.pitchOffset(partialTicks);
        if (dy != 0 || dx != 0) setRotation(yRot + dy, xRot + dx);
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void jjk$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float m = ScreenEffects.fovMultiplier(partialTicks) * dev.rick.jjk.client.rifle.RifleClient.zoom();
        if (m != 1f) cir.setReturnValue(cir.getReturnValue() * m);
    }
}
