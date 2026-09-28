package dev.rick.jjk.mixin;

import dev.rick.jjk.core.world.WorldRestoration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Items spilled by tracked damage, and falling blocks it set loose, never enter the world: the blocks come back instead. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSpawnMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void jjk$cancelDamageSpill(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (WorldRestoration.cancelSpawn((ServerLevel) (Object) this, entity)) cir.setReturnValue(false);
    }
}
