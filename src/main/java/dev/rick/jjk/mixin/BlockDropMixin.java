package dev.rick.jjk.mixin;

import dev.rick.jjk.core.world.WorldRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks broken as a knock-on of tracked damage don't drop items: they come back instead (no duplication). */
@Mixin(Block.class)
public abstract class BlockDropMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void jjk$holdDrop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (level instanceof ServerLevel sl && WorldRestoration.holdDrops(sl, pos)) ci.cancel();
    }

    @Inject(method = "popResourceFromFace", at = @At("HEAD"), cancellable = true)
    private static void jjk$holdFaceDrop(Level level, BlockPos pos, Direction face, ItemStack stack, CallbackInfo ci) {
        if (level instanceof ServerLevel sl && WorldRestoration.holdDrops(sl, pos)) ci.cancel();
    }
}
