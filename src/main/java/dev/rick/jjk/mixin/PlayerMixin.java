package dev.rick.jjk.mixin;

import dev.rick.jjk.core.combat.Combat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {
    /** No vanilla attacks while stunned. */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void jjk$attack(Entity target, CallbackInfo ci) {
        if (Combat.actionsLocked((Player) (Object) this)) ci.cancel();
    }
}
