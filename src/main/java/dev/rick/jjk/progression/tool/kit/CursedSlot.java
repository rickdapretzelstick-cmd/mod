package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.progression.tool.CursedToolItem;
import dev.rick.jjk.registry.ModAttachments;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Cursed Item slot as a one-slot container over the player's {@link ModAttachments#CURSED_ITEM}: what the
 * inventory screen's extra slot reads and writes. Only a cursed tool fits. The attachment is the truth (saved with the
 * player, synced to everyone); this is just the window onto it.
 */
public final class CursedSlot implements Container {
    private final Player player;

    public CursedSlot(Player player) {
        this.player = player;
    }

    public static ItemStack get(Player p) {
        ItemStack s = p.getAttached(ModAttachments.CURSED_ITEM);
        return s == null ? ItemStack.EMPTY : s;
    }

    public static void set(Player p, ItemStack s) {
        if (s == null || s.isEmpty()) p.removeAttached(ModAttachments.CURSED_ITEM);
        else p.setAttached(ModAttachments.CURSED_ITEM, s);
    }

    /** Whether a stack may go in the slot: a cursed tool. */
    public static boolean fits(ItemStack s) {
        return s.getItem() instanceof CursedToolItem;
    }

    public Player player() {
        return player;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return get(player).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? get(player) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot != 0 || count <= 0) return ItemStack.EMPTY;
        ItemStack rest = get(player).copy();
        ItemStack out = rest.split(count);
        set(player, rest);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack out = get(player);
        set(player, ItemStack.EMPTY);
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) set(player, stack.copy());
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player p) {
        return p == player;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return fits(stack);
    }

    @Override
    public void clearContent() {
        set(player, ItemStack.EMPTY);
    }
}
