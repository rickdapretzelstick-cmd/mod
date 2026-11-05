package dev.rick.jjk.progression.item;

import dev.rick.jjk.progression.CursedFingerAcquisition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Cursed Finger. Eating it is always final: the finger is gone whatever happens. The world's Yuji absorbs it (for the
 * Sukuna progression to come); anyone else is consumed by it ({@link CursedFingerAcquisition}). It no longer makes Yuji.
 */
public class CursedFingerItem extends Item {
    public CursedFingerItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Eaten first (the finger is consumed in every outcome), then the server settles what it did to them.
        ItemStack rest = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) CursedFingerAcquisition.eat(player);
        return rest;
    }
}
