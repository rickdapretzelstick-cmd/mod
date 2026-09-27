package dev.rick.jjk.mixin;

import dev.rick.jjk.core.combat.Combat;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {
    /** Stunned mobs don't think: no pathing, targeting or attacking until they recover. */
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void jjk$freezeAi(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (Combat.actionsLocked(self)) {
            self.xxa = 0;
            self.zza = 0;
            self.setJumping(false);
            ci.cancel();
        }
    }
}
