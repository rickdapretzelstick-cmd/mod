package dev.rick.jjk.progression.tool.kit;

import dev.rick.jjk.JJK;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The inventory screen's Cursed Item slot (beside the offhand): one cursed tool, whose moveset it gives. */
public final class CursedItemSlot extends Slot {
    public static final Identifier EMPTY_ICON = JJK.id("container/slot/cursed_item");
    /** Where it sits in the survival inventory (just above the offhand slot). */
    public static final int X = 77, Y = 44;

    public CursedItemSlot(CursedSlot container) {
        super(container, 0, X, Y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return CursedSlot.fits(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public @Nullable Identifier getNoItemIcon() {
        return EMPTY_ICON;
    }
}
