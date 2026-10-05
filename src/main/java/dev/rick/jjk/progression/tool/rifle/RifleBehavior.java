package dev.rick.jjk.progression.tool.rifle;

import dev.rick.jjk.progression.tool.ToolBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/** The rifle's use key, handed to {@link RifleServer} (holding use is aiming, or charging the beam). */
public final class RifleBehavior implements ToolBehavior {
    @Override
    public boolean use(ServerLevel level, ServerPlayer player, ItemStack stack) {
        return RifleServer.use(level, player, stack);
    }

    @Override
    public void release(ServerLevel level, ServerPlayer player, ItemStack stack, int heldTicks) {
        RifleServer.release(level, player, stack, heldTicks);
    }

    @Override
    public int useDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public ItemUseAnimation useAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }
}
