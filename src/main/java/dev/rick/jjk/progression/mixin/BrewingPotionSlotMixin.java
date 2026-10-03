package dev.rick.jjk.progression.mixin;

import dev.rick.jjk.progression.CursedBrewing;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The brewing stand's bottle slots take {@link CursedBrewing} inputs (Soul in a Bottle), not only potions. */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public abstract class BrewingPotionSlotMixin {
    @Inject(method = "mayPlaceItem", at = @At("HEAD"), cancellable = true)
    private static void jjk$cursedBottles(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CursedBrewing.isInput(stack)) cir.setReturnValue(true);
    }
}
