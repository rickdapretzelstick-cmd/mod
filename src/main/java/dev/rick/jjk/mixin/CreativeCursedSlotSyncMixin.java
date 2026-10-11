package dev.rick.jjk.mixin;

import dev.rick.jjk.progression.tool.kit.CursedItemSlot;
import dev.rick.jjk.progression.tool.kit.CursedKits;
import dev.rick.jjk.progression.tool.kit.CursedSlot;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Creative inventory sets slots by index, and the server takes only the vanilla ones (1 to 45). The Cursed Item slot
 * comes after them: without this, putting a tool in or taking it out in Creative only happened on the client, and the
 * server kept the old tool (and its moveset) equipped.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeCursedSlotSyncMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void jjk$cursedSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.level());
        int n = packet.slotNum();
        var menu = player.inventoryMenu;
        if (n < 0 || n >= menu.slots.size() || !(menu.getSlot(n) instanceof CursedItemSlot slot)) return;
        ci.cancel();
        if (!player.hasInfiniteMaterials()) return;
        ItemStack stack = packet.itemStack();
        if (!stack.isEmpty() && (!CursedSlot.fits(stack) || stack.getCount() > 1)) return;
        slot.setByPlayer(stack.copy());
        menu.setRemoteSlot(n, stack);
        menu.broadcastChanges();
        CursedKits.update(player);
    }
}
