package dev.rick.jjk.mixin;

import dev.rick.jjk.core.defense.Defenses;
import dev.rick.jjk.gojo.InfinityDefense;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    /** Projectiles never make contact with someone behind Infinity; the field holds them in the air instead. */
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void jjk$infinity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && ((Projectile) (Object) this).getOwner() != entity
                && Defenses.isActive(living, InfinityDefense.ID)) {
            cir.setReturnValue(false);
        }
    }
}
