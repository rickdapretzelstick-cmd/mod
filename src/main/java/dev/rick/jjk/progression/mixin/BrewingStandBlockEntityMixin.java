package dev.rick.jjk.progression.mixin;

import dev.rick.jjk.progression.CursedBrewing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hoppers can load {@link CursedBrewing} inputs into an empty bottle slot, like potions. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void jjk$cursedBottles(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot >= 0 && slot < 3 && CursedBrewing.isInput(stack)) {
            cir.setReturnValue(((BrewingStandBlockEntity) (Object) this).getItem(slot).isEmpty());
        }
    }
}
