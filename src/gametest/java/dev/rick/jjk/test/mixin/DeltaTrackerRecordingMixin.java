package dev.rick.jjk.test.mixin;

import dev.rick.jjk.test.ShowcaseCamera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a recording pins the sub-tick moment, every reader of the live partial tick sees that moment too. */
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerRecordingMixin {
    @Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
    private void jjk$pinned(boolean ignoreFrozen, CallbackInfoReturnable<Float> cir) {
        if (ShowcaseCamera.pinnedPartial >= 0) cir.setReturnValue(ShowcaseCamera.pinnedPartial);
    }
}
