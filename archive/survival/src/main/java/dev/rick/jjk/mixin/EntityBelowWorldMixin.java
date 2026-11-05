package dev.rick.jjk.mixin;

import dev.rick.jjk.progression.prison.PrisonRealm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Prison Realm lost to the void counts as destroyed: the world may forge another. */
@Mixin(Entity.class)
public abstract class EntityBelowWorldMixin {
    @Inject(method = "onBelowWorld", at = @At("HEAD"))
    private void jjk$prisonRealmLost(CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity item && item.level() instanceof ServerLevel level) PrisonRealm.itemDestroyed(level, item.getItem());
    }
}
