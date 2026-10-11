package dev.rick.jjk.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Adds this frame's impact frame / slowed-time post effects ({@link dev.rick.jjk.client.fx.ImpactFrames}). */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostMixin {
    @Shadow @Final private List<Identifier> requestedPostEffects;

    @Inject(method = "update", at = @At("TAIL"))
    private void jjk$impactFrames(DeltaTracker deltaTracker, CallbackInfo ci) {
        requestedPostEffects.addAll(dev.rick.jjk.client.fx.ImpactFrames.current());
    }
}
