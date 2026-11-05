package dev.rick.jjk.progression.mixin;

import dev.rick.jjk.progression.CurseAggro;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every goal-based target change goes through {@code Mob.setTarget}: a curse that needs perception can't take a player
 * who can't perceive it (unless it is already hostile toward them), and taking one makes it hostile ({@link CurseAggro}).
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void jjk$curseTarget(@Nullable LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (target == null || self.level().isClientSide()) return;
        if (!CurseAggro.mayTarget(self, target)) {
            ci.cancel();
            return;
        }
        CurseAggro.onTargeted(self, target);
    }
}
