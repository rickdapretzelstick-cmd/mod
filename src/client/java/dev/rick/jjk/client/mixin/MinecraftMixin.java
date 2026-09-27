package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.input.InputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** In combat stance, left click is this mod's melee instead of a vanilla attack. */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jjk$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (InputHandler.inStance()) {
            InputHandler.onAttackPressed();
            cir.setReturnValue(false);
        }
    }

    /** Holding left click in stance charges a heavy; it must not mine blocks. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jjk$continueAttack(boolean down, CallbackInfo ci) {
        if (InputHandler.inStance()) ci.cancel();
    }
}
