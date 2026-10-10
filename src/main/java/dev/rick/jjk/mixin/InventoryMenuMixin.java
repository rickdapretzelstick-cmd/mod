package dev.rick.jjk.mixin;

import dev.rick.jjk.progression.tool.kit.CursedItemSlot;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's inventory gains the Cursed Item slot (after every vanilla slot, so their indices don't move). */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void jjk$cursedSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        ((AbstractContainerMenuAccessor) this).jjk$addSlot(new CursedItemSlot(new CursedSlot(owner)));
    }
}
