package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.rifle.RifleScope;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Through the rifle's scope the mouse turns the view in step with the zoom: precise, never sluggish or delayed. */
@Mixin(MouseHandler.class)
public abstract class ScopeSensitivityMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void jjk$scoped(LocalPlayer player, double xo, double yo) {
        // In training the view is the scene's; the mouse doesn't turn the participant.
        if (dev.rick.jjk.client.training.TrainingClient.capturing()) return;
        double k = RifleScope.sensitivity();
        player.turn(xo * k, yo * k);
    }
}
