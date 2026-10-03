package dev.rick.jjk.progression.mixin;

import dev.rick.jjk.progression.CursedBrewing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the brewing stand brew {@link CursedBrewing}'s recipes (items that aren't potions). */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void jjk$hasCursedMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (CursedBrewing.output(input, ingredient) != null) cir.setReturnValue(true);
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void jjk$cursedMix(ItemStack ingredient, ItemStack potion, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack out = CursedBrewing.output(potion, ingredient);
        if (out != null) cir.setReturnValue(out);
    }
}
