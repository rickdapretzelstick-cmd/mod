package dev.rick.jjk.client.mixin;

import dev.rick.jjk.progression.tool.kit.CursedSlot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** The creative inventory tab lays out the survival slots itself: put the Cursed Item slot beside the armour. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeCursedSlotMixin {
    @ModifyArgs(method = "selectTab", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private void jjk$place(Args args) {
        Slot target = args.get(0);
        if (target.container instanceof CursedSlot) {
            args.set(2, 127);
            args.set(3, 20);
        }
    }
}
