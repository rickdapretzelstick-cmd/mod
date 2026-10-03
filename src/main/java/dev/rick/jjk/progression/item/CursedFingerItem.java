package dev.rick.jjk.progression.item;

import dev.rick.jjk.progression.CursedFingerAcquisition;
import dev.rick.jjk.progression.TechniqueProgression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Cursed Finger. Eating it is always final: the finger is gone whatever happens. The first player to eat one becomes
 * the world's Yuji; anyone who eats one after that is consumed by it ({@link CursedFingerAcquisition}).
 */
public class CursedFingerItem extends Item {
    public CursedFingerItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Eaten first (the finger is consumed in every outcome), then the server settles what it did to them.
        ItemStack rest = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide() && entity instanceof ServerPlayer player) TechniqueProgression.acquire(player, CursedFingerAcquisition.INSTANCE);
        return rest;
    }
}
