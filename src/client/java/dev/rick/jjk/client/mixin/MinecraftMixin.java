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
    /** In combat mode, left click is this mod's melee instead of a vanilla attack (never mining). */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jjk$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (dev.rick.jjk.client.training.TrainingClient.capturing()) {
            cir.setReturnValue(false);
            return;
        }
        if (InputHandler.inStance() || dev.rick.jjk.client.finish.BindClient.active()) {
            InputHandler.onAttackPressed();
            cir.setReturnValue(false);
        }
    }

    /** A realm fight's revealed curses are outlined through walls, for the players in that fight only. */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void jjk$revealed(net.minecraft.world.entity.Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (dev.rick.jjk.client.hud.EncounterHud.revealed(e)) cir.setReturnValue(true);
    }

    /** Holding left click charges a heavy; it must never mine blocks. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jjk$continueAttack(boolean down, CallbackInfo ci) {
        if (dev.rick.jjk.client.training.TrainingClient.capturing()) {
            ci.cancel();
            return;
        }
        if (InputHandler.inStance()) ci.cancel();
    }

    /** No using items in training (the hands are the scene's). */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jjk$startUseItem(CallbackInfo ci) {
        if (dev.rick.jjk.client.training.TrainingClient.capturing()) ci.cancel();
    }
}
