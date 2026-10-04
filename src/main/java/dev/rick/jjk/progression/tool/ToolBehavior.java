package dev.rick.jjk.progression.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * What a cursed tool's Mastery unlocks do in a fight. Each tool has its own (a fast blade combos and dashes, a heavy
 * one charges and breaks guards); every hook is optional.
 */
public interface ToolBehavior {
    /** After one of its swings landed on {@code target} (server). */
    default void onHit(ServerLevel level, ServerPlayer player, ItemStack stack, LivingEntity target) {}

    /** Use (right click) pressed. Return true if something happened. */
    default boolean use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        return false;
    }

    /** Use released after holding {@code heldTicks}. */
    default void release(ServerLevel level, ServerPlayer player, ItemStack stack, int heldTicks) {}

    /** How long use can be held (0: it isn't held). */
    default int useDuration(ItemStack stack) {
        return 0;
    }

    default ItemUseAnimation useAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /** Each tick it is held in the main hand (server). */
    default void heldTick(ServerLevel level, ServerPlayer player, ItemStack stack) {}

    ToolBehavior NONE = new ToolBehavior() {};
}
