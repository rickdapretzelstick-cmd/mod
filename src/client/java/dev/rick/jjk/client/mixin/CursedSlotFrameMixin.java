package dev.rick.jjk.client.mixin;

import dev.rick.jjk.progression.tool.kit.CursedSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Cursed Item slot sits where the inventory's background has no slot drawn: draw its frame (a vanilla-style
 * inset, with a faint cursed tint) before its contents.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class CursedSlotFrameMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void jjk$frame(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!(slot.container instanceof CursedSlot)) return;
        int x = slot.x - 1, y = slot.y - 1;
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0x305A2A7A);
    }
}
