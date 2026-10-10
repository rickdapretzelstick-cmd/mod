package dev.rick.jjk.client.mixin;

import dev.rick.jjk.client.gear.CursedGear;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * First person: a drawn cursed tool is in the hand when the hand slot is empty. Going through the vanilla item swap
 * means drawing and holstering lower and raise the hand like any change of item.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
    private ItemStack jjk$drawnTool(LocalPlayer player) {
        return CursedGear.mainHand(player, player.getMainHandItem());
    }
}
